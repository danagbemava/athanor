package rng

// PCG32 is a scaffold placeholder for the deterministic RNG contract.
type PCG32 struct {
	state uint64
	inc   uint64
}

func New(seed uint64) *PCG32 {
	return &PCG32{state: seed, inc: 1442695040888963407}
}

func (p *PCG32) NextUint32() uint32 {
	old := p.state
	p.state = old*6364136223846793005 + (p.inc | 1)
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
