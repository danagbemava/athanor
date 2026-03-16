package com.athanor.api.jobs;

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
import com.athanor.api.simulation.SimulationBatchExecutor;
import com.athanor.api.simulation.SimulationController;
import com.athanor.api.simulation.SimulationExceptionHandler;
import com.athanor.api.simulation.SimulationService;
import com.athanor.api.simulation.WorkerExecutionSummaryMapper;
import com.athanor.api.telemetry.TelemetryService;
import com.athanor.api.telemetry.TelemetryServiceTestFactory;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
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

class SimulationJobControllerTests {

	@TempDir
	Path tempDir;

	private MockMvc mockMvc;
	private ObjectMapper objectMapper;
	private JobService jobService;

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
		TelemetryService telemetryService = TelemetryServiceTestFactory.create(objectMapper);
		SimulationResultStore simulationResultStore = SimulationResultStoreTestFactory.create();
		jobService = new JobService(
			compilerService,
			simulationService,
			simulationBatchExecutor,
			new NoopWorkerRuntimeDispatcher(),
			new WorkerExecutionSummaryMapper(objectMapper),
			telemetryService,
			simulationResultStore,
			SimulationJobRepositoryTestFactory.create(),
			objectMapper,
			new SimpleMeterRegistry()
		);

		mockMvc = MockMvcBuilders
			.standaloneSetup(
				new ScenarioController(scenarioService),
				new SimulationController(simulationService),
				new SimulationJobController(jobService)
			)
			.setControllerAdvice(
				new ScenarioExceptionHandler(),
				new SimulationExceptionHandler(),
				new JobExceptionHandler()
			)
			.build();
	}

	@Test
	void submitSimulationReturnsAcceptedAndRunCanBePolled() throws Exception {
		UUID scenarioId = createScenario(validGraph());

		MvcResult submitResult = mockMvc
			.perform(
				post("/simulate")
					.contentType(MediaType.APPLICATION_JSON)
					.content(
						objectMapper.writeValueAsBytes(
							Map.of("scenarioId", scenarioId, "runCount", 4, "seedStart", 8)
						)
					)
			)
			.andExpect(status().isAccepted())
			.andExpect(jsonPath("$.status").value("pending"))
			.andReturn();

		JsonNode submitted = objectMapper.readTree(resultBytes(submitResult));
		String runId = submitted.get("runId").textValue();

		mockMvc
			.perform(get("/runs/{runId}", runId))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.runId").value(runId))
			.andExpect(jsonPath("$.jobType").value("simulation_batch"))
			.andExpect(jsonPath("$.status").exists());
	}

	@Test
	void missingRunReturnsNotFound() throws Exception {
		mockMvc
			.perform(get("/runs/{runId}", UUID.randomUUID()))
			.andExpect(status().isNotFound())
			.andExpect(jsonPath("$.error").value("runId not found"));
	}

	@Test
	void traceRunsEndpointReturnsPagedSimulationRuns() throws Exception {
		UUID scenarioId = createScenario(validGraph());

		MvcResult submitResult = mockMvc
			.perform(
				post("/simulate")
					.contentType(MediaType.APPLICATION_JSON)
					.content(
						objectMapper.writeValueAsBytes(
							Map.of("scenarioId", scenarioId, "runCount", 4, "seedStart", 8)
						)
					)
			)
			.andExpect(status().isAccepted())
			.andReturn();

		String runId = objectMapper
			.readTree(resultBytes(submitResult))
			.get("runId")
			.textValue();

		awaitCompletion(UUID.fromString(runId));

		mockMvc
			.perform(get("/runs/{runId}/trace-runs", runId).param("page", "0").param("pageSize", "2"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.page").value(0))
			.andExpect(jsonPath("$.pageSize").value(2))
			.andExpect(jsonPath("$.totalRuns").value(4))
			.andExpect(jsonPath("$.runs.length()").value(2));
	}

	@Test
	void submitSimulationRejectsMissingScenarioId() throws Exception {
		Map<String, Object> invalidRequest = new LinkedHashMap<>();
		invalidRequest.put("runCount", 2);

		mockMvc
			.perform(
				post("/simulate")
					.contentType(MediaType.APPLICATION_JSON)
					.content(objectMapper.writeValueAsBytes(invalidRequest))
			)
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.error").value("scenarioId is required"));
	}

	private byte[] resultBytes(MvcResult result) throws Exception {
		return result.getResponse().getContentAsByteArray();
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

	private void awaitCompletion(UUID runId) throws Exception {
		long deadline = System.currentTimeMillis() + 5000L;
		while (System.currentTimeMillis() < deadline) {
			SimulationJobSnapshot snapshot = jobService.getSimulationJob(runId);
			if ("completed".equals(snapshot.status())) {
				return;
			}
			Thread.sleep(25L);
		}
		throw new AssertionError("simulation job did not complete");
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
