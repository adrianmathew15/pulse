package com.pulse.service;

import com.pulse.dto.AlertEventResponse;
import com.pulse.dto.AlertEventType;
import com.pulse.entity.Alert;
import com.pulse.entity.AlertMetricType;
import com.pulse.entity.AlertStatus;
import com.pulse.entity.AlertType;
import com.pulse.entity.MonitoredService;
import com.pulse.exception.ResourceNotFoundException;
import com.pulse.repository.AlertRepository;
import com.pulse.repository.ServiceRepository;
import com.pulse.websocket.AlertChangedEvent;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Service
public class AlertEvaluationService {
    private final ServiceRepository serviceRepository;
    private final AlertRepository alertRepository;
    private final ApplicationEventPublisher eventPublisher;

    public AlertEvaluationService(ServiceRepository serviceRepository,
                                  AlertRepository alertRepository,
                                  ApplicationEventPublisher eventPublisher) {
        this.serviceRepository = serviceRepository;
        this.alertRepository = alertRepository;
        this.eventPublisher = eventPublisher;
    }

    @Transactional
    public void evaluate(UUID serviceId, BigDecimal cpuUsage,
                         BigDecimal memoryUsage, Instant observedAt) {
        MonitoredService service = serviceRepository.findByIdForUpdate(serviceId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Service " + serviceId + " was not found"));
        Instant timestamp = observedAt == null ? Instant.now() : observedAt;

        evaluateCondition(service, AlertMetricType.CPU, AlertType.CPU_THRESHOLD,
                cpuUsage, service.getCpuWarningThreshold(), timestamp);
        evaluateCondition(service, AlertMetricType.MEMORY, AlertType.MEMORY_THRESHOLD,
                memoryUsage, service.getMemoryWarningThreshold(), timestamp);
    }

    private void evaluateCondition(MonitoredService service, AlertMetricType metricType,
                                   AlertType alertType, BigDecimal value,
                                   BigDecimal threshold, Instant timestamp) {
        Alert active = alertRepository.findByServiceIdAndMetricTypeAndStatus(
                service.getId(), metricType, AlertStatus.ACTIVE).orElse(null);

        if (value.compareTo(threshold) > 0) {
            if (active == null) {
                String label = metricType == AlertMetricType.CPU ? "CPU" : "Memory";
                Alert created = new Alert(service, alertType,
                        label + " usage exceeded its warning threshold",
                        metricType, threshold, value, timestamp);
                alertRepository.save(created);
                publish(AlertEventType.CREATED, created);
            }
        } else if (active != null) {
            active.resolve(timestamp);
            publish(AlertEventType.RESOLVED, active);
        }
    }

    private void publish(AlertEventType eventType, Alert alert) {
        eventPublisher.publishEvent(new AlertChangedEvent(
                new AlertEventResponse(eventType, AlertResponseMapper.toResponse(alert))));
    }
}
