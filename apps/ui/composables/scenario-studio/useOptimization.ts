import { computed } from "vue";
import type { ComputedRef } from "vue";
import type {
  ScenarioSnapshot,
  ScenarioStudioState,
  SubmittedOptimizationJob,
} from "@/composables/scenario-studio/types";
import { useScenarioStudioState } from "@/composables/scenario-studio/shared-state";
import { useScenarioStudioApiBaseUrl } from "@/composables/scenario-studio/utils";

type OptimizationDependencies = {
  apiBaseUrl?: Readonly<ComputedRef<string>>;
  fetchImpl?: typeof fetch;
  pushActivity?: (title: string, detail: string, tone?: any) => void;
  graphValidationIssues?: ComputedRef<string[]>;
  upsertScenarioHistory?: (snapshot: ScenarioSnapshot) => void;
  resetSimulationState?: () => void;
  fetchScenarioAnalytics?: (scenarioId?: string, options?: { force?: boolean }) => Promise<unknown>;
};

export function useOptimization(
  state: ScenarioStudioState = useScenarioStudioState(),
  dependencies: OptimizationDependencies = {},
) {
  const apiBaseUrl = dependencies.apiBaseUrl ?? useScenarioStudioApiBaseUrl();
  const fetchImpl = dependencies.fetchImpl ?? fetch;
  const pushActivity = dependencies.pushActivity ?? (() => {});
  const graphValidationIssues = dependencies.graphValidationIssues ?? computed(() => []);
  const upsertScenarioHistory = dependencies.upsertScenarioHistory ?? (() => {});
  const resetSimulationState = dependencies.resetSimulationState ?? (() => {});
  const fetchScenarioAnalytics = dependencies.fetchScenarioAnalytics ?? (async () => null);

  const canOptimize = computed(
    () =>
      !state.isOptimizing.value &&
      !state.isSimulating.value &&
      state.scenarioId.value.trim().length > 0 &&
      graphValidationIssues.value.length === 0,
  );
  const canApplyOptimization = computed(
    () =>
      state.optimizationJob.value?.status === "completed" &&
      !state.optimizationJob.value?.appliedVersionId &&
      !!state.optimizationJob.value?.bestParameters,
  );
  const optimizationProgress = computed(() =>
    Math.max(
      0,
      Math.min(
        100,
        state.optimizationJob.value?.progressPercent ??
          (state.optimizationJob.value?.status === "completed" ? 100 : 0),
      ),
    ),
  );

  function resetOptimizationState() {
    state.optimizationJob.value = null;
    state.activeOptimizationJobId.value = "";
  }

  function parseOptimizationTargets() {
    let parsed: unknown;
    try {
      parsed = JSON.parse(state.optimizationTargetJson.value);
    } catch {
      throw new Error("Optimization target distribution must be valid JSON.");
    }

    if (!parsed || typeof parsed !== "object" || Array.isArray(parsed)) {
      throw new Error("Optimization target distribution must be a JSON object.");
    }

    const normalized: Record<string, number> = {};
    for (const [key, value] of Object.entries(parsed as Record<string, unknown>)) {
      const label = key.trim();
      const numericValue =
        typeof value === "number" ? value : Number.parseFloat(String(value));
      if (!label) {
        throw new Error("Optimization target keys must be non-empty.");
      }
      if (!Number.isFinite(numericValue) || numericValue < 0) {
        throw new Error(
          "Optimization target values must be finite numbers greater than or equal to zero.",
        );
      }
      normalized[label] = numericValue;
    }

    if (Object.keys(normalized).length === 0) {
      throw new Error("Optimization target distribution cannot be empty.");
    }

    return normalized;
  }

  async function pollOptimizationJob(jobId: string) {
    const maxAttempts = 180;
    for (let attempt = 0; attempt < maxAttempts; attempt += 1) {
      const response = await fetchImpl(`${apiBaseUrl.value}/optimize/${jobId}`);
      const payload = await response.json();
      if (!response.ok) {
        throw new Error(String(payload?.error || "Optimization polling failed."));
      }

      const job = payload;
      state.optimizationJob.value = job;

      if (job.status === "completed") {
        state.statusNote.value = job.converged
          ? `Optimization converged with score ${job.bestScore.toFixed(3)}`
          : `Optimization completed with best score ${job.bestScore.toFixed(3)}`;
        pushActivity(
          "Optimization Completed",
          `${job.iterationsCompleted} iterations completed for ${job.scenarioId}.`,
          "secondary",
        );
        return;
      }

      if (job.status === "failed") {
        const message = job.error || "Optimization job failed.";
        state.requestError.value = message;
        state.statusNote.value = "Optimization failed";
        pushActivity("Optimization Failed", message, "destructive");
        return;
      }

      state.statusNote.value = `Optimization in progress: ${job.iterationsCompleted}/${job.maxIterations} iterations`;
      await new Promise((resolve) => window.setTimeout(resolve, 1000));
    }

    throw new Error("Optimization polling timed out.");
  }

  async function runOptimization() {
    if (graphValidationIssues.value[0]) {
      state.statusNote.value = "Fix graph issues before running optimization";
      return;
    }

    state.isOptimizing.value = true;
    state.requestError.value = "";
    state.optimizationJob.value = null;
    state.activeOptimizationJobId.value = "";
    state.statusNote.value = "Queueing optimization job...";

    try {
      const targetDistribution = parseOptimizationTargets();
      const response = await fetchImpl(`${apiBaseUrl.value}/optimize`, {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({
          scenarioId: state.scenarioId.value,
          targetDistribution,
          maxIterations: Number.isFinite(state.optimizationMaxIterations.value)
            ? Math.max(1, Math.trunc(state.optimizationMaxIterations.value))
            : 25,
          runsPerIteration: Number.isFinite(state.optimizationRunsPerIteration.value)
            ? Math.max(10, Math.trunc(state.optimizationRunsPerIteration.value))
            : 500,
          strategy: "random_search",
        }),
      });

      const payload = await response.json();
      if (!response.ok) {
        throw new Error(String(payload?.error || "Optimization request failed."));
      }

      const submittedJob = payload as SubmittedOptimizationJob;
      state.activeOptimizationJobId.value = submittedJob.jobId;
      state.optimizationJob.value = {
        jobId: submittedJob.jobId,
        jobType: "optimizer_random_search",
        status: submittedJob.status,
        scenarioId: state.scenarioId.value,
        baseVersionId: state.scenarioResponse.value?.version.id ?? "",
        baseVersionNumber: state.scenarioResponse.value?.version.number ?? 1,
        strategy: submittedJob.strategy,
        targetDistribution,
        maxIterations: submittedJob.maxIterations,
        runsPerIteration: submittedJob.runsPerIteration,
        iterationsCompleted: 0,
        progressPercent: 0,
        bestScore: 0,
        converged: false,
        bestParameters: null,
        bestOutcomeDistribution: {},
        createdAt: submittedJob.createdAt,
      };
      state.statusNote.value = `Queued optimization job ${submittedJob.jobId.slice(0, 8)}`;
      pushActivity(
        "Optimization Queued",
        `${submittedJob.maxIterations} iterations submitted for ${state.scenarioId.value}.`,
        "outline",
      );
      await pollOptimizationJob(submittedJob.jobId);
    } catch (error) {
      const message = error instanceof Error ? error.message : "Optimization request failed.";
      state.requestError.value = message;
      pushActivity("Optimization Failed", message, "destructive");
      throw error;
    } finally {
      state.isOptimizing.value = false;
    }
  }

  async function applyOptimization() {
    const jobId = state.optimizationJob.value?.jobId;
    if (!jobId) {
      return;
    }

    state.requestError.value = "";
    state.statusNote.value = "Applying optimized parameters as a new version...";

    try {
      const response = await fetchImpl(`${apiBaseUrl.value}/optimize/${jobId}/apply`, {
        method: "POST",
      });
      const payload = await response.json();
      if (!response.ok) {
        throw new Error(String(payload?.error || "Apply optimization failed."));
      }

      state.scenarioResponse.value = payload as ScenarioSnapshot;
      upsertScenarioHistory(state.scenarioResponse.value);
      if (state.optimizationJob.value) {
        state.optimizationJob.value = {
          ...state.optimizationJob.value,
          appliedVersionId: state.scenarioResponse.value.version.id,
          appliedVersionNumber: state.scenarioResponse.value.version.number,
        };
      }
      resetSimulationState();
      state.analyticsLoadedKey.value = "";
      await fetchScenarioAnalytics(state.scenarioResponse.value.scenarioId, { force: true });
      state.statusNote.value = `Applied optimized parameters to version ${state.scenarioResponse.value.version.number}`;
      pushActivity(
        "Optimization Applied",
        `${state.scenarioResponse.value.scenarioId} moved to version ${state.scenarioResponse.value.version.number}.`,
        "default",
      );
    } catch (error) {
      const message = error instanceof Error ? error.message : "Apply optimization failed.";
      state.requestError.value = message;
      pushActivity("Optimization Apply Failed", message, "destructive");
    }
  }

  return {
    optimizationTargetJson: state.optimizationTargetJson,
    optimizationMaxIterations: state.optimizationMaxIterations,
    optimizationRunsPerIteration: state.optimizationRunsPerIteration,
    optimizationJob: state.optimizationJob,
    activeOptimizationJobId: state.activeOptimizationJobId,
    canOptimize,
    canApplyOptimization,
    optimizationProgress,
    runOptimization,
    applyOptimization,
    resetOptimizationState,
  };
}
