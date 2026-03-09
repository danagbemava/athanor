# Athanor Worker

## Purpose

Go simulation worker for deterministic runtime execution contracts.

## Setup

- Go 1.22+
- From repo root: `make worker-test`

## Run

- `cd apps/worker && go run ./cmd/worker`
- `cd apps/worker && go run ./cmd/worker run --bundle /path/to/bundle.json --request /path/to/request.json`

## Notes

- The CLI consumes executable bundle JSON plus a worker execution request and writes worker execution result JSON to stdout.
- Execution currently supports `random-v1` and `scripted-v1` policies in `analytics` and `optimization` modes.
