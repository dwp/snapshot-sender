#!/usr/bin/env python3

import argparse
import base64
import binascii
import json
import os
import time
import uuid

from Crypto import Random
from Crypto.Cipher import AES
from Crypto.Util import Counter

import requests

import happybase
import thriftpy2

def main():
    args = command_line_args()
    connected = False
    attempts = 0
    init(args)
    while not connected and attempts < 100:
        try:
            connection = happybase.Connection(args.zookeeper_quorum)
            connection.open()
            connected = True
            tables = [x.decode('ascii') for x in connection.tables()]
            print(f"tables: '{tables}'")

            print("data_key_service='{}'".format(args.data_key_service))

            if args.data_key_service:
                content = requests.get(args.data_key_service).json()
                encryption_key = content['plaintextDataKey']
                encrypted_key = content['ciphertextDataKey']
                master_key_id = content['dataKeyEncryptionKeyId']
                print("Got data from dks")
            else:
                encryption_key = "czMQLgW/OrzBZwFV9u4EBA=="
                master_key_id = "1234567890"
                encrypted_key = "blahblah"
                print("Using fake dks data")

            with (open(args.test_configuration_file)) as file:
                data = json.load(file)
                for datum in data:
                    record_id = datum['kafka_message_id']
                    timestamp = datum['kafka_message_timestamp']
                    value = datum['kafka_message_value']
                    db_name = value['message']['db']
                    collection_name = value['message']['collection']
                    topic_name = "db." + db_name + "." + collection_name
                    table_name = f"{db_name}:{collection_name}".replace("-", "_")

                    if not table_name in tables:
                        print(f"Created table '{table_name}'.")
                        connection.create_table(table_name,
                                                {'cf': dict(max_versions=1000000)})
                        tables.append(table_name)


                    print(f"Creating record {record_id} timestamp {timestamp} topic {topic_name} in table {table_name}")

                    if 'dbObject' in value['message']:
                        db_object = value['message']['dbObject']
                        if db_object != "CORRUPT":
                            value['message']['dbObject'] = ""
                            record = unique_decrypted_db_object()
                            record_string = json.dumps(record)
                            [iv, encrypted_record] = encrypt(encryption_key,
                                                             record_string)
                            value['message']['encryption']['initialisationVector'] \
                                = iv.decode('ascii')

                            if master_key_id:
                                value['message']['encryption']['keyEncryptionKeyId'] = \
                                    master_key_id

                            if encrypted_key:
                                value['message']['encryption']['encryptedEncryptionKey'] = \
                                    encrypted_key

                            value['message']['dbObject'] = encrypted_record.decode('ascii')
                        else:
                            value['message']['encryption']['initialisationVector'] = "PHONEYVECTOR"

                        column_family_qualifier =  "cf:record"
                        obj = {column_family_qualifier: json.dumps(value)}
                        table = connection.table(table_name)
                        table.put(record_id, obj, timestamp=int(timestamp))
                        print(f"Saved record {record_id} timestamp {timestamp} topic {topic_name} in table {table_name}")
                    else:
                        print(f"Skipped record {record_id} as dbObject was missing")

                if args.completed_flag:
                    print(f"Creating directory: '{args.completed_flag}'.")
                    print(f"/opt/snapshot-sender {args.completed_flag}.")
                    os.makedirs(args.completed_flag)

        except (ConnectionError, thriftpy2.transport.TTransportException) as e:
            attempts = attempts + 1
            print(f"Failed to connect: '{e}', attempt no {attempts}.")
            time.sleep(3)

    exit(0 if connected else 1)


def encrypt(key, plaintext):
    initialisation_vector = Random.new().read(AES.block_size)
    iv_int = int(binascii.hexlify(initialisation_vector), 16)
    counter = Counter.new(AES.block_size * 8, initial_value=iv_int)
    aes = AES.new(base64.b64decode(key), AES.MODE_CTR, counter=counter)
    ciphertext = aes.encrypt(plaintext.encode("utf8"))
    return (base64.b64encode(initialisation_vector),
            base64.b64encode(ciphertext))


def decrypted_db_object():
    return {
        "_id": {
            "someId": "RANDOM_GUID"
        },
        "type": "addressDeclaration",
        "contractId": "RANDOM_GUID",
        "addressNumber": {
            "type": "AddressLine",
            "cryptoId": "RANDOM_GUID"
        },
        "addressLine2": None,
        "townCity": {
            "type": "AddressLine",
            "cryptoId": "RANDOM_GUID"
        },
        "postcode": "SM5 2LE",
        "processId": "RANDOM_GUID",
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
            "$date":"2015-03-20T12:23:25.183Z"
        },
        "_version": 2,
        "_lastModifiedDateTime": {
            "$date": "2018-12-14T15:01:02.000+0000"
        }
    }


def guid():
    return str(uuid.uuid4())


def unique_decrypted_db_object():
    record = decrypted_db_object()
    record['_id']['declarationId'] = guid()
    record['contractId'] = guid()
    record['addressNumber']['cryptoId'] = guid()
    record['townCity']['cryptoId'] = guid()
    record['processId'] = guid()
    return record


def command_line_args():
    parser = argparse.ArgumentParser(description='Pre-populate hbase.')
    parser.add_argument('-c', '--completed-flag',
                        help='The flag to write on successful completion.')
    parser.add_argument('-k', '--data-key-service',
                        help='Use the specified data key service.')
    parser.add_argument('-o', '--remove-output-file',
                        help='Remove the output file.')
    parser.add_argument('-z', '--zookeeper-quorum', default='hbase',
                        help='The zookeeper quorum host.')
    parser.add_argument('-f', '--test-configuration-file',
                        help='File containing the test config for sample data.')
    return parser.parse_args()


def init(args):
    if args.completed_flag:
        if os.path.isdir(args.completed_flag):
            print("Removing directory '{}'.".format(args.completed_flag))
            os.removedirs(args.completed_flag)
        elif os.path.isfile(args.completed_flag):
            print("Removing file '{}'.".format(args.completed_flag))
            os.remove(args.completed_flag)
        else:
            print("Argument --completed-flag was set but no file or folder to remove")
    else:
        print("Argument --completed-flag not set, no file removed")

    if args.remove_output_file:
        if os.path.isfile(args.remove_output_file):
            print("Removing file '{}'.".format(args.remove_output_file))
            os.remove(args.remove_output_file)
        else:
            print("File '{}' not found, no file removed".format(args.remove_output_file))
    else:
        print("Argument --remove-output-file not set, no file removed")


if __name__ == "__main__":
    main()
