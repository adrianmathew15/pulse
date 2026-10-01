package com.pulse.notification;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.util.List;

@Component
public class NotificationDispatcher {
    private static final Logger log = LoggerFactory.getLogger(NotificationDispatcher.class);

    private final List<NotificationChannel> channels;

    public NotificationDispatcher(List<NotificationChannel> channels) {
        this.channels = List.copyOf(channels);
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void dispatch(NotificationRequestedEvent event) {
        IncidentNotification notification = event.notification();
        for (NotificationChannel channel : channels) {
            try {
                channel.send(notification);
            } catch (RuntimeException exception) {
                log.error("Notification channel {} failed for incident {} and event {}",
                        channel.getClass().getSimpleName(), notification.incidentId(),
                        notification.eventType(), exception);
            }
        }
    }
}
