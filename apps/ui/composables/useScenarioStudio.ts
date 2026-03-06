export type ScenarioSnapshot = {
  scenarioId: string;
  name: string;
  description?: string | null;
  createdAt: string;
  updatedAt: string;
  versionCount: number;
  version: {
    id: string;
    number: number;
    state: string;
    createdAt: string;
  };
};

export type ValidationMessage = {
  code: string;
  message: string;
};

export type ValidationSnapshot = {
  scenarioId: string;
  versionId: string;
  versionNumber: number;
  valid: boolean;
  errors: ValidationMessage[];
  warnings: ValidationMessage[];
};

export type ValidationRun = {
  snapshot: ValidationSnapshot;
  recordedAt: string;
};

export type NodeType = "DecisionNode" | "ChanceNode" | "TerminalNode";

export type DecisionOptionDraft = {
  to: string;
  guardVar: string;
  guardEquals: string;
};

export type ChanceOptionDraft = {
  to: string;
  weight: string;
};

export type DraftNode = {
  id: string;
  type: NodeType;
  outcome: string;
  decisionOptions: DecisionOptionDraft[];
  chanceOptions: ChanceOptionDraft[];
};

export type DraftEdge = {
  from: string;
  to: string;
};

export type BadgeTone = "default" | "secondary" | "outline" | "destructive";

export type PortfolioRow = {
  scenarioId: string;
  name: string;
  owner: string;
  status: string;
  versions: number;
  passRate: number;
  risk: "Low" | "Medium" | "High";
  updatedAt: string;
};

export type ActivityItem = {
  id: string;
  title: string;
  detail: string;
  at: string;
  tone: BadgeTone;
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

function createDraftNode(type: NodeType, id: string): DraftNode {
  return {
    id,
    type,
    outcome: type === "TerminalNode" ? "completed" : "",
    decisionOptions:
      type === "DecisionNode"
        ? [{ to: "", guardVar: "", guardEquals: "" }]
        : [],
    chanceOptions: type === "ChanceNode" ? [{ to: "", weight: "1" }] : [],
  };
}

export function useScenarioStudio() {
  const config = useRuntimeConfig();
  const apiBaseUrl = computed(() =>
    String(config.public.apiBaseUrl || "http://localhost:8080"),
  );

  const nativeControlClass =
    "flex h-9 w-full rounded-md border border-input bg-background px-3 py-2 text-sm text-foreground shadow-sm transition-colors focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring disabled:cursor-not-allowed disabled:opacity-50";

  const scenarioName = useState<string>(
    "studio:scenario-name",
    () => "First Draft Scenario",
  );
  const scenarioDescription = useState<string>(
    "studio:scenario-description",
    () => "Interactive authoring draft from Scenario Studio.",
  );
  const scenarioId = useState<string>("studio:scenario-id", () => "");
  const portfolioQuery = useState<string>("studio:portfolio-query", () => "");

  const graphId = useState<string>("studio:graph-id", () => "scenario-graph-1");
  const graphVersion = useState<string>("studio:graph-version", () => "1");
  const entryNodeId = useState<string>("studio:entry-node-id", () => "start");

  const nodes = useState<DraftNode[]>("studio:nodes", () => [
    {
      id: "start",
      type: "DecisionNode",
      outcome: "",
      decisionOptions: [{ to: "terminal", guardVar: "", guardEquals: "" }],
      chanceOptions: [],
    },
    {
      id: "terminal",
      type: "TerminalNode",
      outcome: "approved",
      decisionOptions: [],
      chanceOptions: [],
    },
  ]);

  const edges = useState<DraftEdge[]>("studio:edges", () => [
    { from: "start", to: "terminal" },
  ]);

  const isSaving = useState<boolean>("studio:is-saving", () => false);
  const isValidating = useState<boolean>("studio:is-validating", () => false);
  const requestError = useState<string>("studio:request-error", () => "");
  const statusNote = useState<string>("studio:status-note", () => "Ready");
  const scenarioResponse = useState<ScenarioSnapshot | null>(
    "studio:scenario-response",
    () => null,
  );
  const validationResponse = useState<ValidationSnapshot | null>(
    "studio:validation-response",
    () => null,
  );

  const scenarioHistory = useState<ScenarioSnapshot[]>(
    "studio:scenario-history",
    () => [],
  );
  const validationHistory = useState<ValidationRun[]>(
    "studio:validation-history",
    () => [],
  );
  const activityFeed = useState<ActivityItem[]>(
    "studio:activity-feed",
    () => [],
  );

  const nodeReferences = computed(() => {
    const seen = new Set<string>();
    return nodes.value
      .map((node, index) => {
        const id = node.id.trim();
        return {
          id,
          label: id || `Node ${index + 1}`,
        };
      })
      .filter((item) => {
        if (!item.id || seen.has(item.id)) {
          return false;
        }
        seen.add(item.id);
        return true;
      });
  });

  watch(
    nodeReferences,
    (refs) => {
      if (!refs.some((refNode) => refNode.id === entryNodeId.value)) {
        entryNodeId.value = refs[0]?.id ?? "";
      }
    },
    { immediate: true },
  );

  const graphValidationIssues = computed(() => {
    const issues: string[] = [];
    const addIssue = (message: string) => {
      if (!issues.includes(message)) {
        issues.push(message);
      }
    };

    if (!graphId.value.trim()) {
      addIssue("Graph ID is required.");
    }

    const parsedVersion = Number.parseInt(graphVersion.value, 10);
    if (!Number.isInteger(parsedVersion) || parsedVersion < 1) {
      addIssue("Graph version must be a positive integer.");
    }

    if (nodes.value.length === 0) {
      addIssue("At least one node is required.");
      return issues;
    }

    const nodeIds = nodes.value.map((node) => node.id.trim());
    const nonEmptyNodeIds = nodeIds.filter((id) => id.length > 0);

    if (nonEmptyNodeIds.length !== nodeIds.length) {
      addIssue("Every node must have an ID.");
    }

    if (new Set(nonEmptyNodeIds).size !== nonEmptyNodeIds.length) {
      addIssue("Node IDs must be unique.");
    }

    const idSet = new Set(nonEmptyNodeIds);

    if (!entryNodeId.value.trim()) {
      addIssue("Entry node is required.");
    } else if (!idSet.has(entryNodeId.value.trim())) {
      addIssue("Entry node must reference an existing node ID.");
    }

    edges.value.forEach((edge, edgeIndex) => {
      const from = edge.from.trim();
      const to = edge.to.trim();

      if (!from || !to) {
        addIssue(
          `Edge ${edgeIndex + 1} must include both source and destination.`,
        );
        return;
      }

      if (!idSet.has(from)) {
        addIssue(
          `Edge ${edgeIndex + 1} references unknown source node "${from}".`,
        );
      }

      if (!idSet.has(to)) {
        addIssue(
          `Edge ${edgeIndex + 1} references unknown destination node "${to}".`,
        );
      }
    });

    nodes.value.forEach((node, nodeIndex) => {
      if (node.type === "DecisionNode") {
        const options = node.decisionOptions.filter(
          (option) => option.to.trim().length > 0,
        );
        if (options.length === 0) {
          addIssue(
            `Decision node "${node.id || nodeIndex + 1}" must include at least one decision option.`,
          );
        }

        options.forEach((option, optionIndex) => {
          if (!idSet.has(option.to.trim())) {
            addIssue(
              `Decision option ${optionIndex + 1} on node "${node.id || nodeIndex + 1}" points to an unknown node.`,
            );
          }
        });
      }

      if (node.type === "ChanceNode") {
        const options = node.chanceOptions.filter(
          (option) => option.to.trim().length > 0,
        );
        if (options.length === 0) {
          addIssue(
            `Chance node "${node.id || nodeIndex + 1}" must include at least one chance option.`,
          );
        }

        let totalWeight = 0;
        options.forEach((option, optionIndex) => {
          const parsedWeight = Number.parseFloat(option.weight);
          if (!Number.isFinite(parsedWeight) || parsedWeight <= 0) {
            addIssue(
              `Chance option ${optionIndex + 1} on node "${node.id || nodeIndex + 1}" must use a positive weight.`,
            );
          } else {
            totalWeight += parsedWeight;
          }

          if (!idSet.has(option.to.trim())) {
            addIssue(
              `Chance option ${optionIndex + 1} on node "${node.id || nodeIndex + 1}" points to an unknown node.`,
            );
          }
        });

        if (options.length > 0 && Math.abs(totalWeight - 1) > 0.0001) {
          addIssue(
            `Chance node "${node.id || nodeIndex + 1}" weights should add up to 1.0.`,
          );
        }
      }
    });

    return issues;
  });

  const graphPayload = computed<Record<string, unknown>>(() => {
    const parsedVersion = Number.parseInt(graphVersion.value, 10);

    const compiledNodes = nodes.value.map((node) => {
      const compiled: Record<string, unknown> = {
        id: node.id.trim(),
        type: node.type,
      };

      if (node.type === "DecisionNode") {
        const decisionOptions = node.decisionOptions
          .filter((option) => option.to.trim().length > 0)
          .map((option) => {
            const normalized: Record<string, unknown> = {
              to: option.to.trim(),
            };

            if (option.guardVar.trim().length > 0) {
              normalized.guard = {
                var: option.guardVar.trim(),
                equals: option.guardEquals,
              };
            }

            return normalized;
          });

        if (decisionOptions.length > 0) {
          compiled.decision_options = decisionOptions;
        }
      }

      if (node.type === "ChanceNode") {
        const chanceOptions = node.chanceOptions
          .filter((option) => option.to.trim().length > 0)
          .map((option) => ({
            to: option.to.trim(),
            weight: Number.parseFloat(option.weight),
          }));

        if (chanceOptions.length > 0) {
          compiled.chance_options = chanceOptions;
        }
      }

      if (node.type === "TerminalNode" && node.outcome.trim().length > 0) {
        compiled.outcome = node.outcome.trim();
      }

      return compiled;
    });

    const compiledEdges = edges.value
      .filter(
        (edge) => edge.from.trim().length > 0 && edge.to.trim().length > 0,
      )
      .map((edge) => ({
        from: edge.from.trim(),
        to: edge.to.trim(),
      }));

    return {
      id: graphId.value.trim(),
      name: scenarioName.value.trim() || "Untitled Scenario",
      version:
        Number.isInteger(parsedVersion) && parsedVersion > 0
          ? parsedVersion
          : 1,
      entry_node_id: entryNodeId.value.trim(),
      nodes: compiledNodes,
      edges: compiledEdges,
    };
  });

  const validationStatsByScenario = computed(() => {
    const stats = new Map<
      string,
      { runs: number; pass: number; errors: number; warnings: number }
    >();

    validationHistory.value.forEach((run) => {
      const current = stats.get(run.snapshot.scenarioId) ?? {
        runs: 0,
        pass: 0,
        errors: 0,
        warnings: 0,
      };

      current.runs += 1;
      if (run.snapshot.valid) {
        current.pass += 1;
      }
      current.errors = run.snapshot.errors.length;
      current.warnings = run.snapshot.warnings.length;

      stats.set(run.snapshot.scenarioId, current);
    });

    return stats;
  });

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
    if (scenarioHistory.value.length === 0) {
      return seedPortfolio;
    }

    return scenarioHistory.value
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
    const query = portfolioQuery.value.trim().toLowerCase();
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
    const total = portfolioRows.value.reduce(
      (sum, row) => sum + row.versions,
      0,
    );
    return Math.round((total / portfolioRows.value.length) * 10) / 10;
  });
  const validationRunCount = computed(() => validationHistory.value.length);
  const overallPassRate = computed(() => {
    if (validationHistory.value.length > 0) {
      const passCount = validationHistory.value.filter(
        (run) => run.snapshot.valid,
      ).length;
      return Math.round((passCount / validationHistory.value.length) * 100);
    }

    if (portfolioRows.value.length === 0) {
      return 0;
    }

    return Math.round(
      portfolioRows.value.reduce((sum, row) => sum + row.passRate, 0) /
        portfolioRows.value.length,
    );
  });
  const openIssues = computed(() => {
    if (validationHistory.value.length === 0) {
      return 0;
    }
    const latest = validationHistory.value[0].snapshot;
    return latest.errors.length + latest.warnings.length;
  });

  const validationTrend = computed(() => {
    if (validationHistory.value.length === 0) {
      return [72, 75, 79, 77, 82, 86, 90];
    }

    const points = validationHistory.value
      .slice(0, 7)
      .reverse()
      .map((run) => {
        if (run.snapshot.valid) {
          return Math.max(65, 100 - run.snapshot.warnings.length * 4);
        }
        return Math.max(
          25,
          58 -
            run.snapshot.errors.length * 10 -
            run.snapshot.warnings.length * 3,
        );
      });

    while (points.length < 7) {
      points.unshift(points[0] ?? 70);
    }

    return points;
  });

  const latestValidation = computed(
    () => validationHistory.value[0]?.snapshot ?? validationResponse.value,
  );
  const feedItems = computed(() => {
    if (activityFeed.value.length > 0) {
      return activityFeed.value.slice(0, 8);
    }

    return [
      {
        id: "seed-1",
        title: "Dashboard Ready",
        detail: "Scenario tracking and analytics are active.",
        at: new Date().toISOString(),
        tone: "secondary" as BadgeTone,
      },
    ];
  });

  const canCreate = computed(
    () =>
      !isSaving.value &&
      scenarioName.value.trim().length > 0 &&
      graphValidationIssues.value.length === 0,
  );
  const canSaveVersion = computed(
    () =>
      !isSaving.value &&
      scenarioName.value.trim().length > 0 &&
      scenarioId.value.trim().length > 0 &&
      graphValidationIssues.value.length === 0,
  );
  const canValidate = computed(
    () => !isValidating.value && scenarioId.value.trim().length > 0,
  );

  function pushActivity(
    title: string,
    detail: string,
    tone: BadgeTone = "outline",
  ) {
    activityFeed.value.unshift({
      id: `${Date.now()}-${Math.random().toString(16).slice(2, 8)}`,
      title,
      detail,
      at: new Date().toISOString(),
      tone,
    });

    if (activityFeed.value.length > 30) {
      activityFeed.value.length = 30;
    }
  }

  function nextNodeId(): string {
    const existing = new Set(nodes.value.map((node) => node.id.trim()));
    let cursor = nodes.value.length + 1;
    while (existing.has(`node-${cursor}`)) {
      cursor += 1;
    }
    return `node-${cursor}`;
  }

  function addNode(type: NodeType = "DecisionNode") {
    const node = createDraftNode(type, nextNodeId());
    nodes.value.push(node);
    if (!entryNodeId.value.trim()) {
      entryNodeId.value = node.id;
    }
  }

  function removeNode(nodeIndex: number) {
    if (nodes.value.length <= 1) {
      return;
    }

    const removedNodeId = nodes.value[nodeIndex].id.trim();
    nodes.value.splice(nodeIndex, 1);

    if (!removedNodeId) {
      return;
    }

    edges.value = edges.value.filter(
      (edge) => edge.from !== removedNodeId && edge.to !== removedNodeId,
    );
    nodes.value.forEach((node) => {
      node.decisionOptions = node.decisionOptions.filter(
        (option) => option.to !== removedNodeId,
      );
      node.chanceOptions = node.chanceOptions.filter(
        (option) => option.to !== removedNodeId,
      );
    });

    if (entryNodeId.value === removedNodeId) {
      entryNodeId.value = nodeReferences.value[0]?.id ?? "";
    }
  }

  function normalizeNodeForType(node: DraftNode) {
    if (node.type === "DecisionNode") {
      if (node.decisionOptions.length === 0) {
        node.decisionOptions.push({ to: "", guardVar: "", guardEquals: "" });
      }
      node.chanceOptions = [];
      node.outcome = "";
      return;
    }

    if (node.type === "ChanceNode") {
      if (node.chanceOptions.length === 0) {
        node.chanceOptions.push({ to: "", weight: "1" });
      }
      node.decisionOptions = [];
      node.outcome = "";
      return;
    }

    node.decisionOptions = [];
    node.chanceOptions = [];
  }

  function addDecisionOption(node: DraftNode) {
    node.decisionOptions.push({ to: "", guardVar: "", guardEquals: "" });
  }

  function removeDecisionOption(node: DraftNode, optionIndex: number) {
    node.decisionOptions.splice(optionIndex, 1);
  }

  function addChanceOption(node: DraftNode) {
    node.chanceOptions.push({ to: "", weight: "1" });
  }

  function removeChanceOption(node: DraftNode, optionIndex: number) {
    node.chanceOptions.splice(optionIndex, 1);
  }

  function addEdge() {
    const first = nodeReferences.value[0]?.id ?? "";
    const second = nodeReferences.value[1]?.id ?? first;
    edges.value.push({ from: first, to: second });
  }

  function removeEdge(edgeIndex: number) {
    edges.value.splice(edgeIndex, 1);
  }

  function loadExampleGraph() {
    graphId.value = "scenario-graph-2";
    graphVersion.value = "1";
    entryNodeId.value = "decision";

    nodes.value = [
      {
        id: "decision",
        type: "DecisionNode",
        outcome: "",
        decisionOptions: [
          {
            to: "chance",
            guardVar: "customer_segment",
            guardEquals: "premium",
          },
        ],
        chanceOptions: [],
      },
      {
        id: "chance",
        type: "ChanceNode",
        outcome: "",
        decisionOptions: [],
        chanceOptions: [
          { to: "approved", weight: "0.65" },
          { to: "declined", weight: "0.35" },
        ],
      },
      {
        id: "approved",
        type: "TerminalNode",
        outcome: "approved",
        decisionOptions: [],
        chanceOptions: [],
      },
      {
        id: "declined",
        type: "TerminalNode",
        outcome: "declined",
        decisionOptions: [],
        chanceOptions: [],
      },
    ];

    edges.value = [
      { from: "decision", to: "chance" },
      { from: "chance", to: "approved" },
      { from: "chance", to: "declined" },
    ];

    statusNote.value = "Loaded dashboard sample scenario";
    pushActivity(
      "Sample Graph Loaded",
      "Builder fields were prefilled from template.",
      "secondary",
    );
  }

  function upsertScenarioHistory(snapshot: ScenarioSnapshot) {
    const existing = scenarioHistory.value.findIndex(
      (item) => item.scenarioId === snapshot.scenarioId,
    );
    if (existing >= 0) {
      scenarioHistory.value.splice(existing, 1, snapshot);
    } else {
      scenarioHistory.value.push(snapshot);
    }

    scenarioHistory.value.sort((a, b) => {
      const aTime = new Date(a.updatedAt).getTime();
      const bTime = new Date(b.updatedAt).getTime();
      return bTime - aTime;
    });
  }

  function recordValidation(snapshot: ValidationSnapshot) {
    validationHistory.value.unshift({
      snapshot,
      recordedAt: new Date().toISOString(),
    });
    if (validationHistory.value.length > 20) {
      validationHistory.value.length = 20;
    }
  }

  function formatTimestamp(value: string): string {
    const date = new Date(value);
    if (Number.isNaN(date.getTime())) {
      return value;
    }
    return date.toLocaleString();
  }

  function formatPayload(payload: unknown): string {
    return JSON.stringify(payload, null, 2);
  }

  function statusBadgeVariant(status: string): BadgeTone {
    const normalized = status.toLowerCase();
    if (normalized.includes("publish") || normalized.includes("active")) {
      return "default";
    }
    if (normalized.includes("draft") || normalized.includes("review")) {
      return "secondary";
    }
    if (normalized.includes("error") || normalized.includes("blocked")) {
      return "destructive";
    }
    return "outline";
  }

  function riskBadgeVariant(risk: "Low" | "Medium" | "High"): BadgeTone {
    if (risk === "High") {
      return "destructive";
    }
    if (risk === "Medium") {
      return "secondary";
    }
    return "outline";
  }

  function selectScenario(row: PortfolioRow) {
    scenarioId.value = row.scenarioId;
    scenarioName.value = row.name;
    const fromHistory = scenarioHistory.value.find(
      (item) => item.scenarioId === row.scenarioId,
    );
    scenarioDescription.value =
      fromHistory?.description ?? scenarioDescription.value;
    statusNote.value = `Selected ${row.scenarioId}`;
    pushActivity(
      "Scenario Selected",
      `Loaded ${row.name} into quick actions.`,
      "outline",
    );
  }

  async function createScenario() {
    const firstIssue = graphValidationIssues.value[0];
    if (firstIssue) {
      statusNote.value = "Fix graph issues before creating";
      return;
    }

    isSaving.value = true;
    requestError.value = "";
    statusNote.value = "Creating scenario draft...";

    try {
      const response = await fetch(`${apiBaseUrl.value}/scenarios`, {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({
          name: scenarioName.value.trim(),
          description: scenarioDescription.value.trim(),
          graph: graphPayload.value,
        }),
      });

      const payload = await response.json();
      if (!response.ok) {
        throw new Error(String(payload?.error || "Scenario creation failed."));
      }

      scenarioResponse.value = payload as ScenarioSnapshot;
      scenarioId.value = scenarioResponse.value.scenarioId;
      validationResponse.value = null;
      upsertScenarioHistory(scenarioResponse.value);
      statusNote.value = `Created scenario ${scenarioId.value}`;
      pushActivity(
        "Scenario Created",
        `${scenarioResponse.value.name} created as draft.`,
        "default",
      );
    } catch (error) {
      const message =
        error instanceof Error ? error.message : "Request failed.";
      requestError.value = message;
      pushActivity("Create Failed", message, "destructive");
    } finally {
      isSaving.value = false;
    }
  }

  async function saveNewVersion() {
    const firstIssue = graphValidationIssues.value[0];
    if (firstIssue) {
      statusNote.value = "Fix graph issues before saving a version";
      return;
    }

    isSaving.value = true;
    requestError.value = "";
    statusNote.value = "Saving new scenario version...";

    try {
      const response = await fetch(
        `${apiBaseUrl.value}/scenarios/${scenarioId.value}`,
        {
          method: "PUT",
          headers: { "Content-Type": "application/json" },
          body: JSON.stringify({
            name: scenarioName.value.trim(),
            description: scenarioDescription.value.trim(),
            graph: graphPayload.value,
          }),
        },
      );

      const payload = await response.json();
      if (!response.ok) {
        throw new Error(String(payload?.error || "Version save failed."));
      }

      scenarioResponse.value = payload as ScenarioSnapshot;
      validationResponse.value = null;
      upsertScenarioHistory(scenarioResponse.value);
      statusNote.value = `Saved version ${scenarioResponse.value.version.number}`;
      pushActivity(
        "Version Saved",
        `${scenarioResponse.value.name} moved to version ${scenarioResponse.value.version.number}.`,
        "default",
      );
    } catch (error) {
      const message =
        error instanceof Error ? error.message : "Request failed.";
      requestError.value = message;
      pushActivity("Save Failed", message, "destructive");
    } finally {
      isSaving.value = false;
    }
  }

  async function validateScenario() {
    isValidating.value = true;
    requestError.value = "";
    statusNote.value = "Validating latest version...";

    try {
      const response = await fetch(
        `${apiBaseUrl.value}/scenarios/${scenarioId.value}/validate`,
        {
          method: "POST",
        },
      );

      const payload = await response.json();
      if (!response.ok) {
        throw new Error(String(payload?.error || "Validation failed."));
      }

      validationResponse.value = payload as ValidationSnapshot;
      recordValidation(validationResponse.value);
      statusNote.value = validationResponse.value.valid
        ? "Validation passed"
        : "Validation returned issues";
      pushActivity(
        "Validation Completed",
        `${validationResponse.value.scenarioId} v${validationResponse.value.versionNumber}: ${
          validationResponse.value.valid ? "passed" : "failed"
        }.`,
        validationResponse.value.valid ? "secondary" : "destructive",
      );
    } catch (error) {
      const message =
        error instanceof Error ? error.message : "Validation request failed.";
      requestError.value = message;
      pushActivity("Validation Failed", message, "destructive");
    } finally {
      isValidating.value = false;
    }
  }

  return {
    apiBaseUrl,
    nativeControlClass,
    scenarioName,
    scenarioDescription,
    scenarioId,
    portfolioQuery,
    graphId,
    graphVersion,
    entryNodeId,
    nodes,
    edges,
    isSaving,
    isValidating,
    requestError,
    statusNote,
    scenarioResponse,
    validationResponse,
    scenarioHistory,
    validationHistory,
    activityFeed,
    nodeReferences,
    graphValidationIssues,
    graphPayload,
    portfolioRows,
    filteredPortfolioRows,
    totalScenarios,
    activeDrafts,
    avgVersions,
    validationRunCount,
    overallPassRate,
    openIssues,
    validationTrend,
    latestValidation,
    feedItems,
    canCreate,
    canSaveVersion,
    canValidate,
    pushActivity,
    addNode,
    removeNode,
    normalizeNodeForType,
    addDecisionOption,
    removeDecisionOption,
    addChanceOption,
    removeChanceOption,
    addEdge,
    removeEdge,
    loadExampleGraph,
    formatTimestamp,
    formatPayload,
    statusBadgeVariant,
    riskBadgeVariant,
    selectScenario,
    createScenario,
    saveNewVersion,
    validateScenario,
  };
}
