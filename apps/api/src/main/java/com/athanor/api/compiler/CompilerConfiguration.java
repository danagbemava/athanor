package com.athanor.api.compiler;

import java.nio.file.Path;
import java.time.Clock;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import io.minio.MinioClient;

@Configuration
@EnableConfigurationProperties(BundleStorageProperties.class)
public class CompilerConfiguration {

	@Bean
	Clock compilerClock() {
		return Clock.systemUTC();
	}

	@Bean
	BundleObjectStore bundleObjectStore(BundleStorageProperties properties) {
		if ("filesystem".equalsIgnoreCase(properties.getStorage().getMode())) {
			return new FilesystemBundleObjectStore(properties.getStorage().getFilesystemRoot());
		}
		BundleStorageProperties.S3 s3 = properties.getStorage().getS3();
		MinioClient client = MinioClient
			.builder()
			.endpoint(s3.getEndpoint())
			.region(s3.getRegion())
			.credentials(s3.getAccessKey(), s3.getSecretKey())
			.build();
		return new S3BundleObjectStore(client, s3.getBucket());
	}

	@Bean
	@ConditionalOnMissingBean(BundleStore.class)
	BundleStore bundleStore(
		BundleRegistryRepository registryRepository,
		BundleObjectStore objectStore,
		Clock compilerClock
	) {
		return new RegistryBackedBundleStore(registryRepository, objectStore, compilerClock);
	}

	@Bean
	BundleRetentionCleanupService bundleRetentionCleanupService(
		BundleRegistryRepository registryRepository,
		BundleObjectStore objectStore,
		BundleStorageProperties properties,
		Clock compilerClock
	) {
		return new BundleRetentionCleanupService(
			registryRepository,
			objectStore,
			properties,
			compilerClock
		);
	}

	private static final class FilesystemBundleObjectStore implements BundleObjectStore {

		private final Path rootDirectory;

		private FilesystemBundleObjectStore(Path rootDirectory) {
			this.rootDirectory = rootDirectory.toAbsolutePath().normalize();
		}

		@Override
		public boolean putIfAbsent(String bundleHash, byte[] canonicalBundleJson)
			throws java.io.IOException {
			java.nio.file.Files.createDirectories(rootDirectory);
			Path bundlePath = bundlePath(bundleHash);
			if (java.nio.file.Files.exists(bundlePath)) {
				return false;
			}
			java.nio.file.Files.write(
				bundlePath,
				canonicalBundleJson,
				java.nio.file.StandardOpenOption.CREATE_NEW,
				java.nio.file.StandardOpenOption.WRITE
			);
			return true;
		}

		@Override
		public byte[] read(String bundleHash) throws java.io.IOException {
			return java.nio.file.Files.readAllBytes(bundlePath(bundleHash));
		}

		@Override
		public DeleteResult delete(String bundleHash) throws java.io.IOException {
			Path bundlePath = bundlePath(bundleHash);
			boolean existed = java.nio.file.Files.exists(bundlePath);
			java.nio.file.Files.deleteIfExists(bundlePath);
			return new DeleteResult(existed);
		}

		private Path bundlePath(String bundleHash) {
			return rootDirectory.resolve(BundleHashs.requireValid(bundleHash) + ".json").normalize();
		}
	}
}
