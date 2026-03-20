import { computed } from "vue";
import type {
  ActivityItem,
  BadgeTone,
  ScenarioStudioState,
} from "@/composables/scenario-studio/types";
import { useScenarioStudioState } from "@/composables/scenario-studio/shared-state";

export function useActivityFeed(state: ScenarioStudioState = useScenarioStudioState()) {
  const feedItems = computed<ActivityItem[]>(() => {
    if (state.activityFeed.value.length > 0) {
      return state.activityFeed.value.slice(0, 8);
    }

    return [
      {
        id: "seed-1",
        title: "Dashboard Ready",
        detail: "Scenario tracking and analytics are active.",
        at: new Date().toISOString(),
        tone: "secondary",
      },
    ];
  });

  function pushActivity(
    title: string,
    detail: string,
    tone: BadgeTone = "outline",
  ) {
    state.activityFeed.value.unshift({
      id: `${Date.now()}-${Math.random().toString(16).slice(2, 8)}`,
      title,
      detail,
      at: new Date().toISOString(),
      tone,
    });

    if (state.activityFeed.value.length > 30) {
      state.activityFeed.value.length = 30;
    }
  }

  return {
    activityFeed: state.activityFeed,
    feedItems,
    pushActivity,
  };
}
