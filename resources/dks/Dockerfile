FROM openjdk:8-jdk-alpine

RUN apk update
RUN apk upgrade
RUN apk add curl jq wget

EXPOSE 8443

RUN mkdir -p /opt/data-key-service/data /opt/data-key-service/logs /opt/snapshot-sender/data
WORKDIR /opt/data-key-service

RUN curl --silent --insecure "https://api.github.com/repos/dwp/data-key-service/releases/latest" \
             | jq -r '.assets[0].browser_download_url' >> dks_version

RUN wget --input-file dks_version --quiet --no-check-certificate --output-document dks-latest.jar

COPY application.properties ./application.properties
COPY dks*.jks dks*.crt ./

RUN chmod a+rwx -R /opt/data-key-service /opt/snapshot-sender/

ENTRYPOINT ["java", "-Ddks.log.directory=/opt/data-key-service/logs", "-jar", "dks-latest.jar"]
