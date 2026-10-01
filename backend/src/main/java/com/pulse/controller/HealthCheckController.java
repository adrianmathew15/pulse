package com.pulse.controller;

import com.pulse.dto.HealthCheckResponse;
import com.pulse.service.HealthCheckResultService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/services/{serviceId}/health-checks")
public class HealthCheckController {
    private final HealthCheckResultService resultService;

    public HealthCheckController(HealthCheckResultService resultService) {
        this.resultService = resultService;
    }

    @GetMapping
    public List<HealthCheckResponse> findRecent(@PathVariable UUID serviceId,
                                                @RequestParam(required = false) Integer limit) {
        return resultService.findRecent(serviceId, limit);
    }
}
