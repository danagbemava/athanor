package com.athanor.api.jobs;

import com.athanor.api.compiler.WorkerExecutionResult;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

@Component
public class WorkerRuntimeEventHandler {

	private final JobService jobService;
	private final ObjectMapper objectMapper;

	public WorkerRuntimeEventHandler(JobService jobService, ObjectMapper objectMapper) {
		this.jobService = jobService;
		this.objectMapper = objectMapper;
	}

	public void handle(WorkerRuntimeEventMessage event) {
		try {
			switch (event.type()) {
			case "progress" -> {
				JsonNode payload = objectMapper.readTree(event.payloadJson());
				jobService.recordWorkerProgress(
					event.runId(),
					payload.path("completedRuns").asInt(),
					payload.path("totalRuns").asInt()
				);
			}
			case "complete" -> jobService.completeWorkerJob(
				event.runId(),
				objectMapper.readValue(event.payloadJson(), WorkerExecutionResult.class)
			);
			case "failed" -> {
				JsonNode payload = objectMapper.readTree(event.payloadJson());
				jobService.failWorkerJob(event.runId(), payload.path("error").asText("worker runtime failed"));
			}
			default -> {}
			}
		} catch (Exception exception) {
			throw new IllegalStateException("failed to process worker runtime event", exception);
		}
	}
}
