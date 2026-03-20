import { useState } from "#imports";
import type { ScenarioStudioState } from "@/composables/scenario-studio/types";

export function useScenarioStudioState(): ScenarioStudioState {
  return {
    scenarioName: useState<string>(
      "studio:scenario-name",
      () => "First Draft Scenario",
    ),
    scenarioDescription: useState<string>(
      "studio:scenario-description",
      () => "Interactive authoring draft from Scenario Studio.",
    ),
    scenarioId: useState<string>("studio:scenario-id", () => ""),
    portfolioQuery: useState<string>("studio:portfolio-query", () => ""),
    graphId: useState<string>("studio:graph-id", () => "scenario-graph-1"),
    graphVersion: useState<string>("studio:graph-version", () => "1"),
    entryNodeId: useState<string>("studio:entry-node-id", () => "start"),
    nodes: useState("studio:nodes", () => [
      {
        id: "start",
        type: "DecisionNode" as const,
        outcome: "",
        decisionOptions: [{ to: "terminal", guardVar: "", guardEquals: "" }],
        chanceOptions: [],
      },
      {
        id: "terminal",
        type: "TerminalNode" as const,
        outcome: "approved",
        decisionOptions: [],
        chanceOptions: [],
      },
    ]),
    edges: useState("studio:edges", () => [{ from: "start", to: "terminal" }]),
    isSaving: useState<boolean>("studio:is-saving", () => false),
    isValidating: useState<boolean>("studio:is-validating", () => false),
    isSimulating: useState<boolean>("studio:is-simulating", () => false),
    isOptimizing: useState<boolean>("studio:is-optimizing", () => false),
    requestError: useState<string>("studio:request-error", () => ""),
    statusNote: useState<string>("studio:status-note", () => "Ready"),
    simulationRunCount: useState<number>("studio:simulation-run-count", () => 25),
    optimizationTargetJson: useState<string>(
      "studio:optimization-target-json",
      () => JSON.stringify({ approved: 0.6, declined: 0.4 }, null, 2),
    ),
    optimizationMaxIterations: useState<number>(
      "studio:optimization-max-iterations",
      () => 25,
    ),
    optimizationRunsPerIteration: useState<number>(
      "studio:optimization-runs-per-iteration",
      () => 500,
    ),
    scenarioResponse: useState("studio:scenario-response", () => null),
    validationResponse: useState("studio:validation-response", () => null),
    simulationResponse: useState("studio:simulation-response", () => null),
    simulationJob: useState("studio:simulation-job", () => null),
    simulationTracePage: useState("studio:simulation-trace-page", () => null),
    isLoadingSimulationTracePage: useState<boolean>(
      "studio:is-loading-simulation-trace-page",
      () => false,
    ),
    simulationTracePageError: useState<string>(
      "studio:simulation-trace-page-error",
      () => "",
    ),
    analyticsResponse: useState("studio:analytics-response", () => null),
    optimizationJob: useState("studio:optimization-job", () => null),
    analyticsWatchersInitialized: useState<boolean>(
      "studio:analytics-watchers-initialized",
      () => false,
    ),
    analyticsRequestKey: useState<string>("studio:analytics-request-key", () => ""),
    analyticsLoadedKey: useState<string>("studio:analytics-loaded-key", () => ""),
    isLoadingAnalytics: useState<boolean>("studio:is-loading-analytics", () => false),
    analyticsError: useState<string>("studio:analytics-error", () => ""),
    activeSimulationRunId: useState<string>(
      "studio:active-simulation-run-id",
      () => "",
    ),
    activeOptimizationJobId: useState<string>(
      "studio:active-optimization-job-id",
      () => "",
    ),
    scenarioHistory: useState("studio:scenario-history", () => []),
    validationHistory: useState("studio:validation-history", () => []),
    activityFeed: useState("studio:activity-feed", () => []),
  };
}
