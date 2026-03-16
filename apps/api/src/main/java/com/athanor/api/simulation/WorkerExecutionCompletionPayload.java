package com.athanor.api.simulation;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.Instant;
import java.util.Map;

public record WorkerExecutionCompletionPayload(
	@JsonProperty("bundle_hash") String bundleHash,
	@JsonProperty("agent_policy") String agentPolicy,
	@JsonProperty("agent_version") String agentVersion,
	@JsonProperty("execution_mode") String executionMode,
	@JsonProperty("seed_start") long seedStart,
	@JsonProperty("run_count") int runCount,
	@JsonProperty("max_steps") int maxSteps,
	@JsonProperty("average_steps") double averageSteps,
	@JsonProperty("outcome_counts") Map<String, Integer> outcomeCounts,
	@JsonProperty("result_key") String resultKey,
	@JsonProperty("completed_at") Instant completedAt
) {}
