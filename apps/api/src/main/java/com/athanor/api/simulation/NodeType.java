package com.athanor.api.simulation;

enum NodeType {
	DECISION("decision"),
	CHANCE("chance"),
	TERMINAL("terminal");

	private final String serializedValue;

	NodeType(String serializedValue) {
		this.serializedValue = serializedValue;
	}

	String serializedValue() {
		return serializedValue;
	}

	static NodeType fromSerialized(String value) {
		if (value == null || value.isBlank()) {
			throw new IllegalArgumentException("invalid compiled bundle shape");
		}
		for (NodeType candidate : values()) {
			if (candidate.serializedValue.equals(value)) {
				return candidate;
			}
		}
		throw new IllegalArgumentException("invalid compiled bundle shape");
	}
}
