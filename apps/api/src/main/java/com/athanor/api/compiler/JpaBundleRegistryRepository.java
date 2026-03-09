package com.athanor.api.compiler;

import jakarta.transaction.Transactional;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Repository;

@Repository
@Transactional
public class JpaBundleRegistryRepository implements BundleRegistryRepository {

	private final BundleRecordJpaRepository repository;

	public JpaBundleRegistryRepository(BundleRecordJpaRepository repository) {
		this.repository = repository;
	}

	@Override
	public BundleMetadata upsertOnStore(BundleMetadata metadata, Instant accessedAt) {
		String bundleHash = BundleHashs.requireValid(metadata.bundleHash());
		BundleRecord record = repository.findById(bundleHash).orElse(null);
		if (record == null) {
			record = new BundleRecord(
				bundleHash,
				metadata.storedAt(),
				accessedAt,
				metadata.referenceCount(),
				metadata.retentionClass(),
				metadata.scenarioId(),
				metadata.versionId(),
				metadata.versionNumber(),
				metadata.compilerVersion()
			);
			return toMetadata(repository.save(record));
		}

		record.setLastAccessedAt(accessedAt);
		record.setReferenceCount(record.referenceCount() + 1);
		record.setRetentionClass(
			BundleRetentionClass.strongest(record.retentionClass(), metadata.retentionClass())
		);
		if (record.sourceScenarioId() == null) {
			record.setSourceScenarioId(metadata.scenarioId());
		}
		if (record.sourceVersionId() == null) {
			record.setSourceVersionId(metadata.versionId());
		}
		if (record.sourceVersionNumber() == null) {
			record.setSourceVersionNumber(metadata.versionNumber());
		}
		if (record.compilerVersion() == null) {
			record.setCompilerVersion(metadata.compilerVersion());
		}
		return toMetadata(repository.save(record));
	}

	@Override
	@Transactional(Transactional.TxType.SUPPORTS)
	public Optional<BundleMetadata> find(String bundleHash) {
		return repository.findById(BundleHashs.requireValid(bundleHash)).map(this::toMetadata);
	}

	@Override
	public BundleMetadata markAccessed(String bundleHash, Instant accessedAt) {
		BundleRecord record = repository
			.findById(BundleHashs.requireValid(bundleHash))
			.orElseThrow(() -> new BundleNotFoundException("bundleHash not found"));
		record.setLastAccessedAt(accessedAt);
		return toMetadata(repository.save(record));
	}

	@Override
	@Transactional(Transactional.TxType.SUPPORTS)
	public List<BundleMetadata> findExpiredBundles(Instant draftCutoff, Instant orphanCutoff) {
		return repository.findExpired(draftCutoff, orphanCutoff).stream().map(this::toMetadata).toList();
	}

	@Override
	public void delete(String bundleHash) {
		repository.deleteById(BundleHashs.requireValid(bundleHash));
	}

	private BundleMetadata toMetadata(BundleRecord record) {
		return new BundleMetadata(
			record.bundleHash(),
			record.sourceScenarioId(),
			record.sourceVersionId(),
			record.sourceVersionNumber(),
			record.createdAt(),
			record.retentionClass(),
			record.lastAccessedAt(),
			record.referenceCount(),
			record.compilerVersion()
		);
	}
}
