package com.pulse.websocket;

import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
public class WebSocketEventListener {
    private final SimpMessagingTemplate messagingTemplate;

    public WebSocketEventListener(SimpMessagingTemplate messagingTemplate) {
        this.messagingTemplate = messagingTemplate;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void publishMetric(MetricPersistedEvent event) {
        var metric = event.metric();
        messagingTemplate.convertAndSend(
                "/topic/services/" + metric.serviceId() + "/metrics", metric);
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void publishAlert(AlertChangedEvent event) {
        var payload = event.payload();
        var serviceDestination = "/topic/services/"
                + payload.alert().serviceId() + "/alerts";
        messagingTemplate.convertAndSend(serviceDestination, payload);
        messagingTemplate.convertAndSend("/topic/alerts", payload);
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void publishHealthCheck(HealthCheckPersistedEvent event) {
        var healthCheck = event.healthCheck();
        messagingTemplate.convertAndSend(
                "/topic/services/" + healthCheck.serviceId() + "/health-checks", healthCheck);
        messagingTemplate.convertAndSend("/topic/health-checks", healthCheck);
    }

}
