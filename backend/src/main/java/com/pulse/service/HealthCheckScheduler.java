package com.pulse.service;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(prefix = "pulse.health", name = "enabled",
        havingValue = "true", matchIfMissing = true)
public class HealthCheckScheduler {
    private final HealthCheckMonitoringService monitoringService;

    public HealthCheckScheduler(HealthCheckMonitoringService monitoringService) {
        this.monitoringService = monitoringService;
    }

    @Scheduled(
            fixedDelayString = "${pulse.health.interval-ms:30000}",
            initialDelayString = "${pulse.health.interval-ms:30000}"
    )
    public void checkServices() {
        monitoringService.checkAllServices();
    }
}
