package cli

import (
	"bytes"
	"encoding/json"
	"os"
	"path/filepath"
	"strings"
	"testing"

	"github.com/athanor/apps/worker/internal/contracts"
)

func TestRunEmitsAnalyticsResult(t *testing.T) {
	tempDir := t.TempDir()
	bundlePath, requestPath := fixtureFiles(t, tempDir, "analytics")

	var stdout bytes.Buffer
	var stderr bytes.Buffer
	exitCode := Run([]string{"run", "--bundle", bundlePath, "--request", requestPath}, &stdout, &stderr)
	if exitCode != 0 {
		t.Fatalf("expected success, got exit=%d stderr=%s", exitCode, stderr.String())
	}

	var result contracts.ExecutionResult
	if err := json.Unmarshal(stdout.Bytes(), &result); err != nil {
		t.Fatalf("failed to decode result: %v", err)
	}

	if result.ExecutionMode != contracts.ExecutionModeAnalytics {
		t.Fatalf("expected analytics mode, got %s", result.ExecutionMode)
	}
	if len(result.Runs) != 2 {
		t.Fatalf("expected two runs, got %d", len(result.Runs))
	}
	if len(result.Runs[0].Trace) == 0 {
		t.Fatal("expected analytics mode to include traces")
	}
}

func TestRunOmitsTraceInOptimizationMode(t *testing.T) {
	tempDir := t.TempDir()
	bundlePath, requestPath := fixtureFiles(t, tempDir, "optimization")

	var stdout bytes.Buffer
	var stderr bytes.Buffer
	exitCode := Run([]string{"run", "--bundle", bundlePath, "--request", requestPath}, &stdout, &stderr)
	if exitCode != 0 {
		t.Fatalf("expected success, got exit=%d stderr=%s", exitCode, stderr.String())
	}

	var result contracts.ExecutionResult
	if err := json.Unmarshal(stdout.Bytes(), &result); err != nil {
		t.Fatalf("failed to decode result: %v", err)
	}

	if len(result.Runs) != 2 {
		t.Fatalf("expected two runs, got %d", len(result.Runs))
	}
	if result.Runs[0].Trace != nil {
		t.Fatalf("expected optimization mode to omit trace, got %#v", result.Runs[0].Trace)
	}
}

func TestRunIsDeterministic(t *testing.T) {
	tempDir := t.TempDir()
	bundlePath, requestPath := fixtureFiles(t, tempDir, "analytics")

	var baseline string
	for index := 0; index < 25; index++ {
		var stdout bytes.Buffer
		var stderr bytes.Buffer
		exitCode := Run([]string{"run", "--bundle", bundlePath, "--request", requestPath}, &stdout, &stderr)
		if exitCode != 0 {
			t.Fatalf("expected success, got exit=%d stderr=%s", exitCode, stderr.String())
		}
		if index == 0 {
			baseline = stdout.String()
			continue
		}
		if stdout.String() != baseline {
			t.Fatal("worker CLI output is not deterministic")
		}
	}
}

func TestRunRejectsInvalidRequest(t *testing.T) {
	tempDir := t.TempDir()
	bundlePath := writeFile(
		t,
		tempDir,
		"bundle.json",
		`{"bundle_hash":"abc123","entry_node_id":"start","nodes":[{"id":"start","type":"decision","decision_options":[{"to":"end"}]},{"id":"end","type":"terminal","outcome":"success"}]}`,
	)
	requestPath := writeFile(
		t,
		tempDir,
		"request.json",
		`{"bundle_hash":"abc123","seed_start":0,"run_count":0,"agent_policy":"random-v1","execution_mode":"analytics","max_steps":0}`,
	)

	var stdout bytes.Buffer
	var stderr bytes.Buffer
	exitCode := Run([]string{"run", "--bundle", bundlePath, "--request", requestPath}, &stdout, &stderr)
	if exitCode == 0 {
		t.Fatal("expected invalid request to fail")
	}
	if !strings.Contains(stderr.String(), "run_count must be at least 1") {
		t.Fatalf("unexpected stderr: %s", stderr.String())
	}
}

func TestHealthcheckReturnsFailureWhenRedisIsUnavailable(t *testing.T) {
	previous := os.Getenv("ATHANOR_REDIS_ADDR")
	t.Cleanup(func() {
		if previous == "" {
			_ = os.Unsetenv("ATHANOR_REDIS_ADDR")
			return
		}
		_ = os.Setenv("ATHANOR_REDIS_ADDR", previous)
	})

	_ = os.Setenv("ATHANOR_REDIS_ADDR", "127.0.0.1:1")

	var stdout bytes.Buffer
	var stderr bytes.Buffer
	exitCode := Run([]string{"healthcheck"}, &stdout, &stderr)
	if exitCode == 0 {
		t.Fatal("expected healthcheck to fail when redis is unavailable")
	}
	if stderr.Len() == 0 {
		t.Fatal("expected healthcheck error output")
	}
}

func fixtureFiles(t *testing.T, dir string, mode string) (string, string) {
	t.Helper()

	bundlePath := writeFile(
		t,
		dir,
		"bundle.json",
		`{"bundle_hash":"abc123","entry_node_id":"start","header":{"bundle_spec_version":"0.1.0"},"nodes":[{"id":"start","type":"decision","decision_options":[{"to":"end"}]},{"id":"end","type":"terminal","outcome":"success"}]}`,
	)
	requestTemplate := `{"bundle_hash":"abc123","seed_start":1,"run_count":2,"agent_policy":"scripted-v1","scripted_choices":[0],"execution_mode":"%s","max_steps":10}`
	requestPath := writeFile(
		t,
		dir,
		"request.json",
		sprintf(requestTemplate, mode),
	)
	return bundlePath, requestPath
}

func writeFile(t *testing.T, dir string, name string, content string) string {
	t.Helper()
	path := filepath.Join(dir, name)
	if err := os.WriteFile(path, []byte(content), 0o644); err != nil {
		t.Fatalf("failed to write %s: %v", name, err)
	}
	return path
}

func sprintf(template string, value string) string {
	return strings.Replace(template, "%s", value, 1)
}
