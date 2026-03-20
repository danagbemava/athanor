import { computed, ref } from "vue";
import { describe, expect, it, vi } from "vitest";
import { useScenarioGraph } from "@/composables/scenario-studio/useScenarioGraph";
import { useValidation } from "@/composables/scenario-studio/useValidation";
import type { ScenarioStudioState } from "@/composables/scenario-studio/types";

function createState(): ScenarioStudioState {
  return {
    scenarioName: ref("Validation Scenario"),
    scenarioDescription: ref("Validation test"),
    scenarioId: ref("scenario-123"),
    portfolioQuery: ref(""),
    graphId: ref("graph-1"),
    graphVersion: ref("1"),
    entryNodeId: ref("start"),
    nodes: ref([
      {
        id: "start",
        type: "DecisionNode",
        outcome: "",
        decisionOptions: [{ to: "missing-target", guardVar: "", guardEquals: "" }],
        chanceOptions: [],
      },
    ]),
    edges: ref([]),
    isSaving: ref(false),
    isValidating: ref(false),
    isSimulating: ref(false),
    isOptimizing: ref(false),
    requestError: ref(""),
    statusNote: ref("Ready"),
    simulationRunCount: ref(25),
    optimizationTargetJson: ref('{"approved":0.6}'),
    optimizationMaxIterations: ref(25),
    optimizationRunsPerIteration: ref(500),
    scenarioResponse: ref(null),
    validationResponse: ref(null),
    simulationResponse: ref(null),
    simulationJob: ref(null),
    simulationTracePage: ref(null),
    isLoadingSimulationTracePage: ref(false),
    simulationTracePageError: ref(""),
    analyticsResponse: ref(null),
    optimizationJob: ref(null),
    analyticsWatchersInitialized: ref(false),
    analyticsRequestKey: ref(""),
    analyticsLoadedKey: ref(""),
    isLoadingAnalytics: ref(false),
    analyticsError: ref(""),
    activeSimulationRunId: ref(""),
    activeOptimizationJobId: ref(""),
    scenarioHistory: ref([]),
    validationHistory: ref([]),
    activityFeed: ref([]),
  };
}

describe("useValidation", () => {
  it("exposes graph validation issues independently of the old studio barrel", () => {
    const state = createState();
    const graph = useScenarioGraph(state);
    const validation = useValidation(state, graph, {
      apiBaseUrl: computed(() => "http://127.0.0.1:8080"),
    });

    expect(validation.graphValidationIssues.value).toContain(
      'Decision option 1 on node "start" points to an unknown node.',
    );
  });

  it("records successful validation responses with injected fetch", async () => {
    const state = createState();
    state.nodes.value = [
      {
        id: "start",
        type: "DecisionNode",
        outcome: "",
        decisionOptions: [{ to: "terminal", guardVar: "", guardEquals: "" }],
        chanceOptions: [],
      },
      {
        id: "terminal",
        type: "TerminalNode",
        outcome: "approved",
        decisionOptions: [],
        chanceOptions: [],
      },
    ];

    const graph = useScenarioGraph(state);
    const fetchImpl = vi.fn(async () => ({
      ok: true,
      json: async () => ({
        scenarioId: "scenario-123",
        versionId: "version-1",
        versionNumber: 1,
        valid: true,
        errors: [],
        warnings: [],
      }),
    })) as unknown as typeof fetch;

    const validation = useValidation(state, graph, {
      apiBaseUrl: computed(() => "http://127.0.0.1:8080"),
      fetchImpl,
      pushActivity: vi.fn(),
    });

    await validation.validateScenario();

    expect(fetchImpl).toHaveBeenCalledOnce();
    expect(state.validationResponse.value?.valid).toBe(true);
    expect(state.validationHistory.value).toHaveLength(1);
    expect(state.statusNote.value).toBe("Validation passed");
  });
});
