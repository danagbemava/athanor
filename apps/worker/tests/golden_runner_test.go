package tests

import (
	"encoding/json"
	"fmt"
	"os"
	"path/filepath"
	"sort"
	"strings"
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
	var mismatches []string
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
				mismatches = append(
					mismatches,
					fmt.Sprintf("fixture %s failed seed=%d: %v", entry.Name(), run.Seed, err),
				)
			}

			repeat := engine.Run(bundle, run.Seed, policyFromRun(run))
			if repeat.Outcome != result.Outcome || repeat.StepsTaken != result.StepsTaken {
				mismatches = append(
					mismatches,
					fmt.Sprintf("fixture %s is non-deterministic for seed=%d", entry.Name(), run.Seed),
				)
			}
		}
		count++
	}

	if count < 5 {
		t.Fatalf("expected at least 5 golden fixtures, got %d", count)
	}
	if len(mismatches) > 0 {
		t.Fatalf("golden mismatches:\n%s", strings.Join(mismatches, "\n"))
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

	var scenario map[string]any
	if err := json.Unmarshal(scenarioData, &scenario); err != nil {
		return GoldenManifest{}, engine.Bundle{}, fmt.Errorf("invalid scenario source: %w", err)
	}

	bundle, err := runtimeBundleFromScenarioGraph(manifest.ExpectedBundleHash, scenario)
	if err != nil {
		return GoldenManifest{}, engine.Bundle{}, err
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

func runtimeBundleFromScenarioGraph(bundleHash string, scenario map[string]any) (engine.Bundle, error) {
	entryNodeID, _ := firstString(scenario, "entry_node_id", "entryNodeId")
	if entryNodeID == "" {
		return engine.Bundle{}, fmt.Errorf("scenario graph missing entry node id")
	}

	nodesRaw, ok := scenario["nodes"].([]any)
	if !ok {
		return engine.Bundle{}, fmt.Errorf("scenario graph missing nodes array")
	}

	nodes := make([]engine.Node, 0, len(nodesRaw))
	for _, rawNode := range nodesRaw {
		nodeMap, ok := rawNode.(map[string]any)
		if !ok {
			return engine.Bundle{}, fmt.Errorf("scenario graph node is not an object")
		}

		nodeID, _ := firstString(nodeMap, "id")
		nodeType, _ := firstString(nodeMap, "type")
		normalizedType, err := normalizeNodeType(nodeType)
		if err != nil {
			return engine.Bundle{}, fmt.Errorf("node %s: %w", nodeID, err)
		}

		node := engine.Node{
			ID:      nodeID,
			Type:    normalizedType,
			Effects: runtimeEffects(nodeMap["effects"]),
			Outcome: stringValue(nodeMap["outcome"]),
		}
		node.DecisionOptions = runtimeDecisionOptions(nodeMap["decision_options"])
		node.ChanceOptions = runtimeChanceOptions(nodeMap["chance_options"])
		nodes = append(nodes, node)
	}

	initialState, err := runtimeState(scenario["initial_state"])
	if err != nil {
		return engine.Bundle{}, err
	}

	return engine.Bundle{
		BundleHash:   bundleHash,
		EntryNodeID:  entryNodeID,
		Nodes:        nodes,
		InitialState: initialState,
	}, nil
}

func normalizeNodeType(raw string) (engine.NodeType, error) {
	switch raw {
	case "decision", "DecisionNode":
		return engine.NodeTypeDecision, nil
	case "chance", "ChanceNode":
		return engine.NodeTypeChance, nil
	case "terminal", "TerminalNode":
		return engine.NodeTypeTerminal, nil
	default:
		return "", fmt.Errorf("unsupported node type %q", raw)
	}
}

func runtimeEffects(raw any) []engine.Effect {
	effectsRaw, ok := raw.([]any)
	if !ok {
		return nil
	}

	effects := make([]engine.Effect, 0, len(effectsRaw))
	for _, rawEffect := range effectsRaw {
		effectMap, ok := rawEffect.(map[string]any)
		if !ok {
			continue
		}
		effects = append(effects, engine.Effect{
			Op:    engine.EffectOp(stringValue(effectMap["op"])),
			Path:  stringValue(effectMap["path"]),
			Value: effectMap["value"],
		})
	}
	return effects
}

func runtimeDecisionOptions(raw any) []engine.DecisionOption {
	optionsRaw, ok := raw.([]any)
	if !ok {
		return nil
	}

	options := make([]engine.DecisionOption, 0, len(optionsRaw))
	for _, rawOption := range optionsRaw {
		optionMap, ok := rawOption.(map[string]any)
		if !ok {
			continue
		}
		options = append(options, engine.DecisionOption{
			To:    stringValue(optionMap["to"]),
			Guard: runtimeGuard(optionMap["guard"]),
		})
	}
	return options
}

func runtimeChanceOptions(raw any) []engine.ChanceOption {
	optionsRaw, ok := raw.([]any)
	if !ok {
		return nil
	}

	options := make([]engine.ChanceOption, 0, len(optionsRaw))
	for _, rawOption := range optionsRaw {
		optionMap, ok := rawOption.(map[string]any)
		if !ok {
			continue
		}
		options = append(options, engine.ChanceOption{
			To:     stringValue(optionMap["to"]),
			Weight: floatValue(optionMap["weight"]),
			Guard:  runtimeGuard(optionMap["guard"]),
		})
	}
	return options
}

func runtimeGuard(raw any) *engine.Guard {
	guardMap, ok := raw.(map[string]any)
	if !ok {
		return nil
	}
	return &engine.Guard{
		Var:    stringValue(guardMap["var"]),
		Equals: guardMap["equals"],
	}
}

func runtimeState(raw any) (map[string]any, error) {
	if raw == nil {
		return map[string]any{}, nil
	}
	stateMap, ok := raw.(map[string]any)
	if !ok {
		return nil, fmt.Errorf("initial_state must be an object")
	}

	keys := make([]string, 0, len(stateMap))
	for key := range stateMap {
		keys = append(keys, key)
	}
	sort.Strings(keys)

	state := make(map[string]any, len(stateMap))
	for _, key := range keys {
		state[key] = stateMap[key]
	}
	return state, nil
}

func firstString(source map[string]any, keys ...string) (string, bool) {
	for _, key := range keys {
		if value, ok := source[key].(string); ok {
			return value, true
		}
	}
	return "", false
}

func stringValue(raw any) string {
	value, _ := raw.(string)
	return value
}

func floatValue(raw any) float64 {
	switch value := raw.(type) {
	case float64:
		return value
	case float32:
		return float64(value)
	case int:
		return float64(value)
	case int32:
		return float64(value)
	case int64:
		return float64(value)
	default:
		return 0
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
