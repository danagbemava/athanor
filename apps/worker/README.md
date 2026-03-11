# Athanor Worker

## Purpose

Go simulation worker for deterministic runtime execution contracts.

It supports two execution styles:

- CLI mode for direct bundle + request execution
- Redis service mode for long-lived dispatch consumption, progress emission, and result delivery

## Setup

- Go 1.22+
- From repo root: `make worker-test`

## Run

- `cd apps/worker && go run ./cmd/worker`
- `cd apps/worker && go run ./cmd/worker run --bundle /path/to/bundle.json --request /path/to/request.json`
- `cd apps/worker && ATHANOR_REDIS_ADDR=127.0.0.1:6379 ATHANOR_S3_ENDPOINT=http://127.0.0.1:9000 ATHANOR_S3_BUCKET=athanor-bundles ATHANOR_S3_ACCESS_KEY=minioadmin ATHANOR_S3_SECRET_KEY=minioadmin go run ./cmd/worker serve --cache-dir .worker-cache`

## Notes

- The CLI consumes executable bundle JSON plus a worker execution request and writes worker execution result JSON to stdout.
- Redis mode consumes execution requests from a Redis stream, reads bundle content from S3-compatible object storage by `bundle_hash`, caches bundles locally, and emits progress/completion/failure events back onto Redis for the API to ingest.
- Execution currently supports `random-v1` and `scripted-v1` policies in `analytics` and `optimization` modes.
