ALTER TABLE detour_trip_draft DROP CONSTRAINT fk_detour_trip_draft_trip;
ALTER TABLE detour_trip_draft DROP CONSTRAINT uq_detour_trip_draft_trip_id;
ALTER TABLE detour_trip_draft ADD CONSTRAINT fk_detour_trip_draft_trip FOREIGN KEY (trip_id) REFERENCES detour_trip(id);
