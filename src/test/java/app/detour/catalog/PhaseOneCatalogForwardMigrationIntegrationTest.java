package app.detour.catalog;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.sql.DriverManager;
import java.time.OffsetDateTime;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationVersion;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class PhaseOneCatalogForwardMigrationIntegrationTest {
    @TempDir
    Path temporaryDirectory;

    @Test
    void migratesVersionTwoIdentityDatabaseForwardWithoutDataLoss() throws Exception {
        String url = "jdbc:h2:file:" + temporaryDirectory.resolve("phase-one").toAbsolutePath().toString().replace('\\', '/') + ";DB_CLOSE_ON_EXIT=FALSE";
        Flyway.configure().dataSource(url, "sa", "").locations("classpath:db/migration")
                .target(MigrationVersion.fromVersion("2")).load().migrate();
        OffsetDateTime createdAt = OffsetDateTime.parse("2027-01-01T10:15:30-08:00");
        try (var connection = DriverManager.getConnection(url, "sa", "");
             var insert = connection.prepareStatement("INSERT INTO detour_user (canonical_email, password_hash, created_at) VALUES (?, ?, ?)")) {
            insert.setString(1, "phase-one@example.test");
            insert.setString(2, "stored-password-hash");
            insert.setObject(3, createdAt);
            insert.executeUpdate();
        }
        Flyway fullLineage = Flyway.configure().dataSource(url, "sa", "").locations("classpath:db/migration").load();
        fullLineage.migrate();
        assertEquals("13", fullLineage.info().current().getVersion().getVersion());
        try (var connection = DriverManager.getConnection(url, "sa", "");
             var query = connection.prepareStatement("SELECT password_hash, created_at FROM detour_user WHERE canonical_email = ?")) {
            query.setString(1, "phase-one@example.test");
            try (var result = query.executeQuery()) {
                assertTrue(result.next());
                assertEquals("stored-password-hash", result.getString(1));
                assertEquals(createdAt, result.getObject(2, OffsetDateTime.class));
            }
            try (var tables = connection.getMetaData().getTables(null, null, "FLIGHT_INSTANCE", null)) {
                assertTrue(tables.next());
            }
            try (var instances = connection.createStatement().executeQuery("SELECT COUNT(*) FROM flight_instance")) {
                assertTrue(instances.next());
                assertEquals(720, instances.getInt(1));
            }
            try (var stays = connection.createStatement().executeQuery("SELECT COUNT(*) FROM accommodation_nightly_inventory")) {
                assertTrue(stays.next());
                assertEquals(540, stays.getInt(1));
            }
            try (var units = connection.createStatement().executeQuery("SELECT COUNT(*) FROM rental_unit")) {
                assertTrue(units.next());
                assertEquals(21, units.getInt(1));
            }
            try (var trips = connection.createStatement().executeQuery("SELECT COUNT(*) FROM detour_trip")) {
                assertTrue(trips.next());
                assertEquals(0, trips.getInt(1));
            }
        }
    }
}
