ALTER TABLE services
    ADD COLUMN cpu_warning_threshold NUMERIC(5, 2) NOT NULL DEFAULT 80.00,
    ADD COLUMN memory_warning_threshold NUMERIC(5, 2) NOT NULL DEFAULT 80.00,
    ADD CONSTRAINT ck_services_cpu_warning_threshold
        CHECK (cpu_warning_threshold > 0 AND cpu_warning_threshold <= 100),
    ADD CONSTRAINT ck_services_memory_warning_threshold
        CHECK (memory_warning_threshold > 0 AND memory_warning_threshold <= 100);

CREATE TABLE alerts (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    service_id UUID NOT NULL,
    type VARCHAR(40) NOT NULL,
    message VARCHAR(500) NOT NULL,
    severity VARCHAR(20) NOT NULL,
    metric_type VARCHAR(20) NOT NULL,
    threshold NUMERIC(5, 2) NOT NULL,
    triggered_value NUMERIC(5, 2) NOT NULL,
    triggered_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    resolved_at TIMESTAMPTZ,
    status VARCHAR(20) NOT NULL,
    CONSTRAINT fk_alerts_service
        FOREIGN KEY (service_id) REFERENCES services (id) ON DELETE CASCADE,
    CONSTRAINT ck_alerts_type CHECK (type IN ('CPU_THRESHOLD', 'MEMORY_THRESHOLD')),
    CONSTRAINT ck_alerts_severity CHECK (severity IN ('WARNING')),
    CONSTRAINT ck_alerts_metric_type CHECK (metric_type IN ('CPU', 'MEMORY')),
    CONSTRAINT ck_alerts_status CHECK (status IN ('ACTIVE', 'RESOLVED')),
    CONSTRAINT ck_alerts_threshold CHECK (threshold > 0 AND threshold <= 100),
    CONSTRAINT ck_alerts_triggered_value CHECK (triggered_value BETWEEN 0 AND 100),
    CONSTRAINT ck_alerts_resolution CHECK (
        (status = 'ACTIVE' AND resolved_at IS NULL)
        OR (status = 'RESOLVED' AND resolved_at IS NOT NULL)
    )
);

CREATE INDEX ix_alerts_service_triggered_at
    ON alerts (service_id, triggered_at DESC, id DESC);

CREATE INDEX ix_alerts_status_triggered_at
    ON alerts (status, triggered_at DESC, id DESC);

CREATE UNIQUE INDEX ux_alerts_one_active_episode
    ON alerts (service_id, metric_type)
    WHERE status = 'ACTIVE';
