package rng

const (
	multiplier      uint64 = 6364136223846793005
	defaultSequence uint64 = 54
)

// PCG32 is a deterministic 32-bit Permuted Congruential Generator.
type PCG32 struct {
	state uint64
	inc   uint64
}

// New builds a generator from a seed using the default stream sequence.
func New(seed uint64) *PCG32 {
	return NewWithSequence(seed, defaultSequence)
}

// NewWithSequence matches the reference seeding behavior from the minimal C implementation.
func NewWithSequence(initstate uint64, initseq uint64) *PCG32 {
	p := &PCG32{}
	p.seed(initstate, initseq)
	return p
}

func (p *PCG32) seed(initstate uint64, initseq uint64) {
	p.state = 0
	p.inc = (initseq << 1) | 1
	p.NextUint32()
	p.state += initstate
	p.NextUint32()
}

func (p *PCG32) NextUint32() uint32 {
	old := p.state
	p.state = old*multiplier + p.inc
	xorshifted := uint32(((old >> 18) ^ old) >> 27)
	rot := uint32(old >> 59)
	return (xorshifted >> rot) | (xorshifted << ((-rot) & 31))
}

func (p *PCG32) NextFloat64() float64 {
	return float64(p.NextUint32()) / float64(uint64(1)<<32)
}

func (p *PCG32) NextIntN(n int) int {
	if n <= 0 {
		return 0
	}
	return int(p.NextUint32() % uint32(n))
}
