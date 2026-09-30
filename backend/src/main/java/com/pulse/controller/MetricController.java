package com.pulse.controller;

import com.pulse.dto.MetricResponse;
import com.pulse.service.MetricService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/services/{serviceId}/metrics")
public class MetricController {
    private final MetricService metricService;

    public MetricController(MetricService metricService) {
        this.metricService = metricService;
    }

    @GetMapping
    public List<MetricResponse> findRecent(@PathVariable UUID serviceId,
                                           @RequestParam(required = false) Integer limit) {
        return metricService.findRecent(serviceId, limit);
    }
}
