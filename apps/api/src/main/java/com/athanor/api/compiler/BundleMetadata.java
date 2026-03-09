package com.athanor.api.compiler;

import java.time.Instant;
import java.util.UUID;

public record BundleMetadata(
	String bundleHash,
	UUID scenarioId,
	UUID versionId,
	Integer versionNumber,
	Instant storedAt,
	BundleRetentionClass retentionClass,
	Instant lastAccessedAt,
	int referenceCount,
	String compilerVersion
) {}
