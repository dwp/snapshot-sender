#!/bin/bash

add_container() {
    local container=${1:?Usage: $FUNCNAME container service}

    host_entry=$(docker exec $container cat /etc/hosts |
                     egrep -v '(localhost|ip6)' | tail -n1)

    if [[ -n "$host_entry" ]]; then

        temp_file=$(mktemp)
        (
            cat /etc/hosts | fgrep -v $container
            echo ${host_entry} $container \# added by $FUNCNAME.
        ) > $temp_file

        sudo mv ${temp_file} /etc/hosts
        sudo chmod 644 /etc/hosts
    else
        (
            echo could not get host name for \'$container\' from hosts file:
            docker exec $container cat /etc/hosts
        ) >&2
    fi

}

add_container dks-standalone-http
add_container dks-standalone-https
add_container mock-nifi
add_container hbase
add_container s3-dummy
add_container zookeeper

cat /etc/hosts
