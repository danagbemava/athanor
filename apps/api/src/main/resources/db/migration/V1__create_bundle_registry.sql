CREATE TABLE bundles (
    bundle_hash VARCHAR(64) PRIMARY KEY,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    last_accessed_at TIMESTAMP WITH TIME ZONE NOT NULL,
    reference_count INTEGER NOT NULL,
    retention_class VARCHAR(16) NOT NULL,
    source_scenario_id UUID NULL,
    source_version_id UUID NULL,
    source_version_number INTEGER NULL,
    compiler_version VARCHAR(128) NOT NULL
);

CREATE INDEX idx_bundles_retention_last_accessed
    ON bundles (retention_class, last_accessed_at);
