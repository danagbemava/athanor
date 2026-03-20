<script setup lang="ts">
import { computed } from "vue";
import { Grip, Target } from "lucide-vue-next";
import { Badge } from "@/components/ui/badge";
import { Handle, Position, type NodeProps } from "@vue-flow/core";

type ScenarioNodeData = {
    title: string;
    description: string;
    detail: string;
    selected: boolean;
    connectable: boolean;
};

const props = defineProps<NodeProps<ScenarioNodeData>>();

const nodeClasses = computed(() => {
    const base =
        "w-56 rounded-2xl border px-4 py-3 text-slate-950 shadow-lg transition-all select-none backdrop-blur dark:text-slate-50";

    if (props.type === "decision") {
        return `${base} border-sky-500/45 bg-white/92 shadow-sky-200/60 dark:border-sky-500/60 dark:bg-sky-500/10 dark:shadow-sky-950/30`;
    }
    if (props.type === "chance") {
        return `${base} border-amber-500/45 bg-white/92 shadow-amber-200/60 dark:border-amber-500/60 dark:bg-amber-500/10 dark:shadow-amber-950/30`;
    }
    return `${base} border-emerald-500/45 bg-white/92 shadow-emerald-200/60 dark:border-emerald-500/60 dark:bg-emerald-500/10 dark:shadow-emerald-950/30`;
});
</script>

<template>
    <div
        :class="[
            nodeClasses,
            props.selected || props.data.selected
                ? 'ring-2 ring-sky-500 ring-offset-2 ring-offset-background'
                : '',
        ]"
    >
        <Handle
            type="target"
            :position="Position.Left"
            class="!size-4 !border-2 !border-white !bg-slate-200 dark:!border-slate-950 dark:!bg-slate-600"
            :connectable="props.connectable"
        />

        <Handle
            v-if="props.type !== 'terminal'"
            type="source"
            :position="Position.Right"
            class="!size-4 !border-2 !border-white !bg-sky-500 dark:!border-slate-950"
            :connectable="props.data.connectable"
        />

        <div class="mb-3 flex items-start justify-between gap-2">
            <div>
                <p class="text-sm font-semibold">
                    {{ props.data.title }}
                </p>
                <p class="text-xs text-slate-500 dark:text-slate-400">
                    {{ props.id }}
                </p>
            </div>
            <Grip class="mt-0.5 size-4 text-slate-400 dark:text-slate-500" />
        </div>

        <Badge
            variant="outline"
            class="border-slate-300/80 bg-slate-50/90 text-slate-700 dark:border-slate-700 dark:bg-slate-950/40 dark:text-slate-200"
        >
            {{
                props.type === "decision"
                    ? "DecisionNode"
                    : props.type === "chance"
                      ? "ChanceNode"
                      : "TerminalNode"
            }}
        </Badge>

        <div class="mt-3 space-y-2 text-xs text-slate-600 dark:text-slate-300">
            <p>{{ props.data.detail }}</p>
            <p>
                {{ props.data.description || "Click to edit inspector details." }}
            </p>
            <p
                v-if="props.data.connectable && props.type !== 'terminal'"
                class="inline-flex items-center gap-1 text-sky-600 dark:text-sky-300"
            >
                <Target class="size-3" />
                Drag from the right handle to connect
            </p>
        </div>
    </div>
</template>
