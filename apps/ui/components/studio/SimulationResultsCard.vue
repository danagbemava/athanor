<script setup lang="ts">
import { computed, ref, watch } from "vue";
import { ChevronLeft, ChevronRight, GitBranch, Route, Sparkles } from "lucide-vue-next";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import {
    Card,
    CardContent,
    CardDescription,
    CardHeader,
    CardTitle,
} from "@/components/ui/card";

const {
    isSimulating,
    simulationJob,
    simulationProgress,
    simulationResponse,
    simulationRunCount,
    formatTimestamp,
    formatPayload,
    simulationStatusTone,
} = useScenarioStudio();

const sortedOutcomes = computed(() => {
    const counts = simulationResponse.value?.outcomeCounts ?? {};
    return Object.entries(counts).sort((left, right) => right[1] - left[1]);
});

const tracePageSize = 8;
const selectedRunSeed = ref<string>("");
const selectedStepIndex = ref(0);
const selectedRunPage = ref(0);

const traceableRuns = computed(() => {
    return simulationResponse.value?.runs ?? [];
});

const selectedRun = computed(() => {
    const runs = traceableRuns.value;
    if (runs.length === 0) {
        return null;
    }

    return (
        runs.find((run) => String(run.seed) === selectedRunSeed.value) ?? runs[0]
    );
});
const traceRunPageCount = computed(() =>
    Math.max(1, Math.ceil(traceableRuns.value.length / tracePageSize)),
);
const paginatedTraceableRuns = computed(() => {
    const start = selectedRunPage.value * tracePageSize;
    return traceableRuns.value.slice(start, start + tracePageSize);
});
const paginatedTraceRunLabel = computed(() => {
    if (traceableRuns.value.length === 0) {
        return "0-0";
    }

    const start = selectedRunPage.value * tracePageSize + 1;
    const end = Math.min(
        traceableRuns.value.length,
        (selectedRunPage.value + 1) * tracePageSize,
    );
    return `${start}-${end}`;
});

const selectedTrace = computed(() => selectedRun.value?.trace ?? []);
const selectedTraceStep = computed(() => {
    if (selectedTrace.value.length === 0) {
        return null;
    }
    const index = Math.min(
        Math.max(selectedStepIndex.value, 0),
        selectedTrace.value.length - 1,
    );
    return selectedTrace.value[index] ?? null;
});
const selectedTraceStatusLabel = computed(() => {
    const step = selectedTraceStep.value;
    if (!step) {
        return "No trace available";
    }

    if (step.outcome) {
        return step.outcome;
    }

    const run = selectedRun.value;
    if (!run) {
        return "completed-step";
    }

    const isTerminalStep = selectedStepIndex.value >= selectedTrace.value.length - 1;
    if (isTerminalStep) {
        return run.outcome;
    }

    return "completed-step";
});

watch(
    traceableRuns,
    (runs) => {
        selectedRunSeed.value = runs[0] ? String(runs[0].seed) : "";
        selectedRunPage.value = 0;
        selectedStepIndex.value = 0;
    },
    { immediate: true },
);

watch(selectedRunSeed, (nextSeed) => {
    selectedStepIndex.value = 0;

    const selectedIndex = traceableRuns.value.findIndex(
        (run) => String(run.seed) === nextSeed,
    );
    if (selectedIndex >= 0) {
        selectedRunPage.value = Math.floor(selectedIndex / tracePageSize);
    }
});

function nextTraceStep() {
    if (selectedTrace.value.length === 0) {
        return;
    }
    selectedStepIndex.value = Math.min(
        selectedStepIndex.value + 1,
        selectedTrace.value.length - 1,
    );
}

function previousTraceStep() {
    selectedStepIndex.value = Math.max(selectedStepIndex.value - 1, 0);
}

function nextTraceRunPage() {
    selectedRunPage.value = Math.min(
        selectedRunPage.value + 1,
        traceRunPageCount.value - 1,
    );
}

function previousTraceRunPage() {
    selectedRunPage.value = Math.max(selectedRunPage.value - 1, 0);
}

function selectedTraceLabel() {
    const step = selectedTraceStep.value;
    if (!step) {
        return "No trace available";
    }
    return `Step ${step.step + 1} · ${step.nodeId}`;
}
</script>

<template>
    <Card class="border-border bg-card shadow-sm">
        <CardHeader>
            <CardTitle class="text-lg">Simulation Snapshot</CardTitle>
            <CardDescription>
                Async runs against the latest compiled version of the active
                scenario, with live queue and progress updates.
            </CardDescription>
        </CardHeader>
        <CardContent v-if="isSimulating" class="space-y-4">
            <div class="grid gap-3 md:grid-cols-4">
                <div class="rounded-lg border border-border/70 bg-muted/20 p-3">
                    <p class="text-xs uppercase tracking-wide text-muted-foreground">
                        Run ID
                    </p>
                    <p class="mt-2 text-sm font-semibold">
                        {{ simulationJob?.runId?.slice(0, 12) ?? "pending" }}
                    </p>
                </div>
                <div class="rounded-lg border border-border/70 bg-muted/20 p-3">
                    <p class="text-xs uppercase tracking-wide text-muted-foreground">
                        Status
                    </p>
                    <div class="mt-2">
                        <Badge :variant="simulationStatusTone(simulationJob?.status ?? 'queued')">
                            {{ simulationJob?.status ?? "queued" }}
                        </Badge>
                    </div>
                </div>
                <div class="rounded-lg border border-border/70 bg-muted/20 p-3">
                    <p class="text-xs uppercase tracking-wide text-muted-foreground">
                        Progress
                    </p>
                    <p class="mt-2 text-2xl font-semibold">
                        {{ simulationProgress }}%
                    </p>
                </div>
                <div class="rounded-lg border border-border/70 bg-muted/20 p-3">
                    <p class="text-xs uppercase tracking-wide text-muted-foreground">
                        Runs
                    </p>
                    <p class="mt-2 text-sm font-medium">
                        {{ simulationJob?.completedRuns ?? 0 }} /
                        {{ simulationJob?.totalRuns ?? simulationRunCount }}
                    </p>
                </div>
            </div>

            <div class="grid gap-4 xl:grid-cols-[minmax(0,0.9fr)_minmax(0,1.1fr)]">
                <div class="space-y-3 rounded-lg border border-border/70 bg-muted/20 p-4">
                    <div class="flex items-center justify-between gap-2">
                        <h3 class="text-sm font-semibold">Outcome Distribution</h3>
                        <Badge :variant="simulationStatusTone(simulationJob?.status ?? 'queued')">
                            {{ simulationJob?.completedRuns ?? 0 }}/{{ simulationJob?.totalRuns ?? simulationRunCount }}
                        </Badge>
                    </div>
                    <div class="space-y-2">
                        <div class="flex items-center justify-between gap-3 text-sm">
                            <span class="font-medium text-muted-foreground">
                                Batch completion
                            </span>
                            <span class="text-muted-foreground">
                                {{ simulationProgress }}%
                            </span>
                        </div>
                        <div class="h-2 rounded-full bg-muted">
                            <div
                                class="h-2 rounded-full bg-primary transition-all"
                                :style="{ width: `${simulationProgress}%` }"
                            />
                        </div>
                    </div>
                    <div class="space-y-2">
                        <div
                            v-for="index in 3"
                            :key="`pending-outcome-${index}`"
                            class="space-y-2"
                        >
                            <div class="flex items-center justify-between gap-3 text-sm">
                                <div class="h-4 w-24 animate-pulse rounded bg-muted/80" />
                                <div class="h-4 w-8 animate-pulse rounded bg-muted/80" />
                            </div>
                            <div class="h-2 animate-pulse rounded-full bg-muted/80" />
                        </div>
                    </div>
                </div>

                <div class="space-y-3 rounded-lg border border-border/70 bg-muted/20 p-4">
                    <div class="flex items-center justify-between gap-2">
                        <h3 class="text-sm font-semibold">Trace Explorer</h3>
                        <Badge variant="secondary">
                            attempt {{ simulationJob?.attempts ?? 0 }}
                        </Badge>
                    </div>
                    <p class="text-sm text-muted-foreground">
                        Traces will populate once the worker completes the queued
                        simulation batch.
                    </p>
                    <div class="space-y-2">
                        <div
                            v-for="index in 3"
                            :key="`pending-run-${index}`"
                            class="rounded-lg border border-border/70 bg-background/70 p-3"
                        >
                            <div class="flex items-center justify-between gap-3">
                                <div class="h-4 w-20 animate-pulse rounded bg-muted/80" />
                                <div class="h-5 w-16 animate-pulse rounded bg-muted/80" />
                            </div>
                            <div class="mt-2 h-4 w-40 animate-pulse rounded bg-muted/80" />
                            <div class="mt-2 h-16 animate-pulse rounded-md bg-muted/80" />
                        </div>
                    </div>
                </div>
            </div>
        </CardContent>
        <CardContent v-else-if="simulationResponse" class="space-y-4">
            <div class="grid gap-3 md:grid-cols-4">
                <div class="rounded-lg border border-border/70 bg-muted/20 p-3">
                    <p class="text-xs uppercase tracking-wide text-muted-foreground">
                        Runs
                    </p>
                    <p class="mt-2 text-2xl font-semibold">
                        {{ simulationResponse.runCount }}
                    </p>
                </div>
                <div class="rounded-lg border border-border/70 bg-muted/20 p-3">
                    <p class="text-xs uppercase tracking-wide text-muted-foreground">
                        Avg Steps
                    </p>
                    <p class="mt-2 text-2xl font-semibold">
                        {{ simulationResponse.averageSteps.toFixed(2) }}
                    </p>
                </div>
                <div class="rounded-lg border border-border/70 bg-muted/20 p-3">
                    <p class="text-xs uppercase tracking-wide text-muted-foreground">
                        Agent
                    </p>
                    <p class="mt-2 text-lg font-semibold">
                        {{ simulationResponse.agentVersion }}
                    </p>
                </div>
                <div class="rounded-lg border border-border/70 bg-muted/20 p-3">
                    <p class="text-xs uppercase tracking-wide text-muted-foreground">
                        Completed
                    </p>
                    <p class="mt-2 text-sm font-medium">
                        {{ formatTimestamp(simulationResponse.completedAt) }}
                    </p>
                </div>
            </div>

            <div class="grid gap-4 xl:grid-cols-[minmax(0,0.8fr)_minmax(0,1.2fr)]">
                <div class="space-y-3 rounded-lg border border-border/70 bg-muted/20 p-4">
                    <div class="flex items-center justify-between gap-2">
                        <h3 class="text-sm font-semibold">Outcome Distribution</h3>
                        <Badge variant="outline">{{ sortedOutcomes.length }} outcomes</Badge>
                    </div>
                    <div class="space-y-2">
                        <div
                            v-for="[outcome, count] in sortedOutcomes"
                            :key="outcome"
                            class="space-y-1"
                        >
                            <div class="flex items-center justify-between gap-3 text-sm">
                                <span class="font-medium">{{ outcome }}</span>
                                <span class="text-muted-foreground">{{ count }}</span>
                            </div>
                            <div class="h-2 rounded-full bg-muted">
                                <div
                                    class="h-2 rounded-full bg-primary transition-all"
                                    :style="{
                                        width: `${(count / simulationResponse.runCount) * 100}%`,
                                    }"
                                />
                            </div>
                        </div>
                    </div>
                </div>

                <div class="space-y-4 rounded-lg border border-border/70 bg-muted/20 p-4">
                    <div class="flex items-center justify-between gap-3">
                        <div>
                            <h3 class="text-sm font-semibold">Trace Explorer</h3>
                            <p class="text-sm text-muted-foreground">
                                Walk through each agent run and inspect the path,
                                branch choice, and state changes.
                            </p>
                        </div>
                        <Badge variant="secondary">
                            {{ simulationResponse.bundleHash.slice(0, 12) }}
                        </Badge>
                    </div>

                    <div
                        v-if="traceableRuns.length && selectedRun"
                        class="min-w-0 space-y-4"
                    >
                        <div
                            class="min-w-0 space-y-3 rounded-lg border border-border/70 bg-background/70 p-3"
                        >
                            <div class="flex flex-wrap items-center justify-between gap-3">
                                <p class="text-sm font-semibold">
                                    Seeds {{ paginatedTraceRunLabel }} of
                                    {{ traceableRuns.length }}
                                </p>
                                <div class="flex flex-wrap items-center gap-2">
                                    <Button
                                        variant="outline"
                                        size="sm"
                                        :disabled="selectedRunPage === 0"
                                        @click="previousTraceRunPage"
                                    >
                                        <ChevronLeft class="mr-1 size-4" />
                                        Prev
                                    </Button>
                                    <Button
                                        variant="outline"
                                        size="sm"
                                        :disabled="
                                            selectedRunPage >= traceRunPageCount - 1
                                        "
                                        @click="nextTraceRunPage"
                                    >
                                        Next
                                        <ChevronRight class="ml-1 size-4" />
                                    </Button>
                                </div>
                            </div>

                            <div class="flex gap-2 overflow-x-auto pb-1">
                                <button
                                    v-for="run in paginatedTraceableRuns"
                                    :key="run.seed"
                                    type="button"
                                    class="min-w-32 shrink-0 rounded-lg border px-3 py-2 text-left transition-colors sm:min-w-44"
                                    :class="
                                        String(run.seed) === selectedRunSeed
                                            ? 'border-primary bg-primary/10'
                                            : 'border-border/70 bg-background hover:bg-muted/50'
                                    "
                                    @click="selectedRunSeed = String(run.seed)"
                                >
                                    <p class="text-sm font-semibold">Seed {{ run.seed }}</p>
                                    <p class="mt-1 text-xs text-muted-foreground">
                                        {{ run.outcome }}
                                    </p>
                                    <p class="text-xs text-muted-foreground">
                                        {{ run.stepsTaken }} steps
                                    </p>
                                </button>
                            </div>
                        </div>

                        <div class="min-w-0 space-y-4">
                            <div
                                class="flex flex-wrap items-center justify-between gap-3 rounded-lg border border-border/70 bg-background/70 p-3"
                            >
                                <div class="space-y-1">
                                    <p class="text-sm font-semibold">
                                        Seed {{ selectedRun.seed }} · {{ selectedRun.outcome }}
                                    </p>
                                    <p class="text-sm text-muted-foreground">
                                        {{ selectedRun.stepsTaken }} steps executed
                                    </p>
                                </div>
                                <div class="flex items-center gap-2">
                                    <Button
                                        variant="outline"
                                        size="sm"
                                        :disabled="selectedStepIndex === 0"
                                        @click="previousTraceStep"
                                    >
                                        <ChevronLeft class="mr-1 size-4" />
                                        Prev
                                    </Button>
                                    <Button
                                        variant="outline"
                                        size="sm"
                                        :disabled="
                                            selectedStepIndex >= selectedTrace.length - 1
                                        "
                                        @click="nextTraceStep"
                                    >
                                        Next
                                        <ChevronRight class="ml-1 size-4" />
                                    </Button>
                                </div>
                            </div>

                            <div
                                v-if="selectedTraceStep"
                                class="grid min-w-0 gap-4 xl:grid-cols-[minmax(0,0.85fr)_minmax(0,1.15fr)]"
                            >
                                <div class="min-w-0 space-y-3 rounded-lg border border-border/70 bg-background/70 p-4">
                                    <div class="flex items-center justify-between gap-3">
                                        <div>
                                            <p class="text-sm font-semibold">
                                                {{ selectedTraceLabel() }}
                                            </p>
                                            <p class="text-sm text-muted-foreground">
                                                {{ selectedTraceStep.nodeType }}
                                            </p>
                                        </div>
                                        <Badge variant="outline">
                                            {{ selectedTraceStatusLabel }}
                                        </Badge>
                                    </div>

                                    <div class="space-y-3 text-sm">
                                        <div
                                            class="rounded-lg border border-border/70 bg-muted/20 p-3"
                                        >
                                            <div class="flex items-center gap-2">
                                                <Route class="size-4 text-sky-400" />
                                                <p class="font-medium">Branch Decision</p>
                                            </div>
                                            <p class="mt-2 text-muted-foreground">
                                                <span
                                                    v-if="selectedTraceStep.selectedOption"
                                                >
                                                    Option
                                                    {{
                                                        selectedTraceStep.selectedOption.index + 1
                                                    }}
                                                    routed to
                                                    <span class="font-medium text-foreground">
                                                        {{
                                                            selectedTraceStep.selectedOption.to
                                                        }}
                                                    </span>
                                                </span>
                                                <span v-else>
                                                    No branch selected at this step.
                                                </span>
                                            </p>
                                        </div>

                                        <div
                                            class="rounded-lg border border-border/70 bg-muted/20 p-3"
                                        >
                                            <div class="flex items-center gap-2">
                                                <Sparkles class="size-4 text-emerald-400" />
                                                <p class="font-medium">Effects Applied</p>
                                            </div>
                                            <ul
                                                v-if="
                                                    selectedTraceStep.effectsApplied.length > 0
                                                "
                                                class="mt-2 space-y-2 text-muted-foreground"
                                            >
                                                <li
                                                    v-for="effect in selectedTraceStep.effectsApplied"
                                                    :key="`${effect.op}-${effect.path}`"
                                                >
                                                    <span class="font-medium text-foreground">
                                                        {{ effect.op }}
                                                    </span>
                                                    on
                                                    <span class="font-medium text-foreground">
                                                        {{ effect.path }}
                                                    </span>
                                                </li>
                                            </ul>
                                            <p
                                                v-else
                                                class="mt-2 text-muted-foreground"
                                            >
                                                No effects applied at this node.
                                            </p>
                                        </div>

                                        <div
                                            class="rounded-lg border border-border/70 bg-muted/20 p-3"
                                        >
                                            <div class="flex items-center gap-2">
                                                <GitBranch class="size-4 text-violet-400" />
                                                <p class="font-medium">
                                                    Available Options
                                                </p>
                                            </div>
                                            <ul
                                                v-if="
                                                    selectedTraceStep.availableOptions.length > 0
                                                "
                                                class="mt-2 space-y-2 text-muted-foreground"
                                            >
                                                <li
                                                    v-for="option in selectedTraceStep.availableOptions"
                                                    :key="`${option.index}-${option.to}`"
                                                >
                                                    <span class="font-medium text-foreground">
                                                        Option {{ option.index + 1 }}
                                                    </span>
                                                    to {{ option.to }}
                                                    <span
                                                        v-if="
                                                            option.weight !== null &&
                                                            option.weight !== undefined
                                                        "
                                                    >
                                                        · weight
                                                        {{ option.weight.toFixed(2) }}
                                                    </span>
                                                </li>
                                            </ul>
                                            <p
                                                v-else
                                                class="mt-2 text-muted-foreground"
                                            >
                                                No outgoing options were available.
                                            </p>
                                        </div>
                                    </div>
                                </div>

                                <div class="min-w-0 space-y-3">
                                    <div
                                        class="rounded-lg border border-border/70 bg-background/70 p-4"
                                    >
                                        <p class="text-sm font-semibold">
                                            State Before
                                        </p>
                                        <pre
                                            class="mt-2 overflow-x-auto rounded-md bg-background px-3 py-2 text-xs text-muted-foreground"
                                        ><code>{{ formatPayload(selectedTraceStep.stateBefore) }}</code></pre>
                                    </div>

                                    <div
                                        class="rounded-lg border border-border/70 bg-background/70 p-4"
                                    >
                                        <p class="text-sm font-semibold">
                                            State After
                                        </p>
                                        <pre
                                            class="mt-2 overflow-x-auto rounded-md bg-background px-3 py-2 text-xs text-muted-foreground"
                                        ><code>{{ formatPayload(selectedTraceStep.stateAfter) }}</code></pre>
                                    </div>
                                </div>
                            </div>

                            <div
                                class="grid min-w-0 gap-2 sm:grid-cols-2 xl:grid-cols-3"
                            >
                                <button
                                    v-for="(event, index) in selectedRun.trace"
                                    :key="`${selectedRun.seed}-${index}-${event.nodeId}`"
                                    type="button"
                                    class="rounded-lg border border-border/70 bg-background/70 px-3 py-2 text-left transition hover:border-sky-400/50"
                                    :class="
                                        index === selectedStepIndex
                                            ? 'border-sky-400/70 ring-1 ring-sky-400/40'
                                            : ''
                                    "
                                    @click="selectedStepIndex = index"
                                >
                                    <div class="flex items-center justify-between gap-2">
                                        <span class="text-sm font-medium">
                                            {{ event.nodeId }}
                                        </span>
                                        <Badge variant="outline">
                                            {{ event.step + 1 }}
                                        </Badge>
                                    </div>
                                    <p class="mt-1 text-xs text-muted-foreground">
                                        {{ event.nodeType }}
                                        <span v-if="event.nextNodeId">
                                            → {{ event.nextNodeId }}
                                        </span>
                                    </p>
                                </button>
                            </div>
                        </div>
                    </div>
                </div>
            </div>
        </CardContent>
        <CardContent v-else>
            <p class="text-sm text-muted-foreground">
                No simulation runs yet. Create or select a scenario, then run the
                async simulation action from Composer Actions.
            </p>
        </CardContent>
    </Card>
</template>
