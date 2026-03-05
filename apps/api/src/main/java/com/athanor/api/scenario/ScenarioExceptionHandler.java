package com.athanor.api.scenario;

import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class ScenarioExceptionHandler {

	@ExceptionHandler(ScenarioNotFoundException.class)
	@ResponseStatus(HttpStatus.NOT_FOUND)
	public Map<String, String> handleScenarioNotFound(ScenarioNotFoundException exception) {
		return Map.of("error", exception.getMessage());
	}
}
