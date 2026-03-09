package engine

type NodeType string

const (
	NodeTypeDecision NodeType = "decision"
	NodeTypeChance   NodeType = "chance"
	NodeTypeTerminal NodeType = "terminal"
)

type EffectOp string

const (
	EffectSet           EffectOp = "set"
	EffectIncrement     EffectOp = "increment"
	EffectDecrement     EffectOp = "decrement"
	EffectAddToSet      EffectOp = "add_to_set"
	EffectRemoveFromSet EffectOp = "remove_from_set"
)

type Guard struct {
	Var    string `json:"var"`
	Equals any    `json:"equals"`
}

type Effect struct {
	Op    EffectOp `json:"op"`
	Path  string   `json:"path"`
	Value any      `json:"value,omitempty"`
}

type DecisionOption struct {
	To    string `json:"to"`
	Guard *Guard `json:"guard,omitempty"`
}

type ChanceOption struct {
	To     string  `json:"to"`
	Weight float64 `json:"weight"`
	Guard  *Guard  `json:"guard,omitempty"`
}

type Node struct {
	ID              string           `json:"id"`
	Type            NodeType         `json:"type"`
	Effects         []Effect         `json:"effects,omitempty"`
	DecisionOptions []DecisionOption `json:"decision_options,omitempty"`
	ChanceOptions   []ChanceOption   `json:"chance_options,omitempty"`
	Outcome         string           `json:"outcome,omitempty"`
}

type Bundle struct {
	BundleHash   string         `json:"bundle_hash"`
	EntryNodeID  string         `json:"entry_node_id"`
	Nodes        []Node         `json:"nodes"`
	InitialState map[string]any `json:"initial_state,omitempty"`
}

type TraceGuard struct {
	Var    string `json:"var"`
	Equals any    `json:"equals"`
}

type TraceEffect struct {
	Op    string `json:"op"`
	Path  string `json:"path"`
	Value any    `json:"value,omitempty"`
}

type TraceOption struct {
	Index  int         `json:"index"`
	To     string      `json:"to"`
	Weight *float64    `json:"weight,omitempty"`
	Guard  *TraceGuard `json:"guard,omitempty"`
}

type TraceSelection struct {
	Index  int         `json:"index"`
	To     string      `json:"to"`
	Weight *float64    `json:"weight,omitempty"`
	Guard  *TraceGuard `json:"guard,omitempty"`
}

type TraceEvent struct {
	Step             int             `json:"step"`
	NodeID           string          `json:"node_id"`
	NodeType         string          `json:"node_type"`
	StateBefore      map[string]any  `json:"state_before"`
	StateAfter       map[string]any  `json:"state_after"`
	EffectsApplied   []TraceEffect   `json:"effects_applied"`
	AvailableOptions []TraceOption   `json:"available_options"`
	SelectedOption   *TraceSelection `json:"selected_option,omitempty"`
	NextNodeID       *string         `json:"next_node_id,omitempty"`
	Outcome          *string         `json:"outcome,omitempty"`
}

type RunResult struct {
	BundleHash   string         `json:"bundle_hash"`
	Seed         uint64         `json:"seed"`
	AgentVersion string         `json:"agent_version"`
	Outcome      string         `json:"outcome"`
	StepsTaken   int            `json:"steps_taken"`
	Metrics      map[string]any `json:"metrics,omitempty"`
	Trace        []TraceEvent   `json:"trace,omitempty"`
}
