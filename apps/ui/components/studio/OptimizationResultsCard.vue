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
