package service

import (
	"time"

	"github.com/athanor/apps/worker/internal/contracts"
)

type CompletionPayload struct {
	BundleHash    string         `json:"bundle_hash"`
	AgentPolicy   string         `json:"agent_policy"`
	AgentVersion  string         `json:"agent_version"`
	ExecutionMode string         `json:"execution_mode"`
	SeedStart     uint64         `json:"seed_start"`
	RunCount      int            `json:"run_count"`
	MaxSteps      int            `json:"max_steps"`
	AverageSteps  float64        `json:"average_steps"`
	OutcomeCounts map[string]int `json:"outcome_counts"`
	ResultKey     string         `json:"result_key"`
	CompletedAt   time.Time      `json:"completed_at"`
}

func completionPayload(result contracts.ExecutionResult, resultKey string, completedAt time.Time) CompletionPayload {
	outcomeCounts := make(map[string]int)
	totalSteps := 0
	for _, run := range result.Runs {
		outcomeCounts[run.Outcome] += 1
		totalSteps += run.StepsTaken
	}

	averageSteps := 0.0
	if len(result.Runs) > 0 {
		averageSteps = float64(totalSteps) / float64(len(result.Runs))
	}

	return CompletionPayload{
		BundleHash:    result.BundleHash,
		AgentPolicy:   result.AgentPolicy,
		AgentVersion:  result.AgentVersion,
		ExecutionMode: string(result.ExecutionMode),
		SeedStart:     result.SeedStart,
		RunCount:      result.RunCount,
		MaxSteps:      result.MaxSteps,
		AverageSteps:  averageSteps,
		OutcomeCounts: outcomeCounts,
		ResultKey:     resultKey,
		CompletedAt:   completedAt.UTC(),
	}
}
