package app.detour.catalog;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.time.OffsetDateTime;
import java.util.UUID;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationVersion;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.SingleConnectionDataSource;

class StayAndRentalFixtureIntegrationTest {
    @Test
    void cleanMigrationProducesCompleteStayAndRentalCatalog() {
        JdbcTemplate jdbc = migratedDatabase();
        CatalogFixtureIntegrityAssertions.assertValid(jdbc);
        assertEquals(2, jdbc.queryForObject("""
                SELECT COUNT(*) FROM accommodation_property property JOIN catalog_destination destination ON destination.id = property.destination_id
                WHERE destination.catalog_key = 'destination-sfo' AND property.property_category = 'HOTEL'
                """, Integer.class));
    }

    @Test
    void preV11LineageFailsTheCompleteFixtureFacadeForTheExpectedZeroInventoryReason() {
        String url = "jdbc:h2:mem:stay_rental_pre_v11_" + UUID.randomUUID() + ";DB_CLOSE_DELAY=-1";
        SingleConnectionDataSource dataSource = new SingleConnectionDataSource(url, "sa", "", true);
        Flyway.configure().dataSource(dataSource).locations("classpath:db/migration")
                .target(MigrationVersion.fromVersion("10")).load().migrate();
        JdbcTemplate jdbc = new JdbcTemplate(dataSource);
        assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM accommodation_property", Integer.class));
        assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM rental_unit", Integer.class));
        assertThrows(AssertionError.class, () -> CatalogFixtureIntegrityAssertions.assertValid(jdbc));
    }

    @Test
    void rentalFixturesSupportCycleBillingAndHalfOpenAvailability() {
        JdbcTemplate jdbc = migratedDatabase();
        long dailyTotal = CatalogFixtureIntegrityAssertions.dailyRentalTotal(jdbc, "rental-sfo-standard");
        OffsetDateTime pickup = OffsetDateTime.parse("2027-03-10T10:00:00-08:00");
        assertEquals(2 * dailyTotal, CatalogFixtureIntegrityAssertions.rentalTotalForInterval(
                jdbc, "rental-sfo-standard", pickup, OffsetDateTime.parse("2027-03-11T11:00:00-08:00")));
        assertEquals(CatalogFixtureIntegrityAssertions.dailyRentalTotal(jdbc, "rental-muc-economy"), CatalogFixtureIntegrityAssertions.rentalTotalForInterval(
                jdbc, "rental-muc-economy", OffsetDateTime.parse("2027-03-10T23:30:00+01:00"), OffsetDateTime.parse("2027-03-11T00:30:00+01:00")));

        long unitId = jdbc.queryForObject("SELECT id FROM rental_unit WHERE catalog_key = 'rental-unit-sfo-standard-01'", Long.class);
        insert(jdbc, unitId, "2027-03-10T10:00:00-08:00", "2027-03-10T12:00:00-08:00");
        assertDoesNotThrow(() -> insert(jdbc, unitId, "2027-03-10T12:00:00-08:00", "2027-03-10T14:00:00-08:00"));
        assertThrows(DataAccessException.class, () -> insert(jdbc, unitId, "2027-03-10T11:59:00-08:00", "2027-03-10T13:00:00-08:00"));
    }

    private void insert(JdbcTemplate jdbc, long unitId, String pickup, String returned) {
        jdbc.update("INSERT INTO rental_unit_occupancy (rental_unit_id, pickup_at, return_at, occupancy_status) VALUES (?, ?, ?, 'ACTIVE')",
                unitId, OffsetDateTime.parse(pickup), OffsetDateTime.parse(returned));
    }

    private JdbcTemplate migratedDatabase() {
        String url = "jdbc:h2:mem:stay_rental_" + UUID.randomUUID() + ";DB_CLOSE_DELAY=-1";
        SingleConnectionDataSource dataSource = new SingleConnectionDataSource(url, "sa", "", true);
        Flyway.configure().dataSource(dataSource).locations("classpath:db/migration").load().migrate();
        return new JdbcTemplate(dataSource);
    }
}
