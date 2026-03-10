package com.athanor.api.jobs;

import com.athanor.api.compiler.CompilerService;
import com.athanor.api.simulation.SimulationService;
import java.util.UUID;
import org.springframework.stereotype.Component;

@Component
public class NoopWorkerRuntimeDispatcher implements WorkerRuntimeDispatcher {

	@Override
	public boolean enabled() {
		return false;
	}

	@Override
	public void dispatchSimulationJob(
		UUID runId,
		CompilerService.CompiledBundle compiledBundle,
		SimulationService.SimulationRequest request
	) {
		throw new IllegalStateException("worker runtime redis mode is not enabled");
	}
}
