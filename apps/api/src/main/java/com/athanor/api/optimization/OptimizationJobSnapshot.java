package com.athanor.api.optimization;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

public record OptimizationJobSnapshot(
	UUID jobId,
	String jobType,
	String status,
	UUID scenarioId,
	UUID baseVersionId,
	int baseVersionNumber,
	String strategy,
	Map<String, Double> targetDistribution,
	int maxIterations,
	int runsPerIteration,
	int iterationsCompleted,
	double progressPercent,
	double bestScore,
	boolean converged,
	OptimizationParameters bestParameters,
	Map<String, Double> bestOutcomeDistribution,
	String error,
	Instant createdAt,
	Instant startedAt,
	Instant completedAt,
	UUID appliedVersionId,
	Integer appliedVersionNumber
) {}
