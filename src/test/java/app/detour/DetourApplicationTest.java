package app.detour;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import java.sql.Connection;
import java.util.Arrays;
import java.util.UUID;
import javax.sql.DataSource;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

@SpringBootTest
class DetourApplicationTest {

    private static final String DATABASE_URL = "jdbc:h2:mem:detour_test_" + UUID.randomUUID()
            + ";DB_CLOSE_DELAY=-1";

    @DynamicPropertySource
    static void datasourceProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", () -> DATABASE_URL);
    }

    @Autowired
    private Flyway flyway;

    @Autowired
    private DataSource dataSource;

    @Test
    void startsWithFreshDetourIdentityMigrationAndNoSeededPrivilege() throws Exception {
        assertEquals(
                java.util.List.of("1", "2"),
                Arrays.stream(flyway.info().applied())
                        .map(info -> info.getVersion().getVersion())
                        .toList());

        try (Connection connection = dataSource.getConnection()) {
            try (var tables = connection.getMetaData().getTables(null, null, "DETOUR_USER", null)) {
                org.junit.jupiter.api.Assertions.assertTrue(tables.next(), "Identity table must exist");
            }
            try (var count = connection.createStatement().executeQuery("SELECT COUNT(*) FROM detour_user")) {
                org.junit.jupiter.api.Assertions.assertTrue(count.next());
                assertEquals(0, count.getInt(1), "Fresh identity schema must not seed an account");
            }
            for (String legacyTable : java.util.List.of(
                    "travel_service", "hotel_night", "trip", "assessment", "booking", "intake_draft")) {
                try (var tables = connection.getMetaData().getTables(null, null, legacyTable.toUpperCase(), null)) {
                    assertFalse(tables.next(), () -> "Legacy table must not exist: " + legacyTable);
                }
            }
        }
    }
}
