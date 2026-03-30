import { useRuntimeConfig } from "#imports";
import { computed } from "vue";
import type { BadgeTone } from "@/composables/scenario-studio/types";

export const nativeControlClass =
  "flex h-9 w-full rounded-md border border-input bg-background px-3 py-2 text-sm text-foreground shadow-sm transition-colors focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring disabled:cursor-not-allowed disabled:opacity-50";

export function useScenarioStudioApiBaseUrl() {
  const config = useRuntimeConfig();
  return computed(() => {
    if (import.meta.server) {
      return String(config.apiInternalBaseUrl || config.public.apiBaseUrl || "http://localhost:8080");
    }
    return String(config.public.apiBaseUrl || "http://localhost:8080");
  });
}

export function formatTimestamp(value: string): string {
  return new Intl.DateTimeFormat(undefined, {
    dateStyle: "short",
    timeStyle: "short",
  }).format(new Date(value));
}

export function formatPayload(payload: unknown): string {
  return JSON.stringify(payload, null, 2);
}

export function statusBadgeVariant(status: string): BadgeTone {
  const normalized = status.toLowerCase();
  if (normalized.includes("publish") || normalized.includes("approved")) {
    return "secondary";
  }
  if (normalized.includes("review")) {
    return "outline";
  }
  if (normalized.includes("fail")) {
    return "destructive";
  }
  return "default";
}

export function riskBadgeVariant(risk: "Low" | "Medium" | "High"): BadgeTone {
  if (risk === "Low") {
    return "secondary";
  }
  if (risk === "Medium") {
    return "outline";
  }
  return "destructive";
}

export function simulationStatusTone(status: string): BadgeTone {
  const normalized = status.toLowerCase();
  if (normalized === "completed") {
    return "secondary";
  }
  if (normalized === "failed") {
    return "destructive";
  }
  if (normalized === "running") {
    return "default";
  }
  return "outline";
}
