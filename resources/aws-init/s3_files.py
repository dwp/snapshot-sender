#!/usr/bin/env python3

import base64
import binascii
import gzip
import json

import boto3 as boto3
import requests
from Crypto import Random
from Crypto.Cipher import AES
from Crypto.Util import Counter


def main():
    response = requests.get("https://dks-standalone-https:8443/datakey",
                            cert=("aws-init-crt.pem",
                                  "aws-init-key.pem"),
                            verify="dks-crt.pem").json()

    plaintext_datakey = response['plaintextDataKey']
    encrypting_key_id = response['dataKeyEncryptionKeyId']
    ciphertext_datakey = response['ciphertextDataKey']
    s3 = aws_client("s3")
    for file_number in range(100):
        records = [db_object(file_number, record_number) for record_number in range(1000)]
        raw_contents = "\n".join(records)
        compressed_contents = gzip.compress(raw_contents.encode())
        initialisation_vector, encrypted_contents = encrypt(plaintext_datakey, compressed_contents)
        object_metadata = {
            "iv": initialisation_vector,
            "cipherText": ciphertext_datakey,
            "dataKeyEncryptionKeyId": encrypting_key_id
        }
        s3_object_key = f"test/output/db.core.claimant-045-050-{file_number:06d}.txt.gz.enc"
        s3.put_object(Bucket="demobucket", Body=encrypted_contents, Key=s3_object_key, Metadata=object_metadata)
        print(f"Put {s3_object_key}.")


def db_object(file_number: int, record_number: int) -> str:
    return json.dumps({
        "_id": {
            "citizenId": f"{file_number}/{record_number}"
        },
        "type": "addressDeclaration",
        "contractId": "guid",
        "addressNumber": {
            "type": "AddressLine",
            "cryptoId": "guid"
        },
        "addressLine2": None,
        "townCity": {
            "type": "AddressLine",
            "cryptoId": "guid"
        },
        "postcode": "SM5 2LE",
        "processId": "guid",
        "effectiveDate": {
            "type": "SPECIFIC_EFFECTIVE_DATE",
            "date": 20150320,
            "knownDate": 20150320
        },
        "paymentEffectiveDate": {
            "type": "SPECIFIC_EFFECTIVE_DATE",
            "date": 20150320,
            "knownDate": 20150320
        },
        "createdDateTime": {
            "$date": "2015-03-20T12:23:25.183Z"
        },
        "_version": 2,
        "_lastModifiedDateTime": {
            "$date": "2018-12-14T15:01:02.000+0000"
        }
    })


def encrypt(key, unencrypted):
    initialisation_vector = Random.new().read(AES.block_size)
    iv_int = int(binascii.hexlify(initialisation_vector), 16)
    counter = Counter.new(AES.block_size * 8, initial_value=iv_int)
    aes = AES.new(base64.b64decode(key), AES.MODE_CTR, counter=counter)
    ciphertext = aes.encrypt(unencrypted)
    return base64.b64encode(initialisation_vector).decode(), ciphertext


def aws_client(service_name: str):
    return boto3.client(service_name=service_name,
                        endpoint_url="http://aws:4566",
                        use_ssl=False,
                        region_name='eu-west-2',
                        aws_access_key_id="accessKeyId",
                        aws_secret_access_key="secretAccessKey")


if __name__ == "__main__":
    main()
