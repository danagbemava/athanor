package rng

import "testing"

func TestDeterministicSequenceWithSameSeed(t *testing.T) {
	a := New(1)
	b := New(1)
	for i := 0; i < 1000; i++ {
		if a.NextUint32() != b.NextUint32() {
			t.Fatalf("sequence diverged at index %d", i)
		}
	}
}

func TestReferenceVectorsPlaceholder(t *testing.T) {
	t.Skip("TODO(NEX-205): add PCG reference vectors from official source")
}
