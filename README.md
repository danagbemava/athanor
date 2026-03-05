# Athanor

Athanor is a general-purpose decision simulation platform for deterministic execution, outcome-driven authoring, and optimization workflows.

## Phase 0 Status

This repository is scaffolded for Phase 0 (Deterministic Runtime Skeleton). It provides structure, contracts, and placeholders. Full runtime functionality is intentionally deferred.

## Architecture Map

- `apps/api`: Spring Boot + Spring Modulith API placeholder
- `apps/ui`: Nuxt 3 Scenario Studio placeholder
- `apps/worker`: Go simulation worker skeleton
- `packages/spec`: Canonical JSON schemas and golden fixtures
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
- `make ci`
- `docker compose -f infra/docker-compose.yml config`
- `docker compose -f infra/docker-compose.yml up -d`

## Current Limitations

- API and UI are scaffold placeholders and do not expose production endpoints/features.
- Worker runtime logic is intentionally skeletal and does not implement full deterministic semantics.
- Golden tests validate fixture loading and contract shape only; full determinism assertions are deferred.
