package com.athanor.api.jobs;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import tools.jackson.databind.ObjectMapper;

public final class SimulationResultStoreTestFactory {

	private SimulationResultStoreTestFactory() {}

	public static MutableSimulationResultStore create() {
		return new MutableSimulationResultStore();
	}

	static final class MutableSimulationResultStore implements SimulationResultStore {

		private final Map<String, byte[]> payloads = new ConcurrentHashMap<>();

		@Override
		public byte[] read(String resultKey) throws IOException {
			byte[] payload = payloads.get(resultKey);
			if (payload == null) {
				throw new IOException("simulation result not found");
			}
			return payload;
		}

		void put(String resultKey, byte[] payload) {
			payloads.put(resultKey, payload);
		}

		void putJson(String resultKey, Object payload, ObjectMapper objectMapper) {
			try {
				put(resultKey, objectMapper.writeValueAsBytes(payload));
			} catch (RuntimeException exception) {
				throw new IllegalStateException("failed to serialize simulation result", exception);
			}
		}
	}
}
