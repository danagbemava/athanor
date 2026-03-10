package com.athanor.api.simulation;

import com.athanor.api.compiler.CompilerService;

public class LocalSimulationBatchExecutor implements SimulationBatchExecutor {

	private final SimulationService simulationService;

	public LocalSimulationBatchExecutor(SimulationService simulationService) {
		this.simulationService = simulationService;
	}

	@Override
	public SimulationService.SimulationSummary executeCompiledBundle(
		CompilerService.CompiledBundle compiledBundle,
		SimulationService.SimulationRequest request,
		SimulationService.SimulationProgressListener progressListener
	) {
		return simulationService.simulateCompiledBundle(compiledBundle, request, progressListener);
	}
}
