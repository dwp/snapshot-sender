#!/usr/bin/env python3

import base64
import binascii
import sys

import requests
import regex

from Crypto.Cipher import AES
from Crypto.Util import Counter

def decrypt(key, iv, ciphertext):
    iv_int = int(binascii.hexlify(base64.b64decode(iv)), 16)
    ctr = Counter.new(AES.block_size * 8, initial_value=iv_int)
    aes = AES.new(key.encode("utf-8"), AES.MODE_CTR, counter=ctr)
    return aes.decrypt(base64.b64decode(ciphertext))

def main():
    config = {}
    with open(sys.argv[1]) as metadata:
        for line in metadata:
            match = regex.search(r'(\w+)=(.*)', line)
            (key, value) = (match[1], match[2])
            config[key] = value

    with open(sys.argv[2]) as encrypted_file:
        contents = encrypted_file.read()
        ciphertext = config['ciphertext']
        master_key_id = regex.sub(r'^.*?/', '', config['dataKeyEncryptionKeyId'])
        result = \
            requests.post(f"http://localhost:8090/datakey/actions/decrypt?keyId={master_key_id}",
                          data=ciphertext)
        content = result.json()
        plaintext = content['plaintextDataKey']
        decrypted = decrypt(plaintext, config['iv'], contents)
        sys.stdout.buffer.write(decrypted)

if __name__ == '__main__':
    main()
