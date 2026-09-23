package app.detour.booking;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import app.detour.trip.TestClockConfiguration;
import jakarta.servlet.http.Cookie;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
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
class BookingConcurrencyIntegrationTest {
    private static final String DATABASE_URL = "jdbc:h2:mem:booking_concurrency_" + UUID.randomUUID() + ";DB_CLOSE_DELAY=-1";

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
    void flightSeatContentionRace() throws Exception {
        Client userA = register("concurr-flight-a@example.test");
        Client userB = register("concurr-flight-b@example.test");

        MvcResult tripARes = userA.unsafe(post("/api/trips"),
                "{\"destinationKey\":\"destination-sfo\",\"startDate\":\"2027-03-10\",\"endDate\":\"2027-03-14\",\"travelerCount\":1,\"travelerAges\":[30],\"budgetCents\":500000}")
                .andExpect(status().isCreated()).andReturn();
        String tripAId = jsonField(tripARes, "id");
        String draftAId = getDraftId(tripARes, 0);
        insertSfoAirfareSelection(draftAId);
        MvcResult planARes = userA.unsafe(post("/api/trips/{tripId}/drafts/{draftId}/plan", tripAId, draftAId),
                "{\"expectedVersion\":0,\"expectedDraftVersion\":0}")
                .andExpect(status().isCreated()).andReturn();
        String plannedAId = getPlannedId(planARes, 0);

        MvcResult tripBRes = userB.unsafe(post("/api/trips"),
                "{\"destinationKey\":\"destination-sfo\",\"startDate\":\"2027-03-10\",\"endDate\":\"2027-03-14\",\"travelerCount\":1,\"travelerAges\":[30],\"budgetCents\":500000}")
                .andExpect(status().isCreated()).andReturn();
        String tripBId = jsonField(tripBRes, "id");
        String draftBId = getDraftId(tripBRes, 0);
        insertSfoAirfareSelection(draftBId);
        MvcResult planBRes = userB.unsafe(post("/api/trips/{tripId}/drafts/{draftId}/plan", tripBId, draftBId),
                "{\"expectedVersion\":0,\"expectedDraftVersion\":0}")
                .andExpect(status().isCreated()).andReturn();
        String plannedBId = getPlannedId(planBRes, 0);

        // Set available seats to exactly 1
        jdbc.update("UPDATE flight_instance SET available_seats = 1 WHERE service_date = DATE '2027-03-10' AND flight_schedule_id = (SELECT id FROM flight_schedule WHERE catalog_key = 'airfare-out-sfo-d1')");

        CyclicBarrier barrier = new CyclicBarrier(2);
        try (ExecutorService executor = Executors.newFixedThreadPool(2)) {
            Future<Integer> f1 = executor.submit(() -> {
                barrier.await();
                return userA.unsafe(post("/api/trips/{tripId}/bookings", tripAId),
                        "{\"plannedItineraryId\":\"" + plannedAId + "\",\"expectedVersion\":1,\"idempotencyKey\":\"concurr-air-a\"}")
                        .andReturn().getResponse().getStatus();
            });
            Future<Integer> f2 = executor.submit(() -> {
                barrier.await();
                return userB.unsafe(post("/api/trips/{tripId}/bookings", tripBId),
                        "{\"plannedItineraryId\":\"" + plannedBId + "\",\"expectedVersion\":1,\"idempotencyKey\":\"concurr-air-b\"}")
                        .andReturn().getResponse().getStatus();
            });

            int s1 = f1.get();
            int s2 = f2.get();
            assertEquals(1, List.of(s1, s2).stream().filter(s -> s == 201).count(), "Exactly one booking should succeed (201)");
            assertEquals(1, List.of(s1, s2).stream().filter(s -> s == 409).count(), "Exactly one booking should fail with 409 conflict");
        }

        int finalSeats = jdbc.queryForObject(
                "SELECT available_seats FROM flight_instance WHERE service_date = DATE '2027-03-10' AND flight_schedule_id = (SELECT id FROM flight_schedule WHERE catalog_key = 'airfare-out-sfo-d1')",
                Integer.class);
        assertEquals(0, finalSeats, "Seats should be decremented to exactly 0");

        int totalBookings = jdbc.queryForObject("SELECT COUNT(*) FROM detour_booking", Integer.class);
        assertEquals(1, totalBookings, "Only 1 booking should be persisted");
    }

    @Test
    void stayNightContentionRace() throws Exception {
        Client userA = register("concurr-stay-a@example.test");
        Client userB = register("concurr-stay-b@example.test");

        MvcResult tripARes = userA.unsafe(post("/api/trips"),
                "{\"destinationKey\":\"destination-sfo\",\"startDate\":\"2027-03-10\",\"endDate\":\"2027-03-14\",\"travelerCount\":2,\"travelerAges\":[25,25],\"budgetCents\":500000}")
                .andExpect(status().isCreated()).andReturn();
        String tripAId = jsonField(tripARes, "id");
        String draftAId = getDraftId(tripARes, 0);
        insertSfoAirfareSelection(draftAId);
        insertSfoStaySelection(draftAId);
        MvcResult planARes = userA.unsafe(post("/api/trips/{tripId}/drafts/{draftId}/plan", tripAId, draftAId),
                "{\"expectedVersion\":0,\"expectedDraftVersion\":0}")
                .andExpect(status().isCreated()).andReturn();
        String plannedAId = getPlannedId(planARes, 0);

        MvcResult tripBRes = userB.unsafe(post("/api/trips"),
                "{\"destinationKey\":\"destination-sfo\",\"startDate\":\"2027-03-10\",\"endDate\":\"2027-03-14\",\"travelerCount\":2,\"travelerAges\":[25,25],\"budgetCents\":500000}")
                .andExpect(status().isCreated()).andReturn();
        String tripBId = jsonField(tripBRes, "id");
        String draftBId = getDraftId(tripBRes, 0);
        insertSfoAirfareSelection(draftBId);
        insertSfoStaySelection(draftBId);
        MvcResult planBRes = userB.unsafe(post("/api/trips/{tripId}/drafts/{draftId}/plan", tripBId, draftBId),
                "{\"expectedVersion\":0,\"expectedDraftVersion\":0}")
                .andExpect(status().isCreated()).andReturn();
        String plannedBId = getPlannedId(planBRes, 0);

        // Constrain stay night inventory for 2027-03-11 to 1 unit
        jdbc.update("UPDATE accommodation_nightly_inventory SET available_inventory = 1 WHERE night_date = DATE '2027-03-11' AND accommodation_unit_id = (SELECT id FROM accommodation_unit WHERE catalog_key = 'stay-unit-sfo-hotel-summit')");

        CyclicBarrier barrier = new CyclicBarrier(2);
        try (ExecutorService executor = Executors.newFixedThreadPool(2)) {
            Future<Integer> f1 = executor.submit(() -> {
                barrier.await();
                return userA.unsafe(post("/api/trips/{tripId}/bookings", tripAId),
                        "{\"plannedItineraryId\":\"" + plannedAId + "\",\"expectedVersion\":1,\"idempotencyKey\":\"concurr-stay-a\"}")
                        .andReturn().getResponse().getStatus();
            });
            Future<Integer> f2 = executor.submit(() -> {
                barrier.await();
                return userB.unsafe(post("/api/trips/{tripId}/bookings", tripBId),
                        "{\"plannedItineraryId\":\"" + plannedBId + "\",\"expectedVersion\":1,\"idempotencyKey\":\"concurr-stay-b\"}")
                        .andReturn().getResponse().getStatus();
            });

            int s1 = f1.get();
            int s2 = f2.get();
            assertEquals(1, List.of(s1, s2).stream().filter(s -> s == 201).count());
            assertEquals(1, List.of(s1, s2).stream().filter(s -> s == 409).count());
        }

        int finalInventory = jdbc.queryForObject(
                "SELECT available_inventory FROM accommodation_nightly_inventory WHERE night_date = DATE '2027-03-11' AND accommodation_unit_id = (SELECT id FROM accommodation_unit WHERE catalog_key = 'stay-unit-sfo-hotel-summit')",
                Integer.class);
        assertEquals(0, finalInventory, "Constrained night inventory must end at 0");

        int totalBookings = jdbc.queryForObject("SELECT COUNT(*) FROM detour_booking", Integer.class);
        assertEquals(1, totalBookings);
    }

    @Test
    void rentalCarContentionRace() throws Exception {
        Client userA = register("concurr-rental-a@example.test");
        Client userB = register("concurr-rental-b@example.test");

        MvcResult tripARes = userA.unsafe(post("/api/trips"),
                "{\"destinationKey\":\"destination-sfo\",\"startDate\":\"2027-03-10\",\"endDate\":\"2027-03-14\",\"travelerCount\":2,\"travelerAges\":[25,25],\"budgetCents\":500000}")
                .andExpect(status().isCreated()).andReturn();
        String tripAId = jsonField(tripARes, "id");
        String draftAId = getDraftId(tripARes, 0);
        insertSfoAirfareSelection(draftAId);
        insertSfoRentalSelection(draftAId);
        MvcResult planARes = userA.unsafe(post("/api/trips/{tripId}/drafts/{draftId}/plan", tripAId, draftAId),
                "{\"expectedVersion\":0,\"expectedDraftVersion\":0}")
                .andExpect(status().isCreated()).andReturn();
        String plannedAId = getPlannedId(planARes, 0);

        MvcResult tripBRes = userB.unsafe(post("/api/trips"),
                "{\"destinationKey\":\"destination-sfo\",\"startDate\":\"2027-03-10\",\"endDate\":\"2027-03-14\",\"travelerCount\":2,\"travelerAges\":[25,25],\"budgetCents\":500000}")
                .andExpect(status().isCreated()).andReturn();
        String tripBId = jsonField(tripBRes, "id");
        String draftBId = getDraftId(tripBRes, 0);
        insertSfoAirfareSelection(draftBId);
        insertSfoRentalSelection(draftBId);
        MvcResult planBRes = userB.unsafe(post("/api/trips/{tripId}/drafts/{draftId}/plan", tripBId, draftBId),
                "{\"expectedVersion\":0,\"expectedDraftVersion\":0}")
                .andExpect(status().isCreated()).andReturn();
        String plannedBId = getPlannedId(planBRes, 0);

        CyclicBarrier barrier = new CyclicBarrier(2);
        try (ExecutorService executor = Executors.newFixedThreadPool(2)) {
            Future<Integer> f1 = executor.submit(() -> {
                barrier.await();
                return userA.unsafe(post("/api/trips/{tripId}/bookings", tripAId),
                        "{\"plannedItineraryId\":\"" + plannedAId + "\",\"expectedVersion\":1,\"idempotencyKey\":\"concurr-rent-a\"}")
                        .andReturn().getResponse().getStatus();
            });
            Future<Integer> f2 = executor.submit(() -> {
                barrier.await();
                return userB.unsafe(post("/api/trips/{tripId}/bookings", tripBId),
                        "{\"plannedItineraryId\":\"" + plannedBId + "\",\"expectedVersion\":1,\"idempotencyKey\":\"concurr-rent-b\"}")
                        .andReturn().getResponse().getStatus();
            });

            int s1 = f1.get();
            int s2 = f2.get();
            assertEquals(1, List.of(s1, s2).stream().filter(s -> s == 201).count());
            assertEquals(1, List.of(s1, s2).stream().filter(s -> s == 409).count());
        }

        int activeOccupancies = jdbc.queryForObject(
                "SELECT COUNT(*) FROM rental_unit_occupancy WHERE rental_unit_id = (SELECT id FROM rental_unit WHERE catalog_key = 'rental-unit-sfo-economy-01') AND occupancy_status = 'ACTIVE'",
                Integer.class);
        assertEquals(1, activeOccupancies, "Exactly 1 active rental occupancy should exist");

        int totalBookings = jdbc.queryForObject("SELECT COUNT(*) FROM detour_booking", Integer.class);
        assertEquals(1, totalBookings);
    }

    @Test
    void concurrentDuplicateIdempotencyRace() throws Exception {
        Client user = register("concurr-idemp@example.test");

        MvcResult tripRes = user.unsafe(post("/api/trips"),
                "{\"destinationKey\":\"destination-sfo\",\"startDate\":\"2027-03-10\",\"endDate\":\"2027-03-14\",\"travelerCount\":2,\"travelerAges\":[25,25],\"budgetCents\":500000}")
                .andExpect(status().isCreated()).andReturn();
        String tripId = jsonField(tripRes, "id");
        String draftId = getDraftId(tripRes, 0);
        insertSfoAirfareSelection(draftId);
        MvcResult planRes = user.unsafe(post("/api/trips/{tripId}/drafts/{draftId}/plan", tripId, draftId),
                "{\"expectedVersion\":0,\"expectedDraftVersion\":0}")
                .andExpect(status().isCreated()).andReturn();
        String plannedId = getPlannedId(planRes, 0);

        int initialSeats = jdbc.queryForObject(
                "SELECT available_seats FROM flight_instance WHERE service_date = DATE '2027-03-10' AND flight_schedule_id = (SELECT id FROM flight_schedule WHERE catalog_key = 'airfare-out-sfo-d1')",
                Integer.class);

        CyclicBarrier barrier = new CyclicBarrier(2);
        try (ExecutorService executor = Executors.newFixedThreadPool(2)) {
            Future<MvcResult> f1 = executor.submit(() -> {
                barrier.await();
                return user.unsafe(post("/api/trips/{tripId}/bookings", tripId),
                        "{\"plannedItineraryId\":\"" + plannedId + "\",\"expectedVersion\":1,\"idempotencyKey\":\"racing-same-key\"}")
                        .andReturn();
            });
            Future<MvcResult> f2 = executor.submit(() -> {
                barrier.await();
                return user.unsafe(post("/api/trips/{tripId}/bookings", tripId),
                        "{\"plannedItineraryId\":\"" + plannedId + "\",\"expectedVersion\":1,\"idempotencyKey\":\"racing-same-key\"}")
                        .andReturn();
            });

            MvcResult r1 = f1.get();
            MvcResult r2 = f2.get();

            int s1 = r1.getResponse().getStatus();
            int s2 = r2.getResponse().getStatus();
            assertTrue(s1 == 201 || s1 == 200, "Thread 1 status must be 201 or 200 but was " + s1);
            assertTrue(s2 == 201 || s2 == 200, "Thread 2 status must be 201 or 200 but was " + s2);

            String ref1 = jsonField(r1, "bookingReference");
            String ref2 = jsonField(r2, "bookingReference");
            assertNotNull(ref1);
            assertEquals(ref1, ref2, "Both threads must receive the same booking reference");
        }

        int totalBookings = jdbc.queryForObject("SELECT COUNT(*) FROM detour_booking WHERE trip_id = (SELECT id FROM detour_trip WHERE public_id = ?)", Integer.class, UUID.fromString(tripId));
        assertEquals(1, totalBookings, "Exactly 1 booking row should be created");

        int finalSeats = jdbc.queryForObject(
                "SELECT available_seats FROM flight_instance WHERE service_date = DATE '2027-03-10' AND flight_schedule_id = (SELECT id FROM flight_schedule WHERE catalog_key = 'airfare-out-sfo-d1')",
                Integer.class);
        assertEquals(initialSeats - 2, finalSeats, "Seats should be decremented by travelerCount (2), not double-decremented");
    }

    @Test
    void concurrentDuplicateCancellationRequestsSucceedsExactlyOnce() throws Exception {
        Client user = register("concurr-dup-cancel@example.test");

        MvcResult tripRes = user.unsafe(post("/api/trips"),
                "{\"destinationKey\":\"destination-sfo\",\"startDate\":\"2027-03-10\",\"endDate\":\"2027-03-14\",\"travelerCount\":2,\"travelerAges\":[25,25],\"budgetCents\":500000}")
                .andExpect(status().isCreated()).andReturn();
        String tripId = jsonField(tripRes, "id");
        String draftId = getDraftId(tripRes, 0);
        insertSfoAirfareSelection(draftId);
        insertSfoStaySelection(draftId);
        insertSfoRentalSelection(draftId);

        MvcResult planRes = user.unsafe(post("/api/trips/{tripId}/drafts/{draftId}/plan", tripId, draftId),
                "{\"expectedVersion\":0,\"expectedDraftVersion\":0}")
                .andExpect(status().isCreated()).andReturn();
        String plannedId = getPlannedId(planRes, 0);

        MvcResult bookRes = user.unsafe(post("/api/trips/{tripId}/bookings", tripId),
                "{\"plannedItineraryId\":\"" + plannedId + "\",\"expectedVersion\":1,\"idempotencyKey\":\"concurr-dup-cancel-book\"}")
                .andExpect(status().isCreated()).andReturn();
        String bookingId = jsonField(bookRes, "id");

        int initialSeats = jdbc.queryForObject(
                "SELECT seat_capacity FROM flight_instance WHERE service_date = DATE '2027-03-10' AND flight_schedule_id = (SELECT id FROM flight_schedule WHERE catalog_key = 'airfare-out-sfo-d1')",
                Integer.class);
        int initialStay = jdbc.queryForObject(
                "SELECT inventory_capacity FROM accommodation_nightly_inventory WHERE accommodation_unit_id = (SELECT id FROM accommodation_unit WHERE catalog_key = 'stay-unit-sfo-hotel-summit') AND night_date = DATE '2027-03-10'",
                Integer.class);

        CyclicBarrier barrier = new CyclicBarrier(2);
        try (ExecutorService executor = Executors.newFixedThreadPool(2)) {
            Future<Integer> f1 = executor.submit(() -> {
                barrier.await();
                return user.unsafe(post("/api/trips/{tripId}/bookings/{bookingId}/cancel", tripId, bookingId),
                        "{\"expectedVersion\":2}")
                        .andReturn().getResponse().getStatus();
            });
            Future<Integer> f2 = executor.submit(() -> {
                barrier.await();
                return user.unsafe(post("/api/trips/{tripId}/bookings/{bookingId}/cancel", tripId, bookingId),
                        "{\"expectedVersion\":2}")
                        .andReturn().getResponse().getStatus();
            });

            int s1 = f1.get();
            int s2 = f2.get();

            assertEquals(1, List.of(s1, s2).stream().filter(s -> s == 200).count(), "Exactly one cancel request should succeed (200)");
            assertEquals(1, List.of(s1, s2).stream().filter(s -> s == 409).count(), "Exactly one cancel request should conflict (409)");
        }

        // Seats restored to initial capacity, NOT double-restored
        int finalSeats = jdbc.queryForObject(
                "SELECT available_seats FROM flight_instance WHERE service_date = DATE '2027-03-10' AND flight_schedule_id = (SELECT id FROM flight_schedule WHERE catalog_key = 'airfare-out-sfo-d1')",
                Integer.class);
        assertEquals(initialSeats, finalSeats, "Seats should be restored to initial capacity exactly once");

        int finalStay = jdbc.queryForObject(
                "SELECT available_inventory FROM accommodation_nightly_inventory WHERE accommodation_unit_id = (SELECT id FROM accommodation_unit WHERE catalog_key = 'stay-unit-sfo-hotel-summit') AND night_date = DATE '2027-03-10'",
                Integer.class);
        assertEquals(initialStay, finalStay, "Stay inventory should be restored to initial capacity exactly once");

        int activeOccupancies = jdbc.queryForObject(
                "SELECT COUNT(*) FROM rental_unit_occupancy WHERE rental_unit_id = (SELECT id FROM rental_unit WHERE catalog_key = 'rental-unit-sfo-economy-01') AND occupancy_status = 'ACTIVE'",
                Integer.class);
        assertEquals(0, activeOccupancies, "Rental occupancy should be RELEASED");

        int totalBookings = jdbc.queryForObject("SELECT COUNT(*) FROM detour_booking WHERE trip_id = (SELECT id FROM detour_trip WHERE public_id = ?)", Integer.class, UUID.fromString(tripId));
        assertEquals(1, totalBookings);

        String bookingStatus = jdbc.queryForObject("SELECT status FROM detour_booking WHERE public_id = ?", String.class, UUID.fromString(bookingId));
        assertEquals("CANCELED", bookingStatus);

        int tripVersion = jdbc.queryForObject("SELECT version FROM detour_trip WHERE public_id = ?", Integer.class, UUID.fromString(tripId));
        assertEquals(3, tripVersion, "Trip version should advance from 2 to 3 exactly once");
    }

    @Test
    void concurrentCancellationAndRebookingMaintainsExactInventoryConsistency() throws Exception {
        Client user = register("concurr-cancel-rebook@example.test");

        MvcResult tripRes = user.unsafe(post("/api/trips"),
                "{\"destinationKey\":\"destination-sfo\",\"startDate\":\"2027-03-10\",\"endDate\":\"2027-03-14\",\"travelerCount\":2,\"travelerAges\":[25,25],\"budgetCents\":500000}")
                .andExpect(status().isCreated()).andReturn();
        String tripId = jsonField(tripRes, "id");
        String draftId = getDraftId(tripRes, 0);
        insertSfoAirfareSelection(draftId);
        insertSfoStaySelection(draftId);
        insertSfoRentalSelection(draftId);

        MvcResult planRes = user.unsafe(post("/api/trips/{tripId}/drafts/{draftId}/plan", tripId, draftId),
                "{\"expectedVersion\":0,\"expectedDraftVersion\":0}")
                .andExpect(status().isCreated()).andReturn();
        String plannedId = getPlannedId(planRes, 0);

        MvcResult bookRes = user.unsafe(post("/api/trips/{tripId}/bookings", tripId),
                "{\"plannedItineraryId\":\"" + plannedId + "\",\"expectedVersion\":1,\"idempotencyKey\":\"concurr-rebook-orig\"}")
                .andExpect(status().isCreated()).andReturn();
        String bookingId = jsonField(bookRes, "id");

        int initialSeats = jdbc.queryForObject(
                "SELECT seat_capacity FROM flight_instance WHERE service_date = DATE '2027-03-10' AND flight_schedule_id = (SELECT id FROM flight_schedule WHERE catalog_key = 'airfare-out-sfo-d1')",
                Integer.class);

        CyclicBarrier barrier = new CyclicBarrier(2);
        try (ExecutorService executor = Executors.newFixedThreadPool(2)) {
            // Thread 1: cancel active booking
            Future<Integer> fCancel = executor.submit(() -> {
                barrier.await();
                return user.unsafe(post("/api/trips/{tripId}/bookings/{bookingId}/cancel", tripId, bookingId),
                        "{\"expectedVersion\":2}")
                        .andReturn().getResponse().getStatus();
            });
            // Thread 2: attempt to book concurrently with expectedVersion 2
            Future<Integer> fRebook = executor.submit(() -> {
                barrier.await();
                return user.unsafe(post("/api/trips/{tripId}/bookings", tripId),
                        "{\"plannedItineraryId\":\"" + plannedId + "\",\"expectedVersion\":2,\"idempotencyKey\":\"concurr-racing-rebook\"}")
                        .andReturn().getResponse().getStatus();
            });

            int cancelStatus = fCancel.get();
            int rebookStatus = fRebook.get();

            assertEquals(200, cancelStatus, "Cancellation should succeed with 200");
            assertEquals(409, rebookStatus, "Concurrent rebooking with expectedVersion 2 must conflict with 409 (ALREADY_BOOKED or VERSION_CONFLICT)");
        }

        int finalSeats = jdbc.queryForObject(
                "SELECT available_seats FROM flight_instance WHERE service_date = DATE '2027-03-10' AND flight_schedule_id = (SELECT id FROM flight_schedule WHERE catalog_key = 'airfare-out-sfo-d1')",
                Integer.class);
        assertEquals(initialSeats, finalSeats, "Inventory must be fully restored and never leaked");

        int activeBookings = jdbc.queryForObject(
                "SELECT COUNT(*) FROM detour_booking WHERE trip_id = (SELECT id FROM detour_trip WHERE public_id = ?) AND status = 'ACTIVE'",
                Integer.class, UUID.fromString(tripId));
        assertEquals(0, activeBookings, "Zero active bookings should exist");

        int activeOccupancies = jdbc.queryForObject(
                "SELECT COUNT(*) FROM rental_unit_occupancy WHERE rental_unit_id = (SELECT id FROM rental_unit WHERE catalog_key = 'rental-unit-sfo-economy-01') AND occupancy_status = 'ACTIVE'",
                Integer.class);
        assertEquals(0, activeOccupancies, "Zero active rental occupancies should exist");
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
                                                                          String json) throws Exception {
            if (session != null) request.session(session);
            request.cookie(csrf).header("X-XSRF-TOKEN", csrf.getValue());
            if (json != null) request.contentType(MediaType.APPLICATION_JSON).content(json);
            return mockMvc.perform(request);
        }
    }
}
