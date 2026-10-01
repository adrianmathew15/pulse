package com.pulse.notification;

import java.time.Instant;
import java.util.UUID;

public record IncidentNotification(
        UUID incidentId,
        UUID serviceId,
        String serviceName,
        NotificationEventType eventType,
        Instant timestamp,
        String failureReason,
        Integer httpStatus,
        Instant incidentStartedAt,
        Instant incidentResolvedAt
) {
}
