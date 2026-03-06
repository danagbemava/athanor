<script setup lang="ts">
import { CircleAlert, Save, ShieldCheck } from "lucide-vue-next";
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
    graphValidationIssues,
    isSaving,
    isValidating,
    canCreate,
    canSaveVersion,
    canValidate,
    createScenario,
    saveNewVersion,
    validateScenario,
} = useScenarioStudio();

const { loadExampleCanvasGraph } = useScenarioCanvas();
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
