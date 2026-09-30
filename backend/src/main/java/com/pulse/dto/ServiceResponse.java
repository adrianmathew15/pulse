package com.pulse.dto;

import com.pulse.entity.ServiceStatus;

import java.time.Instant;
import java.math.BigDecimal;
import java.util.UUID;

public record ServiceResponse(
        UUID id,
        String name,
        String description,
        String endpoint,
        ServiceStatus status,
        Instant createdAt,
        BigDecimal cpuWarningThreshold,
        BigDecimal memoryWarningThreshold
) {
}
