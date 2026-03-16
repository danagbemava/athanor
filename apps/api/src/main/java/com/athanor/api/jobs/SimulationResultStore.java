package com.athanor.api.jobs;

import java.io.IOException;

interface SimulationResultStore {
	byte[] read(String resultKey) throws IOException;
}
