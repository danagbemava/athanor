SHELL := /bin/bash

.PHONY: setup lint test build ci compose-up compose-down \
	api-lint api-test api-build ui-lint ui-test ui-build worker-lint worker-test worker-build schema-validate

setup:
	./scripts/setup.sh

lint: api-lint ui-lint worker-lint schema-validate

test: api-test ui-test worker-test

build: api-build ui-build worker-build

ci: lint test build

compose-up:
	docker compose -f infra/docker-compose.yml up -d

compose-down:
	docker compose -f infra/docker-compose.yml down

api-lint:
	./scripts/api-lint.sh

api-test:
	./scripts/api-test.sh

api-build:
	./scripts/api-build.sh

ui-lint:
	./scripts/ui-lint.sh

ui-test:
	./scripts/ui-test.sh

ui-build:
	./scripts/ui-build.sh

worker-lint:
	./scripts/worker-lint.sh

worker-test:
	./scripts/worker-test.sh

worker-build:
	./scripts/worker-build.sh

schema-validate:
	./scripts/validate-schemas.sh
