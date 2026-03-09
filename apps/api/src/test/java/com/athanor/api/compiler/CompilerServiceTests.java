package com.athanor.api.compiler;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.athanor.api.scenario.ScenarioGraphValidator;
import com.athanor.api.scenario.ScenarioService;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.networknt.schema.InputFormat;
import com.networknt.schema.JsonSchema;
import com.networknt.schema.JsonSchemaFactory;
import com.networknt.schema.SpecVersion;
import com.networknt.schema.ValidationMessage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

class CompilerServiceTests {

	@TempDir
	Path tempDir;

	private ObjectMapper objectMapper;
	private ScenarioService scenarioService;
	private CompilerService compilerService;

	@BeforeEach
	void setUp() {
		objectMapper = new ObjectMapper();
		scenarioService = new ScenarioService(new ScenarioGraphValidator(), objectMapper);
		compilerService = new CompilerService(
			scenarioService,
			new ScenarioGraphValidator(),
			new FilesystemBundleStore(tempDir, objectMapper),
			objectMapper
		);
	}

	@Test
	void compileIsDeterministicAndDoesNotRewriteExistingBundle() throws Exception {
		ScenarioService.ScenarioSnapshot created = scenarioService.createScenario(
			new ScenarioService.CreateScenarioCommand("Scenario", null, validGraph())
		);

		CompilerService.CompilationResult first = compilerService.compileLatestScenario(created.scenarioId());
		Path bundlePath = tempDir.resolve(first.bundleHash() + ".json");
		assertTrue(Files.exists(bundlePath));
		var firstStoredAt = Files.getLastModifiedTime(bundlePath).toInstant();

		CompilerService.CompilationResult second = compilerService.compileLatestScenario(created.scenarioId());
		var secondStoredAt = Files.getLastModifiedTime(bundlePath).toInstant();

		assertEquals(first.bundleHash(), second.bundleHash());
		assertEquals(first.storedAt(), second.storedAt());
		assertEquals(firstStoredAt, secondStoredAt);
	}

	@Test
	void compileProducesWorkerCompatiblePayloadAndStableOrdering() throws Exception {
		ScenarioService.ScenarioSnapshot created = scenarioService.createScenario(
			new ScenarioService.CreateScenarioCommand("Scenario", null, complexGraph())
		);

		Map<String, Object> payload = compilerService.compiledBundlePayload(created.scenarioId());
		List<?> nodes = (List<?>) payload.get("nodes");
		assertEquals(List.of("start", "chance", "left", "right", "orphan"), nodeIds(nodes));

		JsonNode json = objectMapper.valueToTree(payload);
		assertEquals("decision", json.at("/nodes/0/type").asText());
		assertEquals("chance", json.at("/nodes/1/type").asText());
		assertEquals("terminal", json.at("/nodes/2/type").asText());
		assertEquals("approved", json.at("/nodes/2/outcome").asText());
		assertEquals("left", json.at("/nodes/1/chance_options/0/to").asText());
		assertFalse(json.at("/nodes/0/decision_options").isMissingNode());
	}

	@Test
	void storedBundleMetadataSurvivesCompilerServiceRecreation() throws Exception {
		ScenarioService.ScenarioSnapshot created = scenarioService.createScenario(
			new ScenarioService.CreateScenarioCommand("Scenario", null, validGraph())
		);

		CompilerService.CompilationResult first = compilerService.compileLatestScenario(created.scenarioId());

		CompilerService reloaded = new CompilerService(
			scenarioService,
			new ScenarioGraphValidator(),
			new FilesystemBundleStore(tempDir, objectMapper),
			objectMapper
		);

		BundleMetadata metadata = reloaded.bundleMetadata(first.bundleHash());
		Map<String, Object> bundleContent = objectMapper.readValue(
			reloaded.bundleContent(first.bundleHash()),
			Map.class
		);

		assertEquals(first.bundleHash(), metadata.bundleHash());
		assertEquals(created.scenarioId(), metadata.scenarioId());
		assertEquals(first.versionId(), metadata.versionId());
		assertEquals(first.versionNumber(), metadata.versionNumber());
		assertEquals(BundleRetentionClass.DRAFT, metadata.retentionClass());
		assertEquals(1, metadata.referenceCount());
		assertEquals("0.0.1-SNAPSHOT", metadata.compilerVersion());
		assertEquals(first.bundleHash(), bundleContent.get("bundle_hash"));
		assertEquals(tempDir.resolve(first.bundleHash() + ".json"), reloaded.bundleContentPath(first.bundleHash()));
	}

	@Test
	void bundleMetadataReadsUpdateLastAccessedAt() throws Exception {
		ScenarioService.ScenarioSnapshot created = scenarioService.createScenario(
			new ScenarioService.CreateScenarioCommand("Scenario", null, validGraph())
		);

		CompilerService.CompilationResult result = compilerService.compileLatestScenario(created.scenarioId());
		BundleMetadata firstRead = compilerService.bundleMetadata(result.bundleHash());
		BundleMetadata secondRead = compilerService.bundleMetadata(result.bundleHash());

		assertTrue(!secondRead.lastAccessedAt().isBefore(firstRead.lastAccessedAt()));
	}

	@Test
	void bundleLookupRejectsPathLikeHashValues() {
		assertThrows(
			IllegalArgumentException.class,
			() -> compilerService.bundleMetadata("../../etc/passwd")
		);
		assertThrows(
			IllegalArgumentException.class,
			() -> compilerService.bundleContent("../not-a-hash")
		);
	}

	@Test
	void compiledBundleMatchesSharedSchemaAndIsReadableFromDisk() throws Exception {
		ScenarioService.ScenarioSnapshot created = scenarioService.createScenario(
			new ScenarioService.CreateScenarioCommand("Scenario", null, validGraph())
		);

		CompilerService.CompilationResult result = compilerService.compileLatestScenario(created.scenarioId());
		Path bundlePath = tempDir.resolve(result.bundleHash() + ".json");
		assertTrue(Files.exists(bundlePath));

		String bundleJson = Files.readString(bundlePath);
		Set<ValidationMessage> errors = executableBundleSchema().validate(
			bundleJson,
			InputFormat.JSON
		);

		assertTrue(errors.isEmpty(), () -> "schema errors: " + errors);

		WorkerBundle bundle = objectMapper.readValue(bundleJson, WorkerBundle.class);
		assertEquals(result.bundleHash(), bundle.bundleHash());
		assertEquals("start", bundle.entryNodeId());
		assertEquals(2, bundle.nodes().size());
		assertNotNull(bundle.header());
	}

	private JsonSchema executableBundleSchema() throws IOException {
		String schemaJson = Files.readString(
			Path.of("..", "..", "packages", "spec", "schemas", "executable-bundle.schema.json")
		);
		JsonSchemaFactory factory = JsonSchemaFactory.getInstance(SpecVersion.VersionFlag.V202012);
		return factory.getSchema(schemaJson, InputFormat.JSON);
	}

	private List<String> nodeIds(List<?> nodes) {
		return nodes.stream()
			.map(Map.class::cast)
			.map(node -> String.valueOf(node.get("id")))
			.toList();
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

	private Map<String, Object> complexGraph() {
		Map<String, Object> start = new LinkedHashMap<>();
		start.put("id", "start");
		start.put("type", "DecisionNode");
		start.put("decision_options", List.of(Map.of("to", "chance")));

		Map<String, Object> chance = new LinkedHashMap<>();
		chance.put("id", "chance");
		chance.put("type", "ChanceNode");
		chance.put(
			"chance_options",
			List.of(Map.of("to", "left", "weight", 0.25), Map.of("to", "right", "weight", 0.75))
		);

		Map<String, Object> left = new LinkedHashMap<>();
		left.put("id", "left");
		left.put("type", "TerminalNode");
		left.put("outcome", "approved");

		Map<String, Object> right = new LinkedHashMap<>();
		right.put("id", "right");
		right.put("type", "TerminalNode");
		right.put("outcome", "declined");

		Map<String, Object> orphan = new LinkedHashMap<>();
		orphan.put("id", "orphan");
		orphan.put("type", "DecisionNode");

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
			List.of(orphan, right, chance, left, start),
			"edges",
			List.of(
				Map.of("from", "start", "to", "chance"),
				Map.of("from", "chance", "to", "right"),
				Map.of("from", "chance", "to", "left")
			)
		);
	}

	private record WorkerBundle(
		@JsonProperty("bundle_hash") String bundleHash,
		@JsonProperty("entry_node_id") String entryNodeId,
		Map<String, Object> header,
		List<Map<String, Object>> nodes
	) {}
}
