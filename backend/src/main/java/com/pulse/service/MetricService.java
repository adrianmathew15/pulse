package com.pulse.service;

import com.pulse.dto.MetricResponse;
import com.pulse.entity.Metric;
import com.pulse.entity.MonitoredService;
import com.pulse.exception.ResourceNotFoundException;
import com.pulse.repository.MetricRepository;
import com.pulse.repository.ServiceRepository;
import com.pulse.websocket.MetricPersistedEvent;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

@Service
@Transactional(readOnly = true)
public class MetricService {
    private final MetricRepository metricRepository;
    private final ServiceRepository serviceRepository;
    private final ApplicationEventPublisher eventPublisher;
    private final int defaultLimit;
    private final int maximumLimit;

    public MetricService(MetricRepository metricRepository,
                         ServiceRepository serviceRepository,
                         ApplicationEventPublisher eventPublisher,
                         @Value("${pulse.metrics.default-query-limit:100}") int defaultLimit,
                         @Value("${pulse.metrics.maximum-query-limit:500}") int maximumLimit) {
        if (defaultLimit < 1 || maximumLimit < 1 || defaultLimit > maximumLimit) {
            throw new IllegalArgumentException("Metric query limits are invalid");
        }
        this.metricRepository = metricRepository;
        this.serviceRepository = serviceRepository;
        this.eventPublisher = eventPublisher;
        this.defaultLimit = defaultLimit;
        this.maximumLimit = maximumLimit;
    }

    public List<MetricResponse> findRecent(UUID serviceId, Integer requestedLimit) {
        requireService(serviceId);
        int limit = resolveLimit(requestedLimit);
        List<Metric> chronological = new ArrayList<>(metricRepository
                .findByServiceIdOrderByTimestampDescIdDesc(
                        serviceId, PageRequest.of(0, limit)));
        Collections.reverse(chronological);
        return chronological.stream().map(this::toResponse).toList();
    }

    @Transactional
    public MetricResponse record(MonitoredService service, double cpuUsage,
                                 double memoryUsage, Instant timestamp) {
        if (service == null) {
            throw new IllegalArgumentException("Service is required");
        }
        BigDecimal cpu = percentage(cpuUsage, "CPU usage");
        BigDecimal memory = percentage(memoryUsage, "Memory usage");
        Metric metric = new Metric(service, cpu, memory,
                timestamp == null ? Instant.now() : timestamp);
        MetricResponse response = toResponse(metricRepository.save(metric));
        eventPublisher.publishEvent(new MetricPersistedEvent(response));
        return response;
    }

    private MonitoredService requireService(UUID serviceId) {
        return serviceRepository.findById(serviceId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Service " + serviceId + " was not found"));
    }

    private int resolveLimit(Integer requestedLimit) {
        if (requestedLimit == null) return defaultLimit;
        if (requestedLimit < 1) {
            throw new IllegalArgumentException("Metric limit must be at least 1");
        }
        return Math.min(requestedLimit, maximumLimit);
    }

    private BigDecimal percentage(double value, String field) {
        if (!Double.isFinite(value) || value < 0 || value > 100) {
            throw new IllegalArgumentException(field + " must be between 0 and 100");
        }
        return BigDecimal.valueOf(value).setScale(2, RoundingMode.HALF_UP);
    }

    private MetricResponse toResponse(Metric metric) {
        return new MetricResponse(metric.getId(), metric.getService().getId(),
                metric.getCpuUsage(), metric.getMemoryUsage(), metric.getTimestamp());
    }
}
