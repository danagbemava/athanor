# Athanor Knowledge Graph

This artifact shows how the Scenario Studio UI, Spring Modulith API, Go worker, shared contracts, and local infrastructure fit together.
Use it for onboarding and for tracing how scenarios move from authoring to validation, compilation, simulation, optimization, and analytics.

## How To Read This

Read the structural view first, then the runtime sections.
The structural view shows ownership boundaries; the runtime sections show request, job, bundle, event, trace, and telemetry flows.

The authoritative graph source lives in `docs/architecture/knowledge-graph.dot`.

## Legend

- `HTTP`: synchronous request/response edge
- `Internal`: in-process module/service call
- `Async`: queue or event-stream handoff
- `Store`: persistent write
- `Read`: persistent or object-store read
- `Poll`: UI status or trace polling

## Structural Architecture

```mermaid
flowchart LR
  subgraph UI["UI (Scenario Studio)"]
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
    WorkerEntry["cmd/worker"]
    WorkerCli["internal/cli"]
    WorkerService["internal/service"]
    WorkerEngine["internal/engine"]
    WorkerAgent["internal/agent"]
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
  Sim --> Scenario
  Opt --> Postgres
  Telemetry --> Postgres
  WorkerEntry --> WorkerCli
  WorkerCli --> WorkerService
  WorkerCli --> WorkerEngine
  WorkerCli --> WorkerAgent
  WorkerService --> Redis
  WorkerService --> MinIO
  WorkerService --> WorkerEngine
  WorkerService --> WorkerAgent
  WorkerEngine --> WorkerContracts
  WorkerEngine --> WorkerRng

  Schemas --- Scenario
  Schemas --- Compiler
  Schemas --- WorkerContracts
  Golden --- Compiler
  Golden --- WorkerEngine
  Fixtures --- WorkerContracts
```

## Async Simulation Runtime

```mermaid
flowchart LR
  UI["UI: useSimulation"] -->|"POST /simulate"| Submit["SimulationJobController"]
  Submit --> Job["JobService"]
  Job --> Compiler["CompilerService"]
  Compiler -->|"Store compiled bundle"| BundleStore["MinIO / S3 bundle store"]
  Job -->|"Publish dispatch"| RedisDispatch["Redis dispatch stream"]
  Worker["Worker: internal/service"] -->|"Consume dispatch"| RedisDispatch
  Worker -->|"Read bundle by bundle_hash"| BundleStore
  Worker --> Engine["Worker: internal/engine"]
  Engine -->|"Run deterministic traces/results"| Worker
  Worker -->|"Publish progress / complete / failed"| RedisEvents["Redis event stream"]
  RedisEvents -->|"Ingest job updates"| Job
  Job -->|"Store summary / result key"| Postgres["Postgres"]
  Worker -->|"Store execution result"| ResultStore["MinIO / S3 result store"]
  UI -->|"Poll /runs/{runId}"| Job
  UI -->|"GET /runs/{runId}/trace-runs"| Job
  Job -->|"Read paginated traces"| ResultStore
```

## Optimization And Telemetry

```mermaid
flowchart LR
  UIOpt["UI: useOptimization"] -->|"POST /optimize"| OptController["OptimizationController"]
  OptController --> OptService["OptimizationService"]
  OptService --> Scenario["ScenarioService"]
  OptService --> Validator["ScenarioGraphValidator"]
  OptService --> Compiler["CompilerService"]
  OptService --> Executor["SimulationBatchExecutor"]
  Compiler -->|"Store orphan bundles"| MinIO["MinIO / S3"]
  Executor -->|"Evaluate candidate outcomes"| OptService
  UIOpt -->|"Poll /optimize/{jobId}"| OptController
  UIOpt -->|"POST /optimize/{jobId}/apply"| OptController
  OptService -->|"Create new scenario version"| Scenario
  Scenario --> Postgres["Postgres"]

  Job["JobService"] -->|"Completed simulation summary"| Telemetry["TelemetryService"]
  Telemetry -->|"Aggregate scenario analytics"| Postgres
  UIAnalytics["UI: useAnalytics"] -->|"GET /scenarios/{id}/analytics"| TelemetryController["TelemetryController"]
  TelemetryController --> Telemetry
```

## Notes

- The UI uses the async simulation job path (`POST /simulate`) as its primary execution flow.
- The API also exposes a synchronous simulation endpoint (`POST /scenarios/{id}/simulate`) as an alternate path.
- `packages/spec` defines the canonical contract boundary, while the live runtime still uses concrete in-repo model shapes and compiled payload conventions.
