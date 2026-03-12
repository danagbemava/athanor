package com.athanor.api.scenario;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import tools.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;

@Service
public class ScenarioService {

    private final ScenarioEntityJpaRepository scenarioRepository;
    private final ScenarioVersionEntityJpaRepository versionRepository;
    private final ScenarioGraphValidator graphValidator;
    private final ObjectMapper objectMapper;

    @Autowired
    public ScenarioService(
        ScenarioEntityJpaRepository scenarioRepository,
        ScenarioVersionEntityJpaRepository versionRepository,
        ScenarioGraphValidator graphValidator,
        ObjectMapper objectMapper
    ) {
        this.scenarioRepository = scenarioRepository;
        this.versionRepository = versionRepository;
        this.graphValidator = graphValidator;
        this.objectMapper = objectMapper;
    }

    public ScenarioSnapshot createScenario(CreateScenarioCommand command) {
        Instant now = Instant.now();
        UUID scenarioId = UUID.randomUUID();
        UUID versionId = UUID.randomUUID();

        ScenarioVersionEntity version = new ScenarioVersionEntity(
            versionId,
            scenarioId,
            1,
            "draft",
            now,
            writeGraph(command.graph())
        );

        ScenarioEntity aggregate = new ScenarioEntity(
            scenarioId,
            command.name(),
            command.description(),
            now,
            now
        );

        scenarioRepository.save(aggregate);
        versionRepository.save(version);
        return toSnapshot(aggregate, version, 1);
    }

    public ScenarioSnapshot createVersion(
        UUID scenarioId,
        CreateVersionCommand command
    ) {
        ScenarioEntity aggregate = findScenario(scenarioId);
        ScenarioVersionEntity latest = latestVersion(scenarioId);
        int nextVersion = latest.versionNumber() + 1;
        Instant now = Instant.now();

        ScenarioVersionEntity version = new ScenarioVersionEntity(
            UUID.randomUUID(),
            scenarioId,
            nextVersion,
            "draft",
            now,
            writeGraph(command.graph())
        );

        String name = command.name() != null && !command.name().isBlank()
            ? command.name()
            : aggregate.name();
        String description = command.description() != null
            ? command.description()
            : aggregate.description();
        scenarioRepository.save(
            new ScenarioEntity(
                aggregate.id(),
                name,
                description,
                aggregate.createdAt(),
                now
            )
        );
        versionRepository.save(version);
        return toSnapshot(
            new ScenarioEntity(
                aggregate.id(),
                name,
                description,
                aggregate.createdAt(),
                now
            ),
            version,
            nextVersion
        );
    }

    public ScenarioValidationSnapshot validateLatestVersion(UUID scenarioId) {
        ScenarioEntity aggregate = findScenario(scenarioId);
        ScenarioVersionEntity latest = latestVersion(scenarioId);
        ValidationResult result = graphValidator.validate(readGraph(latest.graphJson()));

        return new ScenarioValidationSnapshot(
            aggregate.id(),
            latest.id(),
            latest.versionNumber(),
            result.valid(),
            result.errors(),
            result.warnings()
        );
    }

    public LatestScenarioVersionSnapshot latestVersionSnapshot(UUID scenarioId) {
        ScenarioEntity aggregate = findScenario(scenarioId);
        ScenarioVersionEntity latest = latestVersion(scenarioId);

        return new LatestScenarioVersionSnapshot(
            aggregate.id(),
            aggregate.name(),
            aggregate.description(),
            latest.id(),
            latest.versionNumber(),
            latest.state(),
            latest.createdAt(),
            readGraph(latest.graphJson())
        );
    }

    private ScenarioEntity findScenario(UUID id) {
        return scenarioRepository.findById(id).orElseThrow(() -> new ScenarioNotFoundException(id));
    }

    private ScenarioVersionEntity latestVersion(
        UUID scenarioId
    ) {
        findScenario(scenarioId);
        return versionRepository
            .findFirstByScenarioIdOrderByVersionNumberDesc(scenarioId)
            .orElseThrow(() -> new ScenarioNotFoundException(scenarioId));
    }

    private ScenarioSnapshot toSnapshot(
        ScenarioEntity aggregate,
        ScenarioVersionEntity latestVersion,
        int versionCount
    ) {
        return new ScenarioSnapshot(
            aggregate.id(),
            aggregate.name(),
            aggregate.description(),
            aggregate.createdAt(),
            aggregate.updatedAt(),
            versionCount,
            new ScenarioVersionSummary(
                latestVersion.id(),
                latestVersion.versionNumber(),
                latestVersion.state(),
                latestVersion.createdAt()
            )
        );
    }

    private Map<String, Object> deepCopy(Map<String, Object> value) {
        if (value == null) {
            return Map.of();
        }
		@SuppressWarnings("unchecked")
		Map<String, Object> copied = objectMapper.convertValue(value, Map.class);
		return copied == null ? Map.of() : copied;
	}

    private String writeGraph(Map<String, Object> graph) {
        try {
            return objectMapper.writeValueAsString(deepCopy(graph));
        } catch (RuntimeException exception) {
            throw new IllegalStateException("failed to serialize scenario graph", exception);
        }
    }

    private Map<String, Object> readGraph(String graphJson) {
		try {
			@SuppressWarnings("unchecked")
			Map<String, Object> graph = objectMapper.readValue(graphJson, Map.class);
			return graph == null ? Map.of() : deepCopy(graph);
		} catch (RuntimeException exception) {
			throw new IllegalStateException("failed to deserialize scenario graph", exception);
		}
    }

    public record CreateScenarioCommand(
        String name,
        String description,
        Map<String, Object> graph
    ) {}

    public record CreateVersionCommand(
        String name,
        String description,
        Map<String, Object> graph
    ) {}

    public record ScenarioSnapshot(
        UUID scenarioId,
        String name,
        String description,
        Instant createdAt,
        Instant updatedAt,
        int versionCount,
        ScenarioVersionSummary version
    ) {}

    public record ScenarioVersionSummary(
        UUID id,
        int number,
        String state,
        Instant createdAt
    ) {}

    public record ScenarioValidationSnapshot(
        UUID scenarioId,
        UUID versionId,
        int versionNumber,
        boolean valid,
        List<ValidationMessage> errors,
        List<ValidationMessage> warnings
    ) {}

    public record LatestScenarioVersionSnapshot(
        UUID scenarioId,
        String name,
        String description,
        UUID versionId,
        int versionNumber,
        String state,
        Instant versionCreatedAt,
        Map<String, Object> graph
    ) {}
}
