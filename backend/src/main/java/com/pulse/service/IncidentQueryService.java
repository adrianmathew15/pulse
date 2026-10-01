package com.pulse.service;

import com.pulse.dto.IncidentResponse;
import com.pulse.entity.Incident;
import com.pulse.entity.IncidentStatus;
import com.pulse.exception.ResourceNotFoundException;
import com.pulse.repository.IncidentRepository;
import com.pulse.repository.ServiceRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@Transactional(readOnly = true)
public class IncidentQueryService {
    private final IncidentRepository incidentRepository;
    private final ServiceRepository serviceRepository;
    private final int defaultLimit;
    private final int maximumLimit;

    public IncidentQueryService(
            IncidentRepository incidentRepository,
            ServiceRepository serviceRepository,
            @Value("${pulse.incidents.default-query-limit:100}") int defaultLimit,
            @Value("${pulse.incidents.maximum-query-limit:500}") int maximumLimit) {
        if (defaultLimit < 1 || maximumLimit < 1 || defaultLimit > maximumLimit) {
            throw new IllegalArgumentException("Incident query limits are invalid");
        }
        this.incidentRepository = incidentRepository;
        this.serviceRepository = serviceRepository;
        this.defaultLimit = defaultLimit;
        this.maximumLimit = maximumLimit;
    }

    public List<IncidentResponse> findForService(
            UUID serviceId, IncidentStatus status, Integer requestedLimit) {
        if (!serviceRepository.existsById(serviceId)) {
            throw new ResourceNotFoundException("Service " + serviceId + " was not found");
        }
        var page = PageRequest.of(0, resolveLimit(requestedLimit));
        List<Incident> incidents = status == null
                ? incidentRepository.findByServiceIdOrderByStartedAtDescIdDesc(serviceId, page)
                : incidentRepository.findByServiceIdAndStatusOrderByStartedAtDescIdDesc(
                        serviceId, status, page);
        return incidents.stream().map(IncidentResponseMapper::toResponse).toList();
    }

    public List<IncidentResponse> findActive(Integer requestedLimit) {
        return incidentRepository.findByStatusOrderByStartedAtDescIdDesc(
                        IncidentStatus.ACTIVE, PageRequest.of(0, resolveLimit(requestedLimit)))
                .stream().map(IncidentResponseMapper::toResponse).toList();
    }

    private int resolveLimit(Integer requestedLimit) {
        if (requestedLimit == null) return defaultLimit;
        if (requestedLimit < 1) throw new IllegalArgumentException("Incident limit must be at least 1");
        return Math.min(requestedLimit, maximumLimit);
    }
}
