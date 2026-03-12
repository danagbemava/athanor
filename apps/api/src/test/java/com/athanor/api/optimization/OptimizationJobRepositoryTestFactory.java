package com.athanor.api.optimization;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class OptimizationJobRepositoryTestFactory {

	private OptimizationJobRepositoryTestFactory() {}

	public static OptimizationJobEntityJpaRepository create() {
		Map<UUID, OptimizationJobEntity> entities = new ConcurrentHashMap<>();
		OptimizationJobEntityJpaRepository repository = mock(OptimizationJobEntityJpaRepository.class);

		when(repository.save(any(OptimizationJobEntity.class))).thenAnswer(invocation -> {
			OptimizationJobEntity entity = invocation.getArgument(0);
			entities.put(entity.jobId(), entity);
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
