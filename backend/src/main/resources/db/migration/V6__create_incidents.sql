CREATE TABLE incidents (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    service_id UUID NOT NULL REFERENCES services(id) ON DELETE CASCADE,
    started_at TIMESTAMPTZ NOT NULL,
    resolved_at TIMESTAMPTZ,
    status VARCHAR(20) NOT NULL,
    failure_reason VARCHAR(1000),
    http_status INTEGER,
    CONSTRAINT ck_incidents_status CHECK (status IN ('ACTIVE', 'RESOLVED')),
    CONSTRAINT ck_incidents_http_status CHECK (
        http_status IS NULL OR (http_status >= 100 AND http_status <= 599)
    ),
    CONSTRAINT ck_incidents_resolution CHECK (
        (status = 'ACTIVE' AND resolved_at IS NULL)
        OR (status = 'RESOLVED' AND resolved_at IS NOT NULL AND resolved_at >= started_at)
    )
);

CREATE INDEX ix_incidents_service_started_at
    ON incidents (service_id, started_at DESC, id DESC);

CREATE INDEX ix_incidents_active_started_at
    ON incidents (started_at DESC, id DESC)
    WHERE status = 'ACTIVE';

CREATE UNIQUE INDEX ux_incidents_one_active_per_service
    ON incidents (service_id)
    WHERE status = 'ACTIVE';
