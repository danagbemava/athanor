import { computed } from "vue";
import type { ComputedRef, Ref } from "vue";
import type {
  ScenarioStudioState,
  ValidationRun,
  ValidationSnapshot,
} from "@/composables/scenario-studio/types";
import { useScenarioStudioState } from "@/composables/scenario-studio/shared-state";
import { useScenarioStudioApiBaseUrl } from "@/composables/scenario-studio/utils";
import { useScenarioGraph } from "@/composables/scenario-studio/useScenarioGraph";

type ValidationDependencies = {
  apiBaseUrl?: Readonly<Ref<string> | ComputedRef<string>>;
  fetchImpl?: typeof fetch;
  pushActivity?: (title: string, detail: string, tone?: any) => void;
};

export function useValidation(
  state: ScenarioStudioState = useScenarioStudioState(),
  graph = useScenarioGraph(state),
  dependencies: ValidationDependencies = {},
) {
  const apiBaseUrl = dependencies.apiBaseUrl ?? useScenarioStudioApiBaseUrl();
  const fetchImpl = dependencies.fetchImpl ?? fetch;
  const pushActivity = dependencies.pushActivity ?? (() => {});

  const graphValidationIssues = computed(() => {
    const issues: string[] = [];
    const addIssue = (message: string) => {
      if (!issues.includes(message)) {
        issues.push(message);
      }
    };

    if (!state.graphId.value.trim()) {
      addIssue("Graph ID is required.");
    }

    const parsedVersion = Number.parseInt(state.graphVersion.value, 10);
    if (!Number.isInteger(parsedVersion) || parsedVersion < 1) {
      addIssue("Graph version must be a positive integer.");
    }

    if (state.nodes.value.length === 0) {
      addIssue("At least one node is required.");
      return issues;
    }

    const nodeIds = state.nodes.value.map((node) => node.id.trim());
    const nonEmptyNodeIds = nodeIds.filter((id) => id.length > 0);
    if (nonEmptyNodeIds.length !== nodeIds.length) {
      addIssue("Every node must have an ID.");
    }
    if (new Set(nonEmptyNodeIds).size !== nonEmptyNodeIds.length) {
      addIssue("Node IDs must be unique.");
    }

    const idSet = new Set(nonEmptyNodeIds);
    if (!state.entryNodeId.value.trim()) {
      addIssue("Entry node is required.");
    } else if (!idSet.has(state.entryNodeId.value.trim())) {
      addIssue("Entry node must reference an existing node ID.");
    }

    state.edges.value.forEach((edge, edgeIndex) => {
      const from = edge.from.trim();
      const to = edge.to.trim();
      if (!from || !to) {
        addIssue(`Edge ${edgeIndex + 1} must include both source and destination.`);
        return;
      }
      if (!idSet.has(from)) {
        addIssue(`Edge ${edgeIndex + 1} references unknown source node "${from}".`);
      }
      if (!idSet.has(to)) {
        addIssue(`Edge ${edgeIndex + 1} references unknown destination node "${to}".`);
      }
    });

    state.nodes.value.forEach((node, nodeIndex) => {
      const label = node.id || String(nodeIndex + 1);
      if (node.type === "DecisionNode") {
        const options = node.decisionOptions.filter((option) => option.to.trim().length > 0);
        if (options.length === 0) {
          addIssue(`Decision node "${label}" must include at least one decision option.`);
        }
        options.forEach((option, optionIndex) => {
          if (!idSet.has(option.to.trim())) {
            addIssue(`Decision option ${optionIndex + 1} on node "${label}" points to an unknown node.`);
          }
        });
      }

      if (node.type === "ChanceNode") {
        const options = node.chanceOptions.filter((option) => option.to.trim().length > 0);
        if (options.length === 0) {
          addIssue(`Chance node "${label}" must include at least one chance option.`);
        }

        let totalWeight = 0;
        options.forEach((option, optionIndex) => {
          const parsedWeight = Number.parseFloat(option.weight);
          if (!Number.isFinite(parsedWeight) || parsedWeight <= 0) {
            addIssue(`Chance option ${optionIndex + 1} on node "${label}" must use a positive weight.`);
          } else {
            totalWeight += parsedWeight;
          }
          if (!idSet.has(option.to.trim())) {
            addIssue(`Chance option ${optionIndex + 1} on node "${label}" points to an unknown node.`);
          }
        });

        if (options.length > 0 && Math.abs(totalWeight - 1) > 0.0001) {
          addIssue(`Chance node "${label}" weights should add up to 1.0.`);
        }
      }
    });

    return issues;
  });

  const validationStatsByScenario = computed(() => {
    const stats = new Map<
      string,
      { runs: number; pass: number; errors: number; warnings: number }
    >();

    state.validationHistory.value.forEach((run) => {
      const current = stats.get(run.snapshot.scenarioId) ?? {
        runs: 0,
        pass: 0,
        errors: 0,
        warnings: 0,
      };

      current.runs += 1;
      if (run.snapshot.valid) {
        current.pass += 1;
      }
      current.errors = run.snapshot.errors.length;
      current.warnings = run.snapshot.warnings.length;
      stats.set(run.snapshot.scenarioId, current);
    });

    return stats;
  });

  const validationRunCount = computed(() => state.validationHistory.value.length);
  const latestValidation = computed(
    () => state.validationHistory.value[0]?.snapshot ?? state.validationResponse.value,
  );
  const openIssues = computed(() => {
    if (state.validationHistory.value.length === 0) {
      return 0;
    }
    const latest = state.validationHistory.value[0].snapshot;
    return latest.errors.length + latest.warnings.length;
  });
  const overallPassRate = computed(() => {
    if (state.validationHistory.value.length === 0) {
      return 0;
    }
    const passCount = state.validationHistory.value.filter((run) => run.snapshot.valid).length;
    return Math.round((passCount / state.validationHistory.value.length) * 100);
  });
  const validationTrend = computed(() => {
    if (state.validationHistory.value.length === 0) {
      return [72, 75, 79, 77, 82, 86, 90];
    }

    const points = state.validationHistory.value
      .slice(0, 7)
      .reverse()
      .map((run) => {
        if (run.snapshot.valid) {
          return Math.max(65, 100 - run.snapshot.warnings.length * 4);
        }
        return Math.max(
          25,
          58 - run.snapshot.errors.length * 10 - run.snapshot.warnings.length * 3,
        );
      });

    while (points.length < 7) {
      points.unshift(points[0] ?? 70);
    }
    return points;
  });
  const canValidate = computed(
    () => !state.isValidating.value && state.scenarioId.value.trim().length > 0,
  );

  function recordValidation(snapshot: ValidationSnapshot) {
    state.validationHistory.value.unshift({
      snapshot,
      recordedAt: new Date().toISOString(),
    });

    if (state.validationHistory.value.length > 20) {
      state.validationHistory.value.length = 20;
    }
  }

  async function validateScenario() {
    const normalizedScenarioId = state.scenarioId.value.trim();
    if (!normalizedScenarioId) {
      return;
    }

    state.isValidating.value = true;
    state.requestError.value = "";
    state.statusNote.value = "Running validation against latest version...";

    try {
      const response = await fetchImpl(`${apiBaseUrl.value}/scenarios/${normalizedScenarioId}/validate`, {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify(graph.graphPayload.value),
      });
      const payload = await response.json();
      if (!response.ok) {
        throw new Error(String(payload?.error || "Validation request failed."));
      }

      state.validationResponse.value = payload as ValidationSnapshot;
      recordValidation(state.validationResponse.value);
      state.statusNote.value = state.validationResponse.value.valid
        ? "Validation passed"
        : "Validation returned issues";
      pushActivity(
        "Validation Complete",
        `${state.validationResponse.value.valid ? "Passed" : "Issues found"} for ${normalizedScenarioId}.`,
        state.validationResponse.value.valid ? "secondary" : "destructive",
      );
    } catch (error) {
      const message = error instanceof Error ? error.message : "Validation request failed.";
      state.requestError.value = message;
      state.statusNote.value = "Validation failed";
      pushActivity("Validation Failed", message, "destructive");
      throw error;
    } finally {
      state.isValidating.value = false;
    }
  }

  return {
    validationResponse: state.validationResponse,
    validationHistory: state.validationHistory,
    graphValidationIssues,
    validationStatsByScenario,
    validationRunCount,
    latestValidation,
    openIssues,
    overallPassRate,
    validationTrend,
    canValidate,
    validateScenario,
    recordValidation,
  };
}
