ALTER TABLE services
    DROP CONSTRAINT ck_services_status,
    ADD CONSTRAINT ck_services_status CHECK (status IN ('UNKNOWN', 'UP', 'DOWN')),
    ALTER COLUMN status SET DEFAULT 'UNKNOWN';
