package com.athanor.api.jobs;

import java.time.Instant;
import java.util.UUID;

public record SubmittedSimulationJob(
	UUID runId,
	String status,
	Instant createdAt,
	int totalRuns
) {}
