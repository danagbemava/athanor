<script setup lang="ts">
import GraphComposerCard from "@/components/studio/GraphComposerCard.vue";
import QuickActionsCard from "@/components/studio/QuickActionsCard.vue";
import SimulationResultsCard from "@/components/studio/SimulationResultsCard.vue";
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
        <SimulationResultsCard />
        <GraphComposerCard />
    </section>
</template>
