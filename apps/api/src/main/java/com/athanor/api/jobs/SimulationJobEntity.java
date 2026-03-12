package com.athanor.api.jobs;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "simulation_jobs")
class SimulationJobEntity {

	@Id
	@Column(name = "run_id", nullable = false)
	private UUID runId;

	@Column(name = "scenario_id", nullable = false)
	private UUID scenarioId;

	@Column(name = "request_json", nullable = false, columnDefinition = "TEXT")
	private String requestJson;

	@Column(name = "created_at", nullable = false)
	private Instant createdAt;

	@Column(name = "total_runs", nullable = false)
	private int totalRuns;

	@Column(name = "status", nullable = false)
	private String status;

	@Column(name = "completed_runs", nullable = false)
	private int completedRuns;

	@Column(name = "attempts", nullable = false)
	private int attempts;

	@Column(name = "dead_lettered", nullable = false)
	private boolean deadLettered;

	@Column(name = "error_message")
	private String errorMessage;

	@Column(name = "started_at")
	private Instant startedAt;

	@Column(name = "completed_at")
	private Instant completedAt;

	@Column(name = "summary_json", columnDefinition = "TEXT")
	private String summaryJson;

	@Column(name = "version_id")
	private UUID versionId;

	@Column(name = "version_number")
	private Integer versionNumber;

	@Column(name = "bundle_hash")
	private String bundleHash;

	protected SimulationJobEntity() {}

	SimulationJobEntity(
		UUID runId,
		UUID scenarioId,
		String requestJson,
		Instant createdAt,
		int totalRuns,
		String status,
		int completedRuns,
		int attempts,
		boolean deadLettered,
		String errorMessage,
		Instant startedAt,
		Instant completedAt,
		String summaryJson,
		UUID versionId,
		Integer versionNumber,
		String bundleHash
	) {
		this.runId = runId;
		this.scenarioId = scenarioId;
		this.requestJson = requestJson;
		this.createdAt = createdAt;
		this.totalRuns = totalRuns;
		this.status = status;
		this.completedRuns = completedRuns;
		this.attempts = attempts;
		this.deadLettered = deadLettered;
		this.errorMessage = errorMessage;
		this.startedAt = startedAt;
		this.completedAt = completedAt;
		this.summaryJson = summaryJson;
		this.versionId = versionId;
		this.versionNumber = versionNumber;
		this.bundleHash = bundleHash;
	}

	UUID runId() { return runId; }
	UUID scenarioId() { return scenarioId; }
	String requestJson() { return requestJson; }
	Instant createdAt() { return createdAt; }
	int totalRuns() { return totalRuns; }
	String status() { return status; }
	int completedRuns() { return completedRuns; }
	int attempts() { return attempts; }
	boolean deadLettered() { return deadLettered; }
	String errorMessage() { return errorMessage; }
	Instant startedAt() { return startedAt; }
	Instant completedAt() { return completedAt; }
	String summaryJson() { return summaryJson; }
	UUID versionId() { return versionId; }
	Integer versionNumber() { return versionNumber; }
	String bundleHash() { return bundleHash; }
}
