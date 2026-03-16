package com.athanor.api.simulation;

import com.athanor.api.compiler.WorkerExecutionResult;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Component;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

@Component
public class WorkerExecutionSummaryMapper {

	private final ObjectMapper objectMapper;

	public WorkerExecutionSummaryMapper(ObjectMapper objectMapper) {
		this.objectMapper = objectMapper;
	}

	public SimulationService.SimulationSummary toSimulationSummary(
		UUID scenarioId,
		UUID versionId,
		Integer versionNumber,
		String bundleHash,
		WorkerExecutionResult executionResult,
		SimulationService.SimulationProgressListener progressListener
	) {
		List<SimulationService.SimulationRun> runs = new ArrayList<>();
		Map<String, Integer> outcomeCounts = new LinkedHashMap<>();
		int totalSteps = 0;

		for (int index = 0; index < executionResult.runs().size(); index += 1) {
			WorkerExecutionResult.WorkerExecutionRunResult run = executionResult.runs().get(index);
			List<SimulationService.SimulationTraceEvent> trace = traceEvents(run.trace());
			Map<String, Object> finalState = trace.isEmpty()
				? Map.of()
				: new LinkedHashMap<>(trace.get(trace.size() - 1).stateAfter());
			SimulationService.SimulationRun simulationRun = new SimulationService.SimulationRun(
				run.seed(),
				run.outcome(),
				run.stepsTaken(),
				finalState,
				trace
			);
			runs.add(simulationRun);
			outcomeCounts.merge(run.outcome(), 1, Integer::sum);
			totalSteps += run.stepsTaken();
			if (progressListener != null) {
				progressListener.onRunCompleted(index + 1, executionResult.runCount(), simulationRun);
			}
		}

		double averageSteps = runs.isEmpty() ? 0d : (double) totalSteps / (double) runs.size();
		return new SimulationService.SimulationSummary(
			scenarioId,
			versionId,
			versionNumber,
			bundleHash,
			executionResult.agentVersion(),
			executionResult.runCount(),
			executionResult.seedStart(),
			executionResult.maxSteps(),
			averageSteps,
			outcomeCounts,
			runs,
			Instant.now()
		);
	}

	public SimulationService.SimulationSummary toSimulationSummary(
		UUID scenarioId,
		UUID versionId,
		Integer versionNumber,
		String bundleHash,
		WorkerExecutionCompletionPayload payload
	) {
		return new SimulationService.SimulationSummary(
			scenarioId,
			versionId,
			versionNumber == null ? 0 : versionNumber,
			bundleHash,
			payload.agentVersion(),
			payload.runCount(),
			payload.seedStart(),
			payload.maxSteps(),
			payload.averageSteps(),
			payload.outcomeCounts() == null ? Map.of() : new LinkedHashMap<>(payload.outcomeCounts()),
			List.of(),
			payload.completedAt() == null ? Instant.now() : payload.completedAt()
		);
	}

	public SimulationRunPage toSimulationRunPage(
		WorkerExecutionResult executionResult,
		int page,
		int pageSize
	) {
		int offset = Math.max(page, 0) * Math.max(pageSize, 1);
		if (offset >= executionResult.runs().size()) {
			return new SimulationRunPage(page, pageSize, executionResult.runs().size(), List.of());
		}

		int end = Math.min(executionResult.runs().size(), offset + pageSize);
		List<SimulationService.SimulationRun> runs = new ArrayList<>();
		for (int index = offset; index < end; index += 1) {
			WorkerExecutionResult.WorkerExecutionRunResult run = executionResult.runs().get(index);
			List<SimulationService.SimulationTraceEvent> trace = traceEvents(run.trace());
			Map<String, Object> finalState = trace.isEmpty()
				? Map.of()
				: new LinkedHashMap<>(trace.get(trace.size() - 1).stateAfter());
			runs.add(
				new SimulationService.SimulationRun(
					run.seed(),
					run.outcome(),
					run.stepsTaken(),
					finalState,
					trace
				)
			);
		}
		return new SimulationRunPage(page, pageSize, executionResult.runs().size(), runs);
	}

	private List<SimulationService.SimulationTraceEvent> traceEvents(
		List<Map<String, Object>> rawTrace
	) {
		if (rawTrace == null || rawTrace.isEmpty()) {
			return List.of();
		}

		List<SimulationService.SimulationTraceEvent> trace = new ArrayList<>();
		for (Map<String, Object> rawEvent : rawTrace) {
			trace.add(
				new SimulationService.SimulationTraceEvent(
					readInt(rawEvent.get("step")),
					readString(rawEvent.get("node_id")),
					readString(rawEvent.get("node_type")),
					readMap(rawEvent.get("state_before")),
					readMap(rawEvent.get("state_after")),
					traceEffects(rawEvent.get("effects_applied")),
					traceOptions(rawEvent.get("available_options")),
					traceSelection(rawEvent.get("selected_option")),
					readNullableString(rawEvent.get("next_node_id")),
					readNullableString(rawEvent.get("outcome"))
				)
			);
		}
		return trace;
	}

	private List<SimulationService.TraceEffect> traceEffects(Object rawValue) {
		List<Map<String, Object>> effects = readListOfMaps(rawValue);
		List<SimulationService.TraceEffect> result = new ArrayList<>();
		for (Map<String, Object> effect : effects) {
			result.add(
				new SimulationService.TraceEffect(
					readString(effect.get("op")),
					readString(effect.get("path")),
					effect.get("value")
				)
			);
		}
		return result;
	}

	private List<SimulationService.TraceOption> traceOptions(Object rawValue) {
		List<Map<String, Object>> options = readListOfMaps(rawValue);
		List<SimulationService.TraceOption> result = new ArrayList<>();
		for (Map<String, Object> option : options) {
			result.add(
				new SimulationService.TraceOption(
					readInt(option.get("index")),
					readString(option.get("to")),
					readNullableDouble(option.get("weight")),
					traceGuard(option.get("guard"))
				)
			);
		}
		return result;
	}

	private SimulationService.TraceSelection traceSelection(Object rawValue) {
		if (!(rawValue instanceof Map<?, ?> rawMap)) {
			return null;
		}
		Map<String, Object> selection = objectMapper.convertValue(
			rawMap,
			new TypeReference<>() {}
		);
		return new SimulationService.TraceSelection(
			readInt(selection.get("index")),
			readString(selection.get("to")),
			readNullableDouble(selection.get("weight")),
			traceGuard(selection.get("guard"))
		);
	}

	private SimulationService.TraceGuard traceGuard(Object rawValue) {
		if (!(rawValue instanceof Map<?, ?> rawMap)) {
			return null;
		}
		Map<String, Object> guard = objectMapper.convertValue(rawMap, new TypeReference<>() {});
		return new SimulationService.TraceGuard(readString(guard.get("var")), guard.get("equals"));
	}

	private List<Map<String, Object>> readListOfMaps(Object rawValue) {
		List<Map<String, Object>> maps = objectMapper.convertValue(
			rawValue,
			new TypeReference<>() {}
		);
		return maps == null ? List.of() : maps;
	}

	private Map<String, Object> readMap(Object rawValue) {
		Map<String, Object> map = objectMapper.convertValue(rawValue, new TypeReference<>() {});
		return map == null ? Map.of() : map;
	}

	private String readString(Object rawValue) {
		String value = objectMapper.convertValue(rawValue, String.class);
		return value == null ? "" : value;
	}

	private String readNullableString(Object rawValue) {
		return objectMapper.convertValue(rawValue, String.class);
	}

	private Integer readInt(Object rawValue) {
		Integer value = objectMapper.convertValue(rawValue, Integer.class);
		return value == null ? 0 : value;
	}

	private Double readNullableDouble(Object rawValue) {
		return objectMapper.convertValue(rawValue, Double.class);
	}
}
