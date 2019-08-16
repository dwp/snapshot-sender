FROM dwp-centos-with-java:latest

RUN mkdir -p /opt/snapshot-sender/data
ENV SPRING_CONFIG_LOCATION application-htme.properties
WORKDIR /opt/snapshot-sender

COPY build/libs/snapshot-sender-0.0.1.jar ./snapshot-sender-latest.jar
RUN ls -la *.jar

COPY sender*.jks sender*.crt ./

ENTRYPOINT ["sh", "-c", "java -jar snapshot-sender-latest.jar \"$@\"", "--"]
