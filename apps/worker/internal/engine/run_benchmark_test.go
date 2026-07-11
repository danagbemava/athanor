package engine

import (
	"fmt"
	"testing"

	"github.com/athanor/apps/worker/internal/agent"
)

func BenchmarkRunTypicalNestedState(b *testing.B) {
	bundle := typicalNestedStateBenchmarkBundle()
	policy := agent.RandomPolicy{}

	b.ReportAllocs()
	b.ResetTimer()
	for i := 0; i < b.N; i++ {
		result := Run(bundle, uint64(i), policy)
		if result.Outcome != "done" {
			b.Fatalf("unexpected benchmark outcome: %s", result.Outcome)
		}
	}
}

func typicalNestedStateBenchmarkBundle() Bundle {
	nodes := make([]Node, 0, 9)
	for i := 0; i < 8; i++ {
		id := fmt.Sprintf("step-%d", i)
		next := fmt.Sprintf("step-%d", i+1)
		if i == 7 {
			next = "terminal"
		}
		nodes = append(nodes, Node{
			ID:              id,
			Type:            NodeTypeDecision,
			Effects:         []Effect{{Op: EffectIncrement, Path: "score", Value: 1}},
			DecisionOptions: []DecisionOption{{To: next}},
		})
	}
	nodes = append(nodes, Node{ID: "terminal", Type: NodeTypeTerminal, Outcome: "done"})

	return Bundle{
		BundleHash:  "benchmark-nested-state",
		EntryNodeID: "step-0",
		InitialState: map[string]any{
			"score":  float64(0),
			"health": float64(100),
			"level":  float64(4),
			"inventory": map[string]any{
				"gold":  float64(100),
				"items": []any{"rope", "torch", "map"},
			},
			"profile": map[string]any{
				"role":  "scout",
				"flags": []any{"active", "trained"},
			},
		},
		Nodes: nodes,
	}
}
