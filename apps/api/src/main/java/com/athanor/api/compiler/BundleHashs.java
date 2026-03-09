package com.athanor.api.compiler;

import java.util.regex.Pattern;

final class BundleHashs {

	private static final Pattern BUNDLE_HASH_PATTERN = Pattern.compile("^[a-f0-9]{64}$");

	private BundleHashs() {}

	static String requireValid(String bundleHash) {
		if (bundleHash == null || !BUNDLE_HASH_PATTERN.matcher(bundleHash).matches()) {
			throw new IllegalArgumentException("invalid bundle hash");
		}
		return bundleHash;
	}
}
