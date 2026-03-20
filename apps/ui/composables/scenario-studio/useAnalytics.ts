import { watch } from "vue";
import type { ComputedRef } from "vue";
import type { ScenarioStudioState } from "@/composables/scenario-studio/types";
import { useScenarioStudioState } from "@/composables/scenario-studio/shared-state";
import { useScenarioStudioApiBaseUrl } from "@/composables/scenario-studio/utils";

type AnalyticsDependencies = {
  apiBaseUrl?: Readonly<ComputedRef<string>>;
  fetchImpl?: typeof fetch;
  resetSimulationState?: () => void;
  resetOptimizationState?: () => void;
};

export function useAnalytics(
  state: ScenarioStudioState = useScenarioStudioState(),
  dependencies: AnalyticsDependencies = {},
) {
  const apiBaseUrl = dependencies.apiBaseUrl ?? useScenarioStudioApiBaseUrl();
  const fetchImpl = dependencies.fetchImpl ?? fetch;
  const resetSimulationState = dependencies.resetSimulationState ?? (() => {});
  const resetOptimizationState = dependencies.resetOptimizationState ?? (() => {});

  async function fetchScenarioAnalytics(
    targetScenarioId = state.scenarioId.value,
    options: { force?: boolean } = {},
  ) {
    const normalizedScenarioId = targetScenarioId.trim();
    if (!normalizedScenarioId) {
      state.analyticsResponse.value = null;
      state.analyticsError.value = "";
      state.analyticsRequestKey.value = "";
      state.analyticsLoadedKey.value = "";
      return;
    }

    const requestKey = normalizedScenarioId;
    if (
      !options.force &&
      state.analyticsLoadedKey.value === requestKey &&
      state.analyticsResponse.value &&
      !state.analyticsError.value
    ) {
      return state.analyticsResponse.value;
    }
    if (
      state.isLoadingAnalytics.value &&
      state.analyticsRequestKey.value === requestKey
    ) {
      return state.analyticsResponse.value;
    }

    state.isLoadingAnalytics.value = true;
    state.analyticsError.value = "";
    state.analyticsRequestKey.value = requestKey;

    try {
      const response = await fetchImpl(
        `${apiBaseUrl.value}/scenarios/${normalizedScenarioId}/analytics`,
      );
      const payload = await response.json();
      if (!response.ok) {
        throw new Error(String(payload?.error || "Analytics request failed."));
      }

      state.analyticsResponse.value = payload;
      state.analyticsLoadedKey.value = requestKey;
      return state.analyticsResponse.value;
    } catch (error) {
      state.analyticsResponse.value = null;
      state.analyticsLoadedKey.value = "";
      state.analyticsError.value =
        error instanceof Error ? error.message : "Analytics request failed.";
    } finally {
      if (state.analyticsRequestKey.value === requestKey) {
        state.analyticsRequestKey.value = "";
        state.isLoadingAnalytics.value = false;
      }
    }
  }

  if (!state.analyticsWatchersInitialized.value) {
    state.analyticsWatchersInitialized.value = true;

    watch(
      state.scenarioId,
      (value) => {
        resetSimulationState();
        resetOptimizationState();
        state.analyticsLoadedKey.value = "";
        void fetchScenarioAnalytics(value);
      },
      { immediate: true },
    );

    watch(
      () => state.scenarioResponse.value?.version.id,
      () => {
        resetSimulationState();
        state.analyticsLoadedKey.value = "";
        void fetchScenarioAnalytics();
      },
    );
  }

  return {
    analyticsResponse: state.analyticsResponse,
    isLoadingAnalytics: state.isLoadingAnalytics,
    analyticsError: state.analyticsError,
    fetchScenarioAnalytics,
  };
}
