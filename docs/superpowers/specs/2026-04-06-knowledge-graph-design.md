# Knowledge Graph Design

## Goal

Generate a repository artifact that explains, in full operational detail, how Athanor's major components work together for both developer onboarding and runtime tracing/debugging.

The deliverable should make it possible to answer these questions quickly:

- What are the primary application components and their responsibilities?
- How do UI, API, worker, shared contracts, and infrastructure interact?
- Which flows are synchronous versus asynchronous?
- Where is state stored, transformed, queued, and read back?
- Which components are authoritative for authoring, compilation, execution, optimization, and telemetry?

## Outputs

The implementation will create two artifacts:

- `docs/architecture/knowledge-graph.md`
- `docs/architecture/knowledge-graph.dot`

The Markdown file will be the reader-friendly entry point. The DOT file will be the authoritative full graph definition.

## Recommended Graph Shape

Use a layered architecture graph with embedded runtime subgraphs.

This combines two views in one deliverable:

1. Structural view
Shows codebase ownership boundaries and major modules.

2. Runtime view
Shows how requests, jobs, bundles, events, traces, and analytics move through the system.

This shape is preferable to a purely file-level dependency map because the repository has clear service boundaries and multiple runtime modes. A file-exhaustive graph would obscure the critical flows.

## Scope

### Include

- UI application surfaces and state orchestration
- API controllers and core service modules
- Worker runtime components
- Shared contract package
- Infrastructure dependencies used locally and at runtime
- Persistence and object storage boundaries
- Queue/event boundaries
- Primary user and system flows

### Exclude

- Low-level UI primitive components unless they materially alter integration behavior
- Every test file
- Every JPA repository and entity field
- Build tooling internals that do not affect runtime collaboration
- Generated directories such as `.nuxt`, `.output`, and `node_modules`

## Architectural Model

The graph should be organized into these clusters.

### 1. UI Cluster

Represent the UI as the Scenario Studio orchestration surface, not as a flat list of pages.

Include:

- `pages/builder.vue` as the main orchestration page
- Studio cards that expose authoring and results surfaces
- `shared-state.ts` as the cross-composable state container
- `useScenarioGraph`
- `useValidation`
- `useScenarios`
- `useSimulation`
- `useOptimization`
- `useAnalytics`
- `useActivityFeed`

Responsibilities to encode:

- Graph authoring
- Client-side validation guardrails
- Scenario creation/versioning
- Async simulation submission and polling
- Optimization submission, polling, and apply-back flow
- Analytics fetch and refresh
- Activity/event log for the studio shell

### 2. API Cluster

Represent the API as a Spring Modulith service with explicit module boundaries.

Include:

- `scenario`
- `compiler`
- `simulation`
- `jobs`
- `optimization`
- `telemetry`
- storage/configuration support where it participates in cross-component flows

Key controllers to show:

- `ScenarioController`
- `CompilerController`
- `BundleController`
- `SimulationController`
- `SimulationJobController`
- `OptimizationController`
- `TelemetryController`

Key services to show:

- `ScenarioService`
- `ScenarioGraphValidator`
- `CompilerService`
- `SimulationService`
- `JobService`
- `OptimizationService`
- `TelemetryService`
- `RedisWorkerRuntimeDispatcher`

Responsibilities to encode:

- Scenario persistence and version lookup
- Graph validation
- Bundle compilation and storage
- Local simulation path
- Async simulation orchestration path
- Optimization search and apply-back
- Telemetry aggregation and analytics retrieval

### 3. Worker Cluster

Represent the worker as an execution runtime with two major layers.

Include:

- `cmd/worker`
- `internal/cli`
- `internal/service`
- `internal/engine`
- `internal/agent`
- `internal/contracts`
- `internal/rng`

Responsibilities to encode:

- Redis dispatch consumption
- S3/MinIO bundle fetch
- Local bundle cache
- Deterministic execution engine
- Progress/completion/failure publishing
- Result storage for later trace pagination

### 4. Shared Contracts Cluster

Represent `packages/spec` as the canonical contract boundary across UI, API, and worker.

Include:

- Schemas
- Fixtures
- Golden corpus

Responsibilities to encode:

- Shared payload expectations
- Determinism validation corpus
- Contract boundary between compile-time and runtime artifacts

### 5. Infrastructure Cluster

Represent the Docker-backed runtime dependencies:

- Postgres
- Redis
- MinIO
- `minio-init`

Responsibilities to encode:

- Relational persistence
- Queue/event transport and worker coordination
- Bundle and execution result object storage
- Bucket bootstrap for local stack

## Core Edges

The graph should encode edge types explicitly.

### Synchronous HTTP Edges

- UI -> scenario endpoints
- UI -> validation endpoint
- UI -> async simulation submission endpoint
- UI -> run status endpoint
- UI -> trace page endpoint
- UI -> optimization submit/status/apply endpoints
- UI -> analytics endpoint

### Internal Service Edges

- `ScenarioController` -> `ScenarioService`
- `ScenarioService` -> repositories + `ScenarioGraphValidator`
- `CompilerController` -> `CompilerService`
- `CompilerService` -> `ScenarioService`, validator, bundle store
- `SimulationController` -> `SimulationService`
- `SimulationJobController` -> `JobService`
- `JobService` -> `CompilerService`
- `JobService` -> `SimulationBatchExecutor`
- `JobService` -> `WorkerRuntimeDispatcher`
- `JobService` -> `SimulationResultStore`
- `JobService` -> `TelemetryService`
- `OptimizationController` -> `OptimizationService`
- `OptimizationService` -> `ScenarioService`, validator, compiler, simulation executor
- `TelemetryController` -> `ScenarioService` + `TelemetryService`

### Async/Queue Edges

- `JobService` -> Redis dispatch stream via runtime queue/dispatcher
- Worker service loop -> Redis dispatch stream consumption
- Worker -> Redis event stream publication
- API job event handler/processor path -> simulation job updates

### Storage Edges

- `ScenarioService` -> Postgres
- `JobService` -> Postgres
- `OptimizationService` -> Postgres
- `TelemetryService` -> Postgres
- `CompilerService` -> bundle store -> MinIO/S3 or filesystem abstraction
- Worker -> MinIO/S3 bundle read
- Worker -> MinIO/S3 execution result write
- `JobService` -> execution result store read for paginated trace retrieval

## Runtime Flows To Render

The Markdown and DOT artifacts should both describe these runtime flows.

### Flow A: Scenario Authoring

1. User edits graph in builder UI.
2. `useScenarioGraph` derives normalized graph payload.
3. `useScenarios` submits create/version requests.
4. `ScenarioController` delegates to `ScenarioService`.
5. `ScenarioService` persists aggregate/version state in Postgres.
6. UI updates shared state and activity feed.

### Flow B: Validation

1. UI runs local structural checks in `useValidation`.
2. UI submits remote validation for the active scenario.
3. `ScenarioService` loads latest version and validates via `ScenarioGraphValidator`.
4. Validation snapshot is returned to UI and recorded in local history.

### Flow C: Compilation

1. API resolves latest scenario version.
2. `CompilerService` validates graph, canonicalizes nodes/edges, computes `bundle_hash`, and constructs compiled payload.
3. Bundle metadata/content is written through the bundle store.
4. Bundle becomes available to worker/runtime consumers.

### Flow D: Async Simulation

1. UI submits `POST /simulate`.
2. `SimulationJobController` delegates to `JobService`.
3. `JobService` normalizes request, persists a job, and schedules processing.
4. `JobService` compiles the current scenario into a bundle.
5. In Redis mode, `RedisWorkerRuntimeDispatcher` publishes a dispatch message.
6. Worker consumes the dispatch message, claims the run, fetches bundle content from object storage, caches it locally, and executes runs deterministically.
7. Worker publishes progress and completion/failure events to Redis.
8. API ingests job updates, stores summary/result references, and records telemetry.
9. UI polls run status and then fetches paginated trace runs.

### Flow E: Local Simulation Fallback

1. `JobService` compiles the bundle.
2. If remote dispatch is disabled, `SimulationBatchExecutor` executes inside the API process.
3. Summary is stored directly and telemetry is recorded without Redis worker participation.

### Flow F: Optimization

1. UI submits optimization request with target distribution and iteration settings.
2. `OptimizationService` validates the scenario and search space.
3. Service generates candidate parameter sets.
4. Each candidate graph is compiled to a bundle.
5. Candidate bundles are executed through the simulation batch executor with trace disabled.
6. Outcome distributions are scored against the target distribution.
7. Best candidate is persisted in the optimization job.
8. UI polls job state.
9. When applied, `OptimizationService` writes a new scenario version through `ScenarioService`.
10. UI resets simulation state and refreshes analytics.

### Flow G: Telemetry And Analytics

1. Completed simulation summaries are passed to `TelemetryService`.
2. Telemetry aggregates are stored in Postgres.
3. UI requests `/scenarios/{id}/analytics`.
4. `TelemetryController` verifies the scenario exists and returns the aggregate snapshot.

## Differences And Nuances To Preserve

The graph should make these distinctions explicit.

- There are two simulation entry points in the API: synchronous `POST /scenarios/{id}/simulate` and asynchronous `POST /simulate`. The UI currently uses the async job path.
- UI validation has both local preflight checks and remote validation against persisted scenario versions.
- `CompilerService` is used directly by both simulation and optimization workflows.
- Optimization reuses simulation execution machinery but without the UI-facing trace workflow.
- Redis mode changes the execution boundary from in-process API execution to remote worker execution.
- MinIO/S3 is used for both compiled bundle storage and worker result/blob storage.
- `packages/spec` is a contract boundary, but the live runtime still relies on in-repo type shapes and compiled payload conventions in code.

## DOT File Expectations

The DOT file should be the fullest representation.

Requirements:

- Use subgraphs/clusters for UI, API, worker, spec, and infrastructure.
- Label edges by interaction type such as `HTTP`, `compiles`, `publishes`, `consumes`, `stores`, `reads`, `polls`, `aggregates`, `applies`.
- Distinguish async edges visually where practical.
- Include important route labels for externally visible API boundaries.

## Markdown File Expectations

The Markdown file should be easier to read than the DOT source.

Requirements:

- Start with a concise architecture summary.
- Include at least one Mermaid graph for the overall system.
- Include separate Mermaid subgraphs or sections for:
  - structural architecture
  - async simulation runtime
  - optimization and telemetry
- Include a short legend that explains edge semantics.
- Include a short "How to read this" section for onboarding.

## Verification

Implementation will be considered correct if:

- The graph artifacts align with the code structure and endpoints currently present in the repository.
- A new developer can follow authoring, validation, compilation, simulation, optimization, and analytics flows from the artifacts alone.
- The DOT and Markdown artifacts tell the same story at different levels of readability.
- The graph clearly distinguishes synchronous service calls, asynchronous worker dispatch, persistence, and object storage interactions.

## Risks

- Over-detail could make the graph unreadable. Mitigation: cluster aggressively and only include behaviorally significant nodes.
- Under-detail could make the graph useless for debugging. Mitigation: preserve all major runtime handoffs and storage boundaries.
- The repository contains both current and fallback paths. Mitigation: annotate the async job path as primary for UI usage and the synchronous path as an alternate execution route.

## Implementation Notes

The knowledge graph artifacts should be documentation-only changes. No production code changes are required.
