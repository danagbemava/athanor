package com.athanor.api.compiler;

import com.athanor.api.scenario.ScenarioService;

public class CompilerValidationException extends RuntimeException {

	private final ScenarioService.ScenarioValidationSnapshot validation;

	public CompilerValidationException(ScenarioService.ScenarioValidationSnapshot validation) {
		super("scenario graph validation failed");
		this.validation = validation;
	}

	public ScenarioService.ScenarioValidationSnapshot validation() {
		return validation;
	}
}
