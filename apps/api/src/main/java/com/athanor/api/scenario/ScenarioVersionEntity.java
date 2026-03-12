package com.athanor.api.scenario;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "scenario_versions")
class ScenarioVersionEntity {

	@Id
	@Column(name = "id", nullable = false)
	private UUID id;

	@Column(name = "scenario_id", nullable = false)
	private UUID scenarioId;

	@Column(name = "version_number", nullable = false)
	private int versionNumber;

	@Column(name = "state", nullable = false)
	private String state;

	@Column(name = "created_at", nullable = false)
	private Instant createdAt;

	@Column(name = "graph_json", nullable = false, columnDefinition = "TEXT")
	private String graphJson;

	protected ScenarioVersionEntity() {}

	ScenarioVersionEntity(
		UUID id,
		UUID scenarioId,
		int versionNumber,
		String state,
		Instant createdAt,
		String graphJson
	) {
		this.id = id;
		this.scenarioId = scenarioId;
		this.versionNumber = versionNumber;
		this.state = state;
		this.createdAt = createdAt;
		this.graphJson = graphJson;
	}

	UUID id() {
		return id;
	}

	UUID scenarioId() {
		return scenarioId;
	}

	int versionNumber() {
		return versionNumber;
	}

	String state() {
		return state;
	}

	Instant createdAt() {
		return createdAt;
	}

	String graphJson() {
		return graphJson;
	}
}
