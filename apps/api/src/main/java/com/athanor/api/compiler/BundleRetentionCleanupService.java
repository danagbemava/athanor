package com.athanor.api.compiler;

import java.io.IOException;
import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;

public class BundleRetentionCleanupService {

	private static final Logger LOGGER = LoggerFactory.getLogger(
		BundleRetentionCleanupService.class
	);

	private final BundleRegistryRepository registryRepository;
	private final BundleObjectStore objectStore;
	private final BundleStorageProperties properties;
	private final Clock clock;

	public BundleRetentionCleanupService(
		BundleRegistryRepository registryRepository,
		BundleObjectStore objectStore,
		BundleStorageProperties properties,
		Clock clock
	) {
		this.registryRepository = registryRepository;
		this.objectStore = objectStore;
		this.properties = properties;
		this.clock = clock;
	}

	@Scheduled(cron = "${athanor.bundle.retention.schedule:0 0 2 * * *}")
	public void scheduledCleanup() {
		if (!properties.getRetention().isEnabled()) {
			return;
		}
		runCleanup();
	}

	CleanupReport runCleanup() {
		Instant now = clock.instant();
		Instant draftCutoff = now.minus(properties.getRetention().getDraftDays(), ChronoUnit.DAYS);
		Instant orphanCutoff = now.minus(properties.getRetention().getOrphanDays(), ChronoUnit.DAYS);
		List<BundleMetadata> expired = registryRepository.findExpiredBundles(draftCutoff, orphanCutoff);
		List<String> deleted = new ArrayList<>();
		List<String> missing = new ArrayList<>();
		List<String> failed = new ArrayList<>();

		for (BundleMetadata metadata : expired) {
			try {
				BundleObjectStore.DeleteResult deleteResult = objectStore.delete(metadata.bundleHash());
				registryRepository.delete(metadata.bundleHash());
				if (!deleteResult.existed()) {
					LOGGER.warn(
						"removed bundle registry entry for missing object {} class={}",
						metadata.bundleHash(),
						metadata.retentionClass().value()
					);
					missing.add(metadata.bundleHash());
					continue;
				}
				LOGGER.info(
					"deleted expired bundle {} class={}",
					metadata.bundleHash(),
					metadata.retentionClass().value()
				);
				deleted.add(metadata.bundleHash());
			} catch (IOException exception) {
				LOGGER.error(
					"failed to delete expired bundle {} class={}",
					metadata.bundleHash(),
					metadata.retentionClass().value(),
					exception
				);
				failed.add(metadata.bundleHash());
			}
		}

		return new CleanupReport(deleted, missing, failed);
	}

	record CleanupReport(
		List<String> deletedBundleHashes,
		List<String> missingObjectBundleHashes,
		List<String> failedBundleHashes
	) {}
}
