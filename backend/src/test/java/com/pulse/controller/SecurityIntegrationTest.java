package com.pulse.controller;

import com.jayway.jsonpath.JsonPath;
import com.pulse.config.SecurityConfig;
import com.pulse.repository.ServiceRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
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
class SecurityIntegrationTest {
    @Autowired MockMvc mockMvc;
    @Autowired JwtEncoder jwtEncoder;
    @Autowired UserDetailsService userDetailsService;
    @Autowired PasswordEncoder passwordEncoder;
    @Autowired ServiceRepository serviceRepository;

    @AfterEach
    void cleanDatabase() {
        serviceRepository.deleteAll();
    }

    @Test
    void validLoginReturnsSignedAccessToken() throws Exception {
        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"pulse-test\",\"password\":\"password\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").isNotEmpty())
                .andExpect(jsonPath("$.expiresIn").value(900000));
    }

    @Test
    void invalidCredentialsAreUnauthorized() throws Exception {
        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"pulse-test\",\"password\":\"wrong\"}"))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"not-the-user\",\"password\":\"password\"}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void configuredPasswordIsOnlyStoredAsBcryptHash() {
        String stored = userDetailsService.loadUserByUsername("pulse-test").getPassword();
        assertThat(stored).isNotEqualTo("password").startsWith("$2");
        assertThat(passwordEncoder.matches("password", stored)).isTrue();
    }

    @Test
    void missingInvalidAndExpiredTokensAreUnauthorized() throws Exception {
        mockMvc.perform(get("/api/services")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/services").header("Authorization", "Bearer invalid"))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/services").header("Authorization", "Bearer " + expiredToken()))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void authenticatedGetPostPatchAndDeleteWork() throws Exception {
        String token = loginToken();
        String location = mockMvc.perform(post("/api/services")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Secured API\",\"endpoint\":\"https://8.8.8.8\"}"))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getHeader("Location");
        String id = location.substring(location.lastIndexOf('/') + 1);

        mockMvc.perform(get("/api/services").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());
        mockMvc.perform(patch("/api/services/{id}/thresholds", id)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"cpuWarningThreshold\":45,\"memoryWarningThreshold\":55}"))
                .andExpect(status().isOk());
        mockMvc.perform(delete("/api/services/{id}", id)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isNoContent());
    }

    @Test
    void actuatorHealthRemainsPublic() throws Exception {
        mockMvc.perform(get("/actuator/health")).andExpect(status().isOk());
    }

    private String loginToken() throws Exception {
        String body = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"pulse-test\",\"password\":\"password\"}"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        return JsonPath.read(body, "$.token");
    }

    private String expiredToken() {
        Instant now = Instant.now();
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer(SecurityConfig.JWT_ISSUER).subject("pulse-test")
                .issuedAt(now.minusSeconds(120)).expiresAt(now.minusSeconds(60)).build();
        return jwtEncoder.encode(JwtEncoderParameters.from(
                JwsHeader.with(MacAlgorithm.HS256).build(), claims)).getTokenValue();
    }
}
