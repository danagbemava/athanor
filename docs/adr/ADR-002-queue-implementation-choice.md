# ADR-002: Queue Implementation Choice
Status: Accepted
Date: 2026-03-05
## Context
Async simulation dispatch requires a practical MVP queue with migration headroom.
## Decision
Use Redis-backed queueing for early phases; keep interfaces migratable to Kafka.
## Consequences
Fast initial delivery; later migration path requires adapter boundaries.
