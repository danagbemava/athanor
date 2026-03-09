package com.athanor.api.simulation;

final class Pcg32 {

	private static final long MULTIPLIER = 6364136223846793005L;
	private static final long DEFAULT_SEQUENCE = 54L;

	private long state;
	private long inc;

	Pcg32(long seed) {
		seed(seed, DEFAULT_SEQUENCE);
	}

	private void seed(long initState, long initSequence) {
		state = 0L;
		inc = (initSequence << 1) | 1L;
		nextUint32();
		state += initState;
		nextUint32();
	}

	private long nextUint32() {
		long old = state;
		state = (old * MULTIPLIER) + inc;
		int xorshifted = (int) ((((old >>> 18) ^ old) >>> 27) & 0xffffffffL);
		int rot = (int) (old >>> 59);
		return Integer.toUnsignedLong(Integer.rotateRight(xorshifted, rot));
	}

	double nextFloat64() {
		return nextUint32() / (double) (1L << 32);
	}

	int nextIntN(int bound) {
		if (bound <= 0) {
			return 0;
		}
		return (int) (nextUint32() % bound);
	}
}
