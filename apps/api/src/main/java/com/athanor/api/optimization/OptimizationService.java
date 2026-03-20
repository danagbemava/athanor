package com.athanor.api.optimization;

import com.athanor.api.compiler.CompilerService;
import com.athanor.api.compiler.BundleRetentionClass;
import com.athanor.api.scenario.ScenarioGraphValidator;
import com.athanor.api.scenario.ScenarioService;
import com.athanor.api.simulation.SimulationBatchExecutor;
import com.athanor.api.simulation.SimulationService;
import jakarta.annotation.PostConstruct;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Random;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

@Service
public class OptimizationService implements DisposableBean {

	private static final String RANDOM_SEARCH = "random_search";
	private static final int DEFAULT_MAX_ITERATIONS = 25;
	private static final int DEFAULT_RUNS_PER_ITERATION = 500;
	private static final double CONVERGENCE_THRESHOLD = 0.05d;

	private final ScenarioService scenarioService;
	private final ScenarioGraphValidator scenarioGraphValidator;
	private final CompilerService compilerService;
	private final SimulationBatchExecutor simulationBatchExecutor;
	private final ObjectMapper objectMapper;
	private final OptimizationJobEntityJpaRepository jobRepository;
	private final ExecutorService executor;

	@Autowired
	public OptimizationService(
		ScenarioService scenarioService,
		ScenarioGraphValidator scenarioGraphValidator,
		CompilerService compilerService,
		SimulationBatchExecutor simulationBatchExecutor,
		ObjectMapper objectMapper,
		OptimizationJobEntityJpaRepository jobRepository
	) {
		this.scenarioService = scenarioService;
		this.scenarioGraphValidator = scenarioGraphValidator;
		this.compilerService = compilerService;
		this.simulationBatchExecutor = simulationBatchExecutor;
		this.objectMapper = objectMapper;
		this.jobRepository = jobRepository;
		this.executor = Executors.newFixedThreadPool(Math.max(1, Runtime.getRuntime().availableProcessors() / 4));
	}

	@PostConstruct
	void recoverIncompleteJobs() {
		for (OptimizationJobEntity stored : jobRepository.findByStatusIn(
			List.of("pending", "running")
		)) {
			OptimizationJob job = OptimizationJob.fromEntity(stored, objectMapper);
			job.markFailed(new IllegalStateException("optimization interrupted by api restart"));
			save(job);
		}
	}

	public SubmittedOptimizationJob submitOptimizationJob(OptimizationRequest request) {
		OptimizationCommand command = normalize(request);
		ScenarioService.LatestScenarioVersionSnapshot latestVersion = scenarioService.latestVersionSnapshot(
			command.scenarioId()
		);
		validateSearchSpace(latestVersion.graph());

		OptimizationJob job = new OptimizationJob(
			UUID.randomUUID(),
			latestVersion.scenarioId(),
			latestVersion.versionId(),
			latestVersion.versionNumber(),
			command.targetDistribution(),
			command.maxIterations(),
			command.runsPerIteration(),
			command.strategy()
		);
		save(job);
		executor.submit(() -> process(job, latestVersion.graph()));
		return new SubmittedOptimizationJob(
			job.jobId(),
			job.status(),
			job.createdAt(),
			command.maxIterations(),
			command.runsPerIteration(),
			command.strategy()
		);
	}

	public OptimizationJobSnapshot getOptimizationJob(UUID jobId) {
		return job(jobId).snapshot();
	}

	public ScenarioService.ScenarioSnapshot applyOptimizedParameters(UUID jobId) {
		OptimizationJob job = job(jobId);
		if (!Objects.equals(job.status(), "completed")) {
			throw new IllegalArgumentException("optimization job has not completed");
		}
		OptimizationJobSnapshot snapshot = job.snapshot();
		if (snapshot.appliedVersionId() != null) {
			return scenarioService.snapshotForVersion(
				job.scenarioId(),
				snapshot.appliedVersionId()
			);
		}

		Map<String, Object> graph = job.bestGraph();
		if (graph == null) {
			throw new IllegalArgumentException("optimization job has no best parameters");
		}

		ScenarioService.ScenarioSnapshot appliedSnapshot = scenarioService.createVersion(
			job.scenarioId(),
			new ScenarioService.CreateVersionCommand(null, null, graph)
		);
		job.markApplied(appliedSnapshot.version().id(), appliedSnapshot.version().number());
		save(job);
		return appliedSnapshot;
	}

	@Override
	public void destroy() {
		executor.shutdownNow();
	}

	private void process(OptimizationJob job, Map<String, Object> sourceGraph) {
		job.markRunning();
		save(job);

		try {
			Map<String, Object> baseGraph = deepCopyGraph(sourceGraph);
			List<OptimizationParameters> seededCandidates = seededCandidates(
				baseGraph,
				job.snapshot().targetDistribution()
			);
			Random random = new Random(job.jobId().getMostSignificantBits() ^ job.jobId().getLeastSignificantBits());

			for (int iteration = 1; iteration <= job.maxIterations(); iteration += 1) {
				OptimizationParameters parameters = iteration <= seededCandidates.size()
					? seededCandidates.get(iteration - 1)
					: randomParameters(baseGraph, random);
				OptimizationCandidate candidate = evaluateCandidate(
					job.snapshot(),
					baseGraph,
					parameters,
					iteration
				);
				job.recordIteration(iteration, candidate);
				save(job);
				if (candidate.score() < CONVERGENCE_THRESHOLD) {
					job.markCompleted(true);
					save(job);
					return;
				}
			}

			job.markCompleted(false);
			save(job);
		} catch (RuntimeException exception) {
			job.markFailed(exception);
			save(job);
		}
	}

	private OptimizationJob job(UUID jobId) {
		return jobRepository
			.findById(jobId)
			.map(entity -> OptimizationJob.fromEntity(entity, objectMapper))
			.orElseThrow(() -> new OptimizationJobNotFoundException("jobId not found"));
	}

	private void save(OptimizationJob job) {
		jobRepository.save(job.toEntity(objectMapper));
	}

	private OptimizationCandidate evaluateCandidate(
		OptimizationJobSnapshot job,
		Map<String, Object> baseGraph,
		OptimizationParameters parameters,
		int iteration
	) {
		Map<String, Object> candidateGraph = applyParameters(baseGraph, parameters);
		CompilerService.CompiledBundle compiledBundle = compilerService.compileGraph(
			job.scenarioId(),
			job.baseVersionId(),
			job.baseVersionNumber(),
			candidateGraph
		);
		compilerService.storeCompiledBundle(compiledBundle, BundleRetentionClass.ORPHAN);
		SimulationService.SimulationSummary summary = simulationBatchExecutor.executeCompiledBundle(
			compiledBundle,
			new SimulationService.SimulationRequest(
				job.runsPerIteration(),
				(long) ((iteration - 1) * job.runsPerIteration()) + 1L,
				10_000,
				false
			),
			null
		);
		Map<String, Double> actualDistribution = outcomeRates(
			summary.outcomeCounts(),
			summary.runCount()
		);
		double score = score(actualDistribution, job.targetDistribution());
		return new OptimizationCandidate(
			parameters,
			candidateGraph,
			actualDistribution,
			score
		);
	}

	private OptimizationCommand normalize(OptimizationRequest request) {
		if (request == null || request.scenarioId() == null) {
			throw new IllegalArgumentException("scenarioId is required");
		}

		Map<String, Double> normalizedTargets = normalizeTargetDistribution(
			request.targetDistribution()
		);
		int maxIterations = request.maxIterations() == null
			? DEFAULT_MAX_ITERATIONS
			: request.maxIterations();
		int runsPerIteration = request.runsPerIteration() == null
			? DEFAULT_RUNS_PER_ITERATION
			: request.runsPerIteration();
		String strategy = request.strategy() == null || request.strategy().isBlank()
			? RANDOM_SEARCH
			: request.strategy().trim().toLowerCase();

		if (!Objects.equals(strategy, RANDOM_SEARCH)) {
			throw new IllegalArgumentException("strategy must be random_search");
		}
		if (maxIterations < 1 || maxIterations > 250) {
			throw new IllegalArgumentException("maxIterations must be between 1 and 250");
		}
		if (runsPerIteration < 10 || runsPerIteration > 5_000) {
			throw new IllegalArgumentException(
				"runsPerIteration must be between 10 and 5000"
			);
		}

		return new OptimizationCommand(
			request.scenarioId(),
			normalizedTargets,
			maxIterations,
			runsPerIteration,
			strategy
		);
	}

	private Map<String, Double> normalizeTargetDistribution(
		Map<String, Double> targetDistribution
	) {
		if (targetDistribution == null || targetDistribution.isEmpty()) {
			throw new IllegalArgumentException("targetDistribution is required");
		}

		Map<String, Double> sanitized = new LinkedHashMap<>();
		double total = 0d;
		for (Map.Entry<String, Double> entry : targetDistribution.entrySet()) {
			String key = entry.getKey() == null ? "" : entry.getKey().trim();
			Double rawValue = entry.getValue();
			if (key.isEmpty()) {
				throw new IllegalArgumentException("targetDistribution keys must be non-empty");
			}
			if (rawValue == null || !Double.isFinite(rawValue) || rawValue < 0d) {
				throw new IllegalArgumentException(
					"targetDistribution values must be finite and zero or greater"
				);
			}
			sanitized.put(key, rawValue);
			total += rawValue;
		}

		if (total <= 0d) {
			throw new IllegalArgumentException(
				"targetDistribution must contain at least one positive weight"
			);
		}

		double normalizer = total;
		Map<String, Double> normalized = new LinkedHashMap<>();
		sanitized.forEach((key, value) -> normalized.put(key, value / normalizer));
		return normalized;
	}

	private void validateSearchSpace(Map<String, Object> graph) {
		var validation = scenarioGraphValidator.validate(graph);
		if (!validation.valid()) {
			String details = validation
				.errors()
				.stream()
				.map(message -> message.message())
				.reduce((left, right) -> left + "; " + right)
				.orElse("invalid scenario graph");
			throw new IllegalArgumentException(
				"scenario graph validation failed: " + details
			);
		}
		if (extractChanceNodes(graph).isEmpty()) {
			throw new IllegalArgumentException(
				"scenario graph must contain at least one ChanceNode to optimize"
			);
		}
	}

	private List<OptimizationParameters> seededCandidates(
		Map<String, Object> graph,
		Map<String, Double> targetDistribution
	) {
		List<OptimizationParameters> candidates = new ArrayList<>();
		OptimizationParameters baseline = currentParameters(graph);
		candidates.add(baseline);

		OptimizationParameters targetAligned = targetAlignedParameters(
			graph,
			targetDistribution
		);
		if (targetAligned != null && !targetAligned.equals(baseline)) {
			candidates.add(targetAligned);
		}
		return candidates;
	}

	private OptimizationParameters currentParameters(Map<String, Object> graph) {
		return new OptimizationParameters(extractChanceNodes(graph));
	}

	private OptimizationParameters targetAlignedParameters(
		Map<String, Object> graph,
		Map<String, Double> targetDistribution
	) {
		List<Map<String, Object>> rawNodes = objectMapper.convertValue(
			graph.getOrDefault("nodes", List.of()),
			new TypeReference<List<Map<String, Object>>>() {}
		);
		Map<String, String> terminalOutcomes = terminalOutcomesByNode(rawNodes);
		List<OptimizationChanceNodeWeights> chanceNodes = extractChanceNodes(graph);
		if (chanceNodes.size() != 1) {
			return null;
		}

		OptimizationChanceNodeWeights chanceNode = chanceNodes.getFirst();
		List<OptimizationChanceOptionWeight> options = new ArrayList<>();
		double assigned = 0d;
		for (OptimizationChanceOptionWeight option : chanceNode.options()) {
			String outcome = terminalOutcomes.get(option.to());
			if (outcome == null || !targetDistribution.containsKey(outcome)) {
				return null;
			}
			double weight = targetDistribution.get(outcome);
			options.add(new OptimizationChanceOptionWeight(option.to(), weight));
			assigned += weight;
		}

		if (assigned <= 0d) {
			return null;
		}

		double normalizer = assigned;
		return new OptimizationParameters(
			List.of(
				new OptimizationChanceNodeWeights(
					chanceNode.nodeId(),
					options
						.stream()
						.map(option -> new OptimizationChanceOptionWeight(option.to(), option.weight() / normalizer))
						.toList()
				)
			)
		);
	}

	private Map<String, String> terminalOutcomesByNode(List<Map<String, Object>> rawNodes) {
		Map<String, String> outcomes = new LinkedHashMap<>();
		for (Map<String, Object> rawNode : rawNodes) {
			String type = String.valueOf(rawNode.getOrDefault("type", ""));
			if (!"TerminalNode".equals(type)) {
				continue;
			}
			String nodeId = String.valueOf(rawNode.getOrDefault("id", ""));
			String outcome = String.valueOf(rawNode.getOrDefault("outcome", ""));
			if (!nodeId.isBlank() && !outcome.isBlank()) {
				outcomes.put(nodeId, outcome);
			}
		}
		return outcomes;
	}

	private OptimizationParameters randomParameters(
		Map<String, Object> graph,
		Random random
	) {
		List<OptimizationChanceNodeWeights> weights = extractChanceNodes(graph)
			.stream()
			.map(node -> randomizeNodeWeights(node, random))
			.toList();
		return new OptimizationParameters(weights);
	}

	private OptimizationChanceNodeWeights randomizeNodeWeights(
		OptimizationChanceNodeWeights node,
		Random random
	) {
		List<Double> rawWeights = new ArrayList<>();
		double total = 0d;
		for (int index = 0; index < node.options().size(); index++) {
			double value = 0.05d + random.nextDouble();
			rawWeights.add(value);
			total += value;
		}

		List<OptimizationChanceOptionWeight> options = new ArrayList<>();
		for (int index = 0; index < node.options().size(); index++) {
			OptimizationChanceOptionWeight option = node.options().get(index);
			options.add(
				new OptimizationChanceOptionWeight(
					option.to(),
					rawWeights.get(index) / total
				)
			);
		}
		return new OptimizationChanceNodeWeights(node.nodeId(), options);
	}

	private List<OptimizationChanceNodeWeights> extractChanceNodes(Map<String, Object> graph) {
		List<Map<String, Object>> rawNodes = objectMapper.convertValue(
			graph.getOrDefault("nodes", List.of()),
			new TypeReference<List<Map<String, Object>>>() {}
		);
		List<OptimizationChanceNodeWeights> chanceNodes = new ArrayList<>();

		for (Map<String, Object> rawNode : rawNodes) {
			String type = String.valueOf(rawNode.getOrDefault("type", ""));
			if (!"ChanceNode".equals(type)) {
				continue;
			}
			String nodeId = String.valueOf(rawNode.getOrDefault("id", ""));
			List<Map<String, Object>> rawOptions = objectMapper.convertValue(
				rawNode.getOrDefault("chance_options", List.of()),
				new TypeReference<List<Map<String, Object>>>() {}
			);
			List<OptimizationChanceOptionWeight> options = new ArrayList<>();
			for (Map<String, Object> rawOption : rawOptions) {
				String to = String.valueOf(rawOption.getOrDefault("to", ""));
				Object rawWeight = rawOption.get("weight");
				double weight = rawWeight instanceof Number number ? number.doubleValue() : 0d;
				options.add(new OptimizationChanceOptionWeight(to, weight));
			}
			if (!nodeId.isBlank() && !options.isEmpty()) {
				chanceNodes.add(new OptimizationChanceNodeWeights(nodeId, options));
			}
		}

		return chanceNodes;
	}

	private Map<String, Object> applyParameters(
		Map<String, Object> graph,
		OptimizationParameters parameters
	) {
		Map<String, Object> candidateGraph = deepCopyGraph(graph);
		Map<String, OptimizationChanceNodeWeights> byNode = new LinkedHashMap<>();
		for (OptimizationChanceNodeWeights chanceNode : parameters.chanceWeights()) {
			byNode.put(chanceNode.nodeId(), chanceNode);
		}

		List<Map<String, Object>> nodes = objectMapper.convertValue(
			candidateGraph.getOrDefault("nodes", List.of()),
			new TypeReference<List<Map<String, Object>>>() {}
		);
		for (Map<String, Object> node : nodes) {
			String nodeId = String.valueOf(node.getOrDefault("id", ""));
			OptimizationChanceNodeWeights update = byNode.get(nodeId);
			if (update == null) {
				continue;
			}

			Map<String, Double> byDestination = new LinkedHashMap<>();
			for (OptimizationChanceOptionWeight option : update.options()) {
				byDestination.put(option.to(), option.weight());
			}

			List<Map<String, Object>> options = objectMapper.convertValue(
				node.getOrDefault("chance_options", List.of()),
				new TypeReference<List<Map<String, Object>>>() {}
			);
			for (Map<String, Object> option : options) {
				String to = String.valueOf(option.getOrDefault("to", ""));
				if (byDestination.containsKey(to)) {
					option.put("weight", byDestination.get(to));
				}
			}
			node.put("chance_options", options);
		}

		candidateGraph.put("nodes", nodes);
		return candidateGraph;
	}

	private Map<String, Double> outcomeRates(
		Map<String, Integer> outcomeCounts,
		int runCount
	) {
		Map<String, Double> distribution = new LinkedHashMap<>();
		if (runCount <= 0) {
			return distribution;
		}
		outcomeCounts.forEach(
			(outcome, count) -> distribution.put(outcome, (double) count / (double) runCount)
		);
		return distribution;
	}

	private double score(
		Map<String, Double> actualDistribution,
		Map<String, Double> targetDistribution
	) {
		Set<String> keys = new LinkedHashSet<>();
		keys.addAll(targetDistribution.keySet());
		keys.addAll(actualDistribution.keySet());

		double score = 0d;
		for (String key : keys) {
			score += Math.abs(
				actualDistribution.getOrDefault(key, 0d) - targetDistribution.getOrDefault(key, 0d)
			);
		}
		return score;
	}

	private Map<String, Object> deepCopyGraph(Map<String, Object> graph) {
		Map<String, Object> copy = objectMapper.convertValue(
			graph,
			new TypeReference<Map<String, Object>>() {}
		);
		return copy == null ? Map.of() : copy;
	}

	private record OptimizationCommand(
		UUID scenarioId,
		Map<String, Double> targetDistribution,
		int maxIterations,
		int runsPerIteration,
		String strategy
	) {}
}
