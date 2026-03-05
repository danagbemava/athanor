package engine

import (
	"testing"

	"github.com/athanor/apps/worker/internal/agent"
)

func TestRunReturnsContractFields(t *testing.T) {
	bundle := Bundle{BundleHash: "hash-123", EntryNodeID: "start"}
	result := Run(bundle, 42, agent.RandomPolicy{})
	if result.BundleHash != "hash-123" {
		t.Fatalf("unexpected bundle hash: %s", result.BundleHash)
	}
	if result.Seed != 42 {
		t.Fatalf("unexpected seed: %d", result.Seed)
	}
	if result.AgentVersion == "" {
		t.Fatal("expected agent version")
	}
}

func TestDeterminismPlaceholder(t *testing.T) {
	t.Skip("TODO(NEX-196): assert stable outcomes for same bundle+seed+agent over repeated runs")
}
