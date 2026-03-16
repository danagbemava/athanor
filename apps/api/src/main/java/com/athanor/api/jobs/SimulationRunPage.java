package com.athanor.api.jobs;

import com.athanor.api.simulation.SimulationService;
import java.util.List;

public record SimulationRunPage(
	int page,
	int pageSize,
	int totalRuns,
	List<SimulationService.SimulationRun> runs
) {}
