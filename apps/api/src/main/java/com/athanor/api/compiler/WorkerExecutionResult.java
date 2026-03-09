package com.athanor.api.compiler;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;
import java.util.Map;

public record WorkerExecutionResult(
	@JsonProperty("bundle_hash") String bundleHash,
	@JsonProperty("agent_policy") String agentPolicy,
	@JsonProperty("agent_version") String agentVersion,
	@JsonProperty("execution_mode") String executionMode,
	@JsonProperty("seed_start") long seedStart,
	@JsonProperty("run_count") int runCount,
	@JsonProperty("max_steps") int maxSteps,
	List<WorkerExecutionRunResult> runs
) {
	public record WorkerExecutionRunResult(
		@JsonProperty("bundle_hash") String bundleHash,
		long seed,
		@JsonProperty("agent_version") String agentVersion,
		String outcome,
		@JsonProperty("steps_taken") int stepsTaken,
		Map<String, Object> metrics,
		List<Map<String, Object>> trace
	) {}
}
