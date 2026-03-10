package com.athanor.api.jobs;

import com.athanor.api.simulation.WorkerRuntimeProperties;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.stereotype.Component;

@Component
public class WorkerRuntimeEventProcessor implements InitializingBean, DisposableBean {

	private static final Logger log = LoggerFactory.getLogger(WorkerRuntimeEventProcessor.class);

	private final WorkerRuntimeProperties properties;
	private final WorkerRuntimeQueue workerRuntimeQueue;
	private final WorkerRuntimeEventHandler eventHandler;
	private final ExecutorService executor = Executors.newSingleThreadExecutor();
	private volatile boolean running;

	public WorkerRuntimeEventProcessor(
		WorkerRuntimeProperties properties,
		WorkerRuntimeQueue workerRuntimeQueue,
		WorkerRuntimeEventHandler eventHandler
	) {
		this.properties = properties;
		this.workerRuntimeQueue = workerRuntimeQueue;
		this.eventHandler = eventHandler;
	}

	@Override
	public void afterPropertiesSet() {
		if (!properties.redisModeEnabled()) {
			return;
		}
		workerRuntimeQueue.ensureEventConsumerGroup();
		running = true;
		executor.submit(this::runLoop);
	}

	@Override
	public void destroy() {
		running = false;
		executor.shutdownNow();
	}

	private void runLoop() {
		while (running && !Thread.currentThread().isInterrupted()) {
			try {
				List<WorkerRuntimeEventMessage> events = workerRuntimeQueue.readEvents(
					Duration.ofSeconds(1),
					10
				);
				for (WorkerRuntimeEventMessage event : events) {
					eventHandler.handle(event);
					workerRuntimeQueue.acknowledgeEvent(event.messageId());
				}
			} catch (RuntimeException exception) {
				log.warn("worker runtime event loop failed: {}", exception.getMessage());
			}
		}
	}
}
