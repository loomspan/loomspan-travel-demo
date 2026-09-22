-- Enhance planned snapshot tables with complete descriptive attributes so snapshots
-- never reconstruct display data from live catalog joins.

ALTER TABLE detour_planned_airfare_snapshot ADD COLUMN outbound_carrier_name VARCHAR(200);
ALTER TABLE detour_planned_airfare_snapshot ADD COLUMN outbound_flight_number VARCHAR(50);
ALTER TABLE detour_planned_airfare_snapshot ADD COLUMN outbound_stop_count INTEGER;
ALTER TABLE detour_planned_airfare_snapshot ADD COLUMN outbound_layover_airport_code VARCHAR(10);
ALTER TABLE detour_planned_airfare_snapshot ADD COLUMN outbound_layover_duration_minutes INTEGER;
ALTER TABLE detour_planned_airfare_snapshot ADD COLUMN outbound_departure_time TIMESTAMP WITH TIME ZONE;
ALTER TABLE detour_planned_airfare_snapshot ADD COLUMN outbound_arrival_time TIMESTAMP WITH TIME ZONE;
ALTER TABLE detour_planned_airfare_snapshot ADD COLUMN outbound_departure_timezone VARCHAR(100);
ALTER TABLE detour_planned_airfare_snapshot ADD COLUMN outbound_arrival_timezone VARCHAR(100);
ALTER TABLE detour_planned_airfare_snapshot ADD COLUMN outbound_duration_minutes INTEGER;

ALTER TABLE detour_planned_airfare_snapshot ADD COLUMN return_carrier_name VARCHAR(200);
ALTER TABLE detour_planned_airfare_snapshot ADD COLUMN return_flight_number VARCHAR(50);
ALTER TABLE detour_planned_airfare_snapshot ADD COLUMN return_stop_count INTEGER;
ALTER TABLE detour_planned_airfare_snapshot ADD COLUMN return_layover_airport_code VARCHAR(10);
ALTER TABLE detour_planned_airfare_snapshot ADD COLUMN return_layover_duration_minutes INTEGER;
ALTER TABLE detour_planned_airfare_snapshot ADD COLUMN return_departure_time TIMESTAMP WITH TIME ZONE;
ALTER TABLE detour_planned_airfare_snapshot ADD COLUMN return_arrival_time TIMESTAMP WITH TIME ZONE;
ALTER TABLE detour_planned_airfare_snapshot ADD COLUMN return_departure_timezone VARCHAR(100);
ALTER TABLE detour_planned_airfare_snapshot ADD COLUMN return_arrival_timezone VARCHAR(100);
ALTER TABLE detour_planned_airfare_snapshot ADD COLUMN return_duration_minutes INTEGER;

ALTER TABLE detour_planned_airfare_snapshot ADD COLUMN total_duration_minutes INTEGER;

ALTER TABLE detour_planned_stay_snapshot ADD COLUMN property_category VARCHAR(50);
ALTER TABLE detour_planned_stay_snapshot ADD COLUMN location_description VARCHAR(500);
ALTER TABLE detour_planned_stay_snapshot ADD COLUMN distance_to_city_center_meters INTEGER;
ALTER TABLE detour_planned_stay_snapshot ADD COLUMN guest_capacity INTEGER;
ALTER TABLE detour_planned_stay_snapshot ADD COLUMN required_room_count INTEGER;

ALTER TABLE detour_planned_rental_snapshot ADD COLUMN vehicle_category VARCHAR(50);
