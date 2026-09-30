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

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "alerts")
public class Alert {
    @Id
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "service_id", nullable = false)
    private MonitoredService service;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 40)
    private AlertType type;

    @Column(nullable = false, length = 500)
    private String message;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private AlertSeverity severity;

    @Enumerated(EnumType.STRING)
    @Column(name = "metric_type", nullable = false, length = 20)
    private AlertMetricType metricType;

    @Column(nullable = false, precision = 5, scale = 2)
    private BigDecimal threshold;

    @Column(name = "triggered_value", nullable = false, precision = 5, scale = 2)
    private BigDecimal triggeredValue;

    @Column(name = "triggered_at", nullable = false, updatable = false)
    private Instant triggeredAt;

    @Column(name = "resolved_at")
    private Instant resolvedAt;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private AlertStatus status;

    protected Alert() {
    }

    public Alert(MonitoredService service, AlertType type, String message,
                 AlertMetricType metricType, BigDecimal threshold,
                 BigDecimal triggeredValue, Instant triggeredAt) {
        this.id = UUID.randomUUID();
        this.service = service;
        this.type = type;
        this.message = message;
        this.severity = AlertSeverity.WARNING;
        this.metricType = metricType;
        this.threshold = threshold;
        this.triggeredValue = triggeredValue;
        this.triggeredAt = triggeredAt;
        this.status = AlertStatus.ACTIVE;
    }

    @PrePersist
    void applyDefaults() {
        if (id == null) id = UUID.randomUUID();
        if (severity == null) severity = AlertSeverity.WARNING;
        if (status == null) status = AlertStatus.ACTIVE;
        if (triggeredAt == null) triggeredAt = Instant.now();
    }

    public void resolve(Instant resolvedAt) {
        if (status == AlertStatus.ACTIVE) {
            status = AlertStatus.RESOLVED;
            this.resolvedAt = resolvedAt == null ? Instant.now() : resolvedAt;
        }
    }

    public UUID getId() { return id; }
    public MonitoredService getService() { return service; }
    public AlertType getType() { return type; }
    public String getMessage() { return message; }
    public AlertSeverity getSeverity() { return severity; }
    public AlertMetricType getMetricType() { return metricType; }
    public BigDecimal getThreshold() { return threshold; }
    public BigDecimal getTriggeredValue() { return triggeredValue; }
    public Instant getTriggeredAt() { return triggeredAt; }
    public Instant getResolvedAt() { return resolvedAt; }
    public AlertStatus getStatus() { return status; }
}
