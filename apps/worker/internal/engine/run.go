package engine

import "github.com/athanor/apps/worker/internal/agent"

const DefaultMaxSteps = 10000

// Run is a scaffold contract for entry->terminal traversal.
func Run(bundle Bundle, seed uint64, policy agent.Policy) RunResult {
	if policy == nil {
		return RunResult{
			BundleHash: bundle.BundleHash,
			Seed:       seed,
			Outcome:    "error_no_policy",
		}
	}

	return RunResult{
		BundleHash:   bundle.BundleHash,
		Seed:         seed,
		AgentVersion: policy.Version(),
		Outcome:      "scaffold_terminal",
		StepsTaken:   0,
	}
}
