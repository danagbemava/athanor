package com.athanor.api.jobs;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.athanor.api.compiler.CompilerService;
import com.athanor.api.compiler.FilesystemBundleStore;
import com.athanor.api.scenario.ScenarioGraphValidator;
import com.athanor.api.scenario.ScenarioService;
import com.athanor.api.scenario.ScenarioServiceTestFactory;
import com.athanor.api.simulation.LocalSimulationBatchExecutor;
import com.athanor.api.simulation.SimulationService;
import com.athanor.api.simulation.WorkerExecutionSummaryMapper;
import com.athanor.api.telemetry.TelemetryService;
import com.athanor.api.telemetry.TelemetryServiceTestFactory;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Instant;
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
	private ObjectMapper objectMapper;
	private SimulationResultStoreTestFactory.MutableSimulationResultStore simulationResultStore;
	private UUID runId;

	@BeforeEach
	void setUp() throws Exception {
		objectMapper = new ObjectMapper();
		ScenarioService scenarioService = ScenarioServiceTestFactory.create(objectMapper);
		CompilerService compilerService = new CompilerService(
			scenarioService,
			new ScenarioGraphValidator(),
			new FilesystemBundleStore(tempDir, objectMapper),
			objectMapper
		);
		SimulationService simulationService = new SimulationService(compilerService, objectMapper);
		telemetryService = TelemetryServiceTestFactory.create(objectMapper);
		simulationResultStore = SimulationResultStoreTestFactory.create();
		jobService = new JobService(
			compilerService,
			simulationService,
			new LocalSimulationBatchExecutor(simulationService),
			new StubWorkerRuntimeDispatcher(),
			new WorkerExecutionSummaryMapper(objectMapper),
			telemetryService,
			simulationResultStore,
			SimulationJobRepositoryTestFactory.create(),
			objectMapper,
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
		String resultKey = storeExecutionResult(bundleHash);

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
				objectMapper.writeValueAsString(completionPayload(bundleHash, resultKey))
			)
		);

		SimulationJobSnapshot snapshot = jobService.getSimulationJob(runId);
		assertEquals("completed", snapshot.status());
		assertEquals(2, snapshot.completedRuns());
		assertEquals(100.0d, snapshot.progressPercent());
		assertEquals(1, telemetryService.scenarioAnalytics(snapshot.scenarioId()).batchCount());
	}

	@Test
	void ignoresDuplicateCompletionEvents() throws Exception {
		String bundleHash = jobService.getSimulationJob(runId).bundleHash();
		String resultKey = storeExecutionResult(bundleHash);
		WorkerRuntimeEventMessage completion = new WorkerRuntimeEventMessage(
			"1-1",
			runId,
			"complete",
			objectMapper.writeValueAsString(completionPayload(bundleHash, resultKey))
		);

		eventHandler.handle(completion);
		eventHandler.handle(completion);

		SimulationJobSnapshot snapshot = jobService.getSimulationJob(runId);
		assertEquals("completed", snapshot.status());
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

	private String storeExecutionResult(String bundleHash) {
		String resultKey = "simulation-results/" + runId + ".json";
		simulationResultStore.putJson(resultKey, executionResult(bundleHash), objectMapper);
		return resultKey;
	}

	private WorkerExecutionCompletionPayload completionPayload(
		String bundleHash,
		String resultKey
	) {
		return new WorkerExecutionCompletionPayload(
			bundleHash,
			"random-v1",
			"random-v1",
			"analytics",
			9L,
			2,
			50,
			1.0d,
			Map.of("approved", 1, "declined", 1),
			resultKey,
			Instant.now()
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
