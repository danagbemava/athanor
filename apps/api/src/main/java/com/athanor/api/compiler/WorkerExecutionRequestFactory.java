package com.athanor.api.compiler;

import java.util.List;
import org.springframework.stereotype.Component;

@Component
public class WorkerExecutionRequestFactory {

	private static final int DEFAULT_RUN_COUNT = 25;
	private static final long DEFAULT_SEED_START = 1L;
	private static final int DEFAULT_MAX_STEPS = 10_000;

	public WorkerExecutionRequestFactory() {}

	public WorkerExecutionRequest forSimulation(
		CompilerService.CompiledBundle bundle,
		Integer runCount,
		Long seedStart,
		Integer maxSteps,
		WorkerExecutionMode mode
	) {
		return new WorkerExecutionRequest(
			bundle.bundleHash(),
			seedStart == null ? DEFAULT_SEED_START : seedStart,
			runCount == null ? DEFAULT_RUN_COUNT : runCount,
			"random-v1",
			List.of(),
			mode.serializedValue(),
			maxSteps == null ? DEFAULT_MAX_STEPS : maxSteps
		);
	}
}
