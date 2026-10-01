package com.pulse.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "health_checks")
public class HealthCheck {
    @Id
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "service_id", nullable = false)
    private MonitoredService service;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ServiceStatus status;

    @Column(name = "http_status")
    private Integer httpStatus;

    @Column(name = "response_time_ms", nullable = false)
    private long responseTimeMs;

    @Column(name = "checked_at", nullable = false, updatable = false)
    private Instant checkedAt;

    @Column(name = "failure_reason", length = 1000)
    private String failureReason;

    protected HealthCheck() {
    }

    public HealthCheck(MonitoredService service, ServiceStatus status, Integer httpStatus,
                       long responseTimeMs, Instant checkedAt, String failureReason) {
        this.id = UUID.randomUUID();
        this.service = service;
        this.status = status;
        this.httpStatus = httpStatus;
        this.responseTimeMs = responseTimeMs;
        this.checkedAt = checkedAt;
        this.failureReason = failureReason;
    }

    @PrePersist
    void applyDefaults() {
        if (id == null) id = UUID.randomUUID();
        if (checkedAt == null) checkedAt = Instant.now();
    }

    public UUID getId() { return id; }
    public MonitoredService getService() { return service; }
    public ServiceStatus getStatus() { return status; }
    public Integer getHttpStatus() { return httpStatus; }
    public long getResponseTimeMs() { return responseTimeMs; }
    public Instant getCheckedAt() { return checkedAt; }
    public String getFailureReason() { return failureReason; }
}
