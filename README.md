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

The local stack now supports both:

- a default built-image Docker stack for reproducible full-stack bring-up
- dev-profile Docker services for source-mounted iteration

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
- `make compose-build`
- `make compose-smoke`
- `make compose-up`
- `make compose-up-dev`
- `make compose-down`

## Local Development

The default path is now Docker-first.

### Built-image stack

Start the full stack with:

- `make compose-up`
- or `docker compose -f infra/docker-compose.yml up -d --build`

Stop it with:

- `make compose-down`
- or `docker compose -f infra/docker-compose.yml down --volumes`

Entry points:

- UI: `http://127.0.0.1:3001`
- API: `http://127.0.0.1:8080`
- Postgres: `127.0.0.1:5433`
- MinIO console: `http://127.0.0.1:9001`

### Dev-profile stack

If you want source-mounted containers for API, worker, and UI:

- `make compose-up-dev`
- or `docker compose -f infra/docker-compose.yml --profile dev-api --profile dev-worker --profile dev-ui up -d --build postgres redis minio minio-init api-dev worker-dev ui-dev`

This keeps the default stack production-oriented while still supporting in-container local iteration.

## Testing

The repository keeps Docker verification in CI and exposes the same smoke test locally.

### Standard test flow

- `make lint`
- `make test`
- `make build`
- `make ci`

### Docker smoke test

Run the Docker check that CI uses:

- `make compose-smoke`

That command will:

- validate `infra/docker-compose.yml`
- build the `api`, `worker`, and `ui` images
- reset this repository's Compose stack to avoid stale container or port conflicts
- require the default local ports (`3001`, `5433`, `6379`, `8080`, `9000`, `9001`) to be available
- start the built stack
- wait for `postgres`, `redis`, `minio`, `worker`, and `api` to become healthy
- verify `http://127.0.0.1:8080/actuator/health`
- verify `http://127.0.0.1:3001/builder`
- tear the stack down automatically, including volumes

### Host-run fallback

Host-run commands are still available as a fallback if Docker debugging is needed:

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

- The dev-profile Compose path intentionally duplicates service definitions to keep the built stack and source-mounted stack separate.
- Some complex optimization and validation edge cases are still being hardened.
- Full Redis-backed browser E2E coverage exists as a manual workflow and should be automated further.
