SHELL:=bash

S3_READY_REGEX=^Ready\.$

default: help

.PHONY: help
help:
	@grep -E '^[a-zA-Z_-]+:.*?## .*$$' $(MAKEFILE_LIST) | sort | awk 'BEGIN {FS = ":.*?## "}; {printf "\033[36m%-30s\033[0m %s\n", $$1, $$2}'

.PHONY: bootstrap
bootstrap: ## Bootstrap local environment for first use
	make git-hooks

.PHONY: git-hooks
git-hooks: ## Set up hooks in .git/hooks
	@{ \
		HOOK_DIR=.git/hooks; \
		for hook in $(shell ls .githooks); do \
			if [ ! -h $${HOOK_DIR}/$${hook} -a -x $${HOOK_DIR}/$${hook} ]; then \
				mv $${HOOK_DIR}/$${hook} $${HOOK_DIR}/$${hook}.local; \
				echo "moved existing $${hook} to $${hook}.local"; \
			fi; \
			ln -s -f ../../.githooks/$${hook} $${HOOK_DIR}/$${hook}; \
		done \
	}

build-jar: ## Build all code including tests and main jar
	gradle clean build test

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
	docker-compose build

.PHONY: up
up: ## Run the ecosystem of containers
	@{ \
		docker-compose up -d aws; \
		while ! docker logs aws 2> /dev/null | grep -q $(S3_READY_REGEX); do \
			echo "Waiting for aws..."; \
			sleep 2; \
		done; \
#		docker exec -i hbase hbase shell <<< "create_namespace 'claimant_advances'"; \
#		docker exec -i hbase hbase shell <<< "create_namespace 'core'"; \
#		docker exec -i hbase hbase shell <<< "create_namespace 'quartz'"; \
#		docker-compose up -d dks-standalone-http ; \
		docker-compose up -d dks-standalone-https ; \
		docker-compose up -d mock-nifi; \
		while ! docker exec dks-standalone-https cat logs/dks.out | fgrep -q "Started DataKeyServiceApplication"; do \
		  echo "Waiting for dks"; \
		  sleep 2; \
		done; \
		docker-compose up aws-init; \
		docker-compose up snapshot-sender snapshot-sender-no-exports; \
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
	docker-compose run sender-integration-test
