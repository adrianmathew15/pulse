package com.pulse.service;

import com.pulse.dto.AlertResponse;
import com.pulse.entity.Alert;

final class AlertResponseMapper {
    private AlertResponseMapper() {
    }

    static AlertResponse toResponse(Alert alert) {
        return new AlertResponse(alert.getId(), alert.getService().getId(),
                alert.getService().getName(), alert.getType(), alert.getMessage(),
                alert.getSeverity(), alert.getMetricType(), alert.getThreshold(),
                alert.getTriggeredValue(), alert.getTriggeredAt(), alert.getResolvedAt(),
                alert.getStatus());
    }
}
