package com.athanor.api.jobs;

import com.athanor.api.simulation.WorkerRuntimeProperties;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.data.redis.connection.stream.Consumer;
import org.springframework.data.redis.connection.stream.MapRecord;
import org.springframework.data.redis.connection.stream.ReadOffset;
import org.springframework.data.redis.connection.stream.StreamOffset;
import org.springframework.data.redis.connection.stream.StreamReadOptions;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.StreamOperations;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

@Component
public class RedisWorkerRuntimeQueue implements WorkerRuntimeQueue {

	private final StringRedisTemplate redisTemplate;
	private final WorkerRuntimeProperties properties;
	private final ObjectMapper objectMapper;

	public RedisWorkerRuntimeQueue(
		StringRedisTemplate redisTemplate,
		WorkerRuntimeProperties properties,
		ObjectMapper objectMapper
	) {
		this.redisTemplate = redisTemplate;
		this.properties = properties;
		this.objectMapper = objectMapper;
	}

	@Override
	public void publishDispatch(WorkerRuntimeDispatchMessage message) {
		try {
			ops().add(
				properties.getDispatchStream(),
				Map.of(
					"type",
					"dispatch",
					"run_id",
					message.runId().toString(),
					"bundle_hash",
					message.bundleHash(),
					"request_json",
					objectMapper.writeValueAsString(message.request())
				)
			);
		} catch (Exception exception) {
			throw new IllegalStateException("failed to publish worker dispatch", exception);
		}
	}

	@Override
	public void ensureEventConsumerGroup() {
		try {
			ops().add(properties.getEventStream(), Map.of("type", "bootstrap"));
			ops().createGroup(properties.getEventStream(), ReadOffset.latest(), properties.getEventConsumerGroup());
		} catch (Exception ignored) {}
	}

	@Override
	public List<WorkerRuntimeEventMessage> readEvents(Duration timeout, int count) {
		List<MapRecord<String, Object, Object>> records = ops().read(
			Consumer.from(
				properties.getEventConsumerGroup(),
				properties.getEventConsumerName()
			),
			StreamReadOptions.empty().block(timeout).count(count),
			StreamOffset.create(properties.getEventStream(), ReadOffset.lastConsumed())
		);
		if (records == null || records.isEmpty()) {
			return List.of();
		}
		List<WorkerRuntimeEventMessage> events = new java.util.ArrayList<>();
		for (MapRecord<String, Object, Object> record : records) {
			Object type = record.getValue().get("type");
			if ("bootstrap".equals(String.valueOf(type))) {
				acknowledgeEvent(record.getId().getValue());
				continue;
			}
			events.add(toEvent(record));
		}
		return events;
	}

	@Override
	public void acknowledgeEvent(String messageId) {
		ops().acknowledge(
			properties.getEventStream(),
			properties.getEventConsumerGroup(),
			messageId
		);
	}

	private WorkerRuntimeEventMessage toEvent(MapRecord<String, Object, Object> record) {
		Map<String, String> values = new LinkedHashMap<>();
		record.getValue().forEach((key, value) -> values.put(String.valueOf(key), String.valueOf(value)));
		return new WorkerRuntimeEventMessage(
			record.getId().getValue(),
			UUID.fromString(values.get("run_id")),
			values.get("type"),
			values.getOrDefault("payload_json", "{}")
		);
	}

	private StreamOperations<String, Object, Object> ops() {
		return redisTemplate.opsForStream();
	}
}
