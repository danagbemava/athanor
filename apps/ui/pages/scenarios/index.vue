<script setup lang="ts">
import { CircleAlert } from "lucide-vue-next";
import { Alert, AlertDescription, AlertTitle } from "@/components/ui/alert";
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
import QuickActionsCard from "@/components/studio/QuickActionsCard.vue";

const router = useRouter();
const {
    portfolioQuery,
    filteredPortfolioRows,
    statusBadgeVariant,
    riskBadgeVariant,
    formatTimestamp,
    requestError,
    selectScenario,
} = useScenarioStudio();

function openScenario(scenarioId: string) {
    router.push(`/scenarios/${scenarioId}`);
}
</script>

<template>
    <Card class="border-border bg-card shadow-sm">
        <CardHeader>
            <div class="flex flex-wrap items-center justify-between gap-2">
                <div>
                    <CardTitle class="text-2xl">Scenario Portfolio</CardTitle>
                    <CardDescription
                        >Track ownership, risk, release status, and run quality
                        by scenario.</CardDescription
                    >
                </div>
                <Input
                    v-model="portfolioQuery"
                    class="w-full sm:w-72"
                    placeholder="Search scenario or owner"
                />
            </div>
        </CardHeader>
        <CardContent>
            <div class="overflow-x-auto">
                <table class="w-full min-w-[820px] text-left text-sm">
                    <thead
                        class="text-xs uppercase tracking-wide text-muted-foreground"
                    >
                        <tr>
                            <th class="px-2 py-2">Scenario</th>
                            <th class="px-2 py-2">Owner</th>
                            <th class="px-2 py-2">Status</th>
                            <th class="px-2 py-2">Pass</th>
                            <th class="px-2 py-2">Risk</th>
                            <th class="px-2 py-2">Versions</th>
                            <th class="px-2 py-2">Updated</th>
                            <th class="px-2 py-2" />
                        </tr>
                    </thead>
                    <tbody>
                        <tr
                            v-for="row in filteredPortfolioRows"
                            :key="row.scenarioId"
                            class="border-t border-border"
                        >
                            <td class="px-2 py-2">
                                <p class="font-medium">{{ row.name }}</p>
                                <p class="text-xs text-muted-foreground">
                                    {{ row.scenarioId }}
                                </p>
                            </td>
                            <td class="px-2 py-2">{{ row.owner }}</td>
                            <td class="px-2 py-2">
                                <Badge
                                    :variant="statusBadgeVariant(row.status)"
                                    >{{ row.status }}</Badge
                                >
                            </td>
                            <td class="px-2 py-2">{{ row.passRate }}%</td>
                            <td class="px-2 py-2">
                                <Badge :variant="riskBadgeVariant(row.risk)">{{
                                    row.risk
                                }}</Badge>
                            </td>
                            <td class="px-2 py-2">{{ row.versions }}</td>
                            <td class="px-2 py-2 text-xs text-muted-foreground">
                                {{ formatTimestamp(row.updatedAt) }}
                            </td>
                            <td class="px-2 py-2 text-right">
                                <div
                                    class="flex items-center justify-end gap-1"
                                >
                                    <Button
                                        size="sm"
                                        variant="ghost"
                                        @click="selectScenario(row)"
                                        >Select</Button
                                    >
                                    <Button
                                        size="sm"
                                        variant="outline"
                                        @click="openScenario(row.scenarioId)"
                                        >Open</Button
                                    >
                                </div>
                            </td>
                        </tr>
                    </tbody>
                </table>
            </div>
        </CardContent>
    </Card>

    <Alert v-if="requestError" variant="destructive">
        <CircleAlert class="size-4" />
        <AlertTitle>API Error</AlertTitle>
        <AlertDescription>{{ requestError }}</AlertDescription>
    </Alert>

    <QuickActionsCard
        title="Portfolio Actions"
        description="Operate selected scenarios without leaving the portfolio view."
    />
</template>
