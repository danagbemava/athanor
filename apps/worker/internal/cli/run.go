package cli

import (
	"context"
	"encoding/json"
	"errors"
	"fmt"
	"io"
	"os"
	"strings"
	"time"

	"github.com/athanor/apps/worker/internal/agent"
	"github.com/athanor/apps/worker/internal/contracts"
	"github.com/athanor/apps/worker/internal/engine"
	"github.com/athanor/apps/worker/internal/service"
	"github.com/redis/go-redis/v9"
)

func Run(args []string, stdout io.Writer, stderr io.Writer) int {
	if len(args) == 0 {
		_, _ = fmt.Fprintln(stderr, "usage: worker run --bundle <path> --request <path> | worker serve --redis-addr <host:port> [--cache-dir <path>] | worker healthcheck")
		return 1
	}

	switch args[0] {
	case "run":
		return runCommand(args[1:], stdout, stderr)
	case "serve":
		return serveCommand(args[1:], stderr)
	case "healthcheck":
		return healthcheckCommand(stderr)
	default:
		_, _ = fmt.Fprintln(stderr, "usage: worker run --bundle <path> --request <path> | worker serve --redis-addr <host:port> [--cache-dir <path>] | worker healthcheck")
		return 1
	}
}

func runCommand(args []string, stdout io.Writer, stderr io.Writer) int {
	bundlePath, requestPath, err := parseRunArgs(args)
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

func serveCommand(args []string, stderr io.Writer) int {
	config, err := parseServeArgs(args)
	if err != nil {
		_, _ = fmt.Fprintln(stderr, err.Error())
		return 1
	}
	if err := service.Serve(config); err != nil {
		_, _ = fmt.Fprintln(stderr, err.Error())
		return 1
	}
	return 0
}

func healthcheckCommand(stderr io.Writer) int {
	address := os.Getenv("ATHANOR_REDIS_ADDR")
	if address == "" {
		address = "127.0.0.1:6379"
	}

	context, cancel := context.WithTimeout(context.Background(), 5*time.Second)
	defer cancel()

	client := redis.NewClient(&redis.Options{Addr: address})
	defer client.Close()

	if err := client.Ping(context).Err(); err != nil {
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

func parseServeArgs(args []string) (service.RedisConfig, error) {
	config := service.RedisConfig{
		Address:             os.Getenv("ATHANOR_REDIS_ADDR"),
		DispatchStream:      envOrDefault("ATHANOR_WORKER_DISPATCH_STREAM", "athanor.worker.dispatch"),
		DispatchConsumerGrp: envOrDefault("ATHANOR_WORKER_DISPATCH_GROUP", "athanor-worker"),
		DispatchConsumer:    envOrDefault("ATHANOR_WORKER_DISPATCH_CONSUMER", "worker-1"),
		EventStream:         envOrDefault("ATHANOR_WORKER_EVENT_STREAM", "athanor.worker.events"),
		CacheDir:            ".worker-cache",
		ObjectStore: service.ObjectStoreConfig{
			Endpoint:  envOrDefault("ATHANOR_S3_ENDPOINT", "127.0.0.1:9000"),
			Region:    envOrDefault("ATHANOR_S3_REGION", "us-east-1"),
			Bucket:    envOrDefault("ATHANOR_S3_BUCKET", "athanor-bundles"),
			AccessKey: envOrDefault("ATHANOR_S3_ACCESS_KEY", "minioadmin"),
			SecretKey: envOrDefault("ATHANOR_S3_SECRET_KEY", "minioadmin"),
			UseSSL:    strings.HasPrefix(envOrDefault("ATHANOR_S3_ENDPOINT", ""), "https://"),
		},
	}

	for index := 0; index < len(args); index++ {
		switch args[index] {
		case "--redis-addr":
			index++
			if index >= len(args) {
				return service.RedisConfig{}, errors.New("--redis-addr requires an address")
			}
			config.Address = args[index]
		case "--cache-dir":
			index++
			if index >= len(args) {
				return service.RedisConfig{}, errors.New("--cache-dir requires a path")
			}
			config.CacheDir = args[index]
		default:
			return service.RedisConfig{}, fmt.Errorf("unknown argument: %s", args[index])
		}
	}

	if config.Address == "" {
		return service.RedisConfig{}, errors.New("usage: worker serve --redis-addr <host:port> [--cache-dir <path>]")
	}

	return config, nil
}

func envOrDefault(key string, fallback string) string {
	value := os.Getenv(key)
	if value == "" {
		return fallback
	}
	return value
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
