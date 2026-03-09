package com.athanor.api.simulation;

import java.util.List;
import java.util.Map;

record RunResult(
	String outcome,
	int stepsTaken,
	Map<String, Object> finalState,
	List<SimulationService.SimulationTraceEvent> trace
) {}
