<script setup lang="ts">
import { BarChart3, CircleAlert, ShieldCheck } from "lucide-vue-next";
import { Badge } from "@/components/ui/badge";
import {
    Card,
    CardContent,
    CardDescription,
    CardHeader,
    CardTitle,
} from "@/components/ui/card";
import MetricCard from "@/components/studio/MetricCard.vue";

const {
    validationRunCount,
    overallPassRate,
    openIssues,
    validationTrend,
    validationHistory,
    latestValidation,
    formatTimestamp,
} = useScenarioStudio();
</script>

<template>
    <section class="grid gap-4 xl:grid-cols-[1.45fr,1fr]">
        <Card class="border-border bg-card shadow-sm">
            <CardHeader>
                <CardTitle class="text-2xl">Validation Analytics</CardTitle>
                <CardDescription
                    >Quality trend, run outcomes, and issue
                    concentration.</CardDescription
                >
            </CardHeader>
        </Card>

        <Card class="border-border bg-card shadow-sm">
            <CardHeader>
                <CardTitle class="text-lg">Latest Validation</CardTitle>
            </CardHeader>
            <CardContent class="text-sm">
                <div
                    v-if="latestValidation"
                    class="rounded-md border border-border bg-muted/25 p-3"
                >
                    <div class="flex items-center justify-between">
                        <p class="font-medium">
                            {{ latestValidation.scenarioId }}
                        </p>
                        <Badge
                            :variant="
                                latestValidation.valid
                                    ? 'secondary'
                                    : 'destructive'
                            "
                        >
                            {{ latestValidation.valid ? "Valid" : "Invalid" }}
                        </Badge>
                    </div>
                    <p class="pt-1 text-xs text-muted-foreground">
                        Version {{ latestValidation.versionNumber }} ·
                        {{ latestValidation.errors.length }} errors ·
                        {{ latestValidation.warnings.length }} warnings
                    </p>
                </div>
                <p v-else class="text-muted-foreground">
                    No validation runs yet.
                </p>
            </CardContent>
        </Card>
    </section>

    <section class="grid gap-4 sm:grid-cols-3">
        <MetricCard
            label="Validation Runs"
            :value="validationRunCount"
            :icon="BarChart3"
        />
        <MetricCard
            label="Pass Rate"
            :value="`${overallPassRate}%`"
            :icon="ShieldCheck"
        />
        <MetricCard
            label="Open Issues"
            :value="openIssues"
            :icon="CircleAlert"
        />
    </section>

    <section class="grid gap-4 xl:grid-cols-[1fr,1.4fr]">
        <Card class="border-border bg-card shadow-sm">
            <CardHeader>
                <CardTitle class="text-lg">Validation Trend</CardTitle>
                <CardDescription
                    >Normalized health score for the latest 7
                    runs.</CardDescription
                >
            </CardHeader>
            <CardContent>
                <div
                    class="flex h-44 items-end gap-2 rounded-md border border-border bg-muted/30 p-3"
                >
                    <div
                        v-for="(point, index) in validationTrend"
                        :key="`analytics-trend-${index}`"
                        class="flex-1 rounded-t bg-foreground/80"
                        :style="{ height: `${Math.max(point, 8)}%` }"
                    />
                </div>
            </CardContent>
        </Card>

        <Card class="border-border bg-card shadow-sm">
            <CardHeader>
                <CardTitle class="text-lg">Run History</CardTitle>
                <CardDescription
                    >Recent validation executions and outcomes.</CardDescription
                >
            </CardHeader>
            <CardContent class="space-y-2">
                <div
                    v-if="validationHistory.length === 0"
                    class="rounded-md border border-dashed border-border bg-muted/25 p-4 text-sm text-muted-foreground"
                >
                    No validation history yet.
                </div>
                <div
                    v-for="run in validationHistory"
                    :key="`${run.recordedAt}-${run.snapshot.versionId}`"
                    class="rounded-md border border-border bg-muted/25 p-3"
                >
                    <div class="flex items-center justify-between gap-2">
                        <div>
                            <p class="text-sm font-medium">
                                {{ run.snapshot.scenarioId }}
                            </p>
                            <p class="text-xs text-muted-foreground">
                                {{ formatTimestamp(run.recordedAt) }}
                            </p>
                        </div>
                        <Badge
                            :variant="
                                run.snapshot.valid ? 'secondary' : 'destructive'
                            "
                        >
                            {{ run.snapshot.valid ? "Valid" : "Invalid" }}
                        </Badge>
                    </div>
                    <p class="pt-1 text-xs text-muted-foreground">
                        Version {{ run.snapshot.versionNumber }} ·
                        {{ run.snapshot.errors.length }} errors ·
                        {{ run.snapshot.warnings.length }} warnings
                    </p>
                </div>
            </CardContent>
        </Card>
    </section>
</template>
