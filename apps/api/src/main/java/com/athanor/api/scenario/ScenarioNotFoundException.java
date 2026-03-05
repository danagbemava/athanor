package com.athanor.api.scenario;

import java.util.UUID;

public class ScenarioNotFoundException extends RuntimeException {

	public ScenarioNotFoundException(UUID scenarioId) {
		super("Scenario not found: " + scenarioId);
	}
}
