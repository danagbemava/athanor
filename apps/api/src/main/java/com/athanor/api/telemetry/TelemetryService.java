package com.athanor.api.telemetry;

import com.athanor.api.simulation.SimulationService;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import tools.jackson.databind.ObjectMapper;

@Service
public class TelemetryService {

	private final TelemetryEntityJpaRepository telemetryRepository;
	private final ObjectMapper objectMapper;

	@Autowired
	public TelemetryService(
		TelemetryEntityJpaRepository telemetryRepository,
		ObjectMapper objectMapper
	) {
		this.telemetryRepository = telemetryRepository;
		this.objectMapper = objectMapper;
	}

	public void recordSimulationSummary(SimulationService.SimulationSummary summary) {
		if (summary == null) {
			return;
		}

		ScenarioTelemetryAggregate aggregate = telemetryRepository
			.findById(summary.scenarioId())
			.map(entity -> ScenarioTelemetryAggregate.fromEntity(entity, objectMapper))
			.orElseGet(() -> new ScenarioTelemetryAggregate(summary.scenarioId()));
		aggregate.record(summary);
		telemetryRepository.save(aggregate.toEntity(objectMapper));
	}

	public ScenarioAnalyticsSnapshot scenarioAnalytics(UUID scenarioId) {
		return telemetryRepository
			.findById(scenarioId)
			.map(entity -> ScenarioTelemetryAggregate.fromEntity(entity, objectMapper))
			.map(ScenarioTelemetryAggregate::snapshot)
			.orElseGet(() -> ScenarioAnalyticsSnapshot.empty(scenarioId));
	}
}
