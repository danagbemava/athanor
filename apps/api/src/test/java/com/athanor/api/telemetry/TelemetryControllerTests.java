package com.athanor.api.telemetry;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.athanor.api.scenario.ScenarioController;
import com.athanor.api.scenario.ScenarioExceptionHandler;
import com.athanor.api.scenario.ScenarioGraphValidator;
import com.athanor.api.scenario.ScenarioService;
import com.athanor.api.simulation.SimulationService;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import tools.jackson.databind.ObjectMapper;

class TelemetryControllerTests {

	private MockMvc mockMvc;
	private ObjectMapper objectMapper;
	private TelemetryService telemetryService;

	@BeforeEach
	void setUp() {
		objectMapper = new ObjectMapper();
		telemetryService = new TelemetryService();
		ScenarioService scenarioService = new ScenarioService(
			new ScenarioGraphValidator(),
			objectMapper
		);

		mockMvc = MockMvcBuilders
			.standaloneSetup(
				new ScenarioController(scenarioService),
				new TelemetryController(scenarioService, telemetryService)
			)
			.setControllerAdvice(new ScenarioExceptionHandler())
			.build();
	}

	@Test
	void analyticsReturnsEmptySnapshotForScenarioWithoutTelemetry() throws Exception {
		UUID scenarioId = createScenario(validGraph("start", "terminal"));

		mockMvc
			.perform(get("/scenarios/{id}/analytics", scenarioId))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.scenarioId").value(scenarioId.toString()))
			.andExpect(jsonPath("$.runCount").value(0))
			.andExpect(jsonPath("$.batchCount").value(0));
	}

	@Test
	void analyticsReturnsAggregatedTelemetryForScenario() throws Exception {
		UUID scenarioId = createScenario(validGraph("start", "terminal"));
		telemetryService.recordSimulationSummary(
			new SimulationService.SimulationSummary(
				scenarioId,
				UUID.randomUUID(),
				1,
				"bundle-123",
				"random-v1",
				2,
				1L,
				100,
				1.5d,
				Map.of("approved", 1, "declined", 1),
				List.of(
					new SimulationService.SimulationRun(
						1L,
						"approved",
						1,
						Map.of(),
						List.of(
							new SimulationService.SimulationTraceEvent(
								0,
								"start",
								"decision",
								Map.of(),
								Map.of(),
								List.of(),
								List.of(),
								null,
								"terminal",
								null
							)
						)
					),
					new SimulationService.SimulationRun(
						2L,
						"declined",
						2,
						Map.of(),
						List.of(
							new SimulationService.SimulationTraceEvent(
								0,
								"start",
								"decision",
								Map.of(),
								Map.of(),
								List.of(),
								List.of(),
								null,
								"terminal",
								null
							),
							new SimulationService.SimulationTraceEvent(
								1,
								"terminal",
								"terminal",
								Map.of(),
								Map.of(),
								List.of(),
								List.of(),
								null,
								null,
								"declined"
							)
						)
					)
				),
				Instant.parse("2026-03-09T10:15:30Z")
			)
		);

		mockMvc
			.perform(get("/scenarios/{id}/analytics", scenarioId))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.runCount").value(2))
			.andExpect(jsonPath("$.batchCount").value(1))
			.andExpect(jsonPath("$.averageSteps").value(1.5))
			.andExpect(jsonPath("$.p90Steps").value(2))
			.andExpect(jsonPath("$.outcomeCounts.approved").value(1))
			.andExpect(jsonPath("$.nodeVisitCounts.start").value(2));
	}

	private UUID createScenario(Map<String, Object> graph) throws Exception {
		MvcResult result = mockMvc
			.perform(
				post("/scenarios")
					.contentType(MediaType.APPLICATION_JSON)
					.content(
						objectMapper.writeValueAsBytes(
							Map.of(
								"name",
								"Scenario Name",
								"description",
								"Scenario Description",
								"graph",
								graph
							)
						)
					)
			)
			.andExpect(status().isCreated())
			.andReturn();

		return UUID.fromString(
			objectMapper
				.readTree(result.getResponse().getContentAsByteArray())
				.get("scenarioId")
				.asText()
		);
	}

	private Map<String, Object> validGraph(
		String entryNodeId,
		String terminalNodeId
	) {
		return Map.of(
			"id",
			"scenario-id",
			"name",
			"Scenario Graph",
			"version",
			1,
			"entry_node_id",
			entryNodeId,
			"nodes",
			List.of(
				Map.of("id", entryNodeId, "type", "DecisionNode"),
				Map.of("id", terminalNodeId, "type", "TerminalNode")
			),
			"edges",
			List.of(Map.of("from", entryNodeId, "to", terminalNodeId))
		);
	}
}
