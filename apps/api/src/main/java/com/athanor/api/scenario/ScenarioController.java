package com.athanor.api.scenario;

import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/scenarios")
public class ScenarioController {

	private final ScenarioService scenarioService;

	public ScenarioController(ScenarioService scenarioService) {
		this.scenarioService = scenarioService;
	}

	@PostMapping
	public ResponseEntity<ScenarioService.ScenarioSnapshot> createScenario(
		@Valid @RequestBody CreateScenarioRequest request
	) {
		ScenarioService.ScenarioSnapshot created = scenarioService.createScenario(
			new ScenarioService.CreateScenarioCommand(
				request.name(),
				request.description(),
				request.graph()
			)
		);

		return ResponseEntity.status(HttpStatus.CREATED).body(created);
	}

	@PutMapping("/{id}")
	public ResponseEntity<ScenarioService.ScenarioSnapshot> createScenarioVersion(
		@PathVariable("id") UUID scenarioId,
		@Valid @RequestBody UpdateScenarioRequest request
	) {
		ScenarioService.ScenarioSnapshot created = scenarioService.createVersion(
			scenarioId,
			new ScenarioService.CreateVersionCommand(
				request.name(),
				request.description(),
				request.graph()
			)
		);

		return ResponseEntity.status(HttpStatus.CREATED).body(created);
	}

	@PostMapping("/{id}/validate")
	public ScenarioService.ScenarioValidationSnapshot validateScenario(
		@PathVariable("id") UUID scenarioId
	) {
		return scenarioService.validateLatestVersion(scenarioId);
	}
}
