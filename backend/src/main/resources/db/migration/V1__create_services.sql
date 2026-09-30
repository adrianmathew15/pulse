CREATE TABLE services (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    name VARCHAR(120) NOT NULL,
    description VARCHAR(1000),
    endpoint VARCHAR(2048),
    status VARCHAR(20) NOT NULL DEFAULT 'UP',
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT ck_services_name_not_blank CHECK (btrim(name) <> ''),
    CONSTRAINT ck_services_status CHECK (status IN ('UP', 'DOWN')),
    CONSTRAINT ck_services_endpoint_http CHECK (
        endpoint IS NULL OR endpoint ~* '^https?://[^[:space:]]+$'
    )
);

CREATE UNIQUE INDEX ux_services_name_normalized
    ON services (lower(btrim(name)));

CREATE INDEX ix_services_created_at
    ON services (created_at DESC, id);
