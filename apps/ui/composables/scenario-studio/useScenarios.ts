import { computed } from "vue";
import type { ComputedRef } from "vue";
import type {
  PortfolioRow,
  ScenarioSnapshot,
  ScenarioStudioState,
} from "@/composables/scenario-studio/types";
import { useScenarioStudioState } from "@/composables/scenario-studio/shared-state";
import { useScenarioStudioApiBaseUrl } from "@/composables/scenario-studio/utils";
import { useScenarioGraph } from "@/composables/scenario-studio/useScenarioGraph";

type ValidationStats = ComputedRef<
  Map<string, { runs: number; pass: number; errors: number; warnings: number }>
>;

type ScenarioDependencies = {
  apiBaseUrl?: Readonly<ComputedRef<string>>;
  fetchImpl?: typeof fetch;
  pushActivity?: (title: string, detail: string, tone?: any) => void;
  graphValidationIssues?: ComputedRef<string[]>;
  validationStatsByScenario?: ValidationStats;
};

const seedPortfolio: PortfolioRow[] = [
  {
    scenarioId: "scn-credit-intake",
    name: "Credit Intake Funnel",
    owner: "Risk Ops",
    status: "published",
    versions: 14,
    passRate: 96,
    risk: "Low",
    updatedAt: "2026-03-04T09:20:00Z",
  },
  {
    scenarioId: "scn-kyc-routing",
    name: "KYC Exception Routing",
    owner: "Compliance",
    status: "draft",
    versions: 7,
    passRate: 84,
    risk: "Medium",
    updatedAt: "2026-03-03T14:05:00Z",
  },
  {
    scenarioId: "scn-renewal-offers",
    name: "Renewal Offer Strategy",
    owner: "Growth",
    status: "published",
    versions: 11,
    passRate: 91,
    risk: "Low",
    updatedAt: "2026-03-02T18:42:00Z",
  },
  {
    scenarioId: "scn-fraud-escalation",
    name: "Fraud Escalation Matrix",
    owner: "Fraud Ops",
    status: "review",
    versions: 5,
    passRate: 73,
    risk: "High",
    updatedAt: "2026-03-01T11:10:00Z",
  },
];

export function useScenarios(
  state: ScenarioStudioState = useScenarioStudioState(),
  graph = useScenarioGraph(state),
  dependencies: ScenarioDependencies = {},
) {
  const apiBaseUrl = dependencies.apiBaseUrl ?? useScenarioStudioApiBaseUrl();
  const fetchImpl = dependencies.fetchImpl ?? fetch;
  const pushActivity = dependencies.pushActivity ?? (() => {});
  const graphValidationIssues = dependencies.graphValidationIssues ?? computed(() => []);
  const validationStatsByScenario =
    dependencies.validationStatsByScenario ?? computed(() => new Map());

  const canCreate = computed(
    () =>
      !state.isSaving.value &&
      state.scenarioName.value.trim().length > 0 &&
      graphValidationIssues.value.length === 0,
  );
  const canSaveVersion = computed(
    () =>
      !state.isSaving.value &&
      state.scenarioName.value.trim().length > 0 &&
      state.scenarioId.value.trim().length > 0 &&
      graphValidationIssues.value.length === 0,
  );

  function riskFromStats(stats?: {
    errors: number;
    warnings: number;
  }): "Low" | "Medium" | "High" {
    if (!stats) {
      return "Low";
    }
    if (stats.errors > 0) {
      return "High";
    }
    if (stats.warnings > 1) {
      return "Medium";
    }
    return "Low";
  }

  const portfolioRows = computed<PortfolioRow[]>(() => {
    if (state.scenarioHistory.value.length === 0) {
      return seedPortfolio;
    }

    return state.scenarioHistory.value
      .map((snapshot) => {
        const stats = validationStatsByScenario.value.get(snapshot.scenarioId);
        const passRate = stats
          ? Math.round((stats.pass / Math.max(stats.runs, 1)) * 100)
          : 100;

        return {
          scenarioId: snapshot.scenarioId,
          name: snapshot.name,
          owner: "Scenario Ops",
          status: snapshot.version.state,
          versions: snapshot.versionCount,
          passRate,
          risk: riskFromStats(stats),
          updatedAt: snapshot.updatedAt,
        };
      })
      .sort(
        (a, b) =>
          new Date(b.updatedAt).getTime() - new Date(a.updatedAt).getTime(),
      );
  });

  const filteredPortfolioRows = computed(() => {
    const query = state.portfolioQuery.value.trim().toLowerCase();
    if (!query) {
      return portfolioRows.value;
    }

    return portfolioRows.value.filter((row) => {
      return (
        row.name.toLowerCase().includes(query) ||
        row.scenarioId.toLowerCase().includes(query) ||
        row.owner.toLowerCase().includes(query)
      );
    });
  });

  const totalScenarios = computed(() => portfolioRows.value.length);
  const activeDrafts = computed(
    () =>
      portfolioRows.value.filter((row) =>
        row.status.toLowerCase().includes("draft"),
      ).length,
  );
  const avgVersions = computed(() => {
    if (portfolioRows.value.length === 0) {
      return 0;
    }
    const total = portfolioRows.value.reduce((sum, row) => sum + row.versions, 0);
    return Math.round((total / portfolioRows.value.length) * 10) / 10;
  });

  function upsertScenarioHistory(snapshot: ScenarioSnapshot) {
    const nextHistory = state.scenarioHistory.value.filter(
      (item) => item.scenarioId !== snapshot.scenarioId,
    );
    nextHistory.unshift(snapshot);
    state.scenarioHistory.value = nextHistory.slice(0, 16);
  }

  function hydrateGraphFromSnapshot(snapshot: ScenarioSnapshot) {
    state.scenarioName.value = snapshot.name;
    state.scenarioDescription.value = snapshot.description || "";
    state.scenarioId.value = snapshot.scenarioId;
    state.scenarioResponse.value = snapshot;
  }

  function selectScenario(row: PortfolioRow) {
    state.scenarioName.value = row.name;
    state.scenarioDescription.value = `${row.owner} scenario draft`;
    state.scenarioId.value = row.scenarioId;
    state.statusNote.value = `Selected scenario ${row.scenarioId}`;

    const snapshot = state.scenarioHistory.value.find(
      (item) => item.scenarioId === row.scenarioId,
    );
    if (snapshot) {
      hydrateGraphFromSnapshot(snapshot);
    }
  }

  async function createScenario() {
    state.isSaving.value = true;
    state.requestError.value = "";
    state.statusNote.value = "Creating scenario draft...";

    try {
      const response = await fetchImpl(`${apiBaseUrl.value}/scenarios`, {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({
          name: state.scenarioName.value,
          description: state.scenarioDescription.value,
          graph: graph.graphPayload.value,
        }),
      });
      const payload = await response.json();
      if (!response.ok) {
        throw new Error(String(payload?.error || "Create scenario failed."));
      }

      state.scenarioResponse.value = payload as ScenarioSnapshot;
      state.scenarioId.value = state.scenarioResponse.value.scenarioId;
      upsertScenarioHistory(state.scenarioResponse.value);
      state.statusNote.value = `Created scenario ${state.scenarioId.value}`;
      pushActivity(
        "Scenario Created",
        `${state.scenarioResponse.value.name} created as ${state.scenarioId.value}.`,
        "secondary",
      );
    } catch (error) {
      const message = error instanceof Error ? error.message : "Create scenario failed.";
      state.requestError.value = message;
      state.statusNote.value = "Create scenario failed";
      pushActivity("Scenario Create Failed", message, "destructive");
      throw error;
    } finally {
      state.isSaving.value = false;
    }
  }

  async function saveNewVersion() {
    const normalizedScenarioId = state.scenarioId.value.trim();
    if (!normalizedScenarioId) {
      return;
    }

    state.isSaving.value = true;
    state.requestError.value = "";
    state.statusNote.value = "Saving new scenario version...";

    try {
      const response = await fetchImpl(
        `${apiBaseUrl.value}/scenarios/${normalizedScenarioId}/versions`,
        {
          method: "POST",
          headers: { "Content-Type": "application/json" },
          body: JSON.stringify({
            graph: graph.graphPayload.value,
            description: state.scenarioDescription.value,
          }),
        },
      );
      const payload = await response.json();
      if (!response.ok) {
        throw new Error(String(payload?.error || "Save version failed."));
      }

      state.scenarioResponse.value = payload as ScenarioSnapshot;
      upsertScenarioHistory(state.scenarioResponse.value);
      state.statusNote.value = `Saved version ${state.scenarioResponse.value.version.number}`;
      pushActivity(
        "Scenario Version Saved",
        `${normalizedScenarioId} advanced to version ${state.scenarioResponse.value.version.number}.`,
        "default",
      );
    } catch (error) {
      const message = error instanceof Error ? error.message : "Save version failed.";
      state.requestError.value = message;
      state.statusNote.value = "Save version failed";
      pushActivity("Scenario Version Save Failed", message, "destructive");
      throw error;
    } finally {
      state.isSaving.value = false;
    }
  }

  return {
    scenarioName: state.scenarioName,
    scenarioDescription: state.scenarioDescription,
    scenarioId: state.scenarioId,
    portfolioQuery: state.portfolioQuery,
    scenarioResponse: state.scenarioResponse,
    scenarioHistory: state.scenarioHistory,
    canCreate,
    canSaveVersion,
    portfolioRows,
    filteredPortfolioRows,
    totalScenarios,
    activeDrafts,
    avgVersions,
    selectScenario,
    createScenario,
    saveNewVersion,
    upsertScenarioHistory,
  };
}
