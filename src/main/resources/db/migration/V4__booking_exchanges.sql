ALTER TABLE assessment ADD COLUMN base_booking_id VARCHAR(40);
CREATE TABLE booking_exchange (
    trip_id VARCHAR(40) NOT NULL REFERENCES trip(id),
    idempotency_key VARCHAR(80) NOT NULL,
    before_json CLOB NOT NULL,
    after_json CLOB NOT NULL,
    PRIMARY KEY(trip_id, idempotency_key)
);
