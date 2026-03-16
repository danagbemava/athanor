package com.athanor.api.jobs;

import com.athanor.api.compiler.CompilerService;
import com.athanor.api.simulation.SimulationService;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;
import tools.jackson.databind.ObjectMapper;

final class SimulationJob {

	private final UUID runId;
	private final UUID scenarioId;
	private final SimulationService.SimulationRequest request;
	private final Instant createdAt;
	private final int totalRuns;

	private volatile String status;
	private volatile int completedRuns;
	private volatile int attempts;
	private volatile boolean deadLettered;
	private volatile String error;
	private volatile Instant startedAt;
	private volatile Instant completedAt;
	private volatile SimulationService.SimulationSummary summary;
	private volatile UUID versionId;
	private volatile Integer versionNumber;
	private volatile String bundleHash;
	private volatile String resultKey;

	SimulationJob(UUID runId, UUID scenarioId, SimulationService.SimulationRequest request) {
		this(runId, scenarioId, request, Instant.now(), request.runCount(), "pending", 0, 0, false, null, null, null, null, null, null, null, null);
	}

	private SimulationJob(
		UUID runId,
		UUID scenarioId,
		SimulationService.SimulationRequest request,
		Instant createdAt,
		int totalRuns,
		String status,
		int completedRuns,
		int attempts,
		boolean deadLettered,
		String error,
		Instant startedAt,
		Instant completedAt,
		SimulationService.SimulationSummary summary,
		UUID versionId,
		Integer versionNumber,
		String bundleHash,
		String resultKey
	) {
		this.runId = runId;
		this.scenarioId = scenarioId;
		this.request = request;
		this.createdAt = createdAt;
		this.totalRuns = totalRuns;
		this.status = status;
		this.completedRuns = completedRuns;
		this.attempts = attempts;
		this.deadLettered = deadLettered;
		this.error = error;
		this.startedAt = startedAt;
		this.completedAt = completedAt;
		this.summary = summary;
		this.versionId = versionId;
		this.versionNumber = versionNumber;
		this.bundleHash = bundleHash;
		this.resultKey = resultKey;
	}

	static SimulationJob fromEntity(
		SimulationJobEntity entity,
		ObjectMapper objectMapper
	) {
		return new SimulationJob(
			entity.runId(),
			entity.scenarioId(),
			readJson(objectMapper, entity.requestJson(), SimulationService.SimulationRequest.class),
			entity.createdAt(),
			entity.totalRuns(),
			entity.status(),
			entity.completedRuns(),
			entity.attempts(),
			entity.deadLettered(),
			entity.errorMessage(),
			entity.startedAt(),
			entity.completedAt(),
			entity.summaryJson() == null
				? null
				: readJson(
					objectMapper,
					entity.summaryJson(),
					SimulationService.SimulationSummary.class
				),
			entity.versionId(),
			entity.versionNumber(),
			entity.bundleHash(),
			entity.resultKey()
		);
	}

	UUID runId() {
		return runId;
	}

	UUID scenarioId() {
		return scenarioId;
	}

	SimulationService.SimulationRequest request() {
		return request;
	}

	Instant createdAt() {
		return createdAt;
	}

	int totalRuns() {
		return totalRuns;
	}

	String status() {
		return status;
	}

	synchronized void markRunning() {
		attempts += 1;
		status = "running";
		error = null;
		startedAt = Instant.now();
	}

	synchronized void recordProgress(
		int completedRuns,
		int totalRuns,
		SimulationService.SimulationRun ignored
	) {
		this.completedRuns = Math.min(completedRuns, totalRuns);
	}

	synchronized void attachCompiledBundle(CompilerService.CompiledBundle compiledBundle) {
		Objects.requireNonNull(compiledBundle, "compiledBundle is required");
		this.versionId = compiledBundle.versionId();
		this.versionNumber = compiledBundle.versionNumber();
		this.bundleHash = compiledBundle.bundleHash();
	}

	synchronized void markCompleted(
		SimulationService.SimulationSummary summary,
		String resultKey
	) {
		this.summary = summary;
		this.resultKey = resultKey;
		this.completedRuns = totalRuns;
		this.status = "completed";
		this.completedAt = Instant.now();
		this.error = null;
	}

	synchronized boolean markForRetry(RuntimeException exception, int maxAttempts) {
		error = exception.getMessage();
		if (attempts < maxAttempts) {
			status = "pending";
			completedRuns = 0;
			return true;
		}
		status = "failed";
		deadLettered = true;
		completedAt = Instant.now();
		return false;
	}

	synchronized SimulationJobSnapshot snapshot() {
		double progressPercent = totalRuns == 0
			? 0d
			: ((double) completedRuns / (double) totalRuns) * 100d;
		return new SimulationJobSnapshot(
			runId,
			"simulation_batch",
			status,
			scenarioId,
			bundleHash,
			totalRuns,
			completedRuns,
			progressPercent,
			attempts,
			deadLettered,
			error,
			createdAt,
			startedAt,
			completedAt,
			summary
		);
	}

	int attempts() {
		return attempts;
	}

	UUID versionId() {
		return versionId;
	}

	Integer versionNumber() {
		return versionNumber;
	}

	String bundleHash() {
		return bundleHash;
	}

	String resultKey() {
		return resultKey;
	}

	SimulationService.SimulationSummary summary() {
		return summary;
	}

	SimulationJobEntity toEntity(ObjectMapper objectMapper) {
		return new SimulationJobEntity(
			runId,
			scenarioId,
			writeJson(objectMapper, request),
			createdAt,
			totalRuns,
			status,
			completedRuns,
			attempts,
			deadLettered,
			error,
			startedAt,
			completedAt,
			summary == null ? null : writeJson(objectMapper, summary),
			versionId,
			versionNumber,
			bundleHash,
			resultKey
		);
	}

	private static String writeJson(ObjectMapper objectMapper, Object value) {
		try {
			return objectMapper.writeValueAsString(value);
		} catch (RuntimeException exception) {
			throw new IllegalStateException("failed to serialize simulation job", exception);
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
			throw new IllegalStateException("failed to deserialize simulation job", exception);
		}
	}
}
