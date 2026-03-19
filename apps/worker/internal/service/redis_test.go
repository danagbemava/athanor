package service

import (
	"context"
	"errors"
	"net"
	"testing"
	"time"

	"github.com/redis/go-redis/v9"
)

func TestIsIdleRedisRead(t *testing.T) {
	if !isIdleRedisRead(redis.Nil) {
		t.Fatalf("expected redis.Nil to be treated as idle")
	}

	timeoutErr := &net.DNSError{IsTimeout: true}
	if !isIdleRedisRead(timeoutErr) {
		t.Fatalf("expected network timeout to be treated as idle")
	}

	if isIdleRedisRead(errors.New("boom")) {
		t.Fatalf("expected non-timeout errors to remain fatal")
	}
}

func TestClaimRunExecutionSkipsDuplicateRedeliveryWhileClaimExists(t *testing.T) {
	store := newMemoryRunStateStore()

	claimed, err := claimRunExecution(context.Background(), store, "run-1", "worker-a")
	if err != nil {
		t.Fatalf("first claim failed: %v", err)
	}
	if !claimed {
		t.Fatalf("expected first claim to succeed")
	}

	claimed, err = claimRunExecution(context.Background(), store, "run-1", "worker-b")
	if err != nil {
		t.Fatalf("duplicate claim failed: %v", err)
	}
	if claimed {
		t.Fatalf("expected duplicate redelivery to be skipped while claim exists")
	}
}

func TestClaimRunExecutionSkipsCompletedRuns(t *testing.T) {
	store := newMemoryRunStateStore()
	store.values[runStatusKey("run-1")] = "completed"

	claimed, err := claimRunExecution(context.Background(), store, "run-1", "worker-a")
	if err != nil {
		t.Fatalf("claim failed: %v", err)
	}
	if claimed {
		t.Fatalf("expected completed run to be skipped")
	}
	if _, exists := store.values[runClaimKey("run-1")]; exists {
		t.Fatalf("expected completed run claim to be cleaned up")
	}
}

func TestReleaseRunExecutionClaimAllowsRetryAfterFailure(t *testing.T) {
	store := newMemoryRunStateStore()

	claimed, err := claimRunExecution(context.Background(), store, "run-1", "worker-a")
	if err != nil {
		t.Fatalf("initial claim failed: %v", err)
	}
	if !claimed {
		t.Fatalf("expected initial claim to succeed")
	}

	if err := releaseRunExecutionClaim(context.Background(), store, "run-1"); err != nil {
		t.Fatalf("release claim failed: %v", err)
	}

	claimed, err = claimRunExecution(context.Background(), store, "run-1", "worker-b")
	if err != nil {
		t.Fatalf("retry claim failed: %v", err)
	}
	if !claimed {
		t.Fatalf("expected released claim to allow retry")
	}
}

type memoryRunStateStore struct {
	values map[string]string
}

func newMemoryRunStateStore() *memoryRunStateStore {
	return &memoryRunStateStore{values: map[string]string{}}
}

func (store *memoryRunStateStore) Get(ctx context.Context, key string) (string, error) {
	value, ok := store.values[key]
	if !ok {
		return "", redis.Nil
	}
	return value, nil
}

func (store *memoryRunStateStore) SetNX(
	ctx context.Context,
	key string,
	value string,
	expiration time.Duration,
) (bool, error) {
	if _, exists := store.values[key]; exists {
		return false, nil
	}
	store.values[key] = value
	return true, nil
}

func (store *memoryRunStateStore) Del(ctx context.Context, keys ...string) error {
	for _, key := range keys {
		delete(store.values, key)
	}
	return nil
}
