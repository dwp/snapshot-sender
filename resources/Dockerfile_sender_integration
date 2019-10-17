FROM dwp-gradle:latest

COPY build.gradle.kts .
COPY settings.gradle.kts .
COPY src/ ./src
RUN gradle build
