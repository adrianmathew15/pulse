package com.pulse.controller;

import com.pulse.entity.AlertMetricType;
import com.pulse.entity.AlertStatus;
import com.pulse.entity.MonitoredService;
import com.pulse.repository.AlertRepository;
import com.pulse.repository.MetricRepository;
import com.pulse.repository.ServiceRepository;
import com.pulse.service.AlertEvaluationService;
import com.pulse.service.MetricService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
        "spring.datasource.url=${DB_URL:jdbc:postgresql://localhost:5432/pulse}?currentSchema=pulse_test",
        "spring.flyway.schemas=pulse_test",
        "spring.jpa.properties.hibernate.default_schema=pulse_test",
        "pulse.metrics.simulation-enabled=false"
})
@AutoConfigureMockMvc
class AlertControllerIntegrationTest {
    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private ServiceRepository serviceRepository;
    @Autowired
    private MetricRepository metricRepository;
    @Autowired
    private AlertRepository alertRepository;
    @Autowired
    private MetricService metricService;
    @Autowired
    private AlertEvaluationService alertEvaluationService;

    @BeforeEach
    @AfterEach
    void cleanDatabase() {
        alertRepository.deleteAll();
        metricRepository.deleteAll();
        serviceRepository.deleteAll();
    }

    @Test
    void persistsSuppressesResolvesAndRetriggersAlertEpisodes() throws Exception {
        UUID serviceId = createService("Alert API", 50, 60);
        MonitoredService service = serviceRepository.findById(serviceId).orElseThrow();

        recordAndEvaluate(service, 70, 40, "2026-01-01T00:00:00Z");
        recordAndEvaluate(service, 75, 45, "2026-01-01T00:00:05Z");

        assertThat(alertRepository.count()).isEqualTo(1);
        assertThat(alertRepository.findByServiceIdAndMetricTypeAndStatus(
                serviceId, AlertMetricType.CPU, AlertStatus.ACTIVE)).isPresent();

        mockMvc.perform(get("/api/services/{serviceId}/alerts", serviceId)
                        .param("status", "ACTIVE"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].type").value("CPU_THRESHOLD"))
                .andExpect(jsonPath("$[0].triggeredValue").value(70.0))
                .andExpect(jsonPath("$[0].threshold").value(50.0));

        recordAndEvaluate(service, 45, 40, "2026-01-01T00:00:10Z");
        assertThat(alertRepository.findByServiceIdAndMetricTypeAndStatus(
                serviceId, AlertMetricType.CPU, AlertStatus.ACTIVE)).isEmpty();

        recordAndEvaluate(service, 80, 40, "2026-01-01T00:00:15Z");
        assertThat(alertRepository.count()).isEqualTo(2);

        mockMvc.perform(get("/api/services/{serviceId}/alerts", serviceId).param("limit", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].status").value("ACTIVE"))
                .andExpect(jsonPath("$[0].triggeredValue").value(80.0));

        mockMvc.perform(get("/api/alerts/active"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].serviceId").value(serviceId.toString()));
    }

    @Test
    void supportsThresholdUpdatesAndValidatesAlertQueries() throws Exception {
        UUID serviceId = createService("Threshold API", 80, 80);

        mockMvc.perform(patch("/api/services/{serviceId}/thresholds", serviceId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"cpuWarningThreshold":35,"memoryWarningThreshold":45}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.cpuWarningThreshold").value(35.0))
                .andExpect(jsonPath("$.memoryWarningThreshold").value(45.0));

        mockMvc.perform(patch("/api/services/{serviceId}/thresholds", serviceId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"cpuWarningThreshold":0,"memoryWarningThreshold":101}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Validation Error"));

        mockMvc.perform(get("/api/services/{serviceId}/alerts", serviceId)
                        .param("status", "UNKNOWN"))
                .andExpect(status().isBadRequest());

        mockMvc.perform(get("/api/services/{serviceId}/alerts", serviceId)
                        .param("limit", "0"))
                .andExpect(status().isBadRequest());

        mockMvc.perform(get("/api/services/{serviceId}/alerts", UUID.randomUUID()))
                .andExpect(status().isNotFound());
    }

    private UUID createService(String name, int cpuThreshold, int memoryThreshold) throws Exception {
        String location = mockMvc.perform(post("/api/services")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"%s","cpuWarningThreshold":%d,"memoryWarningThreshold":%d}
                                """.formatted(name, cpuThreshold, memoryThreshold)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getHeader("Location");
        return UUID.fromString(location.substring(location.lastIndexOf('/') + 1));
    }

    private void recordAndEvaluate(MonitoredService service, double cpu, double memory,
                                   String timestamp) {
        var metric = metricService.record(service, cpu, memory, Instant.parse(timestamp));
        alertEvaluationService.evaluate(service.getId(), metric.cpuUsage(),
                metric.memoryUsage(), metric.timestamp());
    }
}
