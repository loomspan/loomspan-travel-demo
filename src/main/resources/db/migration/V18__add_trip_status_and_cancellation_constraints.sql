-- V18__add_trip_status_and_cancellation_constraints.sql
-- Add status to detour_trip and update booking foreign key constraints for immutable history.

ALTER TABLE detour_trip ADD COLUMN status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE';
ALTER TABLE detour_trip ADD CONSTRAINT ck_detour_trip_status CHECK (status IN ('ACTIVE', 'CANCELED'));

ALTER TABLE detour_booking ALTER COLUMN planned_itinerary_id DROP NOT NULL;
ALTER TABLE detour_booking DROP CONSTRAINT fk_booking_planned_itinerary;
ALTER TABLE detour_booking ADD CONSTRAINT fk_booking_planned_itinerary FOREIGN KEY (planned_itinerary_id) REFERENCES detour_planned_itinerary(id) ON DELETE SET NULL;
