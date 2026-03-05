# ADR-006: Go for Simulation Workers
Status: Accepted
Date: 2026-03-05
## Context
Simulation workload is compute-heavy and concurrency-sensitive.
## Decision
Use Go for worker implementation and goroutine-based concurrency per simulation run.
## Consequences
Throughput and deployment simplicity improve; cross-language contract discipline is required.
