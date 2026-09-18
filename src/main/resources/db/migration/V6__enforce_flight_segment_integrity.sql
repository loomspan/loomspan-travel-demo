CREATE TRIGGER flight_schedule_segment_integrity
BEFORE INSERT, UPDATE ON flight_schedule_segment
FOR EACH ROW CALL "app.detour.catalog.persistence.FlightSegmentIntegrityTrigger";

CREATE TRIGGER flight_schedule_route_integrity
BEFORE UPDATE ON flight_schedule
FOR EACH ROW CALL "app.detour.catalog.persistence.FlightSegmentIntegrityTrigger";

CREATE TRIGGER flight_instance_segment_integrity
BEFORE INSERT, UPDATE ON flight_instance_segment
FOR EACH ROW CALL "app.detour.catalog.persistence.FlightSegmentIntegrityTrigger";
