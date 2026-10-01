package com.pulse.repository;

import com.pulse.entity.Incident;
import com.pulse.entity.IncidentStatus;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface IncidentRepository extends JpaRepository<Incident, UUID> {
    Optional<Incident> findByServiceIdAndStatus(UUID serviceId, IncidentStatus status);

    List<Incident> findByServiceIdOrderByStartedAtDescIdDesc(UUID serviceId, Pageable pageable);

    List<Incident> findByServiceIdAndStatusOrderByStartedAtDescIdDesc(
            UUID serviceId, IncidentStatus status, Pageable pageable);

    List<Incident> findByStatusOrderByStartedAtDescIdDesc(
            IncidentStatus status, Pageable pageable);
}
