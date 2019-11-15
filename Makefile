SHELL:=bash

snapshot_sender_version=$(shell cat ./gradle.properties | cut -f2 -d'=')
aws_default_region=eu-west-2
aws_secret_access_key=DummyKey
aws_access_key_id=DummyKey
s3_bucket=demobucket
s3_prefix_folder=test-exporter
data_key_service_url=http://dks-standalone-http:8080
data_key_service_url_ssl=https://dks-standalone-https:8443
follow_flag=--follow
S3_READY_REGEX=^Ready\.$

default: help

.PHONY: help
help:
	@grep -E '^[a-zA-Z_-]+:.*?## .*$$' $(MAKEFILE_LIST) | sort | awk 'BEGIN {FS = ":.*?## "}; {printf "\033[36m%-30s\033[0m %s\n", $$1, $$2}'

build-jar: ## Build all code including tests and main jar
	gradle clean build test

dist: ## Assemble distribution files in build/dist
	gradle assembleDist

add-containers-to-hosts: ## Update laptop hosts file with reference to containers
	./resources/add-containers-to-hosts.sh;

generate-developer-certs:  ## Generate temporary local certs and stores for the local developer containers to use
	pushd resources && ./generate-developer-certs.sh && popd

.PHONY: build-all
build-all: build-jar build-images ## Build the jar file and then all docker images

.PHONY: build-base-images
build-base-images: ## Build base images to avoid rebuilding frequently
	@{ \
		pushd resources; \
		docker build --tag dwp-centos-with-java:latest --file Dockerfile_centos_java . ; \
		docker build --tag dwp-python-preinstall:latest --file Dockerfile_python_preinstall . ; \
		popd; \
		docker build --tag dwp-gradle:latest --file resources/Dockerfile_gradle . ; \
	}

.PHONY: build-images
build-images: build-base-images ## Build all ecosystem of images
	@{ \
		docker-compose build hbase hbase-populate s3-dummy s3-bucket-provision dks-standalone-http dks-standalone-https hbase-to-mongo-export mock-nifi; \
		docker-compose build --no-cache snapshot-sender; \
	}

.PHONY: up
up: ## Run the ecosystem of containers
	@{ \
		docker-compose up -d hbase s3-dummy dks-standalone-http dks-standalone-https mock-nifi; \
		echo "Waiting for services"; \
		while ! docker logs s3-dummy 2> /dev/null | grep -q $(S3_READY_REGEX); do \
			echo "Waiting for s3-dummy..."; \
			sleep 2; \
		done; \
		docker-compose up hbase-populate s3-bucket-provision; \
		docker-compose up hbase-to-mongo-export; \
		docker-compose up hbase-to-mongo-export-claimant-event; \
		docker-compose up snapshot-sender; \
	}

.PHONY: up-all
up-all: build-images up

.PHONY: hbase-shell
hbase-shell: ## Open an Hbase shell onto the running hbase container
	@{ \
		docker exec -it hbase hbase shell; \
	}

.PHONY: destroy
destroy: ## Bring down the hbase and other services then delete all volumes
	docker-compose down
	docker network prune -f
	docker volume prune -f

.PHONY: integration-tests
integration-tests: ## Run the integration tests
	docker-compose build --no-cache sender-integration-test
	docker-compose run sender-integration-test

.PHONY: integration-all
integration-all: destroy build-all up integration-tests ## Generate certs, build the jar and images, put up the containers, run the integration tests
