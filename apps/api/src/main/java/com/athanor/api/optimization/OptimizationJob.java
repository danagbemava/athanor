package com.athanor.api.optimization;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

final class OptimizationJob {

	private final UUID jobId;
	private final UUID scenarioId;
	private final UUID baseVersionId;
	private final int baseVersionNumber;
	private final Map<String, Double> targetDistribution;
	private final int maxIterations;
	private final int runsPerIteration;
	private final String strategy;
	private final Instant createdAt;

	private volatile String status;
	private volatile int iterationsCompleted;
	private volatile double bestScore;
	private volatile boolean converged;
	private volatile String error;
	private volatile Instant startedAt;
	private volatile Instant completedAt;
	private volatile OptimizationParameters bestParameters;
	private volatile Map<String, Double> bestOutcomeDistribution;
	private volatile Map<String, Object> bestGraph;
	private volatile UUID appliedVersionId;
	private volatile Integer appliedVersionNumber;

	OptimizationJob(
		UUID jobId,
		UUID scenarioId,
		UUID baseVersionId,
		int baseVersionNumber,
		Map<String, Double> targetDistribution,
		int maxIterations,
		int runsPerIteration,
		String strategy
	) {
		this.jobId = jobId;
		this.scenarioId = scenarioId;
		this.baseVersionId = baseVersionId;
		this.baseVersionNumber = baseVersionNumber;
		this.targetDistribution = Map.copyOf(targetDistribution);
		this.maxIterations = maxIterations;
		this.runsPerIteration = runsPerIteration;
		this.strategy = strategy;
		this.createdAt = Instant.now();
		this.status = "pending";
		this.bestScore = Double.POSITIVE_INFINITY;
		this.bestOutcomeDistribution = Map.of();
	}

	UUID jobId() {
		return jobId;
	}

	UUID scenarioId() {
		return scenarioId;
	}

	String status() {
		return status;
	}

	int maxIterations() {
		return maxIterations;
	}

	int runsPerIteration() {
		return runsPerIteration;
	}

	String strategy() {
		return strategy;
	}

	Instant createdAt() {
		return createdAt;
	}

	synchronized void markRunning() {
		status = "running";
		error = null;
		startedAt = Instant.now();
	}

	synchronized void recordIteration(int iteration, OptimizationCandidate candidate) {
		iterationsCompleted = iteration;
		if (candidate.score() < bestScore) {
			bestScore = candidate.score();
			bestParameters = candidate.parameters();
			bestOutcomeDistribution = Map.copyOf(candidate.outcomeDistribution());
			bestGraph = copyGraph(candidate.graph());
		}
	}

	synchronized void markCompleted(boolean converged) {
		this.converged = converged;
		this.status = "completed";
		this.completedAt = Instant.now();
		this.error = null;
	}

	synchronized void markFailed(RuntimeException exception) {
		status = "failed";
		error = exception.getMessage();
		completedAt = Instant.now();
	}

	synchronized Map<String, Object> bestGraph() {
		return bestGraph == null ? null : copyGraph(bestGraph);
	}

	synchronized void markApplied(UUID versionId, int versionNumber) {
		appliedVersionId = versionId;
		appliedVersionNumber = versionNumber;
	}

	synchronized OptimizationJobSnapshot snapshot() {
		double progressPercent = maxIterations == 0
			? 0d
			: ((double) iterationsCompleted / (double) maxIterations) * 100d;
		if ("completed".equals(status)) {
			progressPercent = 100d;
		}
		return new OptimizationJobSnapshot(
			jobId,
			"optimizer_random_search",
			status,
			scenarioId,
			baseVersionId,
			baseVersionNumber,
			strategy,
			targetDistribution,
			maxIterations,
			runsPerIteration,
			iterationsCompleted,
			Math.min(100d, progressPercent),
			Double.isFinite(bestScore) ? bestScore : 0d,
			converged,
			bestParameters,
			bestOutcomeDistribution,
			error,
			createdAt,
			startedAt,
			completedAt,
			appliedVersionId,
			appliedVersionNumber
		);
	}

	private Map<String, Object> copyGraph(Map<String, Object> graph) {
		Map<String, Object> copy = new LinkedHashMap<>();
		graph.forEach(copy::put);
		return copy;
	}
}
