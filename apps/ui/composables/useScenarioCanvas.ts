import type {
  DraftEdge,
  DraftNode,
  NodeType,
} from "@/composables/scenario-studio/types";
import { useScenarioStudioState } from "@/composables/scenario-studio/shared-state";
import { useScenarioGraph } from "@/composables/scenario-studio/useScenarioGraph";

export type CanvasNodeMeta = {
  x: number;
  y: number;
  title: string;
  description: string;
  score: string;
};

export type CanvasViewport = {
  x: number;
  y: number;
  zoom: number;
};

export type CanvasPoint = {
  x: number;
  y: number;
};

type CanvasSnapshot = {
  graphId: string;
  graphVersion: string;
  entryNodeId: string;
  nodes: DraftNode[];
  edges: DraftEdge[];
  nodeMeta: Record<string, CanvasNodeMeta>;
};

let historyWatchersInitialized = false;
let historyTimer: ReturnType<typeof setTimeout> | null = null;

const NODE_WIDTH = 224;
const NODE_HEIGHT = 124;
const MIN_ZOOM = 0.4;
const MAX_ZOOM = 1.8;

function cloneValue<T>(value: T): T {
  return JSON.parse(JSON.stringify(value)) as T;
}

function metaFor(node: DraftNode, index: number): CanvasNodeMeta {
  return {
    x: 96 + (index % 3) * 260,
    y: 96 + Math.floor(index / 3) * 190,
    title: node.id || `${node.type.replace("Node", "")} ${index + 1}`,
    description: "",
    score: "",
  };
}

function snapshotKey(snapshot: CanvasSnapshot): string {
  return JSON.stringify(snapshot);
}

export function useScenarioCanvas() {
  const state = useScenarioStudioState();
  const graph = useScenarioGraph(state);
  const studio = {
    scenarioName: state.scenarioName,
    scenarioDescription: state.scenarioDescription,
    graphId: graph.graphId,
    graphVersion: graph.graphVersion,
    entryNodeId: graph.entryNodeId,
    nodes: graph.nodes,
    edges: graph.edges,
    nodeReferences: graph.nodeReferences,
    graphPayload: graph.graphPayload,
    addNode: graph.addNode,
    removeNode: graph.removeNode,
    normalizeNodeForType: graph.normalizeNodeForType,
    addDecisionOption: graph.addDecisionOption,
    removeDecisionOption: graph.removeDecisionOption,
    addChanceOption: graph.addChanceOption,
    removeChanceOption: graph.removeChanceOption,
    loadExampleGraph: graph.loadExampleGraph,
  };

  const nodeMeta = useState<Record<string, CanvasNodeMeta>>(
    "studio:canvas-node-meta",
    () => ({}),
  );
  const selectedNodeId = useState<string>("studio:canvas-selected-node-id", () =>
    studio.nodes.value[0]?.id ?? "",
  );
  const selectedEdgeId = useState<string>("studio:canvas-selected-edge-id", () => "");
  const history = useState<CanvasSnapshot[]>("studio:canvas-history", () => []);
  const historyIndex = useState<number>("studio:canvas-history-index", () => -1);
  const hasUnsavedChanges = useState<boolean>(
    "studio:canvas-has-unsaved-changes",
    () => false,
  );
  const viewport = useState<CanvasViewport>("studio:canvas-viewport", () => ({
    x: 0,
    y: 0,
    zoom: 1,
  }));
  const isFullscreen = useState<boolean>("studio:canvas-is-fullscreen", () => false);
  const autosaveState = useState<"idle" | "saving" | "saved" | "error">(
    "studio:canvas-autosave-state",
    () => "idle",
  );
  const lastAutosavedAt = useState<string>("studio:canvas-last-autosaved-at", () => "");
  const isApplyingSnapshot = useState<boolean>(
    "studio:canvas-is-applying-snapshot",
    () => false,
  );
  const isDraggingNode = useState<boolean>("studio:canvas-is-dragging-node", () => false);

  const canvasEdges = computed(() =>
    studio.edges.value.map((edge, index) => ({
      ...edge,
      id: `${edge.from}::${edge.to}::${index}`,
      index,
    })),
  );

  const selectedNode = computed(
    () =>
      studio.nodes.value.find((node) => node.id === selectedNodeId.value) ?? null,
  );
  const selectedNodeMeta = computed(() =>
    selectedNode.value ? nodeMeta.value[selectedNode.value.id] ?? null : null,
  );
  const selectedEdge = computed(
    () =>
      canvasEdges.value.find((edge) => edge.id === selectedEdgeId.value) ?? null,
  );
  const canUndo = computed(() => historyIndex.value > 0);
  const canRedo = computed(
    () =>
      historyIndex.value >= 0 &&
      historyIndex.value < history.value.length - 1,
  );

  function clampZoom(zoom: number) {
    return Math.min(MAX_ZOOM, Math.max(MIN_ZOOM, zoom));
  }

  function graphPointFromCanvasPoint(point: CanvasPoint) {
    return {
      x: (point.x - viewport.value.x) / viewport.value.zoom,
      y: (point.y - viewport.value.y) / viewport.value.zoom,
    };
  }

  function canvasPointFromGraphPoint(point: CanvasPoint) {
    return {
      x: point.x * viewport.value.zoom + viewport.value.x,
      y: point.y * viewport.value.zoom + viewport.value.y,
    };
  }

  function panViewport(deltaX: number, deltaY: number) {
    viewport.value = {
      ...viewport.value,
      x: viewport.value.x + deltaX,
      y: viewport.value.y + deltaY,
    };
  }

  function setViewportZoom(nextZoom: number, anchor?: CanvasPoint) {
    const zoom = clampZoom(nextZoom);
    if (zoom === viewport.value.zoom) {
      return;
    }

    if (!anchor) {
      viewport.value = {
        ...viewport.value,
        zoom,
      };
      return;
    }

    const graphAnchor = graphPointFromCanvasPoint(anchor);
    viewport.value = {
      x: anchor.x - graphAnchor.x * zoom,
      y: anchor.y - graphAnchor.y * zoom,
      zoom,
    };
  }

  function resetViewport() {
    viewport.value = {
      x: 0,
      y: 0,
      zoom: 1,
    };
  }

  function fitGraphToViewport(bounds?: {
    width: number;
    height: number;
    padding?: number;
  }) {
    if (!bounds || !bounds.width || !bounds.height) {
      return;
    }

    if (!studio.nodes.value.length) {
      resetViewport();
      return;
    }

    const padding = bounds.padding ?? 96;
    const coordinates = studio.nodes.value
      .map((node, index) => nodeMeta.value[node.id] ?? metaFor(node, index))
      .map((meta) => ({
        minX: meta.x,
        minY: meta.y,
        maxX: meta.x + NODE_WIDTH,
        maxY: meta.y + NODE_HEIGHT,
      }));

    const minX = Math.min(...coordinates.map((entry) => entry.minX));
    const minY = Math.min(...coordinates.map((entry) => entry.minY));
    const maxX = Math.max(...coordinates.map((entry) => entry.maxX));
    const maxY = Math.max(...coordinates.map((entry) => entry.maxY));
    const graphWidth = Math.max(1, maxX - minX);
    const graphHeight = Math.max(1, maxY - minY);
    const availableWidth = Math.max(1, bounds.width - padding * 2);
    const availableHeight = Math.max(1, bounds.height - padding * 2);
    const zoom = clampZoom(
      Math.min(availableWidth / graphWidth, availableHeight / graphHeight),
    );

    viewport.value = {
      x: padding + (availableWidth - graphWidth * zoom) / 2 - minX * zoom,
      y: padding + (availableHeight - graphHeight * zoom) / 2 - minY * zoom,
      zoom,
    };
  }

  function toggleFullscreen(force?: boolean) {
    isFullscreen.value = force ?? !isFullscreen.value;
  }

  function ensureNodeMeta() {
    const nextMeta: Record<string, CanvasNodeMeta> = {};
    studio.nodes.value.forEach((node, index) => {
      nextMeta[node.id] = nodeMeta.value[node.id] ?? metaFor(node, index);
    });
    nodeMeta.value = nextMeta;

    if (
      selectedNodeId.value &&
      !studio.nodes.value.some((node) => node.id === selectedNodeId.value)
    ) {
      selectedNodeId.value = studio.nodes.value[0]?.id ?? "";
    }

    if (
      selectedEdgeId.value &&
      !canvasEdges.value.some((edge) => edge.id === selectedEdgeId.value)
    ) {
      selectedEdgeId.value = "";
    }
  }

  function captureSnapshot(): CanvasSnapshot {
    return {
      graphId: studio.graphId.value,
      graphVersion: studio.graphVersion.value,
      entryNodeId: studio.entryNodeId.value,
      nodes: cloneValue(studio.nodes.value),
      edges: cloneValue(studio.edges.value),
      nodeMeta: cloneValue(nodeMeta.value),
    };
  }

  function pushSnapshot() {
    const snapshot = captureSnapshot();
    const nextKey = snapshotKey(snapshot);
    const currentKey =
      historyIndex.value >= 0
        ? snapshotKey(history.value[historyIndex.value]!)
        : null;

    if (nextKey === currentKey) {
      return;
    }

    const truncated = history.value.slice(0, historyIndex.value + 1);
    truncated.push(snapshot);
    if (truncated.length > 50) {
      truncated.shift();
    }

    history.value = truncated;
    historyIndex.value = truncated.length - 1;
  }

  function scheduleSnapshot() {
    if (isApplyingSnapshot.value || isDraggingNode.value) {
      return;
    }

    hasUnsavedChanges.value = true;
    if (historyTimer) {
      clearTimeout(historyTimer);
    }
    historyTimer = setTimeout(() => {
      pushSnapshot();
      historyTimer = null;
    }, 180);
  }

  function applySnapshot(snapshot: CanvasSnapshot) {
    isApplyingSnapshot.value = true;
    studio.graphId.value = snapshot.graphId;
    studio.graphVersion.value = snapshot.graphVersion;
    studio.entryNodeId.value = snapshot.entryNodeId;
    studio.nodes.value = cloneValue(snapshot.nodes);
    studio.edges.value = cloneValue(snapshot.edges);
    nodeMeta.value = cloneValue(snapshot.nodeMeta);
    ensureNodeMeta();
    isApplyingSnapshot.value = false;
  }

  function syncEdgesFromNodeOptions() {
    const nextEdges: DraftEdge[] = [];
    const seen = new Set<string>();

    studio.nodes.value.forEach((node) => {
      const pushEdge = (from: string, to: string) => {
        const normalizedFrom = from.trim();
        const normalizedTo = to.trim();
        if (!normalizedFrom || !normalizedTo) {
          return;
        }
        const key = `${normalizedFrom}->${normalizedTo}`;
        if (seen.has(key)) {
          return;
        }
        seen.add(key);
        nextEdges.push({ from: normalizedFrom, to: normalizedTo });
      };

      if (node.type === "DecisionNode") {
        node.decisionOptions.forEach((option) => pushEdge(node.id, option.to));
      }

      if (node.type === "ChanceNode") {
        node.chanceOptions.forEach((option) => pushEdge(node.id, option.to));
      }
    });

    const current = JSON.stringify(studio.edges.value);
    const next = JSON.stringify(nextEdges);
    if (current !== next) {
      studio.edges.value = nextEdges;
    }
  }

  function rebalanceChanceOptions(node: DraftNode) {
    if (node.type !== "ChanceNode" || node.chanceOptions.length === 0) {
      return;
    }
    const weight = (1 / node.chanceOptions.length).toFixed(2);
    node.chanceOptions.forEach((option) => {
      option.weight = weight;
    });
  }

  function addNodeAt(type: NodeType, position: { x: number; y: number }) {
    studio.addNode(type);
    const createdNode = studio.nodes.value.at(-1);
    if (!createdNode) {
      return;
    }

    nodeMeta.value[createdNode.id] = {
      ...metaFor(createdNode, studio.nodes.value.length - 1),
      x: position.x,
      y: position.y,
      title: `${type.replace("Node", "")} ${studio.nodes.value.length}`,
    };
    selectedNodeId.value = createdNode.id;
    selectedEdgeId.value = "";
    scheduleSnapshot();
  }

  function setNodePosition(nodeId: string, position: { x: number; y: number }) {
    if (!nodeMeta.value[nodeId]) {
      return;
    }
    nodeMeta.value[nodeId] = {
      ...nodeMeta.value[nodeId],
      x: position.x,
      y: position.y,
    };
  }

  function beginNodeDragInteraction() {
    isDraggingNode.value = true;
  }

  function endNodeDragInteraction() {
    if (!isDraggingNode.value) {
      return;
    }
    isDraggingNode.value = false;
    scheduleSnapshot();
  }

  function renameNode(nodeId: string, nextNodeId: string) {
    const trimmed = nextNodeId.trim();
    if (!trimmed || trimmed === nodeId) {
      return;
    }
    if (studio.nodes.value.some((node) => node.id === trimmed)) {
      return;
    }

    const node = studio.nodes.value.find((item) => item.id === nodeId);
    if (!node) {
      return;
    }

    node.id = trimmed;
    studio.nodes.value.forEach((candidate) => {
      candidate.decisionOptions.forEach((option) => {
        if (option.to === nodeId) {
          option.to = trimmed;
        }
      });
      candidate.chanceOptions.forEach((option) => {
        if (option.to === nodeId) {
          option.to = trimmed;
        }
      });
    });

    if (studio.entryNodeId.value === nodeId) {
      studio.entryNodeId.value = trimmed;
    }

    if (nodeMeta.value[nodeId]) {
      nodeMeta.value[trimmed] = nodeMeta.value[nodeId];
      delete nodeMeta.value[nodeId];
    }

    selectedNodeId.value = trimmed;
    syncEdgesFromNodeOptions();
    scheduleSnapshot();
  }

  function connectNodes(sourceId: string, targetId: string) {
    if (!sourceId || !targetId || sourceId === targetId) {
      return;
    }

    const sourceNode = studio.nodes.value.find((node) => node.id === sourceId);
    if (!sourceNode || sourceNode.type === "TerminalNode") {
      return;
    }

    if (sourceNode.type === "DecisionNode") {
      if (!sourceNode.decisionOptions.some((option) => option.to === targetId)) {
        sourceNode.decisionOptions.push({
          to: targetId,
          guardVar: "",
          guardEquals: "",
        });
      }
    }

    if (sourceNode.type === "ChanceNode") {
      if (!sourceNode.chanceOptions.some((option) => option.to === targetId)) {
        sourceNode.chanceOptions.push({
          to: targetId,
          weight: "1",
        });
        rebalanceChanceOptions(sourceNode);
      }
    }

    syncEdgesFromNodeOptions();
    selectedNodeId.value = sourceId;
    selectedEdgeId.value =
      canvasEdges.value.find((edge) => edge.from === sourceId && edge.to === targetId)?.id ??
      "";
    scheduleSnapshot();
  }

  function removeCanvasEdge(edgeId: string) {
    const edge = canvasEdges.value.find((candidate) => candidate.id === edgeId);
    if (!edge) {
      return;
    }

    const sourceNode = studio.nodes.value.find((node) => node.id === edge.from);
    if (!sourceNode) {
      return;
    }

    if (sourceNode.type === "DecisionNode") {
      const optionIndex = sourceNode.decisionOptions.findIndex(
        (option) => option.to === edge.to,
      );
      if (optionIndex >= 0) {
        sourceNode.decisionOptions.splice(optionIndex, 1);
      }
    }

    if (sourceNode.type === "ChanceNode") {
      const optionIndex = sourceNode.chanceOptions.findIndex(
        (option) => option.to === edge.to,
      );
      if (optionIndex >= 0) {
        sourceNode.chanceOptions.splice(optionIndex, 1);
        rebalanceChanceOptions(sourceNode);
      }
    }

    syncEdgesFromNodeOptions();
    selectedEdgeId.value = "";
    scheduleSnapshot();
  }

  function deleteSelected() {
    if (selectedEdgeId.value) {
      removeCanvasEdge(selectedEdgeId.value);
      return;
    }

    if (selectedNodeId.value) {
      const index = studio.nodes.value.findIndex(
        (node) => node.id === selectedNodeId.value,
      );
      if (index >= 0) {
        const removedId = selectedNodeId.value;
        studio.removeNode(index);
        delete nodeMeta.value[removedId];
        selectedNodeId.value = studio.nodes.value[0]?.id ?? "";
        scheduleSnapshot();
      }
    }
  }

  function selectNode(nodeId: string) {
    selectedNodeId.value = nodeId;
    selectedEdgeId.value = "";
  }

  function selectEdge(edgeId: string) {
    selectedEdgeId.value = edgeId;
    selectedNodeId.value = "";
  }

  function clearSelection() {
    selectedNodeId.value = "";
    selectedEdgeId.value = "";
  }

  function undo() {
    if (!canUndo.value) {
      return;
    }
    historyIndex.value -= 1;
    applySnapshot(history.value[historyIndex.value]!);
    hasUnsavedChanges.value = true;
  }

  function redo() {
    if (!canRedo.value) {
      return;
    }
    historyIndex.value += 1;
    applySnapshot(history.value[historyIndex.value]!);
    hasUnsavedChanges.value = true;
  }

  async function saveDraftLocally() {
    if (!import.meta.client) {
      return;
    }

    autosaveState.value = "saving";
    try {
      localStorage.setItem(
        "athanor-scenario-canvas-draft",
        JSON.stringify({
          ...captureSnapshot(),
          scenarioName: studio.scenarioName.value,
          scenarioDescription: studio.scenarioDescription.value,
          savedAt: new Date().toISOString(),
        }),
      );
      lastAutosavedAt.value = new Date().toISOString();
      autosaveState.value = "saved";
      hasUnsavedChanges.value = false;
    } catch {
      autosaveState.value = "error";
    }
  }

  function importGraphFromJson(text: string) {
    const parsed = JSON.parse(text) as Record<string, any>;
    const rawNodes = Array.isArray(parsed.nodes) ? parsed.nodes : [];
    const rawEdges = Array.isArray(parsed.edges) ? parsed.edges : [];

    studio.graphId.value = String(parsed.id ?? studio.graphId.value);
    studio.graphVersion.value = String(parsed.version ?? studio.graphVersion.value);
    studio.entryNodeId.value = String(
      parsed.entry_node_id ?? parsed.entryNodeId ?? "",
    );

    studio.nodes.value = rawNodes.map((rawNode, index) => {
      const nodeType = String(rawNode.type ?? "DecisionNode");
      const type =
        nodeType === "decision" || nodeType === "DecisionNode"
          ? "DecisionNode"
          : nodeType === "chance" || nodeType === "ChanceNode"
            ? "ChanceNode"
            : "TerminalNode";

      const fallbackTargets = rawEdges
        .filter((edge: any) => String(edge.from ?? "") === String(rawNode.id ?? ""))
        .map((edge: any) => String(edge.to ?? ""));

      return {
        id: String(rawNode.id ?? `node-${index + 1}`),
        type,
        outcome: String(rawNode.outcome ?? ""),
        decisionOptions: Array.isArray(rawNode.decision_options)
          ? rawNode.decision_options.map((option: any) => ({
              to: String(option.to ?? ""),
              guardVar: String(option.guard?.var ?? ""),
              guardEquals: String(option.guard?.equals ?? ""),
            }))
          : type === "DecisionNode"
            ? fallbackTargets.map((target) => ({
                to: target,
                guardVar: "",
                guardEquals: "",
              }))
          : [],
        chanceOptions: Array.isArray(rawNode.chance_options)
          ? rawNode.chance_options.map((option: any) => ({
              to: String(option.to ?? ""),
              weight: String(option.weight ?? "1"),
            }))
          : type === "ChanceNode"
            ? fallbackTargets.map((target) => ({
                to: target,
                weight: fallbackTargets.length
                  ? (1 / fallbackTargets.length).toFixed(2)
                  : "1",
              }))
          : [],
      } satisfies DraftNode;
    });

    nodeMeta.value = Object.fromEntries(
      studio.nodes.value.map((node, index) => [
        node.id,
        {
          ...metaFor(node, index),
          title: String((rawNodes[index]?.title as string | undefined) ?? node.id),
          description: String(rawNodes[index]?.description ?? ""),
          score: String(rawNodes[index]?.score ?? ""),
        },
      ]),
    );

    syncEdgesFromNodeOptions();
    ensureNodeMeta();
    selectedNodeId.value = studio.nodes.value[0]?.id ?? "";
    selectedEdgeId.value = "";
    scheduleSnapshot();
  }

  function loadExampleCanvasGraph() {
    studio.loadExampleGraph();
    nodeMeta.value = Object.fromEntries(
      studio.nodes.value.map((node, index) => [
        node.id,
        {
          ...metaFor(node, index),
          x: 96 + index * 280,
          y:
            node.type === "DecisionNode"
              ? 96
              : node.type === "ChanceNode"
                ? 296
                : 520 + Math.max(0, index - 2) * 180,
          title: node.id,
          description: "",
          score: "",
        },
      ]),
    );
    syncEdgesFromNodeOptions();
    ensureNodeMeta();
    selectedNodeId.value =
      studio.entryNodeId.value || studio.nodes.value[0]?.id || "";
    selectedEdgeId.value = "";
    scheduleSnapshot();
  }

  function markStudioMutation() {
    syncEdgesFromNodeOptions();
    ensureNodeMeta();
    scheduleSnapshot();
  }

  ensureNodeMeta();

  if (!historyWatchersInitialized) {
    watch(
      [studio.nodes, studio.graphId, studio.graphVersion, studio.entryNodeId],
      () => {
        ensureNodeMeta();
        syncEdgesFromNodeOptions();
      },
      { deep: true },
    );

    watch(
      [
        studio.nodes,
        studio.edges,
        studio.graphId,
        studio.graphVersion,
        studio.entryNodeId,
        nodeMeta,
      ],
      () => {
        if (isDraggingNode.value) {
          hasUnsavedChanges.value = true;
          return;
        }
        if (!history.value.length) {
          pushSnapshot();
          hasUnsavedChanges.value = false;
          return;
        }
        scheduleSnapshot();
      },
      { deep: true, immediate: true },
    );

    historyWatchersInitialized = true;
  }

  return {
    studio,
    nodeMeta,
    canvasEdges,
    selectedNode,
    selectedNodeId,
    selectedNodeMeta,
    selectedEdge,
    selectedEdgeId,
    canUndo,
    canRedo,
    hasUnsavedChanges,
    viewport,
    isFullscreen,
    autosaveState,
    lastAutosavedAt,
    addNodeAt,
    setNodePosition,
    beginNodeDragInteraction,
    endNodeDragInteraction,
    renameNode,
    connectNodes,
    removeCanvasEdge,
    deleteSelected,
    selectNode,
    selectEdge,
    clearSelection,
    syncEdgesFromNodeOptions,
    undo,
    redo,
    panViewport,
    setViewportZoom,
    resetViewport,
    fitGraphToViewport,
    toggleFullscreen,
    graphPointFromCanvasPoint,
    canvasPointFromGraphPoint,
    saveDraftLocally,
    importGraphFromJson,
    loadExampleCanvasGraph,
    markStudioMutation,
  };
}
