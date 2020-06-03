#!/usr/bin/env bash

aws configure set aws_access_key_id "${AWS_ACCESS_KEY_ID}"
aws configure set aws_secret_access_key "${AWS_SECRET_ACCESS_KEY}"
aws configure set default.region "${AWS_DEFAULT_REGION}"
aws configure set region "${AWS_REGION}"

aws configure list

printenv

echo "Listing existing buckets on container ${S3_SERVICE_ENDPOINT}"
aws --endpoint-url="${S3_SERVICE_ENDPOINT}" s3 ls --region "${AWS_REGION}"
echo ""

echo "Creating bucket ${S3_BUCKET}"
aws --endpoint-url="${S3_SERVICE_ENDPOINT}" s3 mb "s3://${S3_BUCKET}" --region "${AWS_REGION}"
echo ""

echo "Setting up ACL for ${S3_BUCKET}"
aws --endpoint-url="${S3_SERVICE_ENDPOINT}" s3api put-bucket-acl --bucket "${S3_BUCKET}" --acl public-read
echo ""

echo "--s3 bucket ${S3_BUCKET} done--"

echo Creating crl bucket
aws --endpoint-url="${S3_SERVICE_ENDPOINT}" s3 mb "s3://dw-local-crl" --region "${AWS_REGION}"
aws --endpoint-url="${S3_SERVICE_ENDPOINT}" s3api put-bucket-acl --bucket "dw-local-crl" --acl public-read
aws --endpoint-url="${S3_SERVICE_ENDPOINT}" s3api put-object --bucket "dw-local-crl" --key crl/

# it only works with 18.03 + docker engine
# https://stackoverflow.com/questions/24319662/from-inside-of-a-docker-container-how-do-i-connect-to-the-localhost-of-the-mach
