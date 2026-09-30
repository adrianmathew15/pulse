package com.pulse.controller;

import com.pulse.repository.ServiceRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
        "spring.datasource.url=${DB_URL:jdbc:postgresql://localhost:5432/pulse}?currentSchema=pulse_test",
        "spring.flyway.schemas=pulse_test",
        "spring.jpa.properties.hibernate.default_schema=pulse_test",
        "pulse.cors.allowed-origin=http://localhost:3000",
        "pulse.metrics.simulation-enabled=false"
})
@AutoConfigureMockMvc
class ServiceControllerIntegrationTest {
    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ServiceRepository repository;

    @BeforeEach
    void cleanDatabase() {
        repository.deleteAll();
    }

    @AfterEach
    void cleanDatabaseAfterTest() {
        repository.deleteAll();
    }

    @Test
    void completesCreateListGetDeleteWorkflowAgainstPostgres() throws Exception {
        String location = mockMvc.perform(post("/api/services")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"Payment API","description":"Payments","endpoint":"https://api.example.com"}
                                """))
                .andExpect(status().isCreated())
                .andExpect(header().exists("Location"))
                .andExpect(jsonPath("$.status").value("UP"))
                .andReturn().getResponse().getHeader("Location");

        String id = location.substring(location.lastIndexOf('/') + 1);

        mockMvc.perform(get("/api/services"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].name").value("Payment API"));

        mockMvc.perform(get("/api/services/{id}", id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.description").value("Payments"));

        mockMvc.perform(delete("/api/services/{id}", id))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/services/{id}", id))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("Not Found"));
    }

    @Test
    void returnsValidationAndDuplicateErrors() throws Exception {
        mockMvc.perform(post("/api/services")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"\",\"endpoint\":\"not-a-url\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Validation Error"))
                .andExpect(jsonPath("$.fieldErrors.name").exists())
                .andExpect(jsonPath("$.fieldErrors.endpoint").exists());

        String request = "{\"name\":\"Payment API\",\"endpoint\":\"https://api.example.com\"}";
        mockMvc.perform(post("/api/services").contentType(MediaType.APPLICATION_JSON).content(request))
                .andExpect(status().isCreated());
        mockMvc.perform(post("/api/services").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"payment api\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value("Conflict"));
    }

    @Test
    void allowsBrowserOriginToPatchAndPersistThresholds() throws Exception {
        String location = mockMvc.perform(post("/api/services")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Threshold API\"}"))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getHeader("Location");
        String id = location.substring(location.lastIndexOf('/') + 1);

        mockMvc.perform(patch("/api/services/{id}/thresholds", id)
                        .header("Origin", "http://localhost:3000")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"cpuWarningThreshold":42.25,"memoryWarningThreshold":73.50}
                                """))
                .andExpect(status().isOk())
                .andExpect(header().string("Access-Control-Allow-Origin", "http://localhost:3000"))
                .andExpect(jsonPath("$.cpuWarningThreshold").value(42.25))
                .andExpect(jsonPath("$.memoryWarningThreshold").value(73.50));

        mockMvc.perform(get("/api/services/{id}", id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.cpuWarningThreshold").value(42.25))
                .andExpect(jsonPath("$.memoryWarningThreshold").value(73.50));
    }
}
