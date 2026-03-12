package com.athanor.api.scenario;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

class ScenarioServiceTests {

	private ScenarioService service;

	@BeforeEach
	void setUp() {
		service = ScenarioServiceTestFactory.create(new ObjectMapper());
	}

	@Test
	void createScenarioCreatesInitialDraftVersion() {
		ScenarioService.ScenarioSnapshot snapshot = service.createScenario(
			new ScenarioService.CreateScenarioCommand(
				"Scenario A",
				"description",
				validGraph("start", "terminal")
			)
		);

		assertEquals("Scenario A", snapshot.name());
		assertEquals(1, snapshot.versionCount());
		assertEquals(1, snapshot.version().number());
		assertEquals("draft", snapshot.version().state());
	}

	@Test
	void createVersionAppendsVersionWithoutReplacingPrevious() {
		ScenarioService.ScenarioSnapshot created = service.createScenario(
			new ScenarioService.CreateScenarioCommand(
				"Scenario A",
				"description",
				validGraph("start", "terminal")
			)
		);

		ScenarioService.ScenarioSnapshot updated = service.createVersion(
			created.scenarioId(),
			new ScenarioService.CreateVersionCommand(
				"Scenario B",
				"description updated",
				validGraph("entry", "finish")
			)
		);

		assertEquals("Scenario B", updated.name());
		assertEquals(2, updated.versionCount());
		assertEquals(2, updated.version().number());
	}

	@Test
	void createVersionThrowsWhenScenarioNotFound() {
		assertThrows(
			ScenarioNotFoundException.class,
			() -> service.createVersion(
				UUID.randomUUID(),
				new ScenarioService.CreateVersionCommand(
					"missing",
					null,
					validGraph("start", "terminal")
				)
			)
		);
	}

	@Test
	void validateLatestVersionReturnsExpectedErrors() {
		Map<String, Object> invalidGraph = new LinkedHashMap<>(validGraph("start", "terminal"));
		invalidGraph.remove("entry_node_id");

		ScenarioService.ScenarioSnapshot created = service.createScenario(
			new ScenarioService.CreateScenarioCommand("Scenario", null, invalidGraph)
		);

		ScenarioService.ScenarioValidationSnapshot validation = service.validateLatestVersion(created.scenarioId());

		assertFalse(validation.valid());
		assertTrue(validation.errors().stream().anyMatch(m -> m.code().equals("entry_node")));
	}

	@Test
	void createScenarioDeepCopiesGraph() {
		Map<String, Object> graph = mutableValidGraph("start", "terminal");
		ScenarioService.ScenarioSnapshot created = service.createScenario(
			new ScenarioService.CreateScenarioCommand("Scenario", null, graph)
		);

		@SuppressWarnings("unchecked")
		List<Map<String, Object>> nodes = (List<Map<String, Object>>) graph.get("nodes");
		nodes.clear();
		graph.remove("entry_node_id");

		ScenarioService.ScenarioValidationSnapshot validation = service.validateLatestVersion(created.scenarioId());
		assertTrue(validation.valid(), "stored graph should not be affected by external mutations");
	}

	private Map<String, Object> validGraph(String entryNodeId, String terminalNodeId) {
		return Map.of(
			"id", "scenario-id",
			"name", "Scenario Graph",
			"version", 1,
			"entry_node_id", entryNodeId,
			"nodes", List.of(
				Map.of("id", entryNodeId, "type", "DecisionNode"),
				Map.of("id", terminalNodeId, "type", "TerminalNode")
			),
			"edges", List.of(Map.of("from", entryNodeId, "to", terminalNodeId))
		);
	}

	private Map<String, Object> mutableValidGraph(String entryNodeId, String terminalNodeId) {
		Map<String, Object> graph = new LinkedHashMap<>();
		graph.put("id", "scenario-id");
		graph.put("name", "Scenario Graph");
		graph.put("version", 1);
		graph.put("entry_node_id", entryNodeId);
		graph.put("nodes", new ArrayList<>(List.of(
			new LinkedHashMap<>(Map.of("id", entryNodeId, "type", "DecisionNode")),
			new LinkedHashMap<>(Map.of("id", terminalNodeId, "type", "TerminalNode"))
		)));
		graph.put("edges", new ArrayList<>(List.of(
			new LinkedHashMap<>(Map.of("from", entryNodeId, "to", terminalNodeId))
		)));
		return graph;
	}
}
