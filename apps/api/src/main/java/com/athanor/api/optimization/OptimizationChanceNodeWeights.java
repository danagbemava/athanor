package com.athanor.api.optimization;

import java.util.List;

public record OptimizationChanceNodeWeights(
	String nodeId,
	List<OptimizationChanceOptionWeight> options
) {}
