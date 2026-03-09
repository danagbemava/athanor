package com.athanor.api.telemetry;

import com.athanor.api.simulation.SimulationService;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import org.springframework.stereotype.Service;

@Service
public class TelemetryService {

	private final ConcurrentMap<UUID, ScenarioTelemetryAggregate> analyticsByScenario =
		new ConcurrentHashMap<>();

	public void recordSimulationSummary(SimulationService.SimulationSummary summary) {
		if (summary == null) {
			return;
		}

		analyticsByScenario
			.computeIfAbsent(
				summary.scenarioId(),
				ignored -> new ScenarioTelemetryAggregate(summary.scenarioId())
			)
			.record(summary);
	}

	public ScenarioAnalyticsSnapshot scenarioAnalytics(UUID scenarioId) {
		ScenarioTelemetryAggregate aggregate = analyticsByScenario.get(scenarioId);
		if (aggregate == null) {
			return ScenarioAnalyticsSnapshot.empty(scenarioId);
		}
		return aggregate.snapshot();
	}
}
