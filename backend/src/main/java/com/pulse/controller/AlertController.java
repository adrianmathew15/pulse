package com.pulse.controller;

import com.pulse.dto.AlertResponse;
import com.pulse.entity.AlertStatus;
import com.pulse.service.AlertQueryService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api")
public class AlertController {
    private final AlertQueryService alertQueryService;

    public AlertController(AlertQueryService alertQueryService) {
        this.alertQueryService = alertQueryService;
    }

    @GetMapping("/services/{serviceId}/alerts")
    public List<AlertResponse> findForService(
            @PathVariable UUID serviceId,
            @RequestParam(required = false) AlertStatus status,
            @RequestParam(required = false) Integer limit) {
        return alertQueryService.findForService(serviceId, status, limit);
    }

    @GetMapping("/alerts/active")
    public List<AlertResponse> findActive(@RequestParam(required = false) Integer limit) {
        return alertQueryService.findActive(limit);
    }
}
