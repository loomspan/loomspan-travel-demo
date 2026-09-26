-- Preserve existing Planned and Booking rows. Only surplus Drafts are normalized.
ALTER TABLE detour_trip ADD COLUMN name VARCHAR(300);
UPDATE detour_trip SET name = COALESCE(NULLIF(TRIM(display_label), ''), 'Trip ' || id);
ALTER TABLE detour_trip ALTER COLUMN name SET NOT NULL;
ALTER TABLE detour_trip ADD CONSTRAINT ck_detour_trip_name CHECK (CHAR_LENGTH(TRIM(name)) BETWEEN 1 AND 300);

ALTER TABLE detour_trip_draft ADD COLUMN start_date DATE;
ALTER TABLE detour_trip_draft ADD COLUMN end_date DATE;
UPDATE detour_trip_draft d SET start_date = (SELECT t.start_date FROM detour_trip t WHERE t.id = d.trip_id),
                               end_date = (SELECT t.end_date FROM detour_trip t WHERE t.id = d.trip_id);

ALTER TABLE detour_planned_itinerary ADD COLUMN name VARCHAR(300);
ALTER TABLE detour_planned_itinerary ADD COLUMN start_date DATE;
ALTER TABLE detour_planned_itinerary ADD COLUMN end_date DATE;
ALTER TABLE detour_planned_itinerary ADD COLUMN version BIGINT NOT NULL DEFAULT 0;
UPDATE detour_planned_itinerary p
SET name = 'Option ' || p.id,
    start_date = (SELECT t.start_date FROM detour_trip t WHERE t.id = p.trip_id),
    end_date = (SELECT t.end_date FROM detour_trip t WHERE t.id = p.trip_id);

CREATE TABLE v19_draft_normalization AS
SELECT d.id AS draft_id, d.trip_id,
       CASE WHEN a.draft_id IS NOT NULL OR s.draft_id IS NOT NULL OR r.draft_id IS NOT NULL THEN 1 ELSE 0 END AS populated,
       ROW_NUMBER() OVER (PARTITION BY d.trip_id ORDER BY
           CASE WHEN a.draft_id IS NOT NULL OR s.draft_id IS NOT NULL OR r.draft_id IS NOT NULL THEN 0 ELSE 1 END, d.id) AS working_rank
FROM detour_trip_draft d
LEFT JOIN detour_trip_draft_airfare_selection a ON a.draft_id = d.id
LEFT JOIN detour_trip_draft_stay_selection s ON s.draft_id = d.id
LEFT JOIN detour_trip_draft_rental_selection r ON r.draft_id = d.id;

CREATE TABLE v19_draft_to_option AS
SELECT n.draft_id, n.trip_id, RANDOM_UUID() AS option_public_id
FROM v19_draft_normalization n WHERE n.working_rank > 1 AND n.populated = 1;

INSERT INTO detour_planned_itinerary (public_id, trip_id, name, start_date, end_date, version)
SELECT m.option_public_id, m.trip_id, 'Option from Draft ' || m.draft_id, t.start_date, t.end_date, 0
FROM v19_draft_to_option m JOIN detour_trip t ON t.id = m.trip_id;

INSERT INTO detour_planned_airfare_snapshot (
    planned_itinerary_id, outbound_flight_instance_id, return_flight_instance_id,
    outbound_description, return_description,
    outbound_base_fare_cents, outbound_tax_cents, outbound_fee_cents,
    return_base_fare_cents, return_tax_cents, return_fee_cents,
    outbound_carrier_name, outbound_flight_number, outbound_stop_count,
    outbound_layover_airport_code, outbound_layover_duration_minutes,
    outbound_departure_time, outbound_arrival_time, outbound_departure_timezone, outbound_arrival_timezone,
    outbound_duration_minutes,
    return_carrier_name, return_flight_number, return_stop_count,
    return_layover_airport_code, return_layover_duration_minutes,
    return_departure_time, return_arrival_time, return_departure_timezone, return_arrival_timezone,
    return_duration_minutes, total_duration_minutes)
SELECT p.id, a.outbound_flight_instance_id, a.return_flight_instance_id,
       'Flight ' || os.flight_number, 'Flight ' || rs.flight_number,
       oi.base_fare_cents, oi.tax_cents, oi.fee_cents,
       ri.base_fare_cents, ri.tax_cents, ri.fee_cents,
       oc.name, os.flight_number, os.stop_count,
       ol.iata_code, TIMESTAMPDIFF(MINUTE, od.arrival_at, od2.departure_at),
       od.departure_at, oa.arrival_at, oap.time_zone_id, oza.time_zone_id,
       TIMESTAMPDIFF(MINUTE, od.departure_at, oa.arrival_at),
       rc.name, rs.flight_number, rs.stop_count,
       rl.iata_code, TIMESTAMPDIFF(MINUTE, rd.arrival_at, rd2.departure_at),
       rd.departure_at, ra.arrival_at, rap.time_zone_id, rza.time_zone_id,
       TIMESTAMPDIFF(MINUTE, rd.departure_at, ra.arrival_at),
       TIMESTAMPDIFF(MINUTE, od.departure_at, oa.arrival_at)
           + TIMESTAMPDIFF(MINUTE, rd.departure_at, ra.arrival_at)
FROM v19_draft_to_option m
JOIN detour_planned_itinerary p ON p.public_id = m.option_public_id
JOIN detour_trip_draft_airfare_selection a ON a.draft_id = m.draft_id
JOIN flight_instance oi ON oi.id = a.outbound_flight_instance_id
JOIN flight_instance ri ON ri.id = a.return_flight_instance_id
JOIN flight_schedule os ON os.id = oi.flight_schedule_id
JOIN flight_schedule rs ON rs.id = ri.flight_schedule_id
JOIN catalog_supplier oc ON oc.id = os.supplier_id
JOIN catalog_supplier rc ON rc.id = rs.supplier_id
JOIN catalog_airport oap ON oap.id = os.origin_airport_id
JOIN catalog_airport oza ON oza.id = os.destination_airport_id
JOIN catalog_airport rap ON rap.id = rs.origin_airport_id
JOIN catalog_airport rza ON rza.id = rs.destination_airport_id
JOIN flight_instance_segment od ON od.flight_instance_id = oi.id AND od.segment_ordinal = 1
JOIN flight_instance_segment rd ON rd.flight_instance_id = ri.id AND rd.segment_ordinal = 1
JOIN flight_instance_segment oa ON oa.flight_instance_id = oi.id AND oa.segment_ordinal = oi.segment_count
JOIN flight_instance_segment ra ON ra.flight_instance_id = ri.id AND ra.segment_ordinal = ri.segment_count
LEFT JOIN flight_instance_segment od2 ON od2.flight_instance_id = oi.id AND od2.segment_ordinal = 2
LEFT JOIN flight_instance_segment rd2 ON rd2.flight_instance_id = ri.id AND rd2.segment_ordinal = 2
LEFT JOIN flight_schedule_segment os1 ON os1.flight_schedule_id = os.id AND os1.segment_ordinal = 1 AND os.stop_count = 1
LEFT JOIN flight_schedule_segment rs1 ON rs1.flight_schedule_id = rs.id AND rs1.segment_ordinal = 1 AND rs.stop_count = 1
LEFT JOIN catalog_airport ol ON ol.id = os1.destination_airport_id
LEFT JOIN catalog_airport rl ON rl.id = rs1.destination_airport_id;

INSERT INTO detour_planned_stay_snapshot (
    planned_itinerary_id, accommodation_unit_id, unit_count, property_name, unit_name,
    property_category, location_description, distance_to_city_center_meters,
    guest_capacity, required_room_count)
SELECT p.id, s.accommodation_unit_id, s.unit_count, ap.name, au.name,
       ap.property_category, ap.location_description, ap.distance_to_city_center_meters,
       au.guest_capacity, CEILING(CAST(t.traveler_count AS DECIMAL) / au.guest_capacity)
FROM v19_draft_to_option m
JOIN detour_planned_itinerary p ON p.public_id = m.option_public_id
JOIN detour_trip t ON t.id = m.trip_id
JOIN detour_trip_draft_stay_selection s ON s.draft_id = m.draft_id
JOIN accommodation_unit au ON au.id = s.accommodation_unit_id
JOIN accommodation_property ap ON ap.id = au.accommodation_property_id;

INSERT INTO detour_planned_stay_night_snapshot (planned_itinerary_id, night_date, base_price_cents, tax_cents, fee_cents)
SELECT p.id, ni.night_date, ni.base_price_cents, ni.tax_cents, ni.fee_cents
FROM v19_draft_to_option m
JOIN detour_planned_itinerary p ON p.public_id = m.option_public_id
JOIN detour_trip t ON t.id = m.trip_id
JOIN detour_trip_draft_stay_selection s ON s.draft_id = m.draft_id
JOIN accommodation_nightly_inventory ni ON ni.accommodation_unit_id = s.accommodation_unit_id
    AND ni.night_date >= t.start_date AND ni.night_date < t.end_date;

INSERT INTO detour_planned_rental_snapshot (
    planned_itinerary_id, rental_unit_id, pickup_at, return_at, location_name,
    vehicle_class_name, unit_identifier, daily_base_price_cents, daily_tax_cents,
    daily_fee_cents, vehicle_category)
SELECT p.id, r.rental_unit_id, r.pickup_at, r.return_at, l.name,
       vc.name, ru.unit_identifier, vc.daily_base_price_cents, vc.daily_tax_cents,
       vc.daily_fee_cents, vc.vehicle_category
FROM v19_draft_to_option m
JOIN detour_planned_itinerary p ON p.public_id = m.option_public_id
JOIN detour_trip_draft_rental_selection r ON r.draft_id = m.draft_id
JOIN rental_unit ru ON ru.id = r.rental_unit_id
JOIN rental_vehicle_class vc ON vc.id = ru.rental_vehicle_class_id
JOIN rental_location l ON l.id = vc.rental_location_id;

-- Fail the migration before deleting Drafts if any component could not be copied.
CREATE TABLE v19_copy_check (valid BOOLEAN NOT NULL CHECK (valid));
INSERT INTO v19_copy_check (valid)
SELECT (SELECT COUNT(*) FROM v19_draft_to_option) =
       (SELECT COUNT(*) FROM detour_planned_itinerary p JOIN v19_draft_to_option m ON m.option_public_id = p.public_id);
INSERT INTO v19_copy_check (valid)
SELECT (SELECT COUNT(*) FROM detour_trip_draft_airfare_selection a JOIN v19_draft_to_option m ON m.draft_id = a.draft_id) =
       (SELECT COUNT(*) FROM detour_planned_airfare_snapshot a JOIN detour_planned_itinerary p ON p.id = a.planned_itinerary_id
        JOIN v19_draft_to_option m ON m.option_public_id = p.public_id);
INSERT INTO v19_copy_check (valid)
SELECT (SELECT COUNT(*) FROM detour_trip_draft_stay_selection s JOIN v19_draft_to_option m ON m.draft_id = s.draft_id) =
       (SELECT COUNT(*) FROM detour_planned_stay_snapshot s JOIN detour_planned_itinerary p ON p.id = s.planned_itinerary_id
        JOIN v19_draft_to_option m ON m.option_public_id = p.public_id);
INSERT INTO v19_copy_check (valid)
SELECT (SELECT COUNT(*) FROM detour_trip_draft_rental_selection r JOIN v19_draft_to_option m ON m.draft_id = r.draft_id) =
       (SELECT COUNT(*) FROM detour_planned_rental_snapshot r JOIN detour_planned_itinerary p ON p.id = r.planned_itinerary_id
        JOIN v19_draft_to_option m ON m.option_public_id = p.public_id);
INSERT INTO v19_copy_check (valid)
SELECT NOT EXISTS (
    SELECT 1 FROM v19_draft_to_option m
    JOIN detour_trip_draft_stay_selection s ON s.draft_id = m.draft_id
    JOIN detour_trip t ON t.id = m.trip_id
    JOIN detour_planned_itinerary p ON p.public_id = m.option_public_id
    LEFT JOIN detour_planned_stay_night_snapshot n ON n.planned_itinerary_id = p.id
    GROUP BY m.draft_id, t.start_date, t.end_date
    HAVING COUNT(n.night_date) <> DATEDIFF('DAY', t.start_date, t.end_date));

DELETE FROM detour_trip_draft WHERE id IN (SELECT draft_id FROM v19_draft_normalization WHERE working_rank > 1);
INSERT INTO detour_trip_draft (public_id, trip_id, version, start_date, end_date)
SELECT RANDOM_UUID(), t.id, 0, t.start_date, t.end_date
FROM detour_trip t LEFT JOIN detour_trip_draft d ON d.trip_id = t.id WHERE d.id IS NULL;

DROP TABLE v19_copy_check;
DROP TABLE v19_draft_to_option;
DROP TABLE v19_draft_normalization;

ALTER TABLE detour_trip_draft ALTER COLUMN start_date SET NOT NULL;
ALTER TABLE detour_trip_draft ALTER COLUMN end_date SET NOT NULL;
ALTER TABLE detour_trip_draft ADD CONSTRAINT ck_detour_trip_draft_dates CHECK (start_date >= DATE '2027-03-01' AND end_date <= DATE '2027-03-31' AND DATEDIFF('DAY', start_date, end_date) BETWEEN 1 AND 14);
ALTER TABLE detour_trip_draft ADD CONSTRAINT uq_detour_trip_draft_working UNIQUE (trip_id);
ALTER TABLE detour_planned_itinerary ALTER COLUMN name SET NOT NULL;
ALTER TABLE detour_planned_itinerary ALTER COLUMN start_date SET NOT NULL;
ALTER TABLE detour_planned_itinerary ALTER COLUMN end_date SET NOT NULL;
ALTER TABLE detour_planned_itinerary ADD CONSTRAINT ck_detour_planned_name CHECK (CHAR_LENGTH(TRIM(name)) BETWEEN 1 AND 300);
ALTER TABLE detour_planned_itinerary ADD CONSTRAINT ck_detour_planned_dates CHECK (start_date >= DATE '2027-03-01' AND end_date <= DATE '2027-03-31' AND DATEDIFF('DAY', start_date, end_date) BETWEEN 1 AND 14);
ALTER TABLE detour_planned_itinerary ADD CONSTRAINT ck_detour_planned_version CHECK (version >= 0);
