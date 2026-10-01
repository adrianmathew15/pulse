package com.pulse.websocket;

import com.pulse.dto.HealthCheckResponse;

public record HealthCheckPersistedEvent(HealthCheckResponse healthCheck) {
}
