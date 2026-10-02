package com.pulse.controller;

import com.pulse.entity.MonitoredService;
import com.pulse.entity.ServiceStatus;
import com.pulse.repository.HealthCheckRepository;
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

import static org.hamcrest.Matchers.hasSize;
import static org.assertj.core.api.Assertions.assertThat;
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
class HealthCheckControllerIntegrationTest extends AuthenticatedMockMvcIntegrationTest {
    @Autowired private ServiceRepository serviceRepository;
    @Autowired private HealthCheckRepository healthCheckRepository;
    @Autowired private HealthCheckResultService resultService;

    @BeforeEach
    @AfterEach
    void cleanDatabase() {
        healthCheckRepository.deleteAll();
        serviceRepository.deleteAll();
    }

    @Test
    void firstFailedCheckChangesUnknownToDownAndPersistsHistory() throws Exception {
        MonitoredService service = serviceRepository.saveAndFlush(
                new MonitoredService("Health API", null, "https://example.com"));
        Instant checkedAt = Instant.parse("2026-09-30T12:00:00Z");

        assertThat(service.getStatus()).isEqualTo(ServiceStatus.UNKNOWN);
        assertThat(healthCheckRepository.count()).isZero();
        resultService.record(service.getId(), new HealthCheckOutcome(
                ServiceStatus.DOWN, 503, 125, checkedAt, "HTTP 503"));

        assertThat(healthCheckRepository.count()).isEqualTo(1);
        assertThat(serviceRepository.findById(service.getId()).orElseThrow().getStatus())
                .isEqualTo(ServiceStatus.DOWN);
        mockMvc.perform(get("/api/services/{serviceId}/health-checks", service.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].status").value("DOWN"))
                .andExpect(jsonPath("$[0].httpStatus").value(503))
                .andExpect(jsonPath("$[0].responseTimeMs").value(125))
                .andExpect(jsonPath("$[0].checkedAt").value("2026-09-30T12:00:00Z"))
                .andExpect(jsonPath("$[0].failureReason").value("HTTP 503"));
    }

    @Test
    void firstSuccessfulCheckChangesUnknownToUp() {
        MonitoredService service = serviceRepository.saveAndFlush(
                new MonitoredService("Successful API", null, "https://example.com"));

        assertThat(service.getStatus()).isEqualTo(ServiceStatus.UNKNOWN);
        resultService.record(service.getId(), new HealthCheckOutcome(
                ServiceStatus.UP, 200, 40, Instant.parse("2026-09-30T12:00:00Z"), null));

        assertThat(serviceRepository.findById(service.getId()).orElseThrow().getStatus())
                .isEqualTo(ServiceStatus.UP);
        assertThat(healthCheckRepository.count()).isEqualTo(1);
    }
}
