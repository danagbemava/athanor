package com.athanor.api.compiler;

public enum WorkerExecutionMode {
	OPTIMIZATION("optimization"),
	ANALYTICS("analytics");

	private final String serializedValue;

	WorkerExecutionMode(String serializedValue) {
		this.serializedValue = serializedValue;
	}

	public String serializedValue() {
		return serializedValue;
	}
}
