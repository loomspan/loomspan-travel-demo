package app.detour.catalog;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.time.OffsetDateTime;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

@SpringBootTest
class RentalUnitOccupancyConstraintIntegrationTest {
    private static final String DATABASE_URL = "jdbc:h2:mem:rental_occupancy_" + UUID.randomUUID() + ";DB_CLOSE_DELAY=-1";

    @DynamicPropertySource
    static void datasourceProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", () -> DATABASE_URL);
    }

    @Autowired
    JdbcTemplate jdbc;

    @BeforeEach
    void setup() {
        CatalogSchemaFixtures.ensureMinimumCatalog(jdbc);
    }

    @Test
    void rejectsOverlappingActiveOccupancyButAllowsHalfOpenBoundary() {
        insert(1, "2027-03-10T10:00:00-08:00", "2027-03-10T12:00:00-08:00", "ACTIVE");
        assertDoesNotThrow(() -> insert(1, "2027-03-10T12:00:00-08:00", "2027-03-10T14:00:00-08:00", "ACTIVE"));
        assertDoesNotThrow(() -> insert(2, "2027-03-10T10:00:00-08:00", "2027-03-10T12:00:00-08:00", "ACTIVE"));
        assertDoesNotThrow(() -> insert(1, "2027-03-10T10:30:00-08:00", "2027-03-10T11:30:00-08:00", "RELEASED"));
        assertThrows(DataAccessException.class, () -> insert(1, "2027-03-10T11:59:59-08:00", "2027-03-10T13:00:00-08:00", "ACTIVE"));
        assertThrows(DataAccessException.class, () -> jdbc.update("UPDATE rental_unit_occupancy SET pickup_at = ?, return_at = ? WHERE id = 2",
                OffsetDateTime.parse("2027-03-10T11:00:00-08:00"), OffsetDateTime.parse("2027-03-10T13:00:00-08:00")));
    }

    private void insert(long unitId, String pickup, String returned, String status) {
        jdbc.update("INSERT INTO rental_unit_occupancy (rental_unit_id, pickup_at, return_at, occupancy_status) VALUES (?, ?, ?, ?)",
                unitId, OffsetDateTime.parse(pickup), OffsetDateTime.parse(returned), status);
    }
}
