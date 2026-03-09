package com.athanor.api.optimization;

import java.util.Map;

record OptimizationCandidate(
	OptimizationParameters parameters,
	Map<String, Object> graph,
	Map<String, Double> outcomeDistribution,
	double score
) {}
