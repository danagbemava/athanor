# Athanor Knowledge Graph

This artifact shows how the Scenario Studio UI, Spring Modulith API, Go worker, shared contracts, and local infrastructure fit together.
Use it for onboarding and for tracing how scenarios move from authoring to validation, compilation, simulation, optimization, and analytics.
The UI surface is split across authoring controls plus result cards for simulation and optimization, all backed by shared studio state, activity feed entries, and local history snapshots.

## How To Read This

Read the structural view first, then the runtime sections.
The structural view shows ownership boundaries; the runtime sections show request, job, bundle, event, trace, and telemetry flows.

The authoritative graph source lives in `docs/architecture/knowledge-graph.dot`.

## View / Render

You can view the knowledge graph in two ways:

1. Read the Mermaid diagrams in this Markdown file directly.
2. Render the Graphviz source in `docs/architecture/knowledge-graph.dot`.
3. Open the standalone searchable viewer in `docs/architecture/knowledge-graph.html`.

From the repository root, generate an SVG with:

```bash
dot -Tsvg docs/architecture/knowledge-graph.dot -o /tmp/athanor-knowledge-graph.svg
```

Generate a PNG instead with:

```bash
dot -Tpng docs/architecture/knowledge-graph.dot -o /tmp/athanor-knowledge-graph.png
```

If `dot` is not installed, install Graphviz first, then rerun the command.

## Searchable Viewer

The searchable viewer is generated from the DOT source and committed as repo artifacts:

- `docs/architecture/knowledge-graph-data.json`
- `docs/architecture/knowledge-graph.html`

Regenerate both artifacts from the repository root with:

```bash
node scripts/generate-knowledge-graph-viewer.mjs
```

Open the generated HTML file directly in a browser:

```bash
open docs/architecture/knowledge-graph.html
```

The viewer embeds the graph data for direct `file://` use and keeps a JSON fetch fallback for hosted or copied artifacts.

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
    QuickActions["QuickActionsCard"]
    SimulationCard["SimulationResultsCard"]
    OptimizationCard["OptimizationResultsCard"]
    SharedState["shared-state.ts"]
    Graph["useScenarioGraph"]
    Validation["useValidation"]
    Scenarios["useScenarios"]
    Simulation["useSimulation"]
    Optimization["useOptimization"]
    Analytics["useAnalytics"]
    Activity["useActivityFeed"]
    LocalHistory["scenarioHistory / validationHistory"]
    AuthoringResult["scenarioResponse / validationResponse"]
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
    WorkerCache["local bundle cache"]
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
    MinioInit["minio-init"]
  end

  Builder --> SharedState
  Builder --> Graph
  Builder --> Validation
  Builder --> Scenarios
  Builder --> Simulation
  Builder --> Optimization
  Builder --> Analytics
  Builder --> Activity
  Builder --> QuickActions
  Builder --> SimulationCard
  Builder --> OptimizationCard

  Scenarios -->|"HTTP"| Scenario
  Validation -->|"HTTP"| Scenario
  Validation -->|"Local structural preflight"| SharedState
  Simulation -->|"HTTP"| Jobs
  Optimization -->|"HTTP"| Opt
  Analytics -->|"HTTP"| Telemetry
  Scenarios --> AuthoringResult
  Validation --> AuthoringResult
  Scenarios --> Activity
  Validation --> Activity
  Scenarios --> LocalHistory
  Validation --> LocalHistory
  Optimization --> Simulation
  Optimization --> Analytics
  Simulation --> SimulationCard

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
  WorkerService --> WorkerCache
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
  MinioInit --> MinIO
```

## Async Simulation Runtime

### Compilation Handoff

```mermaid
flowchart LR
  Source["ScenarioService latest version"] --> Compiler["CompilerService"]
  Compiler --> Validator["ScenarioGraphValidator"]
  Compiler --> Bundle["Compiled bundle + bundle_hash"]
  Bundle --> Store["MinIO / S3 bundle store"]
  Store --> Consumers["JobService and worker/runtime consumers"]
```

```mermaid
flowchart LR
  UI["UI: useSimulation"] -->|"POST /simulate"| Submit["SimulationJobController"]
  Submit --> Job["JobService"]
  Job --> Compiler["CompilerService"]
  Compiler -->|"Store compiled bundle"| BundleStore["MinIO / S3 bundle store"]
  Job -->|"Publish dispatch"| RedisDispatch["Redis dispatch stream"]
  Worker["Worker: internal/service"] -->|"Consume dispatch"| RedisDispatch
  Worker -->|"Read bundle by bundle_hash"| BundleStore
  Worker -->|"Populate local bundle cache"| Cache["Worker local bundle cache"]
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

### Local Simulation Fallback

The API also exposes a synchronous simulation entry point at `POST /scenarios/{id}/simulate`.
When remote dispatch is disabled, the async job path stays inside the API after compilation and uses the local executor instead of Redis/worker dispatch.

```mermaid
flowchart LR
  UI["UI: useSimulation"] -->|"POST /simulate"| Job["JobService"]
  Job --> Compiler["CompilerService"]
  Compiler -->|"Store compiled bundle"| BundleStore["MinIO / S3 bundle store"]
  Job --> Exec["SimulationBatchExecutor"]
  Exec -->|"Store summary directly"| Postgres["Postgres"]
  Job -->|"Record telemetry"| Telemetry["TelemetryService"]
  UI -->|"Poll /runs/{runId}"| Job
  UI -->|"GET /runs/{runId}/trace-runs"| Job
```

## Optimization And Telemetry

```mermaid
flowchart LR
  UIOpt["UI: useOptimization"] -->|"POST /optimize"| OptController["OptimizationController"]
  OptController --> OptService["OptimizationService"]
  OptService --> Candidate["Candidate generation"]
  OptService --> Scenario["ScenarioService"]
  OptService --> Validator["ScenarioGraphValidator"]
  OptService --> Compiler["CompilerService"]
  OptService --> Executor["SimulationBatchExecutor (trace disabled)"]
  Executor --> Score["Score against target distribution"]
  Score --> Best["Best candidate stored in optimization job"]
  Compiler -->|"Store orphan bundles"| MinIO["MinIO / S3"]
  Executor -->|"Evaluate candidate outcomes without UI-facing traces"| OptService
  UIOpt -->|"Poll /optimize/{jobId}"| OptController
  UIOpt -->|"POST /optimize/{jobId}/apply"| OptController
  OptService -->|"Create new scenario version"| Scenario
  OptService -->|"Apply back resets simulation state"| UIOpt
  OptService -->|"Apply back refreshes analytics"| UIOpt
  Scenario --> Postgres["Postgres"]

  Job["JobService"] -->|"Completed simulation summary"| Telemetry["TelemetryService"]
  Telemetry -->|"Aggregate scenario analytics"| Postgres
  UIAnalytics["UI: useAnalytics"] -->|"GET /scenarios/{id}/analytics"| TelemetryController["TelemetryController"]
  TelemetryController --> Telemetry
```

## Notes

- The UI uses the async simulation job path (`POST /simulate`) as its primary execution flow.
- The local simulation fallback stays on the `JobService` path after bundle compilation when remote dispatch is disabled, then stores the summary directly and records telemetry.
- Flow A updates shared studio state, the authoring-result surface, the activity feed, and scenario local history.
- Flow B updates the validation-result surface, the activity feed, and validation local history.
- Flow F generates candidates, scores them against the target distribution, persists the best candidate in the optimization job, and resets simulation state plus analytics after apply-back.
- `packages/spec` defines the canonical contract boundary, while the live runtime still uses concrete in-repo model shapes and compiled payload conventions.
