package com.athanor.api;

import org.junit.jupiter.api.Test;
import org.springframework.modulith.core.ApplicationModules;

class AthanorApiModularityTests {

	@Test
	void verifiesModularStructure() {
		ApplicationModules.of(AthanorApiApplication.class).verify();
	}
}
