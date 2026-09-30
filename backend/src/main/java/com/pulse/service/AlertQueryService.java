package com.pulse.service;

import com.pulse.dto.AlertResponse;
import com.pulse.entity.Alert;
import com.pulse.entity.AlertStatus;
import com.pulse.exception.ResourceNotFoundException;
import com.pulse.repository.AlertRepository;
import com.pulse.repository.ServiceRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@Transactional(readOnly = true)
public class AlertQueryService {
    private final AlertRepository alertRepository;
    private final ServiceRepository serviceRepository;
    private final int defaultLimit;
    private final int maximumLimit;

    public AlertQueryService(AlertRepository alertRepository,
                             ServiceRepository serviceRepository,
                             @Value("${pulse.alerts.default-query-limit:100}") int defaultLimit,
                             @Value("${pulse.alerts.maximum-query-limit:500}") int maximumLimit) {
        if (defaultLimit < 1 || maximumLimit < 1 || defaultLimit > maximumLimit) {
            throw new IllegalArgumentException("Alert query limits are invalid");
        }
        this.alertRepository = alertRepository;
        this.serviceRepository = serviceRepository;
        this.defaultLimit = defaultLimit;
        this.maximumLimit = maximumLimit;
    }

    public List<AlertResponse> findForService(UUID serviceId, AlertStatus status,
                                              Integer requestedLimit) {
        if (!serviceRepository.existsById(serviceId)) {
            throw new ResourceNotFoundException("Service " + serviceId + " was not found");
        }
        var page = PageRequest.of(0, resolveLimit(requestedLimit));
        List<Alert> alerts = status == null
                ? alertRepository.findByServiceIdOrderByTriggeredAtDescIdDesc(serviceId, page)
                : alertRepository.findByServiceIdAndStatusOrderByTriggeredAtDescIdDesc(
                        serviceId, status, page);
        return alerts.stream().map(AlertResponseMapper::toResponse).toList();
    }

    public List<AlertResponse> findActive(Integer requestedLimit) {
        return alertRepository.findByStatusOrderByTriggeredAtDescIdDesc(
                        AlertStatus.ACTIVE, PageRequest.of(0, resolveLimit(requestedLimit)))
                .stream().map(AlertResponseMapper::toResponse).toList();
    }

    private int resolveLimit(Integer requestedLimit) {
        if (requestedLimit == null) return defaultLimit;
        if (requestedLimit < 1) throw new IllegalArgumentException("Alert limit must be at least 1");
        return Math.min(requestedLimit, maximumLimit);
    }

}
