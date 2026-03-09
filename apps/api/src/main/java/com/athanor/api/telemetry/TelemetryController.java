package com.athanor.api.telemetry;

import com.athanor.api.scenario.ScenarioService;
import java.util.UUID;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/scenarios")
public class TelemetryController {

	private final ScenarioService scenarioService;
	private final TelemetryService telemetryService;

	public TelemetryController(
		ScenarioService scenarioService,
		TelemetryService telemetryService
	) {
		this.scenarioService = scenarioService;
		this.telemetryService = telemetryService;
	}

	@GetMapping("/{id}/analytics")
	public ScenarioAnalyticsSnapshot scenarioAnalytics(
		@PathVariable("id") UUID scenarioId
	) {
		scenarioService.latestVersionSnapshot(scenarioId);
		return telemetryService.scenarioAnalytics(scenarioId);
	}
}
