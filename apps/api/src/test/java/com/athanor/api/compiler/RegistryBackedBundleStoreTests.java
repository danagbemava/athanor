package com.athanor.api.compiler;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest(
	properties = {
		"spring.flyway.enabled=true",
		"spring.datasource.url=jdbc:h2:mem:bundles;MODE=PostgreSQL;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE;DATABASE_TO_LOWER=TRUE",
		"spring.datasource.driver-class-name=org.h2.Driver",
		"spring.datasource.username=sa",
		"spring.datasource.password="
	}
)
class RegistryBackedBundleStoreTests {

	@Autowired
	private JpaBundleRegistryRepository repository;

	private RecordingObjectStore objectStore;
	private MutableClock clock;
	private RegistryBackedBundleStore bundleStore;
	private BundleStorageProperties properties;
	private BundleRetentionCleanupService cleanupService;

	@BeforeEach
	void setUp() {
		objectStore = new RecordingObjectStore();
		clock = new MutableClock(Instant.parse("2026-03-09T12:00:00Z"));
		bundleStore = new RegistryBackedBundleStore(repository, objectStore, clock);
		properties = new BundleStorageProperties();
		cleanupService = new BundleRetentionCleanupService(repository, objectStore, properties, clock);
	}

	@Test
	void duplicateStoresReuseObjectAndIncrementReferenceCount() throws Exception {
		BundleMetadata metadata = metadata("aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa");

		BundleStore.StoreResult first = bundleStore.store(metadata, payload("one"));
		clock.advanceSeconds(10);
		BundleStore.StoreResult second = bundleStore.store(metadata, payload("one"));

		assertTrue(first.created());
		assertFalse(second.created());
		assertEquals(1, objectStore.putCount(metadata.bundleHash()));
		assertEquals(2, second.metadata().referenceCount());
		assertEquals(first.metadata().storedAt(), second.metadata().storedAt());
	}

	@Test
	void metadataAndContentReadsUpdateLastAccessedAt() throws Exception {
		BundleMetadata metadata = metadata("bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb");
		bundleStore.store(metadata, payload("bundle"));

		BundleMetadata firstRead = bundleStore.readMetadata(metadata.bundleHash());
		clock.advanceSeconds(5);
		byte[] content = bundleStore.readContent(metadata.bundleHash());
		BundleMetadata afterContentRead = repository.find(metadata.bundleHash()).orElseThrow();

		assertArrayEquals(payload("bundle"), content);
		assertTrue(afterContentRead.lastAccessedAt().isAfter(firstRead.lastAccessedAt()));
	}

	@Test
	void cleanupDeletesExpiredDraftAndOrphanButKeepsPublished() throws Exception {
		BundleMetadata draft = metadata(
			"cccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccc",
			BundleRetentionClass.DRAFT,
			clock.instant().minusSeconds(91L * 24 * 60 * 60)
		);
		BundleMetadata orphan = metadata(
			"dddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddd",
			BundleRetentionClass.ORPHAN,
			clock.instant().minusSeconds(8L * 24 * 60 * 60)
		);
		BundleMetadata published = metadata(
			"eeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeee",
			BundleRetentionClass.PUBLISHED,
			clock.instant().minusSeconds(200L * 24 * 60 * 60)
		);

		storeExpired(draft);
		storeExpired(orphan);
		storeExpired(published);

		BundleRetentionCleanupService.CleanupReport report = cleanupService.runCleanup();

		assertEquals(List.of(draft.bundleHash(), orphan.bundleHash()), report.deletedBundleHashes());
		assertTrue(repository.find(draft.bundleHash()).isEmpty());
		assertTrue(repository.find(orphan.bundleHash()).isEmpty());
		assertTrue(repository.find(published.bundleHash()).isPresent());
	}

	@Test
	void cleanupRemovesRegistryWhenObjectIsAlreadyMissing() throws Exception {
		BundleMetadata orphan = metadata(
			"ffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffff",
			BundleRetentionClass.ORPHAN,
			clock.instant().minusSeconds(8L * 24 * 60 * 60)
		);
		storeExpired(orphan);
		objectStore.objects.remove(orphan.bundleHash());

		BundleRetentionCleanupService.CleanupReport report = cleanupService.runCleanup();

		assertEquals(List.of(orphan.bundleHash()), report.missingObjectBundleHashes());
		assertTrue(repository.find(orphan.bundleHash()).isEmpty());
	}

	@Test
	void cleanupKeepsRegistryEntryWhenDeletionFails() throws Exception {
		BundleMetadata draft = metadata(
			"9999999999999999999999999999999999999999999999999999999999999999",
			BundleRetentionClass.DRAFT,
			clock.instant().minusSeconds(91L * 24 * 60 * 60)
		);
		storeExpired(draft);
		objectStore.failDeletesFor.add(draft.bundleHash());

		BundleRetentionCleanupService.CleanupReport report = cleanupService.runCleanup();

		assertEquals(List.of(draft.bundleHash()), report.failedBundleHashes());
		assertTrue(repository.find(draft.bundleHash()).isPresent());
	}

	private void storeExpired(BundleMetadata metadata) throws Exception {
		bundleStore.store(metadata, payload(metadata.bundleHash()));
		repository.markAccessed(metadata.bundleHash(), metadata.lastAccessedAt());
	}

	private BundleMetadata metadata(String bundleHash) {
		return metadata(bundleHash, BundleRetentionClass.DRAFT, clock.instant());
	}

	private BundleMetadata metadata(
		String bundleHash,
		BundleRetentionClass retentionClass,
		Instant lastAccessedAt
	) {
		return new BundleMetadata(
			bundleHash,
			UUID.fromString("11111111-1111-1111-1111-111111111111"),
			UUID.fromString("22222222-2222-2222-2222-222222222222"),
			1,
			clock.instant(),
			retentionClass,
			lastAccessedAt,
			1,
			"0.0.1-SNAPSHOT"
		);
	}

	private byte[] payload(String value) {
		return value.getBytes(StandardCharsets.UTF_8);
	}

	private static final class RecordingObjectStore implements BundleObjectStore {

		private final Map<String, byte[]> objects = new HashMap<>();
		private final Map<String, Integer> putCounts = new HashMap<>();
		private final java.util.Set<String> failDeletesFor = new java.util.HashSet<>();

		@Override
		public boolean putIfAbsent(String bundleHash, byte[] canonicalBundleJson) {
			boolean created = objects.putIfAbsent(bundleHash, canonicalBundleJson) == null;
			if (created) {
				putCounts.merge(bundleHash, 1, Integer::sum);
			}
			return created;
		}

		@Override
		public byte[] read(String bundleHash) throws IOException {
			byte[] content = objects.get(bundleHash);
			if (content == null) {
				throw new IOException("missing object");
			}
			return content;
		}

		@Override
		public DeleteResult delete(String bundleHash) throws IOException {
			if (failDeletesFor.contains(bundleHash)) {
				throw new IOException("delete failed");
			}
			return new DeleteResult(objects.remove(bundleHash) != null);
		}

		private int putCount(String bundleHash) {
			return putCounts.getOrDefault(bundleHash, 0);
		}
	}

	private static final class MutableClock extends Clock {

		private Instant currentInstant;

		private MutableClock(Instant currentInstant) {
			this.currentInstant = currentInstant;
		}

		@Override
		public ZoneOffset getZone() {
			return ZoneOffset.UTC;
		}

		@Override
		public Clock withZone(java.time.ZoneId zone) {
			return this;
		}

		@Override
		public Instant instant() {
			return currentInstant;
		}

		private void advanceSeconds(long seconds) {
			currentInstant = currentInstant.plusSeconds(seconds);
		}
	}
}
