package com.pulse.dto;

import com.pulse.entity.ServiceStatus;

import java.time.Instant;
import java.util.UUID;

public record HealthCheckResponse(
        UUID id,
        UUID serviceId,
        ServiceStatus status,
        Integer httpStatus,
        long responseTimeMs,
        Instant checkedAt,
        String failureReason
) {
}
