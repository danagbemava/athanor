package com.athanor.api.optimization;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.hamcrest.Matchers.anyOf;
import static org.hamcrest.Matchers.is;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.athanor.api.compiler.CompilerService;
import com.athanor.api.compiler.FilesystemBundleStore;
import com.athanor.api.scenario.ScenarioController;
import com.athanor.api.scenario.ScenarioExceptionHandler;
import com.athanor.api.scenario.ScenarioGraphValidator;
import com.athanor.api.scenario.ScenarioService;
import com.athanor.api.scenario.ScenarioServiceTestFactory;
import com.athanor.api.simulation.LocalSimulationBatchExecutor;
import com.athanor.api.simulation.SimulationExceptionHandler;
import com.athanor.api.simulation.SimulationBatchExecutor;
import com.athanor.api.simulation.SimulationService;
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
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

class OptimizationControllerTests {

	@TempDir
	Path tempDir;

	private MockMvc mockMvc;
	private ObjectMapper objectMapper;

	@BeforeEach
	void setUp() {
		objectMapper = new ObjectMapper();
		ScenarioService scenarioService = ScenarioServiceTestFactory.create(objectMapper);
		CompilerService compilerService = new CompilerService(
			scenarioService,
			new ScenarioGraphValidator(),
			new FilesystemBundleStore(tempDir, objectMapper),
			objectMapper
		);
		SimulationService simulationService = new SimulationService(compilerService, objectMapper);
		SimulationBatchExecutor simulationBatchExecutor = new LocalSimulationBatchExecutor(
			simulationService
		);
		OptimizationService optimizationService = new OptimizationService(
			scenarioService,
			new ScenarioGraphValidator(),
			compilerService,
			simulationBatchExecutor,
			objectMapper,
			OptimizationJobRepositoryTestFactory.create()
		);

		mockMvc = MockMvcBuilders
			.standaloneSetup(
				new ScenarioController(scenarioService),
				new OptimizationController(optimizationService)
			)
			.setControllerAdvice(
				new ScenarioExceptionHandler(),
				new SimulationExceptionHandler(),
				new OptimizationExceptionHandler()
			)
			.build();
	}

	@Test
	void optimizationJobConvergesOnSimpleThreeOutcomeScenarioAndCanBeApplied()
		throws Exception {
		UUID scenarioId = createScenario(threeOutcomeGraph());

		MvcResult submitResult = mockMvc
			.perform(
				post("/optimize")
					.contentType(MediaType.APPLICATION_JSON)
					.content(
						objectMapper.writeValueAsBytes(
							Map.of(
								"scenarioId",
								scenarioId,
								"targetDistribution",
								Map.of("success", 0.2, "partial_success", 0.3, "failure", 0.5),
								"maxIterations",
								12,
								"runsPerIteration",
								500
							)
						)
					)
			)
			.andExpect(status().isAccepted())
			.andExpect(jsonPath("$.status").value(anyOf(is("pending"), is("running"))))
			.andReturn();

		String jobId = objectMapper.readTree(resultBytes(submitResult)).get("jobId").textValue();
		JsonNode completedJob = waitForCompletedJob(jobId);

		assertTrue(completedJob.get("bestScore").asDouble() < 0.05d);

		mockMvc
			.perform(post("/optimize/{jobId}/apply", jobId))
			.andExpect(status().isCreated())
			.andExpect(jsonPath("$.scenarioId").value(scenarioId.toString()))
			.andExpect(jsonPath("$.version.number").value(2));

		mockMvc
			.perform(post("/optimize/{jobId}/apply", jobId))
			.andExpect(status().isCreated())
			.andExpect(jsonPath("$.scenarioId").value(scenarioId.toString()))
			.andExpect(jsonPath("$.version.number").value(2));
	}

	@Test
	void missingOptimizationJobReturnsNotFound() throws Exception {
		mockMvc
			.perform(get("/optimize/{jobId}", UUID.randomUUID()))
			.andExpect(status().isNotFound())
			.andExpect(jsonPath("$.error").value("jobId not found"));
	}

	@Test
	void submitOptimizationRejectsMissingScenarioId() throws Exception {
		Map<String, Object> invalidRequest = new LinkedHashMap<>();
		invalidRequest.put("targetDistribution", Map.of("success", 1.0d));

		mockMvc
			.perform(
				post("/optimize")
					.contentType(MediaType.APPLICATION_JSON)
					.content(objectMapper.writeValueAsBytes(invalidRequest))
			)
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.error").value("scenarioId is required"));
	}

	@Test
	void submitOptimizationRejectsDuplicateChanceDestinationsWithActionableError()
		throws Exception {
		UUID scenarioId = createScenario(duplicateChanceDestinationGraph());

		mockMvc
			.perform(
				post("/optimize")
					.contentType(MediaType.APPLICATION_JSON)
					.content(
						objectMapper.writeValueAsBytes(
							Map.of(
								"scenarioId",
								scenarioId,
								"targetDistribution",
								Map.of("approved", 1.0)
							)
						)
					)
			)
			.andExpect(status().isBadRequest())
			.andExpect(
				jsonPath("$.error").value(
					org.hamcrest.Matchers.containsString(
						"chance node 'review' has duplicate destination 'approved'"
					)
				)
			);
	}

	private JsonNode waitForCompletedJob(String jobId) throws Exception {
		for (int attempt = 0; attempt < 120; attempt += 1) {
			MvcResult result = mockMvc
				.perform(get("/optimize/{jobId}", jobId))
				.andExpect(status().isOk())
				.andReturn();
			JsonNode job = objectMapper.readTree(resultBytes(result));
			String status = job.get("status").textValue();
			if ("completed".equals(status)) {
				return job;
			}
			if ("failed".equals(status)) {
				throw new AssertionError(job.get("error").textValue());
			}
			Thread.sleep(25L);
		}
		throw new AssertionError("Optimization job did not complete in time");
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
			objectMapper.readTree(resultBytes(result)).get("scenarioId").textValue()
		);
	}

	private byte[] resultBytes(MvcResult result) throws Exception {
		return result.getResponse().getContentAsByteArray();
	}

	private Map<String, Object> threeOutcomeGraph() {
		return Map.of(
			"id",
			"three-outcome-graph",
			"name",
			"Three Outcome Graph",
			"version",
			1,
			"entry_node_id",
			"start",
			"nodes",
			List.of(
				Map.of(
					"id",
					"start",
					"type",
					"ChanceNode",
					"chance_options",
					List.of(
						Map.of("to", "success_terminal", "weight", 0.34),
						Map.of("to", "partial_terminal", "weight", 0.33),
						Map.of("to", "failure_terminal", "weight", 0.33)
					)
				),
				Map.of(
					"id",
					"success_terminal",
					"type",
					"TerminalNode",
					"outcome",
					"success"
				),
				Map.of(
					"id",
					"partial_terminal",
					"type",
					"TerminalNode",
					"outcome",
					"partial_success"
				),
				Map.of(
					"id",
					"failure_terminal",
					"type",
					"TerminalNode",
					"outcome",
					"failure"
				)
			),
			"edges",
			List.of(
				Map.of("from", "start", "to", "success_terminal"),
				Map.of("from", "start", "to", "partial_terminal"),
				Map.of("from", "start", "to", "failure_terminal")
			)
		);
	}

	private Map<String, Object> duplicateChanceDestinationGraph() {
		return Map.of(
			"id",
			"duplicate-chance-destination",
			"name",
			"Duplicate Chance Destination",
			"version",
			1,
			"entry_node_id",
			"review",
			"nodes",
			List.of(
				Map.of(
					"id",
					"review",
					"type",
					"ChanceNode",
					"chance_options",
					List.of(
						Map.of("to", "approved", "weight", 0.6),
						Map.of("to", "approved", "weight", 0.4)
					)
				),
				Map.of("id", "approved", "type", "TerminalNode", "outcome", "approved")
			),
			"edges",
			List.of(
				Map.of("from", "review", "to", "approved"),
				Map.of("from", "review", "to", "approved")
			)
		);
	}
}
