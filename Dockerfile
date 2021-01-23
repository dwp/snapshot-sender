FROM dwp-centos-with-java:latest

RUN mkdir -p /opt/snapshot-sender/data
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
