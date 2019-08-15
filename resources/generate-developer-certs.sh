#!/usr/bin/env bash

#!/bin/bash

main() {
    make_keystore dks-keystore.jks dks-standalone-https
    extract_public_certificate dks-keystore.jks dks-standalone-https.crt
    make_truststore dks-truststore.jks dks-standalone-https.crt

    make_keystore htme-keystore.jks hbase-to-mongo-export
    extract_public_certificate htme-keystore.jks hbase-to-mongo-export.crt
    make_truststore htme-truststore.jks hbase-to-mongo-export.crt

    make_keystore snapshot-sender-keystore.jks snapshot-sender
    extract_public_certificate snapshot-sender-keystore.jks snapshot-sender.crt
    make_truststore snapshot-sender-truststore.jks snapshot-sender.crt

    import_into_truststore dks-truststore.jks hbase-to-mongo-export.crt hbase-to-mongo-export
    import_into_truststore dks-truststore.jks snapshot-sender.crt snapshot-sender
    import_into_truststore htme-truststore.jks dks-standalone-https.crt dks
    import_into_truststore snapshot-sender.jks dks-standalone-https.crt dks
}

make_keystore() {
    local keystore="${1:?Usage: $FUNCNAME keystore common-name}"
    local common_name="${2:?Usage: $FUNCNAME keystore common-name}"

    [[ -f "$keystore" ]] && rm -v "$keystore"

    keytool -v \
            -genkeypair \
            -keyalg RSA \
            -alias cid \
            -keystore "$keystore" \
            -storepass $(password) \
            -validity 365 \
            -keysize 2048 \
            -keypass $(password) \
            -dname "CN=${common_name},OU=DataWorks,O=DWP,L=Leeds,ST=West Yorkshire,C=UK"
}

extract_public_certificate() {
    local keystore="${1:?Usage: $FUNCNAME keystore certificate}"
    local certificate="${2:?Usage: $FUNCNAME keystore certificate}"

    [[ -f "$certificate" ]] && rm -v "$certificate"

    keytool -v \
            -exportcert \
            -keystore "$keystore" \
            -storepass $(password) \
            -alias cid \
            -file "$certificate"
}

make_truststore() {
    local truststore="${1:?Usage: $FUNCNAME truststore certificate}"
    local certificate="${2:?Usage: $FUNCNAME truststore certificate}"
    [[ -f $truststore ]] && rm -v "$truststore"
    import_into_truststore $truststore $certificate self
}

import_into_truststore() {
    local truststore="${1:?Usage: $FUNCNAME truststore certificate}"
    local certificate="${2:?Usage: $FUNCNAME truststore certificate}"
    local alias="${3:-cid}"

    keytool -importcert \
            -noprompt \
            -v \
            -trustcacerts \
            -alias "$alias" \
            -file "$certificate" \
            -keystore "$truststore" \
            -storepass $(password)
}

password() {
    echo changeit
}

main