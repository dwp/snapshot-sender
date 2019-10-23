FROM dwp-centos-with-java:latest

RUN mkdir -p /opt/snapshot-sender/data
WORKDIR /opt/snapshot-sender

COPY build/libs/snapshot-sender-0.0.1.jar ./snapshot-sender-latest.jar

COPY resources/snapshot-sender*.jks ./
COPY resources/snapshot-sender*.crt ./

ENTRYPOINT ["sh", "-c", "java -jar snapshot-sender-latest.jar \"$@\"", "--"]
