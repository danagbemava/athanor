package com.athanor.api.telemetry;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.util.UUID;

@Entity
@Table(name = "scenario_telemetry")
class TelemetryEntity {

	@Id
	@Column(name = "scenario_id", nullable = false)
	private UUID scenarioId;

	@Column(name = "aggregate_json", nullable = false, columnDefinition = "TEXT")
	private String aggregateJson;

	protected TelemetryEntity() {}

	TelemetryEntity(UUID scenarioId, String aggregateJson) {
		this.scenarioId = scenarioId;
		this.aggregateJson = aggregateJson;
	}

	UUID scenarioId() { return scenarioId; }
	String aggregateJson() { return aggregateJson; }
}
