# ADR-001: Monorepo Tooling Selection
Status: Accepted
Date: 2026-03-05
## Context
Athanor is polyglot (Java, Go, TypeScript) and needs fast scaffold delivery.
## Decision
Use plain polyglot scripts with a root Makefile and per-app native toolchains.
## Consequences
Lower setup overhead now; task graph caching can be added later if needed.
