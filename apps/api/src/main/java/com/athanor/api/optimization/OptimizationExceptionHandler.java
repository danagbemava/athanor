package com.athanor.api.optimization;

import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice(basePackageClasses = OptimizationController.class)
public class OptimizationExceptionHandler {

	@ExceptionHandler(OptimizationJobNotFoundException.class)
	@ResponseStatus(HttpStatus.NOT_FOUND)
	public Map<String, String> handleNotFound(OptimizationJobNotFoundException exception) {
		return Map.of("error", exception.getMessage());
	}

	@ExceptionHandler(IllegalArgumentException.class)
	@ResponseStatus(HttpStatus.BAD_REQUEST)
	public Map<String, String> handleBadRequest(IllegalArgumentException exception) {
		return Map.of("error", exception.getMessage());
	}
}
