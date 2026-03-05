# Athanor API

## Purpose

Spring Boot + Spring Modulith API service for scenario authoring, compilation orchestration, jobs, and telemetry APIs.

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
- Initial Modulith modules are scaffolded under:
  - `com.athanor.api.scenario`
  - `com.athanor.api.compiler`
  - `com.athanor.api.jobs`
  - `com.athanor.api.telemetry`
- Structural enforcement runs via `AthanorApiModularityTests`.
