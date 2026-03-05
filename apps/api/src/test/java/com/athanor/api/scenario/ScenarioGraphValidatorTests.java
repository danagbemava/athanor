package com.athanor.api.scenario;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class ScenarioGraphValidatorTests {

	private final ScenarioGraphValidator validator = new ScenarioGraphValidator();

	@Test
	void validatesWellFormedGraph() {
		ValidationResult result = validator.validate(validGraph());

		assertTrue(result.valid(), "expected graph to be valid");
		assertTrue(result.errors().isEmpty(), "expected no validation errors");
	}

	@Test
	void failsWhenEntryNodeMissing() {
		Map<String, Object> graph = validGraph();
		graph.remove("entry_node_id");

		ValidationResult result = validator.validate(graph);

		assertFalse(result.valid());
		assertTrue(result.errors().stream().anyMatch(msg -> msg.code().equals("entry_node")));
	}

	@Test
	void failsWhenReferenceTargetDoesNotExist() {
		Map<String, Object> graph = validGraph();
		List<Map<String, Object>> edges = castEdgeList(graph.get("edges"));
		edges.add(Map.of("from", "start", "to", "missing"));

		ValidationResult result = validator.validate(graph);

		assertFalse(result.valid());
		assertTrue(result.errors().stream().anyMatch(msg -> msg.code().equals("next_node_reference")));
	}

	@Test
	void failsWhenChanceWeightsDontSumToOne() {
		Map<String, Object> graph = validGraph();
		@SuppressWarnings("unchecked")
		List<Map<String, Object>> nodes = (List<Map<String, Object>>) graph.get("nodes");
		nodes.add(Map.of(
			"id", "chance",
			"type", "ChanceNode",
			"chance_options", List.of(
				Map.of("to", "terminal", "weight", 0.3),
				Map.of("to", "terminal-2", "weight", 0.3)
			)
		));
		nodes.add(Map.of("id", "terminal-2", "type", "TerminalNode"));

		List<Map<String, Object>> edges = castEdgeList(graph.get("edges"));
		edges.clear();
		edges.add(Map.of("from", "start", "to", "chance"));

		ValidationResult result = validator.validate(graph);

		assertFalse(result.valid());
		assertTrue(result.errors().stream().anyMatch(msg -> msg.code().equals("chance_weights")));
	}

	@Test
	void failsWhenGraphContainsCycle() {
		Map<String, Object> graph = validGraph();
		List<Map<String, Object>> edges = castEdgeList(graph.get("edges"));
		edges.add(Map.of("from", "terminal", "to", "start"));

		ValidationResult result = validator.validate(graph);

		assertFalse(result.valid());
		assertTrue(result.errors().stream().anyMatch(msg -> msg.code().equals("graph_cycle")));
	}

	@Test
	void warnsOnUnreachableNonTerminalNode() {
		Map<String, Object> graph = validGraph();
		@SuppressWarnings("unchecked")
		List<Map<String, Object>> nodes = (List<Map<String, Object>>) graph.get("nodes");
		nodes.add(Map.of("id", "orphan", "type", "DecisionNode"));

		ValidationResult result = validator.validate(graph);

		assertTrue(result.valid(), "unreachable non-terminal nodes should be warnings");
		assertTrue(result.warnings().stream().anyMatch(msg -> msg.code().equals("unreachable_nodes")));
	}

	private Map<String, Object> validGraph() {
		Map<String, Object> graph = new LinkedHashMap<>();
		graph.put("id", "scenario-1");
		graph.put("name", "Validation Sample");
		graph.put("version", 1);
		graph.put("entry_node_id", "start");
		graph.put("nodes", new ArrayList<>(List.of(
			new LinkedHashMap<>(Map.of("id", "start", "type", "DecisionNode")),
			new LinkedHashMap<>(Map.of("id", "terminal", "type", "TerminalNode"))
		)));
		graph.put("edges", new ArrayList<>(List.of(
			new LinkedHashMap<>(Map.of("from", "start", "to", "terminal"))
		)));
		return graph;
	}

	@SuppressWarnings("unchecked")
	private List<Map<String, Object>> castEdgeList(Object value) {
		return (List<Map<String, Object>>) value;
	}
}
