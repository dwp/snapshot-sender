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

jar: ## Build all code including tests and main jar
	./gradlew clean build test

service-aws: ## bring up aws and prepare the services.
	docker-compose up -d aws
	@{ \
		while ! docker logs aws 2> /dev/null | grep -q $(S3_READY_REGEX); do \
			echo Waiting for aws.; \
			sleep 2; \
		done; \
	}
	docker-compose up aws-init

service-dks: # bring up the data key service
	docker-compose up -d dks
	@{ \
		while ! docker exec dks cat logs/dks.out | fgrep -q "Started DataKeyServiceApplication"; do \
			echo "Waiting for dks"; \
			sleep 2; \
		done; \
	}

service-mock-nifi:
	docker-compose up -d mock-nifi

service-aws-init:
	docker-compose up -d aws-init

service-pushgateway:
	docker-compose up -d pushgateway

service-prometheus:
	docker-compose up -d prometheus

services: service-dks service-aws service-mock-nifi service-pushgateway service-prometheus service-aws-init

.PHONY: up
up: services ## Run the ecosystem of containers
	docker-compose up snapshot-sender snapshot-sender-no-exports snapshot-sender-sent

.PHONY: integration-tests
integration-tests: ## Run the integration tests
	docker-compose run sender-integration-test
