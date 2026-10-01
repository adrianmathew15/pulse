package com.pulse.service;

import com.pulse.entity.MonitoredService;
import com.pulse.repository.ServiceRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class HealthCheckMonitoringService {
    private static final Logger log = LoggerFactory.getLogger(HealthCheckMonitoringService.class);

    private final ServiceRepository serviceRepository;
    private final EndpointHealthChecker endpointHealthChecker;
    private final HealthCheckResultService resultService;

    public HealthCheckMonitoringService(ServiceRepository serviceRepository,
                                        EndpointHealthChecker endpointHealthChecker,
                                        HealthCheckResultService resultService) {
        this.serviceRepository = serviceRepository;
        this.endpointHealthChecker = endpointHealthChecker;
        this.resultService = resultService;
    }

    public void checkAllServices() {
        for (MonitoredService service : serviceRepository.findAll()) {
            try {
                HealthCheckOutcome outcome = endpointHealthChecker.check(service.getEndpoint());
                resultService.record(service.getId(), outcome);
            } catch (RuntimeException exception) {
                log.error("Health check failed unexpectedly for service {}", service.getId(), exception);
            }
        }
    }
}
