package com.athanor.api.simulation;

import static org.hamcrest.Matchers.hasKey;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.athanor.api.compiler.CompilerService;
import com.athanor.api.compiler.FilesystemBundleStore;
import com.athanor.api.scenario.ScenarioController;
import com.athanor.api.scenario.ScenarioExceptionHandler;
import com.athanor.api.scenario.ScenarioGraphValidator;
import com.athanor.api.scenario.ScenarioService;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import tools.jackson.databind.ObjectMapper;

class SimulationControllerTests {

	@TempDir
	Path tempDir;

	private MockMvc mockMvc;
	private ObjectMapper objectMapper;

	@BeforeEach
	void setUp() {
		objectMapper = new ObjectMapper();
		ScenarioService scenarioService = new ScenarioService(
			new ScenarioGraphValidator(),
			objectMapper
		);
		CompilerService compilerService = new CompilerService(
			scenarioService,
			new ScenarioGraphValidator(),
			new FilesystemBundleStore(tempDir),
			objectMapper
		);
		SimulationService simulationService = new SimulationService(compilerService, objectMapper);

		mockMvc = MockMvcBuilders
			.standaloneSetup(
				new ScenarioController(scenarioService),
				new SimulationController(simulationService)
			)
			.setControllerAdvice(new ScenarioExceptionHandler(), new SimulationExceptionHandler())
			.build();
	}

	@Test
	void simulateReturnsDeterministicSummaryForValidScenario() throws Exception {
		UUID scenarioId = createScenario(validGraph());

		mockMvc
			.perform(
				post("/scenarios/{id}/simulate", scenarioId)
					.contentType(MediaType.APPLICATION_JSON)
					.content(
						objectMapper.writeValueAsBytes(
							Map.of("runCount", 4, "seedStart", 7, "maxSteps", 100)
						)
					)
			)
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.scenarioId").value(scenarioId.toString()))
			.andExpect(jsonPath("$.versionNumber").value(1))
			.andExpect(jsonPath("$.agentVersion").value("random-v1"))
			.andExpect(jsonPath("$.runCount").value(4))
			.andExpect(jsonPath("$.seedStart").value(7))
			.andExpect(jsonPath("$.outcomeCounts").isMap())
			.andExpect(jsonPath("$.outcomeCounts", hasKey("approved")))
			.andExpect(jsonPath("$.runs.length()").value(4))
			.andExpect(jsonPath("$.runs[0].trace.length()").isNumber())
			.andExpect(jsonPath("$.runs[0].trace[0].nodeId").isString());
	}

	@Test
	void simulateUsesDefaultsWhenRequestBodyIsMissing() throws Exception {
		UUID scenarioId = createScenario(validGraph());

		mockMvc
			.perform(post("/scenarios/{id}/simulate", scenarioId))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.runCount").value(25))
			.andExpect(jsonPath("$.seedStart").value(1))
			.andExpect(jsonPath("$.maxSteps").value(10000))
			.andExpect(jsonPath("$.runs.length()").value(25));
	}

	@Test
	void simulateReturnsValidationErrorsForInvalidScenario() throws Exception {
		Map<String, Object> invalidGraph = new LinkedHashMap<>(validGraph());
		invalidGraph.remove("entry_node_id");
		UUID scenarioId = createScenario(invalidGraph);

		mockMvc
			.perform(post("/scenarios/{id}/simulate", scenarioId))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.error").value("scenario graph validation failed"))
			.andExpect(jsonPath("$.validation.valid").value(false));
	}

	@Test
	void simulateReturnsBadRequestForInvalidRequest() throws Exception {
		UUID scenarioId = createScenario(validGraph());

		mockMvc
			.perform(
				post("/scenarios/{id}/simulate", scenarioId)
					.contentType(MediaType.APPLICATION_JSON)
					.content(objectMapper.writeValueAsBytes(Map.of("runCount", 0)))
			)
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.error").value("runCount must be between 1 and 250"));
	}

	@Test
	void simulateReturnsBadRequestForSeedOverflow() throws Exception {
		UUID scenarioId = createScenario(validGraph());

		mockMvc
			.perform(
				post("/scenarios/{id}/simulate", scenarioId)
					.contentType(MediaType.APPLICATION_JSON)
					.content(
						objectMapper.writeValueAsBytes(
							Map.of("runCount", 2, "seedStart", Long.MAX_VALUE)
						)
					)
			)
			.andExpect(status().isBadRequest())
			.andExpect(
				jsonPath("$.error").value("seedStart range overflows requested runCount")
			);
	}

	@Test
	void simulateReturnsNotFoundForMissingScenario() throws Exception {
		mockMvc
			.perform(post("/scenarios/{id}/simulate", UUID.randomUUID()))
			.andExpect(status().isNotFound())
			.andExpect(jsonPath("$.error").exists());
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
			objectMapper.readTree(result.getResponse().getContentAsByteArray()).get("scenarioId").asText()
		);
	}

	private Map<String, Object> validGraph() {
		return Map.of(
			"id",
			"scenario-id",
			"name",
			"Scenario Graph",
			"version",
			1,
			"entry_node_id",
			"start",
			"nodes",
			List.of(
				Map.of("id", "start", "type", "ChanceNode", "chance_options", List.of(
					Map.of("to", "approved", "weight", 0.75),
					Map.of("to", "declined", "weight", 0.25)
				)),
				Map.of("id", "approved", "type", "TerminalNode", "outcome", "approved"),
				Map.of("id", "declined", "type", "TerminalNode", "outcome", "declined")
			),
			"edges",
			List.of(
				Map.of("from", "start", "to", "approved"),
				Map.of("from", "start", "to", "declined")
			)
		);
	}
}
