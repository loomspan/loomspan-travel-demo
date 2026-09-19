-- Deterministic March 2027 airfare catalog.  The schedule matrix is deliberately
-- compact; dated instances are derived from the two usable service-date ranges.

INSERT INTO catalog_destination (catalog_key, name, country_code, latitude, longitude) VALUES
    ('destination-sfo', 'San Francisco', 'US', 37.7749, -122.4194),
    ('destination-muc', 'Munich', 'DE', 48.1351, 11.5820),
    ('destination-mex', 'Mexico City', 'MX', 19.4326, -99.1332);

INSERT INTO catalog_airport (catalog_key, iata_code, name, destination_id, latitude, longitude, time_zone_id) VALUES
    ('airport-pdx', 'PDX', 'Portland International Airport', NULL, 45.5898, -122.5951, 'America/Los_Angeles'),
    ('airport-sfo', 'SFO', 'San Francisco International Airport', (SELECT id FROM catalog_destination WHERE catalog_key = 'destination-sfo'), 37.6213, -122.3790, 'America/Los_Angeles'),
    ('airport-muc', 'MUC', 'Munich Airport', (SELECT id FROM catalog_destination WHERE catalog_key = 'destination-muc'), 48.3538, 11.7861, 'Europe/Berlin'),
    ('airport-mex', 'MEX', 'Mexico City International Airport', (SELECT id FROM catalog_destination WHERE catalog_key = 'destination-mex'), 19.4361, -99.0719, 'America/Mexico_City'),
    ('airport-sea', 'SEA', 'Seattle-Tacoma International Airport', NULL, 47.4502, -122.3088, 'America/Los_Angeles'),
    ('airport-slc', 'SLC', 'Salt Lake City International Airport', NULL, 40.7899, -111.9791, 'America/Denver'),
    ('airport-ord', 'ORD', 'O''Hare International Airport', NULL, 41.9742, -87.9073, 'America/Chicago'),
    ('airport-lax', 'LAX', 'Los Angeles International Airport', NULL, 33.9416, -118.4085, 'America/Los_Angeles'),
    ('airport-dfw', 'DFW', 'Dallas Fort Worth International Airport', NULL, 32.8998, -97.0403, 'America/Chicago');

INSERT INTO catalog_supplier (catalog_key, name, supplier_category) VALUES
    ('supplier-cascade-skies', 'Cascade Skies', 'AIRLINE'),
    ('supplier-meridian-air', 'Meridian Air', 'AIRLINE');

INSERT INTO flight_schedule (catalog_key, supplier_id, supplier_category, flight_number, origin_airport_id, destination_airport_id, stop_count, segment_count) VALUES
    ('airfare-out-sfo-d1', (SELECT id FROM catalog_supplier WHERE catalog_key = 'supplier-cascade-skies'), 'AIRLINE', 'CS101', (SELECT id FROM catalog_airport WHERE iata_code = 'PDX'), (SELECT id FROM catalog_airport WHERE iata_code = 'SFO'), 0, 1),
    ('airfare-in-sfo-d1', (SELECT id FROM catalog_supplier WHERE catalog_key = 'supplier-cascade-skies'), 'AIRLINE', 'CS102', (SELECT id FROM catalog_airport WHERE iata_code = 'SFO'), (SELECT id FROM catalog_airport WHERE iata_code = 'PDX'), 0, 1),
    ('airfare-out-sfo-d2', (SELECT id FROM catalog_supplier WHERE catalog_key = 'supplier-meridian-air'), 'AIRLINE', 'MA118', (SELECT id FROM catalog_airport WHERE iata_code = 'PDX'), (SELECT id FROM catalog_airport WHERE iata_code = 'SFO'), 0, 1),
    ('airfare-in-sfo-d2', (SELECT id FROM catalog_supplier WHERE catalog_key = 'supplier-meridian-air'), 'AIRLINE', 'MA119', (SELECT id FROM catalog_airport WHERE iata_code = 'SFO'), (SELECT id FROM catalog_airport WHERE iata_code = 'PDX'), 0, 1),
    ('airfare-out-sfo-c1', (SELECT id FROM catalog_supplier WHERE catalog_key = 'supplier-cascade-skies'), 'AIRLINE', 'CS121', (SELECT id FROM catalog_airport WHERE iata_code = 'PDX'), (SELECT id FROM catalog_airport WHERE iata_code = 'SFO'), 1, 2),
    ('airfare-in-sfo-c1', (SELECT id FROM catalog_supplier WHERE catalog_key = 'supplier-cascade-skies'), 'AIRLINE', 'CS122', (SELECT id FROM catalog_airport WHERE iata_code = 'SFO'), (SELECT id FROM catalog_airport WHERE iata_code = 'PDX'), 1, 2),
    ('airfare-out-sfo-c2', (SELECT id FROM catalog_supplier WHERE catalog_key = 'supplier-meridian-air'), 'AIRLINE', 'MA128', (SELECT id FROM catalog_airport WHERE iata_code = 'PDX'), (SELECT id FROM catalog_airport WHERE iata_code = 'SFO'), 1, 2),
    ('airfare-in-sfo-c2', (SELECT id FROM catalog_supplier WHERE catalog_key = 'supplier-meridian-air'), 'AIRLINE', 'MA129', (SELECT id FROM catalog_airport WHERE iata_code = 'SFO'), (SELECT id FROM catalog_airport WHERE iata_code = 'PDX'), 1, 2),
    ('airfare-out-muc-d1', (SELECT id FROM catalog_supplier WHERE catalog_key = 'supplier-cascade-skies'), 'AIRLINE', 'CS401', (SELECT id FROM catalog_airport WHERE iata_code = 'PDX'), (SELECT id FROM catalog_airport WHERE iata_code = 'MUC'), 0, 1),
    ('airfare-in-muc-d1', (SELECT id FROM catalog_supplier WHERE catalog_key = 'supplier-cascade-skies'), 'AIRLINE', 'CS402', (SELECT id FROM catalog_airport WHERE iata_code = 'MUC'), (SELECT id FROM catalog_airport WHERE iata_code = 'PDX'), 0, 1),
    ('airfare-out-muc-d2', (SELECT id FROM catalog_supplier WHERE catalog_key = 'supplier-meridian-air'), 'AIRLINE', 'MA418', (SELECT id FROM catalog_airport WHERE iata_code = 'PDX'), (SELECT id FROM catalog_airport WHERE iata_code = 'MUC'), 0, 1),
    ('airfare-in-muc-d2', (SELECT id FROM catalog_supplier WHERE catalog_key = 'supplier-meridian-air'), 'AIRLINE', 'MA419', (SELECT id FROM catalog_airport WHERE iata_code = 'MUC'), (SELECT id FROM catalog_airport WHERE iata_code = 'PDX'), 0, 1),
    ('airfare-out-muc-c1', (SELECT id FROM catalog_supplier WHERE catalog_key = 'supplier-cascade-skies'), 'AIRLINE', 'CS431', (SELECT id FROM catalog_airport WHERE iata_code = 'PDX'), (SELECT id FROM catalog_airport WHERE iata_code = 'MUC'), 1, 2),
    ('airfare-in-muc-c1', (SELECT id FROM catalog_supplier WHERE catalog_key = 'supplier-cascade-skies'), 'AIRLINE', 'CS432', (SELECT id FROM catalog_airport WHERE iata_code = 'MUC'), (SELECT id FROM catalog_airport WHERE iata_code = 'PDX'), 1, 2),
    ('airfare-out-muc-c2', (SELECT id FROM catalog_supplier WHERE catalog_key = 'supplier-meridian-air'), 'AIRLINE', 'MA438', (SELECT id FROM catalog_airport WHERE iata_code = 'PDX'), (SELECT id FROM catalog_airport WHERE iata_code = 'MUC'), 1, 2),
    ('airfare-in-muc-c2', (SELECT id FROM catalog_supplier WHERE catalog_key = 'supplier-meridian-air'), 'AIRLINE', 'MA439', (SELECT id FROM catalog_airport WHERE iata_code = 'MUC'), (SELECT id FROM catalog_airport WHERE iata_code = 'PDX'), 1, 2),
    ('airfare-out-mex-d1', (SELECT id FROM catalog_supplier WHERE catalog_key = 'supplier-cascade-skies'), 'AIRLINE', 'CS501', (SELECT id FROM catalog_airport WHERE iata_code = 'PDX'), (SELECT id FROM catalog_airport WHERE iata_code = 'MEX'), 0, 1),
    ('airfare-in-mex-d1', (SELECT id FROM catalog_supplier WHERE catalog_key = 'supplier-cascade-skies'), 'AIRLINE', 'CS502', (SELECT id FROM catalog_airport WHERE iata_code = 'MEX'), (SELECT id FROM catalog_airport WHERE iata_code = 'PDX'), 0, 1),
    ('airfare-out-mex-d2', (SELECT id FROM catalog_supplier WHERE catalog_key = 'supplier-meridian-air'), 'AIRLINE', 'MA518', (SELECT id FROM catalog_airport WHERE iata_code = 'PDX'), (SELECT id FROM catalog_airport WHERE iata_code = 'MEX'), 0, 1),
    ('airfare-in-mex-d2', (SELECT id FROM catalog_supplier WHERE catalog_key = 'supplier-meridian-air'), 'AIRLINE', 'MA519', (SELECT id FROM catalog_airport WHERE iata_code = 'MEX'), (SELECT id FROM catalog_airport WHERE iata_code = 'PDX'), 0, 1),
    ('airfare-out-mex-c1', (SELECT id FROM catalog_supplier WHERE catalog_key = 'supplier-cascade-skies'), 'AIRLINE', 'CS531', (SELECT id FROM catalog_airport WHERE iata_code = 'PDX'), (SELECT id FROM catalog_airport WHERE iata_code = 'MEX'), 1, 2),
    ('airfare-in-mex-c1', (SELECT id FROM catalog_supplier WHERE catalog_key = 'supplier-cascade-skies'), 'AIRLINE', 'CS532', (SELECT id FROM catalog_airport WHERE iata_code = 'MEX'), (SELECT id FROM catalog_airport WHERE iata_code = 'PDX'), 1, 2),
    ('airfare-out-mex-c2', (SELECT id FROM catalog_supplier WHERE catalog_key = 'supplier-meridian-air'), 'AIRLINE', 'MA538', (SELECT id FROM catalog_airport WHERE iata_code = 'PDX'), (SELECT id FROM catalog_airport WHERE iata_code = 'MEX'), 1, 2),
    ('airfare-in-mex-c2', (SELECT id FROM catalog_supplier WHERE catalog_key = 'supplier-meridian-air'), 'AIRLINE', 'MA539', (SELECT id FROM catalog_airport WHERE iata_code = 'MEX'), (SELECT id FROM catalog_airport WHERE iata_code = 'PDX'), 1, 2);

INSERT INTO flight_schedule_segment (flight_schedule_id, segment_ordinal, origin_airport_id, destination_airport_id, departure_local_time, arrival_local_time, arrival_day_offset, scheduled_duration_minutes)
SELECT s.id, x.ordinal, ao.id, ad.id, x.departure_time, x.arrival_time, x.arrival_day_offset, x.duration_minutes
FROM (
    VALUES
      ('airfare-out-sfo-d1', 1, 'PDX', 'SFO', TIME '08:15:00', TIME '10:10:00', 0, 115), ('airfare-in-sfo-d1', 1, 'SFO', 'PDX', TIME '08:05:00', TIME '10:00:00', 0, 115),
      ('airfare-out-sfo-d2', 1, 'PDX', 'SFO', TIME '13:40:00', TIME '15:45:00', 0, 125), ('airfare-in-sfo-d2', 1, 'SFO', 'PDX', TIME '16:20:00', TIME '18:20:00', 0, 120),
      ('airfare-out-sfo-c1', 1, 'PDX', 'SEA', TIME '06:35:00', TIME '07:35:00', 0, 60), ('airfare-out-sfo-c1', 2, 'SEA', 'SFO', TIME '08:35:00', TIME '10:50:00', 0, 135),
      ('airfare-in-sfo-c1', 1, 'SFO', 'SEA', TIME '06:15:00', TIME '08:30:00', 0, 135), ('airfare-in-sfo-c1', 2, 'SEA', 'PDX', TIME '09:30:00', TIME '10:30:00', 0, 60),
      ('airfare-out-sfo-c2', 1, 'PDX', 'SLC', TIME '09:00:00', TIME '11:05:00', 0, 125), ('airfare-out-sfo-c2', 2, 'SLC', 'SFO', TIME '12:00:00', TIME '12:55:00', 0, 115),
      ('airfare-in-sfo-c2', 1, 'SFO', 'SLC', TIME '07:20:00', TIME '10:10:00', 0, 110), ('airfare-in-sfo-c2', 2, 'SLC', 'PDX', TIME '11:10:00', TIME '11:15:00', 0, 125),
      ('airfare-out-muc-d1', 1, 'PDX', 'MUC', TIME '13:10:00', TIME '09:00:00', 1, 650), ('airfare-in-muc-d1', 1, 'MUC', 'PDX', TIME '09:15:00', TIME '11:30:00', 0, 735),
      ('airfare-out-muc-d2', 1, 'PDX', 'MUC', TIME '17:15:00', TIME '13:05:00', 1, 650), ('airfare-in-muc-d2', 1, 'MUC', 'PDX', TIME '12:20:00', TIME '14:30:00', 0, 730),
      ('airfare-out-muc-c1', 1, 'PDX', 'SEA', TIME '07:10:00', TIME '08:15:00', 0, 65), ('airfare-out-muc-c1', 2, 'SEA', 'MUC', TIME '09:20:00', TIME '07:00:00', 1, 640),
      ('airfare-in-muc-c1', 1, 'MUC', 'SEA', TIME '08:20:00', TIME '10:15:00', 0, 655), ('airfare-in-muc-c1', 2, 'SEA', 'PDX', TIME '11:20:00', TIME '12:25:00', 0, 65),
      ('airfare-out-muc-c2', 1, 'PDX', 'ORD', TIME '05:50:00', TIME '11:30:00', 0, 220), ('airfare-out-muc-c2', 2, 'ORD', 'MUC', TIME '12:40:00', TIME '05:40:00', 1, 660),
      ('airfare-in-muc-c2', 1, 'MUC', 'ORD', TIME '07:10:00', TIME '10:20:00', 0, 670), ('airfare-in-muc-c2', 2, 'ORD', 'PDX', TIME '11:30:00', TIME '13:45:00', 0, 255),
      ('airfare-out-mex-d1', 1, 'PDX', 'MEX', TIME '08:25:00', TIME '14:20:00', 0, 295), ('airfare-in-mex-d1', 1, 'MEX', 'PDX', TIME '07:55:00', TIME '10:05:00', 0, 310),
      ('airfare-out-mex-d2', 1, 'PDX', 'MEX', TIME '14:50:00', TIME '20:55:00', 0, 305), ('airfare-in-mex-d2', 1, 'MEX', 'PDX', TIME '14:10:00', TIME '16:20:00', 0, 310),
      ('airfare-out-mex-c1', 1, 'PDX', 'LAX', TIME '06:30:00', TIME '08:45:00', 0, 135), ('airfare-out-mex-c1', 2, 'LAX', 'MEX', TIME '10:00:00', TIME '15:35:00', 0, 215),
      ('airfare-in-mex-c1', 1, 'MEX', 'LAX', TIME '06:40:00', TIME '08:40:00', 0, 240), ('airfare-in-mex-c1', 2, 'LAX', 'PDX', TIME '09:45:00', TIME '12:00:00', 0, 135),
      ('airfare-out-mex-c2', 1, 'PDX', 'DFW', TIME '07:15:00', TIME '13:10:00', 0, 235), ('airfare-out-mex-c2', 2, 'DFW', 'MEX', TIME '14:15:00', TIME '15:55:00', 0, 160),
      ('airfare-in-mex-c2', 1, 'MEX', 'DFW', TIME '08:30:00', TIME '12:05:00', 0, 215), ('airfare-in-mex-c2', 2, 'DFW', 'PDX', TIME '13:15:00', TIME '15:30:00', 0, 255)
) x(schedule_key, ordinal, origin_iata, destination_iata, departure_time, arrival_time, arrival_day_offset, duration_minutes)
JOIN flight_schedule s ON s.catalog_key = x.schedule_key
JOIN catalog_airport ao ON ao.iata_code = x.origin_iata
JOIN catalog_airport ad ON ad.iata_code = x.destination_iata;

INSERT INTO flight_instance (catalog_key, flight_schedule_id, service_date, segment_count, base_fare_cents, tax_cents, fee_cents, seat_capacity, available_seats)
SELECT 'airfare-' || CASE WHEN s.catalog_key LIKE 'airfare-out-%' THEN 'out-' ELSE 'in-' END
       || SUBSTRING(s.catalog_key, CASE WHEN s.catalog_key LIKE 'airfare-out-%' THEN 13 ELSE 12 END)
       || '-' || REPLACE(CAST(d.service_date AS VARCHAR), '-', ''),
       s.id, d.service_date, s.segment_count, p.base_fare_cents, p.tax_cents, p.fee_cents, p.seat_capacity, p.seat_capacity
FROM flight_schedule s
JOIN (
    VALUES
      ('sfo', 'd1', 16500, 1410, 390, 48), ('sfo', 'd2', 15400, 1290, 360, 56), ('sfo', 'c1', 11900, 1150, 330, 40), ('sfo', 'c2', 13200, 1210, 350, 44),
      ('muc', 'd1', 58800, 4670, 610, 36), ('muc', 'd2', 55900, 4450, 590, 40), ('muc', 'c1', 50100, 4020, 550, 32), ('muc', 'c2', 52600, 4210, 570, 36),
      ('mex', 'd1', 29400, 2360, 490, 52), ('mex', 'd2', 27200, 2180, 460, 60), ('mex', 'c1', 22300, 1780, 410, 48), ('mex', 'c2', 24700, 1960, 430, 54)
) p(destination_code, option_code, base_fare_cents, tax_cents, fee_cents, seat_capacity)
  ON s.catalog_key LIKE '%-' || p.destination_code || '-' || p.option_code
JOIN (
    SELECT DATEADD('DAY', x - 1, DATE '2027-03-01') service_date, 'out' direction FROM SYSTEM_RANGE(1, 30)
    UNION ALL
    SELECT DATEADD('DAY', x - 1, DATE '2027-03-02') service_date, 'in' direction FROM SYSTEM_RANGE(1, 30)
) d ON s.catalog_key LIKE 'airfare-' || d.direction || '-%'
WHERE NOT (s.catalog_key LIKE 'airfare-out-%' AND d.service_date > DATE '2027-03-30')
  AND NOT (s.catalog_key LIKE 'airfare-in-%' AND d.service_date < DATE '2027-03-02');

INSERT INTO flight_instance_segment (flight_instance_id, segment_ordinal, departure_at, arrival_at)
SELECT i.id, ss.segment_ordinal,
       CAST(CAST(i.service_date AS VARCHAR) || ' ' || CAST(ss.departure_local_time AS VARCHAR) || ' ' || origin.time_zone_id AS TIMESTAMP WITH TIME ZONE),
       CAST(CAST(DATEADD('DAY', ss.arrival_day_offset, i.service_date) AS VARCHAR) || ' ' || CAST(ss.arrival_local_time AS VARCHAR) || ' ' || destination.time_zone_id AS TIMESTAMP WITH TIME ZONE)
FROM flight_instance i
JOIN flight_schedule_segment ss ON ss.flight_schedule_id = i.flight_schedule_id
JOIN catalog_airport origin ON origin.id = ss.origin_airport_id
JOIN catalog_airport destination ON destination.id = ss.destination_airport_id
WHERE CAST(DATEADD('DAY', ss.arrival_day_offset, i.service_date) AS VARCHAR) <= '2027-03-31';
