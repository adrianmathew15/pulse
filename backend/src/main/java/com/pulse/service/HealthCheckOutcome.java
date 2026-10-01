package com.pulse.service;

import com.pulse.entity.ServiceStatus;

import java.time.Instant;

public record HealthCheckOutcome(
        ServiceStatus status,
        Integer httpStatus,
        long responseTimeMs,
        Instant checkedAt,
        String failureReason
) {
}
