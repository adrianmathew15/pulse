package com.pulse.controller;

import com.pulse.dto.CreateServiceRequest;
import com.pulse.dto.ServiceResponse;
import com.pulse.dto.UpdateThresholdsRequest;
import com.pulse.service.ServiceManagementService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/services")
public class ServiceController {
    private final ServiceManagementService serviceManagement;

    public ServiceController(ServiceManagementService serviceManagement) {
        this.serviceManagement = serviceManagement;
    }

    @PostMapping
    public ResponseEntity<ServiceResponse> create(@Valid @RequestBody CreateServiceRequest request) {
        ServiceResponse created = serviceManagement.create(request);
        return ResponseEntity.created(URI.create("/api/services/" + created.id())).body(created);
    }

    @GetMapping
    public List<ServiceResponse> findAll() {
        return serviceManagement.findAll();
    }

    @GetMapping("/{id}")
    public ServiceResponse findById(@PathVariable UUID id) {
        return serviceManagement.findById(id);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable UUID id) {
        serviceManagement.delete(id);
        return ResponseEntity.noContent().build();
    }

    @PatchMapping("/{id}/thresholds")
    public ServiceResponse updateThresholds(@PathVariable UUID id,
                                            @Valid @RequestBody UpdateThresholdsRequest request) {
        return serviceManagement.updateThresholds(id, request);
    }
}
