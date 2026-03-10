package com.athanor.api.jobs;

import com.athanor.api.compiler.WorkerExecutionRequest;
import java.util.UUID;

public record WorkerRuntimeDispatchMessage(
	UUID runId,
	String bundleHash,
	WorkerExecutionRequest request
) {}
