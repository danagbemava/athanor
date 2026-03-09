package com.athanor.api.compiler;

import java.io.IOException;

interface BundleObjectStore {

	boolean putIfAbsent(String bundleHash, byte[] canonicalBundleJson) throws IOException;

	byte[] read(String bundleHash) throws IOException;

	DeleteResult delete(String bundleHash) throws IOException;

	record DeleteResult(boolean existed) {}
}
