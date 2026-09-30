CREATE TABLE metrics (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    service_id UUID NOT NULL,
    cpu_usage NUMERIC(5, 2) NOT NULL,
    memory_usage NUMERIC(5, 2) NOT NULL,
    timestamp TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_metrics_service
        FOREIGN KEY (service_id) REFERENCES services (id) ON DELETE CASCADE,
    CONSTRAINT ck_metrics_cpu_usage CHECK (cpu_usage BETWEEN 0 AND 100),
    CONSTRAINT ck_metrics_memory_usage CHECK (memory_usage BETWEEN 0 AND 100)
);

CREATE INDEX ix_metrics_service_timestamp
    ON metrics (service_id, timestamp DESC, id DESC);
