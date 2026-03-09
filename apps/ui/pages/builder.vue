<script setup lang="ts">
import { computed, ref } from "vue";
import GraphComposerCard from "@/components/studio/GraphComposerCard.vue";
import OptimizationResultsCard from "@/components/studio/OptimizationResultsCard.vue";
import QuickActionsCard from "@/components/studio/QuickActionsCard.vue";
import SimulationResultsCard from "@/components/studio/SimulationResultsCard.vue";

const { optimizationJob, simulationResponse, simulationJob } = useScenarioStudio();

const activeDetailPanel = ref<"simulation" | "optimization">("simulation");

const hasSimulationDetail = computed(
    () => !!simulationResponse.value || !!simulationJob.value,
);

const hasOptimizationDetail = computed(() => !!optimizationJob.value);
</script>

<template>
    <section
        class="rounded-2xl border border-border bg-card px-6 py-5 shadow-sm"
    >
        <p class="text-xs font-medium uppercase tracking-[0.14em] text-muted-foreground">
            Authoring Workspace
        </p>
        <h2 class="pt-2 text-2xl font-semibold tracking-tight">
            Build, validate, and simulate from one screen
        </h2>
        <p class="pt-1 text-sm text-muted-foreground">
            Use the composer to shape the graph, create versions, and inspect
            deterministic run traces without leaving the dashboard shell.
        </p>
    </section>

    <section class="space-y-4">
        <QuickActionsCard
            title="Composer Actions"
            description="Run create, version, and validate actions against the active graph model."
            compact
        />
        <GraphComposerCard />

        <section class="space-y-3 rounded-2xl border border-border bg-card p-4 shadow-sm">
            <div class="flex flex-wrap items-center justify-between gap-3">
                <div>
                    <p class="text-xs font-medium uppercase tracking-[0.14em] text-muted-foreground">
                        Run Insights
                    </p>
                    <h3 class="pt-1 text-lg font-semibold tracking-tight">
                        Simulation and optimization detail
                    </h3>
                    <p class="pt-1 text-sm text-muted-foreground">
                        Keep the graph editor primary and open detail panels only when you need them.
                    </p>
                </div>

                <div class="inline-flex rounded-lg border border-border/70 bg-muted/20 p-1">
                    <button
                        type="button"
                        class="rounded-md px-3 py-1.5 text-sm transition-colors"
                        :class="
                            activeDetailPanel === 'simulation'
                                ? 'bg-background font-medium text-foreground shadow-sm'
                                : 'text-muted-foreground hover:text-foreground'
                        "
                        @click="activeDetailPanel = 'simulation'"
                    >
                        Simulation
                    </button>
                    <button
                        type="button"
                        class="rounded-md px-3 py-1.5 text-sm transition-colors"
                        :class="
                            activeDetailPanel === 'optimization'
                                ? 'bg-background font-medium text-foreground shadow-sm'
                                : 'text-muted-foreground hover:text-foreground'
                        "
                        @click="activeDetailPanel = 'optimization'"
                    >
                        Optimization
                    </button>
                </div>
            </div>

            <div v-if="activeDetailPanel === 'simulation'">
                <SimulationResultsCard />
            </div>
            <div v-else>
                <OptimizationResultsCard />
            </div>

            <div
                v-if="!hasSimulationDetail && !hasOptimizationDetail"
                class="rounded-xl border border-dashed border-border/70 bg-muted/20 px-4 py-6 text-sm text-muted-foreground"
            >
                Run a simulation or optimization job to populate this panel.
            </div>
        </section>
    </section>
</template>
