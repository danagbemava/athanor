# Athanor API

## Purpose

Spring Boot + Spring Modulith API service for:

- scenario authoring and versioning
- graph validation
- bundle compilation and storage
- simulation job submission and polling
- optimization job submission and polling
- Redis-backed worker dispatch and event ingestion
- telemetry and analytics snapshots

## Setup

- Java 21+
- From repo root: `make api-build`

## Run

- `cd apps/api && ./gradlew bootRun`

Useful local runtime environment:

- `ATHANOR_REDIS_HOST` / `ATHANOR_REDIS_PORT`
- `ATHANOR_WORKER_RUNTIME_MODE=cli|redis`
- `ATHANOR_S3_ENDPOINT`
- `ATHANOR_S3_BUCKET`
- `ATHANOR_S3_ACCESS_KEY`
- `ATHANOR_S3_SECRET_KEY`
- `ATHANOR_DB_URL`
- `ATHANOR_DB_USERNAME`
- `ATHANOR_DB_PASSWORD`

Example Redis-backed local run:

- `cd apps/api && ATHANOR_REDIS_HOST=127.0.0.1 ATHANOR_REDIS_PORT=6379 ATHANOR_WORKER_RUNTIME_MODE=redis ATHANOR_S3_ENDPOINT=http://127.0.0.1:9000 ATHANOR_S3_BUCKET=athanor-bundles ATHANOR_S3_ACCESS_KEY=minioadmin ATHANOR_S3_SECRET_KEY=minioadmin ./gradlew bootRun`

## Quality Gates

- Lint gate: `make api-lint` (Gradle `compileJava`)
- Test gate: `make api-test` (Gradle `test`)
- Build gate: `make api-build` (Gradle `build -x test`)

## Notes

- Generated with Spring Initializr using Gradle wrapper and then extended into a real multi-module service.
- Main Modulith modules live under:
  - `com.athanor.api.scenario`
  - `com.athanor.api.compiler`
  - `com.athanor.api.jobs`
  - `com.athanor.api.optimization`
  - `com.athanor.api.simulation`
  - `com.athanor.api.telemetry`
- Structural enforcement runs via `AthanorApiModularityTests`.
- Local development usually pairs the API with Redis and MinIO from `infra/docker-compose.yml`.
