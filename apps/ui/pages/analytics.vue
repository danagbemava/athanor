<script setup lang="ts">
import {
  Activity,
  BarChart3,
  GitBranch,
  Route,
} from "lucide-vue-next";
import { Badge } from "@/components/ui/badge";
import {
  Card,
  CardContent,
  CardDescription,
  CardHeader,
  CardTitle,
} from "@/components/ui/card";
import MetricCard from "@/components/studio/MetricCard.vue";
import { useScenarioStudioState } from "@/composables/scenario-studio/shared-state";
import { useAnalytics } from "@/composables/scenario-studio/useAnalytics";
import { formatTimestamp } from "@/composables/scenario-studio/utils";

const state = useScenarioStudioState();
const {
  scenarioId,
  scenarioName,
} = state;
const { analyticsResponse, isLoadingAnalytics, analyticsError } = useAnalytics(
  state,
);

const outcomeEntries = computed(() =>
  Object.entries(analyticsResponse.value?.outcomeCounts ?? {}),
);
const nodeVisitEntries = computed(() =>
  Object.entries(analyticsResponse.value?.nodeVisitCounts ?? {}).slice(0, 8),
);
const traceSamples = computed(() => analyticsResponse.value?.sampledTraces ?? []);
const hasAnalytics = computed(
  () => (analyticsResponse.value?.runCount ?? 0) > 0,
);
const topOutcomeCount = computed(() =>
  Math.max(1, ...outcomeEntries.value.map(([, count]) => count)),
);
const topNodeVisitCount = computed(() =>
  Math.max(1, ...nodeVisitEntries.value.map(([, count]) => count)),
);
</script>

<template>
  <section class="grid gap-4 xl:grid-cols-[1.45fr,1fr]">
    <div class="rounded-2xl border border-border bg-card px-6 py-5 shadow-sm">
      <p class="text-xs font-medium uppercase tracking-[0.14em] text-muted-foreground">
        Live Telemetry
      </p>
      <h2 class="pt-2 text-2xl font-semibold tracking-tight">
        Scenario analytics from completed simulation batches
      </h2>
      <p class="pt-1 text-sm text-muted-foreground">
        Outcome mix, step distribution, node activity, and sampled traces are
        aggregated from async simulation jobs for the selected scenario.
      </p>
    </div>

    <Card class="border-border bg-card shadow-sm">
      <CardHeader>
        <CardTitle class="text-lg">Selected Scenario</CardTitle>
      </CardHeader>
      <CardContent class="space-y-3 text-sm">
        <div v-if="scenarioId" class="rounded-md border border-border bg-muted/25 p-3">
          <div class="flex items-start justify-between gap-3">
            <div>
              <p class="font-medium">{{ scenarioName }}</p>
              <p class="pt-1 text-xs text-muted-foreground">
                {{ scenarioId }}
              </p>
            </div>
            <Badge variant="outline">
              v{{ analyticsResponse?.latestVersionNumber ?? "?" }}
            </Badge>
          </div>
          <p v-if="analyticsResponse?.lastCompletedAt" class="pt-2 text-xs text-muted-foreground">
            Last batch {{ formatTimestamp(analyticsResponse.lastCompletedAt) }}
          </p>
        </div>
        <p v-else class="text-muted-foreground">
          Select a scenario in Scenario Studio to load live analytics.
        </p>
        <p v-if="analyticsError" class="text-sm text-destructive">
          {{ analyticsError }}
        </p>
      </CardContent>
    </Card>
  </section>

  <section class="grid gap-4 sm:grid-cols-2 xl:grid-cols-4">
    <MetricCard label="Simulation Runs" :value="analyticsResponse?.runCount ?? 0" :icon="BarChart3" />
    <MetricCard label="Batches" :value="analyticsResponse?.batchCount ?? 0" :icon="GitBranch" />
    <MetricCard
      label="Avg Steps"
      :value="Number(analyticsResponse?.averageSteps ?? 0).toFixed(2)"
      :icon="Route"
    />
    <MetricCard label="P90 Steps" :value="analyticsResponse?.p90Steps ?? 0" :icon="Activity" />
  </section>

  <section class="grid gap-4 xl:grid-cols-[1.2fr,1fr]">
    <Card class="border-border bg-card shadow-sm">
      <CardHeader>
        <CardTitle class="text-lg">Outcome Distribution</CardTitle>
        <CardDescription>Aggregated terminal outcomes across all recorded runs.</CardDescription>
      </CardHeader>
      <CardContent class="space-y-3">
        <div
          v-if="isLoadingAnalytics"
          class="rounded-md border border-dashed border-border bg-muted/25 p-4 text-sm text-muted-foreground"
        >
          Loading analytics...
        </div>
        <div
          v-else-if="!hasAnalytics"
          class="rounded-md border border-dashed border-border bg-muted/25 p-4 text-sm text-muted-foreground"
        >
          No simulation telemetry recorded for this scenario yet.
        </div>
        <div
          v-for="[outcome, count] in outcomeEntries"
          :key="outcome"
          class="space-y-2 rounded-md border border-border bg-muted/25 p-3"
        >
          <div class="flex items-center justify-between gap-3 text-sm">
            <p class="font-medium">{{ outcome }}</p>
            <Badge variant="secondary">{{ count }}</Badge>
          </div>
          <div class="h-2 rounded-full bg-muted">
            <div
              class="h-full rounded-full bg-foreground/85"
              :style="{ width: `${(count / topOutcomeCount) * 100}%` }"
            />
          </div>
        </div>
      </CardContent>
    </Card>

    <Card class="border-border bg-card shadow-sm">
      <CardHeader>
        <CardTitle class="text-lg">Node Activity</CardTitle>
        <CardDescription>Most visited nodes across the aggregated traces.</CardDescription>
      </CardHeader>
      <CardContent class="space-y-3">
        <div
          v-if="!hasAnalytics || nodeVisitEntries.length === 0"
          class="rounded-md border border-dashed border-border bg-muted/25 p-4 text-sm text-muted-foreground"
        >
          Node visit counts appear once runs have trace data.
        </div>
        <div
          v-for="[nodeId, count] in nodeVisitEntries"
          :key="nodeId"
          class="space-y-2 rounded-md border border-border bg-muted/25 p-3"
        >
          <div class="flex items-center justify-between gap-3 text-sm">
            <p class="font-medium">{{ nodeId }}</p>
            <Badge variant="outline">{{ count }}</Badge>
          </div>
          <div class="h-2 rounded-full bg-muted">
            <div
              class="h-full rounded-full bg-foreground/70"
              :style="{ width: `${(count / topNodeVisitCount) * 100}%` }"
            />
          </div>
        </div>
      </CardContent>
    </Card>
  </section>

  <section class="grid gap-4 xl:grid-cols-[0.95fr,1.05fr]">
    <Card class="border-border bg-card shadow-sm">
      <CardHeader>
        <CardTitle class="text-lg">Telemetry Envelope</CardTitle>
        <CardDescription>Run metadata retained for the latest analytics snapshot.</CardDescription>
      </CardHeader>
      <CardContent class="space-y-3 text-sm">
        <div class="rounded-md border border-border bg-muted/25 p-3">
          <p class="text-xs uppercase tracking-[0.12em] text-muted-foreground">
            Agent Version
          </p>
          <p class="pt-1 font-medium">
            {{ analyticsResponse?.agentVersion ?? "Not recorded yet" }}
          </p>
        </div>
        <div class="rounded-md border border-border bg-muted/25 p-3">
          <p class="text-xs uppercase tracking-[0.12em] text-muted-foreground">
            Bundle Hash
          </p>
          <p class="break-all pt-1 font-medium">
            {{ analyticsResponse?.latestBundleHash ?? "Not recorded yet" }}
          </p>
        </div>
        <div class="rounded-md border border-border bg-muted/25 p-3">
          <p class="text-xs uppercase tracking-[0.12em] text-muted-foreground">
            Trace Sampling
          </p>
          <p class="pt-1 font-medium">
            {{ analyticsResponse?.sampledTraceCount ?? 0 }} sampled traces at
            {{ analyticsResponse?.traceSampleRate ?? 5 }}%
          </p>
        </div>
      </CardContent>
    </Card>

    <Card class="border-border bg-card shadow-sm">
      <CardHeader>
        <CardTitle class="text-lg">Sampled Traces</CardTitle>
        <CardDescription>Representative traces retained for analytics inspection.</CardDescription>
      </CardHeader>
      <CardContent class="space-y-3">
        <div
          v-if="traceSamples.length === 0"
          class="rounded-md border border-dashed border-border bg-muted/25 p-4 text-sm text-muted-foreground"
        >
          No sampled traces have been retained yet.
        </div>
        <div
          v-for="sample in traceSamples"
          :key="`${sample.seed}-${sample.recordedAt}`"
          class="rounded-md border border-border bg-muted/25 p-3"
        >
          <div class="flex flex-wrap items-center gap-2">
            <Badge variant="outline">Seed {{ sample.seed }}</Badge>
            <Badge variant="secondary">{{ sample.outcome }}</Badge>
            <Badge variant="outline">{{ sample.stepsTaken }} steps</Badge>
          </div>
          <p class="pt-2 text-xs text-muted-foreground">
            {{ sample.trace.map((event) => event.nodeId).join(" -> ") }}
          </p>
        </div>
      </CardContent>
    </Card>
  </section>
</template>
