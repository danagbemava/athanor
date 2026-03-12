package service

import (
	"encoding/json"
	"errors"
	"fmt"
	"os"
	"path/filepath"
	"strings"

	"github.com/athanor/apps/worker/internal/agent"
	"github.com/athanor/apps/worker/internal/contracts"
	"github.com/athanor/apps/worker/internal/engine"
)

type Runtime struct {
	cacheDir     string
	bundleReader BundleReader
	executeFunc  func(engine.Bundle, contracts.ExecutionRequest, ProgressReporter) (contracts.ExecutionResult, error)
}

type ProgressReporter interface {
	RunCompleted(completedRuns int, totalRuns int) error
}

func NewRuntime(cacheDir string, bundleReader BundleReader) *Runtime {
	return &Runtime{
		cacheDir:     cacheDir,
		bundleReader: bundleReader,
		executeFunc:  ExecuteRequest,
	}
}

func (runtime *Runtime) ProcessDispatch(request DispatchRequest, publisher EventPublisher) error {
	if err := request.Validate(); err != nil {
		return err
	}

	bundle, err := runtime.loadBundle(request.BundleHash)
	if err != nil {
		if publisher != nil {
			_ = publisher.PublishFailure(request.RunID, err.Error())
		}
		return err
	}

	reporter := publisherProgressReporter{
		runID:     request.RunID,
		publisher: publisher,
		interval:  progressInterval(request.Request.RunCount),
	}
	result, err := runtime.executeFunc(bundle, request.Request, &reporter)
	if err != nil {
		if publisher != nil {
			_ = publisher.PublishFailure(request.RunID, err.Error())
		}
		return err
	}
	if publisher != nil {
		if err := publisher.PublishCompletion(request.RunID, result); err != nil {
			_ = publisher.PublishFailure(request.RunID, err.Error())
			return err
		}
	}
	return nil
}

func (runtime *Runtime) Dispatch(request DispatchRequest) error {
	return runtime.ProcessDispatch(request, nil)
}

type EventPublisher interface {
	PublishProgress(runID string, completedRuns int, totalRuns int) error
	PublishCompletion(runID string, result contracts.ExecutionResult) error
	PublishFailure(runID string, message string) error
}

type publisherProgressReporter struct {
	runID     string
	publisher EventPublisher
	interval  int
	lastSent  int
}

func (reporter *publisherProgressReporter) RunCompleted(completedRuns int, totalRuns int) error {
	if reporter.publisher == nil {
		return nil
	}
	if completedRuns != 1 &&
		completedRuns != totalRuns &&
		completedRuns-reporter.lastSent < reporter.interval {
		return nil
	}
	reporter.lastSent = completedRuns
	return reporter.publisher.PublishProgress(reporter.runID, completedRuns, totalRuns)
}

func progressInterval(runCount int) int {
	if runCount <= 1 {
		return 1
	}
	interval := runCount / 100
	if interval < 1 {
		return 1
	}
	return interval
}

type DispatchRequest struct {
	RunID      string                     `json:"runId"`
	BundleHash string                     `json:"bundleHash"`
	Request    contracts.ExecutionRequest `json:"request"`
}

func (request DispatchRequest) Validate() error {
	if strings.TrimSpace(request.RunID) == "" {
		return errors.New("runId is required")
	}
	if strings.TrimSpace(request.BundleHash) == "" {
		return errors.New("bundleHash is required")
	}
	return request.Request.Validate()
}

func (runtime *Runtime) loadBundle(bundleHash string) (engine.Bundle, error) {
	cachePath := filepath.Join(runtime.cacheDir, bundleHash+".json")
	if data, err := os.ReadFile(cachePath); err == nil {
		return decodeBundle(data, bundleHash)
	}

	if runtime.bundleReader == nil {
		return engine.Bundle{}, errors.New("bundle reader is not configured")
	}
	data, err := runtime.bundleReader.ReadBundle(bundleHash)
	if err != nil {
		return engine.Bundle{}, fmt.Errorf("failed to load bundle object: %w", err)
	}
	if err := os.MkdirAll(runtime.cacheDir, 0o755); err != nil {
		return engine.Bundle{}, fmt.Errorf("failed to create bundle cache: %w", err)
	}
	if err := os.WriteFile(cachePath, data, 0o644); err != nil {
		return engine.Bundle{}, fmt.Errorf("failed to write bundle cache: %w", err)
	}
	return decodeBundle(data, bundleHash)
}

func decodeBundle(data []byte, bundleHash string) (engine.Bundle, error) {
	var bundle engine.Bundle
	if err := json.Unmarshal(data, &bundle); err != nil {
		return engine.Bundle{}, fmt.Errorf("failed to decode bundle: %w", err)
	}
	if bundle.BundleHash != bundleHash {
		return engine.Bundle{}, errors.New("bundle hash mismatch")
	}
	return bundle, nil
}

type BundleReader interface {
	ReadBundle(bundleHash string) ([]byte, error)
}

func policyForRequest(request contracts.ExecutionRequest) (agent.Policy, error) {
	switch request.AgentPolicy {
	case "random-v1":
		return agent.RandomPolicy{}, nil
	case "scripted-v1":
		return &agent.ScriptedPolicy{Choices: append([]int{}, request.ScriptedChoices...)}, nil
	default:
		return nil, fmt.Errorf("unsupported agent policy: %s", request.AgentPolicy)
	}
}
func ExecuteRequest(
	bundle engine.Bundle,
	request contracts.ExecutionRequest,
	progress ProgressReporter,
) (contracts.ExecutionResult, error) {
	runs := make([]engine.RunResult, 0, request.RunCount)
	var agentVersion string
	for index := 0; index < request.RunCount; index++ {
		policy, err := policyForRequest(request)
		if err != nil {
			return contracts.ExecutionResult{}, err
		}
		agentVersion = policy.Version()
		result := engine.RunWithMaxSteps(bundle, request.SeedStart+uint64(index), policy, request.MaxSteps)
		if request.ExecutionMode == contracts.ExecutionModeOptimization {
			result.Trace = nil
		}
		runs = append(runs, result)
		if progress != nil {
			if err := progress.RunCompleted(index+1, request.RunCount); err != nil {
				return contracts.ExecutionResult{}, err
			}
		}
	}

	return contracts.ExecutionResult{
		BundleHash:    bundle.BundleHash,
		AgentPolicy:   request.AgentPolicy,
		AgentVersion:  agentVersion,
		ExecutionMode: request.ExecutionMode,
		SeedStart:     request.SeedStart,
		RunCount:      request.RunCount,
		MaxSteps:      request.MaxSteps,
		Runs:          runs,
	}, nil
}
