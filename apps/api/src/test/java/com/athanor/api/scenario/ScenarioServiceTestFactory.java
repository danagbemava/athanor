package com.athanor.api.scenario;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyIterable;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import tools.jackson.databind.ObjectMapper;

public final class ScenarioServiceTestFactory {

	private ScenarioServiceTestFactory() {}

	public static ScenarioService create(ObjectMapper objectMapper) {
		Map<UUID, ScenarioEntity> scenarios = new ConcurrentHashMap<>();
		Map<UUID, List<ScenarioVersionEntity>> versionsByScenario = new ConcurrentHashMap<>();

		ScenarioEntityJpaRepository scenarioRepository = mock(ScenarioEntityJpaRepository.class);
		ScenarioVersionEntityJpaRepository versionRepository = mock(
			ScenarioVersionEntityJpaRepository.class
		);

		when(scenarioRepository.save(any(ScenarioEntity.class))).thenAnswer(invocation -> {
			ScenarioEntity entity = invocation.getArgument(0);
			scenarios.put(entity.id(), entity);
			return entity;
		});
		when(scenarioRepository.findById(any(UUID.class))).thenAnswer(invocation ->
			Optional.ofNullable(scenarios.get(invocation.getArgument(0)))
		);

		when(versionRepository.save(any(ScenarioVersionEntity.class))).thenAnswer(invocation -> {
			ScenarioVersionEntity entity = invocation.getArgument(0);
			versionsByScenario
				.computeIfAbsent(entity.scenarioId(), ignored -> new ArrayList<>())
				.removeIf(version -> version.id().equals(entity.id()));
			versionsByScenario
				.computeIfAbsent(entity.scenarioId(), ignored -> new ArrayList<>())
				.add(entity);
			return entity;
		});
		when(versionRepository.saveAll(anyIterable())).thenAnswer(invocation -> {
			Iterable<ScenarioVersionEntity> entities = invocation.getArgument(0);
			List<ScenarioVersionEntity> saved = new ArrayList<>();
			for (ScenarioVersionEntity entity : entities) {
				versionsByScenario
					.computeIfAbsent(entity.scenarioId(), ignored -> new ArrayList<>())
					.removeIf(version -> version.id().equals(entity.id()));
				versionsByScenario
					.computeIfAbsent(entity.scenarioId(), ignored -> new ArrayList<>())
					.add(entity);
				saved.add(entity);
			}
			return saved;
		});
		when(versionRepository.findByScenarioIdOrderByVersionNumberAsc(any(UUID.class))).thenAnswer(
			invocation ->
				versionsByScenario
					.getOrDefault(invocation.getArgument(0), List.of())
					.stream()
					.sorted(Comparator.comparingInt(ScenarioVersionEntity::versionNumber))
					.toList()
		);
		when(
			versionRepository.findFirstByScenarioIdOrderByVersionNumberDesc(any(UUID.class))
		).thenAnswer(invocation ->
			versionsByScenario
				.getOrDefault(invocation.getArgument(0), List.of())
				.stream()
				.max(Comparator.comparingInt(ScenarioVersionEntity::versionNumber))
		);
		when(versionRepository.findById(any(UUID.class))).thenAnswer(invocation ->
			versionsByScenario
				.values()
				.stream()
				.flatMap(List::stream)
				.filter(version -> version.id().equals(invocation.getArgument(0)))
				.findFirst()
		);

		return new ScenarioService(
			scenarioRepository,
			versionRepository,
			new ScenarioGraphValidator(),
			objectMapper
		);
	}
}
