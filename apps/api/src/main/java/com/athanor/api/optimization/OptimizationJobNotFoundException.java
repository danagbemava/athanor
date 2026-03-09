package com.athanor.api.optimization;

final class OptimizationJobNotFoundException extends RuntimeException {

	OptimizationJobNotFoundException(String message) {
		super(message);
	}
}
