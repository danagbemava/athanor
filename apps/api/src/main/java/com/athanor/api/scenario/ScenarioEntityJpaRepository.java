package com.athanor.api.scenario;

import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ScenarioEntityJpaRepository extends JpaRepository<ScenarioEntity, UUID> {}
