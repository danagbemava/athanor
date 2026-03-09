package com.athanor.api.simulation;

import java.util.UUID;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/scenarios")
public class SimulationController {

	private final SimulationService simulationService;

	public SimulationController(SimulationService simulationService) {
		this.simulationService = simulationService;
	}

	@PostMapping("/{id}/simulate")
	public SimulationService.SimulationSummary simulateScenario(
		@PathVariable("id") UUID scenarioId,
		@RequestBody(required = false) SimulationService.SimulationRequest request
	) {
		return simulationService.simulateLatestScenario(scenarioId, request);
	}
}
