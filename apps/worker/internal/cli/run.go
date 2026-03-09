package cli

import (
	"encoding/json"
	"errors"
	"fmt"
	"io"
	"os"

	"github.com/athanor/apps/worker/internal/agent"
	"github.com/athanor/apps/worker/internal/contracts"
	"github.com/athanor/apps/worker/internal/engine"
)

func Run(args []string, stdout io.Writer, stderr io.Writer) int {
	if len(args) == 0 || args[0] != "run" {
		_, _ = fmt.Fprintln(stderr, "usage: worker run --bundle <path> --request <path>")
		return 1
	}

	bundlePath, requestPath, err := parseRunArgs(args[1:])
	if err != nil {
		_, _ = fmt.Fprintln(stderr, err.Error())
		return 1
	}

	bundle, request, err := loadInputs(bundlePath, requestPath)
	if err != nil {
		_, _ = fmt.Fprintln(stderr, err.Error())
		return 1
	}

	result, err := execute(bundle, request)
	if err != nil {
		_, _ = fmt.Fprintln(stderr, err.Error())
		return 1
	}

	encoder := json.NewEncoder(stdout)
	encoder.SetIndent("", "  ")
	if err := encoder.Encode(result); err != nil {
		_, _ = fmt.Fprintln(stderr, err.Error())
		return 1
	}
	return 0
}

func parseRunArgs(args []string) (string, string, error) {
	var bundlePath string
	var requestPath string

	for index := 0; index < len(args); index++ {
		switch args[index] {
		case "--bundle":
			index++
			if index >= len(args) {
				return "", "", errors.New("--bundle requires a path")
			}
			bundlePath = args[index]
		case "--request":
			index++
			if index >= len(args) {
				return "", "", errors.New("--request requires a path")
			}
			requestPath = args[index]
		default:
			return "", "", fmt.Errorf("unknown argument: %s", args[index])
		}
	}

	if bundlePath == "" || requestPath == "" {
		return "", "", errors.New("usage: worker run --bundle <path> --request <path>")
	}

	return bundlePath, requestPath, nil
}

func loadInputs(bundlePath string, requestPath string) (engine.Bundle, contracts.ExecutionRequest, error) {
	var bundle engine.Bundle
	bundleFile, err := os.Open(bundlePath)
	if err != nil {
		return engine.Bundle{}, contracts.ExecutionRequest{}, fmt.Errorf("failed to open bundle: %w", err)
	}
	defer bundleFile.Close()
	if err := json.NewDecoder(bundleFile).Decode(&bundle); err != nil {
		return engine.Bundle{}, contracts.ExecutionRequest{}, fmt.Errorf("failed to decode bundle: %w", err)
	}

	var request contracts.ExecutionRequest
	requestFile, err := os.Open(requestPath)
	if err != nil {
		return engine.Bundle{}, contracts.ExecutionRequest{}, fmt.Errorf("failed to open request: %w", err)
	}
	defer requestFile.Close()
	if err := json.NewDecoder(requestFile).Decode(&request); err != nil {
		return engine.Bundle{}, contracts.ExecutionRequest{}, fmt.Errorf("failed to decode request: %w", err)
	}
	if err := request.Validate(); err != nil {
		return engine.Bundle{}, contracts.ExecutionRequest{}, err
	}
	if request.BundleHash != bundle.BundleHash {
		return engine.Bundle{}, contracts.ExecutionRequest{}, errors.New("request bundle_hash does not match bundle")
	}

	return bundle, request, nil
}

func execute(bundle engine.Bundle, request contracts.ExecutionRequest) (contracts.ExecutionResult, error) {
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
