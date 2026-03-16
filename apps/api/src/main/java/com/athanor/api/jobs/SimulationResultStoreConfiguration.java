package com.athanor.api.jobs;

import com.athanor.api.storage.ObjectStorageProperties;
import io.minio.GetObjectArgs;
import io.minio.MinioClient;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
class SimulationResultStoreConfiguration {

	@Bean
	SimulationResultStore simulationResultStore(ObjectStorageProperties properties) {
		if ("filesystem".equalsIgnoreCase(properties.getStorage().getMode())) {
			return new FilesystemSimulationResultStore(properties.getStorage().getFilesystemRoot());
		}
		ObjectStorageProperties.S3 s3 = properties.getStorage().getS3();
		MinioClient client = MinioClient
			.builder()
			.endpoint(s3.getEndpoint())
			.region(s3.getRegion())
			.credentials(s3.getAccessKey(), s3.getSecretKey())
			.build();
		return new S3SimulationResultStore(client, s3.getBucket());
	}

	private static final class FilesystemSimulationResultStore implements SimulationResultStore {

		private final Path rootDirectory;

		private FilesystemSimulationResultStore(Path rootDirectory) {
			this.rootDirectory = rootDirectory;
		}

		@Override
		public byte[] read(String resultKey) throws IOException {
			return Files.readAllBytes(rootDirectory.resolve(resultKey));
		}
	}

	private static final class S3SimulationResultStore implements SimulationResultStore {

		private final MinioClient minioClient;
		private final String bucketName;

		private S3SimulationResultStore(MinioClient minioClient, String bucketName) {
			this.minioClient = minioClient;
			this.bucketName = bucketName;
		}

		@Override
		public byte[] read(String resultKey) throws IOException {
			try (InputStream stream = minioClient.getObject(
				GetObjectArgs.builder().bucket(bucketName).object(resultKey).build()
			)) {
				return stream.readAllBytes();
			} catch (IOException exception) {
				throw exception;
			} catch (Exception exception) {
				throw new IOException("failed to read simulation result", exception);
			}
		}
	}
}
