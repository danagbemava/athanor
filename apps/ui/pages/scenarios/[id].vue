<script setup lang="ts">
import { computed, watchEffect } from "vue";
import { useRoute, useRouter } from "vue-router";
import { ArrowLeft, CircleAlert } from "lucide-vue-next";
import { Alert, AlertDescription, AlertTitle } from "@/components/ui/alert";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import {
    Card,
    CardContent,
    CardDescription,
    CardHeader,
    CardTitle,
} from "@/components/ui/card";
import QuickActionsCard from "@/components/studio/QuickActionsCard.vue";

const route = useRoute();
const router = useRouter();

const {
    portfolioRows,
    validationHistory,
    formatTimestamp,
    statusBadgeVariant,
    riskBadgeVariant,
    selectScenario,
    requestError,
    scenarioResponse,
    validationResponse,
    formatPayload,
} = useScenarioStudio();

const scenarioIdParam = computed(() => String(route.params.id || ""));
const scenarioRow = computed(() =>
    portfolioRows.value.find((row) => row.scenarioId === scenarioIdParam.value),
);
const scenarioRuns = computed(() =>
    validationHistory.value.filter(
        (run) => run.snapshot.scenarioId === scenarioIdParam.value,
    ),
);

watchEffect(() => {
    if (scenarioRow.value) {
        selectScenario(scenarioRow.value);
    }
});
</script>

<template>
    <section class="flex items-center justify-between gap-2">
        <div>
            <h1 class="text-2xl font-semibold">Scenario Detail</h1>
            <p class="text-sm text-muted-foreground">
                Operational view for a single scenario.
            </p>
        </div>
        <Button variant="outline" @click="router.push('/scenarios')">
            <ArrowLeft class="mr-2 size-4" /> Back to Portfolio
        </Button>
    </section>

    <Card v-if="!scenarioRow" class="border-border bg-card shadow-sm">
        <CardHeader>
            <CardTitle>Scenario Not Found</CardTitle>
            <CardDescription
                >No portfolio scenario matched ID:
                {{ scenarioIdParam }}</CardDescription
            >
        </CardHeader>
    </Card>

    <template v-else>
        <section class="grid gap-4 xl:grid-cols-[1.2fr,1fr]">
            <Card class="border-border bg-card shadow-sm">
                <CardHeader>
                    <CardTitle class="text-xl">{{
                        scenarioRow.name
                    }}</CardTitle>
                    <CardDescription>{{
                        scenarioRow.scenarioId
                    }}</CardDescription>
                </CardHeader>
                <CardContent class="grid gap-2 text-sm sm:grid-cols-2">
                    <div
                        class="rounded-md border border-border bg-muted/25 p-3"
                    >
                        <p
                            class="text-xs uppercase tracking-wide text-muted-foreground"
                        >
                            Owner
                        </p>
                        <p class="pt-1 font-medium">{{ scenarioRow.owner }}</p>
                    </div>
                    <div
                        class="rounded-md border border-border bg-muted/25 p-3"
                    >
                        <p
                            class="text-xs uppercase tracking-wide text-muted-foreground"
                        >
                            Status
                        </p>
                        <Badge
                            :variant="statusBadgeVariant(scenarioRow.status)"
                            >{{ scenarioRow.status }}</Badge
                        >
                    </div>
                    <div
                        class="rounded-md border border-border bg-muted/25 p-3"
                    >
                        <p
                            class="text-xs uppercase tracking-wide text-muted-foreground"
                        >
                            Risk
                        </p>
                        <Badge :variant="riskBadgeVariant(scenarioRow.risk)">{{
                            scenarioRow.risk
                        }}</Badge>
                    </div>
                    <div
                        class="rounded-md border border-border bg-muted/25 p-3"
                    >
                        <p
                            class="text-xs uppercase tracking-wide text-muted-foreground"
                        >
                            Pass Rate
                        </p>
                        <p class="pt-1 font-medium">
                            {{ scenarioRow.passRate }}%
                        </p>
                    </div>
                    <div
                        class="rounded-md border border-border bg-muted/25 p-3"
                    >
                        <p
                            class="text-xs uppercase tracking-wide text-muted-foreground"
                        >
                            Versions
                        </p>
                        <p class="pt-1 font-medium">
                            {{ scenarioRow.versions }}
                        </p>
                    </div>
                    <div
                        class="rounded-md border border-border bg-muted/25 p-3"
                    >
                        <p
                            class="text-xs uppercase tracking-wide text-muted-foreground"
                        >
                            Last Updated
                        </p>
                        <p class="pt-1 font-medium">
                            {{ formatTimestamp(scenarioRow.updatedAt) }}
                        </p>
                    </div>
                </CardContent>
            </Card>

            <Card class="border-border bg-card shadow-sm">
                <CardHeader>
                    <CardTitle class="text-lg">Validation Runs</CardTitle>
                    <CardDescription
                        >Recent validation outcomes for this
                        scenario.</CardDescription
                    >
                </CardHeader>
                <CardContent class="space-y-2">
                    <div
                        v-if="scenarioRuns.length === 0"
                        class="rounded-md border border-dashed border-border bg-muted/25 p-3 text-sm text-muted-foreground"
                    >
                        No validation runs tracked yet.
                    </div>
                    <div
                        v-for="run in scenarioRuns"
                        :key="`${run.recordedAt}-${run.snapshot.versionId}`"
                        class="rounded-md border border-border bg-muted/25 p-3 text-sm"
                    >
                        <div class="flex items-center justify-between">
                            <p class="font-medium">
                                Version {{ run.snapshot.versionNumber }}
                            </p>
                            <Badge
                                :variant="
                                    run.snapshot.valid
                                        ? 'secondary'
                                        : 'destructive'
                                "
                            >
                                {{ run.snapshot.valid ? "Valid" : "Invalid" }}
                            </Badge>
                        </div>
                        <p class="pt-1 text-xs text-muted-foreground">
                            {{ run.snapshot.errors.length }} errors ·
                            {{ run.snapshot.warnings.length }} warnings ·
                            {{ formatTimestamp(run.recordedAt) }}
                        </p>
                    </div>
                </CardContent>
            </Card>
        </section>

        <QuickActionsCard
            title="Scenario Actions"
            description="Run create/version/validate for the currently selected scenario context."
            :show-load-sample="false"
        />

        <Alert v-if="requestError" variant="destructive">
            <CircleAlert class="size-4" />
            <AlertTitle>API Error</AlertTitle>
            <AlertDescription>{{ requestError }}</AlertDescription>
        </Alert>

        <section class="grid gap-4 xl:grid-cols-2">
            <details
                v-if="validationResponse"
                class="rounded-md border border-border bg-card p-3 text-xs"
            >
                <summary class="cursor-pointer font-semibold">
                    Raw Validation Payload
                </summary>
                <pre class="mt-2 max-h-64 overflow-auto whitespace-pre-wrap">{{
                    formatPayload(validationResponse)
                }}</pre>
            </details>

            <details
                v-if="scenarioResponse"
                class="rounded-md border border-border bg-card p-3 text-xs"
            >
                <summary class="cursor-pointer font-semibold">
                    Raw Scenario Payload
                </summary>
                <pre class="mt-2 max-h-64 overflow-auto whitespace-pre-wrap">{{
                    formatPayload(scenarioResponse)
                }}</pre>
            </details>
        </section>
    </template>
</template>
