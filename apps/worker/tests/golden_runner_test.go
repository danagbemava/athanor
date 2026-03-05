package tests

import (
	"encoding/json"
	"fmt"
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
		manifest, bundle, err := loadGoldenFixture(dir)
		if err != nil {
			t.Fatalf("fixture %s invalid: %v", entry.Name(), err)
		}

		for _, run := range manifest.Runs {
			policy := policyFromRun(run)
			result := engine.Run(bundle, run.Seed, policy)
			if err := validateRunResult(run, result); err != nil {
				t.Fatalf("fixture %s failed seed=%d: %v", entry.Name(), run.Seed, err)
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

func TestGoldenHarnessDetectsExpectationRegression(t *testing.T) {
	dir := filepath.Join("..", "..", "..", "packages", "spec", "golden", "linear-chain")
	manifest, bundle, err := loadGoldenFixture(dir)
	if err != nil {
		t.Fatalf("failed to load fixture: %v", err)
	}
	if len(manifest.Runs) == 0 {
		t.Fatal("fixture has no runs")
	}

	run := manifest.Runs[0]
	result := engine.Run(bundle, run.Seed, policyFromRun(run))

	tampered := run
	tampered.ExpectedOutcome = "__intentional_regression__"

	if err := validateRunResult(tampered, result); err == nil {
		t.Fatal("expected mismatch detection for tampered expected outcome")
	}
}

func loadGoldenFixture(dir string) (GoldenManifest, engine.Bundle, error) {
	manifestPath := filepath.Join(dir, "manifest.json")
	manifestData, err := os.ReadFile(manifestPath)
	if err != nil {
		return GoldenManifest{}, engine.Bundle{}, fmt.Errorf("missing manifest: %w", err)
	}

	var manifest GoldenManifest
	if err := json.Unmarshal(manifestData, &manifest); err != nil {
		return GoldenManifest{}, engine.Bundle{}, fmt.Errorf("invalid manifest: %w", err)
	}

	scenarioPath := filepath.Join(dir, manifest.ScenarioSource)
	scenarioData, err := os.ReadFile(scenarioPath)
	if err != nil {
		return GoldenManifest{}, engine.Bundle{}, fmt.Errorf("missing scenario source: %w", err)
	}

	var bundle engine.Bundle
	if err := json.Unmarshal(scenarioData, &bundle); err != nil {
		return GoldenManifest{}, engine.Bundle{}, fmt.Errorf("invalid scenario source: %w", err)
	}

	if bundle.BundleHash != manifest.ExpectedBundleHash {
		return GoldenManifest{}, engine.Bundle{}, fmt.Errorf("bundle hash mismatch: expected %s got %s", manifest.ExpectedBundleHash, bundle.BundleHash)
	}

	return manifest, bundle, nil
}

func validateRunResult(run GoldenRun, result engine.RunResult) error {
	if result.Outcome != run.ExpectedOutcome || result.StepsTaken != run.ExpectedSteps {
		return fmt.Errorf(
			"expected outcome=%s steps=%d got outcome=%s steps=%d",
			run.ExpectedOutcome,
			run.ExpectedSteps,
			result.Outcome,
			result.StepsTaken,
		)
	}
	return nil
}

func policyFromRun(run GoldenRun) agent.Policy {
	switch run.AgentPolicy {
	case "scripted":
		return &agent.ScriptedPolicy{Choices: run.ScriptedChoices}
	default:
		return agent.RandomPolicy{}
	}
}
