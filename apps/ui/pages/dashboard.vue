<script setup lang="ts">
import {
    Activity,
    BarChart3,
    CircleAlert,
    ListTree,
    Save,
    ShieldCheck,
} from "lucide-vue-next";
import { Alert, AlertDescription, AlertTitle } from "@/components/ui/alert";
import { Badge } from "@/components/ui/badge";
import {
    Card,
    CardContent,
    CardDescription,
    CardHeader,
    CardTitle,
} from "@/components/ui/card";
import MetricCard from "@/components/studio/MetricCard.vue";
import QuickActionsCard from "@/components/studio/QuickActionsCard.vue";

const {
    totalScenarios,
    activeDrafts,
    avgVersions,
    overallPassRate,
    openIssues,
    validationRunCount,
    validationTrend,
    feedItems,
    requestError,
    filteredPortfolioRows,
    formatTimestamp,
    statusBadgeVariant,
    riskBadgeVariant,
    selectScenario,
} = useScenarioStudio();
</script>

<template>
    <section class="grid gap-4 xl:grid-cols-[1.45fr,1fr]">
        <Card class="border-border bg-card shadow-sm">
            <CardHeader>
                <Badge
                    variant="outline"
                    class="w-fit px-3 py-1 text-[11px] uppercase tracking-[0.12em]"
                >
                    Product Dashboard
                </Badge>
                <CardTitle
                    class="pt-2 text-balance text-3xl font-semibold md:text-4xl"
                >
                    Athanor Scenario Studio
                </CardTitle>
                <CardDescription class="max-w-3xl text-sm">
                    Portfolio-level control for scenario lifecycle, validation
                    quality, and operational execution.
                </CardDescription>
            </CardHeader>
        </Card>

        <Card class="border-border bg-card shadow-sm">
            <CardHeader class="pb-2">
                <CardTitle class="text-lg">Portfolio Snapshot</CardTitle>
                <CardDescription
                    >Current scenario estate across teams and risk
                    bands.</CardDescription
                >
            </CardHeader>
            <CardContent class="space-y-2 text-sm">
                <div
                    class="flex items-center justify-between rounded-md border border-border bg-muted/30 px-3 py-2"
                >
                    <span class="text-muted-foreground"
                        >Scenarios in Portfolio</span
                    >
                    <span class="font-semibold">{{ totalScenarios }}</span>
                </div>
                <div
                    class="flex items-center justify-between rounded-md border border-border bg-muted/30 px-3 py-2"
                >
                    <span class="text-muted-foreground">Active Drafts</span>
                    <span class="font-semibold">{{ activeDrafts }}</span>
                </div>
                <div
                    class="flex items-center justify-between rounded-md border border-border bg-muted/30 px-3 py-2"
                >
                    <span class="text-muted-foreground"
                        >Open Validation Issues</span
                    >
                    <span class="font-semibold">{{ openIssues }}</span>
                </div>
            </CardContent>
        </Card>
    </section>

    <section class="grid gap-4 sm:grid-cols-2 xl:grid-cols-5">
        <MetricCard
            label="Scenarios"
            :value="totalScenarios"
            :icon="ListTree"
        />
        <MetricCard label="Drafts" :value="activeDrafts" :icon="Activity" />
        <MetricCard label="Avg Versions" :value="avgVersions" :icon="Save" />
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

    <section class="grid gap-4 xl:grid-cols-[1.55fr,1fr]">
        <Card class="border-border bg-card shadow-sm">
            <CardHeader>
                <CardTitle class="text-lg">Scenario Highlights</CardTitle>
                <CardDescription
                    >Most recent scenarios by update timestamp.</CardDescription
                >
            </CardHeader>
            <CardContent class="space-y-2">
                <div
                    v-for="row in filteredPortfolioRows.slice(0, 6)"
                    :key="row.scenarioId"
                    class="flex flex-wrap items-center justify-between gap-2 rounded-md border border-border bg-muted/25 px-3 py-2"
                >
                    <div>
                        <p class="text-sm font-medium">{{ row.name }}</p>
                        <p class="text-xs text-muted-foreground">
                            {{ row.scenarioId }} · {{ row.owner }}
                        </p>
                    </div>
                    <div class="flex items-center gap-2">
                        <Badge :variant="statusBadgeVariant(row.status)">{{
                            row.status
                        }}</Badge>
                        <Badge :variant="riskBadgeVariant(row.risk)">{{
                            row.risk
                        }}</Badge>
                        <button
                            class="text-xs font-medium text-foreground underline-offset-2 hover:underline"
                            @click="selectScenario(row)"
                        >
                            Select
                        </button>
                    </div>
                </div>
            </CardContent>
        </Card>

        <section class="flex flex-col gap-4">
            <Card class="border-border bg-card shadow-sm">
                <CardHeader>
                    <CardTitle class="text-lg">Validation Signal</CardTitle>
                    <CardDescription
                        >Recent quality trend across validation
                        runs.</CardDescription
                    >
                </CardHeader>
                <CardContent>
                    <div
                        class="flex h-36 items-end gap-2 rounded-md border border-border bg-muted/30 p-3"
                    >
                        <div
                            v-for="(point, index) in validationTrend"
                            :key="`trend-${index}`"
                            class="flex-1 rounded-t bg-foreground/80"
                            :style="{ height: `${Math.max(point, 8)}%` }"
                        />
                    </div>
                    <div
                        class="mt-2 flex items-center justify-between text-xs text-muted-foreground"
                    >
                        <span>7-run trend</span>
                        <span>{{ validationRunCount }} tracked runs</span>
                    </div>
                </CardContent>
            </Card>

            <Card class="border-border bg-card shadow-sm">
                <CardHeader>
                    <CardTitle class="text-lg">Activity Feed</CardTitle>
                    <CardDescription
                        >Recent portfolio and run operations.</CardDescription
                    >
                </CardHeader>
                <CardContent class="space-y-2">
                    <div
                        v-for="event in feedItems"
                        :key="event.id"
                        class="rounded-md border border-border bg-muted/25 p-3"
                    >
                        <div class="flex items-center justify-between gap-2">
                            <p class="text-sm font-medium">{{ event.title }}</p>
                            <Badge :variant="event.tone">{{
                                formatTimestamp(event.at)
                            }}</Badge>
                        </div>
                        <p class="pt-1 text-xs text-muted-foreground">
                            {{ event.detail }}
                        </p>
                    </div>
                </CardContent>
            </Card>
        </section>
    </section>

    <Alert v-if="requestError" variant="destructive">
        <CircleAlert class="size-4" />
        <AlertTitle>API Error</AlertTitle>
        <AlertDescription>{{ requestError }}</AlertDescription>
    </Alert>

    <QuickActionsCard />
</template>
