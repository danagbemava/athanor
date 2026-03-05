package tests

import (
	"encoding/json"
	"os"
	"path/filepath"
	"testing"
)

type GoldenRun struct {
	Seed            uint64 `json:"seed"`
	AgentPolicy     string `json:"agent_policy"`
	ExpectedOutcome string `json:"expected_outcome"`
	ExpectedSteps   int    `json:"expected_steps"`
}

type GoldenManifest struct {
	ScenarioSource    string      `json:"scenario_source"`
	ExpectedBundleHash string     `json:"expected_bundle_hash"`
	Runs              []GoldenRun `json:"runs"`
}

func TestGoldenFixturesLoad(t *testing.T) {
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
		manifestPath := filepath.Join(root, entry.Name(), "manifest.json")
		data, err := os.ReadFile(manifestPath)
		if err != nil {
			t.Fatalf("missing manifest for %s: %v", entry.Name(), err)
		}
		var manifest GoldenManifest
		if err := json.Unmarshal(data, &manifest); err != nil {
			t.Fatalf("invalid manifest %s: %v", manifestPath, err)
		}
		if manifest.ScenarioSource == "" || manifest.ExpectedBundleHash == "" || len(manifest.Runs) == 0 {
			t.Fatalf("manifest missing required fields: %s", manifestPath)
		}
		count++
	}

	if count < 5 {
		t.Fatalf("expected at least 5 golden fixtures, got %d", count)
	}
}
