FROM dwp-centos-with-java:latest

RUN mkdir -p /opt/snapshot-sender/data
WORKDIR /opt/snapshot-sender

COPY build/libs/snapshot-sender-0.0.1.jar ./snapshot-sender-latest.jar

COPY resources/snapshot-sender*.jks ./
COPY resources/snapshot-sender*.crt ./

ENTRYPOINT ["sh", "-c", "java -Dcorrelation_id=${CORRELATION_ID} -Denvironment=${ENVIRONMENT} -Dapplication=${APPLICATION} -Dapp_version=${APP_VERSION} -Dcomponent=${COMPONENT} -jar snapshot-sender-latest.jar \"$@\"", "--"]
