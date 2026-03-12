package com.athanor.api.optimization;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "optimization_jobs")
class OptimizationJobEntity {

	@Id
	@Column(name = "job_id", nullable = false)
	private UUID jobId;

	@Column(name = "scenario_id", nullable = false)
	private UUID scenarioId;

	@Column(name = "base_version_id", nullable = false)
	private UUID baseVersionId;

	@Column(name = "base_version_number", nullable = false)
	private int baseVersionNumber;

	@Column(name = "target_distribution_json", nullable = false, columnDefinition = "TEXT")
	private String targetDistributionJson;

	@Column(name = "max_iterations", nullable = false)
	private int maxIterations;

	@Column(name = "runs_per_iteration", nullable = false)
	private int runsPerIteration;

	@Column(name = "strategy", nullable = false)
	private String strategy;

	@Column(name = "created_at", nullable = false)
	private Instant createdAt;

	@Column(name = "status", nullable = false)
	private String status;

	@Column(name = "iterations_completed", nullable = false)
	private int iterationsCompleted;

	@Column(name = "best_score", nullable = false)
	private double bestScore;

	@Column(name = "converged", nullable = false)
	private boolean converged;

	@Column(name = "error_message")
	private String errorMessage;

	@Column(name = "started_at")
	private Instant startedAt;

	@Column(name = "completed_at")
	private Instant completedAt;

	@Column(name = "best_parameters_json", columnDefinition = "TEXT")
	private String bestParametersJson;

	@Column(name = "best_outcome_distribution_json", nullable = false, columnDefinition = "TEXT")
	private String bestOutcomeDistributionJson;

	@Column(name = "best_graph_json", columnDefinition = "TEXT")
	private String bestGraphJson;

	@Column(name = "applied_version_id")
	private UUID appliedVersionId;

	@Column(name = "applied_version_number")
	private Integer appliedVersionNumber;

	protected OptimizationJobEntity() {}

	OptimizationJobEntity(
		UUID jobId,
		UUID scenarioId,
		UUID baseVersionId,
		int baseVersionNumber,
		String targetDistributionJson,
		int maxIterations,
		int runsPerIteration,
		String strategy,
		Instant createdAt,
		String status,
		int iterationsCompleted,
		double bestScore,
		boolean converged,
		String errorMessage,
		Instant startedAt,
		Instant completedAt,
		String bestParametersJson,
		String bestOutcomeDistributionJson,
		String bestGraphJson,
		UUID appliedVersionId,
		Integer appliedVersionNumber
	) {
		this.jobId = jobId;
		this.scenarioId = scenarioId;
		this.baseVersionId = baseVersionId;
		this.baseVersionNumber = baseVersionNumber;
		this.targetDistributionJson = targetDistributionJson;
		this.maxIterations = maxIterations;
		this.runsPerIteration = runsPerIteration;
		this.strategy = strategy;
		this.createdAt = createdAt;
		this.status = status;
		this.iterationsCompleted = iterationsCompleted;
		this.bestScore = bestScore;
		this.converged = converged;
		this.errorMessage = errorMessage;
		this.startedAt = startedAt;
		this.completedAt = completedAt;
		this.bestParametersJson = bestParametersJson;
		this.bestOutcomeDistributionJson = bestOutcomeDistributionJson;
		this.bestGraphJson = bestGraphJson;
		this.appliedVersionId = appliedVersionId;
		this.appliedVersionNumber = appliedVersionNumber;
	}

	UUID jobId() { return jobId; }
	UUID scenarioId() { return scenarioId; }
	UUID baseVersionId() { return baseVersionId; }
	int baseVersionNumber() { return baseVersionNumber; }
	String targetDistributionJson() { return targetDistributionJson; }
	int maxIterations() { return maxIterations; }
	int runsPerIteration() { return runsPerIteration; }
	String strategy() { return strategy; }
	Instant createdAt() { return createdAt; }
	String status() { return status; }
	int iterationsCompleted() { return iterationsCompleted; }
	double bestScore() { return bestScore; }
	boolean converged() { return converged; }
	String errorMessage() { return errorMessage; }
	Instant startedAt() { return startedAt; }
	Instant completedAt() { return completedAt; }
	String bestParametersJson() { return bestParametersJson; }
	String bestOutcomeDistributionJson() { return bestOutcomeDistributionJson; }
	String bestGraphJson() { return bestGraphJson; }
	UUID appliedVersionId() { return appliedVersionId; }
	Integer appliedVersionNumber() { return appliedVersionNumber; }
}
