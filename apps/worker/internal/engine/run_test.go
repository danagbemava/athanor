package engine

import (
	"reflect"
	"testing"

	"github.com/athanor/apps/worker/internal/agent"
)

func TestRunReturnsContractFields(t *testing.T) {
	bundle := Bundle{
		BundleHash:  "hash-123",
		EntryNodeID: "terminal",
		Nodes: []Node{
			{ID: "terminal", Type: NodeTypeTerminal, Outcome: "ok"},
		},
	}

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
	if result.Outcome != "ok" {
		t.Fatalf("unexpected outcome: %s", result.Outcome)
	}
}

func TestDeterministicWithSameSeed(t *testing.T) {
	bundle := Bundle{
		BundleHash:  "hash-det",
		EntryNodeID: "chance",
		Nodes: []Node{
			{ID: "chance", Type: NodeTypeChance, ChanceOptions: []ChanceOption{{To: "a", Weight: 1}, {To: "b", Weight: 3}}},
			{ID: "a", Type: NodeTypeTerminal, Outcome: "A"},
			{ID: "b", Type: NodeTypeTerminal, Outcome: "B"},
		},
	}

	r1 := Run(bundle, 7, agent.RandomPolicy{})
	r2 := Run(bundle, 7, agent.RandomPolicy{})

	if r1.Outcome != r2.Outcome || r1.StepsTaken != r2.StepsTaken {
		t.Fatalf("expected deterministic runs, got %#v and %#v", r1, r2)
	}
}

func TestOverflowGuard(t *testing.T) {
	bundle := Bundle{
		BundleHash:  "hash-loop",
		EntryNodeID: "loop",
		Nodes: []Node{
			{ID: "loop", Type: NodeTypeDecision, DecisionOptions: []DecisionOption{{To: "loop"}}},
		},
	}

	result := RunWithMaxSteps(bundle, 1, agent.RandomPolicy{}, 3)
	if result.Outcome != "error_overflow" {
		t.Fatalf("expected overflow, got %s", result.Outcome)
	}
	if result.StepsTaken != 3 {
		t.Fatalf("expected 3 steps, got %d", result.StepsTaken)
	}
}

func TestAppliesAllEffects(t *testing.T) {
	bundle := Bundle{
		BundleHash:  "hash-effects",
		EntryNodeID: "start",
		InitialState: map[string]any{
			"counter": 1,
			"tags":    []string{"alpha", "beta"},
		},
		Nodes: []Node{
			{
				ID:   "start",
				Type: NodeTypeDecision,
				Effects: []Effect{
					{Op: EffectSet, Path: "status", Value: "active"},
					{Op: EffectIncrement, Path: "counter", Value: 2},
					{Op: EffectDecrement, Path: "counter", Value: 1},
					{Op: EffectAddToSet, Path: "tags", Value: "gamma"},
					{Op: EffectRemoveFromSet, Path: "tags", Value: "beta"},
				},
				DecisionOptions: []DecisionOption{{To: "terminal"}},
			},
			{ID: "terminal", Type: NodeTypeTerminal, Outcome: "done"},
		},
	}

	result := Run(bundle, 99, &agent.ScriptedPolicy{Choices: []int{0}})
	if result.Outcome != "done" {
		t.Fatalf("unexpected outcome: %s", result.Outcome)
	}

	finalState, ok := result.Metrics["final_state"].(map[string]any)
	if !ok {
		t.Fatalf("expected final_state map, got %#v", result.Metrics["final_state"])
	}

	if finalState["status"] != "active" {
		t.Fatalf("expected status=active, got %#v", finalState["status"])
	}
	if finalState["counter"] != float64(2) {
		t.Fatalf("expected counter=2, got %#v", finalState["counter"])
	}
	if !reflect.DeepEqual(finalState["tags"], []string{"alpha", "gamma"}) {
		t.Fatalf("unexpected tags: %#v", finalState["tags"])
	}
}
