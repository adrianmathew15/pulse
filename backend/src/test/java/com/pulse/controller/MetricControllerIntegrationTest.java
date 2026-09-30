package com.pulse.controller;

import com.pulse.entity.MonitoredService;
import com.pulse.repository.MetricRepository;
import com.pulse.repository.ServiceRepository;
import com.pulse.service.MetricGenerationService;
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

import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
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
class MetricControllerIntegrationTest {
    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private ServiceRepository serviceRepository;
    @Autowired
    private MetricRepository metricRepository;
    @Autowired
    private MetricGenerationService generationService;
    @Autowired
    private MetricService metricService;

    @BeforeEach
    @AfterEach
    void cleanDatabase() {
        metricRepository.deleteAll();
        serviceRepository.deleteAll();
    }

    @Test
    void createsServiceGeneratesAndRetrievesPersistedMetric() throws Exception {
        String location = mockMvc.perform(post("/api/services")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"Payment API","endpoint":"https://api.example.com"}
                                """))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getHeader("Location");
        String serviceId = location.substring(location.lastIndexOf('/') + 1);

        generationService.generateForAllServices();

        mockMvc.perform(get("/api/services/{serviceId}/metrics", serviceId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].serviceId").value(serviceId))
                .andExpect(jsonPath("$[0].cpuUsage").isNumber())
                .andExpect(jsonPath("$[0].memoryUsage").isNumber());
    }

    @Test
    void returnsNewestBoundedWindowInChronologicalOrder() throws Exception {
        MonitoredService service = serviceRepository.saveAndFlush(
                new MonitoredService("Ordering API", null, null));
        metricService.record(service, 10, 20, Instant.parse("2026-01-01T00:00:01Z"));
        metricService.record(service, 20, 30, Instant.parse("2026-01-01T00:00:02Z"));
        metricService.record(service, 30, 40, Instant.parse("2026-01-01T00:00:03Z"));

        mockMvc.perform(get("/api/services/{serviceId}/metrics", service.getId())
                        .param("limit", "2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)))
                .andExpect(jsonPath("$[0].cpuUsage").value(20.0))
                .andExpect(jsonPath("$[1].cpuUsage").value(30.0));
    }

    @Test
    void returnsConsistentErrorsForMissingServiceAndInvalidLimit() throws Exception {
        mockMvc.perform(get("/api/services/{serviceId}/metrics", UUID.randomUUID()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("Not Found"));

        MonitoredService service = serviceRepository.saveAndFlush(
                new MonitoredService("Validation API", null, null));
        mockMvc.perform(get("/api/services/{serviceId}/metrics", service.getId())
                        .param("limit", "0"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Invalid Request"));
    }
}
