#!/bin/bash

dks_https_name=$(docker exec dks-standalone-https cat /etc/hosts \
                 | egrep -v '(localhost|ip6)' | tail -n1)

echo "dks https container is '${dks_https_name}'"

if [[ -n "${dks_https_name}" ]]; then

    temp_file=$(mktemp)
    (
        cat /etc/hosts | grep -v 'added by dks-https-to-mongo-export.$'
        echo ${dks_https_name} local-dks-https \# added by dks-https-to-mongo-export.
    ) > $temp_file

    sudo mv $temp_file /etc/hosts
    sudo chmod 644 /etc/hosts
    cat /etc/hosts
else
    (
        echo could not get host name from dks hosts file:
        docker exec dks-standalone-https cat /etc/hosts
    ) >&2
fi
echo "...hosts updated for dks https container '${dks_https_name}'"

dks_http_name=$(docker exec dks-standalone-http cat /etc/hosts \
                 | egrep -v '(localhost|ip6)' | tail -n1)

echo "dks http container is '${dks_http_name}'"

if [[ -n "${dks_http_name}" ]]; then

    temp_file=$(mktemp)
    (
        cat /etc/hosts | grep -v 'added by dks-http-to-mongo-export.$'
        echo ${dks_http_name} local-dks-http \# added by dks-http-to-mongo-export.
    ) > $temp_file

    sudo mv $temp_file /etc/hosts
    sudo chmod 644 /etc/hosts
    cat /etc/hosts
else
    (
        echo could not get host name from dks hosts file:
        docker exec dks-standalone-http cat /etc/hosts
    ) >&2
fi
echo "...hosts updated for dks http container '${dks_http_name}'"

hbase_name=$(docker exec hbase cat /etc/hosts \
                 | egrep -v '(localhost|ip6)' | tail -n1)

echo "hbase container is '${hbase_name}'"

if [[ -n "$hbase_name" ]]; then

    temp_file=$(mktemp)
    (
        cat /etc/hosts | grep -v 'added by hbase-to-mongo-export.$'
        echo ${hbase_name} local-hbase \# added by hbase-to-mongo-export.
    ) > $temp_file

    sudo mv $temp_file /etc/hosts
    sudo chmod 644 /etc/hosts
    cat /etc/hosts
else
    (
        echo could not get host name from hbase hosts file:
        docker exec hbase cat /etc/hosts
    ) >&2
fi
