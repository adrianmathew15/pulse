package com.pulse.dto;

public record IncidentEventResponse(
        IncidentEventType eventType,
        IncidentResponse incident
) {
}
