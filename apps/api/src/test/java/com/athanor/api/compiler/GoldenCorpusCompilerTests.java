package com.athanor.api.compiler;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import com.athanor.api.scenario.ScenarioGraphValidator;
import com.athanor.api.scenario.ScenarioService;
import com.athanor.api.scenario.ScenarioServiceTestFactory;
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
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

class GoldenCorpusCompilerTests {

	private final ObjectMapper objectMapper = new ObjectMapper();
	private final ScenarioService scenarioService = ScenarioServiceTestFactory.create(objectMapper);
	private final CompilerService compilerService = new CompilerService(
		scenarioService,
		new ScenarioGraphValidator(),
		new FilesystemBundleStore(Path.of("build", "athanor", "bundles", "golden-tests"), objectMapper),
		objectMapper
	);

	@Test
	void goldenCorpusCompilesToExpectedBundleHashesAndValidSchema() throws Exception {
		List<Path> fixtureDirectories;
		try (Stream<Path> directories = Files.list(goldenRoot())) {
			fixtureDirectories = directories.filter(Files::isDirectory).sorted().toList();
		}

		assertTrue(fixtureDirectories.size() >= 5, "expected at least five golden fixtures");

		JsonSchema executableBundleSchema = executableBundleSchema();
		StringBuilder mismatches = new StringBuilder();

		for (Path fixtureDirectory : fixtureDirectories) {
			Map<String, Object> manifest = objectMapper.readValue(
				fixtureDirectory.resolve("manifest.json").toFile(),
				new TypeReference<>() {}
			);
			Map<String, Object> scenarioGraph = objectMapper.readValue(
				fixtureDirectory.resolve(readString(manifest, "scenario_source", "scenario.json")).toFile(),
				new TypeReference<>() {}
			);

			ScenarioService.ScenarioSnapshot created = scenarioService.createScenario(
				new ScenarioService.CreateScenarioCommand(
					readString(scenarioGraph, "name", fixtureDirectory.getFileName().toString()),
					"golden corpus fixture",
					scenarioGraph
				)
			);

			Map<String, Object> compiledBundle = compilerService.compiledBundlePayload(
				created.scenarioId()
			);
			String actualBundleHash = String.valueOf(compiledBundle.get("bundle_hash"));

			String expectedBundleHash = readString(manifest, "expected_bundle_hash", "");
			if (!expectedBundleHash.equals(actualBundleHash)) {
				mismatches.append("fixture ")
					.append(fixtureDirectory.getFileName())
					.append(" bundle hash expected=")
					.append(expectedBundleHash)
					.append(" actual=")
					.append(actualBundleHash)
					.append('\n');
			}

			String compiledJson = objectMapper.writeValueAsString(compiledBundle);
			assertTrue(
				objectMapper.readTree(compiledJson).has("header"),
				() -> "fixture " + fixtureDirectory.getFileName() + " missing bundle header"
			);

			List<ValidationMessage> errors = executableBundleSchema.validate(
				compiledJson,
				InputFormat.JSON
			).stream().toList();
			assertTrue(
				errors.isEmpty(),
				() -> "fixture " + fixtureDirectory.getFileName() + " produced invalid bundle schema: " + errors
			);

			assertFalse(
				readRuns(manifest).isEmpty(),
				() -> "fixture " + fixtureDirectory.getFileName() + " must define at least one golden run"
			);
		}

		if (mismatches.length() > 0) {
			fail(mismatches.toString());
		}
	}

	private JsonSchema executableBundleSchema() throws IOException {
		String schemaJson = Files.readString(
			Path.of("..", "..", "packages", "spec", "schemas", "executable-bundle.schema.json")
		);
		return JsonSchemaFactory.getInstance(SpecVersion.VersionFlag.V202012)
			.getSchema(schemaJson);
	}

	private Path goldenRoot() {
		return Path.of("..", "..", "packages", "spec", "golden");
	}

	private String readString(Map<String, Object> source, String key, String fallback) {
		Object value = source.get(key);
		return value instanceof String stringValue && !stringValue.isBlank()
			? stringValue
			: fallback;
	}

	@SuppressWarnings("unchecked")
	private List<Map<String, Object>> readRuns(Map<String, Object> manifest) {
		Object value = manifest.get("runs");
		return value instanceof List<?> list ? (List<Map<String, Object>>) value : List.of();
	}
}
