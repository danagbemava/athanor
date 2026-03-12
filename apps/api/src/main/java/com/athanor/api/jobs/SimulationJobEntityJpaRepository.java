package com.athanor.api.jobs;

import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SimulationJobEntityJpaRepository
	extends JpaRepository<SimulationJobEntity, UUID> {
	List<SimulationJobEntity> findByStatusIn(Collection<String> statuses);
}
