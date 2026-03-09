package com.athanor.api.compiler;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Instant;
import java.util.Optional;
import tools.jackson.databind.ObjectMapper;

public class FilesystemBundleStore implements BundleStore {

	private final Path rootDirectory;
	private final ObjectMapper objectMapper;

	public FilesystemBundleStore(ObjectMapper objectMapper) {
		this(Path.of("build", "athanor", "bundles"), objectMapper);
	}

	public FilesystemBundleStore(Path rootDirectory, ObjectMapper objectMapper) {
		this.rootDirectory = rootDirectory.toAbsolutePath().normalize();
		this.objectMapper = objectMapper;
	}

	@Override
	public StoreResult store(BundleMetadata metadata, byte[] canonicalBundleJson) throws IOException {
		Files.createDirectories(rootDirectory);
		Path bundlePath = contentPath(metadata.bundleHash());
		Path metadataPath = metadataPath(metadata.bundleHash());
		boolean created = false;

		if (!Files.exists(bundlePath)) {
			Files.write(
				bundlePath,
				canonicalBundleJson,
				StandardOpenOption.CREATE_NEW,
				StandardOpenOption.WRITE
			);
			created = true;
		}

		BundleMetadata persistedMetadata = metadata;
		if (Files.exists(metadataPath)) {
			BundleMetadata existing = readMetadata(metadataPath);
			persistedMetadata = new BundleMetadata(
				existing.bundleHash(),
				existing.scenarioId(),
				existing.versionId(),
				existing.versionNumber(),
				existing.storedAt(),
				BundleRetentionClass.strongest(existing.retentionClass(), metadata.retentionClass()),
				Instant.now(),
				existing.referenceCount() + 1,
				existing.compilerVersion()
			);
			writeMetadata(metadataPath, persistedMetadata, true);
		} else {
			writeMetadata(metadataPath, metadata);
		}

		return new StoreResult(bundlePath, metadataPath, persistedMetadata, created);
	}

	@Override
	public Optional<StoredBundle> find(String bundleHash) throws IOException {
		Path bundlePath = contentPath(bundleHash);
		Path metadataPath = metadataPath(bundleHash);
		if (!Files.exists(bundlePath) || !Files.exists(metadataPath)) {
			return Optional.empty();
		}
		return Optional.of(new StoredBundle(readMetadata(metadataPath), bundlePath, metadataPath));
	}

	@Override
	public BundleMetadata readMetadata(String bundleHash) throws IOException {
		Path metadataPath = metadataPath(bundleHash);
		if (!Files.exists(metadataPath)) {
			throw new BundleNotFoundException("bundleHash not found");
		}
		BundleMetadata metadata = readMetadata(metadataPath);
		BundleMetadata updated = new BundleMetadata(
			metadata.bundleHash(),
			metadata.scenarioId(),
			metadata.versionId(),
			metadata.versionNumber(),
			metadata.storedAt(),
			metadata.retentionClass(),
			Instant.now(),
			metadata.referenceCount(),
			metadata.compilerVersion()
		);
		writeMetadata(metadataPath, updated, true);
		return updated;
	}

	@Override
	public byte[] readContent(String bundleHash) throws IOException {
		Path bundlePath = contentPath(bundleHash);
		if (!Files.exists(bundlePath)) {
			throw new BundleNotFoundException("bundleHash not found");
		}
		byte[] content = Files.readAllBytes(bundlePath);
		readMetadata(bundleHash);
		return content;
	}

	private Path contentPath(String bundleHash) {
		return resolveBundlePath(bundleHash, ".json");
	}

	private Path metadataPath(String bundleHash) {
		return resolveBundlePath(bundleHash, ".metadata.json");
	}

	private Path resolveBundlePath(String bundleHash, String suffix) {
		String normalizedHash = BundleHashs.requireValid(bundleHash);
		Path resolved = rootDirectory.resolve(normalizedHash + suffix).normalize();
		if (!resolved.startsWith(rootDirectory)) {
			throw new IllegalArgumentException("invalid bundle hash");
		}
		return resolved;
	}

	private void writeMetadata(Path metadataPath, BundleMetadata metadata) throws IOException {
		writeMetadata(metadataPath, metadata, false);
	}

	private void writeMetadata(Path metadataPath, BundleMetadata metadata, boolean overwrite)
		throws IOException {
		Files.writeString(
			metadataPath,
			objectMapper.writeValueAsString(metadata),
			overwrite ? StandardOpenOption.TRUNCATE_EXISTING : StandardOpenOption.CREATE_NEW,
			StandardOpenOption.CREATE,
			StandardOpenOption.WRITE
		);
	}

	private BundleMetadata readMetadata(Path metadataPath) throws IOException {
		return objectMapper.readValue(Files.readString(metadataPath), BundleMetadata.class);
	}
}
