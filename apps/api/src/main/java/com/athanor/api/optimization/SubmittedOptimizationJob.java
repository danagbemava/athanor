package com.athanor.api.optimization;

import java.time.Instant;
import java.util.UUID;

public record SubmittedOptimizationJob(
	UUID jobId,
	String status,
	Instant createdAt,
	int maxIterations,
	int runsPerIteration,
	String strategy
) {}
