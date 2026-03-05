# Athanor API

## Purpose

Spring Boot API service for scenario authoring, compilation orchestration, and telemetry APIs.

## Setup

- Java 21+
- From repo root: `make api-build`

## Run

- `cd apps/api && ./gradlew bootRun`

## Quality Gates

- Lint gate: `make api-lint` (Gradle `compileJava`)
- Test gate: `make api-test` (Gradle `test`)
- Build gate: `make api-build` (Gradle `build -x test`)

## Notes

- Generated with Spring Initializr using Gradle wrapper.
- Modulith boundaries and domain modules are planned in later phases.
