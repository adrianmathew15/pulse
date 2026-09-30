package com.pulse.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "services")
public class MonitoredService {
    @Id
    private UUID id;

    @Column(nullable = false, length = 120)
    private String name;

    @Column(length = 1000)
    private String description;

    @Column(length = 2048)
    private String endpoint;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ServiceStatus status;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "cpu_warning_threshold", nullable = false, precision = 5, scale = 2)
    private BigDecimal cpuWarningThreshold;

    @Column(name = "memory_warning_threshold", nullable = false, precision = 5, scale = 2)
    private BigDecimal memoryWarningThreshold;

    protected MonitoredService() {
    }

    public MonitoredService(String name, String description, String endpoint) {
        this(name, description, endpoint, new BigDecimal("80.00"), new BigDecimal("80.00"));
    }

    public MonitoredService(String name, String description, String endpoint,
                            BigDecimal cpuWarningThreshold, BigDecimal memoryWarningThreshold) {
        this.id = UUID.randomUUID();
        this.name = name;
        this.description = description;
        this.endpoint = endpoint;
        this.status = ServiceStatus.UP;
        this.createdAt = Instant.now();
        this.cpuWarningThreshold = cpuWarningThreshold;
        this.memoryWarningThreshold = memoryWarningThreshold;
    }

    @PrePersist
    void applyDefaults() {
        if (id == null) id = UUID.randomUUID();
        if (status == null) status = ServiceStatus.UP;
        if (createdAt == null) createdAt = Instant.now();
        if (cpuWarningThreshold == null) cpuWarningThreshold = new BigDecimal("80.00");
        if (memoryWarningThreshold == null) memoryWarningThreshold = new BigDecimal("80.00");
    }

    public void updateThresholds(BigDecimal cpuWarningThreshold, BigDecimal memoryWarningThreshold) {
        this.cpuWarningThreshold = cpuWarningThreshold;
        this.memoryWarningThreshold = memoryWarningThreshold;
    }

    public UUID getId() { return id; }
    public String getName() { return name; }
    public String getDescription() { return description; }
    public String getEndpoint() { return endpoint; }
    public ServiceStatus getStatus() { return status; }
    public Instant getCreatedAt() { return createdAt; }
    public BigDecimal getCpuWarningThreshold() { return cpuWarningThreshold; }
    public BigDecimal getMemoryWarningThreshold() { return memoryWarningThreshold; }
}
