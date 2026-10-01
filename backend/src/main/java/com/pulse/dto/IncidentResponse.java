package com.pulse.dto;

import com.pulse.entity.IncidentStatus;

import java.time.Instant;
import java.util.UUID;

public record IncidentResponse(
        UUID id,
        UUID serviceId,
        String serviceName,
        Instant startedAt,
        Instant resolvedAt,
        IncidentStatus status,
        String failureReason,
        Integer httpStatus
) {
}
