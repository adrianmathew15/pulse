package com.pulse.notification;

import com.pulse.dto.IncidentEventResponse;
import com.pulse.dto.IncidentEventType;
import com.pulse.dto.IncidentResponse;
import com.pulse.entity.IncidentStatus;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;

@Component
public class InAppNotificationChannel implements NotificationChannel {
    private final SimpMessagingTemplate messagingTemplate;

    public InAppNotificationChannel(SimpMessagingTemplate messagingTemplate) {
        this.messagingTemplate = messagingTemplate;
    }

    @Override
    public void send(IncidentNotification notification) {
        IncidentEventResponse payload = toPayload(notification);
        messagingTemplate.convertAndSend(
                "/topic/services/" + notification.serviceId() + "/incidents", payload);
        messagingTemplate.convertAndSend("/topic/incidents", payload);
    }

    private IncidentEventResponse toPayload(IncidentNotification notification) {
        boolean resolved = notification.eventType() == NotificationEventType.INCIDENT_RESOLVED;
        IncidentResponse incident = new IncidentResponse(
                notification.incidentId(), notification.serviceId(), notification.serviceName(),
                notification.incidentStartedAt(), notification.incidentResolvedAt(),
                resolved ? IncidentStatus.RESOLVED : IncidentStatus.ACTIVE,
                notification.failureReason(), notification.httpStatus());
        IncidentEventType eventType = resolved
                ? IncidentEventType.INCIDENT_RESOLVED
                : IncidentEventType.INCIDENT_CREATED;
        return new IncidentEventResponse(eventType, incident);
    }
}
