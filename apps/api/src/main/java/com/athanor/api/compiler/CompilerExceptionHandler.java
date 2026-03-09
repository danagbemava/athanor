package com.athanor.api.compiler;

import com.athanor.api.scenario.ScenarioService;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice(assignableTypes = { CompilerController.class, BundleController.class })
public class CompilerExceptionHandler {

	@ExceptionHandler(CompilerValidationException.class)
	@ResponseStatus(HttpStatus.BAD_REQUEST)
	public CompilationErrorResponse handleCompilerValidation(CompilerValidationException exception) {
		return new CompilationErrorResponse(exception.getMessage(), exception.validation());
	}

	@ExceptionHandler(IllegalStateException.class)
	@ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
	public CompilationErrorResponse handleIllegalState(IllegalStateException exception) {
		return new CompilationErrorResponse(exception.getMessage(), null);
	}

	@ExceptionHandler(IllegalArgumentException.class)
	@ResponseStatus(HttpStatus.BAD_REQUEST)
	public CompilationErrorResponse handleIllegalArgument(IllegalArgumentException exception) {
		return new CompilationErrorResponse(exception.getMessage(), null);
	}

	@ExceptionHandler(BundleNotFoundException.class)
	@ResponseStatus(HttpStatus.NOT_FOUND)
	public CompilationErrorResponse handleBundleNotFound(BundleNotFoundException exception) {
		return new CompilationErrorResponse(exception.getMessage(), null);
	}

	@ExceptionHandler(RuntimeException.class)
	@ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
	public CompilationErrorResponse handleRuntime(RuntimeException exception) {
		return new CompilationErrorResponse(exception.getMessage(), null);
	}

	public record CompilationErrorResponse(
		String error,
		ScenarioService.ScenarioValidationSnapshot validation
	) {}
}
