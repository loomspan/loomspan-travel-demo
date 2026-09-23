package app.detour.booking;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import app.detour.common.ClockConfiguration;
import app.detour.trip.TestClockConfiguration;
import jakarta.servlet.http.Cookie;
import java.time.LocalDate;
import java.time.ZonedDateTime;
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
class BookingApiIntegrationTest {
    private static final String DATABASE_URL = "jdbc:h2:mem:booking_api_" + UUID.randomUUID() + ";DB_CLOSE_DELAY=-1";

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
    void booksValidPlannedItineraryAndDecrementsAllComponents() throws Exception {
        Client owner = register("booking-red-test@example.test");
        MvcResult created = owner.unsafe(post("/api/trips"),
                "{\"destinationKey\":\"destination-sfo\",\"startDate\":\"2027-03-10\",\"endDate\":\"2027-03-14\",\"travelerCount\":2,\"travelerAges\":[25,25],\"budgetCents\":500000}")
                .andExpect(status().isCreated()).andReturn();
        String tripId = jsonField(created, "id");
        String draftId = getDraftId(created, 0);

        insertSfoAirfareSelection(draftId);
        insertSfoStaySelection(draftId);

        MvcResult planRes = owner.unsafe(post("/api/trips/{tripId}/drafts/{draftId}/plan", tripId, draftId),
                "{\"expectedVersion\":0,\"expectedDraftVersion\":0}")
                .andExpect(status().isCreated()).andReturn();
        String plannedId = getPlannedId(planRes, 0);

        int outboundSeatsBefore = getAvailableSeats("airfare-out-sfo-d1", "2027-03-10");
        int returnSeatsBefore = getAvailableSeats("airfare-in-sfo-d1", "2027-03-14");
        int stayInventoryBefore = getMinStayInventory("stay-unit-sfo-hotel-summit", "2027-03-10", "2027-03-14");

        // Submit booking request
        owner.unsafe(post("/api/trips/{tripId}/bookings", tripId),
                "{\"plannedItineraryId\":\"" + plannedId + "\",\"expectedVersion\":1,\"idempotencyKey\":\"red-test-idempotency-key\"}")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").isString())
                .andExpect(jsonPath("$.bookingReference").value(org.hamcrest.Matchers.matchesPattern("^DT-[A-Z0-9]{6}$")))
                .andExpect(jsonPath("$.status").value("ACTIVE"))
                .andExpect(jsonPath("$.airfareReference").value(org.hamcrest.Matchers.matchesPattern("^FL-[A-Z0-9]{6}$")))
                .andExpect(jsonPath("$.stayReference").value(org.hamcrest.Matchers.matchesPattern("^HT-[A-Z0-9]{6}$")))
                .andExpect(jsonPath("$.rentalReference").doesNotExist())
                .andExpect(jsonPath("$.grandTotalCents").isNumber());

        int outboundSeatsAfter = getAvailableSeats("airfare-out-sfo-d1", "2027-03-10");
        int returnSeatsAfter = getAvailableSeats("airfare-in-sfo-d1", "2027-03-14");
        int stayInventoryAfter = getMinStayInventory("stay-unit-sfo-hotel-summit", "2027-03-10", "2027-03-14");

        assertEquals(outboundSeatsBefore - 2, outboundSeatsAfter);
        assertEquals(returnSeatsBefore - 2, returnSeatsAfter);
        assertEquals(stayInventoryBefore - 1, stayInventoryAfter);
    }

    @Test
    void booksAllThreeComponentsAndPopulatesAllReferences() throws Exception {
        Client owner = register("all-three@example.test");
        MvcResult created = owner.unsafe(post("/api/trips"),
                "{\"destinationKey\":\"destination-sfo\",\"startDate\":\"2027-03-10\",\"endDate\":\"2027-03-14\",\"travelerCount\":2,\"travelerAges\":[25,25],\"budgetCents\":500000}")
                .andExpect(status().isCreated()).andReturn();
        String tripId = jsonField(created, "id");
        String draftId = getDraftId(created, 0);

        insertSfoAirfareSelection(draftId);
        insertSfoStaySelection(draftId);
        insertSfoRentalSelection(draftId);

        MvcResult planRes = owner.unsafe(post("/api/trips/{tripId}/drafts/{draftId}/plan", tripId, draftId),
                "{\"expectedVersion\":0,\"expectedDraftVersion\":0}")
                .andExpect(status().isCreated()).andReturn();
        String plannedId = getPlannedId(planRes, 0);

        int occupanciesBefore = countActiveRentalOccupancies("rental-unit-sfo-economy-01");

        owner.unsafe(post("/api/trips/{tripId}/bookings", tripId),
                "{\"plannedItineraryId\":\"" + plannedId + "\",\"expectedVersion\":1,\"idempotencyKey\":\"all-three-key\"}")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.bookingReference").value(org.hamcrest.Matchers.matchesPattern("^DT-[A-Z0-9]{6}$")))
                .andExpect(jsonPath("$.status").value("ACTIVE"))
                .andExpect(jsonPath("$.airfareReference").value(org.hamcrest.Matchers.matchesPattern("^FL-[A-Z0-9]{6}$")))
                .andExpect(jsonPath("$.stayReference").value(org.hamcrest.Matchers.matchesPattern("^HT-[A-Z0-9]{6}$")))
                .andExpect(jsonPath("$.rentalReference").value(org.hamcrest.Matchers.matchesPattern("^RC-[A-Z0-9]{6}$")));

        int occupanciesAfter = countActiveRentalOccupancies("rental-unit-sfo-economy-01");
        assertEquals(occupanciesBefore + 1, occupanciesAfter);
    }

    @Test
    void idempotentReplayReturnsExistingBookingWithoutDoubleDecrement() throws Exception {
        Client owner = register("idemp-body@example.test");
        MvcResult created = owner.unsafe(post("/api/trips"),
                "{\"destinationKey\":\"destination-sfo\",\"startDate\":\"2027-03-10\",\"endDate\":\"2027-03-14\",\"travelerCount\":2,\"travelerAges\":[25,25],\"budgetCents\":500000}")
                .andExpect(status().isCreated()).andReturn();
        String tripId = jsonField(created, "id");
        String draftId = getDraftId(created, 0);

        insertSfoAirfareSelection(draftId);
        MvcResult planRes = owner.unsafe(post("/api/trips/{tripId}/drafts/{draftId}/plan", tripId, draftId),
                "{\"expectedVersion\":0,\"expectedDraftVersion\":0}")
                .andExpect(status().isCreated()).andReturn();
        String plannedId = getPlannedId(planRes, 0);

        int seatsBefore = getAvailableSeats("airfare-out-sfo-d1", "2027-03-10");

        // First attempt
        MvcResult firstBooking = owner.unsafe(post("/api/trips/{tripId}/bookings", tripId),
                "{\"plannedItineraryId\":\"" + plannedId + "\",\"expectedVersion\":1,\"idempotencyKey\":\"idemp-replay-key\"}")
                .andExpect(status().isCreated()).andReturn();
        String bookingRef = jsonField(firstBooking, "bookingReference");
        String bookingId = jsonField(firstBooking, "id");

        int seatsAfterFirst = getAvailableSeats("airfare-out-sfo-d1", "2027-03-10");
        assertEquals(seatsBefore - 2, seatsAfterFirst);

        // Replay attempt with same key
        MvcResult secondBooking = owner.unsafe(post("/api/trips/{tripId}/bookings", tripId),
                "{\"plannedItineraryId\":\"" + plannedId + "\",\"expectedVersion\":1,\"idempotencyKey\":\"idemp-replay-key\"}")
                .andExpect(status().isOk()).andReturn();

        assertEquals(bookingRef, jsonField(secondBooking, "bookingReference"));
        assertEquals(bookingId, jsonField(secondBooking, "id"));

        int seatsAfterSecond = getAvailableSeats("airfare-out-sfo-d1", "2027-03-10");
        assertEquals(seatsAfterFirst, seatsAfterSecond, "Inventory must not be deducted on idempotent replay");
    }

    @Test
    void supportsIdempotencyKeyViaHttpHeader() throws Exception {
        Client owner = register("idemp-header@example.test");
        MvcResult created = owner.unsafe(post("/api/trips"),
                "{\"destinationKey\":\"destination-sfo\",\"startDate\":\"2027-03-10\",\"endDate\":\"2027-03-14\",\"travelerCount\":2,\"travelerAges\":[25,25],\"budgetCents\":500000}")
                .andExpect(status().isCreated()).andReturn();
        String tripId = jsonField(created, "id");
        String draftId = getDraftId(created, 0);

        insertSfoAirfareSelection(draftId);
        MvcResult planRes = owner.unsafe(post("/api/trips/{tripId}/drafts/{draftId}/plan", tripId, draftId),
                "{\"expectedVersion\":0,\"expectedDraftVersion\":0}")
                .andExpect(status().isCreated()).andReturn();
        String plannedId = getPlannedId(planRes, 0);

        // Request with header Idempotency-Key
        owner.unsafeWithHeader(post("/api/trips/{tripId}/bookings", tripId),
                "{\"plannedItineraryId\":\"" + plannedId + "\",\"expectedVersion\":1}",
                "Idempotency-Key", "header-key-123")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.idempotencyKey").value("header-key-123"));

        // Replay with header
        owner.unsafeWithHeader(post("/api/trips/{tripId}/bookings", tripId),
                "{\"plannedItineraryId\":\"" + plannedId + "\",\"expectedVersion\":1}",
                "Idempotency-Key", "header-key-123")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.idempotencyKey").value("header-key-123"));
    }

    @Test
    void rejectsBookingOnExpiredTrip() throws Exception {
        Client owner = register("expired-trip@example.test");
        MvcResult created = owner.unsafe(post("/api/trips"),
                "{\"destinationKey\":\"destination-sfo\",\"startDate\":\"2027-03-10\",\"endDate\":\"2027-03-14\",\"travelerCount\":2,\"travelerAges\":[25,25],\"budgetCents\":500000}")
                .andExpect(status().isCreated()).andReturn();
        String tripId = jsonField(created, "id");
        String draftId = getDraftId(created, 0);

        insertSfoAirfareSelection(draftId);
        MvcResult planRes = owner.unsafe(post("/api/trips/{tripId}/drafts/{draftId}/plan", tripId, draftId),
                "{\"expectedVersion\":0,\"expectedDraftVersion\":0}")
                .andExpect(status().isCreated()).andReturn();
        String plannedId = getPlannedId(planRes, 0);

        // Advance clock to departure midnight in America/Los_Angeles
        ZonedDateTime midnightPdx = LocalDate.parse("2027-03-10").atStartOfDay(ClockConfiguration.PDX_ZONE);
        testClock.setInstant(midnightPdx.toInstant());

        owner.unsafe(post("/api/trips/{tripId}/bookings", tripId),
                "{\"plannedItineraryId\":\"" + plannedId + "\",\"expectedVersion\":1,\"idempotencyKey\":\"expired-booking\"}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("TRIP_EXPIRED"));
    }

    @Test
    void rejectsSecondActiveBookingOnTrip() throws Exception {
        Client owner = register("already-booked@example.test");
        MvcResult created = owner.unsafe(post("/api/trips"),
                "{\"destinationKey\":\"destination-sfo\",\"startDate\":\"2027-03-10\",\"endDate\":\"2027-03-14\",\"travelerCount\":2,\"travelerAges\":[25,25],\"budgetCents\":500000}")
                .andExpect(status().isCreated()).andReturn();
        String tripId = jsonField(created, "id");
        String draftId = getDraftId(created, 0);

        insertSfoAirfareSelection(draftId);
        MvcResult planRes = owner.unsafe(post("/api/trips/{tripId}/drafts/{draftId}/plan", tripId, draftId),
                "{\"expectedVersion\":0,\"expectedDraftVersion\":0}")
                .andExpect(status().isCreated()).andReturn();
        String plannedId = getPlannedId(planRes, 0);

        // First booking succeeds
        owner.unsafe(post("/api/trips/{tripId}/bookings", tripId),
                "{\"plannedItineraryId\":\"" + plannedId + "\",\"expectedVersion\":1,\"idempotencyKey\":\"booking-1\"}")
                .andExpect(status().isCreated());

        // Create a second draft and plan it
        MvcResult secondDraftRes = owner.unsafe(post("/api/trips/{tripId}/drafts", tripId), "{\"expectedVersion\":2}")
                .andExpect(status().isCreated()).andReturn();
        String secondDraftId = getDraftId(secondDraftRes, 1);
        insertSfoStaySelection(secondDraftId);
        MvcResult secondPlanRes = owner.unsafe(post("/api/trips/{tripId}/drafts/{draftId}/plan", tripId, secondDraftId),
                "{\"expectedVersion\":3,\"expectedDraftVersion\":0}")
                .andExpect(status().isCreated()).andReturn();
        String secondPlannedId = getPlannedId(secondPlanRes, 1);

        // Attempting second booking with different idempotency key fails with 409 ALREADY_BOOKED
        owner.unsafe(post("/api/trips/{tripId}/bookings", tripId),
                "{\"plannedItineraryId\":\"" + secondPlannedId + "\",\"expectedVersion\":4,\"idempotencyKey\":\"booking-2\"}")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("ALREADY_BOOKED"));
    }

    @Test
    void rejectsBookingOnVersionMismatch() throws Exception {
        Client owner = register("version-conflict@example.test");
        MvcResult created = owner.unsafe(post("/api/trips"),
                "{\"destinationKey\":\"destination-sfo\",\"startDate\":\"2027-03-10\",\"endDate\":\"2027-03-14\",\"travelerCount\":2,\"travelerAges\":[25,25],\"budgetCents\":500000}")
                .andExpect(status().isCreated()).andReturn();
        String tripId = jsonField(created, "id");
        String draftId = getDraftId(created, 0);

        insertSfoAirfareSelection(draftId);
        MvcResult planRes = owner.unsafe(post("/api/trips/{tripId}/drafts/{draftId}/plan", tripId, draftId),
                "{\"expectedVersion\":0,\"expectedDraftVersion\":0}")
                .andExpect(status().isCreated()).andReturn();
        String plannedId = getPlannedId(planRes, 0);

        // Submit with stale expectedVersion (0 instead of 1)
        owner.unsafe(post("/api/trips/{tripId}/bookings", tripId),
                "{\"plannedItineraryId\":\"" + plannedId + "\",\"expectedVersion\":0,\"idempotencyKey\":\"stale-version\"}")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("VERSION_CONFLICT"));
    }

    @Test
    void rejectsBookingWhenPlannedItineraryHasNoComponents() throws Exception {
        Client owner = register("no-components@example.test");
        MvcResult created = owner.unsafe(post("/api/trips"),
                "{\"destinationKey\":\"destination-sfo\",\"startDate\":\"2027-03-10\",\"endDate\":\"2027-03-14\",\"travelerCount\":2,\"travelerAges\":[25,25],\"budgetCents\":500000}")
                .andExpect(status().isCreated()).andReturn();
        String tripId = jsonField(created, "id");
        long internalTripId = jdbc.queryForObject("SELECT id FROM detour_trip WHERE public_id = ?", Long.class, UUID.fromString(tripId));

        UUID emptyPlannedPublicId = UUID.randomUUID();
        jdbc.update("INSERT INTO detour_planned_itinerary (public_id, trip_id) VALUES (?, ?)", emptyPlannedPublicId, internalTripId);

        owner.unsafe(post("/api/trips/{tripId}/bookings", tripId),
                "{\"plannedItineraryId\":\"" + emptyPlannedPublicId + "\",\"expectedVersion\":0,\"idempotencyKey\":\"empty-plan\"}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("NO_RESERVABLE_COMPONENTS"));
    }

    @Test
    void rollsBackCompletelyWhenAirfareInventoryExhausted() throws Exception {
        Client owner = register("exhaust-air@example.test");
        MvcResult created = owner.unsafe(post("/api/trips"),
                "{\"destinationKey\":\"destination-sfo\",\"startDate\":\"2027-03-10\",\"endDate\":\"2027-03-14\",\"travelerCount\":2,\"travelerAges\":[25,25],\"budgetCents\":500000}")
                .andExpect(status().isCreated()).andReturn();
        String tripId = jsonField(created, "id");
        String draftId = getDraftId(created, 0);

        insertSfoAirfareSelection(draftId);
        insertSfoStaySelection(draftId);
        insertSfoRentalSelection(draftId);

        MvcResult planRes = owner.unsafe(post("/api/trips/{tripId}/drafts/{draftId}/plan", tripId, draftId),
                "{\"expectedVersion\":0,\"expectedDraftVersion\":0}")
                .andExpect(status().isCreated()).andReturn();
        String plannedId = getPlannedId(planRes, 0);

        int initialSeats = getAvailableSeats("airfare-out-sfo-d1", "2027-03-10");
        try {
            // Deplete flight seats
            jdbc.update("UPDATE flight_instance SET available_seats = 1 WHERE service_date = DATE '2027-03-10' AND flight_schedule_id = (SELECT id FROM flight_schedule WHERE catalog_key = 'airfare-out-sfo-d1')");

            int stayInventoryBefore = getMinStayInventory("stay-unit-sfo-hotel-summit", "2027-03-10", "2027-03-14");
            int occupanciesBefore = countActiveRentalOccupancies("rental-unit-sfo-economy-01");

            owner.unsafe(post("/api/trips/{tripId}/bookings", tripId),
                    "{\"plannedItineraryId\":\"" + plannedId + "\",\"expectedVersion\":1,\"idempotencyKey\":\"fail-air\"}")
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.code").value("INVENTORY_CONFLICT"))
                    .andExpect(jsonPath("$.fields.airfare").value("Selected flight does not have enough available seats for party size."));

            // Verify total rollback
            int stayInventoryAfter = getMinStayInventory("stay-unit-sfo-hotel-summit", "2027-03-10", "2027-03-14");
            int occupanciesAfter = countActiveRentalOccupancies("rental-unit-sfo-economy-01");
            assertEquals(stayInventoryBefore, stayInventoryAfter, "Stay inventory must not be modified when airfare fails");
            assertEquals(occupanciesBefore, occupanciesAfter, "Rental occupancy must not be created when airfare fails");
        } finally {
            // Restore seats
            jdbc.update("UPDATE flight_instance SET available_seats = ? WHERE service_date = DATE '2027-03-10' AND flight_schedule_id = (SELECT id FROM flight_schedule WHERE catalog_key = 'airfare-out-sfo-d1')", initialSeats);
        }
    }

    @Test
    void rollsBackCompletelyWhenStayInventoryExhausted() throws Exception {
        Client owner = register("exhaust-stay@example.test");
        MvcResult created = owner.unsafe(post("/api/trips"),
                "{\"destinationKey\":\"destination-sfo\",\"startDate\":\"2027-03-10\",\"endDate\":\"2027-03-14\",\"travelerCount\":2,\"travelerAges\":[25,25],\"budgetCents\":500000}")
                .andExpect(status().isCreated()).andReturn();
        String tripId = jsonField(created, "id");
        String draftId = getDraftId(created, 0);

        insertSfoAirfareSelection(draftId);
        insertSfoStaySelection(draftId);

        MvcResult planRes = owner.unsafe(post("/api/trips/{tripId}/drafts/{draftId}/plan", tripId, draftId),
                "{\"expectedVersion\":0,\"expectedDraftVersion\":0}")
                .andExpect(status().isCreated()).andReturn();
        String plannedId = getPlannedId(planRes, 0);

        int initialStayInv = jdbc.queryForObject("SELECT available_inventory FROM accommodation_nightly_inventory WHERE night_date = DATE '2027-03-11' AND accommodation_unit_id = (SELECT id FROM accommodation_unit WHERE catalog_key = 'stay-unit-sfo-hotel-summit')", Integer.class);
        try {
            // Deplete stay nightly inventory for 1 night
            jdbc.update("UPDATE accommodation_nightly_inventory SET available_inventory = 0 WHERE night_date = DATE '2027-03-11' AND accommodation_unit_id = (SELECT id FROM accommodation_unit WHERE catalog_key = 'stay-unit-sfo-hotel-summit')");

            int seatsBefore = getAvailableSeats("airfare-out-sfo-d1", "2027-03-10");

            owner.unsafe(post("/api/trips/{tripId}/bookings", tripId),
                    "{\"plannedItineraryId\":\"" + plannedId + "\",\"expectedVersion\":1,\"idempotencyKey\":\"fail-stay\"}")
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.code").value("INVENTORY_CONFLICT"))
                    .andExpect(jsonPath("$.fields.stay").value("Selected accommodation is sold out for one or more requested nights."));

            int seatsAfter = getAvailableSeats("airfare-out-sfo-d1", "2027-03-10");
            assertEquals(seatsBefore, seatsAfter, "Flight seats must not be modified when stay fails");
        } finally {
            // Restore stay inventory
            jdbc.update("UPDATE accommodation_nightly_inventory SET available_inventory = ? WHERE night_date = DATE '2027-03-11' AND accommodation_unit_id = (SELECT id FROM accommodation_unit WHERE catalog_key = 'stay-unit-sfo-hotel-summit')", initialStayInv);
        }
    }

    @Test
    void rollsBackCompletelyWhenRentalOccupancyOverlaps() throws Exception {
        Client owner = register("exhaust-rental@example.test");
        MvcResult created = owner.unsafe(post("/api/trips"),
                "{\"destinationKey\":\"destination-sfo\",\"startDate\":\"2027-03-10\",\"endDate\":\"2027-03-14\",\"travelerCount\":2,\"travelerAges\":[25,25],\"budgetCents\":500000}")
                .andExpect(status().isCreated()).andReturn();
        String tripId = jsonField(created, "id");
        String draftId = getDraftId(created, 0);

        insertSfoAirfareSelection(draftId);
        insertSfoRentalSelection(draftId);

        MvcResult planRes = owner.unsafe(post("/api/trips/{tripId}/drafts/{draftId}/plan", tripId, draftId),
                "{\"expectedVersion\":0,\"expectedDraftVersion\":0}")
                .andExpect(status().isCreated()).andReturn();
        String plannedId = getPlannedId(planRes, 0);

        // Insert overlapping rental occupancy
        long rentalUnitId = jdbc.queryForObject("SELECT id FROM rental_unit WHERE catalog_key = 'rental-unit-sfo-economy-01'", Long.class);
        try {
            jdbc.update("INSERT INTO rental_unit_occupancy (rental_unit_id, pickup_at, return_at, occupancy_status) VALUES (?, TIMESTAMP WITH TIME ZONE '2027-03-10 09:00:00+00', TIMESTAMP WITH TIME ZONE '2027-03-14 18:00:00+00', 'ACTIVE')",
                    rentalUnitId);

            int seatsBefore = getAvailableSeats("airfare-out-sfo-d1", "2027-03-10");

            owner.unsafe(post("/api/trips/{tripId}/bookings", tripId),
                    "{\"plannedItineraryId\":\"" + plannedId + "\",\"expectedVersion\":1,\"idempotencyKey\":\"fail-rental\"}")
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.code").value("INVENTORY_CONFLICT"))
                    .andExpect(jsonPath("$.fields.rental").value("Selected rental vehicle is no longer available for the requested interval."));

            int seatsAfter = getAvailableSeats("airfare-out-sfo-d1", "2027-03-10");
            assertEquals(seatsBefore, seatsAfter, "Flight seats must not be modified when rental fails");
        } finally {
            // Clean up overlapping occupancy
            jdbc.update("DELETE FROM rental_unit_occupancy WHERE rental_unit_id = ?", rentalUnitId);
        }
    }

    @Test
    void reportsMultipleExhaustedComponentsSimultaneously() throws Exception {
        Client owner = register("multi-exhaust@example.test");
        MvcResult created = owner.unsafe(post("/api/trips"),
                "{\"destinationKey\":\"destination-sfo\",\"startDate\":\"2027-03-10\",\"endDate\":\"2027-03-14\",\"travelerCount\":2,\"travelerAges\":[25,25],\"budgetCents\":500000}")
                .andExpect(status().isCreated()).andReturn();
        String tripId = jsonField(created, "id");
        String draftId = getDraftId(created, 0);

        insertSfoAirfareSelection(draftId);
        insertSfoStaySelection(draftId);

        MvcResult planRes = owner.unsafe(post("/api/trips/{tripId}/drafts/{draftId}/plan", tripId, draftId),
                "{\"expectedVersion\":0,\"expectedDraftVersion\":0}")
                .andExpect(status().isCreated()).andReturn();
        String plannedId = getPlannedId(planRes, 0);

        int initialSeats = getAvailableSeats("airfare-out-sfo-d1", "2027-03-10");
        int initialStayInv = jdbc.queryForObject("SELECT available_inventory FROM accommodation_nightly_inventory WHERE night_date = DATE '2027-03-12' AND accommodation_unit_id = (SELECT id FROM accommodation_unit WHERE catalog_key = 'stay-unit-sfo-hotel-summit')", Integer.class);
        try {
            // Deplete both airfare and stay
            jdbc.update("UPDATE flight_instance SET available_seats = 0 WHERE service_date = DATE '2027-03-10' AND flight_schedule_id = (SELECT id FROM flight_schedule WHERE catalog_key = 'airfare-out-sfo-d1')");
            jdbc.update("UPDATE accommodation_nightly_inventory SET available_inventory = 0 WHERE night_date = DATE '2027-03-12' AND accommodation_unit_id = (SELECT id FROM accommodation_unit WHERE catalog_key = 'stay-unit-sfo-hotel-summit')");

            owner.unsafe(post("/api/trips/{tripId}/bookings", tripId),
                    "{\"plannedItineraryId\":\"" + plannedId + "\",\"expectedVersion\":1,\"idempotencyKey\":\"multi-fail\"}")
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.code").value("INVENTORY_CONFLICT"))
                    .andExpect(jsonPath("$.fields.airfare").exists())
                    .andExpect(jsonPath("$.fields.stay").exists());
        } finally {
            // Restore
            jdbc.update("UPDATE flight_instance SET available_seats = ? WHERE service_date = DATE '2027-03-10' AND flight_schedule_id = (SELECT id FROM flight_schedule WHERE catalog_key = 'airfare-out-sfo-d1')", initialSeats);
            jdbc.update("UPDATE accommodation_nightly_inventory SET available_inventory = ? WHERE night_date = DATE '2027-03-12' AND accommodation_unit_id = (SELECT id FROM accommodation_unit WHERE catalog_key = 'stay-unit-sfo-hotel-summit')", initialStayInv);
        }
    }

    @Test
    void enforcesTripOwnerIsolation() throws Exception {
        Client userA = register("owner-a@example.test");
        Client userB = register("owner-b@example.test");

        MvcResult createdA = userA.unsafe(post("/api/trips"),
                "{\"destinationKey\":\"destination-sfo\",\"startDate\":\"2027-03-10\",\"endDate\":\"2027-03-14\",\"travelerCount\":2,\"travelerAges\":[25,25],\"budgetCents\":500000}")
                .andExpect(status().isCreated()).andReturn();
        String tripAId = jsonField(createdA, "id");
        String draftAId = getDraftId(createdA, 0);

        insertSfoAirfareSelection(draftAId);
        MvcResult planRes = userA.unsafe(post("/api/trips/{tripId}/drafts/{draftId}/plan", tripAId, draftAId),
                "{\"expectedVersion\":0,\"expectedDraftVersion\":0}")
                .andExpect(status().isCreated()).andReturn();
        String plannedId = getPlannedId(planRes, 0);

        // User B attempts to book User A's trip -> 404 RESOURCE_NOT_FOUND
        userB.unsafe(post("/api/trips/{tripId}/bookings", tripAId),
                "{\"plannedItineraryId\":\"" + plannedId + "\",\"expectedVersion\":1,\"idempotencyKey\":\"attacker-key\"}")
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("RESOURCE_NOT_FOUND"));

        // User B attempts to inspect User A's active booking -> 404 RESOURCE_NOT_FOUND
        userB.unsafe(get("/api/trips/{tripId}/bookings/active", tripAId), null)
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("RESOURCE_NOT_FOUND"));

        // User B attempts to inspect User A's booking history -> 404 RESOURCE_NOT_FOUND
        userB.unsafe(get("/api/trips/{tripId}/bookings", tripAId), null)
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("RESOURCE_NOT_FOUND"));
    }

    @Test
    void retrievesActiveBookingAndHistoryAndUpdatesProfile() throws Exception {
        Client owner = register("profile-and-active@example.test");
        MvcResult created = owner.unsafe(post("/api/trips"),
                "{\"destinationKey\":\"destination-sfo\",\"startDate\":\"2027-03-10\",\"endDate\":\"2027-03-14\",\"travelerCount\":2,\"travelerAges\":[25,25],\"budgetCents\":500000}")
                .andExpect(status().isCreated()).andReturn();
        String tripId = jsonField(created, "id");
        String draftId = getDraftId(created, 0);

        insertSfoAirfareSelection(draftId);
        MvcResult planRes = owner.unsafe(post("/api/trips/{tripId}/drafts/{draftId}/plan", tripId, draftId),
                "{\"expectedVersion\":0,\"expectedDraftVersion\":0}")
                .andExpect(status().isCreated()).andReturn();
        String plannedId = getPlannedId(planRes, 0);

        // Active booking absent before booking
        owner.unsafe(get("/api/trips/{tripId}/bookings/active", tripId), null)
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("RESOURCE_NOT_FOUND"));

        // History empty before booking
        owner.unsafe(get("/api/trips/{tripId}/bookings", tripId), null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));

        // Create booking
        owner.unsafe(post("/api/trips/{tripId}/bookings", tripId),
                "{\"plannedItineraryId\":\"" + plannedId + "\",\"expectedVersion\":1,\"idempotencyKey\":\"profile-test-booking\"}")
                .andExpect(status().isCreated());

        // Active booking present
        owner.unsafe(get("/api/trips/{tripId}/bookings/active", tripId), null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ACTIVE"))
                .andExpect(jsonPath("$.bookingReference").value(org.hamcrest.Matchers.matchesPattern("^DT-[A-Z0-9]{6}$")));

        // History contains 1 booking
        owner.unsafe(get("/api/trips/{tripId}/bookings", tripId), null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].status").value("ACTIVE"));

        // Profile summary reflects bookedCount: 1, hasBookingHistory: true, and primaryBookingReference
        owner.unsafe(get("/api/trips"), null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.upcoming[0].bookedCount").value(1))
                .andExpect(jsonPath("$.upcoming[0].hasBookingHistory").value(true))
                .andExpect(jsonPath("$.upcoming[0].primaryBookingReference").value(org.hamcrest.Matchers.matchesPattern("^DT-[A-Z0-9]{6}$")));

        // Trip deletion rejected because of booking history
        owner.unsafe(delete("/api/trips/{tripId}", tripId),
                "{\"expectedVersion\":2,\"expectedDraftCount\":0,\"expectedPlannedCount\":1,\"confirmed\":true}")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CANNOT_DELETE_BOOKED_TRIP"));
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
        org.junit.jupiter.api.Assertions.assertNotNull(csrf);
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

        private org.springframework.test.web.servlet.ResultActions unsafeWithHeader(
                org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder request,
                String body, String headerName, String headerValue) throws Exception {
            request.cookie(csrf).header("X-XSRF-TOKEN", csrf.getValue())
                    .header(headerName, headerValue)
                    .contentType(MediaType.APPLICATION_JSON);
            if (session != null) request.session(session);
            if (body != null) request.content(body);
            return mockMvc.perform(request);
        }
    }
}
