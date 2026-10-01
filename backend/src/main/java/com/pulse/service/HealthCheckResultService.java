package com.pulse.service;

import com.pulse.dto.HealthCheckResponse;
import com.pulse.notification.IncidentNotification;
import com.pulse.notification.NotificationEventType;
import com.pulse.notification.NotificationService;
import com.pulse.entity.HealthCheck;
import com.pulse.entity.Incident;
import com.pulse.entity.IncidentStatus;
import com.pulse.entity.MonitoredService;
import com.pulse.entity.ServiceStatus;
import com.pulse.exception.ResourceNotFoundException;
import com.pulse.repository.HealthCheckRepository;
import com.pulse.repository.IncidentRepository;
import com.pulse.repository.ServiceRepository;
import com.pulse.websocket.HealthCheckPersistedEvent;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

@Service
@Transactional(readOnly = true)
public class HealthCheckResultService {
    private final HealthCheckRepository healthCheckRepository;
    private final IncidentRepository incidentRepository;
    private final ServiceRepository serviceRepository;
    private final ApplicationEventPublisher eventPublisher;
    private final NotificationService notificationService;
    private final int defaultLimit;
    private final int maximumLimit;

    public HealthCheckResultService(
            HealthCheckRepository healthCheckRepository,
            IncidentRepository incidentRepository,
            ServiceRepository serviceRepository,
            ApplicationEventPublisher eventPublisher,
            NotificationService notificationService,
            @Value("${pulse.health.default-query-limit:100}") int defaultLimit,
            @Value("${pulse.health.maximum-query-limit:500}") int maximumLimit) {
        if (defaultLimit < 1 || maximumLimit < 1 || defaultLimit > maximumLimit) {
            throw new IllegalArgumentException("Health-check query limits are invalid");
        }
        this.healthCheckRepository = healthCheckRepository;
        this.incidentRepository = incidentRepository;
        this.serviceRepository = serviceRepository;
        this.eventPublisher = eventPublisher;
        this.notificationService = notificationService;
        this.defaultLimit = defaultLimit;
        this.maximumLimit = maximumLimit;
    }

    public List<HealthCheckResponse> findRecent(UUID serviceId, Integer requestedLimit) {
        requireService(serviceId);
        List<HealthCheck> chronological = new ArrayList<>(healthCheckRepository
                .findByServiceIdOrderByCheckedAtDescIdDesc(
                        serviceId, PageRequest.of(0, resolveLimit(requestedLimit))));
        Collections.reverse(chronological);
        return chronological.stream().map(this::toResponse).toList();
    }

    @Transactional
    public HealthCheckResponse record(UUID serviceId, HealthCheckOutcome outcome) {
        MonitoredService service = serviceRepository.findByIdForUpdate(serviceId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Service " + serviceId + " was not found"));
        ServiceStatus previousStatus = service.getStatus();
        applyIncidentTransition(service, previousStatus, outcome);
        service.updateStatus(outcome.status());
        HealthCheck saved = healthCheckRepository.save(new HealthCheck(
                service, outcome.status(), outcome.httpStatus(), outcome.responseTimeMs(),
                outcome.checkedAt(), outcome.failureReason()));
        HealthCheckResponse response = toResponse(saved);
        eventPublisher.publishEvent(new HealthCheckPersistedEvent(response));
        return response;
    }

    private void applyIncidentTransition(MonitoredService service,
                                         ServiceStatus previousStatus,
                                         HealthCheckOutcome outcome) {
        if (outcome.status() == ServiceStatus.DOWN && previousStatus != ServiceStatus.DOWN) {
            Incident incident = incidentRepository.save(new Incident(
                    service, outcome.checkedAt(), outcome.failureReason(), outcome.httpStatus()));
            notifyIncident(NotificationEventType.INCIDENT_CREATED, incident);
        } else if (outcome.status() == ServiceStatus.UP && previousStatus == ServiceStatus.DOWN) {
            incidentRepository.findByServiceIdAndStatus(service.getId(), IncidentStatus.ACTIVE)
                    .ifPresent(incident -> {
                        incident.resolve(outcome.checkedAt());
                        notifyIncident(NotificationEventType.INCIDENT_RESOLVED, incident);
                    });
        }
    }

    private void notifyIncident(NotificationEventType eventType, Incident incident) {
        notificationService.notify(new IncidentNotification(
                incident.getId(), incident.getService().getId(), incident.getService().getName(),
                eventType,
                eventType == NotificationEventType.INCIDENT_CREATED
                        ? incident.getStartedAt() : incident.getResolvedAt(),
                incident.getFailureReason(), incident.getHttpStatus(),
                incident.getStartedAt(), incident.getResolvedAt()));
    }

    private MonitoredService requireService(UUID serviceId) {
        return serviceRepository.findById(serviceId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Service " + serviceId + " was not found"));
    }

    private int resolveLimit(Integer requestedLimit) {
        if (requestedLimit == null) return defaultLimit;
        if (requestedLimit < 1) throw new IllegalArgumentException("Health-check limit must be at least 1");
        return Math.min(requestedLimit, maximumLimit);
    }

    private HealthCheckResponse toResponse(HealthCheck healthCheck) {
        return new HealthCheckResponse(
                healthCheck.getId(), healthCheck.getService().getId(), healthCheck.getStatus(),
                healthCheck.getHttpStatus(), healthCheck.getResponseTimeMs(),
                healthCheck.getCheckedAt(), healthCheck.getFailureReason());
    }
}
