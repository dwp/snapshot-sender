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

default: help

.PHONY: help
help:
	@grep -E '^[a-zA-Z_-]+:.*?## .*$$' $(MAKEFILE_LIST) | sort | awk 'BEGIN {FS = ":.*?## "}; {printf "\033[36m%-30s\033[0m %s\n", $$1, $$2}'

build-jar: ## Build the jar file
	./gradlew build

dist: ## Assemble distribution files in build/dist
	./gradlew assembleDist

add-containers-to-hosts: ## Update laptop hosts file with reference to containers
	./resources/add-containers-to-hosts.sh;

generate-developer-certs:  ## Generate temporary local certs and stores for the local developer containers to use
	pushd resources && ./generate-developer-certs.sh && popd

build-all: build-jar build-images ## Build the jar file and then all docker images

build-base-images:
	@{ \
		pushd resources; \
		docker build --tag dwp-centos-with-java:latest --file Dockerfile_centos_java . ; \
		docker build --tag dwp-pthon-preinstall:latest --file Dockerfile_python_preinstall . ; \
		popd; \
	}

build-images: build-base-images ## Build all ecosystem of images
	@{ \
		docker-compose build hbase hbase-populate s3-dummy s3-bucket-provision dks-standalone-http dks-standalone-https hbase-to-mongo-export; \
	}

up: ## Run the ecosystem of containers
	@{ \
		docker-compose up -d hbase hbase-populate s3-dummy s3-bucket-provision dks-standalone-http dks-standalone-https; \
		echo "Waiting for data to arive in s3" && sleep 10; \
		docker-compose up -d hbase-to-mongo-export; \
	}

up-all: build-images up

hbase-shell: ## Open an Hbase shell onto the running hbase container
	@{ \
		docker exec -it hbase hbase shell; \
	}

destroy: ## Bring down the hbase and other services then delete all volumes
	docker-compose down
	docker network prune -f
	docker volume prune -f
