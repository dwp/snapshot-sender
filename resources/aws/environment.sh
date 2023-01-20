#!/usr/bin/env bash

aws_local() {
  aws --endpoint-url=http://aws:4566 --region=eu-west-2 "$@"
}

init() {
    aws_local configure set aws_access_key_id access_key_id
    aws_local configure set aws_secret_access_key secret_access_key
}

make_bucket() {
    local bucket_name=$1

    if ! aws_local s3 ls s3://$bucket_name 2>/dev/null; then
        echo Making $bucket_name
        aws_local s3 mb s3://$bucket_name
        aws_local s3api put-bucket-acl --bucket $bucket_name --acl public-read
    else
        echo \'$bucket_name\' exists.
    fi

}

create_export_bucket() {
    make_bucket demobucket
}

create_crl_bucket() {
    make_bucket dw-local-crl
    aws_local s3api put-object --bucket dw-local-crl --key crl/
}

create_uc_ecc_table() {
    local existing_tables=$(aws_local dynamodb list-tables --query "TableNames")
    if [[ ! $existing_tables =~ UCExportToCrownStatus ]]; then
        echo Creating 'UCExportToCrownStatus' table.
        aws_local dynamodb create-table \
                  --table-name UCExportToCrownStatus \
                  --key-schema \
                    AttributeName=CorrelationId,KeyType=HASH \
                    AttributeName=CollectionName,KeyType=RANGE \
                  --attribute-definitions \
                    AttributeName=CorrelationId,AttributeType=S \
                    AttributeName=CollectionName,AttributeType=S \
                  --billing-mode PAY_PER_REQUEST
    fi

}

create_sns_monitoring_topic() {
    aws_local sns create-topic --region eu-west-2 --name "monitoring-topic"
}

create_sqs_monitoring_queue() {
    aws_local sqs create-queue --region=eu-west-2 --queue-name "monitoring-queue.fifo" --attributes '{"FifoQueue":"true","ContentBasedDeduplication":"true"}'
}

subscribe_sns_to_sqs() {
    aws_local sqs list-queues

    aws_local sns subscribe --region eu-west-2 --topic-arn "arn:aws:sns:eu-west-2:000000000000:monitoring-topic" \
     --protocol "sqs" --notification-endpoint "http://localhost:4566/000000000000/monitoring-queue.fifo"
}

add_status_item() {
    add_item "$(status_item_id)" 100
}

add_empty_status_item() {
    add_item "$(empty_status_item_id)" 0
}

add_sent_status_item() {
    add_item "$(sent_status_item_id)" 10 10 Sent
}

add_htme_outputs() {
  ./s3_files.py
}

add_item() {
    local id=${1:?Usage: $FUNCNAME id files-exported}
    local files_exported=${2:?Usage: $FUNCNAME id files-exported}
    local files_sent=${3:-0}
    local collection_status=${4:-Exported}

    aws_local dynamodb delete-item \
              --table-name "UCExportToCrownStatus" \
              --key "$id" \
              --return-values "ALL_OLD"

    aws_local dynamodb update-item \
              --table-name "UCExportToCrownStatus" \
              --key "$id" \
              --update-expression "SET CollectionStatus = :cs, FilesExported = :fe, FilesSent = :fs" \
              --return-values "ALL_NEW" \
              --expression-attribute-values \
              '{":cs": {"S":"'$collection_status'"}, ":fe": {"N":"'$files_exported'"}, ":fs": {"N":"'$files_sent'"}}'

}

status_item_id() {
    echo '{"CorrelationId":{"S":"123"},"CollectionName":{"S":"db.core.claimant"}}'
}

empty_status_item_id() {
    echo '{"CorrelationId":{"S":"321"},"CollectionName":{"S":"db.database.empty"}}'
}

sent_status_item_id() {
    echo '{"CorrelationId":{"S":"111"},"CollectionName":{"S":"db.database.sent"}}'
}

get_status_item() {
    aws_local dynamodb get-item \
          --table-name "UCExportToCrownStatus" \
          --key \
            '{"CorrelationId":{"S":"123"},"CollectionName":{"S":"db.core.toDo"}}'
}
