package tests

import (
	"encoding/json"
	"os"
	"path/filepath"
	"testing"

	"github.com/athanor/apps/worker/internal/agent"
	"github.com/athanor/apps/worker/internal/engine"
)

type GoldenRun struct {
	Seed            uint64 `json:"seed"`
	AgentPolicy     string `json:"agent_policy"`
	ScriptedChoices []int  `json:"scripted_choices,omitempty"`
	ExpectedOutcome string `json:"expected_outcome"`
	ExpectedSteps   int    `json:"expected_steps"`
}

type GoldenManifest struct {
	ScenarioSource     string      `json:"scenario_source"`
	ExpectedBundleHash string      `json:"expected_bundle_hash"`
	Runs               []GoldenRun `json:"runs"`
}

func TestGoldenFixturesRunDeterministically(t *testing.T) {
	root := filepath.Join("..", "..", "..", "packages", "spec", "golden")
	entries, err := os.ReadDir(root)
	if err != nil {
		t.Fatalf("failed to read golden dir: %v", err)
	}

	count := 0
	for _, entry := range entries {
		if !entry.IsDir() {
			continue
		}
		dir := filepath.Join(root, entry.Name())
		manifestPath := filepath.Join(dir, "manifest.json")
		manifestData, err := os.ReadFile(manifestPath)
		if err != nil {
			t.Fatalf("missing manifest for %s: %v", entry.Name(), err)
		}
		var manifest GoldenManifest
		if err := json.Unmarshal(manifestData, &manifest); err != nil {
			t.Fatalf("invalid manifest %s: %v", manifestPath, err)
		}

		scenarioPath := filepath.Join(dir, manifest.ScenarioSource)
		scenarioData, err := os.ReadFile(scenarioPath)
		if err != nil {
			t.Fatalf("missing scenario source %s: %v", scenarioPath, err)
		}
		var bundle engine.Bundle
		if err := json.Unmarshal(scenarioData, &bundle); err != nil {
			t.Fatalf("invalid scenario source %s: %v", scenarioPath, err)
		}

		if bundle.BundleHash != manifest.ExpectedBundleHash {
			t.Fatalf("bundle hash mismatch for %s: expected %s got %s", entry.Name(), manifest.ExpectedBundleHash, bundle.BundleHash)
		}

		for _, run := range manifest.Runs {
			policy := policyFromRun(run)
			result := engine.Run(bundle, run.Seed, policy)
			if result.Outcome != run.ExpectedOutcome || result.StepsTaken != run.ExpectedSteps {
				t.Fatalf("fixture %s failed seed=%d: expected outcome=%s steps=%d got outcome=%s steps=%d", entry.Name(), run.Seed, run.ExpectedOutcome, run.ExpectedSteps, result.Outcome, result.StepsTaken)
			}

			repeat := engine.Run(bundle, run.Seed, policyFromRun(run))
			if repeat.Outcome != result.Outcome || repeat.StepsTaken != result.StepsTaken {
				t.Fatalf("fixture %s is non-deterministic for seed=%d", entry.Name(), run.Seed)
			}
		}
		count++
	}

	if count < 5 {
		t.Fatalf("expected at least 5 golden fixtures, got %d", count)
	}
}

func policyFromRun(run GoldenRun) agent.Policy {
	switch run.AgentPolicy {
	case "scripted":
		return &agent.ScriptedPolicy{Choices: run.ScriptedChoices}
	default:
		return agent.RandomPolicy{}
	}
}
