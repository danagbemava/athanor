import type { ComputedRef, Ref } from "vue";

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

export type SimulationRun = {
  seed: number;
  outcome: string;
  stepsTaken: number;
  finalState: Record<string, unknown>;
  trace: SimulationTraceEvent[];
};

export type SimulationTraceEffect = {
  op: string;
  path: string;
  value: unknown;
};

export type SimulationTraceGuard = {
  var: string;
  equalsValue: unknown;
};

export type SimulationTraceOption = {
  index: number;
  to: string;
  weight?: number | null;
  guard?: SimulationTraceGuard | null;
};

export type SimulationTraceSelection = {
  index: number;
  to: string;
  weight?: number | null;
  guard?: SimulationTraceGuard | null;
};

export type SimulationTraceEvent = {
  step: number;
  nodeId: string;
  nodeType: string;
  stateBefore: Record<string, unknown>;
  stateAfter: Record<string, unknown>;
  effectsApplied: SimulationTraceEffect[];
  availableOptions: SimulationTraceOption[];
  selectedOption?: SimulationTraceSelection | null;
  nextNodeId?: string | null;
  outcome?: string | null;
};

export type SimulationSnapshot = {
  scenarioId: string;
  versionId: string;
  versionNumber: number;
  bundleHash: string;
  agentVersion: string;
  runCount: number;
  seedStart: number;
  maxSteps: number;
  averageSteps: number;
  outcomeCounts: Record<string, number>;
  runs: SimulationRun[];
  completedAt: string;
};

export type SubmittedSimulationJob = {
  runId: string;
  status: string;
  createdAt: string;
  totalRuns: number;
};

export type SimulationJobSnapshot = {
  runId: string;
  jobType: string;
  status: string;
  scenarioId: string;
  bundleHash?: string | null;
  totalRuns: number;
  completedRuns: number;
  progressPercent: number;
  attempts: number;
  deadLettered: boolean;
  error?: string | null;
  createdAt: string;
  startedAt?: string | null;
  completedAt?: string | null;
  summary?: SimulationSnapshot | null;
};

export type SimulationRunPage = {
  page: number;
  pageSize: number;
  totalRuns: number;
  runs: SimulationRun[];
};

export type ScenarioAnalyticsTraceSample = {
  seed: number;
  outcome: string;
  stepsTaken: number;
  trace: SimulationTraceEvent[];
  recordedAt: string;
};

export type ScenarioAnalyticsSnapshot = {
  scenarioId: string;
  latestVersionId?: string | null;
  latestVersionNumber?: number | null;
  latestBundleHash?: string | null;
  agentVersion?: string | null;
  batchCount: number;
  runCount: number;
  averageSteps: number;
  p90Steps: number;
  outcomeCounts: Record<string, number>;
  nodeVisitCounts: Record<string, number>;
  traceSampleRate: number;
  sampledTraceCount: number;
  sampledTraces: ScenarioAnalyticsTraceSample[];
  lastCompletedAt?: string | null;
};

export type OptimizationChanceOptionWeight = {
  to: string;
  weight: number;
};

export type OptimizationChanceNodeWeights = {
  nodeId: string;
  options: OptimizationChanceOptionWeight[];
};

export type OptimizationParameters = {
  chanceWeights: OptimizationChanceNodeWeights[];
};

export type SubmittedOptimizationJob = {
  jobId: string;
  status: string;
  createdAt: string;
  maxIterations: number;
  runsPerIteration: number;
  strategy: string;
};

export type OptimizationJobSnapshot = {
  jobId: string;
  jobType: string;
  status: string;
  scenarioId: string;
  baseVersionId: string;
  baseVersionNumber: number;
  strategy: string;
  targetDistribution: Record<string, number>;
  maxIterations: number;
  runsPerIteration: number;
  iterationsCompleted: number;
  progressPercent: number;
  bestScore: number;
  converged: boolean;
  bestParameters?: OptimizationParameters | null;
  bestOutcomeDistribution: Record<string, number>;
  error?: string | null;
  createdAt: string;
  startedAt?: string | null;
  completedAt?: string | null;
  appliedVersionId?: string | null;
  appliedVersionNumber?: number | null;
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

export type NodeReference = {
  id: string;
  label: string;
};

export type ScenarioStudioState = {
  scenarioName: Ref<string>;
  scenarioDescription: Ref<string>;
  scenarioId: Ref<string>;
  portfolioQuery: Ref<string>;
  graphId: Ref<string>;
  graphVersion: Ref<string>;
  entryNodeId: Ref<string>;
  nodes: Ref<DraftNode[]>;
  edges: Ref<DraftEdge[]>;
  isSaving: Ref<boolean>;
  isValidating: Ref<boolean>;
  isSimulating: Ref<boolean>;
  isOptimizing: Ref<boolean>;
  requestError: Ref<string>;
  statusNote: Ref<string>;
  simulationRunCount: Ref<number>;
  optimizationTargetJson: Ref<string>;
  optimizationMaxIterations: Ref<number>;
  optimizationRunsPerIteration: Ref<number>;
  scenarioResponse: Ref<ScenarioSnapshot | null>;
  validationResponse: Ref<ValidationSnapshot | null>;
  simulationResponse: Ref<SimulationSnapshot | null>;
  simulationJob: Ref<SimulationJobSnapshot | null>;
  simulationTracePage: Ref<SimulationRunPage | null>;
  isLoadingSimulationTracePage: Ref<boolean>;
  simulationTracePageError: Ref<string>;
  analyticsResponse: Ref<ScenarioAnalyticsSnapshot | null>;
  optimizationJob: Ref<OptimizationJobSnapshot | null>;
  analyticsWatchersInitialized: Ref<boolean>;
  analyticsRequestKey: Ref<string>;
  analyticsLoadedKey: Ref<string>;
  isLoadingAnalytics: Ref<boolean>;
  analyticsError: Ref<string>;
  activeSimulationRunId: Ref<string>;
  activeOptimizationJobId: Ref<string>;
  scenarioHistory: Ref<ScenarioSnapshot[]>;
  validationHistory: Ref<ValidationRun[]>;
  activityFeed: Ref<ActivityItem[]>;
};

export type ScenarioStudioGraphApi = {
  graphId: Ref<string>;
  graphVersion: Ref<string>;
  entryNodeId: Ref<string>;
  nodes: Ref<DraftNode[]>;
  edges: Ref<DraftEdge[]>;
  nodeReferences: ComputedRef<NodeReference[]>;
  graphPayload: ComputedRef<Record<string, unknown>>;
  addNode: (type?: NodeType) => void;
  removeNode: (nodeIndex: number) => void;
  normalizeNodeForType: (node: DraftNode) => void;
  addDecisionOption: (node: DraftNode) => void;
  removeDecisionOption: (node: DraftNode, optionIndex: number) => void;
  addChanceOption: (node: DraftNode) => void;
  removeChanceOption: (node: DraftNode, optionIndex: number) => void;
  addEdge: () => void;
  removeEdge: (edgeIndex: number) => void;
  loadExampleGraph: () => void;
};
