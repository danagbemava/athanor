package com.athanor.api.simulation;

import com.athanor.api.compiler.CompilerService;

public interface SimulationBatchExecutor {

	SimulationService.SimulationSummary executeCompiledBundle(
		CompilerService.CompiledBundle compiledBundle,
		SimulationService.SimulationRequest request,
		SimulationService.SimulationProgressListener progressListener
	);
}
