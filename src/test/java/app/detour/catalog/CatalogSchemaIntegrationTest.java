package app.detour.catalog;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.sql.Connection;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;
import javax.sql.DataSource;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

@SpringBootTest
class CatalogSchemaIntegrationTest {
    private static final String DATABASE_URL = "jdbc:h2:mem:catalog_schema_" + UUID.randomUUID() + ";DB_CLOSE_DELAY=-1";

    @DynamicPropertySource
    static void datasourceProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", () -> DATABASE_URL);
    }

    @Autowired
    JdbcTemplate jdbc;

    @Autowired
    DataSource dataSource;

    @BeforeAll
    static void catalogTablesMustExistBeforeFixtures() {
        // The initial red run intentionally reaches the fixture setup before V3/V4 exist.
    }

    @Test
    void rejectsInvalidCatalogValuesAndReferences() {
        CatalogSchemaFixtures.ensureMinimumCatalog(jdbc);
        assertSqlFails("INSERT INTO catalog_supplier (catalog_key, name, supplier_category) VALUES ('bad-supplier', 'Bad', 'BOGUS')");
        assertSqlFails("INSERT INTO flight_instance (catalog_key, flight_schedule_id, service_date, base_fare_cents, tax_cents, fee_cents, seat_capacity, available_seats) "
                + "VALUES ('bad-flight-price', 1, DATE '2027-03-11', -1, 0, 0, 1, 1)");
        assertSqlFails("UPDATE flight_instance SET tax_cents = -1 WHERE catalog_key = 'instance-pdx-sfo-20270310'");
        assertSqlFails("UPDATE flight_instance SET fee_cents = -1 WHERE catalog_key = 'instance-pdx-sfo-20270310'");
        assertSqlFails("UPDATE flight_instance SET base_fare_cents = 9223372036854775807, tax_cents = 1, fee_cents = 0 "
                + "WHERE catalog_key = 'instance-pdx-sfo-20270310'");
        assertSqlFails("INSERT INTO flight_instance (catalog_key, flight_schedule_id, service_date, base_fare_cents, tax_cents, fee_cents, seat_capacity, available_seats) "
                + "VALUES ('bad-flight-capacity', 1, DATE '2027-03-12', 0, 0, 0, 0, 0)");
        assertSqlFails("UPDATE flight_instance SET available_seats = seat_capacity + 1 WHERE catalog_key = 'instance-pdx-sfo-20270310'");
        assertSqlFails("INSERT INTO flight_instance_segment (flight_instance_id, segment_ordinal, departure_at, arrival_at) VALUES (1, 2, "
                + "TIMESTAMP WITH TIME ZONE '2027-03-10 12:00:00+00', TIMESTAMP WITH TIME ZONE '2027-03-10 11:00:00+00')");
        assertSqlFails("INSERT INTO flight_schedule_segment (flight_schedule_id, segment_ordinal, origin_airport_id, destination_airport_id, departure_local_time, arrival_local_time, arrival_day_offset, scheduled_duration_minutes) "
                + "VALUES (1, 2, 1, 2, TIME '12:00:00', TIME '13:00:00', 0, 60)");
        assertSqlFails("UPDATE flight_schedule_segment SET departure_local_time = TIME '12:00:00', arrival_local_time = TIME '11:00:00' "
                + "WHERE flight_schedule_id = 1 AND segment_ordinal = 1");
        assertSqlFails("UPDATE flight_schedule_segment SET origin_airport_id = 2 WHERE flight_schedule_id = 1 AND segment_ordinal = 1");
        assertSqlFails("INSERT INTO flight_instance_segment (flight_instance_id, segment_ordinal, departure_at, arrival_at) VALUES (1, 2, "
                + "TIMESTAMP WITH TIME ZONE '2027-03-10 12:00:00+00', TIMESTAMP WITH TIME ZONE '2027-03-10 13:00:00+00')");
        assertSqlFails("INSERT INTO accommodation_unit (catalog_key, accommodation_property_id, property_category, unit_kind, guest_capacity, inventory_capacity, name) "
                + "VALUES ('bad-unit', 1, 'HOTEL', 'WHOLE_PROPERTY', 2, 1, 'Bad')");
        assertSqlFails("INSERT INTO accommodation_unit (catalog_key, accommodation_property_id, property_category, unit_kind, guest_capacity, inventory_capacity, name) "
                + "VALUES ('zero-guest-unit', 1, 'HOTEL', 'ROOM', 0, 1, 'Bad')");
        assertSqlFails("INSERT INTO accommodation_unit (catalog_key, accommodation_property_id, property_category, unit_kind, guest_capacity, inventory_capacity, name) "
                + "VALUES ('zero-inventory-unit', 1, 'HOTEL', 'ROOM', 1, 0, 'Bad')");
        assertSqlFails("UPDATE accommodation_property SET guest_rating = 5.1 WHERE catalog_key = 'property-sfo'");
        assertSqlFails("UPDATE accommodation_property SET distance_to_city_center_meters = -1 WHERE catalog_key = 'property-sfo'");
        assertSqlFails("INSERT INTO accommodation_nightly_inventory (accommodation_unit_id, night_date, inventory_capacity, available_inventory, base_price_cents, tax_cents, fee_cents) "
                + "VALUES (1, DATE '2027-03-11', 6, 6, 1, 0, 0)");
        assertSqlFails("UPDATE accommodation_nightly_inventory SET available_inventory = -1 WHERE accommodation_unit_id = 1");
        assertSqlFails("UPDATE accommodation_nightly_inventory SET available_inventory = inventory_capacity + 1 WHERE accommodation_unit_id = 1");
        assertSqlFails("UPDATE accommodation_nightly_inventory SET base_price_cents = -1 WHERE accommodation_unit_id = 1");
        assertSqlFails("UPDATE accommodation_nightly_inventory SET tax_cents = -1 WHERE accommodation_unit_id = 1");
        assertSqlFails("UPDATE accommodation_nightly_inventory SET fee_cents = -1 WHERE accommodation_unit_id = 1");
        assertSqlFails("UPDATE accommodation_nightly_inventory SET base_price_cents = 9223372036854775807, tax_cents = 1, fee_cents = 0 "
                + "WHERE accommodation_unit_id = 1");
        assertSqlFails("UPDATE rental_vehicle_class SET daily_base_price_cents = -1 WHERE catalog_key = 'car-standard'");
        assertSqlFails("UPDATE rental_vehicle_class SET daily_tax_cents = -1 WHERE catalog_key = 'car-standard'");
        assertSqlFails("UPDATE rental_vehicle_class SET daily_fee_cents = -1 WHERE catalog_key = 'car-standard'");
        assertSqlFails("UPDATE rental_vehicle_class SET daily_base_price_cents = 9223372036854775807, daily_tax_cents = 1, daily_fee_cents = 0 "
                + "WHERE catalog_key = 'car-standard'");
        assertSqlFails("UPDATE rental_vehicle_class SET vehicle_category = 'LUXURY' WHERE catalog_key = 'car-standard'");
        assertSqlFails("INSERT INTO rental_unit (catalog_key, rental_vehicle_class_id, unit_identifier) VALUES ('orphan-unit', 999999, 'NONE')");
        assertSqlFails("INSERT INTO rental_unit_occupancy (rental_unit_id, pickup_at, return_at, occupancy_status) VALUES (1, "
                + "TIMESTAMP WITH TIME ZONE '2027-03-10 12:00:00+00', TIMESTAMP WITH TIME ZONE '2027-03-10 12:00:00+00', 'ACTIVE')");
    }

    @Test
    void retainsSharedCatalogKeysWithoutUserOwnership() throws Exception {
        CatalogSchemaFixtures.ensureMinimumCatalog(jdbc);
        assertSqlFails("INSERT INTO catalog_destination (catalog_key, name, country_code, latitude, longitude) VALUES "
                + "('destination-sfo', 'Duplicate', 'US', 0, 0)");
        assertSqlFails("UPDATE catalog_destination SET catalog_key = 'destination-renamed' WHERE catalog_key = 'destination-sfo'");
        assertSqlFails("UPDATE flight_instance SET catalog_key = 'instance-renamed' WHERE catalog_key = 'instance-pdx-sfo-20270310'");
        assertSqlFails("UPDATE accommodation_unit SET catalog_key = 'unit-renamed' WHERE catalog_key = 'unit-sfo-room'");
        assertSqlFails("UPDATE rental_unit SET catalog_key = 'car-unit-renamed' WHERE catalog_key = 'car-unit-1'");
        try (Connection connection = dataSource.getConnection()) {
            for (String catalogTable : java.util.List.of("CATALOG_DESTINATION", "CATALOG_AIRPORT", "CATALOG_SUPPLIER",
                    "FLIGHT_SCHEDULE", "FLIGHT_INSTANCE", "ACCOMMODATION_PROPERTY", "ACCOMMODATION_UNIT",
                    "RENTAL_LOCATION", "RENTAL_VEHICLE_CLASS", "RENTAL_UNIT", "RENTAL_UNIT_OCCUPANCY")) {
                try (var foreignKeys = connection.getMetaData().getImportedKeys(null, null, catalogTable)) {
                    while (foreignKeys.next()) {
                        assertFalse("DETOUR_USER".equalsIgnoreCase(foreignKeys.getString("PKTABLE_NAME")),
                                "Catalog and inventory must remain application-owned shared data");
                    }
                }
            }
        }
        assertTrue(jdbc.queryForObject("SELECT COUNT(*) FROM flight_instance WHERE catalog_key = 'instance-pdx-sfo-20270310'", Integer.class) == 1);
        assertTrue(jdbc.queryForObject("SELECT COUNT(*) FROM accommodation_unit WHERE catalog_key = 'unit-sfo-room'", Integer.class) == 1);
        assertTrue(jdbc.queryForObject("SELECT COUNT(*) FROM rental_unit WHERE catalog_key = 'car-unit-1'", Integer.class) == 1);
    }

    @Test
    void acceptsOneStopFlightsAndWholePropertyUnits() {
        CatalogSchemaFixtures.ensureMinimumCatalog(jdbc);
        jdbc.update("INSERT INTO catalog_destination (catalog_key, name, country_code, latitude, longitude) VALUES "
                + "('destination-muc', 'Munich', 'DE', 48.1351, 11.5820)");
        jdbc.update("INSERT INTO catalog_airport (catalog_key, iata_code, name, destination_id, latitude, longitude, time_zone_id) VALUES "
                + "('airport-muc', 'MUC', 'Munich Airport', (SELECT id FROM catalog_destination WHERE catalog_key = 'destination-muc'), 48.3538, 11.7861, 'Europe/Berlin')");
        jdbc.update("INSERT INTO flight_schedule (catalog_key, supplier_id, supplier_category, flight_number, origin_airport_id, destination_airport_id, stop_count, segment_count) VALUES "
                + "('schedule-pdx-muc-sfo', 1, 'AIRLINE', 'DT202', 1, 2, 1, 2)");
        jdbc.update("INSERT INTO flight_schedule_segment (flight_schedule_id, segment_ordinal, origin_airport_id, destination_airport_id, departure_local_time, arrival_local_time, arrival_day_offset, scheduled_duration_minutes) VALUES "
                + "((SELECT id FROM flight_schedule WHERE catalog_key = 'schedule-pdx-muc-sfo'), 1, 1, (SELECT id FROM catalog_airport WHERE catalog_key = 'airport-muc'), TIME '09:00:00', TIME '08:00:00', 1, 600), "
                + "((SELECT id FROM flight_schedule WHERE catalog_key = 'schedule-pdx-muc-sfo'), 2, (SELECT id FROM catalog_airport WHERE catalog_key = 'airport-muc'), 2, TIME '10:00:00', TIME '12:00:00', 0, 120)");
        jdbc.update("INSERT INTO flight_instance (catalog_key, flight_schedule_id, service_date, segment_count, base_fare_cents, tax_cents, fee_cents, seat_capacity, available_seats) VALUES "
                + "('instance-pdx-muc-sfo-20270311', (SELECT id FROM flight_schedule WHERE catalog_key = 'schedule-pdx-muc-sfo'), DATE '2027-03-11', 2, 50000, 3000, 500, 60, 60)");
        jdbc.update("INSERT INTO flight_instance_segment (flight_instance_id, segment_ordinal, departure_at, arrival_at) VALUES "
                + "((SELECT id FROM flight_instance WHERE catalog_key = 'instance-pdx-muc-sfo-20270311'), 1, TIMESTAMP WITH TIME ZONE '2027-03-11 09:00:00-08', TIMESTAMP WITH TIME ZONE '2027-03-12 08:00:00+01'), "
                + "((SELECT id FROM flight_instance WHERE catalog_key = 'instance-pdx-muc-sfo-20270311'), 2, TIMESTAMP WITH TIME ZONE '2027-03-12 10:00:00+01', TIMESTAMP WITH TIME ZONE '2027-03-12 12:00:00-08')");
        assertSqlFails("UPDATE flight_schedule_segment SET destination_airport_id = 2 WHERE flight_schedule_id = "
                + "(SELECT id FROM flight_schedule WHERE catalog_key = 'schedule-pdx-muc-sfo') AND segment_ordinal = 1");
        assertSqlFails("UPDATE flight_schedule SET destination_airport_id = (SELECT id FROM catalog_airport WHERE catalog_key = 'airport-muc') "
                + "WHERE catalog_key = 'schedule-pdx-muc-sfo'");
        assertSqlFails("UPDATE flight_schedule SET destination_airport_id = (SELECT id FROM catalog_airport WHERE catalog_key = 'airport-muc'), "
                + "stop_count = 0, segment_count = 1 WHERE catalog_key = 'schedule-pdx-muc-sfo'");
        assertSqlFails("DELETE FROM flight_schedule_segment WHERE flight_schedule_id = "
                + "(SELECT id FROM flight_schedule WHERE catalog_key = 'schedule-pdx-muc-sfo') AND segment_ordinal = 2");
        assertSqlFails("DELETE FROM flight_instance_segment WHERE flight_instance_id = "
                + "(SELECT id FROM flight_instance WHERE catalog_key = 'instance-pdx-muc-sfo-20270311') AND segment_ordinal = 2");
        jdbc.update("INSERT INTO accommodation_property (catalog_key, supplier_id, supplier_category, destination_id, property_category, name, latitude, longitude, location_description, guest_rating, distance_to_city_center_meters) VALUES "
                + "('property-sfo-rental', 2, 'LODGING', 1, 'VACATION_RENTAL', 'DeTour House', 37.7800, -122.4200, 'South of downtown', 4.7, 1200)");
        jdbc.update("INSERT INTO accommodation_unit (catalog_key, accommodation_property_id, property_category, unit_kind, guest_capacity, inventory_capacity, name) VALUES "
                + "('unit-sfo-house', (SELECT id FROM accommodation_property WHERE catalog_key = 'property-sfo-rental'), 'VACATION_RENTAL', 'WHOLE_PROPERTY', 4, 1, 'Entire House')");
        jdbc.update("INSERT INTO accommodation_property (catalog_key, supplier_id, supplier_category, destination_id, property_category, name, latitude, longitude, location_description, guest_rating, distance_to_city_center_meters) VALUES "
                + "('property-sfo-bnb', 2, 'LODGING', 1, 'BED_AND_BREAKFAST', 'DeTour B&B', 37.7700, -122.4100, 'Mission District', 4.3, 2400)");
        jdbc.update("INSERT INTO accommodation_unit (catalog_key, accommodation_property_id, property_category, unit_kind, guest_capacity, inventory_capacity, name) VALUES "
                + "('unit-sfo-bnb-room', (SELECT id FROM accommodation_property WHERE catalog_key = 'property-sfo-bnb'), 'BED_AND_BREAKFAST', 'ROOM', 2, 2, 'Garden Room')");
        assertEquals(2, jdbc.queryForObject("SELECT COUNT(*) FROM flight_instance_segment WHERE flight_instance_id = "
                + "(SELECT id FROM flight_instance WHERE catalog_key = 'instance-pdx-muc-sfo-20270311')", Integer.class));
    }

    private void assertSqlFails(String sql) {
        assertThrows(DataAccessException.class, () -> jdbc.execute(sql));
    }
}
