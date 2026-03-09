package com.athanor.api.simulation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.athanor.api.compiler.CompilerValidationException;
import com.athanor.api.compiler.CompilerService;
import com.athanor.api.compiler.FilesystemBundleStore;
import com.athanor.api.scenario.ScenarioGraphValidator;
import com.athanor.api.scenario.ScenarioService;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import tools.jackson.databind.ObjectMapper;

class SimulationServiceTests {

	@TempDir
	Path tempDir;

	@Test
	void simulateLatestScenarioIsDeterministicForSameRequest() {
		TestFixture fixture = fixture();

		var scenario = fixture.scenarioService().createScenario(
			new ScenarioService.CreateScenarioCommand(
				"Deterministic Scenario",
				"Chance split",
				chanceGraph()
			)
		);

		SimulationService.SimulationSummary first = fixture.simulationService().simulateLatestScenario(
			scenario.scenarioId(),
			new SimulationService.SimulationRequest(8, 11L, 100, true)
		);
		SimulationService.SimulationSummary second = fixture.simulationService().simulateLatestScenario(
			scenario.scenarioId(),
			new SimulationService.SimulationRequest(8, 11L, 100, true)
		);

		assertEquals(first.bundleHash(), second.bundleHash());
		assertEquals(first.outcomeCounts(), second.outcomeCounts());
		assertEquals(first.runs(), second.runs());
		assertEquals("start", first.runs().getFirst().trace().getFirst().nodeId());
	}

	@Test
	void simulateLatestScenarioUsesDefaultsWhenRequestIsNull() {
		TestFixture fixture = fixture();
		var scenario = fixture.scenarioService().createScenario(
			new ScenarioService.CreateScenarioCommand("Defaults", "", chanceGraph())
		);

		SimulationService.SimulationSummary summary = fixture.simulationService().simulateLatestScenario(
			scenario.scenarioId(),
			null
		);

		assertEquals(25, summary.runCount());
		assertEquals(1L, summary.seedStart());
		assertEquals(10_000, summary.maxSteps());
		assertEquals(25, summary.runs().size());
	}

	@Test
	void simulateLatestScenarioAppliesEffectsAndGuards() {
		TestFixture fixture = fixture();

		var scenario = fixture.scenarioService().createScenario(
			new ScenarioService.CreateScenarioCommand(
				"Guarded Scenario",
				"Effects route a decision",
				guardedGraph()
			)
		);

		SimulationService.SimulationSummary summary = fixture.simulationService().simulateLatestScenario(
			scenario.scenarioId(),
			new SimulationService.SimulationRequest(1, 3L, 100, true)
		);

		assertEquals(Map.of("incremented", 1), summary.outcomeCounts());
		assertEquals(1.0d, summary.runs().getFirst().finalState().get("score"));
		assertEquals("success", summary.runs().getFirst().trace().getLast().nodeId());
	}

	@Test
	void simulateLatestScenarioSurfacesCompilerValidationForMissingNodeGraph() {
		TestFixture fixture = fixture();
		var scenario = fixture.scenarioService().createScenario(
			new ScenarioService.CreateScenarioCommand("Missing Target", "", missingNodeGraph())
		);

		CompilerValidationException exception = assertThrows(
			CompilerValidationException.class,
			() -> fixture.simulationService().simulateLatestScenario(
				scenario.scenarioId(),
				new SimulationService.SimulationRequest(1, 2L, 100, true)
			)
		);
		assertEquals(false, exception.validation().valid());
	}

	@Test
	void simulateLatestScenarioSurfacesCompilerValidationForZeroChanceWeights() {
		TestFixture fixture = fixture();
		var scenario = fixture.scenarioService().createScenario(
			new ScenarioService.CreateScenarioCommand("Zero Weights", "", zeroWeightChanceGraph())
		);

		CompilerValidationException exception = assertThrows(
			CompilerValidationException.class,
			() -> fixture.simulationService().simulateLatestScenario(
				scenario.scenarioId(),
				new SimulationService.SimulationRequest(1, 5L, 100, true)
			)
		);
		assertEquals(false, exception.validation().valid());
	}

	@Test
	void simulateLatestScenarioReturnsNoOptionsErrorForGuardedDecisionWithoutMatch() {
		TestFixture fixture = fixture();
		var scenario = fixture.scenarioService().createScenario(
			new ScenarioService.CreateScenarioCommand("Guard Miss", "", noOptionGraph())
		);

		SimulationService.SimulationSummary summary = fixture.simulationService().simulateLatestScenario(
			scenario.scenarioId(),
			new SimulationService.SimulationRequest(1, 1L, 100, true)
		);

		assertEquals(Map.of("error_no_options", 1), summary.outcomeCounts());
	}

	@Test
	void simulateLatestScenarioReturnsNoOptionsErrorForChanceGuardMiss() {
		TestFixture fixture = fixture();
		var scenario = fixture.scenarioService().createScenario(
			new ScenarioService.CreateScenarioCommand("Chance Guard Miss", "", noChanceOptionGraph())
		);

		SimulationService.SimulationSummary summary = fixture.simulationService().simulateLatestScenario(
			scenario.scenarioId(),
			new SimulationService.SimulationRequest(1, 1L, 100, true)
		);

		assertEquals(Map.of("error_no_options", 1), summary.outcomeCounts());
	}

	@Test
	void simulateLatestScenarioSurfacesCompilerValidationForCyclicGraph() {
		TestFixture fixture = fixture();
		var scenario = fixture.scenarioService().createScenario(
			new ScenarioService.CreateScenarioCommand("Overflow", "", overflowGraph())
		);

		CompilerValidationException exception = assertThrows(
			CompilerValidationException.class,
			() -> fixture.simulationService().simulateLatestScenario(
				scenario.scenarioId(),
				new SimulationService.SimulationRequest(1, 1L, 2, true)
			)
		);
		assertEquals(false, exception.validation().valid());
	}

	@Test
	void simulateLatestScenarioSurfacesCompilerValidationForInvalidNodeType() {
		TestFixture fixture = fixture();
		var scenario = fixture.scenarioService().createScenario(
			new ScenarioService.CreateScenarioCommand("Invalid Type", "", invalidTypeGraph())
		);

		CompilerValidationException exception = assertThrows(
			CompilerValidationException.class,
			() -> fixture.simulationService().simulateLatestScenario(
				scenario.scenarioId(),
				new SimulationService.SimulationRequest(1, 1L, 100, true)
			)
		);
		assertEquals(false, exception.validation().valid());
	}

	@Test
	void simulateLatestScenarioRejectsInvalidRequestBounds() {
		TestFixture fixture = fixture();
		String scenarioId = fixture
			.scenarioService()
			.createScenario(new ScenarioService.CreateScenarioCommand("Bounds", "", chanceGraph()))
			.scenarioId()
			.toString();

		assertThrows(
			IllegalArgumentException.class,
			() -> fixture.simulationService().simulateLatestScenario(
				java.util.UUID.fromString(scenarioId),
				new SimulationService.SimulationRequest(300, 1L, 100, true)
			)
		);
		assertThrows(
			IllegalArgumentException.class,
			() -> fixture.simulationService().simulateLatestScenario(
				java.util.UUID.fromString(scenarioId),
				new SimulationService.SimulationRequest(2, Long.MAX_VALUE, 100, true)
			)
		);
	}

	@Test
	void simulateLatestScenarioOmitsTraceWhenDisabled() {
		TestFixture fixture = fixture();
		var scenario = fixture.scenarioService().createScenario(
			new ScenarioService.CreateScenarioCommand("No Trace", "", chanceGraph())
		);

		SimulationService.SimulationSummary summary = fixture.simulationService().simulateLatestScenario(
			scenario.scenarioId(),
			new SimulationService.SimulationRequest(2, 1L, 100, false)
		);

		assertEquals(List.of(), summary.runs().getFirst().trace());
	}

	private TestFixture fixture() {
		ObjectMapper objectMapper = new ObjectMapper();
		ScenarioService scenarioService = new ScenarioService(
			new ScenarioGraphValidator(),
			objectMapper
		);
		CompilerService compilerService = new CompilerService(
			scenarioService,
			new ScenarioGraphValidator(),
			new FilesystemBundleStore(tempDir),
			objectMapper
		);
		SimulationService simulationService = new SimulationService(compilerService, objectMapper);
		return new TestFixture(scenarioService, simulationService);
	}

	private Map<String, Object> chanceGraph() {
		return Map.of(
			"id",
			"chance-graph",
			"name",
			"Chance Graph",
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
						Map.of("to", "approved", "weight", 0.65),
						Map.of("to", "declined", "weight", 0.35)
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

	private Map<String, Object> guardedGraph() {
		return Map.of(
			"id",
			"guarded-graph",
			"name",
			"Guarded Graph",
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
					"DecisionNode",
					"effects",
					List.of(Map.of("op", "increment", "path", "score", "value", 1)),
					"decision_options",
					List.of(
						Map.of("to", "success", "guard", Map.of("var", "score", "equals", 1)),
						Map.of("to", "fallback", "guard", Map.of("var", "score", "equals", 2))
					)
				),
				Map.of("id", "success", "type", "TerminalNode", "outcome", "incremented"),
				Map.of("id", "fallback", "type", "TerminalNode", "outcome", "missed")
			),
			"edges",
			List.of(
				Map.of("from", "start", "to", "success"),
				Map.of("from", "start", "to", "fallback")
			)
		);
	}

	private Map<String, Object> missingNodeGraph() {
		return Map.of(
			"id",
			"missing-node-graph",
			"name",
			"Missing Node Graph",
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
					"DecisionNode",
					"decision_options",
					List.of(Map.of("to", "ghost"))
				)
			),
			"edges",
			List.of(Map.of("from", "start", "to", "ghost"))
		);
	}

	private Map<String, Object> zeroWeightChanceGraph() {
		return Map.of(
			"id",
			"zero-weight-graph",
			"name",
			"Zero Weight Graph",
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
						Map.of("to", "approved", "weight", 0.0),
						Map.of("to", "declined", "weight", 0.0)
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

	private Map<String, Object> noOptionGraph() {
		return Map.of(
			"id",
			"no-option-graph",
			"name",
			"No Option Graph",
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
					"DecisionNode",
					"decision_options",
					List.of(
						Map.of("to", "success", "guard", Map.of("var", "status", "equals", "active"))
					)
				),
				Map.of("id", "success", "type", "TerminalNode", "outcome", "approved")
			),
			"edges",
			List.of(Map.of("from", "start", "to", "success"))
		);
	}

	private Map<String, Object> invalidTypeGraph() {
		return Map.of(
			"id",
			"invalid-type-graph",
			"name",
			"Invalid Type Graph",
			"version",
			1,
			"entry_node_id",
			"start",
			"nodes",
			List.of(Map.of("id", "start", "type", "")),
			"edges",
			List.of()
		);
	}

	private Map<String, Object> noChanceOptionGraph() {
		return Map.of(
			"id",
			"no-chance-option-graph",
			"name",
			"No Chance Option Graph",
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
						Map.of(
							"to",
							"approved",
							"weight",
							1.0,
							"guard",
							Map.of("var", "status", "equals", "active")
						)
					)
				),
				Map.of("id", "approved", "type", "TerminalNode", "outcome", "approved")
			),
			"edges",
			List.of(Map.of("from", "start", "to", "approved"))
		);
	}

	private Map<String, Object> overflowGraph() {
		return Map.of(
			"id",
			"overflow-graph",
			"name",
			"Overflow Graph",
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
					"DecisionNode",
					"decision_options",
					List.of(Map.of("to", "loop"))
				),
				Map.of(
					"id",
					"loop",
					"type",
					"DecisionNode",
					"decision_options",
					List.of(Map.of("to", "start"))
				)
			),
			"edges",
			List.of(
				Map.of("from", "start", "to", "loop"),
				Map.of("from", "loop", "to", "start")
			)
		);
	}

	private record TestFixture(
		ScenarioService scenarioService,
		SimulationService simulationService
	) {}
}
