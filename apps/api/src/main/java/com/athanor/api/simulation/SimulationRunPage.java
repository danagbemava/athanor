package com.athanor.api.simulation;

import java.util.List;

public record SimulationRunPage(
	int page,
	int pageSize,
	int totalRuns,
	List<SimulationService.SimulationRun> runs
) {}
