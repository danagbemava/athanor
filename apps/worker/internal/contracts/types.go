package contracts

import "github.com/athanor/apps/worker/internal/engine"

type ExecutionMode string

const (
	ExecutionModeOptimization ExecutionMode = "optimization"
	ExecutionModeAnalytics    ExecutionMode = "analytics"
)

type ExecutionRequest struct {
	BundleHash      string        `json:"bundle_hash"`
	SeedStart       uint64        `json:"seed_start"`
	RunCount        int           `json:"run_count"`
	AgentPolicy     string        `json:"agent_policy"`
	ScriptedChoices []int         `json:"scripted_choices,omitempty"`
	ExecutionMode   ExecutionMode `json:"execution_mode"`
	MaxSteps        int           `json:"max_steps"`
}

type ExecutionResult struct {
	BundleHash    string             `json:"bundle_hash"`
	AgentPolicy   string             `json:"agent_policy"`
	AgentVersion  string             `json:"agent_version"`
	ExecutionMode ExecutionMode      `json:"execution_mode"`
	SeedStart     uint64             `json:"seed_start"`
	RunCount      int                `json:"run_count"`
	MaxSteps      int                `json:"max_steps"`
	Runs          []engine.RunResult `json:"runs"`
}

func (request ExecutionRequest) Validate() error {
	if request.BundleHash == "" {
		return ErrInvalidRequest("bundle_hash is required")
	}
	if request.RunCount < 1 {
		return ErrInvalidRequest("run_count must be at least 1")
	}
	if request.AgentPolicy != "random-v1" && request.AgentPolicy != "scripted-v1" {
		return ErrInvalidRequest("agent_policy must be random-v1 or scripted-v1")
	}
	if request.ExecutionMode != ExecutionModeOptimization && request.ExecutionMode != ExecutionModeAnalytics {
		return ErrInvalidRequest("execution_mode must be optimization or analytics")
	}
	if request.MaxSteps < 1 {
		return ErrInvalidRequest("max_steps must be at least 1")
	}
	return nil
}

type invalidRequestError string

func (e invalidRequestError) Error() string {
	return string(e)
}

func ErrInvalidRequest(message string) error {
	return invalidRequestError(message)
}
