#!/usr/bin/env bash

aws_local() {
  aws --endpoint-url http://aws:4566 --region=eu-west-2 "$@"
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

    aws_local dynamodb delete-item \
          --table-name "UCExportToCrownStatus" \
          --key \
            "{\"CorrelationId\":{\"S\":\"123\"},\"CollectionName\":{\"S\":\"db.core.toDo\"}}" \
          --return-values "ALL_OLD"

    aws_local dynamodb update-item \
          --table-name "UCExportToCrownStatus" \
          --key \
            "{\"CorrelationId\":{\"S\":\"123\"},\"CollectionName\":{\"S\":\"db.core.toDo\"}}" \
          --update-expression "SET CollectionStatus = :cs, FilesExported = :fe, FilesSent = :fs" \
          --return-values "ALL_NEW" \
          --expression-attribute-values "{\":cs\": {\"S\":\"Exported\"}, \":fe\": {\"N\":\"2\"}, \":fs\": {\"N\":\"0\"}}"



}

main() {
    init
    create_export_bucket
    create_crl_bucket
    create_uc_ecc_table
}

main
