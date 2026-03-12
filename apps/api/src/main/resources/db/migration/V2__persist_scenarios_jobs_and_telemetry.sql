CREATE TABLE scenarios (
    id UUID PRIMARY KEY,
    name TEXT NOT NULL,
    description TEXT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL
);

CREATE TABLE scenario_versions (
    id UUID PRIMARY KEY,
    scenario_id UUID NOT NULL REFERENCES scenarios(id) ON DELETE CASCADE,
    version_number INTEGER NOT NULL,
    state VARCHAR(32) NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    graph_json TEXT NOT NULL,
    CONSTRAINT uq_scenario_versions_scenario_version UNIQUE (scenario_id, version_number)
);

CREATE INDEX idx_scenario_versions_scenario
    ON scenario_versions (scenario_id, version_number DESC);

CREATE TABLE simulation_jobs (
    run_id UUID PRIMARY KEY,
    scenario_id UUID NOT NULL,
    request_json TEXT NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    total_runs INTEGER NOT NULL,
    status VARCHAR(32) NOT NULL,
    completed_runs INTEGER NOT NULL,
    attempts INTEGER NOT NULL,
    dead_lettered BOOLEAN NOT NULL,
    error_message TEXT NULL,
    started_at TIMESTAMP WITH TIME ZONE NULL,
    completed_at TIMESTAMP WITH TIME ZONE NULL,
    summary_json TEXT NULL,
    version_id UUID NULL,
    version_number INTEGER NULL,
    bundle_hash VARCHAR(64) NULL
);

CREATE INDEX idx_simulation_jobs_status
    ON simulation_jobs (status);

CREATE TABLE optimization_jobs (
    job_id UUID PRIMARY KEY,
    scenario_id UUID NOT NULL,
    base_version_id UUID NOT NULL,
    base_version_number INTEGER NOT NULL,
    target_distribution_json TEXT NOT NULL,
    max_iterations INTEGER NOT NULL,
    runs_per_iteration INTEGER NOT NULL,
    strategy VARCHAR(64) NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    status VARCHAR(32) NOT NULL,
    iterations_completed INTEGER NOT NULL,
    best_score DOUBLE PRECISION NOT NULL,
    converged BOOLEAN NOT NULL,
    error_message TEXT NULL,
    started_at TIMESTAMP WITH TIME ZONE NULL,
    completed_at TIMESTAMP WITH TIME ZONE NULL,
    best_parameters_json TEXT NULL,
    best_outcome_distribution_json TEXT NOT NULL,
    best_graph_json TEXT NULL,
    applied_version_id UUID NULL,
    applied_version_number INTEGER NULL
);

CREATE INDEX idx_optimization_jobs_status
    ON optimization_jobs (status);

CREATE TABLE scenario_telemetry (
    scenario_id UUID PRIMARY KEY,
    aggregate_json TEXT NOT NULL
);
