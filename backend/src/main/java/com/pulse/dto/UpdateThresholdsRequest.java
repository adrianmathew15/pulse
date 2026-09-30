package com.pulse.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

public record UpdateThresholdsRequest(
        @NotNull(message = "CPU warning threshold is required")
        @DecimalMin(value = "0.01", message = "CPU warning threshold must be at least 0.01")
        @DecimalMax(value = "100", message = "CPU warning threshold must not exceed 100")
        BigDecimal cpuWarningThreshold,

        @NotNull(message = "Memory warning threshold is required")
        @DecimalMin(value = "0.01", message = "Memory warning threshold must be at least 0.01")
        @DecimalMax(value = "100", message = "Memory warning threshold must not exceed 100")
        BigDecimal memoryWarningThreshold
) {
}
