package com.athanor.api.simulation;

import com.athanor.api.compiler.CompilerService;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import org.springframework.stereotype.Service;
import tools.jackson.databind.ObjectMapper;

@Service
public class SimulationService {

	private static final int DEFAULT_RUN_COUNT = 25;
	private static final long DEFAULT_SEED_START = 1L;
	private static final int DEFAULT_MAX_STEPS = 10_000;
	private static final int MAX_RUN_COUNT = 5_000;

	private final CompilerService compilerService;
	private final ObjectMapper objectMapper;

	public SimulationService(CompilerService compilerService, ObjectMapper objectMapper) {
		this.compilerService = compilerService;
		this.objectMapper = objectMapper;
	}

	public SimulationSummary simulateLatestScenario(
		UUID scenarioId,
		SimulationRequest request
	) {
		return simulateLatestScenario(scenarioId, request, null);
	}

	public SimulationSummary simulateLatestScenario(
		UUID scenarioId,
		SimulationRequest request,
		SimulationProgressListener progressListener
	) {
		CompilerService.CompiledBundle compiledBundle = compilerService.compileScenarioBundle(
			scenarioId
		);
		return simulateCompiledBundle(compiledBundle, request, progressListener);
	}

	public SimulationSummary simulateCompiledBundle(
		CompilerService.CompiledBundle compiledBundle,
		SimulationRequest request
	) {
		return simulateCompiledBundle(compiledBundle, request, null);
	}

	public SimulationSummary simulateCompiledBundle(
		CompilerService.CompiledBundle compiledBundle,
		SimulationRequest request,
		SimulationProgressListener progressListener
	) {
		SimulationRequest normalizedRequest = normalizeRequest(request);
		RuntimeBundle bundle = toRuntimeBundle(compiledBundle.payload());

		List<SimulationRun> runs = new ArrayList<>();
		Map<String, Integer> outcomeCounts = new LinkedHashMap<>();
		int totalSteps = 0;
		long seed = normalizedRequest.seedStart();
		for (int index = 0; index < normalizedRequest.runCount(); index++) {
			RunResult result = run(bundle, seed, normalizedRequest.maxSteps());
			runs.add(
				new SimulationRun(
					seed,
					result.outcome(),
					result.stepsTaken(),
					result.finalState(),
					normalizedRequest.trace() ? result.trace() : List.of()
				)
			);
			outcomeCounts.merge(result.outcome(), 1, Integer::sum);
			totalSteps += result.stepsTaken();
			if (progressListener != null) {
				progressListener.onRunCompleted(index + 1, normalizedRequest.runCount(), runs.getLast());
			}
			if (index + 1 < normalizedRequest.runCount()) {
				seed = Math.addExact(seed, 1L);
			}
		}

		double averageSteps = runs.isEmpty()
			? 0d
			: (double) totalSteps / (double) runs.size();
		return new SimulationSummary(
			compiledBundle.scenarioId(),
			compiledBundle.versionId(),
			compiledBundle.versionNumber(),
			compiledBundle.bundleHash(),
			"random-v1",
			normalizedRequest.runCount(),
			normalizedRequest.seedStart(),
			normalizedRequest.maxSteps(),
			averageSteps,
			outcomeCounts,
			runs,
			Instant.now()
		);
	}

	public SimulationRequest normalizeRequest(SimulationRequest request) {
		SimulationRequest value = request == null
			? new SimulationRequest(null, null, null, null)
			: request;
		int runCount = value.runCount() == null ? DEFAULT_RUN_COUNT : value.runCount();
		long seedStart = value.seedStart() == null ? DEFAULT_SEED_START : value.seedStart();
		int maxSteps = value.maxSteps() == null ? DEFAULT_MAX_STEPS : value.maxSteps();
		boolean trace = value.trace() == null ? true : value.trace();

		if (runCount < 1 || runCount > MAX_RUN_COUNT) {
			throw new IllegalArgumentException(
				"runCount must be between 1 and " + MAX_RUN_COUNT
			);
		}
		if (seedStart < 0) {
			throw new IllegalArgumentException("seedStart must be zero or greater");
		}
		if (maxSteps < 1 || maxSteps > 100_000) {
			throw new IllegalArgumentException("maxSteps must be between 1 and 100000");
		}
		if (runCount > 1 && seedStart > Long.MAX_VALUE - (runCount - 1L)) {
			throw new IllegalArgumentException("seedStart range overflows requested runCount");
		}

		return new SimulationRequest(runCount, seedStart, maxSteps, trace);
	}

	private RuntimeBundle toRuntimeBundle(Map<String, Object> payload) {
		try {
			String entryNodeId = objectMapper.convertValue(payload.get("entry_node_id"), String.class);
			String bundleHash = objectMapper.convertValue(payload.get("bundle_hash"), String.class);
			Map<String, Object> initialState = objectMapper.convertValue(
				payload.getOrDefault("initial_state", Map.of()),
				Map.class
			);
			List<Map<String, Object>> rawNodes = objectMapper.convertValue(
				payload.getOrDefault("nodes", List.of()),
				List.class
			);
			List<RuntimeNode> nodes = new ArrayList<>();
			for (Map<String, Object> rawNode : rawNodes) {
				nodes.add(
					new RuntimeNode(
						objectMapper.convertValue(rawNode.get("id"), String.class),
						NodeType.fromSerialized(
							objectMapper.convertValue(rawNode.get("type"), String.class)
						),
						toEffects(rawNode.get("effects")),
						toDecisionOptions(rawNode.get("decision_options")),
						toChanceOptions(rawNode.get("chance_options")),
						objectMapper.convertValue(rawNode.get("outcome"), String.class)
					)
				);
			}
			return new RuntimeBundle(bundleHash, entryNodeId, nodes, initialState);
		} catch (IllegalArgumentException exception) {
			throw new IllegalArgumentException("invalid compiled bundle shape", exception);
		}
	}

	private List<Effect> toEffects(Object rawValue) {
		List<Map<String, Object>> rawEffects = objectMapper.convertValue(rawValue, List.class);
		if (rawEffects == null) {
			return List.of();
		}
		List<Effect> effects = new ArrayList<>();
		for (Map<String, Object> rawEffect : rawEffects) {
			effects.add(
				new Effect(
					EffectOp.fromSerialized(
						objectMapper.convertValue(rawEffect.get("op"), String.class)
					),
					objectMapper.convertValue(rawEffect.get("path"), String.class),
					rawEffect.get("value")
				)
			);
		}
		return effects;
	}

	private List<DecisionOption> toDecisionOptions(Object rawValue) {
		List<Map<String, Object>> rawOptions = objectMapper.convertValue(rawValue, List.class);
		if (rawOptions == null) {
			return List.of();
		}
		List<DecisionOption> options = new ArrayList<>();
		for (Map<String, Object> rawOption : rawOptions) {
			options.add(
				new DecisionOption(
					objectMapper.convertValue(rawOption.get("to"), String.class),
					toGuard(rawOption.get("guard"))
				)
			);
		}
		return options;
	}

	private List<ChanceOption> toChanceOptions(Object rawValue) {
		List<Map<String, Object>> rawOptions = objectMapper.convertValue(rawValue, List.class);
		if (rawOptions == null) {
			return List.of();
		}
		List<ChanceOption> options = new ArrayList<>();
		for (Map<String, Object> rawOption : rawOptions) {
			double weight = 0d;
			Object rawWeight = rawOption.get("weight");
			if (rawWeight instanceof Number number) {
				weight = number.doubleValue();
			}
			options.add(
				new ChanceOption(
					objectMapper.convertValue(rawOption.get("to"), String.class),
					weight,
					toGuard(rawOption.get("guard"))
				)
			);
		}
		return options;
	}

	private Guard toGuard(Object rawValue) {
		if (!(rawValue instanceof Map<?, ?> rawGuard)) {
			return null;
		}
		return new Guard(
			objectMapper.convertValue(rawGuard.get("var"), String.class),
			rawGuard.get("equals")
		);
	}

	private RunResult run(RuntimeBundle bundle, long seed, int maxSteps) {
		Map<String, RuntimeNode> nodeIndex = new LinkedHashMap<>();
		for (RuntimeNode node : bundle.nodes()) {
			nodeIndex.put(node.id(), node);
		}

		Map<String, Object> state = cloneState(bundle.initialState());
		Pcg32 random = new Pcg32(seed);
		String current = bundle.entryNodeId();
		int steps = 0;
		List<SimulationTraceEvent> trace = new ArrayList<>();

		while (steps < maxSteps) {
			RuntimeNode node = nodeIndex.get(current);
			if (node == null) {
				trace.add(
					new SimulationTraceEvent(
						steps,
						current,
						"missing",
						cloneState(state),
						cloneState(state),
						List.of(),
						List.of(),
						null,
						null,
						"error_missing_node"
					)
				);
				return new RunResult("error_missing_node", steps, state, trace);
			}

			Map<String, Object> stateBefore = cloneState(state);
			applyEffects(state, node.effects());
			Map<String, Object> stateAfterEffects = cloneState(state);
			NodeType nodeType = node.type();
			if (nodeType == null) {
				trace.add(
					traceEvent(
						steps,
						node,
						stateBefore,
						stateAfterEffects,
						List.of(),
						null,
						null,
						"error_invalid_node_type"
					)
				);
				return new RunResult("error_invalid_node_type", steps, state, trace);
			}

			switch (nodeType) {
				case DECISION -> {
					List<DecisionOption> options = filterDecisionOptions(
						state,
						node.decisionOptions()
					);
					if (options.isEmpty()) {
						trace.add(
							traceEvent(
								steps,
								node,
								stateBefore,
								stateAfterEffects,
								traceOptionsForDecision(options),
								null,
								null,
								"error_no_options"
							)
						);
						return new RunResult("error_no_options", steps, state, trace);
					}

					int choice = random.nextIntN(options.size());
					if (choice < 0 || choice >= options.size()) {
						trace.add(
							traceEvent(
								steps,
								node,
								stateBefore,
								stateAfterEffects,
								traceOptionsForDecision(options),
								null,
								null,
								"error_policy_choice"
							)
						);
						return new RunResult("error_policy_choice", steps, state, trace);
					}
					DecisionOption selected = options.get(choice);
					String nextNodeId = selected.to();
					trace.add(
						traceEvent(
							steps,
							node,
							stateBefore,
							stateAfterEffects,
							traceOptionsForDecision(options),
							new TraceSelection(
								choice,
								nextNodeId,
								null,
								traceGuard(selected.guard())
							),
							nextNodeId,
							null
						)
					);
					current = nextNodeId;
					steps += 1;
				}
				case CHANCE -> {
					List<ChanceOption> options = filterChanceOptions(
						state,
						node.chanceOptions()
					);
					if (options.isEmpty()) {
						trace.add(
							traceEvent(
								steps,
								node,
								stateBefore,
								stateAfterEffects,
								traceOptionsForChance(options),
								null,
								null,
								"error_no_options"
							)
						);
						return new RunResult("error_no_options", steps, state, trace);
					}

					double total = 0d;
					for (ChanceOption option : options) {
						if (option.weight() > 0d) {
							total += option.weight();
						}
					}
					if (total <= 0d) {
						trace.add(
							traceEvent(
								steps,
								node,
								stateBefore,
								stateAfterEffects,
								traceOptionsForChance(options),
								null,
								null,
								"error_probability_weights"
							)
						);
						return new RunResult("error_probability_weights", steps, state, trace);
					}

					double target = random.nextFloat64() * total;
					double accumulated = 0d;
					ChanceOption selected = options.get(options.size() - 1);
					int selectedIndex = options.size() - 1;
					for (int optionIndex = 0; optionIndex < options.size(); optionIndex++) {
						ChanceOption option = options.get(optionIndex);
						if (option.weight() <= 0d) {
							continue;
						}
						accumulated += option.weight();
						if (target < accumulated) {
							selected = option;
							selectedIndex = optionIndex;
							break;
						}
					}
					String nextNodeId = selected.to();
					trace.add(
						traceEvent(
							steps,
							node,
							stateBefore,
							stateAfterEffects,
							traceOptionsForChance(options),
							new TraceSelection(
								selectedIndex,
								nextNodeId,
								selected.weight(),
								traceGuard(selected.guard())
							),
							nextNodeId,
							null
						)
					);
					current = nextNodeId;
					steps += 1;
				}
				case TERMINAL -> {
					String outcome = node.outcome();
					if (outcome == null || outcome.isBlank()) {
						outcome = "terminal";
					}
					trace.add(
						traceEvent(
							steps,
							node,
							stateBefore,
							stateAfterEffects,
							List.of(),
							null,
							null,
							outcome
						)
					);
					return new RunResult(outcome, steps, state, trace);
				}
				default -> {
					trace.add(
						traceEvent(
							steps,
							node,
							stateBefore,
							stateAfterEffects,
							List.of(),
							null,
							null,
							"error_invalid_node_type"
						)
					);
					return new RunResult("error_invalid_node_type", steps, state, trace);
				}
			}
		}

		trace.add(
			new SimulationTraceEvent(
				maxSteps,
				current,
				"overflow",
				cloneState(state),
				cloneState(state),
				List.of(),
				List.of(),
				null,
				null,
				"error_overflow"
			)
		);
		return new RunResult("error_overflow", maxSteps, state, trace);
	}

	private SimulationTraceEvent traceEvent(
		int step,
		RuntimeNode node,
		Map<String, Object> stateBefore,
		Map<String, Object> stateAfter,
		List<TraceOption> availableOptions,
		TraceSelection selectedOption,
		String nextNodeId,
		String outcome
	) {
		return new SimulationTraceEvent(
			step,
			node.id(),
			node.type().serializedValue(),
			stateBefore,
			stateAfter,
			traceEffects(node.effects()),
			availableOptions,
			selectedOption,
			nextNodeId,
			outcome
		);
	}

	private List<TraceEffect> traceEffects(List<Effect> effects) {
		List<TraceEffect> output = new ArrayList<>();
		for (Effect effect : effects) {
			output.add(
				new TraceEffect(
					effect.op().serializedValue(),
					effect.path(),
					deepCopyObject(effect.value())
				)
			);
		}
		return output;
	}

	private List<TraceOption> traceOptionsForDecision(List<DecisionOption> options) {
		List<TraceOption> output = new ArrayList<>();
		for (int index = 0; index < options.size(); index++) {
			DecisionOption option = options.get(index);
			output.add(new TraceOption(index, option.to(), null, traceGuard(option.guard())));
		}
		return output;
	}

	private List<TraceOption> traceOptionsForChance(List<ChanceOption> options) {
		List<TraceOption> output = new ArrayList<>();
		for (int index = 0; index < options.size(); index++) {
			ChanceOption option = options.get(index);
			output.add(
				new TraceOption(index, option.to(), option.weight(), traceGuard(option.guard()))
			);
		}
		return output;
	}

	private TraceGuard traceGuard(Guard guard) {
		if (guard == null) {
			return null;
		}
		return new TraceGuard(guard.var(), deepCopyObject(guard.equalsValue()));
	}

	private Object deepCopyObject(Object source) {
		if (source == null) {
			return null;
		}
		return objectMapper.convertValue(source, Object.class);
	}

	private Map<String, Object> cloneState(Map<String, Object> state) {
		if (state == null || state.isEmpty()) {
			return new LinkedHashMap<>();
		}
		return new LinkedHashMap<>(state);
	}

	private void applyEffects(Map<String, Object> state, List<Effect> effects) {
		for (Effect effect : effects) {
			if (effect == null || effect.op() == null || effect.path() == null) {
				continue;
			}
			switch (effect.op()) {
				case SET -> state.put(effect.path(), effect.value());
				case INCREMENT -> state.put(
					effect.path(),
					asDouble(state.get(effect.path())) + asDouble(effect.value())
				);
				case DECREMENT -> state.put(
					effect.path(),
					asDouble(state.get(effect.path())) - asDouble(effect.value())
				);
				case ADD_TO_SET -> state.put(
					effect.path(),
					addToSet(state.get(effect.path()), effect.value())
				);
				case REMOVE_FROM_SET -> state.put(
					effect.path(),
					removeFromSet(state.get(effect.path()), effect.value())
				);
				default -> {
				}
			}
		}
	}

	private double asDouble(Object value) {
		if (value instanceof Number number) {
			return number.doubleValue();
		}
		return 0d;
	}

	private List<String> addToSet(Object current, Object value) {
		List<String> set = toStringSet(current);
		String next = value instanceof String stringValue ? stringValue : "";
		if (next.isBlank() || set.contains(next)) {
			return set;
		}
		List<String> updated = new ArrayList<>(set);
		updated.add(next);
		return updated;
	}

	private List<String> removeFromSet(Object current, Object value) {
		List<String> set = toStringSet(current);
		String target = value instanceof String stringValue ? stringValue : "";
		if (target.isBlank()) {
			return set;
		}
		List<String> updated = new ArrayList<>();
		for (String item : set) {
			if (!item.equals(target)) {
				updated.add(item);
			}
		}
		return updated;
	}

	private List<String> toStringSet(Object value) {
		if (value instanceof List<?> items) {
			List<String> output = new ArrayList<>();
			for (Object item : items) {
				if (item instanceof String stringValue && !stringValue.isBlank()) {
					output.add(stringValue);
				}
			}
			return output;
		}
		return List.of();
	}

	private List<DecisionOption> filterDecisionOptions(
		Map<String, Object> state,
		List<DecisionOption> options
	) {
		List<DecisionOption> filtered = new ArrayList<>();
		for (DecisionOption option : options) {
			if (option != null && guardPasses(state, option.guard())) {
				filtered.add(option);
			}
		}
		return filtered;
	}

	private List<ChanceOption> filterChanceOptions(
		Map<String, Object> state,
		List<ChanceOption> options
	) {
		List<ChanceOption> filtered = new ArrayList<>();
		for (ChanceOption option : options) {
			if (option != null && guardPasses(state, option.guard())) {
				filtered.add(option);
			}
		}
		return filtered;
	}

	private boolean guardPasses(Map<String, Object> state, Guard guard) {
		if (guard == null) {
			return true;
		}
		if (guard.var() == null || guard.var().isBlank() || !state.containsKey(guard.var())) {
			return false;
		}
		return valuesEqual(state.get(guard.var()), guard.equalsValue());
	}

	private boolean valuesEqual(Object left, Object right) {
		if (left instanceof Number leftNumber && right instanceof Number rightNumber) {
			return Double.compare(leftNumber.doubleValue(), rightNumber.doubleValue()) == 0;
		}
		return Objects.deepEquals(left, right);
	}

	public record SimulationRequest(
		Integer runCount,
		Long seedStart,
		Integer maxSteps,
		Boolean trace
	) {}

	public record SimulationSummary(
		UUID scenarioId,
		UUID versionId,
		int versionNumber,
		String bundleHash,
		String agentVersion,
		int runCount,
		long seedStart,
		int maxSteps,
		double averageSteps,
		Map<String, Integer> outcomeCounts,
		List<SimulationRun> runs,
		Instant completedAt
	) {}

	public record SimulationRun(
		long seed,
		String outcome,
		int stepsTaken,
		Map<String, Object> finalState,
		List<SimulationTraceEvent> trace
	) {}

	public record SimulationTraceEvent(
		int step,
		String nodeId,
		String nodeType,
		Map<String, Object> stateBefore,
		Map<String, Object> stateAfter,
		List<TraceEffect> effectsApplied,
		List<TraceOption> availableOptions,
		TraceSelection selectedOption,
		String nextNodeId,
		String outcome
	) {}

	public record TraceEffect(String op, String path, Object value) {}

	public record TraceOption(int index, String to, Double weight, TraceGuard guard) {}

	public record TraceSelection(int index, String to, Double weight, TraceGuard guard) {}

	public record TraceGuard(String var, Object equalsValue) {}

	@FunctionalInterface
	public interface SimulationProgressListener {
		void onRunCompleted(int completedRuns, int totalRuns, SimulationRun run);
	}
}
