CREATE TABLE service_cancellation (
    service_id VARCHAR(40) PRIMARY KEY REFERENCES travel_service(id),
    created_at VARCHAR(40) NOT NULL
);
ALTER TABLE catalog_guard ADD COLUMN version INTEGER NOT NULL DEFAULT 0;
ALTER TABLE assessment ADD COLUMN catalog_version INTEGER NOT NULL DEFAULT 0;
ALTER TABLE assessment ADD COLUMN recovery_service_id VARCHAR(40);
