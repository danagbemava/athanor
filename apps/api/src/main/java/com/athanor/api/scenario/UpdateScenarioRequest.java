package com.athanor.api.scenario;

import jakarta.validation.constraints.NotNull;
import java.util.Map;

public record UpdateScenarioRequest(
	String name,
	String description,
	@NotNull Map<String, Object> graph
) {}
