package com.athanor.api.optimization;

import com.athanor.api.scenario.ScenarioService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import java.util.UUID;

@RestController
@RequestMapping("/optimize")
public class OptimizationController {

	private final OptimizationService optimizationService;

	public OptimizationController(OptimizationService optimizationService) {
		this.optimizationService = optimizationService;
	}

	@PostMapping
	public ResponseEntity<SubmittedOptimizationJob> submitOptimization(
		@RequestBody(required = false) OptimizationRequest request
	) {
		return ResponseEntity.status(HttpStatus.ACCEPTED).body(
			optimizationService.submitOptimizationJob(request)
		);
	}

	@GetMapping("/{jobId}")
	public OptimizationJobSnapshot getOptimizationJob(@PathVariable("jobId") UUID jobId) {
		return optimizationService.getOptimizationJob(jobId);
	}

	@PostMapping("/{jobId}/apply")
	public ResponseEntity<ScenarioService.ScenarioSnapshot> applyOptimization(
		@PathVariable("jobId") UUID jobId
	) {
		return ResponseEntity.status(HttpStatus.CREATED).body(
			optimizationService.applyOptimizedParameters(jobId)
		);
	}
}
