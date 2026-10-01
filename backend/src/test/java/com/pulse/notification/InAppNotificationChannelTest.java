package com.pulse.notification;

import com.pulse.dto.IncidentEventResponse;
import com.pulse.dto.IncidentEventType;
import com.pulse.entity.IncidentStatus;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.messaging.simp.SimpMessagingTemplate;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class InAppNotificationChannelTest {
    @Mock private SimpMessagingTemplate messagingTemplate;

    @Test
    void sendsExistingIncidentPayloadToExistingWebSocketDestinations() {
        UUID incidentId = UUID.randomUUID();
        UUID serviceId = UUID.randomUUID();
        Instant startedAt = Instant.parse("2026-09-30T12:00:00Z");
        Instant resolvedAt = startedAt.plusSeconds(45);
        IncidentNotification notification = new IncidentNotification(
                incidentId, serviceId, "Payments", NotificationEventType.INCIDENT_RESOLVED,
                resolvedAt, "HTTP 503", 503, startedAt, resolvedAt);

        new InAppNotificationChannel(messagingTemplate).send(notification);

        ArgumentCaptor<IncidentEventResponse> payload =
                ArgumentCaptor.forClass(IncidentEventResponse.class);
        verify(messagingTemplate).convertAndSend(
                eq("/topic/services/" + serviceId + "/incidents"), payload.capture());
        verify(messagingTemplate).convertAndSend("/topic/incidents", payload.getValue());
        assertThat(payload.getValue().eventType()).isEqualTo(IncidentEventType.INCIDENT_RESOLVED);
        assertThat(payload.getValue().incident().id()).isEqualTo(incidentId);
        assertThat(payload.getValue().incident().status()).isEqualTo(IncidentStatus.RESOLVED);
        assertThat(payload.getValue().incident().resolvedAt()).isEqualTo(resolvedAt);
    }
}
