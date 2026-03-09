package com.athanor.api.compiler;

import java.io.IOException;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.util.Optional;

public class RegistryBackedBundleStore implements BundleStore {

	private final BundleRegistryRepository registryRepository;
	private final BundleObjectStore objectStore;
	private final Clock clock;

	public RegistryBackedBundleStore(
		BundleRegistryRepository registryRepository,
		BundleObjectStore objectStore,
		Clock clock
	) {
		this.registryRepository = registryRepository;
		this.objectStore = objectStore;
		this.clock = clock;
	}

	@Override
	public StoreResult store(BundleMetadata metadata, byte[] canonicalBundleJson) throws IOException {
		String bundleHash = BundleHashs.requireValid(metadata.bundleHash());
		boolean createdObject = false;
		try {
			createdObject = objectStore.putIfAbsent(bundleHash, canonicalBundleJson);
			BundleMetadata persisted = registryRepository.upsertOnStore(metadata, clock.instant());
			return new StoreResult(null, null, persisted, createdObject);
		} catch (RuntimeException exception) {
			if (createdObject) {
				try {
					objectStore.delete(bundleHash);
				} catch (IOException ignored) {}
			}
			throw exception;
		}
	}

	@Override
	public Optional<StoredBundle> find(String bundleHash) throws IOException {
		BundleMetadata metadata = registryRepository
			.find(BundleHashs.requireValid(bundleHash))
			.orElse(null);
		if (metadata == null) {
			return Optional.empty();
		}
		return Optional.of(new StoredBundle(metadata, null, null));
	}

	@Override
	public BundleMetadata readMetadata(String bundleHash) throws IOException {
		String normalizedHash = BundleHashs.requireValid(bundleHash);
		BundleMetadata metadata = registryRepository.find(normalizedHash).orElse(null);
		if (metadata == null) {
			throw new BundleNotFoundException("bundleHash not found");
		}
		return registryRepository.markAccessed(normalizedHash, clock.instant());
	}

	@Override
	public byte[] readContent(String bundleHash) throws IOException {
		String normalizedHash = BundleHashs.requireValid(bundleHash);
		if (registryRepository.find(normalizedHash).isEmpty()) {
			throw new BundleNotFoundException("bundleHash not found");
		}
		byte[] content = objectStore.read(normalizedHash);
		registryRepository.markAccessed(normalizedHash, clock.instant());
		return content;
	}
}
