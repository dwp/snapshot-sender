# snapshot-sender
Sends JSON-L snapshots to Crown

## Makefile targets

`generate-developer-certs` - One-time activity to generate temporary local certs and stores for the local developer containers to use


A Makefile wraps some of the gradle and docker-compose commands to give a
more unified basic set of operations. These can be checked by running:

```
$ make help
```

## Build

Ensure a JVM is installed and run gradle.

    make build

## Run full local stack

A full local stack can be run using the provided Dockerfile and Docker
Compose configuration. The Dockerfile uses a multi-stage build so no
pre-compilation is required.

    make up-all

The environment can be stopped without losing any data:

    make down

Or completely removed including all data volumes:

    make destroy


## Run in an IDE

First bring up the containerized versions of hbase, aws and dks:

    make up-ancillary

Then arrange for their docker level network names and IPs to be in your hosts files:

    make hosts

Create a run configuration with the environment variable `SPRING_CONFIG_LOCATION`
pointing to `resources/application-ide.properties` and a main class of
`app.UcHistoricDataImporterApplication`, run this.


## Getting logs

The services are listed in the `docker-compose.yaml` file and logs can be
retrieved for all services, or for a subset.

    docker-compose logs aws-s3

The logs can be followed so new lines are automatically shown.

    docker-compose logs -f aws-s3

## UC laptops

This is a one time activity.

### Java

Install a JDK underneath your home directory, one way to do this is with
[sdkman](https://sdkman.io).

Once sdkman is installed and initialised you can install a jdk with e.g.:

    sdk install java 8.0.222-zulu

Make sure that JAVA_HOME is set after this completes, start a new shell first
but if it is still not set you may need to add a line to your `.bashrc` thus:

    export JAVA_HOME=/Users/<your-username>/.sdkman/candidates/java/current

then have it set in your current session by executing

    exec bash

### Gradle wrapper

Update the project's gradle wrapper properties file to include a gradle
repository that can be accessed from a UC laptop. From the project root
directory:

    cd setup
    ./wrapper.sh ../gradle/wrapper/gradle-wrapper.properties

A backup of the original file will created at
`./gradle/wrapper/gradle-wrapper.properties.backup.1`

### Gradle.org certificates

The gradle.org certificate chain must be inserted into your local java
truststore:

    cd setup # if not already there.
    ./certificates.sh path-to-truststore
    # e.g.
    ./certificates.sh $JAVA_HOME/jre/lib/security/cacerts

..again a backup will be created at (in the example above)

`$JAVA_HOME/jre/lib/security/cacerts.backup.1`.

### Run a gradle build

From the project root first ensure no gradle daemons are running.

    gradle --stop

Then run a gradle build

    gradle build

To ensure the dockerized setup is functional, first generate the self-signed
certificates needed for local development (from the project root):

    make genrate-developer-certs.sh


Then bring up the containers:

    make up

Note that you are at the mercy of the quarry house wifi here as there are a
number of large docker image downloads.
