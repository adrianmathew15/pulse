CREATE TABLE health_checks (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    service_id UUID NOT NULL REFERENCES services(id) ON DELETE CASCADE,
    status VARCHAR(20) NOT NULL,
    http_status INTEGER,
    response_time_ms BIGINT NOT NULL,
    checked_at TIMESTAMPTZ NOT NULL,
    failure_reason VARCHAR(1000),
    CONSTRAINT ck_health_checks_status CHECK (status IN ('UP', 'DOWN')),
    CONSTRAINT ck_health_checks_http_status CHECK (
        http_status IS NULL OR (http_status >= 100 AND http_status <= 599)
    ),
    CONSTRAINT ck_health_checks_response_time CHECK (response_time_ms >= 0)
);

CREATE INDEX ix_health_checks_service_checked_at
    ON health_checks (service_id, checked_at DESC, id DESC);
