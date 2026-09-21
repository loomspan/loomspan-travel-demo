ALTER TABLE detour_trip_traveler DROP CONSTRAINT fk_detour_trip_traveler_trip;
ALTER TABLE detour_trip_traveler ADD CONSTRAINT fk_detour_trip_traveler_trip FOREIGN KEY (trip_id) REFERENCES detour_trip(id) ON DELETE CASCADE;

ALTER TABLE detour_trip_draft DROP CONSTRAINT fk_detour_trip_draft_trip;
ALTER TABLE detour_trip_draft ADD CONSTRAINT fk_detour_trip_draft_trip FOREIGN KEY (trip_id) REFERENCES detour_trip(id) ON DELETE CASCADE;
