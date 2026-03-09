package com.athanor.api.compiler;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "bundles")
public class BundleRecord {

	@Id
	@Column(name = "bundle_hash", length = 64, nullable = false)
	private String bundleHash;

	@Column(name = "created_at", nullable = false)
	private Instant createdAt;

	@Column(name = "last_accessed_at", nullable = false)
	private Instant lastAccessedAt;

	@Column(name = "reference_count", nullable = false)
	private int referenceCount;

	@Enumerated(EnumType.STRING)
	@Column(name = "retention_class", nullable = false, length = 16)
	private BundleRetentionClass retentionClass;

	@Column(name = "source_scenario_id")
	private UUID sourceScenarioId;

	@Column(name = "source_version_id")
	private UUID sourceVersionId;

	@Column(name = "source_version_number")
	private Integer sourceVersionNumber;

	@Column(name = "compiler_version", nullable = false, length = 128)
	private String compilerVersion;

	protected BundleRecord() {}

	BundleRecord(
		String bundleHash,
		Instant createdAt,
		Instant lastAccessedAt,
		int referenceCount,
		BundleRetentionClass retentionClass,
		UUID sourceScenarioId,
		UUID sourceVersionId,
		Integer sourceVersionNumber,
		String compilerVersion
	) {
		this.bundleHash = bundleHash;
		this.createdAt = createdAt;
		this.lastAccessedAt = lastAccessedAt;
		this.referenceCount = referenceCount;
		this.retentionClass = retentionClass;
		this.sourceScenarioId = sourceScenarioId;
		this.sourceVersionId = sourceVersionId;
		this.sourceVersionNumber = sourceVersionNumber;
		this.compilerVersion = compilerVersion;
	}

	String bundleHash() {
		return bundleHash;
	}

	Instant createdAt() {
		return createdAt;
	}

	Instant lastAccessedAt() {
		return lastAccessedAt;
	}

	void setLastAccessedAt(Instant lastAccessedAt) {
		this.lastAccessedAt = lastAccessedAt;
	}

	int referenceCount() {
		return referenceCount;
	}

	void setReferenceCount(int referenceCount) {
		this.referenceCount = referenceCount;
	}

	BundleRetentionClass retentionClass() {
		return retentionClass;
	}

	void setRetentionClass(BundleRetentionClass retentionClass) {
		this.retentionClass = retentionClass;
	}

	UUID sourceScenarioId() {
		return sourceScenarioId;
	}

	void setSourceScenarioId(UUID sourceScenarioId) {
		this.sourceScenarioId = sourceScenarioId;
	}

	UUID sourceVersionId() {
		return sourceVersionId;
	}

	void setSourceVersionId(UUID sourceVersionId) {
		this.sourceVersionId = sourceVersionId;
	}

	Integer sourceVersionNumber() {
		return sourceVersionNumber;
	}

	void setSourceVersionNumber(Integer sourceVersionNumber) {
		this.sourceVersionNumber = sourceVersionNumber;
	}

	String compilerVersion() {
		return compilerVersion;
	}

	void setCompilerVersion(String compilerVersion) {
		this.compilerVersion = compilerVersion;
	}
}
