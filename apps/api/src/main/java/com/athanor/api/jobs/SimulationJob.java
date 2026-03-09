package com.athanor.api.jobs;

import com.athanor.api.simulation.SimulationService;
import java.time.Instant;
import java.util.UUID;

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

	SimulationJob(UUID runId, UUID scenarioId, SimulationService.SimulationRequest request) {
		this.runId = runId;
		this.scenarioId = scenarioId;
		this.request = request;
		this.createdAt = Instant.now();
		this.totalRuns = request.runCount();
		this.status = "pending";
		this.completedRuns = 0;
		this.attempts = 0;
		this.deadLettered = false;
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

	synchronized void markCompleted(SimulationService.SimulationSummary summary) {
		this.summary = summary;
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
		String bundleHash = summary == null ? null : summary.bundleHash();
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
}
