# Athanor Knowledge Graph

## How To Read This

This document has three graph sections:

- A structural architecture view that shows ownership boundaries.
- A runtime view that shows how requests, jobs, bundles, worker events, traces, and analytics move through the system.

The authoritative graph source lives in `docs/architecture/knowledge-graph.dot`.

## Legend

- `HTTP`: synchronous request/response edge
- `Internal`: in-process module/service call
- `Async`: queue or event-stream handoff
- `Store`: persistent write
- `Read`: persistent or object-store read
- `Poll`: UI status or trace polling

## System Overview

```mermaid
flowchart LR
  subgraph UI["UI (Nuxt Scenario Studio)"]
    Builder["pages/builder.vue"]
    SharedState["shared-state.ts"]
    Graph["useScenarioGraph"]
    Validation["useValidation"]
    Scenarios["useScenarios"]
    Simulation["useSimulation"]
    Optimization["useOptimization"]
    Analytics["useAnalytics"]
    Activity["useActivityFeed"]
  end

  subgraph API["API (Spring Modulith)"]
    Scenario["scenario module"]
    Compiler["compiler module"]
    Jobs["jobs module"]
    Sim["simulation module"]
    Opt["optimization module"]
    Telemetry["telemetry module"]
  end

  subgraph Worker["Worker (Go runtime)"]
    WorkerService["internal/service"]
    WorkerEngine["internal/engine"]
    WorkerContracts["internal/contracts"]
    WorkerRng["internal/rng"]
  end

  subgraph Spec["Shared Contracts"]
    Schemas["packages/spec/schemas"]
    Fixtures["packages/spec/fixtures"]
    Golden["packages/spec/golden"]
  end

  subgraph Infra["Infrastructure"]
    Postgres["Postgres"]
    Redis["Redis"]
    MinIO["MinIO / S3"]
  end

  Builder --> SharedState
  Builder --> Graph
  Builder --> Validation
  Builder --> Scenarios
  Builder --> Simulation
  Builder --> Optimization
  Builder --> Analytics
  Builder --> Activity

  Scenarios -->|"HTTP"| Scenario
  Validation -->|"HTTP"| Scenario
  Simulation -->|"HTTP"| Jobs
  Optimization -->|"HTTP"| Opt
  Analytics -->|"HTTP"| Telemetry

  Scenario --> Postgres
  Compiler --> MinIO
  Jobs --> Postgres
  Jobs --> Redis
  Jobs --> MinIO
  Opt --> Postgres
  Telemetry --> Postgres
  WorkerService --> Redis
  WorkerService --> MinIO
  WorkerService --> WorkerEngine
  WorkerEngine --> WorkerContracts
  WorkerEngine --> WorkerRng

  Schemas --- Scenario
  Schemas --- Compiler
  Schemas --- WorkerContracts
  Golden --- Compiler
  Golden --- WorkerEngine
```

## Async Simulation Runtime

## Optimization And Analytics

## Notes

- The UI uses the async simulation job path (`POST /simulate`) as its primary execution flow.
- The API also exposes a synchronous simulation endpoint (`POST /scenarios/{id}/simulate`) as an alternate path.
- `packages/spec` defines the canonical contract boundary, while the live runtime still uses concrete in-repo model shapes and compiled payload conventions.
