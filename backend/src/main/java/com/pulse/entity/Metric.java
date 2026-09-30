package com.pulse.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "metrics")
public class Metric {
    @Id
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "service_id", nullable = false)
    private MonitoredService service;

    @Column(name = "cpu_usage", nullable = false, precision = 5, scale = 2)
    private BigDecimal cpuUsage;

    @Column(name = "memory_usage", nullable = false, precision = 5, scale = 2)
    private BigDecimal memoryUsage;

    @Column(nullable = false, updatable = false)
    private Instant timestamp;

    protected Metric() {
    }

    public Metric(MonitoredService service, BigDecimal cpuUsage,
                  BigDecimal memoryUsage, Instant timestamp) {
        this.id = UUID.randomUUID();
        this.service = service;
        this.cpuUsage = cpuUsage;
        this.memoryUsage = memoryUsage;
        this.timestamp = timestamp;
    }

    @PrePersist
    void applyDefaults() {
        if (id == null) id = UUID.randomUUID();
        if (timestamp == null) timestamp = Instant.now();
    }

    public UUID getId() { return id; }
    public MonitoredService getService() { return service; }
    public BigDecimal getCpuUsage() { return cpuUsage; }
    public BigDecimal getMemoryUsage() { return memoryUsage; }
    public Instant getTimestamp() { return timestamp; }
}
