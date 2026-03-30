package com.athanor.api.jobs;

import com.athanor.api.compiler.CompilerService;
import com.athanor.api.simulation.SimulationRunPage;
import com.athanor.api.simulation.SimulationService;
import com.athanor.api.simulation.SimulationBatchExecutor;
import com.athanor.api.simulation.WorkerExecutionCompletionPayload;
import com.athanor.api.simulation.WorkerExecutionSummaryMapper;
import com.athanor.api.telemetry.TelemetryService;
import io.micrometer.core.instrument.MeterRegistry;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;
import tools.jackson.databind.ObjectMapper;

@Service
public class JobService implements DisposableBean {

	private static final Logger log = LoggerFactory.getLogger(JobService.class);
	private static final int MAX_ATTEMPTS = 4;

	private final CompilerService compilerService;
	private final SimulationService simulationService;
	private final SimulationBatchExecutor simulationBatchExecutor;
	private final WorkerRuntimeDispatcher workerRuntimeDispatcher;
	private final WorkerExecutionSummaryMapper summaryMapper;
	private final TelemetryService telemetryService;
	private final SimulationResultStore simulationResultStore;
	private final SimulationJobEntityJpaRepository jobRepository;
	private final ObjectMapper objectMapper;
	private final ExecutorService executor;
	private final AtomicInteger pendingJobs = new AtomicInteger();
	private final AtomicInteger runningJobs = new AtomicInteger();
	private final AtomicInteger deadLetterJobs = new AtomicInteger();
	private final int workerCount;

	@org.springframework.beans.factory.annotation.Autowired
	public JobService(
		CompilerService compilerService,
		SimulationService simulationService,
		SimulationBatchExecutor simulationBatchExecutor,
		WorkerRuntimeDispatcher workerRuntimeDispatcher,
		WorkerExecutionSummaryMapper summaryMapper,
		TelemetryService telemetryService,
		SimulationResultStore simulationResultStore,
		SimulationJobEntityJpaRepository jobRepository,
		ObjectMapper objectMapper,
		MeterRegistry meterRegistry
	) {
		this.compilerService = compilerService;
		this.simulationService = simulationService;
		this.simulationBatchExecutor = simulationBatchExecutor;
		this.workerRuntimeDispatcher = workerRuntimeDispatcher;
		this.summaryMapper = summaryMapper;
		this.telemetryService = telemetryService;
		this.simulationResultStore = simulationResultStore;
		this.jobRepository = jobRepository;
		this.objectMapper = objectMapper;
		this.workerCount = Math.max(2, Runtime.getRuntime().availableProcessors() / 2);
		this.executor =
			Executors.newFixedThreadPool(workerCount, new SimulationWorkerThreadFactory());

		meterRegistry.gauge("athanor.jobs.queue.depth", pendingJobs);
		meterRegistry.gauge("athanor.jobs.workers.running", runningJobs);
		meterRegistry.gauge("athanor.jobs.workers.idle", this, JobService::idleWorkers);
		meterRegistry.gauge("athanor.jobs.dead_letter.depth", deadLetterJobs);
	}

	@EventListener(ApplicationReadyEvent.class)
	void recoverIncompleteJobs() {
		for (SimulationJobEntity stored : jobRepository.findByStatusIn(
			java.util.List.of("pending")
		)) {
			SimulationJob job = SimulationJob.fromEntity(stored, objectMapper);
			pendingJobs.incrementAndGet();
			schedule(job);
		}
		if (!workerRuntimeDispatcher.enabled()) {
			for (SimulationJobEntity stored : jobRepository.findByStatusIn(
				java.util.List.of("running")
			)) {
				SimulationJob job = SimulationJob.fromEntity(stored, objectMapper);
				job.markForRetry(new IllegalStateException("api restarted during execution"), MAX_ATTEMPTS);
				save(job);
				pendingJobs.incrementAndGet();
				schedule(job);
			}
		}
		for (SimulationJobEntity stored : jobRepository.findByStatusIn(
			java.util.List.of("failed")
		)) {
			if (stored.deadLettered()) {
				deadLetterJobs.incrementAndGet();
			}
		}
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
		save(job);
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
		SimulationJob job = job(runId);
		return job.snapshot();
	}

	public void recordWorkerProgress(
		UUID runId,
		int completedRuns,
		int totalRuns
	) {
		SimulationJob job = job(runId);
		if (isTerminal(job.status())) {
			return;
		}
		job.recordProgress(completedRuns, totalRuns, null);
		save(job);
	}

	public void completeWorkerJob(UUID runId, WorkerExecutionCompletionPayload completionPayload) {
		SimulationJob job = job(runId);
		if (isTerminal(job.status())) {
			return;
		}
		SimulationService.SimulationSummary summary = summaryMapper.toSimulationSummary(
			job.scenarioId(),
			job.versionId(),
			job.versionNumber(),
			job.bundleHash(),
			completionPayload
		);
		job.markCompleted(summary, completionPayload.resultKey());
		save(job);
		recordTelemetry(summary);
		decrementRunningJobs();
	}

	public SimulationRunPage getSimulationTracePage(UUID runId, int page, int pageSize) {
		SimulationJob job = job(runId);
		int sanitizedPage = Math.max(page, 0);
		int sanitizedPageSize = Math.max(1, Math.min(pageSize, 50));
		int offset = sanitizedPage * sanitizedPageSize;

		if (job.summary() == null) {
			return new SimulationRunPage(sanitizedPage, sanitizedPageSize, 0, java.util.List.of());
		}

		if (job.resultKey() == null || job.resultKey().isBlank()) {
			java.util.List<SimulationService.SimulationRun> runs = job.summary().runs();
			int end = Math.min(runs.size(), offset + sanitizedPageSize);
			if (offset >= runs.size()) {
				return new SimulationRunPage(sanitizedPage, sanitizedPageSize, runs.size(), java.util.List.of());
			}
			return new SimulationRunPage(
				sanitizedPage,
				sanitizedPageSize,
				runs.size(),
				runs.subList(offset, end)
			);
		}

		try {
			byte[] payload = simulationResultStore.read(job.resultKey());
			return summaryMapper.toSimulationRunPage(
				objectMapper.readValue(payload, com.athanor.api.compiler.WorkerExecutionResult.class),
				sanitizedPage,
				sanitizedPageSize
			);
		} catch (Exception exception) {
			throw new IllegalStateException("failed to read simulation trace page", exception);
		}
	}

	public void failWorkerJob(UUID runId, String error) {
		SimulationJob job = job(runId);
		if (isTerminal(job.status())) {
			return;
		}
		decrementRunningJobs();
		if (job.markForRetry(new IllegalStateException(error), MAX_ATTEMPTS)) {
			save(job);
			pendingJobs.incrementAndGet();
			schedule(job);
			return;
		}
		save(job);
		deadLetterJobs.incrementAndGet();
		log.warn(
			"simulation job {} moved to dead-letter queue after {} attempts: {}",
			job.runId(),
			job.attempts(),
			error
		);
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
		save(job);
		boolean remoteExecutionAccepted = false;

		try {
			var compiledBundle = compilerService.compileScenarioBundle(job.scenarioId());
			job.attachCompiledBundle(compiledBundle);
			save(job);
			if (workerRuntimeDispatcher.enabled()) {
				workerRuntimeDispatcher.dispatchSimulationJob(job.runId(), compiledBundle, job.request());
				remoteExecutionAccepted = true;
				return;
			}
			SimulationService.SimulationSummary summary = simulationBatchExecutor.executeCompiledBundle(
				compiledBundle,
				job.request(),
				job::recordProgress
			);
			job.markCompleted(summary, null);
			save(job);
			recordTelemetry(summary);
		} catch (RuntimeException exception) {
			if (job.markForRetry(exception, MAX_ATTEMPTS)) {
				save(job);
				pendingJobs.incrementAndGet();
				schedule(job);
			} else {
				save(job);
				deadLetterJobs.incrementAndGet();
				log.warn(
					"simulation job {} moved to dead-letter queue after {} attempts: {}",
					job.runId(),
					job.attempts(),
					exception.getMessage()
				);
			}
		} finally {
			if (!remoteExecutionAccepted) {
				decrementRunningJobs();
			}
		}
	}

	private void decrementRunningJobs() {
		runningJobs.updateAndGet(current -> current > 0 ? current - 1 : 0);
	}

	private boolean isTerminal(String status) {
		return "completed".equals(status) || "failed".equals(status);
	}

	private SimulationJob job(UUID runId) {
		return jobRepository
			.findById(runId)
			.map(entity -> SimulationJob.fromEntity(entity, objectMapper))
			.orElseThrow(() -> new SimulationJobNotFoundException("runId not found"));
	}

	private void save(SimulationJob job) {
		jobRepository.save(job.toEntity(objectMapper));
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
