package com.athanor.api.compiler;

import static org.hamcrest.Matchers.hasItem;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.athanor.api.scenario.ScenarioExceptionHandler;
import com.athanor.api.scenario.ScenarioGraphValidator;
import com.athanor.api.scenario.ScenarioController;
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

class CompilerControllerTests {

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
			new FilesystemBundleStore(tempDir, objectMapper),
			objectMapper
		);

		mockMvc = MockMvcBuilders
			.standaloneSetup(
				new ScenarioController(scenarioService),
				new CompilerController(compilerService),
				new BundleController(compilerService)
			)
			.setControllerAdvice(new ScenarioExceptionHandler(), new CompilerExceptionHandler())
			.build();
	}

	@Test
	void compileReturnsBundleHashForValidScenario() throws Exception {
		UUID scenarioId = createScenario(validGraph());

		mockMvc
			.perform(post("/scenarios/{id}/compile", scenarioId))
			.andExpect(status().isCreated())
			.andExpect(jsonPath("$.scenarioId").value(scenarioId.toString()))
			.andExpect(jsonPath("$.versionNumber").value(1))
			.andExpect(jsonPath("$.bundleHash").isString())
			.andExpect(jsonPath("$.storedAt").isNotEmpty());
	}

	@Test
	void compileReturnsNotFoundForMissingScenario() throws Exception {
		mockMvc
			.perform(post("/scenarios/{id}/compile", UUID.randomUUID()))
			.andExpect(status().isNotFound())
			.andExpect(jsonPath("$.error").exists());
	}

	@Test
	void compileReturnsValidationErrorsForInvalidScenario() throws Exception {
		Map<String, Object> invalidGraph = new LinkedHashMap<>(validGraph());
		invalidGraph.remove("entry_node_id");
		UUID scenarioId = createScenario(invalidGraph);

		mockMvc
			.perform(post("/scenarios/{id}/compile", scenarioId))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.error").value("scenario graph validation failed"))
			.andExpect(jsonPath("$.validation.valid").value(false))
			.andExpect(jsonPath("$.validation.errors[*].code", hasItem("entry_node")));
	}

	@Test
	void bundleEndpointsReturnStoredMetadataAndContent() throws Exception {
		UUID scenarioId = createScenario(validGraph());

		MvcResult compileResult = mockMvc
			.perform(post("/scenarios/{id}/compile", scenarioId))
			.andExpect(status().isCreated())
			.andReturn();

		String bundleHash = objectMapper
			.readTree(compileResult.getResponse().getContentAsByteArray())
			.get("bundleHash")
			.asText();

		mockMvc
			.perform(get("/bundles/{bundleHash}", bundleHash))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.bundleHash").value(bundleHash))
			.andExpect(jsonPath("$.scenarioId").value(scenarioId.toString()))
			.andExpect(jsonPath("$.versionNumber").value(1))
			.andExpect(jsonPath("$.storedAt").isNotEmpty());

		mockMvc
			.perform(get("/bundles/{bundleHash}/content", bundleHash))
			.andExpect(status().isOk())
			.andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
			.andExpect(jsonPath("$.bundle_hash").value(bundleHash))
			.andExpect(jsonPath("$.entry_node_id").value("start"));
	}

	@Test
	void bundleEndpointsReturnNotFoundForUnknownHash() throws Exception {
		mockMvc
			.perform(get("/bundles/{bundleHash}", "missing"))
			.andExpect(status().isNotFound())
			.andExpect(jsonPath("$.error").value("bundleHash not found"));

		mockMvc
			.perform(get("/bundles/{bundleHash}/content", "missing"))
			.andExpect(status().isNotFound())
			.andExpect(jsonPath("$.error").value("bundleHash not found"));
	}

	@Test
	void compileReturnsInternalServerErrorForIllegalState() throws Exception {
		CompilerService failingCompilerService = new CompilerService(
			new ScenarioService(new ScenarioGraphValidator(), objectMapper),
			new ScenarioGraphValidator(),
			new FilesystemBundleStore(tempDir, objectMapper),
			objectMapper
		) {
			@Override
			public CompilationResult compileLatestScenario(UUID scenarioId) {
				throw new IllegalStateException("failed to store compiled bundle");
			}
		};

		MockMvc failingMockMvc = MockMvcBuilders
			.standaloneSetup(new CompilerController(failingCompilerService))
			.setControllerAdvice(new CompilerExceptionHandler())
			.build();

		failingMockMvc
			.perform(post("/scenarios/{id}/compile", UUID.randomUUID()))
			.andExpect(status().isInternalServerError())
			.andExpect(jsonPath("$.error").value("failed to store compiled bundle"))
			.andExpect(jsonPath("$.validation").doesNotExist());
	}

	@Test
	void compileReturnsBadRequestForIllegalArgument() throws Exception {
		CompilerService failingCompilerService = new CompilerService(
			new ScenarioService(new ScenarioGraphValidator(), objectMapper),
			new ScenarioGraphValidator(),
			new FilesystemBundleStore(tempDir, objectMapper),
			objectMapper
		) {
			@Override
			public CompilationResult compileLatestScenario(UUID scenarioId) {
				throw new IllegalArgumentException("invalid compiled bundle shape");
			}
		};

		MockMvc failingMockMvc = MockMvcBuilders
			.standaloneSetup(new CompilerController(failingCompilerService))
			.setControllerAdvice(new CompilerExceptionHandler())
			.build();

		failingMockMvc
			.perform(post("/scenarios/{id}/compile", UUID.randomUUID()))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.error").value("invalid compiled bundle shape"))
			.andExpect(jsonPath("$.validation").doesNotExist());
	}

	@Test
	void compileReturnsInternalServerErrorForUnexpectedRuntimeException() throws Exception {
		CompilerService failingCompilerService = new CompilerService(
			new ScenarioService(new ScenarioGraphValidator(), objectMapper),
			new ScenarioGraphValidator(),
			new FilesystemBundleStore(tempDir, objectMapper),
			objectMapper
		) {
			@Override
			public CompilationResult compileLatestScenario(UUID scenarioId) {
				throw new RuntimeException("unexpected compiler failure");
			}
		};

		MockMvc failingMockMvc = MockMvcBuilders
			.standaloneSetup(new CompilerController(failingCompilerService))
			.setControllerAdvice(new CompilerExceptionHandler())
			.build();

		failingMockMvc
			.perform(post("/scenarios/{id}/compile", UUID.randomUUID()))
			.andExpect(status().isInternalServerError())
			.andExpect(jsonPath("$.error").value("unexpected compiler failure"))
			.andExpect(jsonPath("$.validation").doesNotExist());
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
				Map.of("id", "start", "type", "DecisionNode"),
				Map.of("id", "terminal", "type", "TerminalNode", "outcome", "approved")
			),
			"edges",
			List.of(Map.of("from", "start", "to", "terminal"))
		);
	}
}
