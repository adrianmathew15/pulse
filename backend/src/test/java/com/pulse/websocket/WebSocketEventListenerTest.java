package com.pulse.websocket;

import com.pulse.dto.AlertEventResponse;
import com.pulse.dto.AlertEventType;
import com.pulse.dto.AlertResponse;
import com.pulse.dto.MetricResponse;
import com.pulse.entity.AlertMetricType;
import com.pulse.entity.AlertSeverity;
import com.pulse.entity.AlertStatus;
import com.pulse.entity.AlertType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import static org.mockito.Mockito.verify;
import static org.assertj.core.api.Assertions.assertThat;

@ExtendWith(MockitoExtension.class)
class WebSocketEventListenerTest {
    @Mock
    private SimpMessagingTemplate messagingTemplate;

    private WebSocketEventListener listener;

    @BeforeEach
    void setUp() {
        listener = new WebSocketEventListener(messagingTemplate);
    }

    @Test
    void publishesMetricDtoToServiceTopic() {
        UUID serviceId = UUID.randomUUID();
        MetricResponse metric = new MetricResponse(UUID.randomUUID(), serviceId,
                new BigDecimal("42.00"), new BigDecimal("55.00"), Instant.now());

        listener.publishMetric(new MetricPersistedEvent(metric));

        verify(messagingTemplate).convertAndSend(
                "/topic/services/" + serviceId + "/metrics", metric);
    }

    @Test
    void publishesAlertDtoToServiceAndDashboardTopics() {
        UUID serviceId = UUID.randomUUID();
        AlertResponse alert = new AlertResponse(UUID.randomUUID(), serviceId, "Payments",
                AlertType.CPU_THRESHOLD, "CPU threshold exceeded", AlertSeverity.WARNING,
                AlertMetricType.CPU, new BigDecimal("80.00"), new BigDecimal("90.00"),
                Instant.now(), null, AlertStatus.ACTIVE);
        AlertEventResponse payload = new AlertEventResponse(AlertEventType.CREATED, alert);

        listener.publishAlert(new AlertChangedEvent(payload));

        verify(messagingTemplate).convertAndSend(
                "/topic/services/" + serviceId + "/alerts", payload);
        verify(messagingTemplate).convertAndSend("/topic/alerts", payload);
    }

    @Test
    void defersBothWebSocketListenersUntilAfterCommit() throws NoSuchMethodException {
        var metricListener = WebSocketEventListener.class
                .getMethod("publishMetric", MetricPersistedEvent.class)
                .getAnnotation(TransactionalEventListener.class);
        var alertListener = WebSocketEventListener.class
                .getMethod("publishAlert", AlertChangedEvent.class)
                .getAnnotation(TransactionalEventListener.class);

        assertThat(metricListener.phase()).isEqualTo(TransactionPhase.AFTER_COMMIT);
        assertThat(alertListener.phase()).isEqualTo(TransactionPhase.AFTER_COMMIT);
    }
}
