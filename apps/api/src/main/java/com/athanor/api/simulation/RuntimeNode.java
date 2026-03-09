package com.athanor.api.simulation;

import java.util.List;

record RuntimeNode(
	String id,
	NodeType type,
	List<Effect> effects,
	List<DecisionOption> decisionOptions,
	List<ChanceOption> chanceOptions,
	String outcome
) {
	RuntimeNode {
		effects = effects == null ? List.of() : List.copyOf(effects);
		decisionOptions = decisionOptions == null ? List.of() : List.copyOf(decisionOptions);
		chanceOptions = chanceOptions == null ? List.of() : List.copyOf(chanceOptions);
	}
}
