# ADR-003: Deterministic RNG Algorithm (PCG32)
Status: Accepted
Date: 2026-03-05
## Context
Deterministic simulation reproducibility depends on stable seeded PRNG semantics.
## Decision
Adopt PCG32 contract for worker RNG with explicit versioning in bundle headers.
## Consequences
Determinism improves; future algorithm changes require versioned compatibility strategy.
