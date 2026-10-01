package com.pulse.notification;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;

@Service
public class SpringNotificationService implements NotificationService {
    private final ApplicationEventPublisher eventPublisher;

    public SpringNotificationService(ApplicationEventPublisher eventPublisher) {
        this.eventPublisher = eventPublisher;
    }

    @Override
    public void notify(IncidentNotification notification) {
        eventPublisher.publishEvent(new NotificationRequestedEvent(notification));
    }
}
