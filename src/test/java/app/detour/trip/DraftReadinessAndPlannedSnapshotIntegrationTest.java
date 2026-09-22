package app.detour.trip;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import app.detour.common.ClockConfiguration;
import jakarta.servlet.http.Cookie;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.AfterEach;
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
class DraftReadinessAndPlannedSnapshotIntegrationTest {
    private static final String DATABASE_URL = "jdbc:h2:mem:readiness_snap_" + UUID.randomUUID() + ";DB_CLOSE_DELAY=-1";

    @DynamicPropertySource
    static void datasourceProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", () -> DATABASE_URL);
    }

    @Autowired private MockMvc mockMvc;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private TestClockConfiguration.TestClock testClock;

    @AfterEach
    void resetClock() {
        testClock.reset();
    }

    @Test
    void readinessEndpointReturnsCompleteStatusForReadyDraftWithinBudget() throws Exception {
        Client owner = register("ready-within-budget@example.test");
        MvcResult created = owner.unsafe(post("/api/trips"),
                "{\"destinationKey\":\"destination-sfo\",\"startDate\":\"2027-03-10\",\"endDate\":\"2027-03-14\",\"travelerCount\":2,\"travelerAges\":[25,25],\"budgetCents\":500000}")
                .andExpect(status().isCreated()).andReturn();
        String tripId = jsonField(created, "id");
        String draftId = getDraftId(created, 0);

        insertSfoAirfareSelection(draftId);
        insertSfoStaySelection(draftId);

        owner.unsafe(get("/api/trips/{tripId}/drafts/{draftId}/readiness", tripId, draftId), null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ready").value(true))
                .andExpect(jsonPath("$.blockingIssues").isEmpty())
                .andExpect(jsonPath("$.isOverBudget").value(false))
                .andExpect(jsonPath("$.budgetOverageCents").value(0))
                .andExpect(jsonPath("$.requiresOverageAcknowledgment").value(false));
    }

    @Test
    void reportsAllMissingFieldReadinessIssuesTogether() throws Exception {
        Client owner = register("missing-readiness@example.test");
        // Trip with minors only, null budget, and no components
        MvcResult created = owner.unsafe(post("/api/trips"),
                "{\"destinationKey\":\"destination-sfo\",\"startDate\":\"2027-03-10\",\"endDate\":\"2027-03-14\",\"travelerCount\":2,\"travelerAges\":[16,14],\"budgetCents\":null}")
                .andExpect(status().isCreated()).andReturn();
        String tripId = jsonField(created, "id");
        String draftId = getDraftId(created, 0);

        // Readiness endpoint reports all issues simultaneously
        owner.unsafe(get("/api/trips/{tripId}/drafts/{draftId}/readiness", tripId, draftId), null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ready").value(false))
                .andExpect(jsonPath("$.blockingIssues.adult").value("At least one traveler must be an adult before planning."))
                .andExpect(jsonPath("$.blockingIssues.components").value("Select at least one structurally valid reservable component before planning."))
                .andExpect(jsonPath("$.blockingIssues.budgetCents").value("Provide a budget before planning."));

        // Promotion endpoint fails with 400 PLANNING_NOT_READY and returns all issues in fields
        owner.unsafe(post("/api/trips/{tripId}/drafts/{draftId}/plan", tripId, draftId),
                "{\"expectedVersion\":0,\"expectedDraftVersion\":0}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("PLANNING_NOT_READY"))
                .andExpect(jsonPath("$.fields.adult").exists())
                .andExpect(jsonPath("$.fields.components").exists())
                .andExpect(jsonPath("$.fields.budgetCents").exists());
    }

    @Test
    void rejectsPromotionWhenCatalogComponentsAreSoldOutOrUnavailable() throws Exception {
        Client owner = register("sold-out-check@example.test");
        MvcResult created = owner.unsafe(post("/api/trips"),
                "{\"destinationKey\":\"destination-sfo\",\"startDate\":\"2027-03-10\",\"endDate\":\"2027-03-14\",\"travelerCount\":2,\"travelerAges\":[25,25],\"budgetCents\":500000}")
                .andExpect(status().isCreated()).andReturn();
        String tripId = jsonField(created, "id");
        String draftId = getDraftId(created, 0);

        insertSfoAirfareSelection(draftId);
        insertSfoStaySelection(draftId);
        insertSfoRentalSelection(draftId);

        // 1. Airfare seats depleted (< travelerCount)
        jdbc.update("UPDATE flight_instance SET available_seats = 1 WHERE id = (SELECT outbound_flight_instance_id FROM detour_trip_draft_airfare_selection WHERE draft_id = (SELECT id FROM detour_trip_draft WHERE public_id = ?))", UUID.fromString(draftId));
        owner.unsafe(post("/api/trips/{tripId}/drafts/{draftId}/plan", tripId, draftId),
                "{\"expectedVersion\":0,\"expectedDraftVersion\":0}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("PLANNING_NOT_READY"))
                .andExpect(jsonPath("$.fields.airfare").value("Selected flight does not have enough available seats for party size."));
        owner.unsafe(get("/api/trips/{tripId}/drafts/{draftId}/readiness", tripId, draftId), null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ready").value(false))
                .andExpect(jsonPath("$.blockingIssues.airfare").value("Selected flight does not have enough available seats for party size."));
        jdbc.update("UPDATE flight_instance SET available_seats = 48 WHERE id = (SELECT outbound_flight_instance_id FROM detour_trip_draft_airfare_selection WHERE draft_id = (SELECT id FROM detour_trip_draft WHERE public_id = ?))", UUID.fromString(draftId));

        // 2. Stay inventory depleted (< unitCount)
        jdbc.update("UPDATE accommodation_nightly_inventory SET available_inventory = 0 WHERE accommodation_unit_id = (SELECT accommodation_unit_id FROM detour_trip_draft_stay_selection WHERE draft_id = (SELECT id FROM detour_trip_draft WHERE public_id = ?))", UUID.fromString(draftId));
        owner.unsafe(post("/api/trips/{tripId}/drafts/{draftId}/plan", tripId, draftId),
                "{\"expectedVersion\":0,\"expectedDraftVersion\":0}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("PLANNING_NOT_READY"))
                .andExpect(jsonPath("$.fields.stay").value("Selected accommodation has insufficient inventory for the requested dates."));
        jdbc.update("UPDATE accommodation_nightly_inventory SET available_inventory = inventory_capacity WHERE accommodation_unit_id = (SELECT accommodation_unit_id FROM detour_trip_draft_stay_selection WHERE draft_id = (SELECT id FROM detour_trip_draft WHERE public_id = ?))", UUID.fromString(draftId));

        // 3. Stay capacity exceeded
        Client capacityOwner = register("stay-capacity-check@example.test");
        MvcResult capacityTrip = capacityOwner.unsafe(post("/api/trips"),
                "{\"destinationKey\":\"destination-sfo\",\"startDate\":\"2027-03-10\",\"endDate\":\"2027-03-14\",\"travelerCount\":5,\"travelerAges\":[30,28,25,20,19],\"budgetCents\":500000}")
                .andExpect(status().isCreated()).andReturn();
        String capTripId = jsonField(capacityTrip, "id");
        String capDraftId = getDraftId(capacityTrip, 0);
        insertSfoStaySelection(capDraftId); // guest capacity = 4, party = 5
        capacityOwner.unsafe(post("/api/trips/{tripId}/drafts/{draftId}/plan", capTripId, capDraftId),
                "{\"expectedVersion\":0,\"expectedDraftVersion\":0}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("PLANNING_NOT_READY"))
                .andExpect(jsonPath("$.fields.stay").value("Selected accommodation unit capacity is insufficient for party size."));

        // 4. Rental driver age < 25
        Client rentalAgeOwner = register("rental-driver-age@example.test");
        MvcResult under25Trip = rentalAgeOwner.unsafe(post("/api/trips"),
                "{\"destinationKey\":\"destination-sfo\",\"startDate\":\"2027-03-10\",\"endDate\":\"2027-03-14\",\"travelerCount\":2,\"travelerAges\":[24,22],\"budgetCents\":500000}")
                .andExpect(status().isCreated()).andReturn();
        String under25TripId = jsonField(under25Trip, "id");
        String under25DraftId = getDraftId(under25Trip, 0);
        insertSfoRentalSelection(under25DraftId);
        rentalAgeOwner.unsafe(post("/api/trips/{tripId}/drafts/{draftId}/plan", under25TripId, under25DraftId),
                "{\"expectedVersion\":0,\"expectedDraftVersion\":0}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("PLANNING_NOT_READY"))
                .andExpect(jsonPath("$.fields.rental").value("At least one traveler must be 25 or older to rent a vehicle."));

        // 5. Rental pickup date outside trip interval
        Client rentalDateOwner = register("rental-dates@example.test");
        MvcResult dateTrip = rentalDateOwner.unsafe(post("/api/trips"),
                "{\"destinationKey\":\"destination-sfo\",\"startDate\":\"2027-03-10\",\"endDate\":\"2027-03-14\",\"travelerCount\":2,\"travelerAges\":[30,25],\"budgetCents\":500000}")
                .andExpect(status().isCreated()).andReturn();
        String dateTripId = jsonField(dateTrip, "id");
        String dateDraftId = getDraftId(dateTrip, 0);
        insertSfoRentalSelection(dateDraftId);
        jdbc.update("UPDATE detour_trip_draft_rental_selection SET pickup_at = TIMESTAMP WITH TIME ZONE '2027-03-08 10:00:00+00' WHERE draft_id = (SELECT id FROM detour_trip_draft WHERE public_id = ?)", UUID.fromString(dateDraftId));
        rentalDateOwner.unsafe(post("/api/trips/{tripId}/drafts/{draftId}/plan", dateTripId, dateDraftId),
                "{\"expectedVersion\":0,\"expectedDraftVersion\":0}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("PLANNING_NOT_READY"))
                .andExpect(jsonPath("$.fields.rental").value("Rental dates must be within the trip interval."));

        // 6. Rental active occupancy overlap
        Client rentalOccOwner = register("rental-occupancy@example.test");
        MvcResult occTrip = rentalOccOwner.unsafe(post("/api/trips"),
                "{\"destinationKey\":\"destination-sfo\",\"startDate\":\"2027-03-10\",\"endDate\":\"2027-03-14\",\"travelerCount\":2,\"travelerAges\":[30,25],\"budgetCents\":500000}")
                .andExpect(status().isCreated()).andReturn();
        String occTripId = jsonField(occTrip, "id");
        String occDraftId = getDraftId(occTrip, 0);
        insertSfoRentalSelection(occDraftId);
        jdbc.update("""
                INSERT INTO rental_unit_occupancy (rental_unit_id, pickup_at, return_at, occupancy_status)
                VALUES ((SELECT rental_unit_id FROM detour_trip_draft_rental_selection WHERE draft_id = (SELECT id FROM detour_trip_draft WHERE public_id = ?)),
                    TIMESTAMP WITH TIME ZONE '2027-03-11 10:00:00+00', TIMESTAMP WITH TIME ZONE '2027-03-13 12:00:00+00', 'ACTIVE')
                """, UUID.fromString(occDraftId));
        rentalOccOwner.unsafe(post("/api/trips/{tripId}/drafts/{draftId}/plan", occTripId, occDraftId),
                "{\"expectedVersion\":0,\"expectedDraftVersion\":0}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("PLANNING_NOT_READY"))
                .andExpect(jsonPath("$.fields.rental").value("Selected rental car is not available for the requested interval."));
    }

    @Test
    void enforcesBudgetOverageAcknowledgmentOnPromotion() throws Exception {
        Client owner = register("overage-enforcement@example.test");
        MvcResult created = owner.unsafe(post("/api/trips"),
                "{\"destinationKey\":\"destination-sfo\",\"startDate\":\"2027-03-10\",\"endDate\":\"2027-03-14\",\"travelerCount\":2,\"travelerAges\":[25,25],\"budgetCents\":50000}")
                .andExpect(status().isCreated()).andReturn();
        String tripId = jsonField(created, "id");
        String draftId = getDraftId(created, 0);

        insertSfoAirfareSelection(draftId);

        // 1. Omitted acknowledgment
        owner.unsafe(post("/api/trips/{tripId}/drafts/{draftId}/plan", tripId, draftId),
                "{\"expectedVersion\":0,\"expectedDraftVersion\":0}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("BUDGET_OVERAGE_UNACKNOWLEDGED"))
                .andExpect(jsonPath("$.fields.grandTotalCents").exists())
                .andExpect(jsonPath("$.fields.budgetCents").value("50000"))
                .andExpect(jsonPath("$.fields.budgetOverageCents").exists());

        // 2. Explicit false acknowledgment
        owner.unsafe(post("/api/trips/{tripId}/drafts/{draftId}/plan", tripId, draftId),
                "{\"expectedVersion\":0,\"expectedDraftVersion\":0,\"budgetOverageAcknowledged\":false}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("BUDGET_OVERAGE_UNACKNOWLEDGED"));
    }

    @Test
    void allowsPromotionWhenBudgetOverageIsExplicitlyAcknowledged() throws Exception {
        Client owner = register("overage-acknowledged@example.test");
        MvcResult created = owner.unsafe(post("/api/trips"),
                "{\"destinationKey\":\"destination-sfo\",\"startDate\":\"2027-03-10\",\"endDate\":\"2027-03-14\",\"travelerCount\":2,\"travelerAges\":[25,25],\"budgetCents\":50000}")
                .andExpect(status().isCreated()).andReturn();
        String tripId = jsonField(created, "id");
        String draftId = getDraftId(created, 0);

        insertSfoAirfareSelection(draftId);

        owner.unsafe(post("/api/trips/{tripId}/drafts/{draftId}/plan", tripId, draftId),
                "{\"expectedVersion\":0,\"expectedDraftVersion\":0,\"budgetOverageAcknowledged\":true}")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.version").value(1))
                .andExpect(jsonPath("$.planned.length()").value(1))
                .andExpect(jsonPath("$.planned[0].tally.isOverBudget").value(true))
                .andExpect(jsonPath("$.planned[0].tally.budgetOverageCents").isNumber());
    }

    @Test
    void invalidatesOverageAcknowledgmentOnSubsequentEdits() throws Exception {
        Client owner = register("invalidation-test@example.test");
        MvcResult created = owner.unsafe(post("/api/trips"),
                "{\"destinationKey\":\"destination-sfo\",\"startDate\":\"2027-03-10\",\"endDate\":\"2027-03-14\",\"travelerCount\":2,\"travelerAges\":[25,25],\"budgetCents\":50000}")
                .andExpect(status().isCreated()).andReturn();
        String tripId = jsonField(created, "id");
        String draftId = getDraftId(created, 0);

        insertSfoAirfareSelection(draftId);

        // Edit shared trip details (changes budget and advances trip version to 1)
        owner.unsafe(put("/api/trips/{tripId}", tripId),
                "{\"expectedVersion\":0,\"destinationKey\":\"destination-sfo\",\"startDate\":\"2027-03-10\",\"endDate\":\"2027-03-14\",\"travelerCount\":2,\"travelerAges\":[25,25],\"budgetCents\":60000}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.version").value(1));

        // Stale expectedVersion: 0 rejected with 409 VERSION_CONFLICT
        owner.unsafe(post("/api/trips/{tripId}/drafts/{draftId}/plan", tripId, draftId),
                "{\"expectedVersion\":0,\"expectedDraftVersion\":0,\"budgetOverageAcknowledged\":true}")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("VERSION_CONFLICT"));

        // Promotion with updated version 1 without acknowledgment fails with 400 BUDGET_OVERAGE_UNACKNOWLEDGED
        owner.unsafe(post("/api/trips/{tripId}/drafts/{draftId}/plan", tripId, draftId),
                "{\"expectedVersion\":1,\"expectedDraftVersion\":0}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("BUDGET_OVERAGE_UNACKNOWLEDGED"));
    }

    @Test
    void persistsCompleteDescriptiveFactsIntoPlannedSnapshotV16() throws Exception {
        Client owner = register("v16-facts@example.test");
        MvcResult created = owner.unsafe(post("/api/trips"),
                "{\"destinationKey\":\"destination-sfo\",\"startDate\":\"2027-03-10\",\"endDate\":\"2027-03-14\",\"travelerCount\":2,\"travelerAges\":[25,25],\"budgetCents\":500000}")
                .andExpect(status().isCreated()).andReturn();
        String tripId = jsonField(created, "id");
        String draftId = getDraftId(created, 0);

        insertSfoAirfareWithLayoverSelection(draftId);
        insertSfoStaySelection(draftId);
        insertSfoRentalSelection(draftId);

        MvcResult promoted = owner.unsafe(post("/api/trips/{tripId}/drafts/{draftId}/plan", tripId, draftId),
                "{\"expectedVersion\":0,\"expectedDraftVersion\":0}")
                .andExpect(status().isCreated()).andReturn();
        String plannedId = tools.jackson.databind.json.JsonMapper.builder().build()
                .readTree(promoted.getResponse().getContentAsString()).get("planned").get(0).get("id").asString();

        // 1. Direct JDBC verification of detour_planned_airfare_snapshot V16 columns
        var airfareRow = jdbc.queryForMap("""
                SELECT * FROM detour_planned_airfare_snapshot
                WHERE planned_itinerary_id = (SELECT id FROM detour_planned_itinerary WHERE public_id = ?)
                """, UUID.fromString(plannedId));
        assertEquals("Cascade Skies", airfareRow.get("outbound_carrier_name"));
        assertEquals("CS121", airfareRow.get("outbound_flight_number"));
        assertEquals(1, ((Number) airfareRow.get("outbound_stop_count")).intValue());
        assertEquals("SEA", airfareRow.get("outbound_layover_airport_code"));
        assertEquals(60, ((Number) airfareRow.get("outbound_layover_duration_minutes")).intValue());
        assertNotNull(airfareRow.get("outbound_departure_time"));
        assertNotNull(airfareRow.get("outbound_arrival_time"));
        assertEquals("America/Los_Angeles", airfareRow.get("outbound_departure_timezone"));
        assertEquals("America/Los_Angeles", airfareRow.get("outbound_arrival_timezone"));
        assertNotNull(airfareRow.get("total_duration_minutes"));

        // 2. Direct JDBC verification of detour_planned_stay_snapshot V16 columns
        var stayRow = jdbc.queryForMap("""
                SELECT * FROM detour_planned_stay_snapshot
                WHERE planned_itinerary_id = (SELECT id FROM detour_planned_itinerary WHERE public_id = ?)
                """, UUID.fromString(plannedId));
        assertEquals("HOTEL", stayRow.get("property_category"));
        assertNotNull(stayRow.get("location_description"));
        assertNotNull(stayRow.get("distance_to_city_center_meters"));
        assertNotNull(stayRow.get("guest_capacity"));
        assertNotNull(stayRow.get("required_room_count"));

        // 3. Direct JDBC verification of detour_planned_rental_snapshot V16 columns
        var rentalRow = jdbc.queryForMap("""
                SELECT * FROM detour_planned_rental_snapshot
                WHERE planned_itinerary_id = (SELECT id FROM detour_planned_itinerary WHERE public_id = ?)
                """, UUID.fromString(plannedId));
        assertEquals("ECONOMY", rentalRow.get("vehicle_category"));
        assertEquals("SFO Economy", rentalRow.get("vehicle_class_name"));
        assertNotNull(rentalRow.get("location_name"));
        assertNotNull(rentalRow.get("unit_identifier"));
    }

    @Test
    void plannedSnapshotIsUnaffectedByCatalogEditsOrDraftDeletion() throws Exception {
        Client owner = register("snapshot-stability@example.test");
        MvcResult created = owner.unsafe(post("/api/trips"),
                "{\"destinationKey\":\"destination-sfo\",\"startDate\":\"2027-03-10\",\"endDate\":\"2027-03-14\",\"travelerCount\":2,\"travelerAges\":[25,25],\"budgetCents\":500000}")
                .andExpect(status().isCreated()).andReturn();
        String tripId = jsonField(created, "id");
        String draftId = getDraftId(created, 0);

        insertSfoAirfareSelection(draftId);
        insertSfoStaySelection(draftId);
        insertSfoRentalSelection(draftId);

        MvcResult promoted = owner.unsafe(post("/api/trips/{tripId}/drafts/{draftId}/plan", tripId, draftId),
                "{\"expectedVersion\":0,\"expectedDraftVersion\":0}")
                .andExpect(status().isCreated()).andReturn();
        var planTree = tools.jackson.databind.json.JsonMapper.builder().build().readTree(promoted.getResponse().getContentAsString());
        String plannedId = planTree.get("planned").get(0).get("id").asString();
        long origFare = planTree.get("planned").get(0).get("selections").get("airfare").get("outboundBaseFareCents").asLong();
        String origCarrier = planTree.get("planned").get(0).get("selections").get("airfare").get("outboundCarrierName").asString();
        String origPropertyName = planTree.get("planned").get(0).get("selections").get("stay").get("propertyName").asString();

        // Mutate live catalog tables
        jdbc.update("UPDATE flight_instance SET base_fare_cents = base_fare_cents + 7777");
        jdbc.update("UPDATE catalog_supplier SET name = 'Completely Changed Airline'");
        jdbc.update("UPDATE accommodation_property SET name = 'Completely Changed Hotel'");
        jdbc.update("UPDATE rental_vehicle_class SET daily_base_price_cents = daily_base_price_cents + 8888");

        // Delete source draft
        owner.unsafe(delete("/api/trips/{tripId}/drafts/{draftId}", tripId, draftId),
                "{\"expectedVersion\":1,\"expectedDraftVersion\":0}")
                .andExpect(status().isOk());

        // Re-fetch trip: planned snapshot retains exact original frozen facts
        owner.unsafe(get("/api/trips/{tripId}", tripId), null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.drafts.length()").value(0))
                .andExpect(jsonPath("$.planned.length()").value(1))
                .andExpect(jsonPath("$.planned[0].id").value(plannedId))
                .andExpect(jsonPath("$.planned[0].selections.airfare.outboundBaseFareCents").value(origFare))
                .andExpect(jsonPath("$.planned[0].selections.airfare.outboundCarrierName").value(origCarrier))
                .andExpect(jsonPath("$.planned[0].selections.stay.propertyName").value(origPropertyName));
    }

    @Test
    void rejectsInPlaceMutationOnPlannedSnapshotWithImmutableAlternative() throws Exception {
        Client owner = register("immutable-alternative@example.test");
        MvcResult created = owner.unsafe(post("/api/trips"),
                "{\"destinationKey\":\"destination-sfo\",\"startDate\":\"2027-03-10\",\"endDate\":\"2027-03-14\",\"travelerCount\":2,\"travelerAges\":[25,25],\"budgetCents\":500000}")
                .andExpect(status().isCreated()).andReturn();
        String tripId = jsonField(created, "id");
        String draftId = getDraftId(created, 0);

        insertSfoAirfareSelection(draftId);

        MvcResult promoted = owner.unsafe(post("/api/trips/{tripId}/drafts/{draftId}/plan", tripId, draftId),
                "{\"expectedVersion\":0,\"expectedDraftVersion\":0}")
                .andExpect(status().isCreated()).andReturn();
        String plannedId = tools.jackson.databind.json.JsonMapper.builder().build()
                .readTree(promoted.getResponse().getContentAsString()).get("planned").get(0).get("id").asString();

        // In-place mutation endpoints on Planned alternative return 409 IMMUTABLE_ALTERNATIVE
        owner.unsafe(delete("/api/trips/{tripId}/drafts/{draftId}/airfare", tripId, plannedId),
                "{\"expectedVersion\":1,\"expectedDraftVersion\":0}")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("IMMUTABLE_ALTERNATIVE"));

        // Duplication of Planned alternative creates a new mutable Draft
        owner.unsafe(post("/api/trips/{tripId}/alternatives/{alternativeId}/duplicate", tripId, plannedId),
                "{\"expectedVersion\":1}")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.drafts.length()").value(2));
    }

    @Test
    void disallowsPromotionOfExpiredTrip() throws Exception {
        Client owner = register("expired-promotion@example.test");
        MvcResult created = owner.unsafe(post("/api/trips"),
                "{\"destinationKey\":\"destination-sfo\",\"startDate\":\"2027-03-10\",\"endDate\":\"2027-03-14\",\"travelerCount\":2,\"travelerAges\":[25,25],\"budgetCents\":500000}")
                .andExpect(status().isCreated()).andReturn();
        String tripId = jsonField(created, "id");
        String draftId = getDraftId(created, 0);

        insertSfoAirfareSelection(draftId);

        // Advance clock past trip departure date (2027-03-10)
        testClock.setInstant(ZonedDateTime.of(2027, 3, 11, 10, 0, 0, 0, ClockConfiguration.PDX_ZONE).toInstant());

        // Readiness endpoint reports expired departure date
        owner.unsafe(get("/api/trips/{tripId}/drafts/{draftId}/readiness", tripId, draftId), null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ready").value(false))
                .andExpect(jsonPath("$.blockingIssues.dates").value("Trip departure date has passed."));

        // Promotion rejected with 400 ALTERNATIVE_EXPIRED
        owner.unsafe(post("/api/trips/{tripId}/drafts/{draftId}/plan", tripId, draftId),
                "{\"expectedVersion\":0,\"expectedDraftVersion\":0}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("ALTERNATIVE_EXPIRED"));
    }

    @Test
    void racingPromotionRequestsHaveSingleWinnerAndNoCorruption() throws Exception {
        Client first = register("race-owner-1@example.test");
        Client second = login("race-owner-1@example.test");

        MvcResult created = first.unsafe(post("/api/trips"),
                "{\"destinationKey\":\"destination-sfo\",\"startDate\":\"2027-03-10\",\"endDate\":\"2027-03-14\",\"travelerCount\":2,\"travelerAges\":[25,25],\"budgetCents\":500000}")
                .andExpect(status().isCreated()).andReturn();
        String tripId = jsonField(created, "id");
        String draftId = getDraftId(created, 0);

        insertSfoAirfareSelection(draftId);

        CyclicBarrier planBarrier = new CyclicBarrier(2);
        try (ExecutorService workers = Executors.newFixedThreadPool(2)) {
            Future<Integer> f1 = workers.submit(() -> {
                planBarrier.await();
                return first.unsafe(post("/api/trips/{tripId}/drafts/{draftId}/plan", tripId, draftId),
                        "{\"expectedVersion\":0,\"expectedDraftVersion\":0}").andReturn().getResponse().getStatus();
            });
            Future<Integer> f2 = workers.submit(() -> {
                planBarrier.await();
                return second.unsafe(post("/api/trips/{tripId}/drafts/{draftId}/plan", tripId, draftId),
                        "{\"expectedVersion\":0,\"expectedDraftVersion\":0}").andReturn().getResponse().getStatus();
            });
            int s1 = f1.get();
            int s2 = f2.get();
            assertEquals(1, List.of(s1, s2).stream().filter(s -> s == 201).count());
            assertEquals(1, List.of(s1, s2).stream().filter(s -> s == 409).count());
        }

        int plannedCount = jdbc.queryForObject("""
                SELECT COUNT(*) FROM detour_planned_itinerary
                WHERE trip_id = (SELECT id FROM detour_trip WHERE public_id = ?)
                """, Integer.class, UUID.fromString(tripId));
        assertEquals(1, plannedCount);
    }

    @Test
    void enforcesMultiUserIsolationAcrossAllEndpoints() throws Exception {
        Client userA = register("user-a-isolation@example.test");
        Client userB = register("user-b-isolation@example.test");

        MvcResult tripA = userA.unsafe(post("/api/trips"),
                "{\"destinationKey\":\"destination-sfo\",\"startDate\":\"2027-03-10\",\"endDate\":\"2027-03-14\",\"travelerCount\":2,\"travelerAges\":[25,25],\"budgetCents\":500000}")
                .andExpect(status().isCreated()).andReturn();
        String tripAId = jsonField(tripA, "id");
        String draftAId = getDraftId(tripA, 0);

        insertSfoAirfareSelection(draftAId);

        // User B attempts to inspect User A's draft readiness -> 404 RESOURCE_NOT_FOUND
        userB.unsafe(get("/api/trips/{tripId}/drafts/{draftId}/readiness", tripAId, draftAId), null)
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("RESOURCE_NOT_FOUND"));

        // User B attempts to promote User A's draft -> 404 RESOURCE_NOT_FOUND
        userB.unsafe(post("/api/trips/{tripId}/drafts/{draftId}/plan", tripAId, draftAId),
                "{\"expectedVersion\":0,\"expectedDraftVersion\":0}")
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("RESOURCE_NOT_FOUND"));

        // User B attempts to duplicate User A's alternative -> 404 RESOURCE_NOT_FOUND
        userB.unsafe(post("/api/trips/{tripId}/alternatives/{alternativeId}/duplicate", tripAId, draftAId),
                "{\"expectedVersion\":0}")
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("RESOURCE_NOT_FOUND"));

        // User B attempts to delete User A's alternative -> 404 RESOURCE_NOT_FOUND
        userB.unsafe(delete("/api/trips/{tripId}/alternatives/{alternativeId}", tripAId, draftAId),
                "{\"expectedVersion\":0,\"confirmed\":true}")
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("RESOURCE_NOT_FOUND"));
    }

    private void insertSfoAirfareSelection(String draftId) {
        jdbc.update("""
                INSERT INTO detour_trip_draft_airfare_selection (draft_id, outbound_flight_instance_id, return_flight_instance_id)
                VALUES ((SELECT id FROM detour_trip_draft WHERE public_id = ?),
                    (SELECT instance.id FROM flight_instance instance JOIN flight_schedule schedule ON schedule.id = instance.flight_schedule_id WHERE schedule.catalog_key = 'airfare-out-sfo-d1' AND instance.service_date = DATE '2027-03-10'),
                    (SELECT instance.id FROM flight_instance instance JOIN flight_schedule schedule ON schedule.id = instance.flight_schedule_id WHERE schedule.catalog_key = 'airfare-in-sfo-d1' AND instance.service_date = DATE '2027-03-14'))
                """, UUID.fromString(draftId));
    }

    private void insertSfoAirfareWithLayoverSelection(String draftId) {
        jdbc.update("""
                INSERT INTO detour_trip_draft_airfare_selection (draft_id, outbound_flight_instance_id, return_flight_instance_id)
                VALUES ((SELECT id FROM detour_trip_draft WHERE public_id = ?),
                    (SELECT instance.id FROM flight_instance instance JOIN flight_schedule schedule ON schedule.id = instance.flight_schedule_id WHERE schedule.catalog_key = 'airfare-out-sfo-c1' AND instance.service_date = DATE '2027-03-10'),
                    (SELECT instance.id FROM flight_instance instance JOIN flight_schedule schedule ON schedule.id = instance.flight_schedule_id WHERE schedule.catalog_key = 'airfare-in-sfo-c1' AND instance.service_date = DATE '2027-03-14'))
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

    private Client login(String email) throws Exception {
        Client client = anonymous();
        MvcResult result = client.unsafe(post("/api/auth/login"), "{\"email\":\"" + email + "\",\"password\":\"aaaaaaaaaaaa\"}")
                .andExpect(status().isNoContent()).andReturn();
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
    }
}
