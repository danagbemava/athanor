package com.athanor.api.jobs;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.athanor.api.compiler.CompilerService;
import com.athanor.api.compiler.FilesystemBundleStore;
import com.athanor.api.scenario.ScenarioGraphValidator;
import com.athanor.api.scenario.ScenarioService;
import com.athanor.api.simulation.LocalSimulationBatchExecutor;
import com.athanor.api.simulation.SimulationService;
import com.athanor.api.simulation.WorkerExecutionSummaryMapper;
import com.athanor.api.telemetry.TelemetryService;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import tools.jackson.databind.ObjectMapper;

class WorkerRuntimeEventHandlerTests {

	@TempDir
	Path tempDir;

	private JobService jobService;
	private TelemetryService telemetryService;
	private WorkerRuntimeEventHandler eventHandler;
	private UUID runId;

	@BeforeEach
	void setUp() throws Exception {
		ObjectMapper objectMapper = new ObjectMapper();
		ScenarioService scenarioService = new ScenarioService(
			new ScenarioGraphValidator(),
			objectMapper
		);
		CompilerService compilerService = new CompilerService(
			scenarioService,
			new ScenarioGraphValidator(),
			new FilesystemBundleStore(tempDir, objectMapper),
			objectMapper
		);
		SimulationService simulationService = new SimulationService(compilerService, objectMapper);
		telemetryService = new TelemetryService();
		jobService = new JobService(
			compilerService,
			simulationService,
			new LocalSimulationBatchExecutor(simulationService),
			new StubWorkerRuntimeDispatcher(),
			new WorkerExecutionSummaryMapper(objectMapper),
			telemetryService,
			new SimpleMeterRegistry()
		);
		eventHandler = new WorkerRuntimeEventHandler(jobService, objectMapper);

		UUID scenarioId = scenarioService
			.createScenario(new ScenarioService.CreateScenarioCommand("Queued", "", validGraph()))
			.scenarioId();
		runId = jobService
			.submitSimulationJob(
				scenarioId,
				new SimulationService.SimulationRequest(2, 9L, 50, true)
			)
			.runId();
		awaitBundleAttachment(runId);
	}

	@Test
	void handlesProgressAndCompletionEvents() throws Exception {
		String bundleHash = jobService.getSimulationJob(runId).bundleHash();

		eventHandler.handle(
			new WorkerRuntimeEventMessage(
				"1-0",
				runId,
				"progress",
				"{\"completedRuns\":1,\"totalRuns\":2}"
			)
		);
		eventHandler.handle(
			new WorkerRuntimeEventMessage(
				"1-1",
				runId,
				"complete",
				new ObjectMapper().writeValueAsString(executionResult(bundleHash))
			)
		);

		SimulationJobSnapshot snapshot = jobService.getSimulationJob(runId);
		assertEquals("completed", snapshot.status());
		assertEquals(2, snapshot.completedRuns());
		assertEquals(100.0d, snapshot.progressPercent());
		assertEquals(1, telemetryService.scenarioAnalytics(snapshot.scenarioId()).batchCount());
	}

	private void awaitBundleAttachment(UUID runId) throws InterruptedException {
		for (int index = 0; index < 100; index += 1) {
			if (jobService.getSimulationJob(runId).bundleHash() != null) {
				return;
			}
			Thread.sleep(10L);
		}
		throw new IllegalStateException("job did not attach compiled bundle");
	}

	private com.athanor.api.compiler.WorkerExecutionResult executionResult(String bundleHash) {
		return new com.athanor.api.compiler.WorkerExecutionResult(
			bundleHash,
			"random-v1",
			"random-v1",
			"analytics",
			9L,
			2,
			50,
			List.of(
				new com.athanor.api.compiler.WorkerExecutionResult.WorkerExecutionRunResult(
					bundleHash,
					9L,
					"random-v1",
					"approved",
					1,
					Map.of(),
					List.of()
				),
				new com.athanor.api.compiler.WorkerExecutionResult.WorkerExecutionRunResult(
					bundleHash,
					10L,
					"random-v1",
					"declined",
					1,
					Map.of(),
					List.of()
				)
			)
		);
	}

	private Map<String, Object> validGraph() {
		return Map.of(
			"id",
			"async-graph",
			"name",
			"Async Graph",
			"version",
			1,
			"entry_node_id",
			"start",
			"nodes",
			List.of(
				Map.of(
					"id",
					"start",
					"type",
					"ChanceNode",
					"chance_options",
					List.of(Map.of("to", "approved", "weight", 1.0))
				),
				Map.of("id", "approved", "type", "TerminalNode", "outcome", "approved")
			),
			"edges",
			List.of(Map.of("from", "start", "to", "approved"))
		);
	}

	private static final class StubWorkerRuntimeDispatcher implements WorkerRuntimeDispatcher {

		@Override
		public boolean enabled() {
			return true;
		}

		@Override
		public void dispatchSimulationJob(
			UUID runId,
			CompilerService.CompiledBundle compiledBundle,
			SimulationService.SimulationRequest request
		) {}
	}
}
