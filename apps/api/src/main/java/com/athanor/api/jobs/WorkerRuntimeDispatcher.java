package com.athanor.api.jobs;

import com.athanor.api.compiler.CompilerService;
import com.athanor.api.simulation.SimulationService;
import java.util.UUID;

public interface WorkerRuntimeDispatcher {

	boolean enabled();

	void dispatchSimulationJob(
		UUID runId,
		CompilerService.CompiledBundle compiledBundle,
		SimulationService.SimulationRequest request
	);
}
