DROP TRIGGER flight_schedule_segment_integrity;

DROP TRIGGER flight_instance_segment_integrity;

CREATE TRIGGER flight_schedule_segment_integrity
BEFORE INSERT, UPDATE, DELETE ON flight_schedule_segment
FOR EACH ROW CALL "app.detour.catalog.persistence.FlightSegmentIntegrityTrigger";

CREATE TRIGGER flight_instance_segment_integrity
BEFORE INSERT, UPDATE, DELETE ON flight_instance_segment
FOR EACH ROW CALL "app.detour.catalog.persistence.FlightSegmentIntegrityTrigger";
