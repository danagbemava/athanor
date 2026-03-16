package com.athanor.api.jobs;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.athanor.api.compiler.CompilerService;
import com.athanor.api.compiler.FilesystemBundleStore;
import com.athanor.api.scenario.ScenarioGraphValidator;
import com.athanor.api.scenario.ScenarioService;
import com.athanor.api.scenario.ScenarioServiceTestFactory;
import com.athanor.api.simulation.LocalSimulationBatchExecutor;
import com.athanor.api.simulation.SimulationBatchExecutor;
import com.athanor.api.simulation.SimulationService;
import com.athanor.api.simulation.WorkerExecutionSummaryMapper;
import com.athanor.api.telemetry.ScenarioAnalyticsSnapshot;
import com.athanor.api.telemetry.TelemetryService;
import com.athanor.api.telemetry.TelemetryServiceTestFactory;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import tools.jackson.databind.ObjectMapper;

class JobServiceTests {

	@TempDir
	Path tempDir;

	@Test
	void submittedSimulationJobsCompleteAndExposeProgress() throws Exception {
		TestFixture fixture = fixture();
		UUID scenarioId = fixture.createScenario(validGraph());

		SubmittedSimulationJob submitted = fixture.jobService().submitSimulationJob(
			scenarioId,
			new SimulationService.SimulationRequest(6, 10L, 100, false)
		);

		assertTrue(
			List.of("pending", "running").contains(submitted.status()),
			"submitted job should be pending or already running"
		);
		assertEquals(6, submitted.totalRuns());

		SimulationJobSnapshot completed = awaitCompletion(
			fixture.jobService(),
			submitted.runId()
		);

		assertEquals("completed", completed.status());
		assertEquals(6, completed.completedRuns());
		assertEquals(100.0d, completed.progressPercent());
		assertEquals(1, completed.attempts());
		assertEquals(6, completed.summary().runCount());
		assertTrue(completed.summary().outcomeCounts().containsKey("approved"));
		ScenarioAnalyticsSnapshot analytics = fixture.telemetryService().scenarioAnalytics(
			scenarioId
		);
		assertEquals(1, analytics.batchCount());
		assertEquals(6L, analytics.runCount());
		assertTrue(analytics.outcomeCounts().containsKey("approved"));
	}

	@Test
	void failedJobsRetryThenDeadLetter() throws Exception {
		TestFixture fixture = fixture();

		SubmittedSimulationJob submitted = fixture.jobService().submitSimulationJob(
			UUID.randomUUID(),
			new SimulationService.SimulationRequest(2, 1L, 100, false)
		);

		SimulationJobSnapshot failed = awaitFailure(
			fixture.jobService(),
			submitted.runId()
		);

		assertEquals("failed", failed.status());
		assertTrue(failed.deadLettered());
		assertEquals(4, failed.attempts());
		assertTrue(failed.error() != null && !failed.error().isBlank());
	}

	private SimulationJobSnapshot awaitCompletion(
		JobService jobService,
		UUID runId
	) throws InterruptedException {
		return awaitStatus(jobService, runId, "completed");
	}

	private SimulationJobSnapshot awaitFailure(JobService jobService, UUID runId)
		throws InterruptedException {
		return awaitStatus(jobService, runId, "failed");
	}

	private SimulationJobSnapshot awaitStatus(
		JobService jobService,
		UUID runId,
		String expectedStatus
	) throws InterruptedException {
		Instant deadline = Instant.now().plus(Duration.ofSeconds(5));
		SimulationJobSnapshot snapshot = jobService.getSimulationJob(runId);
		while (!expectedStatus.equals(snapshot.status()) && Instant.now().isBefore(deadline)) {
			Thread.sleep(25L);
			snapshot = jobService.getSimulationJob(runId);
		}
		assertEquals(expectedStatus, snapshot.status());
		return snapshot;
	}

	private TestFixture fixture() {
		ObjectMapper objectMapper = new ObjectMapper();
		ScenarioService scenarioService = ScenarioServiceTestFactory.create(objectMapper);
		CompilerService compilerService = new CompilerService(
			scenarioService,
			new ScenarioGraphValidator(),
			new FilesystemBundleStore(tempDir, objectMapper),
			objectMapper
		);
		SimulationService simulationService = new SimulationService(compilerService, objectMapper);
		SimulationBatchExecutor simulationBatchExecutor = new LocalSimulationBatchExecutor(
			simulationService
		);
		TelemetryService telemetryService = TelemetryServiceTestFactory.create(objectMapper);
		SimulationResultStore simulationResultStore = SimulationResultStoreTestFactory.create();
		JobService jobService = new JobService(
			compilerService,
			simulationService,
			simulationBatchExecutor,
			new NoopWorkerRuntimeDispatcher(),
			new WorkerExecutionSummaryMapper(objectMapper),
			telemetryService,
			simulationResultStore,
			SimulationJobRepositoryTestFactory.create(),
			objectMapper,
			new SimpleMeterRegistry()
		);
		return new TestFixture(scenarioService, jobService, telemetryService);
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
					List.of(
						Map.of("to", "approved", "weight", 0.7),
						Map.of("to", "declined", "weight", 0.3)
					)
				),
				Map.of("id", "approved", "type", "TerminalNode", "outcome", "approved"),
				Map.of("id", "declined", "type", "TerminalNode", "outcome", "declined")
			),
			"edges",
			List.of(
				Map.of("from", "start", "to", "approved"),
				Map.of("from", "start", "to", "declined")
			)
		);
	}

	private record TestFixture(
		ScenarioService scenarioService,
		JobService jobService,
		TelemetryService telemetryService
	) {
		private UUID createScenario(Map<String, Object> graph) {
			return scenarioService
				.createScenario(new ScenarioService.CreateScenarioCommand("Queued", "", graph))
				.scenarioId();
		}
	}
}
