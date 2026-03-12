package com.athanor.api.optimization;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import tools.jackson.databind.ObjectMapper;

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
		this(
			jobId,
			scenarioId,
			baseVersionId,
			baseVersionNumber,
			targetDistribution,
			maxIterations,
			runsPerIteration,
			strategy,
			Instant.now(),
			"pending",
			0,
			Double.POSITIVE_INFINITY,
			false,
			null,
			null,
			null,
			null,
			Map.of(),
			null,
			null,
			null
		);
	}

	private OptimizationJob(
		UUID jobId,
		UUID scenarioId,
		UUID baseVersionId,
		int baseVersionNumber,
		Map<String, Double> targetDistribution,
		int maxIterations,
		int runsPerIteration,
		String strategy,
		Instant createdAt,
		String status,
		int iterationsCompleted,
		double bestScore,
		boolean converged,
		String error,
		Instant startedAt,
		Instant completedAt,
		OptimizationParameters bestParameters,
		Map<String, Double> bestOutcomeDistribution,
		Map<String, Object> bestGraph,
		UUID appliedVersionId,
		Integer appliedVersionNumber
	) {
		this.jobId = jobId;
		this.scenarioId = scenarioId;
		this.baseVersionId = baseVersionId;
		this.baseVersionNumber = baseVersionNumber;
		this.targetDistribution = Map.copyOf(targetDistribution);
		this.maxIterations = maxIterations;
		this.runsPerIteration = runsPerIteration;
		this.strategy = strategy;
		this.createdAt = createdAt;
		this.status = status;
		this.iterationsCompleted = iterationsCompleted;
		this.bestScore = bestScore;
		this.converged = converged;
		this.error = error;
		this.startedAt = startedAt;
		this.completedAt = completedAt;
		this.bestParameters = bestParameters;
		this.bestOutcomeDistribution = Map.copyOf(bestOutcomeDistribution);
		this.bestGraph = bestGraph == null ? null : copyGraph(bestGraph);
		this.appliedVersionId = appliedVersionId;
		this.appliedVersionNumber = appliedVersionNumber;
	}

	static OptimizationJob fromEntity(
		OptimizationJobEntity entity,
		ObjectMapper objectMapper
	) {
		return new OptimizationJob(
			entity.jobId(),
			entity.scenarioId(),
			entity.baseVersionId(),
			entity.baseVersionNumber(),
			readJson(objectMapper, entity.targetDistributionJson(), Map.class),
			entity.maxIterations(),
			entity.runsPerIteration(),
			entity.strategy(),
			entity.createdAt(),
			entity.status(),
			entity.iterationsCompleted(),
			entity.bestScore(),
			entity.converged(),
			entity.errorMessage(),
			entity.startedAt(),
			entity.completedAt(),
			entity.bestParametersJson() == null
				? null
				: readJson(objectMapper, entity.bestParametersJson(), OptimizationParameters.class),
			readJson(objectMapper, entity.bestOutcomeDistributionJson(), Map.class),
			entity.bestGraphJson() == null
				? null
				: readJson(objectMapper, entity.bestGraphJson(), Map.class),
			entity.appliedVersionId(),
			entity.appliedVersionNumber()
		);
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

	synchronized OptimizationJobEntity toEntity(ObjectMapper objectMapper) {
		return new OptimizationJobEntity(
			jobId,
			scenarioId,
			baseVersionId,
			baseVersionNumber,
			writeJson(objectMapper, targetDistribution),
			maxIterations,
			runsPerIteration,
			strategy,
			createdAt,
			status,
			iterationsCompleted,
			bestScore,
			converged,
			error,
			startedAt,
			completedAt,
			bestParameters == null ? null : writeJson(objectMapper, bestParameters),
			writeJson(objectMapper, bestOutcomeDistribution),
			bestGraph == null ? null : writeJson(objectMapper, copyGraph(bestGraph)),
			appliedVersionId,
			appliedVersionNumber
		);
	}

	private static String writeJson(ObjectMapper objectMapper, Object value) {
		try {
			return objectMapper.writeValueAsString(value);
		} catch (RuntimeException exception) {
			throw new IllegalStateException("failed to serialize optimization job", exception);
		}
	}

	private static <T> T readJson(
		ObjectMapper objectMapper,
		String json,
		Class<T> type
	) {
		try {
			return objectMapper.readValue(json, type);
		} catch (RuntimeException exception) {
			throw new IllegalStateException("failed to deserialize optimization job", exception);
		}
	}
}
