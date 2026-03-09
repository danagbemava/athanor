package com.athanor.api.simulation;

import java.util.List;
import java.util.Map;

record RuntimeBundle(
	String bundleHash,
	String entryNodeId,
	List<RuntimeNode> nodes,
	Map<String, Object> initialState
) {
	RuntimeBundle {
		nodes = nodes == null ? List.of() : List.copyOf(nodes);
		initialState = initialState == null ? Map.of() : initialState;
	}
}
