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
@Table(name = "incidents")
public class Incident {
    @Id
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "service_id", nullable = false)
    private MonitoredService service;

    @Column(name = "started_at", nullable = false, updatable = false)
    private Instant startedAt;

    @Column(name = "resolved_at")
    private Instant resolvedAt;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private IncidentStatus status;

    @Column(name = "failure_reason", length = 1000)
    private String failureReason;

    @Column(name = "http_status")
    private Integer httpStatus;

    protected Incident() {
    }

    public Incident(MonitoredService service, Instant startedAt,
                    String failureReason, Integer httpStatus) {
        this.id = UUID.randomUUID();
        this.service = service;
        this.startedAt = startedAt;
        this.status = IncidentStatus.ACTIVE;
        this.failureReason = failureReason;
        this.httpStatus = httpStatus;
    }

    @PrePersist
    void applyDefaults() {
        if (id == null) id = UUID.randomUUID();
        if (startedAt == null) startedAt = Instant.now();
        if (status == null) status = IncidentStatus.ACTIVE;
    }

    public void resolve(Instant resolvedAt) {
        if (status == IncidentStatus.ACTIVE) {
            status = IncidentStatus.RESOLVED;
            this.resolvedAt = resolvedAt == null ? Instant.now() : resolvedAt;
        }
    }

    public UUID getId() { return id; }
    public MonitoredService getService() { return service; }
    public Instant getStartedAt() { return startedAt; }
    public Instant getResolvedAt() { return resolvedAt; }
    public IncidentStatus getStatus() { return status; }
    public String getFailureReason() { return failureReason; }
    public Integer getHttpStatus() { return httpStatus; }
}
