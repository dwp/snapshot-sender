FROM openjdk:8-jdk-alpine

RUN apk update
RUN apk upgrade
RUN apk add curl jq wget

EXPOSE 5443

RUN mkdir -p /opt/mock-nifi/data /opt/snapshot-sender/data
WORKDIR /opt/mock-nifi

RUN wget $(curl --silent --insecure "https://api.github.com/repos/dwp/mock-nifi/releases/latest" \
    | jq -r '.assets[0].browser_download_url') --quiet --no-check-certificate --output-document mock-nifi-latest.jar

RUN ls -la *.jar

COPY application.properties ./
COPY mock-nifi*.jks ./

ENTRYPOINT ["java", "-jar", "mock-nifi-latest.jar"]
