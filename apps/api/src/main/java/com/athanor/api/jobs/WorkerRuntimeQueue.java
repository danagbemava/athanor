package com.athanor.api.jobs;

import java.time.Duration;
import java.util.List;

public interface WorkerRuntimeQueue {

	void publishDispatch(WorkerRuntimeDispatchMessage message);

	void ensureEventConsumerGroup();

	List<WorkerRuntimeEventMessage> readEvents(Duration timeout, int count);

	void acknowledgeEvent(String messageId);
}
