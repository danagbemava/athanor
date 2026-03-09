package com.athanor.api.compiler;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Optional;

public interface BundleStore {

	StoreResult store(BundleMetadata metadata, byte[] canonicalBundleJson) throws IOException;

	Optional<StoredBundle> find(String bundleHash) throws IOException;

	BundleMetadata readMetadata(String bundleHash) throws IOException;

	byte[] readContent(String bundleHash) throws IOException;

	record StoreResult(
		Path contentPath,
		Path metadataPath,
		BundleMetadata metadata,
		boolean created
	) {}

	record StoredBundle(
		BundleMetadata metadata,
		Path contentPath,
		Path metadataPath
	) {}
}
