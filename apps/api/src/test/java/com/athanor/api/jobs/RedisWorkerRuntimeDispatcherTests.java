package com.athanor.api.jobs;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.athanor.api.compiler.CompilerService;
import com.athanor.api.compiler.WorkerExecutionRequestFactory;
import com.athanor.api.simulation.SimulationService;
import com.athanor.api.simulation.WorkerRuntimeProperties;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class RedisWorkerRuntimeDispatcherTests {

	@Test
	void dispatchPublishesRequestToQueue() {
		WorkerRuntimeProperties properties = new WorkerRuntimeProperties();
		properties.setMode("redis");
		CapturingQueue queue = new CapturingQueue();
		RedisWorkerRuntimeDispatcher dispatcher = new RedisWorkerRuntimeDispatcher(
			properties,
			new WorkerExecutionRequestFactory(),
			queue
		);

		dispatcher.dispatchSimulationJob(
			UUID.fromString("11111111-1111-1111-1111-111111111111"),
			new CompilerService.CompiledBundle(
				UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa"),
				UUID.fromString("bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb"),
				4,
				"bundle-hash-123",
				java.util.Map.of()
			),
			new SimulationService.SimulationRequest(3, 12L, 99, true)
		);

		assertEquals(1, queue.dispatched.size());
		WorkerRuntimeDispatchMessage published = queue.dispatched.getFirst();
		assertEquals("bundle-hash-123", published.bundleHash());
		assertEquals(3, published.request().runCount());
		assertEquals("analytics", published.request().executionMode());
		assertTrue(dispatcher.enabled());
	}

	private static final class CapturingQueue implements WorkerRuntimeQueue {

		private final List<WorkerRuntimeDispatchMessage> dispatched = new ArrayList<>();

		@Override
		public void publishDispatch(WorkerRuntimeDispatchMessage message) {
			dispatched.add(message);
		}

		@Override
		public void ensureEventConsumerGroup() {}

		@Override
		public List<WorkerRuntimeEventMessage> readEvents(Duration timeout, int count) {
			return List.of();
		}

		@Override
		public void acknowledgeEvent(String messageId) {}
	}
}
