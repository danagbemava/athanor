package com.athanor.api.simulation;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "athanor.worker.runtime")
public class WorkerRuntimeProperties {

	private String mode = "cli";
	private String dispatchStream = "athanor.worker.dispatch";
	private String dispatchConsumerGroup = "athanor-worker";
	private String eventStream = "athanor.worker.events";
	private String eventConsumerGroup = "athanor-api";
	private String eventConsumerName = "api-1";

	public String getMode() {
		return mode;
	}

	public void setMode(String mode) {
		this.mode = mode;
	}

	public String getDispatchStream() {
		return dispatchStream;
	}

	public void setDispatchStream(String dispatchStream) {
		this.dispatchStream = dispatchStream;
	}

	public String getDispatchConsumerGroup() {
		return dispatchConsumerGroup;
	}

	public void setDispatchConsumerGroup(String dispatchConsumerGroup) {
		this.dispatchConsumerGroup = dispatchConsumerGroup;
	}

	public String getEventStream() {
		return eventStream;
	}

	public void setEventStream(String eventStream) {
		this.eventStream = eventStream;
	}

	public String getEventConsumerGroup() {
		return eventConsumerGroup;
	}

	public void setEventConsumerGroup(String eventConsumerGroup) {
		this.eventConsumerGroup = eventConsumerGroup;
	}

	public String getEventConsumerName() {
		return eventConsumerName;
	}

	public void setEventConsumerName(String eventConsumerName) {
		this.eventConsumerName = eventConsumerName;
	}

	public boolean redisModeEnabled() {
		return "redis".equalsIgnoreCase(mode);
	}
}
