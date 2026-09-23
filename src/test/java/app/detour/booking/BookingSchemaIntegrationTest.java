package app.detour.booking;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

@SpringBootTest
class BookingSchemaIntegrationTest {
    private static final String DATABASE_URL = "jdbc:h2:mem:booking_schema_" + UUID.randomUUID() + ";DB_CLOSE_DELAY=-1";

    @DynamicPropertySource
    static void datasourceProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", () -> DATABASE_URL);
    }

    @Autowired private JdbcTemplate jdbc;

    private long tripId;
    private long plannedId;

    @BeforeEach
    void setUp() {
        String email = "schema-test-" + UUID.randomUUID() + "@example.test";
        jdbc.update("INSERT INTO detour_user (canonical_email, password_hash, created_at) VALUES (?, 'hash', CURRENT_TIMESTAMP)", email);
        long userId = jdbc.queryForObject("SELECT id FROM detour_user WHERE canonical_email = ?", Long.class, email);

        UUID tripPublicId = UUID.randomUUID();
        jdbc.update(
                "INSERT INTO detour_trip (public_id, owner_user_id, catalog_destination_id, start_date, end_date, traveler_count, budget_cents, display_label, version) " +
                "VALUES (?, ?, (SELECT id FROM catalog_destination LIMIT 1), DATE '2027-03-10', DATE '2027-03-14', 2, 500000, 'Test Trip', 0)",
                tripPublicId, userId);
        tripId = jdbc.queryForObject("SELECT id FROM detour_trip WHERE public_id = ?", Long.class, tripPublicId);

        UUID plannedPublicId = UUID.randomUUID();
        jdbc.update("INSERT INTO detour_planned_itinerary (public_id, trip_id) VALUES (?, ?)", plannedPublicId, tripId);
        plannedId = jdbc.queryForObject("SELECT id FROM detour_planned_itinerary WHERE public_id = ?", Long.class, plannedPublicId);
    }

    @Test
    void allowsSingleActiveBookingPerTripAndMultipleCanceledBookings() {
        // First active booking succeeds
        insertBooking(tripId, plannedId, "DT-ACT001", "ACTIVE", 10000L, "idemp-1");

        // Second active booking on same trip fails with DataIntegrityViolationException
        assertThrows(DataIntegrityViolationException.class, () ->
                insertBooking(tripId, plannedId, "DT-ACT002", "ACTIVE", 20000L, "idemp-2"));

        // Update first booking to CANCELED
        jdbc.update("UPDATE detour_booking SET status = 'CANCELED', canceled_at = CURRENT_TIMESTAMP WHERE booking_reference = 'DT-ACT001'");

        // Now a new active booking succeeds
        assertDoesNotThrow(() ->
                insertBooking(tripId, plannedId, "DT-ACT003", "ACTIVE", 30000L, "idemp-3"));

        // A second canceled booking can also exist
        assertDoesNotThrow(() -> {
            insertBooking(tripId, plannedId, "DT-CAN001", "CANCELED", 15000L, "idemp-4");
            insertBooking(tripId, plannedId, "DT-CAN002", "CANCELED", 16000L, "idemp-5");
        });
    }

    @Test
    void rejectsDuplicateIdempotencyKeyForSameTrip() {
        insertBooking(tripId, plannedId, "DT-IDP001", "ACTIVE", 10000L, "duplicate-key");

        assertThrows(DataIntegrityViolationException.class, () ->
                insertBooking(tripId, plannedId, "DT-IDP002", "CANCELED", 20000L, "duplicate-key"));
    }

    @Test
    void allowsSameIdempotencyKeyForDifferentTrips() {
        String email = "schema-test-diff-" + UUID.randomUUID() + "@example.test";
        jdbc.update("INSERT INTO detour_user (canonical_email, password_hash, created_at) VALUES (?, 'hash', CURRENT_TIMESTAMP)", email);
        long userId = jdbc.queryForObject("SELECT id FROM detour_user WHERE canonical_email = ?", Long.class, email);

        UUID otherTripPublicId = UUID.randomUUID();
        jdbc.update(
                "INSERT INTO detour_trip (public_id, owner_user_id, catalog_destination_id, start_date, end_date, traveler_count, budget_cents, display_label, version) " +
                "VALUES (?, ?, (SELECT id FROM catalog_destination LIMIT 1), DATE '2027-03-10', DATE '2027-03-14', 2, 500000, 'Other Trip', 0)",
                otherTripPublicId, userId);
        long otherTripId = jdbc.queryForObject("SELECT id FROM detour_trip WHERE public_id = ?", Long.class, otherTripPublicId);

        UUID otherPlannedPublicId = UUID.randomUUID();
        jdbc.update("INSERT INTO detour_planned_itinerary (public_id, trip_id) VALUES (?, ?)", otherPlannedPublicId, otherTripId);
        long otherPlannedId = jdbc.queryForObject("SELECT id FROM detour_planned_itinerary WHERE public_id = ?", Long.class, otherPlannedPublicId);

        insertBooking(tripId, plannedId, "DT-KEY001", "ACTIVE", 10000L, "shared-idempotency-key");
        assertDoesNotThrow(() ->
                insertBooking(otherTripId, otherPlannedId, "DT-KEY002", "ACTIVE", 10000L, "shared-idempotency-key"));
    }

    @Test
    void enforcesCheckConstraints() {
        // Invalid status
        assertThrows(DataIntegrityViolationException.class, () ->
                insertBooking(tripId, plannedId, "DT-BAD001", "PENDING", 10000L, "bad-status"));

        // Negative grand total
        assertThrows(DataIntegrityViolationException.class, () ->
                insertBooking(tripId, plannedId, "DT-BAD002", "ACTIVE", -100L, "bad-total"));
    }

    @Test
    void cascadesOnTripDeletion() {
        long bookingId = insertBooking(tripId, plannedId, "DT-CAS001", "ACTIVE", 10000L, "cascade-key");

        // Insert stay snapshot and night snapshot
        long unitId = jdbc.queryForObject("SELECT id FROM accommodation_unit LIMIT 1", Long.class);
        jdbc.update("INSERT INTO detour_booking_stay_snapshot (booking_id, accommodation_unit_id, unit_count, property_name, unit_name) VALUES (?, ?, 1, 'Hotel', 'Room')",
                bookingId, unitId);
        jdbc.update("INSERT INTO detour_booking_stay_night_snapshot (booking_id, night_date, base_price_cents, tax_cents, fee_cents) VALUES (?, DATE '2027-03-10', 100, 10, 5)",
                bookingId);

        // Delete trip
        jdbc.update("DELETE FROM detour_trip WHERE id = ?", tripId);

        int bookingCount = jdbc.queryForObject("SELECT COUNT(*) FROM detour_booking WHERE id = ?", Integer.class, bookingId);
        int stayCount = jdbc.queryForObject("SELECT COUNT(*) FROM detour_booking_stay_snapshot WHERE booking_id = ?", Integer.class, bookingId);
        int nightCount = jdbc.queryForObject("SELECT COUNT(*) FROM detour_booking_stay_night_snapshot WHERE booking_id = ?", Integer.class, bookingId);

        assertEquals(0, bookingCount);
        assertEquals(0, stayCount);
        assertEquals(0, nightCount);
    }

    private long insertBooking(long tripId, long plannedItineraryId, String reference, String status, long totalCents, String idempotencyKey) {
        UUID publicId = UUID.randomUUID();
        jdbc.update(
                "INSERT INTO detour_booking (public_id, trip_id, planned_itinerary_id, booking_reference, status, grand_total_cents, idempotency_key, created_at) " +
                "VALUES (?, ?, ?, ?, ?, ?, ?, CURRENT_TIMESTAMP)",
                publicId, tripId, plannedItineraryId, reference, status, totalCents, idempotencyKey);
        return jdbc.queryForObject("SELECT id FROM detour_booking WHERE public_id = ?", Long.class, publicId);
    }
}
