package com.athanor.api.telemetry;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import tools.jackson.databind.ObjectMapper;

public final class TelemetryServiceTestFactory {

	private TelemetryServiceTestFactory() {}

	public static TelemetryService create(ObjectMapper objectMapper) {
		Map<UUID, TelemetryEntity> entities = new ConcurrentHashMap<>();
		TelemetryEntityJpaRepository repository = mock(TelemetryEntityJpaRepository.class);

		when(repository.save(any(TelemetryEntity.class))).thenAnswer(invocation -> {
			TelemetryEntity entity = invocation.getArgument(0);
			entities.put(entity.scenarioId(), entity);
			return entity;
		});
		when(repository.findById(any(UUID.class))).thenAnswer(invocation ->
			Optional.ofNullable(entities.get(invocation.getArgument(0)))
		);

		return new TelemetryService(repository, objectMapper);
	}
}
