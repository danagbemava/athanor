package com.athanor.api.jobs;

import com.athanor.api.compiler.CompilerService;
import com.athanor.api.simulation.SimulationService;
import com.athanor.api.simulation.SimulationBatchExecutor;
import com.athanor.api.telemetry.TelemetryService;
import io.micrometer.core.instrument.MeterRegistry;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.stereotype.Service;

@Service
public class JobService implements DisposableBean {

	private static final Logger log = LoggerFactory.getLogger(JobService.class);
	private static final int MAX_ATTEMPTS = 4;

	private final CompilerService compilerService;
	private final SimulationService simulationService;
	private final SimulationBatchExecutor simulationBatchExecutor;
	private final TelemetryService telemetryService;
	private final ExecutorService executor;
	private final ConcurrentMap<UUID, SimulationJob> jobs = new ConcurrentHashMap<>();
	private final AtomicInteger pendingJobs = new AtomicInteger();
	private final AtomicInteger runningJobs = new AtomicInteger();
	private final AtomicInteger deadLetterJobs = new AtomicInteger();
	private final int workerCount;

	public JobService(
		CompilerService compilerService,
		SimulationService simulationService,
		SimulationBatchExecutor simulationBatchExecutor,
		TelemetryService telemetryService,
		MeterRegistry meterRegistry
	) {
		this.compilerService = compilerService;
		this.simulationService = simulationService;
		this.simulationBatchExecutor = simulationBatchExecutor;
		this.telemetryService = telemetryService;
		this.workerCount = Math.max(2, Runtime.getRuntime().availableProcessors() / 2);
		this.executor =
			Executors.newFixedThreadPool(workerCount, new SimulationWorkerThreadFactory());

		meterRegistry.gauge("athanor.jobs.queue.depth", pendingJobs);
		meterRegistry.gauge("athanor.jobs.workers.running", runningJobs);
		meterRegistry.gauge("athanor.jobs.workers.idle", this, JobService::idleWorkers);
		meterRegistry.gauge("athanor.jobs.dead_letter.depth", deadLetterJobs);
	}

	public SubmittedSimulationJob submitSimulationJob(
		UUID scenarioId,
		SimulationService.SimulationRequest request
	) {
		if (scenarioId == null) {
			throw new IllegalArgumentException("scenarioId is required");
		}

		SimulationService.SimulationRequest normalizedRequest = simulationService.normalizeRequest(
			request
		);
		SimulationJob job = new SimulationJob(
			UUID.randomUUID(),
			scenarioId,
			normalizedRequest
		);
		jobs.put(job.runId(), job);
		pendingJobs.incrementAndGet();
		schedule(job);
		return new SubmittedSimulationJob(
			job.runId(),
			job.status(),
			job.createdAt(),
			job.totalRuns()
		);
	}

	public SimulationJobSnapshot getSimulationJob(UUID runId) {
		SimulationJob job = jobs.get(runId);
		if (job == null) {
			throw new SimulationJobNotFoundException("runId not found");
		}
		return job.snapshot();
	}

	@Override
	public void destroy() {
		executor.shutdownNow();
	}

	private double idleWorkers() {
		return Math.max(0, workerCount - runningJobs.get());
	}

	private void schedule(SimulationJob job) {
		executor.submit(() -> process(job));
	}

	private void process(SimulationJob job) {
		pendingJobs.decrementAndGet();
		runningJobs.incrementAndGet();
		job.markRunning();

		try {
			var compiledBundle = compilerService.compileScenarioBundle(job.scenarioId());
			SimulationService.SimulationSummary summary = simulationBatchExecutor.executeCompiledBundle(
				compiledBundle,
				job.request(),
				job::recordProgress
			);
			job.markCompleted(summary);
			recordTelemetry(summary);
		} catch (RuntimeException exception) {
			if (job.markForRetry(exception, MAX_ATTEMPTS)) {
				pendingJobs.incrementAndGet();
				schedule(job);
			} else {
				deadLetterJobs.incrementAndGet();
				log.warn(
					"simulation job {} moved to dead-letter queue after {} attempts: {}",
					job.runId(),
					job.attempts(),
					exception.getMessage()
				);
			}
		} finally {
			runningJobs.decrementAndGet();
		}
	}

	private void recordTelemetry(SimulationService.SimulationSummary summary) {
		try {
			telemetryService.recordSimulationSummary(summary);
		} catch (RuntimeException exception) {
			log.warn(
				"telemetry ingest failed for simulation job scenario {}: {}",
				summary.scenarioId(),
				exception.getMessage()
			);
		}
	}

}
