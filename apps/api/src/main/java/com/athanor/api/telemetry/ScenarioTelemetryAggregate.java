package com.athanor.api.telemetry;

import com.athanor.api.simulation.SimulationService;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

final class ScenarioTelemetryAggregate {

	private static final int TRACE_SAMPLE_RATE = 5;
	private static final int MAX_SAMPLED_TRACES = 25;

	private final UUID scenarioId;
	private UUID latestVersionId;
	private Integer latestVersionNumber;
	private String latestBundleHash;
	private String agentVersion;
	private int batchCount;
	private long runCount;
	private long totalSteps;
	private Instant lastCompletedAt;
	private final List<Integer> completedRunSteps = new ArrayList<>();
	private final Map<String, Long> outcomeCounts = new LinkedHashMap<>();
	private final Map<String, Long> nodeVisitCounts = new LinkedHashMap<>();
	private final List<ScenarioAnalyticsTraceSample> sampledTraces = new ArrayList<>();

	ScenarioTelemetryAggregate(UUID scenarioId) {
		this.scenarioId = scenarioId;
	}

	synchronized void record(SimulationService.SimulationSummary summary) {
		latestVersionId = summary.versionId();
		latestVersionNumber = summary.versionNumber();
		latestBundleHash = summary.bundleHash();
		agentVersion = summary.agentVersion();
		lastCompletedAt = summary.completedAt();
		batchCount += 1;

		for (SimulationService.SimulationRun run : summary.runs()) {
			runCount += 1L;
			totalSteps += run.stepsTaken();
			completedRunSteps.add(run.stepsTaken());
			outcomeCounts.merge(run.outcome(), 1L, Long::sum);
			recordNodeVisits(run);
			sampleTrace(run, summary.completedAt());
		}
	}

	synchronized ScenarioAnalyticsSnapshot snapshot() {
		double averageSteps = runCount == 0 ? 0d : (double) totalSteps / (double) runCount;
		return new ScenarioAnalyticsSnapshot(
			scenarioId,
			latestVersionId,
			latestVersionNumber,
			latestBundleHash,
			agentVersion,
			batchCount,
			runCount,
			averageSteps,
			p90Steps(),
			sortByCountDescending(outcomeCounts),
			sortByCountDescending(nodeVisitCounts),
			TRACE_SAMPLE_RATE,
			sampledTraces.size(),
			List.copyOf(sampledTraces),
			lastCompletedAt
		);
	}

	private void recordNodeVisits(SimulationService.SimulationRun run) {
		for (SimulationService.SimulationTraceEvent traceEvent : run.trace()) {
			nodeVisitCounts.merge(traceEvent.nodeId(), 1L, Long::sum);
		}
	}

	private void sampleTrace(SimulationService.SimulationRun run, Instant completedAt) {
		if (run.trace().isEmpty() || sampledTraces.size() >= MAX_SAMPLED_TRACES) {
			return;
		}
		if (Math.floorMod(Long.hashCode(run.seed()), 100) >= TRACE_SAMPLE_RATE) {
			return;
		}

		sampledTraces.add(
			new ScenarioAnalyticsTraceSample(
				run.seed(),
				run.outcome(),
				run.stepsTaken(),
				run.trace(),
				completedAt
			)
		);
	}

	private int p90Steps() {
		if (completedRunSteps.isEmpty()) {
			return 0;
		}

		List<Integer> sorted = new ArrayList<>(completedRunSteps);
		sorted.sort(Comparator.naturalOrder());
		int index = (int) Math.ceil(sorted.size() * 0.9d) - 1;
		return sorted.get(Math.max(0, Math.min(index, sorted.size() - 1)));
	}

	private Map<String, Long> sortByCountDescending(Map<String, Long> source) {
		return source
			.entrySet()
			.stream()
			.sorted(
				Map.Entry.<String, Long>comparingByValue(Comparator.reverseOrder())
					.thenComparing(Map.Entry.comparingByKey())
			)
			.collect(
				LinkedHashMap::new,
				(map, entry) -> map.put(entry.getKey(), entry.getValue()),
				LinkedHashMap::putAll
			);
	}
}
