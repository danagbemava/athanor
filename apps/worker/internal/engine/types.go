package engine

type Bundle struct {
	BundleHash string `json:"bundle_hash"`
	EntryNodeID string `json:"entry_node_id"`
}

type RunResult struct {
	BundleHash   string `json:"bundle_hash"`
	Seed         uint64 `json:"seed"`
	AgentVersion string `json:"agent_version"`
	Outcome      string `json:"outcome"`
	StepsTaken   int    `json:"steps_taken"`
}
