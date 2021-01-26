# snapshot-sender

Sends JSON-L snapshots to Crown via a HTTPS receiver (i.e. NiFi)

# Key settings

For a sample list of settings see the `docker-compose.yml`

The following settings are typically set by Terraform and must match as follows:

| Target                   | Sample Value | Description |
|--------------------------|--------------|-------------|
|  `--s3.bucket`           | MyBucket     | Source bucket where the HTME exports and the Sender picks up from |
|  `--s3.prefix.folder`    | business-data-export/JobNumber/2019-12-31 | Where the sender will search for file to pick up |
|  `--s3.htme.root.folder` | business-data-export | The root location the htme will output into - should be the start of `s3.prefix.folder` |
|  `--s3.status.folder`    | business-sender-export | Where the sender records its progress - should be a sibling folder of `s3.htme.root.folder` |

## Makefile targets

A Makefile wraps some of the gradle and docker-compose commands to give a
more unified basic set of operations. These can be checked by running:

```
$ make help
```

| Target                       | Description |
|------------------------------|-------------|
| add-containers-to-hosts      | Update laptop hosts file with reference to containers |
| all                    | Build the jar file and then all docker images |
| build-base-images            | Build base images to avoid rebuilding frequently |
| images                 | Build all ecosystem of images |
| jar                    | Build the jar file |
| destroy                      | Bring down the hbase and other services then delete all volumes |
| dist                         | Assemble distribution files in build/dist |
| generate-developer-certs     | Generate temporary local certs and stores for the local developer containers to use |
| hbase-shell                  | Open an Hbase shell onto the running hbase container |
| integration-all              | Generate certs, build the jar and images, put up the containers, run the integration tests |
| integration-tests            | (Re-)Run the integration tests in a Docker container |
| up                           | Run the ecosystem of containers |


## Build

Ensure a JVM is installed and run gradle.

    make all

## Run full local stack

A full local stack can be run using the provided Dockerfile and Docker
Compose configuration. The Dockerfile uses a multi-stage build so no
pre-compilation is required.

    make up

The environment can completely removed including all data volumes:

    make add-containers-to-hosts

## Run full local stack and integration tests against it

    make integration-all

## Run in an IDE

Then arrange for the docker level network names and IPs to be in your hosts files:

    make add-containers-to-hosts

Create a run configuration with the environment variable `SPRING_CONFIG_LOCATION`
pointing to `resources/application-ide.properties` and a main class of
`app.SnapshotSenderApplication.kt`, run this.


## Getting logs

The services are listed in the `docker-compose.yaml` file and logs can be
retrieved for all services, or for a subset.

    docker-compose logs <container-name-or-id>

The logs can be followed so output is automatically shown:

    docker-compose logs -f  <container-name-or-id>

## UC laptop setup

This is a set of one time activities.

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

    make genrate-developer-certs

Then bring up the containers:

    make up

Note that you are at the mercy of any office wifi here as there are a
number of large docker image downloads.


### Gradle wrapper (optional - deprecated)

Update the project's gradle wrapper properties file to include a gradle
repository that can be accessed from a UC laptop. From the project root
directory:

    cd setup
    ./wrapper.sh ../gradle/wrapper/gradle-wrapper.properties

A backup of the original file will created at
`./gradle/wrapper/gradle-wrapper.properties.backup.1`
