package com.athanor.api.jobs;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice(assignableTypes = SimulationJobController.class)
public class JobExceptionHandler {

	@ExceptionHandler(SimulationJobNotFoundException.class)
	@ResponseStatus(HttpStatus.NOT_FOUND)
	public JobErrorResponse handleMissingJob(SimulationJobNotFoundException exception) {
		return new JobErrorResponse(exception.getMessage());
	}

	@ExceptionHandler(IllegalArgumentException.class)
	@ResponseStatus(HttpStatus.BAD_REQUEST)
	public JobErrorResponse handleIllegalArgument(IllegalArgumentException exception) {
		return new JobErrorResponse(exception.getMessage());
	}

	@ExceptionHandler(RuntimeException.class)
	@ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
	public JobErrorResponse handleRuntime(RuntimeException exception) {
		return new JobErrorResponse(exception.getMessage());
	}

	public record JobErrorResponse(String error) {}
}
