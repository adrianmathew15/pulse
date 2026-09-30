package com.pulse.service;

import com.pulse.dto.CreateServiceRequest;
import com.pulse.dto.ServiceResponse;
import com.pulse.dto.UpdateThresholdsRequest;
import com.pulse.entity.MonitoredService;
import com.pulse.exception.DuplicateServiceException;
import com.pulse.exception.ResourceNotFoundException;
import com.pulse.repository.ServiceRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.UUID;

@Service
@Transactional(readOnly = true)
public class ServiceManagementService {
    private final ServiceRepository repository;

    public ServiceManagementService(ServiceRepository repository) {
        this.repository = repository;
    }

    @Transactional
    public ServiceResponse create(CreateServiceRequest request) {
        String name = requireName(request.name());
        if (repository.existsByNameIgnoreCase(name)) {
            throw duplicate(name);
        }

        MonitoredService service = new MonitoredService(
                name,
                normalizeOptional(request.description()),
                normalizeOptional(request.endpoint()),
                thresholdOrDefault(request.cpuWarningThreshold()),
                thresholdOrDefault(request.memoryWarningThreshold())
        );

        try {
            return toResponse(repository.saveAndFlush(service));
        } catch (DataIntegrityViolationException exception) {
            throw duplicate(name);
        }
    }

    public List<ServiceResponse> findAll() {
        return repository.findAllByOrderByCreatedAtDescIdAsc().stream()
                .map(this::toResponse)
                .toList();
    }

    public ServiceResponse findById(UUID id) {
        return toResponse(findEntity(id));
    }

    @Transactional
    public void delete(UUID id) {
        MonitoredService service = findEntity(id);
        repository.delete(service);
    }

    @Transactional
    public ServiceResponse updateThresholds(UUID id, UpdateThresholdsRequest request) {
        MonitoredService service = findEntity(id);
        service.updateThresholds(
                threshold(request.cpuWarningThreshold(), "CPU warning threshold"),
                threshold(request.memoryWarningThreshold(), "Memory warning threshold")
        );
        return toResponse(service);
    }

    private MonitoredService findEntity(UUID id) {
        return repository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Service " + id + " was not found"));
    }

    private String requireName(String name) {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("Service name must not be blank");
        }
        return name.trim();
    }

    private String normalizeOptional(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private BigDecimal thresholdOrDefault(BigDecimal value) {
        return value == null ? new BigDecimal("80.00") : threshold(value, "Warning threshold");
    }

    private BigDecimal threshold(BigDecimal value, String field) {
        BigDecimal rounded = value == null ? null : value.setScale(2, RoundingMode.HALF_UP);
        if (rounded == null || rounded.compareTo(new BigDecimal("0.01")) < 0
                || value.compareTo(new BigDecimal("100")) > 0) {
            throw new IllegalArgumentException(field + " must be at least 0.01 and at most 100");
        }
        return rounded;
    }

    private DuplicateServiceException duplicate(String name) {
        return new DuplicateServiceException("A service named '" + name + "' already exists");
    }

    private ServiceResponse toResponse(MonitoredService service) {
        return new ServiceResponse(
                service.getId(), service.getName(), service.getDescription(),
                service.getEndpoint(), service.getStatus(), service.getCreatedAt(),
                service.getCpuWarningThreshold(), service.getMemoryWarningThreshold()
        );
    }
}
