package com.athanor.api.simulation;

import com.athanor.api.compiler.CompilerValidationException;
import com.athanor.api.scenario.ScenarioService;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice(assignableTypes = SimulationController.class)
public class SimulationExceptionHandler {

	@ExceptionHandler(CompilerValidationException.class)
	@ResponseStatus(HttpStatus.BAD_REQUEST)
	public SimulationErrorResponse handleCompilerValidation(CompilerValidationException exception) {
		return new SimulationErrorResponse(exception.getMessage(), exception.validation());
	}

	@ExceptionHandler(IllegalArgumentException.class)
	@ResponseStatus(HttpStatus.BAD_REQUEST)
	public SimulationErrorResponse handleIllegalArgument(IllegalArgumentException exception) {
		return new SimulationErrorResponse(exception.getMessage(), null);
	}

	@ExceptionHandler(IllegalStateException.class)
	@ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
	public SimulationErrorResponse handleIllegalState(IllegalStateException exception) {
		return new SimulationErrorResponse(exception.getMessage(), null);
	}

	@ExceptionHandler(RuntimeException.class)
	@ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
	public SimulationErrorResponse handleRuntime(RuntimeException exception) {
		return new SimulationErrorResponse(exception.getMessage(), null);
	}

	public record SimulationErrorResponse(
		String error,
		ScenarioService.ScenarioValidationSnapshot validation
	) {}
}
