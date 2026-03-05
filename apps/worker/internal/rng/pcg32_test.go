package rng

import (
	"sync"
	"testing"
)

func TestDeterministicSequenceWithSameSeed(t *testing.T) {
	a := New(1)
	b := New(1)
	for i := range 1000 {
		if a.NextUint32() != b.NextUint32() {
			t.Fatalf("sequence diverged at index %d", i)
		}
	}
}

func TestReferenceVectorsFromPCGOfficialDemo(t *testing.T) {
	// Reference vector from PCG minimal C demo for initstate=42, initseq=54.
	r := NewWithSequence(42, 54)
	want := []uint32{
		0xa15c02b7,
		0x7b47f409,
		0xba1d3330,
		0x83d2f293,
		0xbfa4784b,
		0xcbed606e,
	}

	for i, expected := range want {
		got := r.NextUint32()
		if got != expected {
			t.Fatalf("reference vector mismatch at index %d: got=0x%08x want=0x%08x", i, got, expected)
		}
	}
}

func TestNextFloat64Range(t *testing.T) {
	r := New(123)
	for range 10000 {
		v := r.NextFloat64()
		if v < 0.0 || v >= 1.0 {
			t.Fatalf("value out of range [0,1): %f", v)
		}
	}
}

func TestOneMillionCallsAreStableForSameSeed(t *testing.T) {
	const iterations = 1_000_000

	a := New(1)
	b := New(1)

	for i := range iterations {
		if a.NextUint32() != b.NextUint32() {
			t.Fatalf("sequence diverged at iteration %d", i)
		}
	}
}

func TestConcurrentGeneratorsWithSameSeedProduceIdenticalSequences(t *testing.T) {
	const iterations = 100_000
	const seed = uint64(9_001)

	var wg sync.WaitGroup
	start := make(chan struct{})
	results := make([][]uint32, 2)

	for idx := range 2 {
		wg.Add(1)
		go func(i int) {
			defer wg.Done()
			<-start

			r := New(seed)
			seq := make([]uint32, iterations)
			for j := range iterations {
				seq[j] = r.NextUint32()
			}
			results[i] = seq
		}(idx)
	}

	close(start)
	wg.Wait()

	for i := range iterations {
		if results[0][i] != results[1][i] {
			t.Fatalf("concurrent sequence mismatch at index %d: %d vs %d", i, results[0][i], results[1][i])
		}
	}
}
