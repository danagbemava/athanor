# Athanor Worker

## Purpose

Go simulation worker for deterministic runtime execution contracts.

## Setup

- Go 1.22+
- From repo root: `make worker-test`

## Run

- `cd apps/worker && go run ./cmd/worker`
- `cd apps/worker && go run ./cmd/worker run --bundle /path/to/bundle.json --request /path/to/request.json`
- `cd apps/worker && ATHANOR_REDIS_ADDR=127.0.0.1:6379 go run ./cmd/worker serve --cache-dir .worker-cache`

## Notes

- The CLI consumes executable bundle JSON plus a worker execution request and writes worker execution result JSON to stdout.
- Redis mode consumes execution requests from a Redis stream, caches bundles locally by `bundle_hash`, and emits progress/completion/failure events back onto Redis for the API to ingest.
- Execution currently supports `random-v1` and `scripted-v1` policies in `analytics` and `optimization` modes.
