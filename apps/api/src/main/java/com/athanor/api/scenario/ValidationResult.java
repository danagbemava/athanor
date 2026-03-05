package com.athanor.api.scenario;

import java.util.List;

public record ValidationResult(
	boolean valid,
	List<ValidationMessage> errors,
	List<ValidationMessage> warnings
) {}
