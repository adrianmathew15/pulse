package com.pulse.service;

import com.pulse.entity.MonitoredService;
import com.pulse.dto.MetricResponse;
import com.pulse.repository.ServiceRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.math.BigDecimal;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MetricGenerationServiceTest {
    @Mock
    private ServiceRepository serviceRepository;
    @Mock
    private MetricService metricService;
    @Mock
    private MetricValueSimulator simulator;
    @Mock
    private AlertEvaluationService alertEvaluationService;

    @Test
    void safelyDoesNothingWhenThereAreNoServices() {
        when(serviceRepository.findAll()).thenReturn(List.of());
        var generation = generationService();

        generation.generateForAllServices();

        verify(simulator, never()).next(any());
        verify(metricService, never()).record(any(), anyDouble(), anyDouble(), any());
        verify(alertEvaluationService, never()).evaluate(any(), any(), any(), any());
    }

    @Test
    void persistsOneMetricForEachServiceAndContinuesAfterFailure() {
        MonitoredService first = new MonitoredService("First", null, null);
        MonitoredService second = new MonitoredService("Second", null, null);
        when(serviceRepository.findAll()).thenReturn(List.of(first, second));
        when(simulator.next(first.getId())).thenReturn(new MetricValueSimulator.SimulatedMetric(40, 50));
        when(simulator.next(second.getId())).thenReturn(new MetricValueSimulator.SimulatedMetric(41, 51));
        doThrow(new IllegalStateException("isolated failure"))
                .when(metricService).record(first, 40, 50, Instant.parse("2026-01-01T00:00:00Z"));
        when(metricService.record(second, 41, 51, Instant.parse("2026-01-01T00:00:00Z")))
                .thenReturn(new MetricResponse(java.util.UUID.randomUUID(), second.getId(),
                        new BigDecimal("41.00"), new BigDecimal("51.00"),
                        Instant.parse("2026-01-01T00:00:00Z")));

        generationService().generateForAllServices();

        verify(metricService).record(first, 40, 50, Instant.parse("2026-01-01T00:00:00Z"));
        verify(metricService).record(second, 41, 51, Instant.parse("2026-01-01T00:00:00Z"));
        verify(alertEvaluationService).evaluate(second.getId(), new BigDecimal("41.00"),
                new BigDecimal("51.00"), Instant.parse("2026-01-01T00:00:00Z"));
    }

    private MetricGenerationService generationService() {
        Clock clock = Clock.fixed(Instant.parse("2026-01-01T00:00:00Z"), ZoneOffset.UTC);
        return new MetricGenerationService(serviceRepository, metricService, simulator,
                alertEvaluationService, clock);
    }
}
