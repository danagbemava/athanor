package com.athanor.api.telemetry;

import com.athanor.api.simulation.SimulationService;
import java.time.Instant;
import java.util.List;

public record ScenarioAnalyticsTraceSample(
	long seed,
	String outcome,
	int stepsTaken,
	List<SimulationService.SimulationTraceEvent> trace,
	Instant recordedAt
) {}
