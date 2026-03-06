package com.athanor.api.scenario;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Service;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

@Service
public class ScenarioService {

    private final Map<UUID, ScenarioAggregate> scenarios =
        new ConcurrentHashMap<>();
    private final ScenarioGraphValidator graphValidator;
    private final ObjectMapper objectMapper;

    public ScenarioService(
        ScenarioGraphValidator graphValidator,
        ObjectMapper objectMapper
    ) {
        this.graphValidator = graphValidator;
        this.objectMapper = objectMapper;
    }

    public ScenarioSnapshot createScenario(CreateScenarioCommand command) {
        Instant now = Instant.now();
        UUID scenarioId = UUID.randomUUID();
        UUID versionId = UUID.randomUUID();

        ScenarioVersion version = new ScenarioVersion(
            versionId,
            1,
            "draft",
            now,
            deepCopy(command.graph())
        );

        ScenarioAggregate aggregate = new ScenarioAggregate(
            scenarioId,
            command.name(),
            command.description(),
            now,
            now,
            new ArrayList<>(List.of(version))
        );

        scenarios.put(scenarioId, aggregate);
        return toSnapshot(aggregate, version);
    }

    public ScenarioSnapshot createVersion(
        UUID scenarioId,
        CreateVersionCommand command
    ) {
        ScenarioAggregate aggregate = findScenario(scenarioId);

        synchronized (aggregate) {
            ScenarioVersion latest = latestVersion(aggregate);
            int nextVersion = latest.versionNumber() + 1;
            Instant now = Instant.now();

            ScenarioVersion version = new ScenarioVersion(
                UUID.randomUUID(),
                nextVersion,
                "draft",
                now,
                deepCopy(command.graph())
            );

            aggregate.versions().add(version);
            if (command.name() != null && !command.name().isBlank()) {
                aggregate.setName(command.name());
            }
            if (command.description() != null) {
                aggregate.setDescription(command.description());
            }
            aggregate.setUpdatedAt(now);
            return toSnapshot(aggregate, version);
        }
    }

    public ScenarioValidationSnapshot validateLatestVersion(UUID scenarioId) {
        ScenarioAggregate aggregate = findScenario(scenarioId);
        ScenarioVersion latest = latestVersion(aggregate);
        ValidationResult result = graphValidator.validate(latest.graph());

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
        ScenarioAggregate aggregate = findScenario(scenarioId);
        ScenarioVersion latest = latestVersion(aggregate);

        return new LatestScenarioVersionSnapshot(
            aggregate.id(),
            aggregate.name(),
            aggregate.description(),
            latest.id(),
            latest.versionNumber(),
            latest.state(),
            latest.createdAt(),
            deepCopy(latest.graph())
        );
    }

    private ScenarioAggregate findScenario(UUID id) {
        ScenarioAggregate aggregate = scenarios.get(id);
        if (aggregate == null) {
            throw new ScenarioNotFoundException(id);
        }
        return aggregate;
    }

    private ScenarioVersion latestVersion(ScenarioAggregate aggregate) {
        List<ScenarioVersion> versions = aggregate.versions();
        return versions.get(versions.size() - 1);
    }

    private ScenarioSnapshot toSnapshot(
        ScenarioAggregate aggregate,
        ScenarioVersion latestVersion
    ) {
        return new ScenarioSnapshot(
            aggregate.id(),
            aggregate.name(),
            aggregate.description(),
            aggregate.createdAt(),
            aggregate.updatedAt(),
            aggregate.versions().size(),
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
        Map<String, Object> copied = objectMapper.convertValue(
            value,
            new TypeReference<>() {}
        );
        return copied == null ? Map.of() : copied;
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

    private record ScenarioVersion(
        UUID id,
        int versionNumber,
        String state,
        Instant createdAt,
        Map<String, Object> graph
    ) {}

    private static final class ScenarioAggregate {

        private final UUID id;
        private String name;
        private String description;
        private final Instant createdAt;
        private Instant updatedAt;
        private final List<ScenarioVersion> versions;

        private ScenarioAggregate(
            UUID id,
            String name,
            String description,
            Instant createdAt,
            Instant updatedAt,
            List<ScenarioVersion> versions
        ) {
            this.id = id;
            this.name = name;
            this.description = description;
            this.createdAt = createdAt;
            this.updatedAt = updatedAt;
            this.versions = versions;
        }

        private UUID id() {
            return id;
        }

        private String name() {
            return name;
        }

        private String description() {
            return description;
        }

        private Instant createdAt() {
            return createdAt;
        }

        private Instant updatedAt() {
            return updatedAt;
        }

        private void setUpdatedAt(Instant updatedAt) {
            this.updatedAt = updatedAt;
        }

        private void setName(String name) {
            this.name = name;
        }

        private void setDescription(String description) {
            this.description = description;
        }

        private List<ScenarioVersion> versions() {
            return versions;
        }
    }
}
