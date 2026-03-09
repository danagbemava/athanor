package com.athanor.api.jobs;

import com.athanor.api.simulation.SimulationService;
import java.time.Instant;
import java.util.UUID;

public record SimulationJobSnapshot(
	UUID runId,
	String jobType,
	String status,
	UUID scenarioId,
	String bundleHash,
	int totalRuns,
	int completedRuns,
	double progressPercent,
	int attempts,
	boolean deadLettered,
	String error,
	Instant createdAt,
	Instant startedAt,
	Instant completedAt,
	SimulationService.SimulationSummary summary
) {}
