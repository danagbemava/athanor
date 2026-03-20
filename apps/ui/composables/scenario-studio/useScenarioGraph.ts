import { computed, watch } from "vue";
import type {
  DraftNode,
  NodeReference,
  NodeType,
  ScenarioStudioGraphApi,
  ScenarioStudioState,
} from "@/composables/scenario-studio/types";
import { useScenarioStudioState } from "@/composables/scenario-studio/shared-state";

function createDraftNode(type: NodeType, id: string): DraftNode {
  return {
    id,
    type,
    outcome: type === "TerminalNode" ? "completed" : "",
    decisionOptions:
      type === "DecisionNode"
        ? [{ to: "", guardVar: "", guardEquals: "" }]
        : [],
    chanceOptions: type === "ChanceNode" ? [{ to: "", weight: "1" }] : [],
  };
}

export function useScenarioGraph(
  state: ScenarioStudioState = useScenarioStudioState(),
): ScenarioStudioGraphApi {
  const nodeReferences = computed<NodeReference[]>(() => {
    const seen = new Set<string>();
    return state.nodes.value
      .map((node, index) => ({
        id: node.id.trim(),
        label: node.id.trim() || `Node ${index + 1}`,
      }))
      .filter((item) => {
        if (!item.id || seen.has(item.id)) {
          return false;
        }
        seen.add(item.id);
        return true;
      });
  });

  watch(
    nodeReferences,
    (refs) => {
      if (!refs.some((refNode) => refNode.id === state.entryNodeId.value)) {
        state.entryNodeId.value = refs[0]?.id ?? "";
      }
    },
    { immediate: true },
  );

  const graphPayload = computed<Record<string, unknown>>(() => {
    const parsedVersion = Number.parseInt(state.graphVersion.value, 10);

    const compiledNodes = state.nodes.value.map((node) => {
      const compiled: Record<string, unknown> = {
        id: node.id.trim(),
        type: node.type,
      };

      if (node.type === "DecisionNode") {
        const decisionOptions = node.decisionOptions
          .filter((option) => option.to.trim().length > 0)
          .map((option) => {
            const normalized: Record<string, unknown> = {
              to: option.to.trim(),
            };

            if (option.guardVar.trim().length > 0) {
              normalized.guard = {
                var: option.guardVar.trim(),
                equals: option.guardEquals,
              };
            }

            return normalized;
          });

        if (decisionOptions.length > 0) {
          compiled.decision_options = decisionOptions;
        }
      }

      if (node.type === "ChanceNode") {
        const chanceOptions = node.chanceOptions
          .filter((option) => option.to.trim().length > 0)
          .map((option) => ({
            to: option.to.trim(),
            weight: Number.parseFloat(option.weight),
          }));

        if (chanceOptions.length > 0) {
          compiled.chance_options = chanceOptions;
        }
      }

      if (node.type === "TerminalNode" && node.outcome.trim().length > 0) {
        compiled.outcome = node.outcome.trim();
      }

      return compiled;
    });

    const compiledEdges = state.edges.value
      .filter(
        (edge) => edge.from.trim().length > 0 && edge.to.trim().length > 0,
      )
      .map((edge) => ({
        from: edge.from.trim(),
        to: edge.to.trim(),
      }));

    return {
      id: state.graphId.value.trim(),
      name: state.scenarioName.value.trim() || "Untitled Scenario",
      version:
        Number.isInteger(parsedVersion) && parsedVersion > 0
          ? parsedVersion
          : 1,
      entry_node_id: state.entryNodeId.value.trim(),
      nodes: compiledNodes,
      edges: compiledEdges,
    };
  });

  function nextNodeId(): string {
    const existing = new Set(state.nodes.value.map((node) => node.id.trim()));
    let cursor = state.nodes.value.length + 1;
    while (existing.has(`node-${cursor}`)) {
      cursor += 1;
    }
    return `node-${cursor}`;
  }

  function addNode(type: NodeType = "DecisionNode") {
    const node = createDraftNode(type, nextNodeId());
    state.nodes.value.push(node);
    if (!state.entryNodeId.value.trim()) {
      state.entryNodeId.value = node.id;
    }
  }

  function removeNode(nodeIndex: number) {
    if (state.nodes.value.length <= 1) {
      return;
    }

    const removedNodeId = state.nodes.value[nodeIndex]?.id.trim();
    state.nodes.value.splice(nodeIndex, 1);
    if (!removedNodeId) {
      return;
    }

    state.edges.value = state.edges.value.filter(
      (edge) => edge.from !== removedNodeId && edge.to !== removedNodeId,
    );
    state.nodes.value.forEach((node) => {
      node.decisionOptions = node.decisionOptions.filter(
        (option) => option.to !== removedNodeId,
      );
      node.chanceOptions = node.chanceOptions.filter(
        (option) => option.to !== removedNodeId,
      );
    });

    if (state.entryNodeId.value === removedNodeId) {
      state.entryNodeId.value = nodeReferences.value[0]?.id ?? "";
    }
  }

  function normalizeNodeForType(node: DraftNode) {
    if (node.type === "DecisionNode") {
      if (node.decisionOptions.length === 0) {
        node.decisionOptions.push({ to: "", guardVar: "", guardEquals: "" });
      }
      node.chanceOptions = [];
      node.outcome = "";
      return;
    }

    if (node.type === "ChanceNode") {
      if (node.chanceOptions.length === 0) {
        node.chanceOptions.push({ to: "", weight: "1" });
      }
      node.decisionOptions = [];
      node.outcome = "";
      return;
    }

    node.decisionOptions = [];
    node.chanceOptions = [];
  }

  function addDecisionOption(node: DraftNode) {
    node.decisionOptions.push({ to: "", guardVar: "", guardEquals: "" });
  }

  function removeDecisionOption(node: DraftNode, optionIndex: number) {
    node.decisionOptions.splice(optionIndex, 1);
  }

  function addChanceOption(node: DraftNode) {
    node.chanceOptions.push({ to: "", weight: "1" });
  }

  function removeChanceOption(node: DraftNode, optionIndex: number) {
    node.chanceOptions.splice(optionIndex, 1);
  }

  function addEdge() {
    const first = nodeReferences.value[0]?.id ?? "";
    const second = nodeReferences.value[1]?.id ?? first;
    state.edges.value.push({ from: first, to: second });
  }

  function removeEdge(edgeIndex: number) {
    state.edges.value.splice(edgeIndex, 1);
  }

  function loadExampleGraph() {
    state.graphId.value = "scenario-graph-2";
    state.graphVersion.value = "1";
    state.entryNodeId.value = "decision";
    state.nodes.value = [
      {
        id: "decision",
        type: "DecisionNode",
        outcome: "",
        decisionOptions: [
          {
            to: "chance",
            guardVar: "customer_segment",
            guardEquals: "premium",
          },
        ],
        chanceOptions: [],
      },
      {
        id: "chance",
        type: "ChanceNode",
        outcome: "",
        decisionOptions: [],
        chanceOptions: [
          { to: "approved", weight: "0.65" },
          { to: "declined", weight: "0.35" },
        ],
      },
      {
        id: "approved",
        type: "TerminalNode",
        outcome: "approved",
        decisionOptions: [],
        chanceOptions: [],
      },
      {
        id: "declined",
        type: "TerminalNode",
        outcome: "declined",
        decisionOptions: [],
        chanceOptions: [],
      },
    ];
    state.edges.value = [
      { from: "decision", to: "chance" },
      { from: "chance", to: "approved" },
      { from: "chance", to: "declined" },
    ];
  }

  return {
    graphId: state.graphId,
    graphVersion: state.graphVersion,
    entryNodeId: state.entryNodeId,
    nodes: state.nodes,
    edges: state.edges,
    nodeReferences,
    graphPayload,
    addNode,
    removeNode,
    normalizeNodeForType,
    addDecisionOption,
    removeDecisionOption,
    addChanceOption,
    removeChanceOption,
    addEdge,
    removeEdge,
    loadExampleGraph,
  };
}
