package com.athanor.api.telemetry;

import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TelemetryEntityJpaRepository extends JpaRepository<TelemetryEntity, UUID> {}
