package com.athanor.api.scenario;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Component;

@Component
public class ScenarioGraphValidator {

	private static final double CHANCE_SUM_TOLERANCE = 0.001d;

	public ValidationResult validate(Map<String, Object> graph) {
		List<ValidationMessage> errors = new ArrayList<>();
		List<ValidationMessage> warnings = new ArrayList<>();

		if (graph == null) {
			errors.add(error("schema", "graph payload is required"));
			return new ValidationResult(false, errors, warnings);
		}

		validateSchema(graph, errors);

		Map<String, Map<String, Object>> nodesById = parseNodes(graph, errors);
		List<Transition> transitions = parseTransitions(graph, nodesById, errors);

		validateReferencedNodes(nodesById, transitions, errors);

		String entryNodeId = readString(graph, "entryNodeId", "entry_node_id");
		if (entryNodeId == null || entryNodeId.isBlank()) {
			errors.add(error("entry_node", "entry node id is required (`entryNodeId` or `entry_node_id`)"));
		} else if (!nodesById.containsKey(entryNodeId)) {
			errors.add(error("entry_node", "entry node '" + entryNodeId + "' does not exist"));
		}

		validateChanceWeights(nodesById, transitions, errors);

		Map<String, Set<String>> adjacency = buildAdjacency(nodesById.keySet(), transitions);
		if (containsCycle(adjacency)) {
			errors.add(error("graph_cycle", "graph must be acyclic (DAG)"));
		}

		if (entryNodeId != null && nodesById.containsKey(entryNodeId)) {
			Set<String> reachable = collectReachable(entryNodeId, adjacency);
			Set<String> terminalIds = terminalNodeIds(nodesById);

			for (String terminalId : terminalIds) {
				if (!reachable.contains(terminalId)) {
					errors.add(error("terminal_reachability", "terminal node '" + terminalId + "' is not reachable from entry"));
				}
			}

			Set<String> unreachableNonTerminal = new LinkedHashSet<>();
			for (String nodeId : nodesById.keySet()) {
				if (!reachable.contains(nodeId) && !terminalIds.contains(nodeId)) {
					unreachableNonTerminal.add(nodeId);
				}
			}
			if (!unreachableNonTerminal.isEmpty()) {
				warnings.add(warn("unreachable_nodes", "unreachable nodes: " + String.join(", ", unreachableNonTerminal)));
			}
		}

		return new ValidationResult(errors.isEmpty(), errors, warnings);
	}

	private void validateSchema(Map<String, Object> graph, List<ValidationMessage> errors) {
		requireString(graph, "id", "schema", errors);
		requireString(graph, "name", "schema", errors);

		Object version = graph.get("version");
		if (!(version instanceof Number number) || number.intValue() < 1) {
			errors.add(error("schema", "field `version` must be an integer >= 1"));
		}

		if (!(graph.get("nodes") instanceof List<?>)) {
			errors.add(error("schema", "field `nodes` must be an array"));
		}
		if (!(graph.get("edges") instanceof List<?>)) {
			errors.add(error("schema", "field `edges` must be an array"));
		}
	}

	private Map<String, Map<String, Object>> parseNodes(Map<String, Object> graph, List<ValidationMessage> errors) {
		Map<String, Map<String, Object>> nodes = new LinkedHashMap<>();
		Object rawNodes = graph.get("nodes");
		if (!(rawNodes instanceof List<?> nodeList)) {
			return nodes;
		}

		for (int index = 0; index < nodeList.size(); index++) {
			Object rawNode = nodeList.get(index);
			if (!(rawNode instanceof Map<?, ?> nodeMapRaw)) {
				errors.add(error("schema", "nodes[" + index + "] must be an object"));
				continue;
			}
			Map<String, Object> node = toObjectMap(nodeMapRaw);
			String nodeId = readString(node, "id");
			if (nodeId == null || nodeId.isBlank()) {
				errors.add(error("schema", "nodes[" + index + "].id is required"));
				continue;
			}

			String nodeType = normalizeNodeType(readString(node, "type"));
			if (nodeType == null) {
				errors.add(error(
					"schema",
					"nodes[" + index + "].type must be one of DecisionNode, ChanceNode, TerminalNode (or decision/chance/terminal)"
				));
			}

			if (nodes.containsKey(nodeId)) {
				errors.add(error("schema", "duplicate node id: " + nodeId));
				continue;
			}
			nodes.put(nodeId, node);
		}
		return nodes;
	}

	private List<Transition> parseTransitions(
		Map<String, Object> graph,
		Map<String, Map<String, Object>> nodesById,
		List<ValidationMessage> errors
	) {
		List<Transition> transitions = new ArrayList<>();
		Object rawEdges = graph.get("edges");
		if (rawEdges instanceof List<?> edgeList) {
			for (int index = 0; index < edgeList.size(); index++) {
				Object rawEdge = edgeList.get(index);
				if (!(rawEdge instanceof Map<?, ?> edgeMapRaw)) {
					errors.add(error("schema", "edges[" + index + "] must be an object"));
					continue;
				}
				Map<String, Object> edge = toObjectMap(edgeMapRaw);
				String from = readString(edge, "from");
				String to = readString(edge, "to");
				if (from == null || from.isBlank() || to == null || to.isBlank()) {
					errors.add(error("schema", "edges[" + index + "] must include non-empty `from` and `to`"));
					continue;
				}
				transitions.add(new Transition("edge", from, to, readWeight(edge)));
			}
		}

		for (Map.Entry<String, Map<String, Object>> nodeEntry : nodesById.entrySet()) {
			String sourceNodeId = nodeEntry.getKey();
			Map<String, Object> node = nodeEntry.getValue();

			addOptionTransitions(
				sourceNodeId,
				node,
				"decision_options",
				"decisionOptions",
				false,
				transitions,
				errors
			);
			addOptionTransitions(
				sourceNodeId,
				node,
				"chance_options",
				"chanceOptions",
				true,
				transitions,
				errors
			);
		}

		return transitions;
	}

	private void addOptionTransitions(
		String sourceNodeId,
		Map<String, Object> node,
		String snakeCaseKey,
		String camelCaseKey,
		boolean expectsWeight,
		List<Transition> transitions,
		List<ValidationMessage> errors
	) {
		Object rawOptions = node.containsKey(snakeCaseKey) ? node.get(snakeCaseKey) : node.get(camelCaseKey);
		if (rawOptions == null) {
			return;
		}
		if (!(rawOptions instanceof List<?> optionList)) {
			errors.add(error("schema", "node '" + sourceNodeId + "' field `" + snakeCaseKey + "` must be an array"));
			return;
		}

		Set<String> seenTargets = expectsWeight ? new LinkedHashSet<>() : null;
		for (int index = 0; index < optionList.size(); index++) {
			Object rawOption = optionList.get(index);
			if (!(rawOption instanceof Map<?, ?> optionRaw)) {
				errors.add(error("schema", "node '" + sourceNodeId + "' option at index " + index + " must be an object"));
				continue;
			}
			Map<String, Object> option = toObjectMap(optionRaw);
			String target = readString(option, "to", "next_node_id", "nextNodeId");
			if (target == null || target.isBlank()) {
				errors.add(error(
					"next_node_reference",
					"node '" + sourceNodeId + "' option at index " + index + " is missing target (`to`/`next_node_id`)"
				));
				continue;
			}
			if (expectsWeight && !seenTargets.add(target)) {
				errors.add(error(
					"chance_destinations",
					"chance node '" + sourceNodeId + "' has duplicate destination '" + target + "'"
				));
			}

			Double weight = readWeight(option);
			if (expectsWeight && weight == null) {
				errors.add(error("chance_weights", "chance option from node '" + sourceNodeId + "' is missing numeric `weight`"));
			}

			transitions.add(new Transition(expectsWeight ? "chance_option" : "decision_option", sourceNodeId, target, weight));
		}
	}

	private void validateReferencedNodes(
		Map<String, Map<String, Object>> nodesById,
		List<Transition> transitions,
		List<ValidationMessage> errors
	) {
		for (Transition transition : transitions) {
			if (!nodesById.containsKey(transition.from())) {
				errors.add(error("next_node_reference", "source node '" + transition.from() + "' does not exist"));
			}
			if (!nodesById.containsKey(transition.to())) {
				errors.add(error("next_node_reference", "target node '" + transition.to() + "' does not exist"));
			}
		}
	}

	private void validateChanceWeights(
		Map<String, Map<String, Object>> nodesById,
		List<Transition> transitions,
		List<ValidationMessage> errors
	) {
		Map<String, List<Double>> chanceWeightsByNode = new HashMap<>();

		for (Transition transition : transitions) {
			if (!"chance_option".equals(transition.kind())) {
				continue;
			}
			chanceWeightsByNode.computeIfAbsent(transition.from(), ignored -> new ArrayList<>())
				.add(transition.weight() == null ? 0d : transition.weight());
		}

		for (Transition transition : transitions) {
			if (!"edge".equals(transition.kind())) {
				continue;
			}
			Map<String, Object> sourceNode = nodesById.get(transition.from());
			if (sourceNode == null) {
				continue;
			}
			if (!"chance".equals(normalizeNodeType(readString(sourceNode, "type")))) {
				continue;
			}
			chanceWeightsByNode.computeIfAbsent(transition.from(), ignored -> new ArrayList<>());
			if (transition.weight() != null) {
				chanceWeightsByNode.get(transition.from()).add(transition.weight());
			}
		}

		for (Map.Entry<String, Map<String, Object>> entry : nodesById.entrySet()) {
			String nodeId = entry.getKey();
			String nodeType = normalizeNodeType(readString(entry.getValue(), "type"));
			if (!"chance".equals(nodeType)) {
				continue;
			}

			List<Double> weights = chanceWeightsByNode.getOrDefault(nodeId, List.of());
			if (weights.isEmpty()) {
				errors.add(error("chance_weights", "chance node '" + nodeId + "' has no probability weights"));
				continue;
			}

			double sum = 0d;
			for (Double weight : weights) {
				if (weight == null) {
					continue;
				}
				if (weight < 0) {
					errors.add(error("chance_weights", "chance node '" + nodeId + "' has negative weight"));
				}
				sum += weight;
			}
			if (Math.abs(sum - 1d) > CHANCE_SUM_TOLERANCE) {
				errors.add(error(
					"chance_weights",
					"chance node '" + nodeId + "' weights must sum to 1.0 ± " + CHANCE_SUM_TOLERANCE + " (was " + sum + ")"
				));
			}
		}
	}

	private Map<String, Set<String>> buildAdjacency(Set<String> nodeIds, List<Transition> transitions) {
		Map<String, Set<String>> adjacency = new LinkedHashMap<>();
		for (String nodeId : nodeIds) {
			adjacency.put(nodeId, new LinkedHashSet<>());
		}

		for (Transition transition : transitions) {
			if (!adjacency.containsKey(transition.from()) || !adjacency.containsKey(transition.to())) {
				continue;
			}
			adjacency.get(transition.from()).add(transition.to());
		}

		return adjacency;
	}

	private boolean containsCycle(Map<String, Set<String>> adjacency) {
		Set<String> visited = new HashSet<>();
		Set<String> inStack = new HashSet<>();

		for (String nodeId : adjacency.keySet()) {
			if (dfsCycle(nodeId, adjacency, visited, inStack)) {
				return true;
			}
		}
		return false;
	}

	private boolean dfsCycle(
		String nodeId,
		Map<String, Set<String>> adjacency,
		Set<String> visited,
		Set<String> inStack
	) {
		if (inStack.contains(nodeId)) {
			return true;
		}
		if (visited.contains(nodeId)) {
			return false;
		}

		visited.add(nodeId);
		inStack.add(nodeId);
		for (String next : adjacency.getOrDefault(nodeId, Set.of())) {
			if (dfsCycle(next, adjacency, visited, inStack)) {
				return true;
			}
		}
		inStack.remove(nodeId);
		return false;
	}

	private Set<String> collectReachable(String entryNodeId, Map<String, Set<String>> adjacency) {
		Set<String> reachable = new LinkedHashSet<>();
		ArrayDeque<String> queue = new ArrayDeque<>();
		queue.add(entryNodeId);

		while (!queue.isEmpty()) {
			String node = queue.removeFirst();
			if (!reachable.add(node)) {
				continue;
			}
			for (String next : adjacency.getOrDefault(node, Set.of())) {
				queue.addLast(next);
			}
		}

		return reachable;
	}

	private Set<String> terminalNodeIds(Map<String, Map<String, Object>> nodesById) {
		Set<String> terminalIds = new LinkedHashSet<>();
		for (Map.Entry<String, Map<String, Object>> entry : nodesById.entrySet()) {
			String normalized = normalizeNodeType(readString(entry.getValue(), "type"));
			if ("terminal".equals(normalized)) {
				terminalIds.add(entry.getKey());
			}
		}
		return terminalIds;
	}

	private void requireString(
		Map<String, Object> root,
		String fieldName,
		String code,
		List<ValidationMessage> errors
	) {
		Object value = root.get(fieldName);
		if (!(value instanceof String str) || str.isBlank()) {
			errors.add(error(code, "field `" + fieldName + "` must be a non-empty string"));
		}
	}

	private String readString(Map<String, Object> source, String... keys) {
		for (String key : keys) {
			Object value = source.get(key);
			if (value instanceof String str && !str.isBlank()) {
				return str;
			}
		}
		return null;
	}

	private Double readWeight(Map<String, Object> source) {
		Object weight = source.containsKey("weight") ? source.get("weight") : source.get("probability");
		if (weight instanceof Number number) {
			return number.doubleValue();
		}
		return null;
	}

	private String normalizeNodeType(String rawType) {
		if (rawType == null) {
			return null;
		}
		return switch (rawType) {
			case "DecisionNode", "decision" -> "decision";
			case "ChanceNode", "chance" -> "chance";
			case "TerminalNode", "terminal" -> "terminal";
			default -> null;
		};
	}

	private ValidationMessage error(String code, String message) {
		return new ValidationMessage(code, message);
	}

	private ValidationMessage warn(String code, String message) {
		return new ValidationMessage(code, message);
	}

	private Map<String, Object> toObjectMap(Map<?, ?> value) {
		Map<String, Object> result = new LinkedHashMap<>();
		for (Map.Entry<?, ?> entry : value.entrySet()) {
			if (entry.getKey() instanceof String key) {
				result.put(key, entry.getValue());
			}
		}
		return result;
	}

	private record Transition(String kind, String from, String to, Double weight) {}
}
