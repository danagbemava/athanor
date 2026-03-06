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
        "w-56 rounded-2xl border px-4 py-3 shadow-lg transition-all select-none backdrop-blur";

    if (props.type === "decision") {
        return `${base} border-sky-500/60 bg-sky-500/10`;
    }
    if (props.type === "chance") {
        return `${base} border-amber-500/60 bg-amber-500/10`;
    }
    return `${base} border-emerald-500/60 bg-emerald-500/10`;
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
            class="!size-4 !border-2 !border-background !bg-muted"
            :connectable="props.connectable"
        />

        <Handle
            v-if="props.type !== 'terminal'"
            type="source"
            :position="Position.Right"
            class="!size-4 !border-2 !border-background !bg-sky-500"
            :connectable="props.data.connectable"
        />

        <div class="mb-3 flex items-start justify-between gap-2">
            <div>
                <p class="text-sm font-semibold">
                    {{ props.data.title }}
                </p>
                <p class="text-xs text-muted-foreground">
                    {{ props.id }}
                </p>
            </div>
            <Grip class="mt-0.5 size-4 text-muted-foreground" />
        </div>

        <Badge variant="outline">
            {{
                props.type === "decision"
                    ? "DecisionNode"
                    : props.type === "chance"
                      ? "ChanceNode"
                      : "TerminalNode"
            }}
        </Badge>

        <div class="mt-3 space-y-2 text-xs text-muted-foreground">
            <p>{{ props.data.detail }}</p>
            <p>
                {{ props.data.description || "Click to edit inspector details." }}
            </p>
            <p
                v-if="props.data.connectable && props.type !== 'terminal'"
                class="inline-flex items-center gap-1 text-sky-300"
            >
                <Target class="size-3" />
                Drag from the right handle to connect
            </p>
        </div>
    </div>
</template>
