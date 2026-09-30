package com.pulse.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import org.hibernate.validator.constraints.URL;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import java.math.BigDecimal;

public record CreateServiceRequest(
        @NotBlank(message = "Service name must not be blank")
        @Size(max = 120, message = "Service name must not exceed 120 characters")
        String name,

        @Size(max = 1000, message = "Description must not exceed 1000 characters")
        String description,

        @Size(max = 2048, message = "Endpoint must not exceed 2048 characters")
        @URL(message = "Endpoint must be a valid URL")
        @Pattern(regexp = "^$|^https?://.*", flags = Pattern.Flag.CASE_INSENSITIVE,
                message = "Endpoint must use HTTP or HTTPS")
        String endpoint,

        @DecimalMin(value = "0.01", message = "CPU warning threshold must be at least 0.01")
        @DecimalMax(value = "100", message = "CPU warning threshold must not exceed 100")
        BigDecimal cpuWarningThreshold,

        @DecimalMin(value = "0.01", message = "Memory warning threshold must be at least 0.01")
        @DecimalMax(value = "100", message = "Memory warning threshold must not exceed 100")
        BigDecimal memoryWarningThreshold
) {
    public CreateServiceRequest(String name, String description, String endpoint) {
        this(name, description, endpoint, null, null);
    }
}
