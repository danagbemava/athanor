SHELL := /bin/bash

.PHONY: setup lint test build ci compose-up compose-up-dev compose-down compose-logs compose-build compose-smoke \
	api-lint api-test api-build ui-lint ui-test ui-build worker-lint worker-test worker-build schema-validate

setup:
	./scripts/setup.sh

lint: api-lint ui-lint worker-lint schema-validate

test: api-test ui-test worker-test

build: api-build ui-build worker-build

ci: lint test build

compose-up:
	docker compose -f infra/docker-compose.yml up -d --build

compose-down:
	docker compose -f infra/docker-compose.yml down

compose-up-dev:
	docker compose -f infra/docker-compose.yml --profile dev-api --profile dev-worker --profile dev-ui up -d --build postgres redis minio minio-init api-dev worker-dev ui-dev

compose-logs:
	docker compose -f infra/docker-compose.yml logs -f

compose-build:
	docker compose -f infra/docker-compose.yml build api worker ui

compose-smoke:
	bash ./scripts/docker-smoke.sh

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
