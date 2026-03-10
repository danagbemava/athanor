package com.athanor.api.jobs;

import java.util.UUID;

public record WorkerRuntimeEventMessage(
	String messageId,
	UUID runId,
	String type,
	String payloadJson
) {}
