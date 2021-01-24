#!/usr/bin/env bash

main() {
    make_keystore dks-keystore.jks dks
    extract_public_certificate dks-keystore.jks dks.crt
    make_truststore dks-truststore.jks dks.crt

    make_keystore snapshot-sender-keystore.jks snapshot-sender
    extract_public_certificate snapshot-sender-keystore.jks snapshot-sender.crt
    make_truststore snapshot-sender-truststore.jks snapshot-sender.crt

    make_keystore mock-nifi-keystore.jks mock-nifi
    extract_public_certificate mock-nifi-keystore.jks mock-nifi.crt
    make_truststore mock-nifi-truststore.jks mock-nifi.crt

    make_keystore aws-init-keystore.jks localstack
    extract_public_certificate aws-init-keystore.jks aws-init.crt
    import_into_truststore dks-truststore.jks aws-init.crt \
                           aws-init

    extract_pems ./aws-init-keystore.jks
    extract_pems ./dks-keystore.jks

    cp -v dks-crt.pem aws-init-key.pem aws-init-crt.pem aws-init

    import_into_truststore dks-truststore.jks snapshot-sender.crt snapshot-sender
    import_into_truststore snapshot-sender-truststore.jks mock-nifi.crt mock-nifi
    import_into_truststore snapshot-sender-truststore.jks dks.crt dks
    import_into_truststore mock-nifi-truststore.jks snapshot-sender.crt snapshot-sender
}

make_keystore() {
    local keystore="${1:?Usage: $FUNCNAME keystore common-name}"
    local common_name="${2:?Usage: $FUNCNAME keystore common-name}"

    [[ -f "${keystore}" ]] && rm -v "${keystore}"

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

    [[ -f "${certificate}" ]] && rm -v "${certificate}"

    keytool -v \
            -exportcert \
            -keystore "$keystore" \
            -storepass $(password) \
            -alias cid \
            -file "${certificate}"
}

make_truststore() {
    local truststore="${1:?Usage: $FUNCNAME truststore certificate}"
    local certificate="${2:?Usage: $FUNCNAME truststore certificate}"
    [[ -f ${truststore} ]] && rm -v "${truststore}"
    import_into_truststore ${truststore} ${certificate} self
}

import_into_truststore() {
    local truststore="${1:?Usage: $FUNCNAME truststore certificate}"
    local certificate="${2:?Usage: $FUNCNAME truststore certificate}"
    local alias="${3:-cid}"

    keytool -importcert \
            -noprompt \
            -v \
            -trustcacerts \
            -alias "${alias}" \
            -file "${certificate}" \
            -keystore "${truststore}" \
            -storepass $(password)
}

extract_pems() {
    local keystore=${1:-keystore.jks}
    local key=${2:-${keystore%-keystore.jks}-key.pem}
    local certificate=${3:-${keystore%-keystore.jks}-crt.pem}

    local intermediate_store=${keystore/jks/p12}

    local filename=$(basename $keystore)
    local alias=cid

    [[ -f $intermediate_store ]] && rm -v $intermediate_store
    [[ -f $key ]] && rm -v $key

    if keytool -importkeystore \
               -srckeystore $keystore \
               -srcstorepass $(password) \
               -srckeypass $(password) \
               -srcalias $alias \
               -destalias $alias \
               -destkeystore $intermediate_store \
               -deststoretype PKCS12 \
               -deststorepass $(password) \
               -destkeypass $(password); then
        local pwd=$(password)
        export pwd

        openssl pkcs12 \
                -in $intermediate_store \
                -nodes \
                -nocerts \
                -password env:pwd \
                -out $key

        openssl pkcs12 \
                -in $intermediate_store \
                -nokeys \
                -out $certificate \
                -password env:pwd

        unset pwd
    else
        echo Failed to generate intermediate keystore $intermediate_store >&2
    fi
}

password() {
    echo changeit
}

main
