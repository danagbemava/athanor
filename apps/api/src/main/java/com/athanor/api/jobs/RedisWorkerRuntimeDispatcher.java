package com.athanor.api.jobs;

import com.athanor.api.compiler.CompilerService;
import com.athanor.api.compiler.WorkerExecutionMode;
import com.athanor.api.compiler.WorkerExecutionRequest;
import com.athanor.api.compiler.WorkerExecutionRequestFactory;
import com.athanor.api.simulation.SimulationService;
import com.athanor.api.simulation.WorkerRuntimeProperties;
import java.util.UUID;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

@Component
@Primary
public class RedisWorkerRuntimeDispatcher implements WorkerRuntimeDispatcher {

	private final WorkerRuntimeProperties properties;
	private final WorkerExecutionRequestFactory requestFactory;
	private final WorkerRuntimeQueue workerRuntimeQueue;

	public RedisWorkerRuntimeDispatcher(
		WorkerRuntimeProperties properties,
		WorkerExecutionRequestFactory requestFactory,
		WorkerRuntimeQueue workerRuntimeQueue
	) {
		this.properties = properties;
		this.requestFactory = requestFactory;
		this.workerRuntimeQueue = workerRuntimeQueue;
	}

	@Override
	public boolean enabled() {
		return properties.redisModeEnabled();
	}

	@Override
	public void dispatchSimulationJob(
		UUID runId,
		CompilerService.CompiledBundle compiledBundle,
		SimulationService.SimulationRequest request
	) {
		if (!enabled()) {
			throw new IllegalStateException("worker runtime redis mode is not enabled");
		}

		SimulationService.SimulationRequest normalizedRequest = request == null
			? new SimulationService.SimulationRequest(25, 1L, 10_000, false)
			: request;
		WorkerExecutionMode mode = Boolean.TRUE.equals(normalizedRequest.trace())
			? WorkerExecutionMode.ANALYTICS
			: WorkerExecutionMode.OPTIMIZATION;
		WorkerExecutionRequest workerRequest = requestFactory.forSimulation(
			compiledBundle,
			normalizedRequest.runCount(),
			normalizedRequest.seedStart(),
			normalizedRequest.maxSteps(),
			mode
		);

		workerRuntimeQueue.publishDispatch(
			new WorkerRuntimeDispatchMessage(
				runId,
				compiledBundle.bundleHash(),
				workerRequest
			)
		);
	}
}
