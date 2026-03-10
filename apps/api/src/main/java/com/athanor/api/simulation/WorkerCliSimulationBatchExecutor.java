package com.athanor.api.simulation;

import com.athanor.api.compiler.CompilerService;
import com.athanor.api.compiler.WorkerExecutionMode;
import com.athanor.api.compiler.WorkerExecutionRequest;
import com.athanor.api.compiler.WorkerExecutionRequestFactory;
import com.athanor.api.compiler.WorkerExecutionResult;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

@Component
public class WorkerCliSimulationBatchExecutor implements SimulationBatchExecutor {

	private final CompilerService compilerService;
	private final WorkerExecutionRequestFactory requestFactory;
	private final WorkerCliProperties properties;
	private final ObjectMapper objectMapper;

	public WorkerCliSimulationBatchExecutor(
		CompilerService compilerService,
		WorkerExecutionRequestFactory requestFactory,
		WorkerCliProperties properties,
		ObjectMapper objectMapper
	) {
		this.compilerService = compilerService;
		this.requestFactory = requestFactory;
		this.properties = properties;
		this.objectMapper = objectMapper;
	}

	@Override
	public SimulationService.SimulationSummary executeCompiledBundle(
		CompilerService.CompiledBundle compiledBundle,
		SimulationService.SimulationRequest request,
		SimulationService.SimulationProgressListener progressListener
	) {
		SimulationService.SimulationRequest normalizedRequest = normalize(request);
		WorkerExecutionMode mode = Boolean.TRUE.equals(normalizedRequest.trace())
			? WorkerExecutionMode.ANALYTICS
			: WorkerExecutionMode.OPTIMIZATION;
		WorkerExecutionRequest workerRequest = requestFactory.forSimulation(
			compiledBundle,
			normalizedRequest.runCount(),
			normalizedRequest.seedStart(),
			normalizedRequest.maxSteps(),
			mode
		);

		Path bundlePath = null;
		Path requestPath = null;
		try {
			bundlePath = Files.createTempFile("athanor-bundle-", ".json");
			requestPath = Files.createTempFile("athanor-worker-request-", ".json");
			Files.write(bundlePath, compilerService.bundleContent(compiledBundle.bundleHash()));
			objectMapper.writeValue(requestPath.toFile(), workerRequest);

			WorkerExecutionResult executionResult = runWorker(bundlePath, requestPath);
			return toSimulationSummary(
				compiledBundle,
				normalizedRequest,
				executionResult,
				progressListener
			);
		} catch (IOException exception) {
			throw new IllegalStateException("failed to execute worker batch", exception);
		} catch (InterruptedException exception) {
			Thread.currentThread().interrupt();
			throw new IllegalStateException("worker batch execution interrupted", exception);
		} finally {
			deleteQuietly(bundlePath);
			deleteQuietly(requestPath);
		}
	}

	private WorkerExecutionResult runWorker(Path bundlePath, Path requestPath)
		throws IOException, InterruptedException {
		List<String> command = new ArrayList<>();
		command.addAll(Arrays.asList(properties.getCommand().trim().split("\\s+")));
		command.addAll(
			List.of(
				"run",
				"--bundle",
				bundlePath.toAbsolutePath().toString(),
				"--request",
				requestPath.toAbsolutePath().toString()
			)
		);

		Process process = new ProcessBuilder(command)
			.directory(Path.of(properties.getWorkingDirectory()).toFile())
			.redirectErrorStream(true)
			.start();
		String output = new String(process.getInputStream().readAllBytes());
		int exitCode = process.waitFor();
		if (exitCode != 0) {
			throw new IllegalStateException(
				"worker batch execution failed: " + output.trim()
			);
		}
		return objectMapper.readValue(output, WorkerExecutionResult.class);
	}

	private SimulationService.SimulationSummary toSimulationSummary(
		CompilerService.CompiledBundle compiledBundle,
		SimulationService.SimulationRequest request,
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
			compiledBundle.scenarioId(),
			compiledBundle.versionId(),
			compiledBundle.versionNumber(),
			compiledBundle.bundleHash(),
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

	private SimulationService.SimulationRequest normalize(
		SimulationService.SimulationRequest request
	) {
		return request == null
			? new SimulationService.SimulationRequest(25, 1L, 10_000, true)
			: new SimulationService.SimulationRequest(
				request.runCount() == null ? 25 : request.runCount(),
				request.seedStart() == null ? 1L : request.seedStart(),
				request.maxSteps() == null ? 10_000 : request.maxSteps(),
				request.trace() == null ? true : request.trace()
			);
	}

	private void deleteQuietly(Path path) {
		if (path == null) {
			return;
		}
		try {
			Files.deleteIfExists(path);
		} catch (IOException ignored) {}
	}
}
