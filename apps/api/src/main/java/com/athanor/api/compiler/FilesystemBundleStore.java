package com.athanor.api.compiler;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Optional;
import java.util.regex.Pattern;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

@Component
public class FilesystemBundleStore implements BundleStore {

	private static final Pattern BUNDLE_HASH_PATTERN = Pattern.compile("^[a-f0-9]{64}$");

	private final Path rootDirectory;
	private final ObjectMapper objectMapper;

	@Autowired
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
			persistedMetadata = readMetadata(metadataPath);
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
	public byte[] readContent(String bundleHash) throws IOException {
		return Files.readAllBytes(contentPath(bundleHash));
	}

	private Path contentPath(String bundleHash) {
		return resolveBundlePath(bundleHash, ".json");
	}

	private Path metadataPath(String bundleHash) {
		return resolveBundlePath(bundleHash, ".metadata.json");
	}

	private Path resolveBundlePath(String bundleHash, String suffix) {
		String normalizedHash = normalizeBundleHash(bundleHash);
		Path resolved = rootDirectory.resolve(normalizedHash + suffix).normalize();
		if (!resolved.startsWith(rootDirectory)) {
			throw new IllegalArgumentException("invalid bundle hash");
		}
		return resolved;
	}

	private String normalizeBundleHash(String bundleHash) {
		if (bundleHash == null || !BUNDLE_HASH_PATTERN.matcher(bundleHash).matches()) {
			throw new IllegalArgumentException("invalid bundle hash");
		}
		return bundleHash;
	}

	private void writeMetadata(Path metadataPath, BundleMetadata metadata) throws IOException {
		Files.writeString(
			metadataPath,
			objectMapper.writeValueAsString(metadata),
			StandardOpenOption.CREATE_NEW,
			StandardOpenOption.WRITE
		);
	}

	private BundleMetadata readMetadata(Path metadataPath) throws IOException {
		return objectMapper.readValue(Files.readString(metadataPath), BundleMetadata.class);
	}
}
