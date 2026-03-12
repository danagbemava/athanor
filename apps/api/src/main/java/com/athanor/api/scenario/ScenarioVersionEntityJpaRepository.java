package com.athanor.api.scenario;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ScenarioVersionEntityJpaRepository
	extends JpaRepository<ScenarioVersionEntity, UUID> {
	List<ScenarioVersionEntity> findByScenarioIdOrderByVersionNumberAsc(UUID scenarioId);
	Optional<ScenarioVersionEntity> findFirstByScenarioIdOrderByVersionNumberDesc(UUID scenarioId);
}
