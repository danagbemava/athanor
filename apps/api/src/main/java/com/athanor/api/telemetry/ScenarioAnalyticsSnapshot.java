package com.athanor.api.telemetry;

import com.athanor.api.simulation.SimulationService;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public record ScenarioAnalyticsSnapshot(
	UUID scenarioId,
	UUID latestVersionId,
	Integer latestVersionNumber,
	String latestBundleHash,
	String agentVersion,
	int batchCount,
	long runCount,
	double averageSteps,
	int p90Steps,
	Map<String, Long> outcomeCounts,
	Map<String, Long> nodeVisitCounts,
	int traceSampleRate,
	int sampledTraceCount,
	List<ScenarioAnalyticsTraceSample> sampledTraces,
	Instant lastCompletedAt
) {
	public static ScenarioAnalyticsSnapshot empty(UUID scenarioId) {
		return new ScenarioAnalyticsSnapshot(
			scenarioId,
			null,
			null,
			null,
			null,
			0,
			0L,
			0d,
			0,
			Map.of(),
			Map.of(),
			5,
			0,
			List.of(),
			null
		);
	}
}
