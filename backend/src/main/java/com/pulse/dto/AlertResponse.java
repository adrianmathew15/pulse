package com.pulse.dto;

import com.pulse.entity.AlertMetricType;
import com.pulse.entity.AlertSeverity;
import com.pulse.entity.AlertStatus;
import com.pulse.entity.AlertType;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record AlertResponse(
        UUID id,
        UUID serviceId,
        String serviceName,
        AlertType type,
        String message,
        AlertSeverity severity,
        AlertMetricType metricType,
        BigDecimal threshold,
        BigDecimal triggeredValue,
        Instant triggeredAt,
        Instant resolvedAt,
        AlertStatus status
) {
}
