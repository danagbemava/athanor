# Athanor Worker (Scaffold)

## Purpose

Go simulation worker skeleton for deterministic runtime execution contracts.

## Setup

- Go 1.22+
- From repo root: `make worker-test`

## Run

- `cd apps/worker && go run ./cmd/worker`

## Notes

- PCG32, decision loop semantics, and full determinism guarantees are scaffolded only.
