package com.athanor.api.compiler;

import java.time.Instant;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

interface BundleRecordJpaRepository extends JpaRepository<BundleRecord, String> {

	@Query(
		"""
		SELECT bundle
		FROM BundleRecord bundle
		WHERE (bundle.retentionClass = com.athanor.api.compiler.BundleRetentionClass.DRAFT AND bundle.lastAccessedAt < :draftCutoff)
		   OR (bundle.retentionClass = com.athanor.api.compiler.BundleRetentionClass.ORPHAN AND bundle.lastAccessedAt < :orphanCutoff)
		ORDER BY bundle.lastAccessedAt ASC
		"""
	)
	List<BundleRecord> findExpired(
		@Param("draftCutoff") Instant draftCutoff,
		@Param("orphanCutoff") Instant orphanCutoff
	);
}
