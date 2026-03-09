package com.athanor.api.compiler;

import io.minio.BucketExistsArgs;
import io.minio.GetObjectArgs;
import io.minio.MakeBucketArgs;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import io.minio.RemoveObjectArgs;
import io.minio.StatObjectArgs;
import io.minio.errors.ErrorResponseException;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;

public class S3BundleObjectStore implements BundleObjectStore {

	private final MinioClient minioClient;
	private final String bucketName;

	public S3BundleObjectStore(MinioClient minioClient, String bucketName) {
		this.minioClient = minioClient;
		this.bucketName = bucketName;
	}

	@Override
	public boolean putIfAbsent(String bundleHash, byte[] canonicalBundleJson) throws IOException {
		String objectKey = objectKey(bundleHash);
		try {
			ensureBucketExists();
			if (objectExists(objectKey)) {
				return false;
			}
			minioClient.putObject(
				PutObjectArgs
					.builder()
					.bucket(bucketName)
					.object(objectKey)
					.stream(
						new ByteArrayInputStream(canonicalBundleJson),
						canonicalBundleJson.length,
						-1
					)
					.contentType("application/json")
					.build()
			);
			return true;
		} catch (IOException exception) {
			throw exception;
		} catch (Exception exception) {
			throw new IOException("failed to store compiled bundle object", exception);
		}
	}

	@Override
	public byte[] read(String bundleHash) throws IOException {
		try (InputStream stream = minioClient.getObject(
			GetObjectArgs.builder().bucket(bucketName).object(objectKey(bundleHash)).build()
		)) {
			return stream.readAllBytes();
		} catch (IOException exception) {
			throw exception;
		} catch (Exception exception) {
			throw new IOException("failed to read compiled bundle object", exception);
		}
	}

	@Override
	public DeleteResult delete(String bundleHash) throws IOException {
		String objectKey = objectKey(bundleHash);
		try {
			boolean existed = objectExists(objectKey);
			if (existed) {
				minioClient.removeObject(
					RemoveObjectArgs.builder().bucket(bucketName).object(objectKey).build()
				);
			}
			return new DeleteResult(existed);
		} catch (IOException exception) {
			throw exception;
		} catch (Exception exception) {
			throw new IOException("failed to delete compiled bundle object", exception);
		}
	}

	private void ensureBucketExists() throws Exception {
		boolean exists = minioClient.bucketExists(
			BucketExistsArgs.builder().bucket(bucketName).build()
		);
		if (!exists) {
			minioClient.makeBucket(MakeBucketArgs.builder().bucket(bucketName).build());
		}
	}

	private boolean objectExists(String objectKey) throws Exception {
		try {
			minioClient.statObject(
				StatObjectArgs.builder().bucket(bucketName).object(objectKey).build()
			);
			return true;
		} catch (ErrorResponseException exception) {
			String code = exception.errorResponse().code();
			if ("NoSuchKey".equals(code) || "NoSuchObject".equals(code)) {
				return false;
			}
			throw exception;
		}
	}

	private String objectKey(String bundleHash) {
		return "bundles/" + BundleHashs.requireValid(bundleHash);
	}
}
