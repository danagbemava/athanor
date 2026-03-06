package com.athanor.api.compiler;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Instant;
import org.springframework.stereotype.Component;

@Component
public class FilesystemBundleStore implements BundleStore {

	private final Path rootDirectory;

	public FilesystemBundleStore() {
		this(Path.of("build", "athanor", "bundles"));
	}

	FilesystemBundleStore(Path rootDirectory) {
		this.rootDirectory = rootDirectory;
	}

	@Override
	public StoreResult store(String bundleHash, byte[] canonicalBundleJson) throws IOException {
		Files.createDirectories(rootDirectory);
		Path bundlePath = rootDirectory.resolve(bundleHash + ".json");
		if (Files.exists(bundlePath)) {
			return new StoreResult(bundlePath, Files.getLastModifiedTime(bundlePath).toInstant(), false);
		}

		Files.write(
			bundlePath,
			canonicalBundleJson,
			StandardOpenOption.CREATE_NEW,
			StandardOpenOption.WRITE
		);
		Instant storedAt = Files.getLastModifiedTime(bundlePath).toInstant();
		return new StoreResult(bundlePath, storedAt, true);
	}
}
