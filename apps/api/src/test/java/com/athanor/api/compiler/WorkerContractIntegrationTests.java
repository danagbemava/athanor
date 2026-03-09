package com.athanor.api.compiler;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.athanor.api.scenario.ScenarioGraphValidator;
import com.athanor.api.scenario.ScenarioService;
import com.networknt.schema.InputFormat;
import com.networknt.schema.JsonSchema;
import com.networknt.schema.JsonSchemaFactory;
import com.networknt.schema.SpecVersion;
import com.networknt.schema.ValidationMessage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

class WorkerContractIntegrationTests {

	@TempDir
	Path tempDir;

	private ObjectMapper objectMapper;
	private ScenarioService scenarioService;
	private CompilerService compilerService;
	private WorkerExecutionRequestFactory workerExecutionRequestFactory;

	@BeforeEach
	void setUp() {
		objectMapper = new ObjectMapper();
		scenarioService = new ScenarioService(
			new ScenarioGraphValidator(),
			objectMapper
		);
		compilerService = new CompilerService(
			scenarioService,
			new ScenarioGraphValidator(),
			new FilesystemBundleStore(tempDir, objectMapper),
			objectMapper
		);
		workerExecutionRequestFactory = new WorkerExecutionRequestFactory();
	}

	@Test
	void compiledBundleCanBeConsumedByWorkerCli() throws Exception {
		java.util.UUID scenarioId = createScenario();
		CompilerService.CompilationResult compilationResult = compilerService.compileLatestScenario(
			scenarioId
		);
		CompilerService.CompiledBundle bundle = compilerService.compileScenarioBundle(scenarioId);

		WorkerExecutionRequest request = workerExecutionRequestFactory.forSimulation(
			bundle,
			2,
			1L,
			10,
			WorkerExecutionMode.ANALYTICS
		);

		Path requestPath = tempDir.resolve("worker-request.json");
		objectMapper.writeValue(requestPath.toFile(), request);

		Process process = new ProcessBuilder(
			"go",
			"run",
			"./cmd/worker",
			"run",
			"--bundle",
			compilerService.bundleContentPath(compilationResult.bundleHash()).toAbsolutePath().toString(),
			"--request",
			requestPath.toAbsolutePath().toString()
		)
			.directory(Path.of("..", "worker").toFile())
			.redirectErrorStream(true)
			.start();

		String output = new String(process.getInputStream().readAllBytes());
		int exitCode = process.waitFor();
		assertEquals(0, exitCode, output);

		List<ValidationMessage> errors = workerExecutionResultSchema().validate(
			output,
			InputFormat.JSON
		).stream().toList();
		assertTrue(errors.isEmpty(), () -> "schema errors: " + errors + "\n" + output);

		JsonNode result = objectMapper.readTree(output);
		assertEquals(compilationResult.bundleHash(), result.get("bundle_hash").asText());
		assertEquals("analytics", result.get("execution_mode").asText());
		assertEquals(2, result.get("runs").size());
		assertTrue(result.at("/runs/0/trace").isArray());
	}

	private java.util.UUID createScenario() {
		ScenarioService.ScenarioSnapshot created = scenarioService.createScenario(
			new ScenarioService.CreateScenarioCommand("Worker Fixture", null, validGraph())
		);
		return created.scenarioId();
	}

	private JsonSchema workerExecutionResultSchema() throws IOException {
		String schemaJson = Files.readString(
			Path.of("..", "..", "packages", "spec", "schemas", "worker-execution-result.schema.json")
		);
		return JsonSchemaFactory.getInstance(SpecVersion.VersionFlag.V202012)
			.getSchema(schemaJson);
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
