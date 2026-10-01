package com.pulse.notification;

import com.pulse.entity.Incident;
import com.pulse.entity.IncidentStatus;
import com.pulse.entity.MonitoredService;
import com.pulse.entity.ServiceStatus;
import com.pulse.repository.HealthCheckRepository;
import com.pulse.repository.IncidentRepository;
import com.pulse.repository.ServiceRepository;
import com.pulse.service.HealthCheckOutcome;
import com.pulse.service.HealthCheckResultService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Instant;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(properties = {
        "spring.datasource.url=${DB_URL:jdbc:postgresql://localhost:5432/pulse}?currentSchema=pulse_test",
        "spring.flyway.schemas=pulse_test",
        "spring.jpa.properties.hibernate.default_schema=pulse_test",
        "pulse.metrics.simulation-enabled=false",
        "pulse.health.enabled=false"
})
@Import(IncidentNotificationIntegrationTest.NotificationTestConfiguration.class)
class IncidentNotificationIntegrationTest {
    private static final Instant T0 = Instant.parse("2026-09-30T12:00:00Z");

    @Autowired private ServiceRepository serviceRepository;
    @Autowired private HealthCheckRepository healthCheckRepository;
    @Autowired private IncidentRepository incidentRepository;
    @Autowired private HealthCheckResultService resultService;
    @Autowired private RecordingNotificationChannel recordingChannel;

    @BeforeEach
    @AfterEach
    void resetState() {
        recordingChannel.reset();
        incidentRepository.deleteAll();
        healthCheckRepository.deleteAll();
        serviceRepository.deleteAll();
    }

    @Test
    void incidentCreationSendsOneNotification() {
        MonitoredService service = createService("Creation API");

        record(service, ServiceStatus.DOWN, 503, "HTTP 503", T0);

        assertThat(recordingChannel.notifications()).singleElement().satisfies(notification -> {
            assertThat(notification.eventType()).isEqualTo(NotificationEventType.INCIDENT_CREATED);
            assertThat(notification.serviceId()).isEqualTo(service.getId());
            assertThat(notification.serviceName()).isEqualTo("Creation API");
            assertThat(notification.timestamp()).isEqualTo(T0);
            assertThat(notification.failureReason()).isEqualTo("HTTP 503");
            assertThat(notification.httpStatus()).isEqualTo(503);
            assertThat(notification.incidentStartedAt()).isEqualTo(T0);
            assertThat(notification.incidentResolvedAt()).isNull();
        });
    }

    @Test
    void incidentResolutionSendsOneNotification() {
        MonitoredService service = createService("Recovery API");
        record(service, ServiceStatus.DOWN, null, "Connection refused", T0);
        recordingChannel.clearNotifications();

        record(service, ServiceStatus.UP, 200, null, T0.plusSeconds(30));

        assertThat(recordingChannel.notifications()).singleElement().satisfies(notification -> {
            assertThat(notification.eventType()).isEqualTo(NotificationEventType.INCIDENT_RESOLVED);
            assertThat(notification.timestamp()).isEqualTo(T0.plusSeconds(30));
            assertThat(notification.incidentStartedAt()).isEqualTo(T0);
            assertThat(notification.incidentResolvedAt()).isEqualTo(T0.plusSeconds(30));
            assertThat(notification.failureReason()).isEqualTo("Connection refused");
        });
    }

    @Test
    void repeatedDownChecksDoNotSendDuplicateNotifications() {
        MonitoredService service = createService("Unavailable API");

        record(service, ServiceStatus.DOWN, 503, "HTTP 503", T0);
        record(service, ServiceStatus.DOWN, 503, "HTTP 503", T0.plusSeconds(10));
        record(service, ServiceStatus.DOWN, 503, "HTTP 503", T0.plusSeconds(20));

        assertThat(recordingChannel.notifications()).hasSize(1);
        assertThat(incidentRepository.count()).isEqualTo(1);
    }

    @Test
    void secondOutageSendsANewNotification() {
        MonitoredService service = createService("Flapping API");

        record(service, ServiceStatus.DOWN, 500, "HTTP 500", T0);
        record(service, ServiceStatus.UP, 200, null, T0.plusSeconds(10));
        record(service, ServiceStatus.DOWN, 502, "HTTP 502", T0.plusSeconds(20));

        assertThat(recordingChannel.notifications())
                .extracting(IncidentNotification::eventType)
                .containsExactly(NotificationEventType.INCIDENT_CREATED,
                        NotificationEventType.INCIDENT_RESOLVED,
                        NotificationEventType.INCIDENT_CREATED);
        assertThat(recordingChannel.notifications().get(0).incidentId())
                .isNotEqualTo(recordingChannel.notifications().get(2).incidentId());
    }

    @Test
    void notificationDispatchHappensAfterIncidentCommit() {
        MonitoredService service = createService("Committed API");

        record(service, ServiceStatus.DOWN, 503, "HTTP 503", T0);

        assertThat(recordingChannel.incidentWasVisibleInNewTransaction()).containsExactly(true);
    }

    @Test
    void notificationFailureDoesNotRollBackIncident() {
        MonitoredService service = createService("Durable API");
        recordingChannel.failDelivery();

        record(service, ServiceStatus.DOWN, 503, "HTTP 503", T0);

        assertThat(incidentRepository.count()).isEqualTo(1);
        Incident incident = incidentRepository.findAll().getFirst();
        assertThat(incident.getStatus()).isEqualTo(IncidentStatus.ACTIVE);
        assertThat(recordingChannel.notifications()).hasSize(1);
    }

    private MonitoredService createService(String name) {
        return serviceRepository.saveAndFlush(
                new MonitoredService(name, null, "https://example.com"));
    }

    private void record(MonitoredService service, ServiceStatus status, Integer httpStatus,
                        String reason, Instant checkedAt) {
        resultService.record(service.getId(), new HealthCheckOutcome(
                status, httpStatus, 25, checkedAt, reason));
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class NotificationTestConfiguration {
        @Bean
        RecordingNotificationChannel recordingNotificationChannel(
                IncidentRepository incidentRepository,
                PlatformTransactionManager transactionManager) {
            return new RecordingNotificationChannel(incidentRepository, transactionManager);
        }
    }

    static class RecordingNotificationChannel implements NotificationChannel {
        private final IncidentRepository incidentRepository;
        private final TransactionTemplate newTransaction;
        private final List<IncidentNotification> notifications = new CopyOnWriteArrayList<>();
        private final List<Boolean> incidentWasVisible = new CopyOnWriteArrayList<>();
        private volatile boolean fail;

        RecordingNotificationChannel(IncidentRepository incidentRepository,
                                     PlatformTransactionManager transactionManager) {
            this.incidentRepository = incidentRepository;
            this.newTransaction = new TransactionTemplate(transactionManager);
            this.newTransaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        }

        @Override
        public void send(IncidentNotification notification) {
            notifications.add(notification);
            incidentWasVisible.add(Boolean.TRUE.equals(newTransaction.execute(
                    status -> incidentRepository.findById(notification.incidentId()).isPresent())));
            if (fail) throw new IllegalStateException("test notification channel unavailable");
        }

        List<IncidentNotification> notifications() {
            return List.copyOf(notifications);
        }

        List<Boolean> incidentWasVisibleInNewTransaction() {
            return List.copyOf(incidentWasVisible);
        }

        void clearNotifications() {
            notifications.clear();
            incidentWasVisible.clear();
        }

        void failDelivery() {
            fail = true;
        }

        void reset() {
            fail = false;
            clearNotifications();
        }
    }
}
