package com.pulse.service;

import com.pulse.dto.AlertEventType;
import com.pulse.entity.Alert;
import com.pulse.entity.AlertMetricType;
import com.pulse.entity.AlertStatus;
import com.pulse.entity.MonitoredService;
import com.pulse.repository.AlertRepository;
import com.pulse.repository.ServiceRepository;
import com.pulse.websocket.AlertChangedEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AlertEvaluationServiceTest {
    private static final Instant OBSERVED_AT = Instant.parse("2026-01-01T00:00:00Z");

    @Mock
    private ServiceRepository serviceRepository;
    @Mock
    private AlertRepository alertRepository;
    @Mock
    private ApplicationEventPublisher eventPublisher;

    private AlertEvaluationService evaluationService;

    @BeforeEach
    void setUp() {
        evaluationService = new AlertEvaluationService(
                serviceRepository, alertRepository, eventPublisher);
    }

    @Test
    void createsNoAlertWhenBothMetricsAreAtOrBelowThreshold() {
        MonitoredService service = service("Normal", "80", "80");
        prepare(service);

        evaluationService.evaluate(service.getId(), decimal("80"), decimal("40"), OBSERVED_AT);

        verify(alertRepository, never()).save(any());
        verify(eventPublisher, never()).publishEvent(any());
    }

    @Test
    void createsCpuAlertOnlyWhenCpuCrossesThreshold() {
        MonitoredService service = service("CPU", "50", "90");
        prepare(service);

        evaluationService.evaluate(service.getId(), decimal("51"), decimal("30"), OBSERVED_AT);

        Alert saved = capturedAlert();
        assertThat(saved.getMetricType()).isEqualTo(AlertMetricType.CPU);
        assertThat(saved.getStatus()).isEqualTo(AlertStatus.ACTIVE);
        assertThat(saved.getThreshold()).isEqualByComparingTo("50.00");
        assertThat(saved.getTriggeredValue()).isEqualByComparingTo("51");
        AlertChangedEvent event = capturedEvent();
        assertThat(event.payload().eventType()).isEqualTo(AlertEventType.CREATED);
        assertThat(event.payload().alert().id()).isEqualTo(saved.getId());
    }

    @Test
    void createsMemoryAlertIndependently() {
        MonitoredService service = service("Memory", "90", "50");
        prepare(service);

        evaluationService.evaluate(service.getId(), decimal("30"), decimal("51"), OBSERVED_AT);

        assertThat(capturedAlert().getMetricType()).isEqualTo(AlertMetricType.MEMORY);
    }

    @Test
    void suppressesDuplicateWhileEpisodeIsActive() {
        MonitoredService service = service("Duplicate", "50", "90");
        Alert active = new Alert(service, com.pulse.entity.AlertType.CPU_THRESHOLD,
                "CPU usage exceeded its warning threshold", AlertMetricType.CPU,
                decimal("50"), decimal("60"), OBSERVED_AT.minusSeconds(5));
        when(serviceRepository.findByIdForUpdate(service.getId())).thenReturn(Optional.of(service));
        when(alertRepository.findByServiceIdAndMetricTypeAndStatus(
                service.getId(), AlertMetricType.CPU, AlertStatus.ACTIVE)).thenReturn(Optional.of(active));
        when(alertRepository.findByServiceIdAndMetricTypeAndStatus(
                service.getId(), AlertMetricType.MEMORY, AlertStatus.ACTIVE)).thenReturn(Optional.empty());

        evaluationService.evaluate(service.getId(), decimal("75"), decimal("20"), OBSERVED_AT);

        verify(alertRepository, never()).save(any());
        verify(eventPublisher, never()).publishEvent(any());
        assertThat(active.getStatus()).isEqualTo(AlertStatus.ACTIVE);
    }

    @Test
    void resolvesActiveAlertWhenMetricReturnsToThresholdOrBelow() {
        MonitoredService service = service("Resolution", "50", "90");
        Alert active = new Alert(service, com.pulse.entity.AlertType.CPU_THRESHOLD,
                "CPU usage exceeded its warning threshold", AlertMetricType.CPU,
                decimal("50"), decimal("60"), OBSERVED_AT.minusSeconds(5));
        when(serviceRepository.findByIdForUpdate(service.getId())).thenReturn(Optional.of(service));
        when(alertRepository.findByServiceIdAndMetricTypeAndStatus(
                service.getId(), AlertMetricType.CPU, AlertStatus.ACTIVE)).thenReturn(Optional.of(active));
        when(alertRepository.findByServiceIdAndMetricTypeAndStatus(
                service.getId(), AlertMetricType.MEMORY, AlertStatus.ACTIVE)).thenReturn(Optional.empty());

        evaluationService.evaluate(service.getId(), decimal("50"), decimal("20"), OBSERVED_AT);

        assertThat(active.getStatus()).isEqualTo(AlertStatus.RESOLVED);
        assertThat(active.getResolvedAt()).isEqualTo(OBSERVED_AT);
        AlertChangedEvent event = capturedEvent();
        assertThat(event.payload().eventType()).isEqualTo(AlertEventType.RESOLVED);
        assertThat(event.payload().alert().status()).isEqualTo(AlertStatus.RESOLVED);
    }

    @Test
    void createsNewEpisodeAfterResolutionAndRetrigger() {
        MonitoredService service = service("Retrigger", "50", "90");
        List<Alert> episodes = statefulRepository(service);

        evaluationService.evaluate(service.getId(), decimal("60"), decimal("20"), OBSERVED_AT);
        evaluationService.evaluate(service.getId(), decimal("40"), decimal("20"), OBSERVED_AT.plusSeconds(5));
        evaluationService.evaluate(service.getId(), decimal("70"), decimal("20"), OBSERVED_AT.plusSeconds(10));

        assertThat(episodes).hasSize(2);
        assertThat(episodes.get(0).getStatus()).isEqualTo(AlertStatus.RESOLVED);
        assertThat(episodes.get(1).getStatus()).isEqualTo(AlertStatus.ACTIVE);
    }

    @Test
    void keepsAlertEpisodesIsolatedByService() {
        MonitoredService first = service("First", "50", "90");
        MonitoredService second = service("Second", "50", "90");
        when(serviceRepository.findByIdForUpdate(first.getId())).thenReturn(Optional.of(first));
        when(serviceRepository.findByIdForUpdate(second.getId())).thenReturn(Optional.of(second));
        when(alertRepository.findByServiceIdAndMetricTypeAndStatus(any(), any(), eq(AlertStatus.ACTIVE)))
                .thenReturn(Optional.empty());

        evaluationService.evaluate(first.getId(), decimal("60"), decimal("20"), OBSERVED_AT);
        evaluationService.evaluate(second.getId(), decimal("40"), decimal("20"), OBSERVED_AT);

        ArgumentCaptor<Alert> alerts = ArgumentCaptor.forClass(Alert.class);
        verify(alertRepository).save(alerts.capture());
        assertThat(alerts.getValue().getService().getId()).isEqualTo(first.getId());
    }

    private void prepare(MonitoredService service) {
        when(serviceRepository.findByIdForUpdate(service.getId())).thenReturn(Optional.of(service));
        when(alertRepository.findByServiceIdAndMetricTypeAndStatus(
                any(), any(), eq(AlertStatus.ACTIVE))).thenReturn(Optional.empty());
    }

    private List<Alert> statefulRepository(MonitoredService service) {
        List<Alert> episodes = new ArrayList<>();
        when(serviceRepository.findByIdForUpdate(service.getId())).thenReturn(Optional.of(service));
        when(alertRepository.findByServiceIdAndMetricTypeAndStatus(
                any(), any(), eq(AlertStatus.ACTIVE))).thenAnswer(call -> episodes.stream()
                .filter(alert -> alert.getService().getId().equals(call.getArgument(0)))
                .filter(alert -> alert.getMetricType() == call.getArgument(1))
                .filter(alert -> alert.getStatus() == AlertStatus.ACTIVE)
                .findFirst());
        when(alertRepository.save(any(Alert.class))).thenAnswer(call -> {
            Alert alert = call.getArgument(0);
            episodes.add(alert);
            return alert;
        });
        return episodes;
    }

    private Alert capturedAlert() {
        ArgumentCaptor<Alert> alert = ArgumentCaptor.forClass(Alert.class);
        verify(alertRepository).save(alert.capture());
        return alert.getValue();
    }

    private AlertChangedEvent capturedEvent() {
        ArgumentCaptor<AlertChangedEvent> event = ArgumentCaptor.forClass(AlertChangedEvent.class);
        verify(eventPublisher).publishEvent(event.capture());
        return event.getValue();
    }

    private MonitoredService service(String name, String cpu, String memory) {
        return new MonitoredService(name, null, null, decimal(cpu), decimal(memory));
    }

    private BigDecimal decimal(String value) {
        return new BigDecimal(value).setScale(2);
    }
}
