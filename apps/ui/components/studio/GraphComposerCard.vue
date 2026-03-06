<script setup lang="ts">
import { Plus, Trash2 } from "lucide-vue-next";
import { Button } from "@/components/ui/button";
import {
    Card,
    CardContent,
    CardDescription,
    CardHeader,
    CardTitle,
} from "@/components/ui/card";
import { Input } from "@/components/ui/input";

const {
    nativeControlClass,
    graphId,
    graphVersion,
    entryNodeId,
    nodes,
    edges,
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
    formatPayload,
} = useScenarioStudio();
</script>

<template>
    <Card class="border-border bg-card shadow-sm">
        <CardHeader>
            <CardTitle class="text-lg">Advanced Graph Composer</CardTitle>
            <CardDescription>
                Node and edge editor used by quick actions. JSON is generated
                automatically and remains read-only.
            </CardDescription>
        </CardHeader>
        <CardContent class="space-y-4">
            <div class="grid gap-4 sm:grid-cols-3">
                <div class="space-y-2">
                    <p
                        class="text-xs font-medium uppercase tracking-wide text-muted-foreground"
                    >
                        Graph ID
                    </p>
                    <Input v-model="graphId" placeholder="scenario-graph-1" />
                </div>
                <div class="space-y-2">
                    <p
                        class="text-xs font-medium uppercase tracking-wide text-muted-foreground"
                    >
                        Version
                    </p>
                    <Input
                        v-model="graphVersion"
                        inputmode="numeric"
                        placeholder="1"
                    />
                </div>
                <div class="space-y-2">
                    <p
                        class="text-xs font-medium uppercase tracking-wide text-muted-foreground"
                    >
                        Entry Node
                    </p>
                    <select v-model="entryNodeId" :class="nativeControlClass">
                        <option value="">Select entry node</option>
                        <option
                            v-for="refNode in nodeReferences"
                            :key="`entry-${refNode.id}`"
                            :value="refNode.id"
                        >
                            {{ refNode.label }}
                        </option>
                    </select>
                </div>
            </div>

            <div class="rounded-lg border border-border bg-muted/30 p-3">
                <div
                    class="mb-3 flex flex-wrap items-center justify-between gap-2"
                >
                    <p class="text-sm font-semibold">Nodes</p>
                    <div class="flex flex-wrap gap-2">
                        <Button
                            size="sm"
                            variant="secondary"
                            @click="addNode('DecisionNode')"
                        >
                            <Plus class="mr-2 size-4" /> Decision
                        </Button>
                        <Button
                            size="sm"
                            variant="secondary"
                            @click="addNode('ChanceNode')"
                        >
                            <Plus class="mr-2 size-4" /> Chance
                        </Button>
                        <Button
                            size="sm"
                            variant="secondary"
                            @click="addNode('TerminalNode')"
                        >
                            <Plus class="mr-2 size-4" /> Terminal
                        </Button>
                    </div>
                </div>

                <div class="space-y-3">
                    <div
                        v-for="(node, nodeIndex) in nodes"
                        :key="`node-${nodeIndex}`"
                        class="rounded-md border border-border bg-card p-3"
                    >
                        <div class="grid gap-3 sm:grid-cols-[1fr,180px,auto]">
                            <Input v-model="node.id" placeholder="node-id" />
                            <select
                                v-model="node.type"
                                :class="nativeControlClass"
                                @change="normalizeNodeForType(node)"
                            >
                                <option value="DecisionNode">
                                    DecisionNode
                                </option>
                                <option value="ChanceNode">ChanceNode</option>
                                <option value="TerminalNode">
                                    TerminalNode
                                </option>
                            </select>
                            <Button
                                variant="outline"
                                size="icon"
                                :disabled="nodes.length <= 1"
                                @click="removeNode(nodeIndex)"
                            >
                                <Trash2 class="size-4" />
                            </Button>
                        </div>

                        <div
                            v-if="node.type === 'DecisionNode'"
                            class="mt-3 space-y-2"
                        >
                            <p
                                class="text-xs font-medium uppercase tracking-wide text-muted-foreground"
                            >
                                Decision Options
                            </p>
                            <div
                                v-for="(
                                    option, optionIndex
                                ) in node.decisionOptions"
                                :key="`decision-${nodeIndex}-${optionIndex}`"
                                class="grid gap-2 rounded-md border border-border bg-background p-2 md:grid-cols-[1fr,1fr,1fr,auto]"
                            >
                                <select
                                    v-model="option.to"
                                    :class="nativeControlClass"
                                >
                                    <option value="">To node</option>
                                    <option
                                        v-for="refNode in nodeReferences"
                                        :key="`decision-to-${nodeIndex}-${optionIndex}-${refNode.id}`"
                                        :value="refNode.id"
                                    >
                                        {{ refNode.label }}
                                    </option>
                                </select>
                                <Input
                                    v-model="option.guardVar"
                                    placeholder="Guard var (optional)"
                                />
                                <Input
                                    v-model="option.guardEquals"
                                    placeholder="Guard equals (optional)"
                                />
                                <Button
                                    variant="ghost"
                                    size="icon"
                                    @click="
                                        removeDecisionOption(node, optionIndex)
                                    "
                                >
                                    <Trash2 class="size-4" />
                                </Button>
                            </div>
                            <Button
                                variant="outline"
                                size="sm"
                                @click="addDecisionOption(node)"
                            >
                                <Plus class="mr-2 size-4" /> Add Option
                            </Button>
                        </div>

                        <div
                            v-if="node.type === 'ChanceNode'"
                            class="mt-3 space-y-2"
                        >
                            <p
                                class="text-xs font-medium uppercase tracking-wide text-muted-foreground"
                            >
                                Chance Options
                            </p>
                            <div
                                v-for="(
                                    option, optionIndex
                                ) in node.chanceOptions"
                                :key="`chance-${nodeIndex}-${optionIndex}`"
                                class="grid gap-2 rounded-md border border-border bg-background p-2 md:grid-cols-[1fr,180px,auto]"
                            >
                                <select
                                    v-model="option.to"
                                    :class="nativeControlClass"
                                >
                                    <option value="">To node</option>
                                    <option
                                        v-for="refNode in nodeReferences"
                                        :key="`chance-to-${nodeIndex}-${optionIndex}-${refNode.id}`"
                                        :value="refNode.id"
                                    >
                                        {{ refNode.label }}
                                    </option>
                                </select>
                                <Input
                                    v-model="option.weight"
                                    inputmode="decimal"
                                    placeholder="Weight (e.g. 0.5)"
                                />
                                <Button
                                    variant="ghost"
                                    size="icon"
                                    @click="
                                        removeChanceOption(node, optionIndex)
                                    "
                                >
                                    <Trash2 class="size-4" />
                                </Button>
                            </div>
                            <Button
                                variant="outline"
                                size="sm"
                                @click="addChanceOption(node)"
                            >
                                <Plus class="mr-2 size-4" /> Add Option
                            </Button>
                        </div>

                        <div
                            v-if="node.type === 'TerminalNode'"
                            class="mt-3 space-y-2"
                        >
                            <p
                                class="text-xs font-medium uppercase tracking-wide text-muted-foreground"
                            >
                                Outcome
                            </p>
                            <Input
                                v-model="node.outcome"
                                placeholder="terminal outcome label"
                            />
                        </div>
                    </div>
                </div>
            </div>

            <div class="rounded-lg border border-border bg-muted/30 p-3">
                <div class="mb-3 flex items-center justify-between gap-2">
                    <p class="text-sm font-semibold">Edges</p>
                    <Button size="sm" variant="outline" @click="addEdge">
                        <Plus class="mr-2 size-4" /> Add Edge
                    </Button>
                </div>
                <div class="space-y-2">
                    <div
                        v-for="(edge, edgeIndex) in edges"
                        :key="`edge-${edgeIndex}`"
                        class="grid gap-2 rounded-md border border-border bg-background p-2 md:grid-cols-[1fr,1fr,auto]"
                    >
                        <select v-model="edge.from" :class="nativeControlClass">
                            <option value="">From node</option>
                            <option
                                v-for="refNode in nodeReferences"
                                :key="`edge-from-${edgeIndex}-${refNode.id}`"
                                :value="refNode.id"
                            >
                                {{ refNode.label }}
                            </option>
                        </select>
                        <select v-model="edge.to" :class="nativeControlClass">
                            <option value="">To node</option>
                            <option
                                v-for="refNode in nodeReferences"
                                :key="`edge-to-${edgeIndex}-${refNode.id}`"
                                :value="refNode.id"
                            >
                                {{ refNode.label }}
                            </option>
                        </select>
                        <Button
                            variant="ghost"
                            size="icon"
                            @click="removeEdge(edgeIndex)"
                        >
                            <Trash2 class="size-4" />
                        </Button>
                    </div>
                </div>
            </div>

            <details
                class="rounded-md border border-border bg-muted/30 p-2 text-xs"
            >
                <summary class="cursor-pointer font-semibold">
                    Generated Graph Payload (Read-only)
                </summary>
                <pre class="mt-2 max-h-72 overflow-auto whitespace-pre-wrap">{{
                    formatPayload(graphPayload)
                }}</pre>
            </details>
        </CardContent>
    </Card>
</template>
