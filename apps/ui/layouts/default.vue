<script setup lang="ts">
import {
    BarChart3,
    FolderKanban,
    LayoutDashboard,
    Monitor,
    Moon,
    Sun,
    Workflow,
} from "lucide-vue-next";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { useScenarioStudioState } from "@/composables/scenario-studio/shared-state";
import { useValidation } from "@/composables/scenario-studio/useValidation";
import { useScenarioGraph } from "@/composables/scenario-studio/useScenarioGraph";

const route = useRoute();
const state = useScenarioStudioState();
const graph = useScenarioGraph(state);
const { openIssues } = useValidation(state, graph);
const { statusNote } = state;
const { themeMode, resolvedTheme, setThemeMode } = useThemeMode();

const themeOptions = [
    { value: "light", label: "Light", icon: Sun },
    { value: "dark", label: "Dark", icon: Moon },
    { value: "system", label: "System", icon: Monitor },
] as const;

const navItems = [
    { to: "/dashboard", label: "Dashboard", icon: LayoutDashboard },
    { to: "/scenarios", label: "Scenarios", icon: FolderKanban },
    { to: "/analytics", label: "Analytics", icon: BarChart3 },
    { to: "/builder", label: "Composer", icon: Workflow },
] as const;

const pageMeta = computed(() => {
    const exact =
        navItems.find((item) => route.path === item.to) ??
        navItems.find((item) => route.path.startsWith(`${item.to}/`));

    if (exact?.to === "/dashboard") {
        return {
            eyebrow: "Overview",
            title: "Scenario Studio Dashboard",
            description:
                "Portfolio health, validation signal, and recent operational activity.",
        };
    }

    if (exact?.to === "/scenarios") {
        return {
            eyebrow: "Portfolio",
            title: "Scenario Portfolio",
            description:
                "Track ownership, risk, release status, and version history across scenarios.",
        };
    }

    if (exact?.to === "/analytics") {
        return {
            eyebrow: "Analytics",
            title: "Simulation Analytics",
            description:
                "Telemetry-backed outcomes, step distribution, and sampled traces for the active scenario.",
        };
    }

    if (exact?.to === "/builder") {
        return {
            eyebrow: "Composer",
            title: "Scenario Composer",
            description:
                "Author graphs, validate drafts, and run async simulation workflows.",
        };
    }

    return {
        eyebrow: "Workspace",
        title: "Athanor",
        description: "Scenario modeling, validation, and simulation workspace.",
    };
});

function isActive(to: string): boolean {
    return route.path === to || route.path.startsWith(`${to}/`);
}
</script>

<template>
    <div class="min-h-screen bg-muted/30 text-foreground">
        <aside
            class="hidden lg:fixed lg:inset-y-0 lg:left-0 lg:z-40 lg:flex lg:w-72 lg:flex-col lg:border-r lg:border-border lg:bg-background"
        >
            <div class="flex h-16 items-center justify-between border-b border-border px-6">
                <div>
                    <p class="text-xs font-medium uppercase tracking-[0.14em] text-muted-foreground">
                        Athanor
                    </p>
                    <p class="text-base font-semibold">Scenario Studio</p>
                </div>
                <Badge variant="outline">v0</Badge>
            </div>

            <div class="flex flex-1 flex-col px-4 py-6">
                <nav class="space-y-1.5">
                    <NuxtLink
                        v-for="item in navItems"
                        :key="item.to"
                        :to="item.to"
                        class="flex items-center gap-3 rounded-lg px-3 py-2.5 text-sm transition-colors"
                        :class="
                            isActive(item.to)
                                ? 'bg-primary text-primary-foreground shadow-sm'
                                : 'text-muted-foreground hover:bg-muted hover:text-foreground'
                        "
                    >
                        <component :is="item.icon" class="size-4" />
                        <span class="font-medium">{{ item.label }}</span>
                    </NuxtLink>
                </nav>

                <div class="mt-8 rounded-xl border border-border bg-muted/35 p-4">
                    <div>
                        <p class="text-xs font-medium uppercase tracking-wide text-muted-foreground">
                            Workspace status
                        </p>
                        <p class="pt-2 text-sm font-medium">{{ statusNote }}</p>
                        <p class="pt-1 text-xs text-muted-foreground">
                            Open issues: {{ openIssues }}
                        </p>
                    </div>
                </div>

                <div
                    class="mt-auto rounded-xl border border-border bg-muted/35 p-4"
                >
                    <p class="text-xs font-medium uppercase tracking-wide text-muted-foreground">
                        Theme
                    </p>
                    <div class="mt-3 grid grid-cols-3 gap-1.5">
                        <Button
                            v-for="option in themeOptions"
                            :key="option.value"
                            size="sm"
                            :variant="
                                themeMode === option.value
                                    ? 'secondary'
                                    : 'ghost'
                            "
                            class="h-8 justify-center gap-1 px-2"
                            @click="setThemeMode(option.value)"
                        >
                            <component :is="option.icon" class="size-3.5" />
                            <span class="text-[11px]">{{ option.label }}</span>
                        </Button>
                    </div>
                    <p class="pt-2 text-[11px] text-muted-foreground">
                        Active: {{ resolvedTheme }}
                    </p>
                </div>
            </div>
        </aside>

        <div class="lg:pl-72">
            <header
                class="sticky top-0 z-30 border-b border-border bg-background/90 backdrop-blur"
            >
                <div
                    class="mx-auto flex max-w-7xl flex-col gap-4 px-4 py-4 sm:px-6 lg:px-8"
                >
                    <div class="flex items-start justify-between gap-4">
                        <div class="space-y-1">
                            <p
                                class="text-xs font-medium uppercase tracking-[0.14em] text-muted-foreground"
                            >
                                {{ pageMeta.eyebrow }}
                            </p>
                            <div>
                                <h1 class="text-2xl font-semibold tracking-tight">
                                    {{ pageMeta.title }}
                                </h1>
                                <p class="text-sm text-muted-foreground">
                                    {{ pageMeta.description }}
                                </p>
                            </div>
                        </div>

                        <div class="hidden items-center gap-2 md:flex">
                            <Badge variant="outline">{{ statusNote }}</Badge>
                            <Badge variant="secondary">{{ openIssues }} open issues</Badge>
                        </div>
                    </div>

                    <nav class="flex gap-2 overflow-x-auto lg:hidden">
                        <NuxtLink
                            v-for="item in navItems"
                            :key="`mobile-${item.to}`"
                            :to="item.to"
                            class="flex shrink-0 items-center gap-2 rounded-lg border px-3 py-2 text-sm transition-colors"
                            :class="
                                isActive(item.to)
                                    ? 'border-primary bg-primary text-primary-foreground'
                                    : 'border-border bg-card text-muted-foreground hover:text-foreground'
                            "
                        >
                            <component :is="item.icon" class="size-4" />
                            {{ item.label }}
                        </NuxtLink>
                    </nav>
                </div>
            </header>

            <main class="mx-auto max-w-7xl px-4 py-6 sm:px-6 lg:px-8">
                <div class="space-y-6">
                    <slot />
                </div>
            </main>
        </div>
    </div>
</template>
