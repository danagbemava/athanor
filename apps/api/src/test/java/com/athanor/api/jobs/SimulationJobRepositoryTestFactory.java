package com.athanor.api.jobs;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class SimulationJobRepositoryTestFactory {

	private SimulationJobRepositoryTestFactory() {}

	public static SimulationJobEntityJpaRepository create() {
		Map<UUID, SimulationJobEntity> entities = new ConcurrentHashMap<>();
		SimulationJobEntityJpaRepository repository = mock(SimulationJobEntityJpaRepository.class);

		when(repository.save(any(SimulationJobEntity.class))).thenAnswer(invocation -> {
			SimulationJobEntity entity = invocation.getArgument(0);
			entities.put(entity.runId(), entity);
			return entity;
		});
		when(repository.findById(any(UUID.class))).thenAnswer(invocation ->
			Optional.ofNullable(entities.get(invocation.getArgument(0)))
		);
		when(repository.findByStatusIn(any())).thenAnswer(invocation -> {
			@SuppressWarnings("unchecked")
			java.util.Collection<String> statuses = invocation.getArgument(0);
			return entities
				.values()
				.stream()
				.filter(entity -> statuses.contains(entity.status()))
				.toList();
		});

		return repository;
	}
}
