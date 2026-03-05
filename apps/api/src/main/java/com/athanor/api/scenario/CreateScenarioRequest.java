package com.athanor.api.scenario;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.Map;

public record CreateScenarioRequest(
	@NotBlank String name,
	String description,
	@NotNull Map<String, Object> graph
) {}
