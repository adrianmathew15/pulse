package com.pulse.websocket;

import com.pulse.dto.MetricResponse;

public record MetricPersistedEvent(MetricResponse metric) {
}
