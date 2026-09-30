package com.pulse.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record MetricResponse(
        UUID id,
        UUID serviceId,
        BigDecimal cpuUsage,
        BigDecimal memoryUsage,
        Instant timestamp
) {
}
