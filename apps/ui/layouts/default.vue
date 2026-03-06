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

const route = useRoute();
const { statusNote, openIssues } = useScenarioStudio();
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

function isActive(to: string): boolean {
    return route.path === to || route.path.startsWith(`${to}/`);
}
</script>

<template>
    <div class="min-h-screen bg-background text-foreground">
        <div
            class="flex w-full flex-col gap-6 px-4 py-4 md:px-8 md:py-8 lg:flex-row"
        >
            <aside
                class="w-full rounded-xl border border-border bg-card p-4 shadow-sm lg:sticky lg:top-8 lg:h-[calc(100vh-4rem)] lg:w-64"
            >
                <div class="mb-6 flex items-center justify-between">
                    <div>
                        <p class="text-xs font-medium text-muted-foreground">
                            Athanor
                        </p>
                        <p class="text-base font-semibold">Scenario Studio</p>
                    </div>
                    <Badge variant="outline">v0</Badge>
                </div>

                <nav class="space-y-1.5">
                    <NuxtLink
                        v-for="item in navItems"
                        :key="item.to"
                        :to="item.to"
                        class="flex items-center gap-2 rounded-lg border px-3 py-2 text-sm transition-colors"
                        :class="
                            isActive(item.to)
                                ? 'border-border bg-secondary text-foreground'
                                : 'border-transparent text-muted-foreground hover:border-border hover:bg-muted/60 hover:text-foreground'
                        "
                    >
                        <component :is="item.icon" class="size-4" />
                        {{ item.label }}
                    </NuxtLink>
                </nav>

                <div
                    class="mt-6 rounded-lg border border-border bg-muted/30 p-3"
                >
                    <p class="text-xs font-medium text-muted-foreground">
                        Theme
                    </p>
                    <div class="mt-2 grid grid-cols-3 gap-1.5">
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

                <div
                    class="mt-6 rounded-lg border border-border bg-muted/35 p-3 text-xs"
                >
                    <p class="text-muted-foreground">Workspace status</p>
                    <p class="pt-1 font-medium">{{ statusNote }}</p>
                    <p class="pt-1 text-muted-foreground">
                        Open issues: {{ openIssues }}
                    </p>
                </div>
            </aside>

            <div class="min-w-0 flex-1 space-y-4">
                <slot />
            </div>
        </div>
    </div>
</template>
