FROM centos:latest

## Fix for "Error: Failed to download metadata for repo 'appstream': Cannot prepare internal mirrorlist: No URLs in mirrorlist"
RUN cd /etc/yum.repos.d/
RUN sed -i 's/mirrorlist/#mirrorlist/g' /etc/yum.repos.d/CentOS-*
RUN sed -i 's|#baseurl=http://mirror.centos.org|baseurl=http://vault.centos.org|g' /etc/yum.repos.d/CentOS-*
##

RUN mkdir -p /opt/snapshot-sender/data

RUN yum -y upgrade
RUN yum install -y epel-release
RUN yum install -y java-1.8.0-openjdk wget jq
ENV JAVA_HOME /etc/alternatives/jre

RUN chmod a+rwx -R /opt/snapshot-sender/ /opt/snapshot-sender/data

WORKDIR /opt/snapshot-sender

COPY build/libs/snapshot-sender-0.0.1.jar ./snapshot-sender-latest.jar

COPY resources/snapshot-sender*.jks ./
COPY resources/snapshot-sender*.crt ./
COPY resources/snapshot-sender/application.properties ./

ENTRYPOINT ["java", \
            "-Denvironment=local_docker", \
            "-Dapplication=snapshot_sender", \
            "-Dapp_version=latest-dev", \
            "-Dcomponent=jar_file", \
            "-jar", "snapshot-sender-latest.jar"]
