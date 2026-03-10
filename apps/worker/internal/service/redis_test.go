package service

import (
	"errors"
	"net"
	"testing"

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
