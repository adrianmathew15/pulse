package com.pulse.dto;

public record AlertEventResponse(
        AlertEventType eventType,
        AlertResponse alert
) {
}
