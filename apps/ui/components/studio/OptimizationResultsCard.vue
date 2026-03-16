<script setup lang="ts">
import { computed } from "vue";
import { FlaskConical, Sparkles, Target } from "lucide-vue-next";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import {
  Card,
  CardContent,
  CardDescription,
  CardHeader,
  CardTitle,
} from "@/components/ui/card";
import { Input } from "@/components/ui/input";
import { Textarea } from "@/components/ui/textarea";

const {
  isOptimizing,
  optimizationTargetJson,
  optimizationMaxIterations,
  optimizationRunsPerIteration,
  optimizationJob,
  optimizationProgress,
  canOptimize,
  canApplyOptimization,
  runOptimization,
  applyOptimization,
  simulationStatusTone,
  formatTimestamp,
} = useScenarioStudio();

const sortedTargetDistribution = computed(() =>
  Object.entries(optimizationJob.value?.targetDistribution ?? {}).sort(
    (left, right) => right[1] - left[1],
  ),
);

const sortedBestDistribution = computed(() =>
  Object.entries(optimizationJob.value?.bestOutcomeDistribution ?? {}).sort(
    (left, right) => right[1] - left[1],
  ),
);

const tunedNodes = computed(
  () => optimizationJob.value?.bestParameters?.chanceWeights ?? [],
);

const optimizationAgents = computed(() => {
  const maxIterations = Math.max(
    1,
    optimizationJob.value?.maxIterations ?? optimizationMaxIterations.value ?? 1,
  );
  const agentCount = Math.min(5, Math.max(3, Math.ceil(maxIterations / 3)));
  const progress = Math.max(0, Math.min(100, optimizationProgress.value));

  return Array.from({ length: agentCount }, (_, index) => ({
    id: `optimization-agent-${index}`,
    hue: index % 2 === 0 ? "bg-amber-400/90" : "bg-rose-400/90",
    left: `${Math.max(0, progress - index * 4)}%`,
    opacity: progress > 0 ? Math.max(0.3, 1 - index * 0.16) : 0.22,
  }));
});
</script>

<template>
  <Card class="border-border bg-card shadow-sm">
    <CardHeader>
      <CardTitle class="text-lg">Optimization Loop</CardTitle>
      <CardDescription>
        Tune chance-node weights toward a target outcome distribution using the
        Phase 1 random-search optimizer.
      </CardDescription>
    </CardHeader>
    <CardContent class="space-y-4">
      <div class="grid gap-4 xl:grid-cols-[minmax(0,1.15fr)_minmax(0,0.85fr)]">
        <div class="space-y-2">
          <p class="text-xs font-medium uppercase tracking-wide text-muted-foreground">
            Target Distribution
          </p>
          <Textarea
            v-model="optimizationTargetJson"
            class="min-h-40 font-mono text-xs"
            placeholder='{"approved":0.6,"declined":0.4}'
          />
        </div>

        <div class="space-y-4 rounded-lg border border-border/70 bg-muted/20 p-4">
          <div class="grid gap-3 sm:grid-cols-2">
            <div class="space-y-2">
              <p class="text-xs font-medium uppercase tracking-wide text-muted-foreground">
                Max Iterations
              </p>
              <Input
                v-model.number="optimizationMaxIterations"
                type="number"
                min="1"
                max="250"
              />
            </div>
            <div class="space-y-2">
              <p class="text-xs font-medium uppercase tracking-wide text-muted-foreground">
                Runs / Iteration
              </p>
              <Input
                v-model.number="optimizationRunsPerIteration"
                type="number"
                min="10"
                max="5000"
              />
            </div>
          </div>

          <Button
            variant="outline"
            class="w-full"
            :disabled="!canOptimize"
            @click="runOptimization"
          >
            <FlaskConical class="mr-2 size-4" />
            {{ isOptimizing ? "Optimizing..." : "Run Optimization" }}
          </Button>

          <Button
            class="w-full"
            :disabled="!canApplyOptimization"
            @click="applyOptimization"
          >
            <Sparkles class="mr-2 size-4" />
            Apply Best Parameters
          </Button>
        </div>
      </div>

      <div v-if="optimizationJob" class="grid gap-3 md:grid-cols-4">
        <div class="rounded-lg border border-border/70 bg-muted/20 p-3">
          <p class="text-xs uppercase tracking-wide text-muted-foreground">
            Status
          </p>
          <div class="mt-2">
            <Badge :variant="simulationStatusTone(optimizationJob.status)">
              {{ optimizationJob.status }}
            </Badge>
          </div>
        </div>
        <div class="rounded-lg border border-border/70 bg-muted/20 p-3">
          <p class="text-xs uppercase tracking-wide text-muted-foreground">
            Progress
          </p>
          <p class="mt-2 text-2xl font-semibold">{{ optimizationProgress }}%</p>
        </div>
        <div class="rounded-lg border border-border/70 bg-muted/20 p-3">
          <p class="text-xs uppercase tracking-wide text-muted-foreground">
            Best Score
          </p>
          <p class="mt-2 text-2xl font-semibold">
            {{ optimizationJob.bestScore.toFixed(3) }}
          </p>
        </div>
        <div class="rounded-lg border border-border/70 bg-muted/20 p-3">
          <p class="text-xs uppercase tracking-wide text-muted-foreground">
            Iterations
          </p>
          <p class="mt-2 text-sm font-medium">
            {{ optimizationJob.iterationsCompleted }} /
            {{ optimizationJob.maxIterations }}
          </p>
          <p
            v-if="optimizationJob.completedAt"
            class="mt-1 text-xs text-muted-foreground"
          >
            {{ formatTimestamp(optimizationJob.completedAt) }}
          </p>
        </div>
      </div>

      <div
        v-if="optimizationJob"
        class="overflow-hidden rounded-xl border border-border/70 bg-muted/20 p-4"
      >
        <div class="flex items-start justify-between gap-3">
          <div>
            <p class="text-xs font-medium uppercase tracking-[0.24em] text-muted-foreground">
              Optimization Pulse
            </p>
            <p class="mt-2 text-sm text-foreground/80">
              Tracking candidate evaluations across {{ optimizationJob.maxIterations }}
              iterations.
            </p>
          </div>
          <Badge :variant="simulationStatusTone(optimizationJob.status)">
            {{ optimizationJob.iterationsCompleted }} / {{ optimizationJob.maxIterations }}
          </Badge>
        </div>

        <div class="mt-4 space-y-3">
          <div class="relative h-4 overflow-hidden rounded-full bg-background/80 ring-1 ring-border/70">
            <div
              class="absolute inset-y-0 left-0 rounded-full bg-gradient-to-r from-amber-400 via-orange-400 to-rose-500 transition-[width] duration-500 ease-out"
              :style="{ width: `${optimizationProgress}%` }"
            />
            <div
              v-for="agent in optimizationAgents"
              :key="agent.id"
              class="absolute top-1/2 size-2.5 -translate-y-1/2 rounded-full shadow-[0_0_0_4px_rgba(251,191,36,0.12)] transition-[left,opacity] duration-500 ease-out"
              :class="agent.hue"
              :style="{
                left: `calc(0.4rem + (100% - 1.35rem) * ${agent.left} / 100)`,
                opacity: agent.opacity,
              }"
            />
          </div>

          <div class="flex flex-wrap items-center justify-between gap-2 text-xs text-muted-foreground">
            <span>{{ optimizationProgress }}% complete</span>
            <span>
              {{ optimizationJob.iterationsCompleted }} of
              {{ optimizationJob.maxIterations }} iterations
            </span>
            <span>Best score {{ optimizationJob.bestScore.toFixed(3) }}</span>
          </div>
        </div>
      </div>

      <div v-if="optimizationJob" class="grid gap-4 xl:grid-cols-3">
        <div class="space-y-3 rounded-lg border border-border/70 bg-muted/20 p-4">
          <div class="flex items-center gap-2">
            <Target class="size-4 text-muted-foreground" />
            <h3 class="text-sm font-semibold">Target vs Best</h3>
          </div>
          <div class="space-y-2 text-sm">
            <div
              v-for="[outcome, target] in sortedTargetDistribution"
              :key="`target-${outcome}`"
              class="rounded-lg border border-border/60 bg-background/70 px-3 py-2"
            >
              <div class="flex items-center justify-between gap-3">
                <span class="font-medium">{{ outcome }}</span>
                <span class="text-muted-foreground">
                  target {{ target.toFixed(3) }}
                </span>
              </div>
              <p class="mt-1 text-xs text-muted-foreground">
                best {{ (optimizationJob.bestOutcomeDistribution[outcome] ?? 0).toFixed(3) }}
              </p>
            </div>
          </div>
        </div>

        <div class="space-y-3 rounded-lg border border-border/70 bg-muted/20 p-4">
          <h3 class="text-sm font-semibold">Best Outcome Distribution</h3>
          <div class="space-y-2 text-sm">
            <div
              v-for="[outcome, rate] in sortedBestDistribution"
              :key="`best-${outcome}`"
              class="flex items-center justify-between gap-3 rounded-lg border border-border/60 bg-background/70 px-3 py-2"
            >
              <span class="font-medium">{{ outcome }}</span>
              <span class="text-muted-foreground">{{ rate.toFixed(3) }}</span>
            </div>
          </div>
        </div>

        <div class="space-y-3 rounded-lg border border-border/70 bg-muted/20 p-4">
          <h3 class="text-sm font-semibold">Tuned Nodes</h3>
          <div class="space-y-3 text-sm">
            <div
              v-for="node in tunedNodes"
              :key="node.nodeId"
              class="rounded-lg border border-border/60 bg-background/70 p-3"
            >
              <p class="font-medium">{{ node.nodeId }}</p>
              <div class="mt-2 space-y-1 text-xs text-muted-foreground">
                <div
                  v-for="option in node.options"
                  :key="`${node.nodeId}-${option.to}`"
                  class="flex items-center justify-between gap-3"
                >
                  <span>{{ option.to }}</span>
                  <span>{{ option.weight.toFixed(3) }}</span>
                </div>
              </div>
            </div>
            <p v-if="tunedNodes.length === 0" class="text-sm text-muted-foreground">
              Best parameters will appear after the optimizer evaluates the first candidate.
            </p>
          </div>
        </div>
      </div>
    </CardContent>
  </Card>
</template>
