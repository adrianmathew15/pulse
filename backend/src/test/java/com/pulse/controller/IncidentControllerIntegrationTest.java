package com.pulse.controller;

import com.pulse.entity.Incident;
import com.pulse.entity.IncidentStatus;
import com.pulse.entity.MonitoredService;
import com.pulse.entity.ServiceStatus;
import com.pulse.repository.HealthCheckRepository;
import com.pulse.repository.IncidentRepository;
import com.pulse.repository.ServiceRepository;
import com.pulse.service.HealthCheckOutcome;
import com.pulse.service.HealthCheckResultService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
        "spring.datasource.url=${DB_URL:jdbc:postgresql://localhost:5432/pulse}?currentSchema=pulse_test",
        "spring.flyway.schemas=pulse_test",
        "spring.jpa.properties.hibernate.default_schema=pulse_test",
        "pulse.metrics.simulation-enabled=false",
        "pulse.health.enabled=false"
})
@AutoConfigureMockMvc
class IncidentControllerIntegrationTest extends AuthenticatedMockMvcIntegrationTest {
    private static final Instant T0 = Instant.parse("2026-09-30T12:00:00Z");

    @Autowired private ServiceRepository serviceRepository;
    @Autowired private HealthCheckRepository healthCheckRepository;
    @Autowired private IncidentRepository incidentRepository;
    @Autowired private HealthCheckResultService resultService;

    @BeforeEach
    @AfterEach
    void cleanDatabase() {
        incidentRepository.deleteAll();
        healthCheckRepository.deleteAll();
        serviceRepository.deleteAll();
    }

    @Test
    void unknownToUpCreatesNoIncident() {
        MonitoredService service = createService("Healthy API");

        record(service, ServiceStatus.UP, 200, null, T0);

        assertThat(incidentRepository.count()).isZero();
        assertThat(reload(service).getStatus()).isEqualTo(ServiceStatus.UP);
    }

    @Test
    void unknownToDownCreatesOneActiveIncidentAndRepeatedDownDoesNotDuplicate() throws Exception {
        MonitoredService service = createService("Unavailable API");

        record(service, ServiceStatus.DOWN, 503, "HTTP 503", T0);
        record(service, ServiceStatus.DOWN, 503, "HTTP 503", T0.plusSeconds(30));

        assertThat(incidentRepository.count()).isEqualTo(1);
        Incident incident = incidentRepository.findAll().getFirst();
        assertThat(incident.getStatus()).isEqualTo(IncidentStatus.ACTIVE);
        assertThat(incident.getStartedAt()).isEqualTo(T0);
        assertThat(incident.getFailureReason()).isEqualTo("HTTP 503");
        assertThat(incident.getHttpStatus()).isEqualTo(503);

        mockMvc.perform(get("/api/incidents/active"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].serviceId").value(service.getId().toString()))
                .andExpect(jsonPath("$[0].serviceName").value("Unavailable API"))
                .andExpect(jsonPath("$[0].status").value("ACTIVE"));
    }

    @Test
    void upToDownCreatesIncidentAndDownToUpResolvesItWithoutRepeatedUpChanges() {
        MonitoredService service = createService("Recovery API");
        record(service, ServiceStatus.UP, 200, null, T0);

        record(service, ServiceStatus.DOWN, null, "Connection refused", T0.plusSeconds(30));
        record(service, ServiceStatus.UP, 200, null, T0.plusSeconds(60));
        record(service, ServiceStatus.UP, 200, null, T0.plusSeconds(90));

        assertThat(incidentRepository.count()).isEqualTo(1);
        Incident incident = incidentRepository.findAll().getFirst();
        assertThat(incident.getStatus()).isEqualTo(IncidentStatus.RESOLVED);
        assertThat(incident.getStartedAt()).isEqualTo(T0.plusSeconds(30));
        assertThat(incident.getResolvedAt()).isEqualTo(T0.plusSeconds(60));
        assertThat(reload(service).getStatus()).isEqualTo(ServiceStatus.UP);
    }

    @Test
    void laterFailureAfterResolutionCreatesNewIncidentAndStatusFiltersWork() throws Exception {
        MonitoredService service = createService("Flapping API");
        record(service, ServiceStatus.DOWN, 500, "HTTP 500", T0);
        record(service, ServiceStatus.UP, 200, null, T0.plusSeconds(30));
        record(service, ServiceStatus.DOWN, 502, "HTTP 502", T0.plusSeconds(60));

        assertThat(incidentRepository.count()).isEqualTo(2);
        assertThat(incidentRepository.findByServiceIdAndStatus(
                service.getId(), IncidentStatus.ACTIVE)).isPresent();

        mockMvc.perform(get("/api/services/{serviceId}/incidents", service.getId())
                        .param("status", "RESOLVED"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].status").value("RESOLVED"));
        mockMvc.perform(get("/api/services/{serviceId}/incidents", service.getId())
                        .param("status", "ACTIVE"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].httpStatus").value(502));
    }

    @Test
    void deletingServiceCascadesIncidentHistory() throws Exception {
        MonitoredService service = createService("Disposable API");
        record(service, ServiceStatus.DOWN, 503, "HTTP 503", T0);

        mockMvc.perform(delete("/api/services/{id}", service.getId()))
                .andExpect(status().isNoContent());

        assertThat(incidentRepository.count()).isZero();
        assertThat(healthCheckRepository.count()).isZero();
    }

    private MonitoredService createService(String name) {
        return serviceRepository.saveAndFlush(
                new MonitoredService(name, null, "https://example.com"));
    }

    private void record(MonitoredService service, ServiceStatus status, Integer httpStatus,
                        String reason, Instant checkedAt) {
        resultService.record(service.getId(), new HealthCheckOutcome(
                status, httpStatus, 25, checkedAt, reason));
    }

    private MonitoredService reload(MonitoredService service) {
        return serviceRepository.findById(service.getId()).orElseThrow();
    }
}
