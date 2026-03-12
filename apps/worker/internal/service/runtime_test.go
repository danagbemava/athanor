package service

import (
	"os"
	"path/filepath"
	"testing"

	"github.com/athanor/apps/worker/internal/contracts"
	"github.com/athanor/apps/worker/internal/engine"
	"github.com/redis/go-redis/v9"
)

func TestProcessDispatchCachesBundleAndPublishesEvents(t *testing.T) {
	tempDir := t.TempDir()
	runtime := NewRuntime(tempDir, stubBundleReader{content: []byte(validBundleJSON)})
	publisher := &stubPublisher{}
	request := DispatchRequest{
		RunID:      "run-1",
		BundleHash: "abc123",
		Request: contracts.ExecutionRequest{
			BundleHash:      "abc123",
			SeedStart:       1,
			RunCount:        2,
			AgentPolicy:     "scripted-v1",
			ScriptedChoices: []int{0},
			ExecutionMode:   contracts.ExecutionModeAnalytics,
			MaxSteps:        20,
		},
	}

	if err := runtime.ProcessDispatch(request, publisher); err != nil {
		t.Fatalf("process dispatch failed: %v", err)
	}

	if len(publisher.progress) != 2 {
		t.Fatalf("expected 2 progress events, got %d", len(publisher.progress))
	}
	if len(publisher.completions) != 1 {
		t.Fatalf("expected 1 completion event, got %d", len(publisher.completions))
	}
	cachePath := filepath.Join(tempDir, "abc123.json")
	if _, err := os.Stat(cachePath); err != nil {
		t.Fatalf("expected bundle cache to exist: %v", err)
	}
}

func TestExecuteRequestReportsProgress(t *testing.T) {
	reporter := &stubReporter{}
	result, err := ExecuteRequest(
		engine.Bundle{
			BundleHash:  "abc123",
			EntryNodeID: "start",
			Nodes: []engine.Node{
				{ID: "start", Type: engine.NodeTypeDecision, DecisionOptions: []engine.DecisionOption{{To: "end"}}},
				{ID: "end", Type: engine.NodeTypeTerminal, Outcome: "done"},
			},
		},
		contracts.ExecutionRequest{
			BundleHash:      "abc123",
			SeedStart:       1,
			RunCount:        3,
			AgentPolicy:     "scripted-v1",
			ScriptedChoices: []int{0},
			ExecutionMode:   contracts.ExecutionModeOptimization,
			MaxSteps:        20,
		},
		reporter,
	)
	if err != nil {
		t.Fatalf("execute failed: %v", err)
	}
	if len(result.Runs) != 3 {
		t.Fatalf("expected 3 runs, got %d", len(result.Runs))
	}
	if reporter.completed != 3 {
		t.Fatalf("expected 3 progress events, got %d", reporter.completed)
	}
}

func TestPublisherProgressReporterThrottlesLargeRunCounts(t *testing.T) {
	publisher := &stubPublisher{}
	reporter := &publisherProgressReporter{
		runID:     "run-1",
		publisher: publisher,
		interval:  progressInterval(5000),
	}

	for completedRuns := 1; completedRuns <= 5000; completedRuns += 1 {
		if err := reporter.RunCompleted(completedRuns, 5000); err != nil {
			t.Fatalf("progress reporter failed: %v", err)
		}
	}

	if len(publisher.progress) != 101 {
		t.Fatalf("expected 101 throttled progress events, got %d", len(publisher.progress))
	}
}

func TestDispatchRequestFromMessageValidatesPayload(t *testing.T) {
	_, err := dispatchRequestFromMessage(redis.XMessage{
		Values: map[string]any{
			"run_id":       "run-1",
			"bundle_hash":  "abc123",
			"request_json": `{"bundle_hash":"abc123","seed_start":1,"run_count":1,"agent_policy":"random-v1","execution_mode":"optimization","max_steps":10}`,
		},
	})
	if err != nil {
		t.Fatalf("expected dispatch message to parse: %v", err)
	}
}

type stubPublisher struct {
	progress    []string
	completions []string
}

func (publisher *stubPublisher) PublishProgress(runID string, completedRuns int, totalRuns int) error {
	publisher.progress = append(publisher.progress, runID)
	return nil
}

func (publisher *stubPublisher) PublishCompletion(runID string, result contracts.ExecutionResult) error {
	publisher.completions = append(publisher.completions, runID)
	return nil
}

func (publisher *stubPublisher) PublishFailure(runID string, message string) error {
	return nil
}

type stubReporter struct {
	completed int
}

func (reporter *stubReporter) RunCompleted(completedRuns int, totalRuns int) error {
	reporter.completed++
	return nil
}

type stubBundleReader struct {
	content []byte
}

func (reader stubBundleReader) ReadBundle(bundleHash string) ([]byte, error) {
	return reader.content, nil
}

const validBundleJSON = `{"bundle_hash":"abc123","entry_node_id":"start","nodes":[{"id":"start","type":"decision","decision_options":[{"to":"end"}]},{"id":"end","type":"terminal","outcome":"success"}]}`
