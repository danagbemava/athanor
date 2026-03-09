<script setup lang="ts">
import { CircleAlert, Play, Save, ShieldCheck } from "lucide-vue-next";
import { Alert, AlertDescription, AlertTitle } from "@/components/ui/alert";
import { Button } from "@/components/ui/button";
import {
    Card,
    CardContent,
    CardDescription,
    CardHeader,
    CardTitle,
} from "@/components/ui/card";
import { Input } from "@/components/ui/input";

const props = withDefaults(
    defineProps<{
        title?: string;
        description?: string;
        showLoadSample?: boolean;
        compact?: boolean;
    }>(),
    {
        title: "Quick Actions",
        description:
            "Create drafts, version scenarios, and run validation checks.",
        showLoadSample: true,
        compact: false,
    },
);

const {
    scenarioName,
    scenarioDescription,
    scenarioId,
    simulationRunCount,
    graphValidationIssues,
    isSaving,
    isValidating,
    isSimulating,
    canCreate,
    canSaveVersion,
    canValidate,
    canSimulate,
    createScenario,
    runSimulation,
    saveNewVersion,
    validateScenario,
} = useScenarioStudio();

const { loadExampleCanvasGraph } = useScenarioCanvas();

const simulationAgents = computed(() => {
    const runCount = Number.isFinite(simulationRunCount.value)
        ? Math.max(1, Math.trunc(simulationRunCount.value))
        : 1;
    const agentCount = Math.min(5, Math.max(3, Math.ceil(runCount / 8)));

    return Array.from({ length: agentCount }, (_, index) => ({
        id: `agent-${index}`,
        duration: 1.8 + index * 0.35,
        delay: index * 0.18,
        hue: index % 2 === 0 ? "bg-sky-400/90" : "bg-emerald-400/90",
    }));
});
</script>

<template>
    <Card class="border-border bg-card shadow-sm">
        <CardHeader>
            <CardTitle class="text-lg">{{ props.title }}</CardTitle>
            <CardDescription>{{ props.description }}</CardDescription>
        </CardHeader>
        <CardContent :class="props.compact ? 'space-y-3' : 'space-y-4'">
            <div
                :class="
                    props.compact
                        ? 'grid gap-3 xl:grid-cols-[minmax(0,1.1fr)_minmax(0,1fr)_minmax(0,1.2fr)]'
                        : 'grid gap-4 sm:grid-cols-2'
                "
            >
                <div class="space-y-2">
                    <p
                        class="text-xs font-medium uppercase tracking-wide text-muted-foreground"
                    >
                        Scenario Name
                    </p>
                    <Input
                        v-model="scenarioName"
                        placeholder="Campaign Decision Tree"
                    />
                </div>
                <div class="space-y-2">
                    <p
                        class="text-xs font-medium uppercase tracking-wide text-muted-foreground"
                    >
                        Scenario ID
                    </p>
                    <Input
                        v-model="scenarioId"
                        placeholder="Existing scenario ID for versioning"
                    />
                </div>

                <div class="space-y-2">
                    <p
                        class="text-xs font-medium uppercase tracking-wide text-muted-foreground"
                    >
                        Description
                    </p>
                    <Input
                        v-model="scenarioDescription"
                        placeholder="High-level context for this scenario"
                    />
                </div>
            </div>

            <div
                :class="
                    props.compact
                        ? 'flex flex-wrap items-end gap-3 rounded-lg border border-border/70 bg-muted/20 px-3 py-2'
                        : 'grid gap-3 sm:grid-cols-[minmax(0,1fr)_auto]'
                "
            >
                <div class="space-y-2">
                    <p
                        class="text-xs font-medium uppercase tracking-wide text-muted-foreground"
                    >
                        Run Count
                    </p>
                    <Input
                        v-model.number="simulationRunCount"
                        type="number"
                        min="1"
                        max="250"
                        class="max-w-32"
                    />
                </div>
                <Button
                    variant="outline"
                    :disabled="!canSimulate"
                    @click="runSimulation"
                >
                    <Play class="mr-2 size-4" />
                    {{ isSimulating ? "Running simulations..." : "Run Simulation" }}
                </Button>
            </div>

            <div
                v-if="isSimulating"
                class="rounded-lg border border-sky-500/30 bg-sky-500/5 px-3 py-3"
            >
                <div
                    class="flex flex-wrap items-center justify-between gap-3 border-b border-sky-500/10 pb-2"
                >
                    <div>
                        <p
                            class="text-xs font-medium uppercase tracking-[0.24em] text-sky-200/70"
                        >
                            Simulation In Progress
                        </p>
                        <p class="mt-1 text-sm text-foreground">
                            Executing {{ simulationRunCount }} runs with
                            <span class="font-semibold">random-v1</span>
                        </p>
                    </div>
                    <div
                        class="rounded-full border border-sky-400/20 bg-sky-500/10 px-2.5 py-1 text-xs text-sky-100"
                    >
                        Results will appear automatically
                    </div>
                </div>

                <div class="mt-3 space-y-2">
                    <div
                        v-for="agent in simulationAgents"
                        :key="agent.id"
                        class="relative h-7 overflow-hidden rounded-full border border-border/60 bg-background/70"
                    >
                        <div
                            class="absolute inset-y-1 left-2 right-2 rounded-full bg-[linear-gradient(90deg,rgba(56,189,248,0.06),rgba(56,189,248,0.14),rgba(16,185,129,0.08))]"
                        />
                        <div
                            class="simulation-agent absolute top-1/2 size-3 -translate-y-1/2 rounded-full shadow-[0_0_18px_rgba(56,189,248,0.35)]"
                            :class="agent.hue"
                            :style="{
                                animationDuration: `${agent.duration}s`,
                                animationDelay: `${agent.delay}s`,
                            }"
                        />
                    </div>
                </div>
            </div>

            <div
                :class="
                    props.compact
                        ? 'flex flex-wrap items-center gap-2 border-t border-border/70 pt-3'
                        : 'flex flex-wrap gap-2'
                "
            >
                <Button :disabled="!canCreate" @click="createScenario">
                    <Save class="mr-2 size-4" />
                    {{ isSaving ? "Saving..." : "Create Draft" }}
                </Button>
                <Button
                    variant="secondary"
                    :disabled="!canSaveVersion"
                    @click="saveNewVersion"
                >
                    {{ isSaving ? "Saving..." : "Save New Version" }}
                </Button>
                <Button
                    variant="outline"
                    :disabled="!canValidate"
                    @click="validateScenario"
                >
                    <ShieldCheck class="mr-2 size-4" />
                    {{ isValidating ? "Validating..." : "Validate Latest" }}
                </Button>
                <Button
                    v-if="props.showLoadSample"
                    variant="ghost"
                    @click="loadExampleCanvasGraph"
                >
                    Load Sample Builder Graph
                </Button>
            </div>

            <Alert v-if="graphValidationIssues.length" variant="destructive">
                <CircleAlert class="size-4" />
                <AlertTitle>Graph Builder Issues</AlertTitle>
                <AlertDescription>
                    <ul class="list-disc space-y-1 pl-5">
                        <li
                            v-for="issue in graphValidationIssues.slice(0, 4)"
                            :key="issue"
                        >
                            {{ issue }}
                        </li>
                    </ul>
                </AlertDescription>
            </Alert>
        </CardContent>
    </Card>
</template>

<style scoped>
@keyframes simulation-agent-lane {
    0% {
        transform: translate(0, -50%) scale(0.92);
        opacity: 0.7;
    }

    50% {
        transform: translate(calc(100% - 2.5rem), -50%) scale(1);
        opacity: 1;
    }

    100% {
        transform: translate(0, -50%) scale(0.92);
        opacity: 0.7;
    }
}

.simulation-agent {
    animation-name: simulation-agent-lane;
    animation-timing-function: ease-in-out;
    animation-iteration-count: infinite;
    left: 0.6rem;
}
</style>
