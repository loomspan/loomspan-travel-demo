package app.detour.catalog;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.SingleConnectionDataSource;

class AirfareFixtureIntegrationTest {
    @Test
    void cleanMigrationProducesCompleteDeterministicCatalog() {
        JdbcTemplate jdbc = migratedDatabase();
        CatalogFixtureIntegrityAssertions.assertValid(jdbc);
        assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM flight_instance WHERE catalog_key = 'airfare-out-sfo-d1-20270301'", Integer.class));
    }

    @Test
    void fixtureUsesOnePerTravelerFareAndMeaningfulComparisonVariation() {
        JdbcTemplate jdbc = migratedDatabase();
        List<Long> totals = jdbc.query("SELECT base_fare_cents + tax_cents + fee_cents FROM flight_instance ORDER BY catalog_key", (rs, n) -> rs.getLong(1));
        List<List<Integer>> mixedAgeParties = List.of(
                List.of(42),
                List.of(2, 42),
                List.of(2, 14, 42),
                List.of(2, 14, 31, 42),
                List.of(1, 2, 14, 31, 42),
                List.of(1, 2, 14, 19, 31, 42),
                List.of(1, 2, 8, 14, 19, 31, 42),
                List.of(1, 2, 8, 14, 19, 27, 31, 42));
        for (long perTraveler : totals) {
            for (List<Integer> ages : mixedAgeParties) {
                long completePartyTotal = ages.stream().mapToLong(age -> perTraveler).sum();
                assertEquals(perTraveler * ages.size(), completePartyTotal,
                        "each traveler, regardless of age, uses the one persisted airfare");
            }
        }
        CatalogFixtureIntegrityAssertions.assertValid(jdbc);
    }

    @Test
    void independentCleanBuildsProduceIdenticalOrderedAirfareSnapshots() {
        assertEquals(snapshot(migratedDatabase()), snapshot(migratedDatabase()));
    }

    @Test
    void fixtureIntegrityRejectsRepresentativeCorruptions() {
        JdbcTemplate coverage = migratedDatabase();
        coverage.execute("DROP TRIGGER flight_instance_segment_integrity");
        coverage.update("DELETE FROM flight_instance_segment WHERE flight_instance_id = (SELECT id FROM flight_instance WHERE catalog_key = 'airfare-out-sfo-d1-20270301')");
        coverage.update("DELETE FROM flight_instance WHERE catalog_key = 'airfare-out-sfo-d1-20270301'");
        assertThrows(AssertionError.class, () -> CatalogFixtureIntegrityAssertions.assertValid(coverage));

        JdbcTemplate price = migratedDatabase();
        price.update("UPDATE flight_instance SET base_fare_cents = 0, tax_cents = 0, fee_cents = 0 "
                + "WHERE catalog_key = 'airfare-out-sfo-d1-20270301'");
        assertThrows(AssertionError.class, () -> CatalogFixtureIntegrityAssertions.assertValid(price));

        JdbcTemplate capacity = migratedDatabase();
        capacity.update("UPDATE flight_instance SET available_seats = 7 "
                + "WHERE catalog_key = 'airfare-out-sfo-d1-20270301'");
        assertThrows(AssertionError.class, () -> CatalogFixtureIntegrityAssertions.assertValid(capacity));

        JdbcTemplate connection = migratedDatabase();
        connection.update("UPDATE catalog_airport SET iata_code = 'YVR' WHERE iata_code = 'SEA'");
        assertThrows(AssertionError.class, () -> AirfareFixtureIntegrityAssertions.assertConnectionPolicy(connection));

        JdbcTemplate timing = migratedDatabase();
        OffsetDateTime firstArrival = timing.queryForObject("SELECT arrival_at FROM flight_instance_segment WHERE flight_instance_id = "
                + "(SELECT id FROM flight_instance WHERE catalog_key = 'airfare-out-sfo-c1-20270301') AND segment_ordinal = 1", OffsetDateTime.class);
        timing.update("UPDATE flight_instance_segment SET departure_at = ? WHERE flight_instance_id = "
                + "(SELECT id FROM flight_instance WHERE catalog_key = 'airfare-out-sfo-c1-20270301') AND segment_ordinal = 2",
                firstArrival.plusMinutes(30));
        assertThrows(AssertionError.class, () -> CatalogFixtureIntegrityAssertions.assertValid(timing));

        JdbcTemplate finalArrival = migratedDatabase();
        finalArrival.update("UPDATE flight_instance_segment SET arrival_at = ? WHERE flight_instance_id = "
                        + "(SELECT id FROM flight_instance WHERE catalog_key = 'airfare-in-sfo-d1-20270331')",
                OffsetDateTime.parse("2027-04-01T00:01:00-07:00"));
        assertThrows(AssertionError.class, () -> CatalogFixtureIntegrityAssertions.assertValid(finalArrival));

        JdbcTemplate missingNight = migratedDatabase();
        missingNight.update("DELETE FROM accommodation_nightly_inventory WHERE accommodation_unit_id = "
                + "(SELECT id FROM accommodation_unit WHERE catalog_key = 'stay-unit-sfo-hotel-harbor') AND night_date = DATE '2027-03-10'");
        assertThrows(AssertionError.class, () -> CatalogFixtureIntegrityAssertions.assertValid(missingNight));

        JdbcTemplate invalidStay = migratedDatabase();
        invalidStay.update("UPDATE accommodation_property SET guest_rating = 0 WHERE catalog_key = 'stay-sfo-hotel-harbor'");
        assertThrows(AssertionError.class, () -> CatalogFixtureIntegrityAssertions.assertValid(invalidStay));

        JdbcTemplate missingRental = migratedDatabase();
        missingRental.update("DELETE FROM rental_unit WHERE rental_vehicle_class_id = (SELECT id FROM rental_vehicle_class WHERE catalog_key = 'rental-sfo-economy')");
        assertThrows(AssertionError.class, () -> CatalogFixtureIntegrityAssertions.assertValid(missingRental));

        assertThrows(DataAccessException.class, () -> migratedDatabase().update(
                "INSERT INTO flight_instance (catalog_key, flight_schedule_id, service_date, segment_count, base_fare_cents, tax_cents, fee_cents, seat_capacity, available_seats) "
                        + "VALUES ('airfare-out-sfo-d1-20270301', (SELECT id FROM flight_schedule WHERE catalog_key = 'airfare-out-sfo-d1'), DATE '2027-04-01', 1, 1, 0, 0, 8, 8)"));
    }

    private JdbcTemplate migratedDatabase() {
        String url = "jdbc:h2:mem:airfare_" + UUID.randomUUID() + ";DB_CLOSE_DELAY=-1";
        SingleConnectionDataSource dataSource = new SingleConnectionDataSource(url, "sa", "", true);
        Flyway.configure().dataSource(dataSource).locations("classpath:db/migration").load().migrate();
        return new JdbcTemplate(dataSource);
    }

    private List<String> snapshot(JdbcTemplate jdbc) {
        return jdbc.query("""
                SELECT snapshot_line FROM (
                    SELECT 'destination|' || catalog_key || '|' || name || '|' || country_code || '|' || latitude || '|' || longitude AS snapshot_line
                    FROM catalog_destination
                    UNION ALL
                    SELECT 'airport|' || airport.catalog_key || '|' || airport.iata_code || '|' || airport.name || '|' || COALESCE(destination.catalog_key, '') || '|' || airport.latitude || '|' || airport.longitude || '|' || airport.time_zone_id
                    FROM catalog_airport airport LEFT JOIN catalog_destination destination ON destination.id = airport.destination_id
                    UNION ALL
                    SELECT 'supplier|' || catalog_key || '|' || name || '|' || supplier_category FROM catalog_supplier
                    UNION ALL
                    SELECT 'schedule|' || schedule.catalog_key || '|' || supplier.catalog_key || '|' || schedule.supplier_category || '|' || schedule.flight_number || '|' || origin.iata_code || '|' || destination.iata_code || '|' || schedule.stop_count || '|' || schedule.segment_count
                    FROM flight_schedule schedule
                    JOIN catalog_supplier supplier ON supplier.id = schedule.supplier_id
                    JOIN catalog_airport origin ON origin.id = schedule.origin_airport_id
                    JOIN catalog_airport destination ON destination.id = schedule.destination_airport_id
                    UNION ALL
                    SELECT 'schedule-segment|' || schedule.catalog_key || '|' || segment.segment_ordinal || '|' || origin.iata_code || '|' || destination.iata_code || '|' || segment.departure_local_time || '|' || segment.arrival_local_time || '|' || segment.arrival_day_offset || '|' || segment.scheduled_duration_minutes
                    FROM flight_schedule_segment segment
                    JOIN flight_schedule schedule ON schedule.id = segment.flight_schedule_id
                    JOIN catalog_airport origin ON origin.id = segment.origin_airport_id
                    JOIN catalog_airport destination ON destination.id = segment.destination_airport_id
                    UNION ALL
                    SELECT 'instance|' || instance.catalog_key || '|' || schedule.catalog_key || '|' || instance.service_date || '|' || instance.segment_count || '|' || instance.base_fare_cents || '|' || instance.tax_cents || '|' || instance.fee_cents || '|' || instance.seat_capacity || '|' || instance.available_seats
                    FROM flight_instance instance JOIN flight_schedule schedule ON schedule.id = instance.flight_schedule_id
                    UNION ALL
                    SELECT 'instance-segment|' || instance.catalog_key || '|' || segment.segment_ordinal || '|' || segment.departure_at || '|' || segment.arrival_at
                    FROM flight_instance_segment segment JOIN flight_instance instance ON instance.id = segment.flight_instance_id
                    UNION ALL
                    SELECT 'property|' || property.catalog_key || '|' || supplier.catalog_key || '|' || destination.catalog_key || '|' || property.property_category || '|' || property.name || '|' || property.latitude || '|' || property.longitude || '|' || property.location_description || '|' || property.guest_rating || '|' || property.distance_to_city_center_meters
                    FROM accommodation_property property JOIN catalog_supplier supplier ON supplier.id = property.supplier_id JOIN catalog_destination destination ON destination.id = property.destination_id
                    UNION ALL
                    SELECT 'stay-unit|' || unit.catalog_key || '|' || property.catalog_key || '|' || unit.property_category || '|' || unit.unit_kind || '|' || unit.guest_capacity || '|' || unit.inventory_capacity || '|' || unit.name
                    FROM accommodation_unit unit JOIN accommodation_property property ON property.id = unit.accommodation_property_id
                    UNION ALL
                    SELECT 'nightly|' || unit.catalog_key || '|' || nightly.night_date || '|' || nightly.inventory_capacity || '|' || nightly.available_inventory || '|' || nightly.base_price_cents || '|' || nightly.tax_cents || '|' || nightly.fee_cents
                    FROM accommodation_nightly_inventory nightly JOIN accommodation_unit unit ON unit.id = nightly.accommodation_unit_id
                    UNION ALL
                    SELECT 'rental-location|' || location.catalog_key || '|' || destination.catalog_key || '|' || airport.catalog_key || '|' || location.name
                    FROM rental_location location JOIN catalog_destination destination ON destination.id = location.destination_id JOIN catalog_airport airport ON airport.id = location.airport_id
                    UNION ALL
                    SELECT 'rental-class|' || vehicle_class.catalog_key || '|' || supplier.catalog_key || '|' || location.catalog_key || '|' || vehicle_class.vehicle_category || '|' || vehicle_class.name || '|' || vehicle_class.daily_base_price_cents || '|' || vehicle_class.daily_tax_cents || '|' || vehicle_class.daily_fee_cents
                    FROM rental_vehicle_class vehicle_class JOIN catalog_supplier supplier ON supplier.id = vehicle_class.supplier_id JOIN rental_location location ON location.id = vehicle_class.rental_location_id
                    UNION ALL
                    SELECT 'rental-unit|' || unit.catalog_key || '|' || vehicle_class.catalog_key || '|' || unit.unit_identifier
                    FROM rental_unit unit JOIN rental_vehicle_class vehicle_class ON vehicle_class.id = unit.rental_vehicle_class_id
                ) snapshots
                ORDER BY snapshot_line
                """, (rs, n) -> rs.getString(1));
    }
}
