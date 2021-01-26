#!/bin/bash

source ./environment.sh

main() {
    init
    create_export_bucket
    create_crl_bucket
    create_uc_ecc_table
    add_status_item
    add_empty_status_item
    add_sent_status_item
    add_htme_outputs
}

main
