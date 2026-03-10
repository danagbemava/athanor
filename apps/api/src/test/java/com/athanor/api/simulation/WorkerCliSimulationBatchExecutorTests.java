package com.athanor.api.simulation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.athanor.api.compiler.CompilerService;
import com.athanor.api.compiler.FilesystemBundleStore;
import com.athanor.api.compiler.WorkerExecutionRequestFactory;
import com.athanor.api.scenario.ScenarioGraphValidator;
import com.athanor.api.scenario.ScenarioService;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import tools.jackson.databind.ObjectMapper;

class WorkerCliSimulationBatchExecutorTests {

	@TempDir
	Path tempDir;

	private ScenarioService scenarioService;
	private CompilerService compilerService;
	private WorkerCliSimulationBatchExecutor executor;

	@BeforeEach
	void setUp() {
		ObjectMapper objectMapper = new ObjectMapper();
		scenarioService = new ScenarioService(new ScenarioGraphValidator(), objectMapper);
		compilerService = new CompilerService(
			scenarioService,
			new ScenarioGraphValidator(),
			new FilesystemBundleStore(tempDir, objectMapper),
			objectMapper
		);
		WorkerCliProperties properties = new WorkerCliProperties();
		properties.setCommand("go run ./cmd/worker");
		properties.setWorkingDirectory("../worker");
		executor = new WorkerCliSimulationBatchExecutor(
			compilerService,
			new WorkerExecutionRequestFactory(),
			properties,
			objectMapper
		);
	}

	@Test
	void executesCompiledBundleThroughWorkerCli() {
		UUID scenarioId = scenarioService
			.createScenario(new ScenarioService.CreateScenarioCommand("Worker", null, validGraph()))
			.scenarioId();
		CompilerService.CompiledBundle bundle = compilerService.compileScenarioBundle(scenarioId);

		SimulationService.SimulationSummary summary = executor.executeCompiledBundle(
			bundle,
			new SimulationService.SimulationRequest(4, 8L, 100, true),
			null
		);

		assertEquals(4, summary.runCount());
		assertEquals(8L, summary.seedStart());
		assertEquals(bundle.bundleHash(), summary.bundleHash());
		assertEquals("random-v1", summary.agentVersion());
		assertEquals(4, summary.runs().size());
		assertTrue(summary.outcomeCounts().containsKey("approved"));
		assertFalse(summary.runs().getFirst().trace().isEmpty());
	}

	@Test
	void optimizationModeOmitsTracePayload() {
		UUID scenarioId = scenarioService
			.createScenario(new ScenarioService.CreateScenarioCommand("Worker", null, validGraph()))
			.scenarioId();
		CompilerService.CompiledBundle bundle = compilerService.compileScenarioBundle(scenarioId);

		SimulationService.SimulationSummary summary = executor.executeCompiledBundle(
			bundle,
			new SimulationService.SimulationRequest(2, 1L, 100, false),
			null
		);

		assertEquals(2, summary.runs().size());
		assertTrue(summary.runs().stream().allMatch(run -> run.trace().isEmpty()));
	}

	private Map<String, Object> validGraph() {
		return Map.of(
			"id",
			"scenario-id",
			"name",
			"Scenario Graph",
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
}
