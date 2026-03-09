package com.athanor.api.compiler;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

interface BundleRegistryRepository {

	BundleMetadata upsertOnStore(BundleMetadata metadata, Instant accessedAt);

	Optional<BundleMetadata> find(String bundleHash);

	BundleMetadata markAccessed(String bundleHash, Instant accessedAt);

	List<BundleMetadata> findExpiredBundles(Instant draftCutoff, Instant orphanCutoff);

	void delete(String bundleHash);
}
