package app.detour.catalog;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import org.springframework.jdbc.core.JdbcTemplate;

final class CatalogSchemaFixtures {
    private CatalogSchemaFixtures() {
    }

    static void insertMinimumCatalog(JdbcTemplate jdbc) {
        jdbc.update("INSERT INTO catalog_destination (catalog_key, name, country_code, latitude, longitude) VALUES "
                + "('destination-sfo', 'San Francisco', 'US', 37.7749, -122.4194)");
        jdbc.update("INSERT INTO catalog_airport (catalog_key, iata_code, name, destination_id, latitude, longitude, time_zone_id) "
                + "VALUES ('airport-pdx', 'PDX', 'Portland International', NULL, 45.5898, -122.5951, 'America/Los_Angeles')");
        jdbc.update("INSERT INTO catalog_airport (catalog_key, iata_code, name, destination_id, latitude, longitude, time_zone_id) "
                + "VALUES ('airport-sfo', 'SFO', 'San Francisco International', "
                + "(SELECT id FROM catalog_destination WHERE catalog_key = 'destination-sfo'), 37.6213, -122.3790, 'America/Los_Angeles')");
        jdbc.update("INSERT INTO catalog_supplier (catalog_key, name, supplier_category) VALUES "
                + "('supplier-air', 'DeTour Air', 'AIRLINE'), ('supplier-lodging', 'DeTour Stays', 'LODGING'), ('supplier-cars', 'DeTour Cars', 'CAR_RENTAL')");

        jdbc.update("INSERT INTO flight_schedule (catalog_key, supplier_id, supplier_category, flight_number, origin_airport_id, destination_airport_id, stop_count, segment_count) "
                + "VALUES ('schedule-pdx-sfo', (SELECT id FROM catalog_supplier WHERE catalog_key = 'supplier-air'), 'AIRLINE', 'DT101', "
                + "(SELECT id FROM catalog_airport WHERE catalog_key = 'airport-pdx'), (SELECT id FROM catalog_airport WHERE catalog_key = 'airport-sfo'), 0, 1)");
        jdbc.update("INSERT INTO flight_schedule_segment (flight_schedule_id, segment_ordinal, origin_airport_id, destination_airport_id, "
                + "departure_local_time, arrival_local_time, arrival_day_offset, scheduled_duration_minutes) VALUES "
                + "((SELECT id FROM flight_schedule WHERE catalog_key = 'schedule-pdx-sfo'), 1, "
                + "(SELECT id FROM catalog_airport WHERE catalog_key = 'airport-pdx'), (SELECT id FROM catalog_airport WHERE catalog_key = 'airport-sfo'), "
                + "?, ?, 0, 120)", LocalTime.of(9, 0), LocalTime.of(11, 0));
        jdbc.update("INSERT INTO flight_instance (catalog_key, flight_schedule_id, service_date, base_fare_cents, tax_cents, fee_cents, seat_capacity, available_seats) "
                + "VALUES ('instance-pdx-sfo-20270310', (SELECT id FROM flight_schedule WHERE catalog_key = 'schedule-pdx-sfo'), ?, 12500, 1100, 400, 100, 100)", LocalDate.of(2027, 3, 10));
        jdbc.update("INSERT INTO flight_instance_segment (flight_instance_id, segment_ordinal, departure_at, arrival_at) VALUES "
                + "((SELECT id FROM flight_instance WHERE catalog_key = 'instance-pdx-sfo-20270310'), 1, ?, ?)",
                OffsetDateTime.parse("2027-03-10T09:00:00-08:00"), OffsetDateTime.parse("2027-03-10T11:00:00-08:00"));

        jdbc.update("INSERT INTO accommodation_property (catalog_key, supplier_id, supplier_category, destination_id, property_category, name, latitude, longitude, location_description, guest_rating, distance_to_city_center_meters) VALUES "
                + "('property-sfo', (SELECT id FROM catalog_supplier WHERE catalog_key = 'supplier-lodging'), 'LODGING', "
                + "(SELECT id FROM catalog_destination WHERE catalog_key = 'destination-sfo'), 'HOTEL', 'DeTour Hotel', 37.7749, -122.4194, 'Downtown San Francisco', 4.5, 500)");
        jdbc.update("INSERT INTO accommodation_unit (catalog_key, accommodation_property_id, property_category, unit_kind, guest_capacity, inventory_capacity, name) VALUES "
                + "('unit-sfo-room', (SELECT id FROM accommodation_property WHERE catalog_key = 'property-sfo'), 'HOTEL', 'ROOM', 2, 5, 'King Room')");
        jdbc.update("INSERT INTO accommodation_nightly_inventory (accommodation_unit_id, night_date, inventory_capacity, available_inventory, base_price_cents, tax_cents, fee_cents) VALUES "
                + "((SELECT id FROM accommodation_unit WHERE catalog_key = 'unit-sfo-room'), ?, 5, 5, 18000, 1600, 200)", LocalDate.of(2027, 3, 10));

        jdbc.update("INSERT INTO rental_location (catalog_key, destination_id, airport_id, name) VALUES "
                + "('rental-sfo-airport', (SELECT id FROM catalog_destination WHERE catalog_key = 'destination-sfo'), "
                + "(SELECT id FROM catalog_airport WHERE catalog_key = 'airport-sfo'), 'SFO terminal')");
        jdbc.update("INSERT INTO rental_vehicle_class (catalog_key, supplier_id, supplier_category, rental_location_id, vehicle_category, name, daily_base_price_cents, daily_tax_cents, daily_fee_cents) VALUES "
                + "('car-standard', (SELECT id FROM catalog_supplier WHERE catalog_key = 'supplier-cars'), 'CAR_RENTAL', "
                + "(SELECT id FROM rental_location WHERE catalog_key = 'rental-sfo-airport'), 'STANDARD', 'Standard Sedan', 5500, 500, 100)");
        jdbc.update("INSERT INTO rental_unit (catalog_key, rental_vehicle_class_id, unit_identifier) VALUES "
                + "('car-unit-1', (SELECT id FROM rental_vehicle_class WHERE catalog_key = 'car-standard'), 'STD-001'), "
                + "('car-unit-2', (SELECT id FROM rental_vehicle_class WHERE catalog_key = 'car-standard'), 'STD-002')");
    }

    static void ensureMinimumCatalog(JdbcTemplate jdbc) {
        if (jdbc.queryForObject("SELECT COUNT(*) FROM catalog_destination", Integer.class) == 0) {
            insertMinimumCatalog(jdbc);
            return;
        }
        if (jdbc.queryForObject("SELECT COUNT(*) FROM rental_unit", Integer.class) == 0) {
            jdbc.update("INSERT INTO catalog_supplier (catalog_key, name, supplier_category) VALUES ('test-lodging', 'Test Lodging', 'LODGING')");
            jdbc.update("INSERT INTO accommodation_property (catalog_key, supplier_id, supplier_category, destination_id, property_category, name, latitude, longitude, location_description, guest_rating, distance_to_city_center_meters) VALUES ('test-property-sfo', (SELECT id FROM catalog_supplier WHERE catalog_key = 'test-lodging'), 'LODGING', (SELECT id FROM catalog_destination WHERE catalog_key = 'destination-sfo'), 'HOTEL', 'Test hotel', 37.7, -122.4, 'Test', 4.0, 100)");
            jdbc.update("INSERT INTO accommodation_unit (catalog_key, accommodation_property_id, property_category, unit_kind, guest_capacity, inventory_capacity, name) VALUES ('test-unit-sfo-room', (SELECT id FROM accommodation_property WHERE catalog_key = 'test-property-sfo'), 'HOTEL', 'ROOM', 2, 5, 'Test room')");
            jdbc.update("INSERT INTO accommodation_nightly_inventory (accommodation_unit_id, night_date, inventory_capacity, available_inventory, base_price_cents, tax_cents, fee_cents) VALUES ((SELECT id FROM accommodation_unit WHERE catalog_key = 'test-unit-sfo-room'), DATE '2027-03-10', 5, 5, 1, 0, 0)");
            jdbc.update("INSERT INTO catalog_supplier (catalog_key, name, supplier_category) VALUES ('test-cars', 'Test Cars', 'CAR_RENTAL')");
            jdbc.update("INSERT INTO rental_location (catalog_key, destination_id, airport_id, name) VALUES ('test-rental-sfo', (SELECT id FROM catalog_destination WHERE catalog_key = 'destination-sfo'), (SELECT id FROM catalog_airport WHERE iata_code = 'SFO'), 'Test terminal')");
            jdbc.update("INSERT INTO rental_vehicle_class (catalog_key, supplier_id, supplier_category, rental_location_id, vehicle_category, name, daily_base_price_cents, daily_tax_cents, daily_fee_cents) VALUES ('test-car-standard', (SELECT id FROM catalog_supplier WHERE catalog_key = 'test-cars'), 'CAR_RENTAL', (SELECT id FROM rental_location WHERE catalog_key = 'test-rental-sfo'), 'STANDARD', 'Test car', 1, 0, 0)");
            jdbc.update("INSERT INTO rental_unit (catalog_key, rental_vehicle_class_id, unit_identifier) VALUES ('test-car-unit-1', (SELECT id FROM rental_vehicle_class WHERE catalog_key = 'test-car-standard'), 'ONE'), ('test-car-unit-2', (SELECT id FROM rental_vehicle_class WHERE catalog_key = 'test-car-standard'), 'TWO')");
        }
    }
}
