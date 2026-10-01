package com.pulse.controller;

import com.pulse.dto.IncidentResponse;
import com.pulse.entity.IncidentStatus;
import com.pulse.service.IncidentQueryService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api")
public class IncidentController {
    private final IncidentQueryService incidentQueryService;

    public IncidentController(IncidentQueryService incidentQueryService) {
        this.incidentQueryService = incidentQueryService;
    }

    @GetMapping("/services/{serviceId}/incidents")
    public List<IncidentResponse> findForService(
            @PathVariable UUID serviceId,
            @RequestParam(required = false) IncidentStatus status,
            @RequestParam(required = false) Integer limit) {
        return incidentQueryService.findForService(serviceId, status, limit);
    }

    @GetMapping("/incidents/active")
    public List<IncidentResponse> findActive(@RequestParam(required = false) Integer limit) {
        return incidentQueryService.findActive(limit);
    }
}
