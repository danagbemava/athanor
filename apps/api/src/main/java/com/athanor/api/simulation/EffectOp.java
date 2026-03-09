package com.athanor.api.simulation;

enum EffectOp {
	SET("set"),
	INCREMENT("increment"),
	DECREMENT("decrement"),
	ADD_TO_SET("add_to_set"),
	REMOVE_FROM_SET("remove_from_set");

	private final String serializedValue;

	EffectOp(String serializedValue) {
		this.serializedValue = serializedValue;
	}

	String serializedValue() {
		return serializedValue;
	}

	static EffectOp fromSerialized(String value) {
		if (value == null || value.isBlank()) {
			throw new IllegalArgumentException("invalid compiled bundle shape");
		}
		for (EffectOp candidate : values()) {
			if (candidate.serializedValue.equals(value)) {
				return candidate;
			}
		}
		throw new IllegalArgumentException("invalid compiled bundle shape");
	}
}
