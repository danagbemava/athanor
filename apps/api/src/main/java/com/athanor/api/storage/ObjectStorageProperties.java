package com.athanor.api.storage;

import java.nio.file.Path;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "athanor.bundle")
public class ObjectStorageProperties {

	private final Storage storage = new Storage();
	private final Retention retention = new Retention();

	public Storage getStorage() {
		return storage;
	}

	public Retention getRetention() {
		return retention;
	}

	public static class Storage {

		private String mode = "s3";
		private Path filesystemRoot = Path.of("build", "athanor", "bundles");
		private final S3 s3 = new S3();

		public String getMode() {
			return mode;
		}

		public void setMode(String mode) {
			this.mode = mode;
		}

		public Path getFilesystemRoot() {
			return filesystemRoot;
		}

		public void setFilesystemRoot(Path filesystemRoot) {
			this.filesystemRoot = filesystemRoot;
		}

		public S3 getS3() {
			return s3;
		}
	}

	public static class S3 {

		private String endpoint;
		private String region = "us-east-1";
		private String bucket = "athanor-bundles";
		private String accessKey;
		private String secretKey;

		public String getEndpoint() {
			return endpoint;
		}

		public void setEndpoint(String endpoint) {
			this.endpoint = endpoint;
		}

		public String getRegion() {
			return region;
		}

		public void setRegion(String region) {
			this.region = region;
		}

		public String getBucket() {
			return bucket;
		}

		public void setBucket(String bucket) {
			this.bucket = bucket;
		}

		public String getAccessKey() {
			return accessKey;
		}

		public void setAccessKey(String accessKey) {
			this.accessKey = accessKey;
		}

		public String getSecretKey() {
			return secretKey;
		}

		public void setSecretKey(String secretKey) {
			this.secretKey = secretKey;
		}
	}

	public static class Retention {

		private boolean enabled = true;
		private String schedule = "0 0 2 * * *";
		private int draftDays = 90;
		private int orphanDays = 7;

		public boolean isEnabled() {
			return enabled;
		}

		public void setEnabled(boolean enabled) {
			this.enabled = enabled;
		}

		public String getSchedule() {
			return schedule;
		}

		public void setSchedule(String schedule) {
			this.schedule = schedule;
		}

		public int getDraftDays() {
			return draftDays;
		}

		public void setDraftDays(int draftDays) {
			this.draftDays = draftDays;
		}

		public int getOrphanDays() {
			return orphanDays;
		}

		public void setOrphanDays(int orphanDays) {
			this.orphanDays = orphanDays;
		}
	}
}
