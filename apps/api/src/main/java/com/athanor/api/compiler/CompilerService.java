package com.athanor.api.compiler;

import com.athanor.api.scenario.ScenarioGraphValidator;
import com.athanor.api.scenario.ScenarioService;
import com.athanor.api.scenario.ValidationResult;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Service;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

@Service
public class CompilerService {

	private static final String BUNDLE_SPEC_VERSION = "0.1.0-scaffold";
	private static final String COMPILER_VERSION = "0.0.1-SNAPSHOT";
	private static final String RUNTIME_VERSION = "0.1.0-scaffold";
	private static final String RNG_ALGORITHM = "PCG32";
	private static final String RNG_VERSION = "1";
	private static final String FLOAT_MODE = "IEEE754_STRICT";
	private static final String EXPRESSION_LANGUAGE_VERSION = "0.1.0-scaffold";
	private static final String ITERATION_ORDER_GUARANTEE = "topological";
	private static final HexFormat HEX = HexFormat.of();

	private final ScenarioService scenarioService;
	private final ScenarioGraphValidator graphValidator;
	private final BundleStore bundleStore;
	private final ObjectMapper objectMapper;
	private final Map<String, BundleRegistryEntry> registry = new ConcurrentHashMap<>();

	public CompilerService(
		ScenarioService scenarioService,
		ScenarioGraphValidator graphValidator,
		BundleStore bundleStore,
		ObjectMapper objectMapper
	) {
		this.scenarioService = scenarioService;
		this.graphValidator = graphValidator;
		this.bundleStore = bundleStore;
		this.objectMapper = objectMapper;
	}

	public CompilationResult compileLatestScenario(UUID scenarioId) {
		CompiledBundle bundle = compileScenarioBundle(scenarioId);
		BundleStore.StoreResult storeResult = store(
			bundle.bundleHash(),
			canonicalJson(bundle.payload())
		);
		BundleRegistryEntry entry = registry.computeIfAbsent(
			bundle.bundleHash(),
			ignored -> new BundleRegistryEntry(
				bundle.bundleHash(),
				bundle.scenarioId(),
				bundle.versionId(),
				bundle.versionNumber(),
				"draft",
				storeResult.storedAt()
			)
		);

		return new CompilationResult(
			entry.scenarioId(),
			entry.versionId(),
			entry.versionNumber(),
			entry.bundleHash(),
			entry.storedAt()
		);
	}

	public CompiledBundle compileScenarioBundle(UUID scenarioId) {
		ScenarioService.LatestScenarioVersionSnapshot latestVersion = scenarioService.latestVersionSnapshot(
			scenarioId
		);
		ScenarioService.ScenarioValidationSnapshot validation = validate(latestVersion);
		if (!validation.valid()) {
			throw new CompilerValidationException(validation);
		}

		Map<String, Object> graph = latestVersion.graph();
		List<String> orderedNodeIds = orderedNodeIds(graph);
		Map<String, Map<String, Object>> nodesById = nodesById(graph);
		Map<String, List<String>> edgesBySource = edgesBySource(graph);

		Map<String, Object> artifactWithoutHash = new LinkedHashMap<>();
		artifactWithoutHash.put("entry_node_id", readString(graph, "entry_node_id", "entryNodeId"));
		artifactWithoutHash.put("header", bundleHeader());
		artifactWithoutHash.put("initial_state", readObject(graph, "initial_state", "initialState"));
		artifactWithoutHash.put("nodes", compileNodes(orderedNodeIds, nodesById, edgesBySource));
		artifactWithoutHash.values().removeIf(value -> value == null);
		String bundleHash = sha256Hex(canonicalJson(artifactWithoutHash));
		Map<String, Object> artifact = new LinkedHashMap<>(artifactWithoutHash);
		artifact.put("bundle_hash", bundleHash);
		return new CompiledBundle(
			latestVersion.scenarioId(),
			latestVersion.versionId(),
			latestVersion.versionNumber(),
			bundleHash,
			artifact
		);
	}

	public Map<String, Object> compiledBundlePayload(UUID scenarioId) {
		return compileScenarioBundle(scenarioId).payload();
	}

	private ScenarioService.ScenarioValidationSnapshot validate(
		ScenarioService.LatestScenarioVersionSnapshot latestVersion
	) {
		ValidationResult result = graphValidator.validate(latestVersion.graph());
		return new ScenarioService.ScenarioValidationSnapshot(
			latestVersion.scenarioId(),
			latestVersion.versionId(),
			latestVersion.versionNumber(),
			result.valid(),
			result.errors(),
			result.warnings()
		);
	}

	private BundleStore.StoreResult store(String bundleHash, byte[] payload) {
		try {
			return bundleStore.store(bundleHash, payload);
		} catch (IOException exception) {
			throw new IllegalStateException("failed to store compiled bundle", exception);
		}
	}

	private byte[] canonicalJson(Map<String, Object> payload) {
		try {
			return objectMapper.writeValueAsBytes(canonicalizeObject(payload));
		} catch (Exception exception) {
			throw new IllegalStateException("failed to serialize bundle payload", exception);
		}
	}

	private String sha256Hex(byte[] canonicalJson) {
		try {
			MessageDigest digest = MessageDigest.getInstance("SHA-256");
			return HEX.formatHex(digest.digest(canonicalJson));
		} catch (NoSuchAlgorithmException exception) {
			throw new IllegalStateException("SHA-256 is not available", exception);
		}
	}

	private Map<String, Object> bundleHeader() {
		Map<String, Object> header = new LinkedHashMap<>();
		header.put("bundle_spec_version", BUNDLE_SPEC_VERSION);
		header.put("compiler_version", COMPILER_VERSION);
		header.put("runtime_version", RUNTIME_VERSION);
		header.put("rng_algorithm", RNG_ALGORITHM);
		header.put("rng_version", RNG_VERSION);
		header.put("float_mode", FLOAT_MODE);
		header.put("expression_language_version", EXPRESSION_LANGUAGE_VERSION);
		header.put("iteration_order_guarantee", ITERATION_ORDER_GUARANTEE);
		return header;
	}

	private List<Map<String, Object>> compileNodes(
		List<String> orderedNodeIds,
		Map<String, Map<String, Object>> nodesById,
		Map<String, List<String>> edgesBySource
	) {
		List<Map<String, Object>> compiledNodes = new ArrayList<>();
		for (String nodeId : orderedNodeIds) {
			Map<String, Object> source = nodesById.get(nodeId);
			if (source == null) {
				continue;
			}

			Map<String, Object> node = new LinkedHashMap<>();
			String normalizedType = normalizeNodeType(readString(source, "type"));
			node.put("id", nodeId);
			node.put("type", normalizedType);

			List<Map<String, Object>> decisionOptions = optionMaps(
				source,
				"decision_options",
				"decisionOptions"
			);
			List<Map<String, Object>> chanceOptions = optionMaps(
				source,
				"chance_options",
				"chanceOptions"
			);

			if ("decision".equals(normalizedType)) {
				node.put(
					"decision_options",
					decisionOptions.isEmpty()
						? fallbackDecisionOptions(edgesBySource.getOrDefault(nodeId, List.of()))
						: decisionOptions
				);
			}

			if ("chance".equals(normalizedType)) {
				node.put(
					"chance_options",
					chanceOptions.isEmpty()
						? fallbackChanceOptions(edgesBySource.getOrDefault(nodeId, List.of()))
						: chanceOptions
				);
			}

			List<Map<String, Object>> effects = objectList(source, "effects");
			if (!effects.isEmpty()) {
				node.put("effects", effects);
			}

			String outcome = readString(source, "outcome");
			if (outcome != null && !outcome.isBlank()) {
				node.put("outcome", outcome);
			}

			node.values().removeIf(value -> value == null);
			compiledNodes.add(node);
		}

		return compiledNodes;
	}

	private List<Map<String, Object>> fallbackDecisionOptions(List<String> targets) {
		List<Map<String, Object>> options = new ArrayList<>();
		for (String target : targets.stream().sorted().toList()) {
			Map<String, Object> option = new LinkedHashMap<>();
			option.put("to", target);
			options.add(option);
		}
		return options;
	}

	private List<Map<String, Object>> fallbackChanceOptions(List<String> targets) {
		List<String> sortedTargets = targets.stream().sorted().toList();
		if (sortedTargets.isEmpty()) {
			return List.of();
		}

		double weight = 1d / sortedTargets.size();
		List<Map<String, Object>> options = new ArrayList<>();
		for (String target : sortedTargets) {
			options.add(Map.of("to", target, "weight", weight));
		}
		return options;
	}

	private List<String> orderedNodeIds(Map<String, Object> graph) {
		Map<String, Set<String>> adjacency = buildAdjacency(graph);
		String entryNodeId = readString(graph, "entry_node_id", "entryNodeId");
		Set<String> reachable = reachableFrom(entryNodeId, adjacency);
		List<String> ordered = topologicalSort(reachable, adjacency);

		Set<String> remaining = new LinkedHashSet<>(adjacency.keySet());
		remaining.removeAll(reachable);
		ordered.addAll(topologicalSort(remaining, adjacency));
		return ordered;
	}

	private List<String> topologicalSort(Set<String> targetNodes, Map<String, Set<String>> adjacency) {
		if (targetNodes.isEmpty()) {
			return new ArrayList<>();
		}

		Map<String, Integer> indegree = new LinkedHashMap<>();
		for (String nodeId : targetNodes) {
			indegree.put(nodeId, 0);
		}
		for (Map.Entry<String, Set<String>> entry : adjacency.entrySet()) {
			if (!targetNodes.contains(entry.getKey())) {
				continue;
			}
			for (String target : entry.getValue()) {
				if (targetNodes.contains(target)) {
					indegree.computeIfPresent(target, (ignored, current) -> current + 1);
				}
			}
		}

		PriorityQueue<String> ready = new PriorityQueue<>();
		for (Map.Entry<String, Integer> entry : indegree.entrySet()) {
			if (entry.getValue() == 0) {
				ready.add(entry.getKey());
			}
		}

		List<String> ordered = new ArrayList<>();
		while (!ready.isEmpty()) {
			String current = ready.poll();
			ordered.add(current);
			for (String target : adjacency.getOrDefault(current, Set.of()).stream().sorted().toList()) {
				if (!indegree.containsKey(target)) {
					continue;
				}
				int next = indegree.computeIfPresent(target, (ignored, value) -> value - 1);
				if (next == 0) {
					ready.add(target);
				}
			}
		}

		if (ordered.size() != targetNodes.size()) {
			throw new IllegalStateException("deterministic topological ordering failed");
		}
		return ordered;
	}

	private Set<String> reachableFrom(String entryNodeId, Map<String, Set<String>> adjacency) {
		if (entryNodeId == null || !adjacency.containsKey(entryNodeId)) {
			return Set.of();
		}

		Set<String> reachable = new LinkedHashSet<>();
		ArrayDeque<String> queue = new ArrayDeque<>();
		queue.add(entryNodeId);
		while (!queue.isEmpty()) {
			String current = queue.removeFirst();
			if (!reachable.add(current)) {
				continue;
			}
			for (String target : adjacency.getOrDefault(current, Set.of()).stream().sorted().toList()) {
				queue.addLast(target);
			}
		}
		return reachable;
	}

	private Map<String, Set<String>> buildAdjacency(Map<String, Object> graph) {
		Map<String, Map<String, Object>> nodesById = nodesById(graph);
		Map<String, Set<String>> adjacency = new LinkedHashMap<>();
		for (String nodeId : nodesById.keySet()) {
			adjacency.put(nodeId, new LinkedHashSet<>());
		}

		for (Map.Entry<String, List<String>> entry : edgesBySource(graph).entrySet()) {
			adjacency.computeIfAbsent(entry.getKey(), ignored -> new LinkedHashSet<>())
				.addAll(entry.getValue());
		}

		for (Map.Entry<String, Map<String, Object>> entry : nodesById.entrySet()) {
			String nodeId = entry.getKey();
			for (Map<String, Object> option : optionMaps(entry.getValue(), "decision_options", "decisionOptions")) {
				String target = readString(option, "to", "next_node_id", "nextNodeId");
				if (target != null && !target.isBlank()) {
					adjacency.computeIfAbsent(nodeId, ignored -> new LinkedHashSet<>()).add(target);
				}
			}
			for (Map<String, Object> option : optionMaps(entry.getValue(), "chance_options", "chanceOptions")) {
				String target = readString(option, "to", "next_node_id", "nextNodeId");
				if (target != null && !target.isBlank()) {
					adjacency.computeIfAbsent(nodeId, ignored -> new LinkedHashSet<>()).add(target);
				}
			}
		}

		return adjacency;
	}

	private Map<String, List<String>> edgesBySource(Map<String, Object> graph) {
		Map<String, List<String>> edgesBySource = new LinkedHashMap<>();
		for (Map<String, Object> edge : objectList(graph, "edges")) {
			String from = readString(edge, "from");
			String to = readString(edge, "to");
			if (from == null || from.isBlank() || to == null || to.isBlank()) {
				continue;
			}
			edgesBySource.computeIfAbsent(from, ignored -> new ArrayList<>()).add(to);
		}
		return edgesBySource;
	}

	private Map<String, Map<String, Object>> nodesById(Map<String, Object> graph) {
		Map<String, Map<String, Object>> nodesById = new LinkedHashMap<>();
		for (Map<String, Object> node : objectList(graph, "nodes")) {
			String nodeId = readString(node, "id");
			if (nodeId != null && !nodeId.isBlank()) {
				nodesById.put(nodeId, deepCopy(node));
			}
		}
		return nodesById;
	}

	private List<Map<String, Object>> optionMaps(
		Map<String, Object> source,
		String snakeCaseKey,
		String camelCaseKey
	) {
		Object rawValue = source.containsKey(snakeCaseKey) ? source.get(snakeCaseKey) : source.get(camelCaseKey);
		if (!(rawValue instanceof List<?> items)) {
			return List.of();
		}

		List<Map<String, Object>> options = new ArrayList<>();
		for (Object item : items) {
			if (item instanceof Map<?, ?> map) {
				options.add(toObjectMap(map));
			}
		}
		return options;
	}

	private List<Map<String, Object>> objectList(Map<String, Object> source, String key) {
		Object rawValue = source.get(key);
		if (!(rawValue instanceof List<?> items)) {
			return List.of();
		}

		List<Map<String, Object>> objects = new ArrayList<>();
		for (Object item : items) {
			if (item instanceof Map<?, ?> map) {
				objects.add(toObjectMap(map));
			}
		}
		return objects;
	}

	private Object readObject(Map<String, Object> source, String... keys) {
		for (String key : keys) {
			if (source.containsKey(key)) {
				return deepCopyObject(source.get(key));
			}
		}
		return null;
	}

	private String readString(Map<String, Object> source, String... keys) {
		for (String key : keys) {
			Object value = source.get(key);
			if (value instanceof String stringValue) {
				return stringValue;
			}
		}
		return null;
	}

	private String normalizeNodeType(String rawType) {
		if (rawType == null || rawType.isBlank()) {
			return null;
		}

		return switch (rawType) {
			case "DecisionNode", "decision" -> "decision";
			case "ChanceNode", "chance" -> "chance";
			case "TerminalNode", "terminal" -> "terminal";
			default -> rawType.toLowerCase();
		};
	}

	private Map<String, Object> toObjectMap(Map<?, ?> source) {
		return objectMapper.convertValue(source, new TypeReference<>() {});
	}

	private Map<String, Object> deepCopy(Map<String, Object> source) {
		return objectMapper.convertValue(source, new TypeReference<>() {});
	}

	private Object deepCopyObject(Object source) {
		return objectMapper.convertValue(source, Object.class);
	}

	private Object canonicalizeObject(Object source) {
		if (source instanceof Map<?, ?> map) {
			List<String> keys = map.keySet().stream().map(String::valueOf).sorted().toList();
			Map<String, Object> sorted = new LinkedHashMap<>();
			for (String key : keys) {
				sorted.put(key, canonicalizeObject(map.get(key)));
			}
			return sorted;
		}

		if (source instanceof List<?> list) {
			List<Object> sorted = new ArrayList<>();
			for (Object item : list) {
				sorted.add(canonicalizeObject(item));
			}
			return sorted;
		}

		return source;
	}

	public record CompilationResult(
		UUID scenarioId,
		UUID versionId,
		int versionNumber,
		String bundleHash,
		Instant storedAt
	) {}

	public record CompiledBundle(
		UUID scenarioId,
		UUID versionId,
		int versionNumber,
		String bundleHash,
		Map<String, Object> payload
	) {}

	record BundleRegistryEntry(
		String bundleHash,
		UUID scenarioId,
		UUID versionId,
		int versionNumber,
		String retentionClass,
		Instant storedAt
	) {}
}
