package com.athanor.api.jobs;

import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicInteger;

final class SimulationWorkerThreadFactory implements ThreadFactory {

	private final AtomicInteger sequence = new AtomicInteger();

	@Override
	public Thread newThread(Runnable runnable) {
		Thread thread = new Thread(
			runnable,
			"athanor-simulation-worker-" + sequence.incrementAndGet()
		);
		thread.setDaemon(true);
		return thread;
	}
}
