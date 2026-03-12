package com.athanor.api.optimization;

import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface OptimizationJobEntityJpaRepository
	extends JpaRepository<OptimizationJobEntity, UUID> {
	List<OptimizationJobEntity> findByStatusIn(Collection<String> statuses);
}
