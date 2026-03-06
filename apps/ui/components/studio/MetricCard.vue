<script setup lang="ts">
import type { Component } from "vue";
import { Card, CardContent } from "@/components/ui/card";

defineProps<{
    label: string;
    value: string | number;
    hint?: string;
    icon: Component;
}>();
</script>

<template>
    <Card class="border-border bg-card shadow-sm">
        <CardContent class="flex items-center justify-between p-4">
            <div>
                <p
                    class="text-xs font-medium uppercase tracking-wide text-muted-foreground"
                >
                    {{ label }}
                </p>
                <p class="pt-1 text-2xl font-semibold">{{ value }}</p>
                <p v-if="hint" class="pt-1 text-xs text-muted-foreground">
                    {{ hint }}
                </p>
            </div>
            <component :is="icon" class="size-5 text-muted-foreground" />
        </CardContent>
    </Card>
</template>
