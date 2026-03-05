# ADR-005: Spring Modulith Module Boundaries
Status: Accepted
Date: 2026-03-05
## Context
API complexity requires explicit boundaries for scenario, compiler, jobs, and telemetry.
## Decision
Define modules with event-driven cross-module communication as the default.
## Consequences
Boundary clarity improves maintainability; module contracts must be enforced in tests.
