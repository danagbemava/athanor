# Athanor

Athanor is a general-purpose decision simulation platform for deterministic execution, outcome-driven authoring, and optimization workflows.

## Phase 0 Status

This repository is in Phase 0 (Deterministic Runtime Skeleton). Core scaffolding is in place, with deterministic worker/runtime contracts, Spring Modulith API bootstrap, schema contracts, and golden determinism harness coverage.

## Architecture Map

- `apps/api`: Spring Boot + Spring Modulith API bootstrap (`scenario`, `compiler`, `jobs`, `telemetry` modules)
- `apps/ui`: Nuxt 3 Scenario Studio scaffold with lint/test/build gates
- `apps/worker`: Go deterministic simulation worker (PCG32 RNG + traversal/effects loop)
- `packages/spec`: Canonical JSON schemas, fixture validation, and golden determinism corpus
- `infra`: Local stack via Docker Compose
- `docs/adr`: Architecture Decision Records

## Prerequisites

- Docker + Docker Compose
- Go 1.22+
- Java 21+
- Node.js 22+
- `make`

## Bootstrap Commands

- `make setup`
- `make lint`
- `make test`
- `make build`
- `make ci`
- `docker compose -f infra/docker-compose.yml config`
- `docker compose -f infra/docker-compose.yml up -d`

## What Works Now

- API compiles/tests/builds through Gradle wrapper gates.
- Spring Modulith module structure is verified by test (`ApplicationModules.verify()`).
- Worker runs deterministic simulations with seeded PCG32 and supports decision/chance/terminal traversal with state effects.
- Golden test harness executes fixture scenarios and checks deterministic outcome/step expectations.
- Schema validation checks metadata plus valid/invalid fixture contracts.

## Current Limitations

- API modules are scaffolded and do not yet implement production domain endpoints/workflows.
- UI remains a scaffold shell (no graph editor/runtime analytics yet).
- Docker Compose `api` and `worker` services are still placeholder runtime commands, not full app launches.
- End-to-end compiler-to-worker integration is still pending (Phase 1+ work).
