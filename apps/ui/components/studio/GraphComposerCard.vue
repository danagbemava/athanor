<script setup lang="ts">
import { computed, nextTick, onBeforeUnmount, onMounted, ref, watch } from "vue";
import {
    ArrowRight,
    CornerDownLeft,
    Download,
    MousePointerClick,
    Plus,
    Redo2,
    Trash2,
    Undo2,
    Upload,
} from "lucide-vue-next";
import { Controls } from "@vue-flow/controls";
import { MiniMap } from "@vue-flow/minimap";
import {
    MarkerType,
    VueFlow,
    useVueFlow,
    type Connection,
    type Edge,
    type EdgeMouseEvent,
    type Node,
    type NodeDragEvent,
    type NodeMouseEvent,
} from "@vue-flow/core";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import {
    Card,
    CardContent,
    CardDescription,
    CardHeader,
    CardTitle,
} from "@/components/ui/card";
import { Input } from "@/components/ui/input";
import { Textarea } from "@/components/ui/textarea";

import ScenarioFlowNode from "@/components/studio/ScenarioFlowNode.vue";
import type { DraftNode, NodeType } from "@/composables/useScenarioStudio";

const flowId = "scenario-studio-flow";
const canvas = useScenarioCanvas();
const {
    studio,
    nodeMeta,
    canvasEdges,
    selectedNode,
    selectedNodeId,
    selectedNodeMeta,
    selectedEdge,
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
    setViewportZoom,
    resetViewport,
    fitGraphToViewport,
    toggleFullscreen,
    saveDraftLocally,
    importGraphFromJson,
    markStudioMutation,
    graphPointFromCanvasPoint,
} = canvas;

const {
    setViewport,
    removeSelectedElements,
} = useVueFlow({ id: flowId });

type ScenarioFlowNodeData = {
    title: string;
    description: string;
    detail: string;
    selected: boolean;
    connectable: boolean;
};

const canvasRef = ref<HTMLElement | null>(null);
const previousBodyOverflow = ref("");
const rawNodeId = ref("");
const rawImportError = ref("");
const quickAddMenu = ref<{
    x: number;
    y: number;
    graphX: number;
    graphY: number;
} | null>(null);

const nodeReferences = computed(() => studio.nodeReferences.value);
const graphNodes = computed(() => studio.nodes.value);
const graphValidationIssues = computed(() => studio.graphValidationIssues.value);
const zoomPercent = computed(() => `${Math.round(viewport.value.zoom * 100)}%`);
const shellClass = computed(() =>
    isFullscreen.value
        ? "fixed inset-4 z-50 flex max-h-[calc(100vh-2rem)] flex-col overflow-hidden rounded-3xl border-border bg-card shadow-2xl"
        : "border-border bg-card shadow-sm",
);
const contentClass = computed(() =>
    isFullscreen.value ? "flex min-h-0 flex-1 flex-col space-y-4 overflow-hidden" : "space-y-4",
);
const layoutClass = computed(() =>
    isFullscreen.value
        ? "grid min-h-0 flex-1 gap-4 xl:grid-cols-[minmax(0,1fr)_300px]"
        : "grid gap-4 xl:grid-cols-[minmax(0,1fr)_320px] 2xl:grid-cols-[minmax(0,1fr)_340px]",
);
const canvasWrapperClass = computed(() =>
    isFullscreen.value
        ? "relative h-full min-h-0 overflow-hidden rounded-2xl border border-border bg-background"
        : "relative min-h-[78vh] overflow-hidden rounded-2xl border border-border bg-background",
);
const chanceWeightIssue = computed(() => {
    const node = selectedNode.value;
    if (!node || node.type !== "ChanceNode") {
        return "";
    }
    const total = node.chanceOptions.reduce((sum, option) => {
        const parsed = Number.parseFloat(option.weight);
        return Number.isFinite(parsed) ? sum + parsed : sum;
    }, 0);
    return Math.abs(total - 1) > 0.0001
        ? `Weights currently add up to ${total.toFixed(2)}`
        : "";
});

const flowNodes = computed<Node<ScenarioFlowNodeData>[]>(() =>
    graphNodes.value.map((node, index) => {
        const meta = nodeMeta.value[node.id] ?? {
            x: 96 + (index % 3) * 260,
            y: 96 + Math.floor(index / 3) * 190,
            title: node.id,
            description: "",
            score: "",
        };

        let type = "decision";
        let detail = `${node.decisionOptions.length} decision paths`;
        if (node.type === "ChanceNode") {
            type = "chance";
            detail = `${node.chanceOptions.length} weighted paths`;
        } else if (node.type === "TerminalNode") {
            type = "terminal";
            detail = `Outcome: ${node.outcome || "Unspecified terminal"}`;
        }

        return {
            id: node.id,
            type,
            position: { x: meta.x, y: meta.y },
            draggable: true,
            selectable: true,
            connectable: node.type !== "TerminalNode",
            data: {
                title: meta.title || node.id,
                description: meta.description,
                detail,
                selected: selectedNodeId.value === node.id,
                connectable: node.type !== "TerminalNode",
            },
        };
    }),
);

const flowEdges = computed<Edge[]>(() =>
    canvasEdges.value.map((edge) => ({
        id: edge.id,
        source: edge.from,
        target: edge.to,
        markerEnd: MarkerType.ArrowClosed,
        selectable: true,
        animated: true,
        style: {
            stroke:
                selectedEdge.value?.id === edge.id
                    ? "rgb(14 165 233)"
                    : "rgb(148 163 184 / 0.8)",
            strokeWidth: selectedEdge.value?.id === edge.id ? 3 : 2,
        },
        class: selectedEdge.value?.id === edge.id ? "selected" : "",
    })),
);

watch(
    selectedNode,
    (node) => {
        rawNodeId.value = node?.id ?? "";
    },
    { immediate: true },
);

watch(isFullscreen, async (fullscreen) => {
    if (!import.meta.client) {
        return;
    }

    if (fullscreen) {
        previousBodyOverflow.value = document.body.style.overflow;
        document.body.style.overflow = "hidden";
        await nextTick();
        void fitFlowToGraph();
        return;
    }

    document.body.style.overflow = previousBodyOverflow.value;
});

const rawPayload = computed(() => studio.formatPayload(studio.graphPayload.value));

function setEntryNode(nodeId: string) {
    studio.entryNodeId.value = nodeId;
    markStudioMutation();
}

function addToolbarNode(type: NodeType) {
    const index = graphNodes.value.length;
    addNodeAt(type, {
        x: 120 + (index % 3) * 260,
        y: 120 + Math.floor(index / 3) * 200,
    });
}

function handleNodeClick({ node }: NodeMouseEvent) {
    quickAddMenu.value = null;
    selectNode(node.id);
}

function handleEdgeClick({ edge }: EdgeMouseEvent) {
    quickAddMenu.value = null;
    selectEdge(edge.id);
}

function handlePaneClick(event: MouseEvent) {
    const rect = canvasRef.value?.getBoundingClientRect();
    clearSelection();
    removeSelectedElements();
    if (!rect) {
        quickAddMenu.value = null;
        return;
    }

    const canvasPoint = {
        x: event.clientX - rect.left,
        y: event.clientY - rect.top,
    };
    const graphPoint = graphPointFromCanvasPoint(canvasPoint);

    quickAddMenu.value = {
        x: Math.max(16, Math.min(canvasPoint.x, rect.width - 180)),
        y: Math.max(16, Math.min(canvasPoint.y, rect.height - 176)),
        graphX: graphPoint.x,
        graphY: graphPoint.y,
    };
}

function handleNodeDragStart() {
    beginNodeDragInteraction();
}

function handleNodeDragStop({ node }: NodeDragEvent) {
    setNodePosition(node.id, {
        x: node.position.x,
        y: node.position.y,
    });
    endNodeDragInteraction();
}

function handleConnect(connection: Connection) {
    if (!connection.source || !connection.target) {
        return;
    }
    connectNodes(connection.source, connection.target);
}

function handleViewportChangeEnd(nextViewport: {
    x: number;
    y: number;
    zoom: number;
}) {
    viewport.value = {
        x: nextViewport.x,
        y: nextViewport.y,
        zoom: nextViewport.zoom,
    };
}

async function fitFlowToGraph() {
    await nextTick();
    const rect = canvasRef.value?.getBoundingClientRect();
    if (rect) {
        fitGraphToViewport({
            width: rect.width,
            height: rect.height,
            padding: isFullscreen.value ? 120 : 96,
        });
    }

    await setViewport({
        x: viewport.value.x,
        y: viewport.value.y,
        zoom: viewport.value.zoom,
    });
}

async function resetCanvasViewport() {
    resetViewport();
    await setViewport({ x: 0, y: 0, zoom: 1 });
}

async function zoomInViewport() {
    setViewportZoom(viewport.value.zoom + 0.1);
    await setViewport({
        x: viewport.value.x,
        y: viewport.value.y,
        zoom: viewport.value.zoom,
    });
}

async function zoomOutViewport() {
    setViewportZoom(viewport.value.zoom - 0.1);
    await setViewport({
        x: viewport.value.x,
        y: viewport.value.y,
        zoom: viewport.value.zoom,
    });
}

async function toggleCanvasFullscreen() {
    quickAddMenu.value = null;
    toggleFullscreen();
    if (isFullscreen.value) {
        await nextTick();
        await fitFlowToGraph();
    }
}

async function importFromClipboard() {
    rawImportError.value = "";
    try {
        const text = await navigator.clipboard.readText();
        importGraphFromJson(text);
        await fitFlowToGraph();
    } catch (error) {
        rawImportError.value =
            error instanceof Error ? error.message : "Clipboard import failed.";
    }
}

function commitNodeIdRename() {
    if (!selectedNode.value) {
        return;
    }
    renameNode(selectedNode.value.id, rawNodeId.value);
    rawNodeId.value = selectedNode.value.id;
}

function handleKeyboardShortcut(event: KeyboardEvent) {
    const target = event.target as HTMLElement | null;

    if (event.key === "Escape") {
        quickAddMenu.value = null;
    }

    if (event.key === "Escape" && isFullscreen.value) {
        event.preventDefault();
        toggleFullscreen(false);
        return;
    }

    if (target?.closest("input, textarea, select")) {
        return;
    }

    if ((event.metaKey || event.ctrlKey) && event.key.toLowerCase() === "z") {
        event.preventDefault();
        if (event.shiftKey) {
            redo();
        } else {
            undo();
        }
        return;
    }

    if ((event.metaKey || event.ctrlKey) && event.key.toLowerCase() === "y") {
        event.preventDefault();
        redo();
        return;
    }

    if (event.key === "Delete" || event.key === "Backspace") {
        event.preventDefault();
        deleteSelected();
    }
}

function addNodeFromQuickMenu(type: NodeType) {
    if (!quickAddMenu.value) {
        return;
    }

    addNodeAt(type, {
        x: quickAddMenu.value.graphX,
        y: quickAddMenu.value.graphY,
    });
    quickAddMenu.value = null;
}

let autosaveTimer: ReturnType<typeof setInterval> | null = null;

onMounted(() => {
    window.addEventListener("keydown", handleKeyboardShortcut);
    autosaveTimer = window.setInterval(() => {
        if (hasUnsavedChanges.value) {
            void saveDraftLocally();
        }
    }, 30000);

    void nextTick(async () => {
        await setViewport({
            x: viewport.value.x,
            y: viewport.value.y,
            zoom: viewport.value.zoom,
        });
    });
});

onBeforeUnmount(() => {
    window.removeEventListener("keydown", handleKeyboardShortcut);
    document.body.style.overflow = previousBodyOverflow.value;
    if (autosaveTimer) {
        window.clearInterval(autosaveTimer);
    }
});
</script>

<template>
    <div class="relative">
        <div
            v-if="isFullscreen"
            class="fixed inset-0 z-40 bg-background/80 backdrop-blur-sm"
        />

        <Card :class="shellClass">
            <CardHeader>
                <div class="flex flex-wrap items-start justify-between gap-3">
                    <div>
                        <CardTitle class="text-lg">Canvas Graph Editor</CardTitle>
                        <CardDescription>
                            Vue Flow-backed scenario graph editing with built-in
                            drag, connect, pan, and zoom behavior.
                        </CardDescription>
                    </div>
                    <div class="flex flex-wrap items-center gap-2">
                        <Badge
                            :variant="
                                hasUnsavedChanges ? 'secondary' : 'outline'
                            "
                        >
                            {{ hasUnsavedChanges ? "Unsaved changes" : "Saved" }}
                        </Badge>
                        <Badge variant="outline">
                            {{ zoomPercent }}
                        </Badge>
                        <Badge variant="outline">
                            {{
                                autosaveState === "saved" && lastAutosavedAt
                                    ? `Auto-saved ${studio.formatTimestamp(lastAutosavedAt)}`
                                    : autosaveState === "saving"
                                      ? "Saving draft..."
                                      : autosaveState === "error"
                                        ? "Autosave failed"
                                        : "Autosave every 30s"
                            }}
                        </Badge>
                        <Button size="sm" variant="outline" @click="zoomOutViewport">
                            Zoom Out
                        </Button>
                        <Button size="sm" variant="outline" @click="zoomInViewport">
                            Zoom In
                        </Button>
                        <Button size="sm" variant="outline" @click="resetCanvasViewport">
                            Reset View
                        </Button>
                        <Button size="sm" variant="outline" @click="fitFlowToGraph">
                            Fit Graph
                        </Button>
                        <Button
                            size="sm"
                            variant="outline"
                            :disabled="!canUndo"
                            @click="undo"
                        >
                            <Undo2 class="mr-2 size-4" /> Undo
                        </Button>
                        <Button
                            size="sm"
                            variant="outline"
                            :disabled="!canRedo"
                            @click="redo"
                        >
                            <Redo2 class="mr-2 size-4" /> Redo
                        </Button>
                        <Button size="sm" variant="outline" @click="importFromClipboard">
                            <Upload class="mr-2 size-4" /> Import JSON
                        </Button>
                        <Button size="sm" variant="outline" @click="saveDraftLocally">
                            <Download class="mr-2 size-4" /> Save Draft
                        </Button>
                        <Button
                            size="sm"
                            variant="secondary"
                            @click="toggleCanvasFullscreen"
                        >
                            {{ isFullscreen ? "Exit Fullscreen" : "Fullscreen" }}
                        </Button>
                    </div>
                </div>
            </CardHeader>
            <CardContent :class="contentClass">
                <div :class="layoutClass">
                    <div
                        ref="canvasRef"
                        :class="canvasWrapperClass"
                    >
                        <div class="absolute left-4 top-4 z-20 flex flex-wrap gap-2">
                            <Button
                                size="sm"
                                variant="secondary"
                                @click.stop="addToolbarNode('DecisionNode')"
                            >
                                <Plus class="mr-2 size-4" /> Decision
                            </Button>
                            <Button
                                size="sm"
                                variant="secondary"
                                @click.stop="addToolbarNode('ChanceNode')"
                            >
                                <Plus class="mr-2 size-4" /> Chance
                            </Button>
                            <Button
                                size="sm"
                                variant="secondary"
                                @click.stop="addToolbarNode('TerminalNode')"
                            >
                                <Plus class="mr-2 size-4" /> Terminal
                            </Button>
                        </div>

                        <div
                            v-if="quickAddMenu"
                            class="absolute z-30 w-40 rounded-2xl border border-border bg-card/95 p-2 shadow-2xl backdrop-blur"
                            :style="{
                                left: `${quickAddMenu.x}px`,
                                top: `${quickAddMenu.y}px`,
                            }"
                        >
                            <p class="mb-2 flex items-center gap-2 px-2 text-xs font-medium uppercase tracking-wide text-muted-foreground">
                                <MousePointerClick class="size-3" />
                                Add Node
                            </p>
                            <div class="space-y-1">
                                <Button
                                    class="w-full justify-start"
                                    size="sm"
                                    variant="ghost"
                                    @click.stop="addNodeFromQuickMenu('DecisionNode')"
                                >
                                    <Plus class="mr-2 size-4" /> Decision
                                </Button>
                                <Button
                                    class="w-full justify-start"
                                    size="sm"
                                    variant="ghost"
                                    @click.stop="addNodeFromQuickMenu('ChanceNode')"
                                >
                                    <Plus class="mr-2 size-4" /> Chance
                                </Button>
                                <Button
                                    class="w-full justify-start"
                                    size="sm"
                                    variant="ghost"
                                    @click.stop="addNodeFromQuickMenu('TerminalNode')"
                                >
                                    <Plus class="mr-2 size-4" /> Terminal
                                </Button>
                            </div>
                        </div>

                        <VueFlow
                            :id="flowId"
                            :nodes="flowNodes"
                            :edges="flowEdges"
                            :min-zoom="0.4"
                            :max-zoom="1.8"
                            :nodes-draggable="true"
                            :nodes-connectable="true"
                            :elements-selectable="true"
                            :delete-key-code="null"
                            :selection-key-code="null"
                            :multi-selection-key-code="null"
                            :select-nodes-on-drag="false"
                            :pan-on-drag="true"
                            :zoom-on-double-click="false"
                            :auto-pan-on-node-drag="true"
                            class="scenario-flow h-full w-full"
                            @node-click="handleNodeClick"
                            @edge-click="handleEdgeClick"
                            @pane-click="handlePaneClick"
                            @connect="handleConnect"
                            @node-drag-start="handleNodeDragStart"
                            @node-drag-stop="handleNodeDragStop"
                            @viewport-change-end="handleViewportChangeEnd"
                        >
                            <Controls position="bottom-left" />
                            <MiniMap
                                position="bottom-right"
                                pannable
                                zoomable
                            />

                            <template #node-decision="nodeProps">
                                <ScenarioFlowNode v-bind="nodeProps" />
                            </template>

                            <template #node-chance="nodeProps">
                                <ScenarioFlowNode v-bind="nodeProps" />
                            </template>

                            <template #node-terminal="nodeProps">
                                <ScenarioFlowNode v-bind="nodeProps" />
                            </template>
                        </VueFlow>
                    </div>

                    <div class="max-h-full space-y-4 overflow-y-auto pr-1 xl:sticky xl:top-4 xl:self-start">
                        <Card class="border-border bg-muted/30 shadow-sm">
                            <CardHeader>
                                <CardTitle class="text-base">Inspector</CardTitle>
                                <CardDescription>
                                    Select a node or edge to edit its details.
                                </CardDescription>
                            </CardHeader>
                            <CardContent class="space-y-4">
                                <template v-if="selectedNode && selectedNodeMeta">
                                    <div class="grid gap-3">
                                        <div class="space-y-2">
                                            <p class="text-xs font-medium uppercase tracking-wide text-muted-foreground">
                                                Node Title
                                            </p>
                                            <Input
                                                v-model="selectedNodeMeta.title"
                                                @input="markStudioMutation"
                                            />
                                        </div>

                                        <div class="space-y-2">
                                            <p class="text-xs font-medium uppercase tracking-wide text-muted-foreground">
                                                Node ID
                                            </p>
                                            <Input
                                                v-model="rawNodeId"
                                                @change="commitNodeIdRename"
                                            />
                                        </div>

                                        <div class="space-y-2">
                                            <p class="text-xs font-medium uppercase tracking-wide text-muted-foreground">
                                                Type
                                            </p>
                                            <select
                                                v-model="selectedNode.type"
                                                :class="studio.nativeControlClass"
                                                @change="
                                                    studio.normalizeNodeForType(selectedNode);
                                                    syncEdgesFromNodeOptions();
                                                    markStudioMutation();
                                                "
                                            >
                                                <option value="DecisionNode">DecisionNode</option>
                                                <option value="ChanceNode">ChanceNode</option>
                                                <option value="TerminalNode">TerminalNode</option>
                                            </select>
                                        </div>

                                        <div class="space-y-2">
                                            <p class="text-xs font-medium uppercase tracking-wide text-muted-foreground">
                                                Description
                                            </p>
                                            <Textarea
                                                v-model="selectedNodeMeta.description"
                                                rows="3"
                                                @input="markStudioMutation"
                                            />
                                        </div>

                                        <div
                                            v-if="selectedNode.type === 'TerminalNode'"
                                            class="grid gap-3 sm:grid-cols-2"
                                        >
                                            <div class="space-y-2">
                                                <p class="text-xs font-medium uppercase tracking-wide text-muted-foreground">
                                                    Outcome
                                                </p>
                                                <Input
                                                    v-model="selectedNode.outcome"
                                                    @input="markStudioMutation"
                                                />
                                            </div>
                                            <div class="space-y-2">
                                                <p class="text-xs font-medium uppercase tracking-wide text-muted-foreground">
                                                    Score
                                                </p>
                                                <Input
                                                    v-model="selectedNodeMeta.score"
                                                    placeholder="Optional"
                                                    @input="markStudioMutation"
                                                />
                                            </div>
                                        </div>

                                        <label class="flex items-center gap-2 text-sm">
                                            <input
                                                :checked="studio.entryNodeId.value === selectedNode.id"
                                                type="checkbox"
                                                @change="setEntryNode(selectedNode.id)"
                                            />
                                            Use as entry node
                                        </label>

                                        <div
                                            v-if="selectedNode.type === 'DecisionNode'"
                                            class="space-y-3"
                                        >
                                            <div class="flex items-center justify-between">
                                                <p class="text-sm font-semibold">Decision Options</p>
                                                <Button
                                                    size="sm"
                                                    variant="outline"
                                                    @click="
                                                        studio.addDecisionOption(selectedNode);
                                                        markStudioMutation();
                                                    "
                                                >
                                                    <Plus class="mr-2 size-4" /> Add
                                                </Button>
                                            </div>
                                            <div
                                                v-for="(option, optionIndex) in selectedNode.decisionOptions"
                                                :key="`decision-${optionIndex}`"
                                                class="space-y-2 rounded-xl border border-border bg-background/70 p-3"
                                            >
                                                <div class="flex items-center justify-between">
                                                    <p class="text-xs font-medium uppercase tracking-wide text-muted-foreground">
                                                        Path {{ optionIndex + 1 }}
                                                    </p>
                                                    <Button
                                                        size="icon"
                                                        variant="ghost"
                                                        @click="
                                                            studio.removeDecisionOption(selectedNode, optionIndex);
                                                            syncEdgesFromNodeOptions();
                                                            markStudioMutation();
                                                        "
                                                    >
                                                        <Trash2 class="size-4" />
                                                    </Button>
                                                </div>
                                                <select
                                                    v-model="option.to"
                                                    :class="studio.nativeControlClass"
                                                    @change="
                                                        syncEdgesFromNodeOptions();
                                                        markStudioMutation();
                                                    "
                                                >
                                                    <option value="">Next node</option>
                                                    <option
                                                        v-for="refNode in nodeReferences"
                                                        :key="`decision-ref-${refNode.id}`"
                                                        :value="refNode.id"
                                                    >
                                                        {{ refNode.label }}
                                                    </option>
                                                </select>
                                                <Input
                                                    v-model="option.guardVar"
                                                    placeholder="Guard variable"
                                                    @input="markStudioMutation"
                                                />
                                                <Input
                                                    v-model="option.guardEquals"
                                                    placeholder="Guard equals"
                                                    @input="markStudioMutation"
                                                />
                                            </div>
                                        </div>

                                        <div
                                            v-if="selectedNode.type === 'ChanceNode'"
                                            class="space-y-3"
                                        >
                                            <div class="flex items-center justify-between">
                                                <p class="text-sm font-semibold">Chance Outcomes</p>
                                                <Button
                                                    size="sm"
                                                    variant="outline"
                                                    @click="
                                                        studio.addChanceOption(selectedNode);
                                                        syncEdgesFromNodeOptions();
                                                        markStudioMutation();
                                                    "
                                                >
                                                    <Plus class="mr-2 size-4" /> Add
                                                </Button>
                                            </div>
                                            <div
                                                v-for="(option, optionIndex) in selectedNode.chanceOptions"
                                                :key="`chance-${optionIndex}`"
                                                class="space-y-2 rounded-xl border border-border bg-background/70 p-3"
                                            >
                                                <div class="flex items-center justify-between">
                                                    <p class="text-xs font-medium uppercase tracking-wide text-muted-foreground">
                                                        Outcome {{ optionIndex + 1 }}
                                                    </p>
                                                    <Button
                                                        size="icon"
                                                        variant="ghost"
                                                        @click="
                                                            studio.removeChanceOption(selectedNode, optionIndex);
                                                            syncEdgesFromNodeOptions();
                                                            markStudioMutation();
                                                        "
                                                    >
                                                        <Trash2 class="size-4" />
                                                    </Button>
                                                </div>
                                                <select
                                                    v-model="option.to"
                                                    :class="studio.nativeControlClass"
                                                    @change="
                                                        syncEdgesFromNodeOptions();
                                                        markStudioMutation();
                                                    "
                                                >
                                                    <option value="">Next node</option>
                                                    <option
                                                        v-for="refNode in nodeReferences"
                                                        :key="`chance-ref-${refNode.id}`"
                                                        :value="refNode.id"
                                                    >
                                                        {{ refNode.label }}
                                                    </option>
                                                </select>
                                                <Input
                                                    v-model="option.weight"
                                                    placeholder="Weight"
                                                    inputmode="decimal"
                                                    @input="
                                                        syncEdgesFromNodeOptions();
                                                        markStudioMutation();
                                                    "
                                                />
                                            </div>
                                            <p
                                                v-if="chanceWeightIssue"
                                                class="text-sm text-amber-600"
                                            >
                                                {{ chanceWeightIssue }}
                                            </p>
                                        </div>
                                    </div>
                                </template>

                                <template v-else-if="selectedEdge">
                                    <div class="space-y-3 rounded-xl border border-border bg-background/70 p-4">
                                        <p class="text-sm font-semibold">Selected Edge</p>
                                        <p class="text-sm text-muted-foreground">
                                            {{ selectedEdge.from }}
                                            <ArrowRight class="mx-1 inline size-4" />
                                            {{ selectedEdge.to }}
                                        </p>
                                        <Button
                                            variant="destructive"
                                            @click="removeCanvasEdge(selectedEdge.id)"
                                        >
                                            <Trash2 class="mr-2 size-4" /> Delete Edge
                                        </Button>
                                    </div>
                                </template>

                                <template v-else>
                                    <div class="rounded-xl border border-dashed border-border bg-background/60 p-4 text-sm text-muted-foreground">
                                        Click a node to select it. Selected nodes become draggable,
                                        and non-terminal nodes expose Vue Flow connection handles.
                                    </div>
                                </template>
                            </CardContent>
                        </Card>

                        <Card class="border-border bg-muted/30 shadow-sm">
                            <CardHeader>
                                <CardTitle class="text-base">Draft Diagnostics</CardTitle>
                                <CardDescription>
                                    Vue Flow now handles drag, connect, pan, zoom, minimap, and controls.
                                    Cmd/Ctrl+Z and Cmd/Ctrl+Y still drive graph history.
                                </CardDescription>
                            </CardHeader>
                            <CardContent class="space-y-3 text-sm">
                                <div
                                    v-if="graphValidationIssues.length"
                                    class="rounded-xl border border-amber-500/40 bg-amber-500/10 p-3"
                                >
                                    <p class="mb-2 font-semibold text-amber-700">
                                        Validation issues
                                    </p>
                                    <ul class="space-y-1 text-amber-800">
                                        <li
                                            v-for="issue in graphValidationIssues.slice(0, 5)"
                                            :key="issue"
                                        >
                                            <CornerDownLeft class="mr-1 inline size-3" />
                                            {{ issue }}
                                        </li>
                                    </ul>
                                </div>
                                <div
                                    v-if="rawImportError"
                                    class="rounded-xl border border-destructive/40 bg-destructive/10 p-3 text-destructive"
                                >
                                    {{ rawImportError }}
                                </div>
                                <details class="rounded-xl border border-border bg-background/60 p-3">
                                    <summary class="cursor-pointer font-semibold">
                                        Raw Scenario Payload
                                    </summary>
                                    <pre class="mt-3 max-h-72 overflow-auto whitespace-pre-wrap text-xs">{{ rawPayload }}</pre>
                                </details>
                            </CardContent>
                        </Card>
                    </div>
                </div>
            </CardContent>
        </Card>
    </div>
</template>

<style>
@import "@vue-flow/core/dist/style.css";
@import "@vue-flow/core/dist/theme-default.css";
@import "@vue-flow/controls/dist/style.css";
@import "@vue-flow/minimap/dist/style.css";

.scenario-flow {
    --vf-node-bg: rgba(12, 18, 28, 0.94);
    --vf-node-text: rgb(244 244 245);
    --vf-node-color: rgb(148 163 184 / 0.7);
    --vf-connection-path: rgb(14 165 233);
    background:
        radial-gradient(circle at top, rgba(14, 165, 233, 0.08), transparent 35%),
        linear-gradient(180deg, rgba(15, 23, 42, 0.04), transparent);
}

.scenario-flow .vue-flow__controls {
    box-shadow: none;
    border: 1px solid rgb(63 63 70 / 0.7);
    background: rgb(10 10 10 / 0.92);
}

.scenario-flow .vue-flow__controls-button {
    border-bottom-color: rgb(63 63 70 / 0.7);
    color: rgb(228 228 231);
    background: transparent;
}

.scenario-flow .vue-flow__minimap {
    background: rgb(10 10 10 / 0.92);
    border: 1px solid rgb(63 63 70 / 0.7);
}

.scenario-flow .vue-flow__edge.selected path {
    stroke: rgb(14 165 233);
    stroke-width: 3;
}

.scenario-flow .vue-flow__edge.animated path {
    stroke-dasharray: 10 8;
}

.scenario-flow .vue-flow__pane {
    cursor: crosshair;
    background-color: rgb(9 14 24 / 0.95);
    background-image:
        radial-gradient(circle at center, rgba(56, 189, 248, 0.18) 0 1px, transparent 1.5px),
        radial-gradient(circle at top, rgba(14, 165, 233, 0.06), transparent 38%);
    background-position: 0 0, center;
    background-size: 24px 24px, 100% 100%;
}
</style>
