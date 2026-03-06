package com.athanor.api.compiler;

import java.io.IOException;
import java.nio.file.Path;
import java.time.Instant;

public interface BundleStore {

	StoreResult store(String bundleHash, byte[] canonicalBundleJson) throws IOException;

	record StoreResult(Path path, Instant storedAt, boolean created) {}
}
