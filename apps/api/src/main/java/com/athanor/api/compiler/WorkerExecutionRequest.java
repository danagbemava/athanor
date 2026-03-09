package com.athanor.api.compiler;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

public record WorkerExecutionRequest(
	@JsonProperty("bundle_hash") String bundleHash,
	@JsonProperty("seed_start") long seedStart,
	@JsonProperty("run_count") int runCount,
	@JsonProperty("agent_policy") String agentPolicy,
	@JsonProperty("scripted_choices") List<Integer> scriptedChoices,
	@JsonProperty("execution_mode") String executionMode,
	@JsonProperty("max_steps") int maxSteps
) {}
