package com.pulse.service;

import com.pulse.dto.IncidentResponse;
import com.pulse.entity.Incident;

public final class IncidentResponseMapper {
    private IncidentResponseMapper() {
    }

    public static IncidentResponse toResponse(Incident incident) {
        return new IncidentResponse(
                incident.getId(), incident.getService().getId(), incident.getService().getName(),
                incident.getStartedAt(), incident.getResolvedAt(), incident.getStatus(),
                incident.getFailureReason(), incident.getHttpStatus());
    }
}
