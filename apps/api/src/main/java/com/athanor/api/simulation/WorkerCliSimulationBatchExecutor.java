package com.athanor.api.simulation;

import com.athanor.api.compiler.CompilerService;
import com.athanor.api.compiler.WorkerExecutionMode;
import com.athanor.api.compiler.WorkerExecutionRequest;
import com.athanor.api.compiler.WorkerExecutionRequestFactory;
import com.athanor.api.compiler.WorkerExecutionResult;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

@Component
public class WorkerCliSimulationBatchExecutor implements SimulationBatchExecutor {

	private final CompilerService compilerService;
	private final WorkerExecutionRequestFactory requestFactory;
	private final WorkerCliProperties properties;
	private final ObjectMapper objectMapper;
	private final WorkerExecutionSummaryMapper summaryMapper;

	public WorkerCliSimulationBatchExecutor(
		CompilerService compilerService,
		WorkerExecutionRequestFactory requestFactory,
		WorkerCliProperties properties,
		ObjectMapper objectMapper,
		WorkerExecutionSummaryMapper summaryMapper
	) {
		this.compilerService = compilerService;
		this.requestFactory = requestFactory;
		this.properties = properties;
		this.objectMapper = objectMapper;
		this.summaryMapper = summaryMapper;
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
			return summaryMapper.toSimulationSummary(
				compiledBundle.scenarioId(),
				compiledBundle.versionId(),
				compiledBundle.versionNumber(),
				compiledBundle.bundleHash(),
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
