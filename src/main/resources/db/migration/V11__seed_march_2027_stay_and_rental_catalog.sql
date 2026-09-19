-- Deterministic March 2027 accommodation and airport-rental catalog.
-- All names below are fictional. Stay availability is stored per night; rental
-- availability is derived from the absence of an intersecting active interval.

INSERT INTO catalog_supplier (catalog_key, name, supplier_category) VALUES
    ('supplier-stays-pacific', 'Pacific Lantern Stays', 'LODGING'),
    ('supplier-stays-bavaria', 'Bavaria Hearth Lodging', 'LODGING'),
    ('supplier-stays-valle', 'Valle Vista Stays', 'LODGING'),
    ('supplier-rental-sfo', 'Harborline Mobility', 'CAR_RENTAL'),
    ('supplier-rental-muc', 'Alpine Roadworks', 'CAR_RENTAL'),
    ('supplier-rental-mex', 'Círculo Drive', 'CAR_RENTAL');

INSERT INTO accommodation_property (catalog_key, supplier_id, supplier_category, destination_id, property_category, name,
                                    latitude, longitude, location_description, guest_rating, distance_to_city_center_meters)
SELECT p.catalog_key, supplier.id, 'LODGING', destination.id, p.property_category, p.name,
       p.latitude, p.longitude, p.location_description, p.guest_rating, p.distance_meters
FROM (
    VALUES
      ('stay-sfo-hotel-harbor', 'supplier-stays-pacific', 'destination-sfo', 'HOTEL', 'Harbor Civic Hotel', 37.7790, -122.4140, 'Civic Center', 4.3, 550),
      ('stay-sfo-hotel-summit', 'supplier-stays-pacific', 'destination-sfo', 'HOTEL', 'Summit Family Suites', 37.7950, -122.3930, 'Embarcadero', 4.7, 1400),
      ('stay-sfo-bnb-mission', 'supplier-stays-pacific', 'destination-sfo', 'BED_AND_BREAKFAST', 'Mission Garden House', 37.7590, -122.4200, 'Mission Dolores', 4.5, 2100),
      ('stay-sfo-bnb-pacific', 'supplier-stays-pacific', 'destination-sfo', 'BED_AND_BREAKFAST', 'Pacific View Inn', 37.8060, -122.4130, 'North Beach', 4.1, 750),
      ('stay-sfo-rental-sunset', 'supplier-stays-pacific', 'destination-sfo', 'VACATION_RENTAL', 'Sunset Courtyard Cottage', 37.7540, -122.4940, 'Outer Sunset', 4.6, 3900),
      ('stay-sfo-rental-presidio', 'supplier-stays-pacific', 'destination-sfo', 'VACATION_RENTAL', 'Presidio Grand Home', 37.7980, -122.4660, 'Presidio', 4.8, 2200),
      ('stay-muc-hotel-isar', 'supplier-stays-bavaria', 'destination-muc', 'HOTEL', 'Isar Market Hotel', 48.1380, 11.5750, 'Altstadt', 4.4, 800),
      ('stay-muc-hotel-alpine', 'supplier-stays-bavaria', 'destination-muc', 'HOTEL', 'Alpine Family Hotel', 48.1650, 11.5900, 'Schwabing', 4.8, 2700),
      ('stay-muc-bnb-glocken', 'supplier-stays-bavaria', 'destination-muc', 'BED_AND_BREAKFAST', 'Glocken Garden B&B', 48.1280, 11.5700, 'Glockenbach', 4.6, 1900),
      ('stay-muc-bnb-englischer', 'supplier-stays-bavaria', 'destination-muc', 'BED_AND_BREAKFAST', 'Englischer Loft Inn', 48.1570, 11.6050, 'Englischer Garten', 4.2, 650),
      ('stay-muc-rental-lehel', 'supplier-stays-bavaria', 'destination-muc', 'VACATION_RENTAL', 'Lehel Courtyard Flat', 48.1450, 11.5880, 'Lehel', 4.7, 1250),
      ('stay-muc-rental-bavaria', 'supplier-stays-bavaria', 'destination-muc', 'VACATION_RENTAL', 'Bavaria Terrace House', 48.1540, 11.5000, 'Nymphenburg', 4.5, 4100),
      ('stay-mex-hotel-centro', 'supplier-stays-valle', 'destination-mex', 'HOTEL', 'Centro Alameda Hotel', 19.4350, -99.1420, 'Alameda Central', 4.1, 500),
      ('stay-mex-hotel-paseo', 'supplier-stays-valle', 'destination-mex', 'HOTEL', 'Paseo Family Suites', 19.4270, -99.1660, 'Paseo de la Reforma', 4.6, 1800),
      ('stay-mex-bnb-coyoacan', 'supplier-stays-valle', 'destination-mex', 'BED_AND_BREAKFAST', 'Coyoacán Courtyard B&B', 19.3500, -99.1620, 'Coyoacán', 4.8, 6400),
      ('stay-mex-bnb-roma', 'supplier-stays-valle', 'destination-mex', 'BED_AND_BREAKFAST', 'Roma Artisan Inn', 19.4180, -99.1640, 'Roma Norte', 4.3, 2100),
      ('stay-mex-rental-condesa', 'supplier-stays-valle', 'destination-mex', 'VACATION_RENTAL', 'Condesa Patio Casa', 19.4130, -99.1790, 'Condesa', 4.7, 3300),
      ('stay-mex-rental-pedregal', 'supplier-stays-valle', 'destination-mex', 'VACATION_RENTAL', 'Pedregal Family Villa', 19.3100, -99.2170, 'Pedregal', 4.4, 9200)
) p(catalog_key, supplier_key, destination_key, property_category, name, latitude, longitude, location_description, guest_rating, distance_meters)
JOIN catalog_supplier supplier ON supplier.catalog_key = p.supplier_key
JOIN catalog_destination destination ON destination.catalog_key = p.destination_key;

INSERT INTO accommodation_unit (catalog_key, accommodation_property_id, property_category, unit_kind, guest_capacity, inventory_capacity, name)
SELECT u.catalog_key, property.id, property.property_category, u.unit_kind, u.guest_capacity, u.inventory_capacity, u.name
FROM (
    VALUES
      ('stay-unit-sfo-hotel-harbor', 'stay-sfo-hotel-harbor', 'ROOM', 2, 6, 'Harbor King Room'),
      ('stay-unit-sfo-hotel-summit', 'stay-sfo-hotel-summit', 'ROOM', 4, 3, 'Bay Family Suite'),
      ('stay-unit-sfo-bnb-mission', 'stay-sfo-bnb-mission', 'ROOM', 2, 4, 'Garden Room'),
      ('stay-unit-sfo-bnb-pacific', 'stay-sfo-bnb-pacific', 'ROOM', 4, 2, 'View Loft'),
      ('stay-unit-sfo-rental-sunset', 'stay-sfo-rental-sunset', 'WHOLE_PROPERTY', 4, 1, 'Entire Cottage'),
      ('stay-unit-sfo-rental-presidio', 'stay-sfo-rental-presidio', 'WHOLE_PROPERTY', 8, 1, 'Entire Home'),
      ('stay-unit-muc-hotel-isar', 'stay-muc-hotel-isar', 'ROOM', 2, 6, 'Market Double'),
      ('stay-unit-muc-hotel-alpine', 'stay-muc-hotel-alpine', 'ROOM', 4, 3, 'Family Studio'),
      ('stay-unit-muc-bnb-glocken', 'stay-muc-bnb-glocken', 'ROOM', 2, 4, 'Garden Room'),
      ('stay-unit-muc-bnb-englischer', 'stay-muc-bnb-englischer', 'ROOM', 4, 2, 'Loft Room'),
      ('stay-unit-muc-rental-lehel', 'stay-muc-rental-lehel', 'WHOLE_PROPERTY', 4, 1, 'Entire Flat'),
      ('stay-unit-muc-rental-bavaria', 'stay-muc-rental-bavaria', 'WHOLE_PROPERTY', 8, 1, 'Entire House'),
      ('stay-unit-mex-hotel-centro', 'stay-mex-hotel-centro', 'ROOM', 2, 6, 'Alameda Double'),
      ('stay-unit-mex-hotel-paseo', 'stay-mex-hotel-paseo', 'ROOM', 4, 3, 'Paseo Suite'),
      ('stay-unit-mex-bnb-coyoacan', 'stay-mex-bnb-coyoacan', 'ROOM', 2, 4, 'Courtyard Room'),
      ('stay-unit-mex-bnb-roma', 'stay-mex-bnb-roma', 'ROOM', 4, 2, 'Artisan Loft'),
      ('stay-unit-mex-rental-condesa', 'stay-mex-rental-condesa', 'WHOLE_PROPERTY', 4, 1, 'Entire Casa'),
      ('stay-unit-mex-rental-pedregal', 'stay-mex-rental-pedregal', 'WHOLE_PROPERTY', 8, 1, 'Entire Villa')
) u(catalog_key, property_key, unit_kind, guest_capacity, inventory_capacity, name)
JOIN accommodation_property property ON property.catalog_key = u.property_key;

INSERT INTO accommodation_nightly_inventory (accommodation_unit_id, night_date, inventory_capacity, available_inventory, base_price_cents, tax_cents, fee_cents)
SELECT unit.id, DATEADD('DAY', nights.x - 1, DATE '2027-03-01'), unit.inventory_capacity, unit.inventory_capacity,
       prices.base_price_cents, prices.tax_cents, prices.fee_cents
FROM (
    VALUES
      ('stay-unit-sfo-hotel-harbor', 17129, 1371, 500), ('stay-unit-sfo-hotel-summit', 29629, 2371, 500),
      ('stay-unit-sfo-bnb-mission', 14074, 1126, 300), ('stay-unit-sfo-bnb-pacific', 22407, 1793, 300),
      ('stay-unit-sfo-rental-sunset', 27500, 2200, 800), ('stay-unit-sfo-rental-presidio', 47407, 3793, 800),
      ('stay-unit-muc-hotel-isar', 14814, 1186, 500), ('stay-unit-muc-hotel-alpine', 25000, 2000, 500),
      ('stay-unit-muc-bnb-glocken', 12222, 978, 300), ('stay-unit-muc-bnb-englischer', 20092, 1608, 300),
      ('stay-unit-muc-rental-lehel', 23796, 1904, 800), ('stay-unit-muc-rental-bavaria', 41851, 3349, 800),
      ('stay-unit-mex-hotel-centro', 9722, 778, 500), ('stay-unit-mex-hotel-paseo', 17592, 1408, 500),
      ('stay-unit-mex-bnb-coyoacan', 8518, 682, 300), ('stay-unit-mex-bnb-roma', 15462, 1238, 300),
      ('stay-unit-mex-rental-condesa', 18703, 1497, 800), ('stay-unit-mex-rental-pedregal', 31666, 2534, 800)
) prices(unit_key, base_price_cents, tax_cents, fee_cents)
JOIN accommodation_unit unit ON unit.catalog_key = prices.unit_key
CROSS JOIN SYSTEM_RANGE(1, 30) nights;

INSERT INTO rental_location (catalog_key, destination_id, airport_id, name)
SELECT 'rental-location-' || destination_code, destination.id, airport.id, location_name
FROM (
    VALUES ('sfo', 'destination-sfo', 'SFO', 'Harborline Mobility at SFO'),
           ('muc', 'destination-muc', 'MUC', 'Alpine Roadworks at MUC'),
           ('mex', 'destination-mex', 'MEX', 'Círculo Drive at MEX')
) locations(destination_code, destination_key, iata_code, location_name)
JOIN catalog_destination destination ON destination.catalog_key = locations.destination_key
JOIN catalog_airport airport ON airport.iata_code = locations.iata_code;

INSERT INTO rental_vehicle_class (catalog_key, supplier_id, supplier_category, rental_location_id, vehicle_category, name,
                                  daily_base_price_cents, daily_tax_cents, daily_fee_cents)
SELECT classes.catalog_key, supplier.id, 'CAR_RENTAL', location.id, classes.vehicle_category, classes.name,
       classes.base_price_cents, classes.tax_cents, classes.fee_cents
FROM (
    VALUES
      ('rental-sfo-economy', 'supplier-rental-sfo', 'rental-location-sfo', 'ECONOMY', 'SFO Economy', 4277, 343, 180),
      ('rental-sfo-standard', 'supplier-rental-sfo', 'rental-location-sfo', 'STANDARD', 'SFO Standard', 6333, 507, 260),
      ('rental-sfo-suv', 'supplier-rental-sfo', 'rental-location-sfo', 'SUV', 'SFO SUV', 9185, 735, 380),
      ('rental-muc-economy', 'supplier-rental-muc', 'rental-location-muc', 'ECONOMY', 'MUC Economy', 4000, 320, 180),
      ('rental-muc-standard', 'supplier-rental-muc', 'rental-location-muc', 'STANDARD', 'MUC Standard', 5777, 463, 260),
      ('rental-muc-suv', 'supplier-rental-muc', 'rental-location-muc', 'SUV', 'MUC SUV', 8537, 683, 380),
      ('rental-mex-economy', 'supplier-rental-mex', 'rental-location-mex', 'ECONOMY', 'MEX Economy', 3259, 261, 180),
      ('rental-mex-standard', 'supplier-rental-mex', 'rental-location-mex', 'STANDARD', 'MEX Standard', 4851, 389, 260),
      ('rental-mex-suv', 'supplier-rental-mex', 'rental-location-mex', 'SUV', 'MEX SUV', 7240, 580, 380)
) classes(catalog_key, supplier_key, location_key, vehicle_category, name, base_price_cents, tax_cents, fee_cents)
JOIN catalog_supplier supplier ON supplier.catalog_key = classes.supplier_key
JOIN rental_location location ON location.catalog_key = classes.location_key;

INSERT INTO rental_unit (catalog_key, rental_vehicle_class_id, unit_identifier)
SELECT fleet.catalog_key, vehicle_class.id, fleet.unit_identifier
FROM (
    VALUES
      ('rental-unit-sfo-economy-01', 'rental-sfo-economy', 'SFO-ECO-01'), ('rental-unit-sfo-economy-02', 'rental-sfo-economy', 'SFO-ECO-02'), ('rental-unit-sfo-economy-03', 'rental-sfo-economy', 'SFO-ECO-03'),
      ('rental-unit-sfo-standard-01', 'rental-sfo-standard', 'SFO-STA-01'), ('rental-unit-sfo-standard-02', 'rental-sfo-standard', 'SFO-STA-02'),
      ('rental-unit-sfo-suv-01', 'rental-sfo-suv', 'SFO-SUV-01'), ('rental-unit-sfo-suv-02', 'rental-sfo-suv', 'SFO-SUV-02'),
      ('rental-unit-muc-economy-01', 'rental-muc-economy', 'MUC-ECO-01'), ('rental-unit-muc-economy-02', 'rental-muc-economy', 'MUC-ECO-02'),
      ('rental-unit-muc-standard-01', 'rental-muc-standard', 'MUC-STA-01'), ('rental-unit-muc-standard-02', 'rental-muc-standard', 'MUC-STA-02'), ('rental-unit-muc-standard-03', 'rental-muc-standard', 'MUC-STA-03'),
      ('rental-unit-muc-suv-01', 'rental-muc-suv', 'MUC-SUV-01'), ('rental-unit-muc-suv-02', 'rental-muc-suv', 'MUC-SUV-02'),
      ('rental-unit-mex-economy-01', 'rental-mex-economy', 'MEX-ECO-01'), ('rental-unit-mex-economy-02', 'rental-mex-economy', 'MEX-ECO-02'),
      ('rental-unit-mex-standard-01', 'rental-mex-standard', 'MEX-STA-01'), ('rental-unit-mex-standard-02', 'rental-mex-standard', 'MEX-STA-02'),
      ('rental-unit-mex-suv-01', 'rental-mex-suv', 'MEX-SUV-01'), ('rental-unit-mex-suv-02', 'rental-mex-suv', 'MEX-SUV-02'), ('rental-unit-mex-suv-03', 'rental-mex-suv', 'MEX-SUV-03')
) fleet(catalog_key, class_key, unit_identifier)
JOIN rental_vehicle_class vehicle_class ON vehicle_class.catalog_key = fleet.class_key;
