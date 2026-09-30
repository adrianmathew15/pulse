package com.pulse.repository;

import com.pulse.entity.Alert;
import com.pulse.entity.AlertMetricType;
import com.pulse.entity.AlertStatus;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface AlertRepository extends JpaRepository<Alert, UUID> {
    Optional<Alert> findByServiceIdAndMetricTypeAndStatus(
            UUID serviceId, AlertMetricType metricType, AlertStatus status);

    List<Alert> findByServiceIdOrderByTriggeredAtDescIdDesc(UUID serviceId, Pageable pageable);

    List<Alert> findByServiceIdAndStatusOrderByTriggeredAtDescIdDesc(
            UUID serviceId, AlertStatus status, Pageable pageable);

    List<Alert> findByStatusOrderByTriggeredAtDescIdDesc(AlertStatus status, Pageable pageable);
}
