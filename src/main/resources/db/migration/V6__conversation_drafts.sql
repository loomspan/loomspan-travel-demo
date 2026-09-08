CREATE TABLE intake_draft (
    id VARCHAR(40) PRIMARY KEY,
    trip_id VARCHAR(40) NOT NULL REFERENCES trip(id),
    view_json CLOB NOT NULL,
    created_at VARCHAR(40) NOT NULL,
    confirmed_revision INTEGER
);
