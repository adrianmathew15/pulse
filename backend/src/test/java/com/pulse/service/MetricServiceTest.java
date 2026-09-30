package com.pulse.service;

import com.pulse.entity.Metric;
import com.pulse.entity.MonitoredService;
import com.pulse.exception.ResourceNotFoundException;
import com.pulse.repository.MetricRepository;
import com.pulse.repository.ServiceRepository;
import com.pulse.websocket.MetricPersistedEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Pageable;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MetricServiceTest {
    @Mock
    private MetricRepository metricRepository;
    @Mock
    private ServiceRepository serviceRepository;
    @Mock
    private ApplicationEventPublisher eventPublisher;

    private MetricService service;

    @BeforeEach
    void setUp() {
        service = new MetricService(metricRepository, serviceRepository, eventPublisher, 2, 3);
    }

    @Test
    void recordsRoundedMetricAssociatedWithService() {
        MonitoredService monitoredService = new MonitoredService("Payments", null, null);
        when(metricRepository.save(any(Metric.class))).thenAnswer(call -> call.getArgument(0));

        var result = service.record(monitoredService, 42.126, 61.235, Instant.parse("2026-01-01T00:00:00Z"));

        assertThat(result.serviceId()).isEqualTo(monitoredService.getId());
        assertThat(result.cpuUsage()).isEqualByComparingTo("42.13");
        assertThat(result.memoryUsage()).isEqualByComparingTo("61.24");
        verify(metricRepository).save(any(Metric.class));
        ArgumentCaptor<MetricPersistedEvent> event = ArgumentCaptor.forClass(MetricPersistedEvent.class);
        verify(eventPublisher).publishEvent(event.capture());
        assertThat(event.getValue().metric()).isEqualTo(result);
    }

    @Test
    void doesNotPublishMetricEventWhenPersistenceFails() {
        MonitoredService monitoredService = new MonitoredService("Payments", null, null);
        when(metricRepository.save(any(Metric.class)))
                .thenThrow(new IllegalStateException("database unavailable"));

        assertThatThrownBy(() -> service.record(monitoredService, 42, 61, Instant.now()))
                .isInstanceOf(IllegalStateException.class);

        verify(eventPublisher, never()).publishEvent(any());
    }

    @Test
    void rejectsOutOfRangeMetric() {
        MonitoredService monitoredService = new MonitoredService("Payments", null, null);

        assertThatThrownBy(() -> service.record(monitoredService, 101, 50, Instant.now()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("CPU usage");
        verify(metricRepository, never()).save(any());
    }

    @Test
    void returnsNewestWindowInChronologicalOrderUsingDefaultLimit() {
        MonitoredService monitoredService = new MonitoredService("Payments", null, null);
        Metric older = metric(monitoredService, "10.00", "20.00", "2026-01-01T00:00:01Z");
        Metric newer = metric(monitoredService, "11.00", "21.00", "2026-01-01T00:00:02Z");
        when(serviceRepository.findById(monitoredService.getId())).thenReturn(Optional.of(monitoredService));
        when(metricRepository.findByServiceIdOrderByTimestampDescIdDesc(
                any(UUID.class), any(Pageable.class))).thenReturn(List.of(newer, older));

        var result = service.findRecent(monitoredService.getId(), null);

        assertThat(result).extracting("timestamp")
                .containsExactly(older.getTimestamp(), newer.getTimestamp());
        ArgumentCaptor<Pageable> page = ArgumentCaptor.forClass(Pageable.class);
        verify(metricRepository).findByServiceIdOrderByTimestampDescIdDesc(
                org.mockito.ArgumentMatchers.eq(monitoredService.getId()), page.capture());
        assertThat(page.getValue().getPageSize()).isEqualTo(2);
    }

    @Test
    void capsRequestedLimitAtConfiguredMaximum() {
        MonitoredService monitoredService = new MonitoredService("Payments", null, null);
        when(serviceRepository.findById(monitoredService.getId())).thenReturn(Optional.of(monitoredService));
        when(metricRepository.findByServiceIdOrderByTimestampDescIdDesc(any(), any())).thenReturn(List.of());

        service.findRecent(monitoredService.getId(), 999);

        ArgumentCaptor<Pageable> page = ArgumentCaptor.forClass(Pageable.class);
        verify(metricRepository).findByServiceIdOrderByTimestampDescIdDesc(any(), page.capture());
        assertThat(page.getValue().getPageSize()).isEqualTo(3);
    }

    @Test
    void rejectsNonPositiveLimit() {
        UUID id = UUID.randomUUID();
        when(serviceRepository.findById(id)).thenReturn(Optional.of(new MonitoredService("Payments", null, null)));

        assertThatThrownBy(() -> service.findRecent(id, 0))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsMissingService() {
        UUID id = UUID.randomUUID();
        when(serviceRepository.findById(id)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.findRecent(id, 10))
                .isInstanceOf(ResourceNotFoundException.class);
        verify(metricRepository, never()).findByServiceIdOrderByTimestampDescIdDesc(any(), any());
    }

    private Metric metric(MonitoredService service, String cpu, String memory, String timestamp) {
        return new Metric(service, new BigDecimal(cpu), new BigDecimal(memory), Instant.parse(timestamp));
    }
}
