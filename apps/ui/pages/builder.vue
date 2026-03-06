<script setup lang="ts">
import GraphComposerCard from "@/components/studio/GraphComposerCard.vue";
import QuickActionsCard from "@/components/studio/QuickActionsCard.vue";
import {
    Card,
    CardDescription,
    CardHeader,
    CardTitle,
} from "@/components/ui/card";
</script>

<template>
    <Card class="border-border bg-card shadow-sm">
        <CardHeader>
            <CardTitle class="text-2xl">Scenario Composer</CardTitle>
            <CardDescription>
                Build the underlying graph model and execute draft/version
                workflows.
            </CardDescription>
        </CardHeader>
    </Card>

    <section class="space-y-4">
        <QuickActionsCard
            title="Composer Actions"
            description="Run create, version, and validate actions against the active graph model."
            compact
        />
        <GraphComposerCard />
    </section>
</template>
