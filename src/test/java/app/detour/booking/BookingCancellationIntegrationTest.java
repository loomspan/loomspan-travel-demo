package app.detour.booking;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import app.detour.common.ClockConfiguration;
import app.detour.trip.TestClockConfiguration;
import jakarta.servlet.http.Cookie;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

@SpringBootTest
@AutoConfigureMockMvc
@Import(TestClockConfiguration.class)
class BookingCancellationIntegrationTest {
    private static final String DATABASE_URL = "jdbc:h2:mem:booking_cancellation_" + UUID.randomUUID() + ";DB_CLOSE_DELAY=-1";

    @DynamicPropertySource
    static void datasourceProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", () -> DATABASE_URL);
    }

    @Autowired private MockMvc mockMvc;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private TestClockConfiguration.TestClock testClock;

    @BeforeEach
    @AfterEach
    void resetDatabaseAndClock() {
        testClock.reset();
        jdbc.update("DELETE FROM detour_booking");
        jdbc.update("DELETE FROM rental_unit_occupancy");
        jdbc.update("UPDATE flight_instance SET available_seats = seat_capacity");
        jdbc.update("UPDATE accommodation_nightly_inventory SET available_inventory = inventory_capacity");
    }

    @Test
    void cancelingActiveBookingRestoresInventoryAndAdvancesTripVersion() throws Exception {
        Client owner = register("cancel-booking-test@example.test");
        TripFixture fixture = createTripWithBooking(owner);

        int outboundSeatsBefore = getAvailableSeats("airfare-out-sfo-d1", "2027-03-10");
        int returnSeatsBefore = getAvailableSeats("airfare-in-sfo-d1", "2027-03-14");
        int stayInventoryBefore = getMinStayInventory("stay-unit-sfo-hotel-summit", "2027-03-10", "2027-03-14");
        int rentalOccupancyBefore = countActiveRentalOccupancies("rental-unit-sfo-economy-01");

        // Verify decremented
        assertEquals(1, rentalOccupancyBefore);

        // Cancel booking
        MvcResult cancelRes = owner.unsafe(post("/api/trips/{tripId}/bookings/{bookingId}/cancel", fixture.tripId, fixture.bookingId),
                "{\"expectedVersion\":2}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ACTIVE"))
                .andExpect(jsonPath("$.version").value(3))
                .andExpect(jsonPath("$.booking.status").value("CANCELED"))
                .andExpect(jsonPath("$.booking.canceledAt").isNotEmpty())
                .andReturn();

        // Verify inventory restored
        int outboundSeatsAfter = getAvailableSeats("airfare-out-sfo-d1", "2027-03-10");
        int returnSeatsAfter = getAvailableSeats("airfare-in-sfo-d1", "2027-03-14");
        int stayInventoryAfter = getMinStayInventory("stay-unit-sfo-hotel-summit", "2027-03-10", "2027-03-14");
        int rentalOccupancyAfter = countActiveRentalOccupancies("rental-unit-sfo-economy-01");

        assertEquals(outboundSeatsBefore + 2, outboundSeatsAfter);
        assertEquals(returnSeatsBefore + 2, returnSeatsAfter);
        assertEquals(stayInventoryBefore + 1, stayInventoryAfter);
        assertEquals(0, rentalOccupancyAfter);

        // Verify database records
        String bookingDbStatus = jdbc.queryForObject(
                "SELECT status FROM detour_booking WHERE public_id = ?",
                String.class, UUID.fromString(fixture.bookingId));
        assertEquals("CANCELED", bookingDbStatus);

        int airfareSnapshots = jdbc.queryForObject(
                "SELECT COUNT(*) FROM detour_booking_airfare_snapshot WHERE booking_id = (SELECT id FROM detour_booking WHERE public_id = ?)",
                Integer.class, UUID.fromString(fixture.bookingId));
        int staySnapshots = jdbc.queryForObject(
                "SELECT COUNT(*) FROM detour_booking_stay_snapshot WHERE booking_id = (SELECT id FROM detour_booking WHERE public_id = ?)",
                Integer.class, UUID.fromString(fixture.bookingId));
        int rentalSnapshots = jdbc.queryForObject(
                "SELECT COUNT(*) FROM detour_booking_rental_snapshot WHERE booking_id = (SELECT id FROM detour_booking WHERE public_id = ?)",
                Integer.class, UUID.fromString(fixture.bookingId));

        assertEquals(1, airfareSnapshots, "Airfare snapshot must be preserved");
        assertEquals(1, staySnapshots, "Stay snapshot must be preserved");
        assertEquals(1, rentalSnapshots, "Rental snapshot must be preserved");
    }

    @Test
    void rebookingPossibleAfterBookingCancellation() throws Exception {
        Client owner = register("rebooking-test@example.test");
        TripFixture fixture = createTripWithBooking(owner);

        // Cancel first booking
        owner.unsafe(post("/api/trips/{tripId}/bookings/{bookingId}/cancel", fixture.tripId, fixture.bookingId),
                "{\"expectedVersion\":2}")
                .andExpect(status().isOk());

        // Re-book with newly restored inventory and updated version 3
        owner.unsafe(post("/api/trips/{tripId}/bookings", fixture.tripId),
                "{\"plannedItineraryId\":\"" + fixture.plannedId + "\",\"expectedVersion\":3,\"idempotencyKey\":\"rebooking-key\"}")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("ACTIVE"))
                .andExpect(jsonPath("$.bookingReference").isNotEmpty());

        // Verify total bookings = 2 (1 CANCELED, 1 ACTIVE)
        int totalBookings = jdbc.queryForObject(
                "SELECT COUNT(*) FROM detour_booking WHERE trip_id = (SELECT id FROM detour_trip WHERE public_id = ?)",
                Integer.class, UUID.fromString(fixture.tripId));
        assertEquals(2, totalBookings);

        int activeBookings = jdbc.queryForObject(
                "SELECT COUNT(*) FROM detour_booking WHERE trip_id = (SELECT id FROM detour_trip WHERE public_id = ?) AND status = 'ACTIVE'",
                Integer.class, UUID.fromString(fixture.tripId));
        assertEquals(1, activeBookings);
    }

    @Test
    void cancelingBookingOnExpiredTripRejectedWithHttp400() throws Exception {
        Client owner = register("expired-booking-cancel@example.test");
        TripFixture fixture = createTripWithBooking(owner);

        // Advance clock to departure date midnight in America/Los_Angeles
        ZoneId pdxZone = ZoneId.of("America/Los_Angeles");
        testClock.setInstant(LocalDate.of(2027, 3, 10).atStartOfDay(pdxZone).toInstant());

        owner.unsafe(post("/api/trips/{tripId}/bookings/{bookingId}/cancel", fixture.tripId, fixture.bookingId),
                "{\"expectedVersion\":2}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("TRIP_EXPIRED"));
    }

    @Test
    void cancelingAlreadyCanceledBookingRejectedWithHttp409() throws Exception {
        Client owner = register("double-cancel-booking@example.test");
        TripFixture fixture = createTripWithBooking(owner);

        // Cancel first time
        owner.unsafe(post("/api/trips/{tripId}/bookings/{bookingId}/cancel", fixture.tripId, fixture.bookingId),
                "{\"expectedVersion\":2}")
                .andExpect(status().isOk());

        int seatsAfterFirstCancel = getAvailableSeats("airfare-out-sfo-d1", "2027-03-10");

        // Cancel second time with current version 3
        owner.unsafe(post("/api/trips/{tripId}/bookings/{bookingId}/cancel", fixture.tripId, fixture.bookingId),
                "{\"expectedVersion\":3}")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("BOOKING_NOT_ACTIVE"));

        int seatsAfterSecondCancel = getAvailableSeats("airfare-out-sfo-d1", "2027-03-10");
        assertEquals(seatsAfterFirstCancel, seatsAfterSecondCancel, "Seats must not be double-incremented");
    }

    @Test
    void cancelingTripWithActiveBookingAtomicallyRestoresInventoryAndSetsTripCanceled() throws Exception {
        Client owner = register("cancel-trip-active@example.test");
        TripFixture fixture = createTripWithBooking(owner);

        int outboundSeatsBefore = getAvailableSeats("airfare-out-sfo-d1", "2027-03-10");
        int stayInventoryBefore = getMinStayInventory("stay-unit-sfo-hotel-summit", "2027-03-10", "2027-03-14");

        owner.unsafe(post("/api/trips/{tripId}/cancel", fixture.tripId),
                "{\"expectedVersion\":2}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELED"))
                .andExpect(jsonPath("$.version").value(3))
                .andExpect(jsonPath("$.booking.status").value("CANCELED"));

        // Inventory restored
        int outboundSeatsAfter = getAvailableSeats("airfare-out-sfo-d1", "2027-03-10");
        int stayInventoryAfter = getMinStayInventory("stay-unit-sfo-hotel-summit", "2027-03-10", "2027-03-14");
        assertEquals(outboundSeatsBefore + 2, outboundSeatsAfter);
        assertEquals(stayInventoryBefore + 1, stayInventoryAfter);
        assertEquals(0, countActiveRentalOccupancies("rental-unit-sfo-economy-01"));

        // Trip status in database
        String tripDbStatus = jdbc.queryForObject(
                "SELECT status FROM detour_trip WHERE public_id = ?",
                String.class, UUID.fromString(fixture.tripId));
        assertEquals("CANCELED", tripDbStatus);
    }

    @Test
    void cancelingTripWithOnlyCanceledBookingsSetsTripCanceledWithoutInventoryAdjustment() throws Exception {
        Client owner = register("cancel-trip-only-canceled@example.test");
        TripFixture fixture = createTripWithBooking(owner);

        // Cancel booking first
        owner.unsafe(post("/api/trips/{tripId}/bookings/{bookingId}/cancel", fixture.tripId, fixture.bookingId),
                "{\"expectedVersion\":2}")
                .andExpect(status().isOk());

        int outboundSeatsBefore = getAvailableSeats("airfare-out-sfo-d1", "2027-03-10");

        // Cancel trip (version is now 3)
        owner.unsafe(post("/api/trips/{tripId}/cancel", fixture.tripId),
                "{\"expectedVersion\":3}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELED"))
                .andExpect(jsonPath("$.version").value(4));

        int outboundSeatsAfter = getAvailableSeats("airfare-out-sfo-d1", "2027-03-10");
        assertEquals(outboundSeatsBefore, outboundSeatsAfter, "Inventory must not be adjusted when trip had only canceled bookings");
    }

    @Test
    void cancelingTripWithoutBookingHistoryRejectedWithHttp400() throws Exception {
        Client owner = register("cancel-trip-unbooked@example.test");
        MvcResult created = owner.unsafe(post("/api/trips"),
                "{\"destinationKey\":\"destination-sfo\",\"startDate\":\"2027-03-10\",\"endDate\":\"2027-03-14\",\"travelerCount\":2,\"travelerAges\":[25,25],\"budgetCents\":500000}")
                .andExpect(status().isCreated()).andReturn();
        String tripId = jsonField(created, "id");

        owner.unsafe(post("/api/trips/{tripId}/cancel", tripId),
                "{\"expectedVersion\":0}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("NO_BOOKING_HISTORY"));

        // Trip remains ACTIVE
        String tripStatus = jdbc.queryForObject(
                "SELECT status FROM detour_trip WHERE public_id = ?",
                String.class, UUID.fromString(tripId));
        assertEquals("ACTIVE", tripStatus);
    }

    @Test
    void cancelingExpiredTripRejectedWithHttp400() throws Exception {
        Client owner = register("cancel-expired-trip@example.test");
        TripFixture fixture = createTripWithBooking(owner);

        // Advance clock to departure date midnight in America/Los_Angeles
        ZoneId pdxZone = ZoneId.of("America/Los_Angeles");
        testClock.setInstant(LocalDate.of(2027, 3, 10).atStartOfDay(pdxZone).toInstant());

        owner.unsafe(post("/api/trips/{tripId}/cancel", fixture.tripId),
                "{\"expectedVersion\":2}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("TRIP_EXPIRED"));
    }

    @Test
    void cancelingAlreadyCanceledTripRejectedWithHttp409() throws Exception {
        Client owner = register("cancel-trip-already-canceled@example.test");
        TripFixture fixture = createTripWithBooking(owner);

        // Cancel trip first time
        owner.unsafe(post("/api/trips/{tripId}/cancel", fixture.tripId),
                "{\"expectedVersion\":2}")
                .andExpect(status().isOk());

        // Cancel trip second time with current version 3
        owner.unsafe(post("/api/trips/{tripId}/cancel", fixture.tripId),
                "{\"expectedVersion\":3}")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("TRIP_ALREADY_CANCELED"));
    }

    @Test
    void allMutationsOnCanceledTripRejectedWithHttp409() throws Exception {
        Client owner = register("mutations-on-canceled@example.test");
        TripFixture fixture = createTripWithBooking(owner);

        // Add an extra draft before canceling so we can test draft mutations
        MvcResult draftRes = owner.unsafe(post("/api/trips/{tripId}/drafts", fixture.tripId),
                "{\"expectedVersion\":2}")
                .andExpect(status().isCreated()).andReturn();
        String draftId = getDraftId(draftRes, 1);

        // Cancel the trip (expectedVersion is now 3)
        owner.unsafe(post("/api/trips/{tripId}/cancel", fixture.tripId),
                "{\"expectedVersion\":3}")
                .andExpect(status().isOk());

        // 1. replaceSharedDetails -> 409 TRIP_CANCELED
        owner.unsafe(put("/api/trips/{tripId}", fixture.tripId),
                "{\"expectedVersion\":4,\"destinationKey\":\"destination-sfo\",\"startDate\":\"2027-03-10\",\"endDate\":\"2027-03-14\",\"travelerCount\":2,\"travelerAges\":[25,25],\"budgetCents\":500000}")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("TRIP_CANCELED"));

        // 2. createDraft -> 409 TRIP_CANCELED
        owner.unsafe(post("/api/trips/{tripId}/drafts", fixture.tripId),
                "{\"expectedVersion\":4}")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("TRIP_CANCELED"));

        // 3. duplicateDraft -> 409 TRIP_CANCELED
        owner.unsafe(post("/api/trips/{tripId}/drafts/{draftId}/duplicate", fixture.tripId, draftId),
                "{\"expectedVersion\":4,\"expectedDraftVersion\":0}")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("TRIP_CANCELED"));

        // 4. deleteDraft -> 409 TRIP_CANCELED
        owner.unsafe(delete("/api/trips/{tripId}/drafts/{draftId}", fixture.tripId, draftId),
                "{\"expectedVersion\":4,\"expectedDraftVersion\":0}")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("TRIP_CANCELED"));

        // 5. promoteDraft -> 409 TRIP_CANCELED
        owner.unsafe(post("/api/trips/{tripId}/drafts/{draftId}/plan", fixture.tripId, draftId),
                "{\"expectedVersion\":4,\"expectedDraftVersion\":0}")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("TRIP_CANCELED"));

        // 6. duplicateAlternative -> 409 TRIP_CANCELED
        owner.unsafe(post("/api/trips/{tripId}/alternatives/{altId}/duplicate", fixture.tripId, fixture.plannedId),
                "{\"expectedVersion\":4}")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("TRIP_CANCELED"));

        // 7. deleteAlternative -> 409 TRIP_CANCELED
        owner.unsafe(delete("/api/trips/{tripId}/alternatives/{altId}", fixture.tripId, fixture.plannedId),
                "{\"expectedVersion\":4,\"confirmed\":true}")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("TRIP_CANCELED"));

        // 8. selectDraftAirfare -> 409 TRIP_CANCELED
        owner.unsafe(put("/api/trips/{tripId}/drafts/{draftId}/airfare", fixture.tripId, draftId),
                "{\"expectedVersion\":4,\"expectedDraftVersion\":0,\"outboundFlightInstanceId\":1,\"returnFlightInstanceId\":2}")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("TRIP_CANCELED"));

        // 9. removeDraftAirfare -> 409 TRIP_CANCELED
        owner.unsafe(delete("/api/trips/{tripId}/drafts/{draftId}/airfare", fixture.tripId, draftId),
                "{\"expectedVersion\":4,\"expectedDraftVersion\":0}")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("TRIP_CANCELED"));

        // 10. selectDraftStay -> 409 TRIP_CANCELED
        owner.unsafe(put("/api/trips/{tripId}/drafts/{draftId}/stays", fixture.tripId, draftId),
                "{\"expectedVersion\":4,\"expectedDraftVersion\":0,\"accommodationUnitId\":1,\"unitCount\":1}")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("TRIP_CANCELED"));

        // 11. removeDraftStay -> 409 TRIP_CANCELED
        owner.unsafe(delete("/api/trips/{tripId}/drafts/{draftId}/stays", fixture.tripId, draftId),
                "{\"expectedVersion\":4,\"expectedDraftVersion\":0}")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("TRIP_CANCELED"));

        // 12. selectDraftRental -> 409 TRIP_CANCELED
        owner.unsafe(put("/api/trips/{tripId}/drafts/{draftId}/rentals", fixture.tripId, draftId),
                "{\"expectedVersion\":4,\"expectedDraftVersion\":0,\"rentalUnitId\":1,\"pickupAt\":\"2027-03-10T10:00:00Z\",\"returnAt\":\"2027-03-14T15:00:00Z\"}")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("TRIP_CANCELED"));

        // 13. removeDraftRental -> 409 TRIP_CANCELED
        owner.unsafe(delete("/api/trips/{tripId}/drafts/{draftId}/rentals", fixture.tripId, draftId),
                "{\"expectedVersion\":4,\"expectedDraftVersion\":0}")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("TRIP_CANCELED"));

        // 14. createBooking -> 409 TRIP_CANCELED
        owner.unsafe(post("/api/trips/{tripId}/bookings", fixture.tripId),
                "{\"plannedItineraryId\":\"" + fixture.plannedId + "\",\"expectedVersion\":4,\"idempotencyKey\":\"attempt-booking\"}")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("TRIP_CANCELED"));
    }

    @Test
    void duplicateTripAllowedOnCanceledTrip() throws Exception {
        Client owner = register("duplicate-canceled-trip@example.test");
        TripFixture fixture = createTripWithBooking(owner);

        // Cancel trip (expectedVersion is 2, advances to 3)
        owner.unsafe(post("/api/trips/{tripId}/cancel", fixture.tripId),
                "{\"expectedVersion\":2}")
                .andExpect(status().isOk());

        // Duplicate the canceled trip
        MvcResult duplicateRes = owner.unsafe(post("/api/trips/{tripId}/duplicate", fixture.tripId),
                "{\"expectedVersion\":3,\"destinationKey\":\"destination-sfo\",\"startDate\":\"2027-03-10\",\"endDate\":\"2027-03-14\",\"travelerCount\":2,\"travelerAges\":[25,25],\"budgetCents\":500000,\"sourcePlannedItineraryIds\":[\"" + fixture.plannedId + "\"]}")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").isNotEmpty())
                .andExpect(jsonPath("$.status").value("ACTIVE"))
                .andExpect(jsonPath("$.version").value(0))
                .andReturn();

        String newTripId = jsonField(duplicateRes, "id");
        org.junit.jupiter.api.Assertions.assertNotEquals(fixture.tripId, newTripId);

        // Original trip remains CANCELED
        String originalStatus = jdbc.queryForObject(
                "SELECT status FROM detour_trip WHERE public_id = ?",
                String.class, UUID.fromString(fixture.tripId));
        assertEquals("CANCELED", originalStatus);
    }

    @Test
    void deletingActivelyBookedPlannedAlternativeRejectedWithHttp409() throws Exception {
        Client owner = register("delete-active-booked-alt@example.test");
        TripFixture fixture = createTripWithBooking(owner);

        // Attempt to delete actively booked planned alternative -> 409 CANNOT_DELETE_ACTIVE_BOOKED_ALTERNATIVE
        owner.unsafe(delete("/api/trips/{tripId}/alternatives/{altId}", fixture.tripId, fixture.plannedId),
                "{\"expectedVersion\":2,\"confirmed\":true}")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CANNOT_DELETE_ACTIVE_BOOKED_ALTERNATIVE"));

        // Planned alternative remains
        int plannedCount = jdbc.queryForObject(
                "SELECT COUNT(*) FROM detour_planned_itinerary WHERE public_id = ?",
                Integer.class, UUID.fromString(fixture.plannedId));
        assertEquals(1, plannedCount);
    }

    @Test
    void deletingUnbookedPlannedAlternativeSucceedsAndPreservesHistoricalCanceledBookings() throws Exception {
        Client owner = register("delete-unbooked-alt-preserves-booking@example.test");
        TripFixture fixture = createTripWithBooking(owner);

        // Cancel the booking first (trip remains ACTIVE, version advances to 3)
        owner.unsafe(post("/api/trips/{tripId}/bookings/{bookingId}/cancel", fixture.tripId, fixture.bookingId),
                "{\"expectedVersion\":2}")
                .andExpect(status().isOk());

        // Delete the formerly-booked planned alternative
        owner.unsafe(delete("/api/trips/{tripId}/alternatives/{altId}", fixture.tripId, fixture.plannedId),
                "{\"expectedVersion\":3,\"confirmed\":true}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.planned.length()").value(0));

        // Verify planned itinerary record deleted
        int plannedCount = jdbc.queryForObject(
                "SELECT COUNT(*) FROM detour_planned_itinerary WHERE public_id = ?",
                Integer.class, UUID.fromString(fixture.plannedId));
        assertEquals(0, plannedCount);

        // Verify booking row preserved with planned_itinerary_id set to NULL
        Long dbPlannedFk = jdbc.queryForObject(
                "SELECT planned_itinerary_id FROM detour_booking WHERE public_id = ?",
                Long.class, UUID.fromString(fixture.bookingId));
        org.junit.jupiter.api.Assertions.assertNull(dbPlannedFk);

        // Verify snapshots preserved
        int staySnapshots = jdbc.queryForObject(
                "SELECT COUNT(*) FROM detour_booking_stay_snapshot WHERE booking_id = (SELECT id FROM detour_booking WHERE public_id = ?)",
                Integer.class, UUID.fromString(fixture.bookingId));
        assertEquals(1, staySnapshots);

        // hasBookingHistory still true on trip profile
        owner.unsafe(get("/api/trips"), null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.upcoming[0].hasBookingHistory").value(true));
    }

    @Test
    void deletingTripWithBookingHistoryPermanentlyRejectedWithHttp409() throws Exception {
        Client owner = register("delete-booked-trip-rejected@example.test");
        TripFixture fixture = createTripWithBooking(owner);

        // Cancel the booking (version advances to 3)
        owner.unsafe(post("/api/trips/{tripId}/bookings/{bookingId}/cancel", fixture.tripId, fixture.bookingId),
                "{\"expectedVersion\":2}")
                .andExpect(status().isOk());

        // Attempt to delete trip -> 409 CANNOT_DELETE_BOOKED_TRIP
        owner.unsafe(delete("/api/trips/{tripId}", fixture.tripId),
                "{\"expectedVersion\":3,\"expectedDraftCount\":0,\"expectedPlannedCount\":1,\"confirmed\":true}")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CANNOT_DELETE_BOOKED_TRIP"));

        // Trip still exists in database
        int tripCount = jdbc.queryForObject(
                "SELECT COUNT(*) FROM detour_trip WHERE public_id = ?",
                Integer.class, UUID.fromString(fixture.tripId));
        assertEquals(1, tripCount);
    }

    @Test
    void multiUserIsolationPreventsCancelingOtherUsersBookingsOrTrips() throws Exception {
        Client userA = register("isolation-user-a@example.test");
        Client userB = register("isolation-user-b@example.test");
        TripFixture fixtureA = createTripWithBooking(userA);

        // User B attempts to cancel User A's booking -> 404 RESOURCE_NOT_FOUND
        userB.unsafe(post("/api/trips/{tripId}/bookings/{bookingId}/cancel", fixtureA.tripId, fixtureA.bookingId),
                "{\"expectedVersion\":2}")
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("RESOURCE_NOT_FOUND"));

        // User B attempts to cancel User A's trip -> 404 RESOURCE_NOT_FOUND
        userB.unsafe(post("/api/trips/{tripId}/cancel", fixtureA.tripId),
                "{\"expectedVersion\":2}")
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("RESOURCE_NOT_FOUND"));

        // User A's booking is still ACTIVE and trip is still ACTIVE
        String bookingStatus = jdbc.queryForObject(
                "SELECT status FROM detour_booking WHERE public_id = ?",
                String.class, UUID.fromString(fixtureA.bookingId));
        assertEquals("ACTIVE", bookingStatus);

        String tripStatus = jdbc.queryForObject(
                "SELECT status FROM detour_trip WHERE public_id = ?",
                String.class, UUID.fromString(fixtureA.tripId));
        assertEquals("ACTIVE", tripStatus);
    }

    // Helper fixture class
    private record TripFixture(String tripId, String draftId, String plannedId, String bookingId) { }

    private TripFixture createTripWithBooking(Client client) throws Exception {
        MvcResult created = client.unsafe(post("/api/trips"),
                "{\"destinationKey\":\"destination-sfo\",\"startDate\":\"2027-03-10\",\"endDate\":\"2027-03-14\",\"travelerCount\":2,\"travelerAges\":[25,25],\"budgetCents\":500000}")
                .andExpect(status().isCreated()).andReturn();
        String tripId = jsonField(created, "id");
        String draftId = getDraftId(created, 0);

        insertSfoAirfareSelection(draftId);
        insertSfoStaySelection(draftId);
        insertSfoRentalSelection(draftId);

        MvcResult planRes = client.unsafe(post("/api/trips/{tripId}/drafts/{draftId}/plan", tripId, draftId),
                "{\"expectedVersion\":0,\"expectedDraftVersion\":0}")
                .andExpect(status().isCreated()).andReturn();
        String plannedId = getPlannedId(planRes, 0);

        MvcResult bookRes = client.unsafe(post("/api/trips/{tripId}/bookings", tripId),
                "{\"plannedItineraryId\":\"" + plannedId + "\",\"expectedVersion\":1,\"idempotencyKey\":\"fixture-booking-" + UUID.randomUUID() + "\"}")
                .andExpect(status().isCreated()).andReturn();
        String bookingId = jsonField(bookRes, "id");

        return new TripFixture(tripId, draftId, plannedId, bookingId);
    }

    private int getAvailableSeats(String catalogKey, String serviceDate) {
        return jdbc.queryForObject(
                "SELECT instance.available_seats FROM flight_instance instance JOIN flight_schedule schedule ON schedule.id = instance.flight_schedule_id WHERE schedule.catalog_key = ? AND instance.service_date = DATE '" + serviceDate + "'",
                Integer.class, catalogKey);
    }

    private int getMinStayInventory(String unitCatalogKey, String startDate, String endDate) {
        return jdbc.queryForObject(
                "SELECT MIN(available_inventory) FROM accommodation_nightly_inventory WHERE accommodation_unit_id = (SELECT id FROM accommodation_unit WHERE catalog_key = ?) AND night_date >= DATE '" + startDate + "' AND night_date < DATE '" + endDate + "'",
                Integer.class, unitCatalogKey);
    }

    private int countActiveRentalOccupancies(String unitCatalogKey) {
        return jdbc.queryForObject(
                "SELECT COUNT(*) FROM rental_unit_occupancy WHERE rental_unit_id = (SELECT id FROM rental_unit WHERE catalog_key = ?) AND occupancy_status = 'ACTIVE'",
                Integer.class, unitCatalogKey);
    }

    private void insertSfoAirfareSelection(String draftId) {
        jdbc.update("""
                INSERT INTO detour_trip_draft_airfare_selection (draft_id, outbound_flight_instance_id, return_flight_instance_id)
                VALUES ((SELECT id FROM detour_trip_draft WHERE public_id = ?),
                    (SELECT instance.id FROM flight_instance instance JOIN flight_schedule schedule ON schedule.id = instance.flight_schedule_id WHERE schedule.catalog_key = 'airfare-out-sfo-d1' AND instance.service_date = DATE '2027-03-10'),
                    (SELECT instance.id FROM flight_instance instance JOIN flight_schedule schedule ON schedule.id = instance.flight_schedule_id WHERE schedule.catalog_key = 'airfare-in-sfo-d1' AND instance.service_date = DATE '2027-03-14'))
                """, UUID.fromString(draftId));
    }

    private void insertSfoStaySelection(String draftId) {
        jdbc.update("""
                INSERT INTO detour_trip_draft_stay_selection (draft_id, accommodation_unit_id, unit_count)
                VALUES ((SELECT id FROM detour_trip_draft WHERE public_id = ?),
                    (SELECT id FROM accommodation_unit WHERE catalog_key = 'stay-unit-sfo-hotel-summit'), 1)
                """, UUID.fromString(draftId));
    }

    private void insertSfoRentalSelection(String draftId) {
        jdbc.update("""
                INSERT INTO detour_trip_draft_rental_selection (draft_id, rental_unit_id, pickup_at, return_at)
                VALUES ((SELECT id FROM detour_trip_draft WHERE public_id = ?),
                    (SELECT id FROM rental_unit WHERE catalog_key = 'rental-unit-sfo-economy-01'),
                    TIMESTAMP WITH TIME ZONE '2027-03-10 10:00:00+00',
                    TIMESTAMP WITH TIME ZONE '2027-03-14 15:00:00+00')
                """, UUID.fromString(draftId));
    }

    private static String getDraftId(MvcResult result, int index) throws Exception {
        return tools.jackson.databind.json.JsonMapper.builder().build()
                .readTree(result.getResponse().getContentAsString()).get("drafts").get(index).get("id").asString();
    }

    private static String getPlannedId(MvcResult result, int index) throws Exception {
        return tools.jackson.databind.json.JsonMapper.builder().build()
                .readTree(result.getResponse().getContentAsString()).get("planned").get(index).get("id").asString();
    }

    private static String jsonField(MvcResult result, String field) throws Exception {
        return tools.jackson.databind.json.JsonMapper.builder().build().readTree(result.getResponse().getContentAsString()).get(field).asString();
    }

    private Client register(String email) throws Exception {
        Client client = anonymous();
        MvcResult result = client.unsafe(post("/api/auth/register"), "{\"email\":\"" + email + "\",\"password\":\"aaaaaaaaaaaa\"}")
                .andExpect(status().isCreated()).andReturn();
        client.session = (MockHttpSession) result.getRequest().getSession(false);
        return client;
    }

    private Client anonymous() throws Exception {
        MvcResult result = mockMvc.perform(get("/")).andExpect(status().isOk()).andReturn();
        Cookie csrf = result.getResponse().getCookie("XSRF-TOKEN");
        assertNotNull(csrf);
        return new Client(csrf);
    }

    private final class Client {
        private MockHttpSession session;
        private final Cookie csrf;
        private Client(Cookie csrf) { this.csrf = csrf; }
        private org.springframework.test.web.servlet.ResultActions unsafe(org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder request,
                String body) throws Exception {
            request.cookie(csrf).header("X-XSRF-TOKEN", csrf.getValue()).contentType(MediaType.APPLICATION_JSON);
            if (session != null) request.session(session);
            if (body != null) request.content(body);
            return mockMvc.perform(request);
        }
    }
}
