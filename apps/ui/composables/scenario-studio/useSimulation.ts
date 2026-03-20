import { computed } from "vue";
import type { ComputedRef } from "vue";
import type {
  ScenarioStudioState,
  SubmittedSimulationJob,
} from "@/composables/scenario-studio/types";
import { useScenarioStudioState } from "@/composables/scenario-studio/shared-state";
import { useScenarioStudioApiBaseUrl } from "@/composables/scenario-studio/utils";

const MAX_SIMULATION_RUN_COUNT = 5000;

type SimulationDependencies = {
  apiBaseUrl?: Readonly<ComputedRef<string>>;
  fetchImpl?: typeof fetch;
  pushActivity?: (title: string, detail: string, tone?: any) => void;
  graphValidationIssues?: ComputedRef<string[]>;
  fetchScenarioAnalytics?: (scenarioId?: string, options?: { force?: boolean }) => Promise<unknown>;
};

export function useSimulation(
  state: ScenarioStudioState = useScenarioStudioState(),
  dependencies: SimulationDependencies = {},
) {
  const apiBaseUrl = dependencies.apiBaseUrl ?? useScenarioStudioApiBaseUrl();
  const fetchImpl = dependencies.fetchImpl ?? fetch;
  const pushActivity = dependencies.pushActivity ?? (() => {});
  const graphValidationIssues = dependencies.graphValidationIssues ?? computed(() => []);
  const fetchScenarioAnalytics = dependencies.fetchScenarioAnalytics ?? (async () => null);

  const canSimulate = computed(
    () =>
      !state.isSimulating.value &&
      state.scenarioId.value.trim().length > 0 &&
      graphValidationIssues.value.length === 0,
  );
  const simulationProgress = computed(() =>
    Math.max(
      0,
      Math.min(
        100,
        state.simulationJob.value?.progressPercent ??
          (state.simulationResponse.value ? 100 : 0),
      ),
    ),
  );

  function resetSimulationState() {
    state.simulationResponse.value = null;
    state.simulationJob.value = null;
    state.simulationTracePage.value = null;
    state.simulationTracePageError.value = "";
    state.isLoadingSimulationTracePage.value = false;
    state.activeSimulationRunId.value = "";
  }

  async function fetchSimulationTracePage(runId: string, page = 0, pageSize = 8) {
    const normalizedRunId = runId.trim();
    if (!normalizedRunId) {
      state.simulationTracePage.value = null;
      state.simulationTracePageError.value = "";
      return null;
    }

    state.isLoadingSimulationTracePage.value = true;
    state.simulationTracePageError.value = "";
    try {
      const response = await fetchImpl(
        `${apiBaseUrl.value}/runs/${normalizedRunId}/trace-runs?page=${Math.max(page, 0)}&pageSize=${Math.max(1, Math.min(pageSize, 50))}`,
      );
      const payload = await response.json();
      if (!response.ok) {
        throw new Error(String(payload?.error || "Simulation trace request failed."));
      }

      state.simulationTracePage.value = payload;
      return state.simulationTracePage.value;
    } catch (error) {
      state.simulationTracePage.value = null;
      state.simulationTracePageError.value =
        error instanceof Error ? error.message : "Simulation trace request failed.";
      return null;
    } finally {
      state.isLoadingSimulationTracePage.value = false;
    }
  }

  async function pollSimulationJob(runId: string) {
    const maxIdleAttempts = 120;
    let idleAttempts = 0;
    let lastCompletedRuns = -1;

    for (;;) {
      const response = await fetchImpl(`${apiBaseUrl.value}/runs/${runId}`);
      const payload = await response.json();
      if (!response.ok) {
        throw new Error(String(payload?.error || "Simulation polling failed."));
      }

      const job = payload;
      state.simulationJob.value = job;

      if (job.completedRuns > lastCompletedRuns) {
        lastCompletedRuns = job.completedRuns;
        idleAttempts = 0;
      } else {
        idleAttempts += 1;
      }

      if (job.summary) {
        state.simulationResponse.value = job.summary;
      }

      if (job.status === "completed" && job.summary) {
        state.simulationResponse.value = job.summary;
        await fetchSimulationTracePage(runId, 0);
        await fetchScenarioAnalytics(job.summary.scenarioId, { force: true });
        state.statusNote.value = `Completed ${job.summary.runCount} simulation runs`;
        pushActivity(
          "Simulation Completed",
          `${job.summary.runCount} runs executed for ${job.summary.scenarioId}.`,
          "secondary",
        );
        return;
      }

      if (job.status === "failed") {
        const message = job.error || "Simulation job failed.";
        state.requestError.value = message;
        state.statusNote.value = job.deadLettered
          ? "Simulation failed and was dead-lettered"
          : "Simulation failed";
        pushActivity("Simulation Failed", message, "destructive");
        return;
      }

      state.statusNote.value =
        job.status === "running"
          ? `Simulation in progress: ${job.completedRuns}/${job.totalRuns} runs`
          : "Waiting for simulation worker...";

      if (idleAttempts >= maxIdleAttempts) {
        break;
      }

      await new Promise((resolve) => window.setTimeout(resolve, 1000));
    }

    throw new Error("Simulation polling timed out.");
  }

  async function runSimulation() {
    const normalizedScenarioId = state.scenarioId.value.trim();
    if (!normalizedScenarioId) {
      return;
    }
    if (graphValidationIssues.value.length > 0) {
      state.statusNote.value = "Fix graph issues before running simulations";
      return;
    }

    const normalizedRunCount = Math.max(1, Math.trunc(state.simulationRunCount.value));
    if (normalizedRunCount > MAX_SIMULATION_RUN_COUNT) {
      const message = `runCount must be between 1 and ${MAX_SIMULATION_RUN_COUNT}`;
      state.requestError.value = message;
      state.statusNote.value = "Simulation request failed";
      throw new Error(message);
    }

    state.isSimulating.value = true;
    state.requestError.value = "";
    resetSimulationState();
    state.statusNote.value = "Queueing simulation batch...";

    try {
      const response = await fetchImpl(`${apiBaseUrl.value}/simulate`, {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({
          scenarioId: normalizedScenarioId,
          runCount: normalizedRunCount,
          trace: true,
        }),
      });

      const payload = await response.json();
      if (!response.ok) {
        throw new Error(String(payload?.error || "Simulation run failed."));
      }

      const submittedJob = payload as SubmittedSimulationJob;
      state.activeSimulationRunId.value = submittedJob.runId;
      state.simulationJob.value = {
        runId: submittedJob.runId,
        jobType: "simulation_batch",
        status: submittedJob.status,
        scenarioId: normalizedScenarioId,
        totalRuns: submittedJob.totalRuns,
        completedRuns: 0,
        progressPercent: 0,
        attempts: 0,
        deadLettered: false,
        createdAt: submittedJob.createdAt,
        summary: null,
      };
      state.statusNote.value = `Queued ${submittedJob.totalRuns} simulation runs`;
      pushActivity(
        "Simulation Queued",
        `${submittedJob.totalRuns} runs submitted as ${submittedJob.runId.slice(0, 8)}.`,
        "outline",
      );
      await pollSimulationJob(submittedJob.runId);
    } catch (error) {
      const message = error instanceof Error ? error.message : "Simulation request failed.";
      state.requestError.value = message;
      pushActivity("Simulation Failed", message, "destructive");
      throw error;
    } finally {
      state.isSimulating.value = false;
    }
  }

  return {
    simulationRunCount: state.simulationRunCount,
    simulationJob: state.simulationJob,
    simulationResponse: state.simulationResponse,
    simulationTracePage: state.simulationTracePage,
    isLoadingSimulationTracePage: state.isLoadingSimulationTracePage,
    simulationTracePageError: state.simulationTracePageError,
    activeSimulationRunId: state.activeSimulationRunId,
    canSimulate,
    simulationProgress,
    resetSimulationState,
    fetchSimulationTracePage,
    runSimulation,
  };
}
