package com.pulse.repository;

import com.pulse.entity.HealthCheck;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface HealthCheckRepository extends JpaRepository<HealthCheck, UUID> {
    List<HealthCheck> findByServiceIdOrderByCheckedAtDescIdDesc(UUID serviceId, Pageable pageable);
}
