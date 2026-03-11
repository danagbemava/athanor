# Athanor

Athanor is a general-purpose decision simulation platform for deterministic execution, outcome-driven authoring, and optimization workflows.

## Current Status

The project is beyond the initial runtime skeleton. The repository now contains a working end-to-end path for:

- scenario authoring and versioning
- bundle compilation and storage
- deterministic simulation runs
- optimization jobs
- Redis-backed worker dispatch and progress ingestion
- UI-driven simulation and optimization workflows

The local stack is still partially split between Docker infrastructure and host-run app processes. `infra/docker-compose.yml` provisions Postgres, Redis, and MinIO, but the `api` and `worker` services in that compose file are still placeholders rather than the real app launch commands.

## Architecture Map

- `apps/api`: Spring Boot + Spring Modulith API for scenario authoring, compilation, simulation jobs, optimization jobs, telemetry, bundle storage, and Redis worker orchestration
- `apps/ui`: Nuxt 3 Scenario Studio for graph authoring, validation, simulation, optimization, and result inspection
- `apps/worker`: Go deterministic simulation worker with CLI execution plus Redis service mode backed by object storage bundle fetch
- `packages/spec`: canonical JSON schemas, fixture validation, and golden determinism corpus
- `infra`: Local stack via Docker Compose
- `docs/adr`: Architecture Decision Records

## Prerequisites

- Docker + Docker Compose
- Go 1.22+
- Java 21+
- Node.js 22+
- `make`

## Common Commands

- `make setup`
- `make lint`
- `make test`
- `make build`
- `make ci`
- `docker compose -f infra/docker-compose.yml config`
- `docker compose -f infra/docker-compose.yml up -d`

## Local Development

Typical local development currently looks like this:

1. Start infrastructure with `docker compose -f infra/docker-compose.yml up -d`.
2. Run the API on the host from `apps/api`.
3. Run the worker on the host from `apps/worker`.
4. Run the UI on the host from `apps/ui`.

Example host-run commands:

- API:
  `cd apps/api && ATHANOR_REDIS_HOST=127.0.0.1 ATHANOR_REDIS_PORT=6379 ATHANOR_WORKER_RUNTIME_MODE=redis ATHANOR_S3_ENDPOINT=http://127.0.0.1:9000 ATHANOR_S3_BUCKET=athanor-bundles ATHANOR_S3_ACCESS_KEY=minioadmin ATHANOR_S3_SECRET_KEY=minioadmin ./gradlew bootRun`
- Worker:
  `cd apps/worker && ATHANOR_REDIS_ADDR=127.0.0.1:6379 ATHANOR_S3_ENDPOINT=http://127.0.0.1:9000 ATHANOR_S3_BUCKET=athanor-bundles ATHANOR_S3_ACCESS_KEY=minioadmin ATHANOR_S3_SECRET_KEY=minioadmin go run ./cmd/worker serve --cache-dir .worker-cache`
- UI:
  `cd apps/ui && NUXT_PUBLIC_API_BASE_URL=http://127.0.0.1:8080 npm run dev -- --host 127.0.0.1 --port 3001`

## What Works Now

- API compile, test, and build gates run through Gradle wrapper scripts.
- Spring Modulith module structure is verified by `AthanorApiModularityTests`.
- Scenario authoring, validation, versioning, and bundle compilation are implemented in the API.
- Bundles can be stored in S3-compatible storage, with MinIO used locally.
- The worker supports deterministic CLI execution and Redis service mode.
- Simulation jobs report progress and completion back through the API job model.
- Optimization jobs can be submitted and applied back onto scenario versions.
- The UI can author graphs, run simulations, run optimizations, and inspect traces and outcomes.
- Golden worker determinism and schema validation coverage remain in place.

## Current Limitations

- Docker Compose still uses placeholder `api` and `worker` commands instead of booting the real services directly.
- Some complex optimization and validation edge cases are still being hardened.
- Full Redis-backed browser E2E coverage exists as a manual workflow and should be automated further.
