package com.pulse.service;

import com.pulse.entity.MonitoredService;
import com.pulse.entity.ServiceStatus;
import com.pulse.repository.ServiceRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;

import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class HealthCheckMonitoringServiceTest {
    @Mock private ServiceRepository serviceRepository;
    @Mock private EndpointHealthChecker endpointHealthChecker;
    @Mock private HealthCheckResultService resultService;

    @Test
    void oneServiceFailureDoesNotStopRemainingChecks() {
        MonitoredService first = new MonitoredService("First", null, "http://first.test");
        MonitoredService second = new MonitoredService("Second", null, "http://second.test");
        HealthCheckOutcome firstOutcome = new HealthCheckOutcome(
                ServiceStatus.DOWN, null, 5, Instant.now(), "connection failed");
        HealthCheckOutcome secondOutcome = new HealthCheckOutcome(
                ServiceStatus.UP, 200, 8, Instant.now(), null);
        when(serviceRepository.findAll()).thenReturn(List.of(first, second));
        when(endpointHealthChecker.check(first.getEndpoint())).thenReturn(firstOutcome);
        when(endpointHealthChecker.check(second.getEndpoint())).thenReturn(secondOutcome);
        doThrow(new IllegalStateException("database unavailable"))
                .when(resultService).record(first.getId(), firstOutcome);

        new HealthCheckMonitoringService(serviceRepository, endpointHealthChecker, resultService)
                .checkAllServices();

        verify(resultService).record(first.getId(), firstOutcome);
        verify(resultService).record(second.getId(), secondOutcome);
    }

    @Test
    void blockedDestinationCreatesNoResultAndDoesNotStopRemainingChecks() {
        MonitoredService blocked = new MonitoredService("Blocked", null, "http://localhost");
        MonitoredService publicService = new MonitoredService("Public", null, "https://8.8.8.8");
        HealthCheckOutcome publicOutcome = new HealthCheckOutcome(
                ServiceStatus.UP, 200, 8, Instant.now(), null);
        when(serviceRepository.findAll()).thenReturn(List.of(blocked, publicService));
        when(endpointHealthChecker.check(blocked.getEndpoint())).thenThrow(
                new EndpointNotAllowedException("Endpoint destination is not allowed"));
        when(endpointHealthChecker.check(publicService.getEndpoint())).thenReturn(publicOutcome);

        new HealthCheckMonitoringService(serviceRepository, endpointHealthChecker, resultService)
                .checkAllServices();

        verify(resultService, never()).record(org.mockito.ArgumentMatchers.eq(blocked.getId()),
                org.mockito.ArgumentMatchers.any());
        verify(resultService).record(publicService.getId(), publicOutcome);
    }
}
