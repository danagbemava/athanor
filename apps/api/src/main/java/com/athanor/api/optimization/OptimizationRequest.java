package com.athanor.api.optimization;

import java.util.Map;
import java.util.UUID;

public record OptimizationRequest(
	UUID scenarioId,
	Map<String, Double> targetDistribution,
	Integer maxIterations,
	Integer runsPerIteration,
	String strategy
) {}
