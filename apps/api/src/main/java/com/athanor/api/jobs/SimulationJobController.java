package com.athanor.api.jobs;

import com.athanor.api.simulation.SimulationService;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class SimulationJobController {

	private final JobService jobService;

	public SimulationJobController(JobService jobService) {
		this.jobService = jobService;
	}

	@PostMapping("/simulate")
	@ResponseStatus(HttpStatus.ACCEPTED)
	public SubmittedSimulationJob submitSimulation(
		@RequestBody AsyncSimulationRequest request
	) {
		return jobService.submitSimulationJob(
			request.scenarioId(),
			new SimulationService.SimulationRequest(
				request.runCount(),
				request.seedStart(),
				request.maxSteps(),
				request.trace()
			)
		);
	}

	@GetMapping("/runs/{runId}")
	public SimulationJobSnapshot getSimulationRun(
		@PathVariable("runId") UUID runId
	) {
		return jobService.getSimulationJob(runId);
	}

	public record AsyncSimulationRequest(
		UUID scenarioId,
		Integer runCount,
		Long seedStart,
		Integer maxSteps,
		Boolean trace
	) {}
}
