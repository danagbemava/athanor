package com.athanor.api.compiler;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

public enum BundleRetentionClass {
	PUBLISHED("published", 3),
	DRAFT("draft", 2),
	ORPHAN("orphan", 1);

	private final String value;
	private final int priority;

	BundleRetentionClass(String value, int priority) {
		this.value = value;
		this.priority = priority;
	}

	@JsonValue
	public String value() {
		return value;
	}

	public static BundleRetentionClass fromScenarioState(String state) {
		if ("published".equalsIgnoreCase(state)) {
			return PUBLISHED;
		}
		return DRAFT;
	}

	@JsonCreator
	public static BundleRetentionClass fromValue(String value) {
		for (BundleRetentionClass retentionClass : values()) {
			if (retentionClass.value.equalsIgnoreCase(value)) {
				return retentionClass;
			}
		}
		throw new IllegalArgumentException("invalid retention class");
	}

	public static BundleRetentionClass strongest(
		BundleRetentionClass left,
		BundleRetentionClass right
	) {
		if (left == null) {
			return right;
		}
		if (right == null) {
			return left;
		}
		return left.priority >= right.priority ? left : right;
	}
}
