#!/bin/bash

source ./environment.sh

main() {
    init
    create_export_bucket
    create_crl_bucket
    create_uc_ecc_table
    create_sns_monitoring_topic
    create_sqs_monitoring_queue
    sqs_list_queues
    subscribe_sns_to_sqs
    add_status_item
    add_empty_status_item
    add_sent_status_item
    add_htme_outputs
}

main
