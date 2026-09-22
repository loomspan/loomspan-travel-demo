package app.detour.trip;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import jakarta.servlet.http.Cookie;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

@SpringBootTest
@AutoConfigureMockMvc
class RentalSearchAndSelectionIntegrationTest {
    private static final String DATABASE_URL = "jdbc:h2:mem:rental_api_" + UUID.randomUUID() + ";DB_CLOSE_DELAY=-1";
    private static final JsonMapper JSON = JsonMapper.builder().build();

    @DynamicPropertySource
    static void datasourceProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", () -> DATABASE_URL);
    }

    @Autowired private MockMvc mockMvc;
    @Autowired private JdbcTemplate jdbc;

    @org.junit.jupiter.api.BeforeEach
    void cleanOccupancy() {
        jdbc.update("DELETE FROM rental_unit_occupancy");
    }

    @Test
    void searchRentalCarsReturnsAvailableInventoryDeterministicallyRanked() throws Exception {
        Client owner = register("search-rank-test@example.test");
        MvcResult tripResult = owner.unsafe(post("/api/trips"), tripJson("destination-sfo", "2027-03-02", "2027-03-06", List.of(30, 28), 100000L))
                .andExpect(status().isCreated()).andReturn();
        String tripId = jsonField(tripResult, "id");
        String draftId = JSON.readTree(tripResult.getResponse().getContentAsString()).get("drafts").get(0).get("id").asString();

        String pickup = "2027-03-02T10:00:00-08:00";
        String ret = "2027-03-06T10:00:00-08:00";

        // 1. Draft-scoped plural route
        MvcResult draftResult = owner.unsafe(get("/api/trips/{tripId}/drafts/{draftId}/rentals?pickupAt={pickup}&returnAt={ret}", tripId, draftId, pickup, ret), null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tripId").value(tripId))
                .andExpect(jsonPath("$.draftId").value(draftId))
                .andExpect(jsonPath("$.destinationKey").value("destination-sfo"))
                .andExpect(jsonPath("$.billingCycles").value(4))
                .andExpect(jsonPath("$.driverEligible").value(true))
                .andExpect(jsonPath("$.selectionDisabled").value(false))
                .andExpect(jsonPath("$.options.length()").value(7))
                .andReturn();

        JsonNode options = JSON.readTree(draftResult.getResponse().getContentAsString()).get("options");

        // Category ordering: Economy (first 3), Standard (next 2), SUV (last 2)
        assertEquals("ECONOMY", options.get(0).get("vehicleCategory").asString());
        assertEquals("rental-unit-sfo-economy-01", options.get(0).get("unitCatalogKey").asString());
        assertEquals("ECONOMY", options.get(1).get("vehicleCategory").asString());
        assertEquals("rental-unit-sfo-economy-02", options.get(1).get("unitCatalogKey").asString());
        assertEquals("ECONOMY", options.get(2).get("vehicleCategory").asString());
        assertEquals("rental-unit-sfo-economy-03", options.get(2).get("unitCatalogKey").asString());

        assertEquals("STANDARD", options.get(3).get("vehicleCategory").asString());
        assertEquals("rental-unit-sfo-standard-01", options.get(3).get("unitCatalogKey").asString());
        assertEquals("STANDARD", options.get(4).get("vehicleCategory").asString());
        assertEquals("rental-unit-sfo-standard-02", options.get(4).get("unitCatalogKey").asString());

        assertEquals("SUV", options.get(5).get("vehicleCategory").asString());
        assertEquals("rental-unit-sfo-suv-01", options.get(5).get("unitCatalogKey").asString());
        assertEquals("SUV", options.get(6).get("vehicleCategory").asString());
        assertEquals("rental-unit-sfo-suv-02", options.get(6).get("unitCatalogKey").asString());

        // Pricing verification: 4 cycles
        // Economy: daily total 4800 cents * 4 = 19200 cents
        assertEquals(19200, options.get(0).get("pricing").get("totalPriceCents").asLong());
        // Standard: daily total 7100 cents * 4 = 28400 cents
        assertEquals(28400, options.get(3).get("pricing").get("totalPriceCents").asLong());
        // SUV: daily total 10300 cents * 4 = 41200 cents
        assertEquals(41200, options.get(5).get("pricing").get("totalPriceCents").asLong());

        // 2. Draft-scoped singular and aliases
        owner.unsafe(get("/api/trips/{tripId}/drafts/{draftId}/rental?pickupAt={p}&returnAt={r}", tripId, draftId, pickup, ret), null)
                .andExpect(status().isOk()).andExpect(jsonPath("$.options.length()").value(7));
        owner.unsafe(get("/api/trips/{tripId}/drafts/{draftId}/cars?pickupAt={p}&returnAt={r}", tripId, draftId, pickup, ret), null)
                .andExpect(status().isOk()).andExpect(jsonPath("$.options.length()").value(7));
        owner.unsafe(get("/api/trips/{tripId}/drafts/{draftId}/car?pickupAt={p}&returnAt={r}", tripId, draftId, pickup, ret), null)
                .andExpect(status().isOk()).andExpect(jsonPath("$.options.length()").value(7));

        // 3. Trip-scoped routes
        owner.unsafe(get("/api/trips/{tripId}/rentals?pickupAt={p}&returnAt={r}", tripId, pickup, ret), null)
                .andExpect(status().isOk()).andExpect(jsonPath("$.options.length()").value(7));
        owner.unsafe(get("/api/trips/{tripId}/rental?pickupAt={p}&returnAt={r}", tripId, pickup, ret), null)
                .andExpect(status().isOk()).andExpect(jsonPath("$.options.length()").value(7));
        owner.unsafe(get("/api/trips/{tripId}/cars?pickupAt={p}&returnAt={r}", tripId, pickup, ret), null)
                .andExpect(status().isOk()).andExpect(jsonPath("$.options.length()").value(7));
        owner.unsafe(get("/api/trips/{tripId}/car?pickupAt={p}&returnAt={r}", tripId, pickup, ret), null)
                .andExpect(status().isOk()).andExpect(jsonPath("$.options.length()").value(7));
    }

    @Test
    void searchRentalCarsValidatesPickupAndReturnDatesWithinTripIntervalInAirportTimezone() throws Exception {
        Client owner = register("dates-validation-test@example.test");
        MvcResult tripResult = owner.unsafe(post("/api/trips"), tripJson("destination-sfo", "2027-03-02", "2027-03-06", List.of(30), 100000L))
                .andExpect(status().isCreated()).andReturn();
        String tripId = jsonField(tripResult, "id");
        String draftId = JSON.readTree(tripResult.getResponse().getContentAsString()).get("drafts").get(0).get("id").asString();

        // Pickup before trip start in America/Los_Angeles (2027-03-01 23:59:59 PST)
        owner.unsafe(get("/api/trips/{tripId}/drafts/{draftId}/rentals?pickupAt=2027-03-01T23:59:59-08:00&returnAt=2027-03-06T10:00:00-08:00", tripId, draftId), null)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.fields.pickupAt").value("Pickup date must fall within the trip dates at the destination airport."));

        // Return after trip end in America/Los_Angeles (2027-03-07 00:00:01 PST)
        owner.unsafe(get("/api/trips/{tripId}/drafts/{draftId}/rentals?pickupAt=2027-03-02T10:00:00-08:00&returnAt=2027-03-07T00:00:01-08:00", tripId, draftId), null)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.fields.returnAt").value("Return date must fall within the trip dates at the destination airport."));
    }

    @Test
    void searchRentalCarsRejectsReturnBeforeOrEqualToPickup() throws Exception {
        Client owner = register("inverted-dates-test@example.test");
        MvcResult tripResult = owner.unsafe(post("/api/trips"), tripJson("destination-sfo", "2027-03-02", "2027-03-06", List.of(30), 100000L))
                .andExpect(status().isCreated()).andReturn();
        String tripId = jsonField(tripResult, "id");
        String draftId = JSON.readTree(tripResult.getResponse().getContentAsString()).get("drafts").get(0).get("id").asString();

        // Exact equality
        owner.unsafe(get("/api/trips/{tripId}/drafts/{draftId}/rentals?pickupAt=2027-03-03T10:00:00-08:00&returnAt=2027-03-03T10:00:00-08:00", tripId, draftId), null)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.fields.returnAt").value("Return time must be strictly after pickup time."));

        // Return before pickup
        owner.unsafe(get("/api/trips/{tripId}/drafts/{draftId}/rentals?pickupAt=2027-03-03T10:00:00-08:00&returnAt=2027-03-03T09:00:00-08:00", tripId, draftId), null)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.fields.returnAt").value("Return time must be strictly after pickup time."));
    }

    @Test
    void searchRentalCarsEnforces25PlusDriverEligibilityWithActionableExplanation() throws Exception {
        Client owner = register("driver-age-test@example.test");
        // Travelers under 25 (e.g. 24 and 22)
        MvcResult under25Trip = owner.unsafe(post("/api/trips"), tripJson("destination-sfo", "2027-03-02", "2027-03-06", List.of(24, 22), 100000L))
                .andExpect(status().isCreated()).andReturn();
        String tripId = jsonField(under25Trip, "id");
        String draftId = JSON.readTree(under25Trip.getResponse().getContentAsString()).get("drafts").get(0).get("id").asString();

        String expectedExplanation = "Rental cars require at least one traveler aged 25 or older. Please add traveler ages in Trip Details to select a car.";

        MvcResult result = owner.unsafe(get("/api/trips/{tripId}/drafts/{draftId}/rentals?pickupAt=2027-03-02T10:00:00-08:00&returnAt=2027-03-06T10:00:00-08:00", tripId, draftId), null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.driverEligible").value(false))
                .andExpect(jsonPath("$.selectionDisabled").value(true))
                .andExpect(jsonPath("$.explanation").value(expectedExplanation))
                .andExpect(jsonPath("$.disabledReason").value(expectedExplanation))
                .andExpect(jsonPath("$.options.length()").value(7)) // Inventory still browsable
                .andReturn();

        // Also test trip with null traveler ages
        MvcResult noAgesTrip = owner.unsafe(post("/api/trips"), """
                {
                    "destinationKey": "destination-sfo",
                    "startDate": "2027-03-02",
                    "endDate": "2027-03-06",
                    "travelerCount": 2,
                    "travelerAges": null,
                    "budgetCents": 100000
                }
                """).andExpect(status().isCreated()).andReturn();
        String noAgesTripId = jsonField(noAgesTrip, "id");
        String noAgesDraftId = JSON.readTree(noAgesTrip.getResponse().getContentAsString()).get("drafts").get(0).get("id").asString();

        owner.unsafe(get("/api/trips/{tripId}/drafts/{draftId}/rentals?pickupAt=2027-03-02T10:00:00-08:00&returnAt=2027-03-06T10:00:00-08:00", noAgesTripId, noAgesDraftId), null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.driverEligible").value(false))
                .andExpect(jsonPath("$.selectionDisabled").value(true))
                .andExpect(jsonPath("$.explanation").value(expectedExplanation));
    }

    @Test
    void searchRentalCarsFiltersOutActiveOverlappingOccupancyOnHalfOpenInterval() throws Exception {
        Client owner = register("occupancy-overlap-test@example.test");
        MvcResult tripResult = owner.unsafe(post("/api/trips"), tripJson("destination-sfo", "2027-03-02", "2027-03-06", List.of(30), 100000L))
                .andExpect(status().isCreated()).andReturn();
        String tripId = jsonField(tripResult, "id");
        String draftId = JSON.readTree(tripResult.getResponse().getContentAsString()).get("drafts").get(0).get("id").asString();

        long unitId = jdbc.queryForObject("SELECT id FROM rental_unit WHERE catalog_key = 'rental-unit-sfo-economy-01'", Long.class);

        // Insert active occupancy from March 3 10:00 to March 4 10:00
        OffsetDateTime occPickup = OffsetDateTime.parse("2027-03-03T10:00:00-08:00");
        OffsetDateTime occReturn = OffsetDateTime.parse("2027-03-04T10:00:00-08:00");
        jdbc.update("INSERT INTO rental_unit_occupancy (rental_unit_id, pickup_at, return_at, occupancy_status) VALUES (?, ?, ?, 'ACTIVE')",
                unitId, occPickup, occReturn);

        // 1. Overlapping search: March 2 10:00 to March 5 10:00 -> economy-01 is excluded!
        MvcResult overlapResult = owner.unsafe(get("/api/trips/{tripId}/drafts/{draftId}/rentals?pickupAt=2027-03-02T10:00:00-08:00&returnAt=2027-03-05T10:00:00-08:00", tripId, draftId), null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.options.length()").value(6))
                .andReturn();
        JsonNode options = JSON.readTree(overlapResult.getResponse().getContentAsString()).get("options");
        for (JsonNode opt : options) {
            assertFalse(opt.get("unitCatalogKey").asString().equals("rental-unit-sfo-economy-01"));
        }

        // 2. Abutting before: March 2 10:00 to March 3 10:00 (searchReturn == occPickup) -> economy-01 is available!
        MvcResult abutBeforeResult = owner.unsafe(get("/api/trips/{tripId}/drafts/{draftId}/rentals?pickupAt=2027-03-02T10:00:00-08:00&returnAt=2027-03-03T10:00:00-08:00", tripId, draftId), null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.options.length()").value(7))
                .andReturn();
        boolean foundAbutBefore = false;
        for (JsonNode opt : JSON.readTree(abutBeforeResult.getResponse().getContentAsString()).get("options")) {
            if ("rental-unit-sfo-economy-01".equals(opt.get("unitCatalogKey").asString())) foundAbutBefore = true;
        }
        assertTrue(foundAbutBefore);

        // 3. Abutting after: March 4 10:00 to March 5 10:00 (searchPickup == occReturn) -> economy-01 is available!
        MvcResult abutAfterResult = owner.unsafe(get("/api/trips/{tripId}/drafts/{draftId}/rentals?pickupAt=2027-03-04T10:00:00-08:00&returnAt=2027-03-05T10:00:00-08:00", tripId, draftId), null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.options.length()").value(7))
                .andReturn();
        boolean foundAbutAfter = false;
        for (JsonNode opt : JSON.readTree(abutAfterResult.getResponse().getContentAsString()).get("options")) {
            if ("rental-unit-sfo-economy-01".equals(opt.get("unitCatalogKey").asString())) foundAbutAfter = true;
        }
        assertTrue(foundAbutAfter);

        // 4. Update status to RELEASED -> overlapping search now includes economy-01
        jdbc.update("UPDATE rental_unit_occupancy SET occupancy_status = 'RELEASED' WHERE rental_unit_id = ?", unitId);
        MvcResult releasedResult = owner.unsafe(get("/api/trips/{tripId}/drafts/{draftId}/rentals?pickupAt=2027-03-02T10:00:00-08:00&returnAt=2027-03-05T10:00:00-08:00", tripId, draftId), null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.options.length()").value(7))
                .andReturn();
        boolean foundReleased = false;
        for (JsonNode opt : JSON.readTree(releasedResult.getResponse().getContentAsString()).get("options")) {
            if ("rental-unit-sfo-economy-01".equals(opt.get("unitCatalogKey").asString())) foundReleased = true;
        }
        assertTrue(foundReleased);
    }

    @Test
    void pricingCalculatesConsecutive24HourBillingCyclesAndRoundsUpPartialFinalCycle() throws Exception {
        Client owner = register("pricing-math-test@example.test");
        MvcResult tripResult = owner.unsafe(post("/api/trips"), tripJson("destination-sfo", "2027-03-02", "2027-03-06", List.of(30), 100000L))
                .andExpect(status().isCreated()).andReturn();
        String tripId = jsonField(tripResult, "id");
        String draftId = JSON.readTree(tripResult.getResponse().getContentAsString()).get("drafts").get(0).get("id").asString();

        // Exact 24 hours: 1 cycle -> $48.00 (4800 cents)
        MvcResult r24 = owner.unsafe(get("/api/trips/{tripId}/drafts/{draftId}/rentals?pickupAt=2027-03-02T10:00:00-08:00&returnAt=2027-03-03T10:00:00-08:00", tripId, draftId), null)
                .andExpect(status().isOk()).andExpect(jsonPath("$.billingCycles").value(1)).andReturn();
        JsonNode opt24 = JSON.readTree(r24.getResponse().getContentAsString()).get("options").get(0);
        assertEquals(4800, opt24.get("pricing").get("totalPriceCents").asLong());
        assertEquals(4277, opt24.get("pricing").get("totalBasePriceCents").asLong());
        assertEquals(343, opt24.get("pricing").get("totalTaxCents").asLong());
        assertEquals(180, opt24.get("pricing").get("totalFeeCents").asLong());

        // 24 hours 1 second: 2 cycles -> $96.00 (9600 cents)
        MvcResult r24s1 = owner.unsafe(get("/api/trips/{tripId}/drafts/{draftId}/rentals?pickupAt=2027-03-02T10:00:00-08:00&returnAt=2027-03-03T10:00:01-08:00", tripId, draftId), null)
                .andExpect(status().isOk()).andExpect(jsonPath("$.billingCycles").value(2)).andReturn();
        JsonNode opt24s1 = JSON.readTree(r24s1.getResponse().getContentAsString()).get("options").get(0);
        assertEquals(9600, opt24s1.get("pricing").get("totalPriceCents").asLong());

        // 25 hours: 2 cycles -> $96.00 (9600 cents)
        MvcResult r25 = owner.unsafe(get("/api/trips/{tripId}/drafts/{draftId}/rentals?pickupAt=2027-03-02T10:00:00-08:00&returnAt=2027-03-03T11:00:00-08:00", tripId, draftId), null)
                .andExpect(status().isOk()).andExpect(jsonPath("$.billingCycles").value(2)).andReturn();
        JsonNode opt25 = JSON.readTree(r25.getResponse().getContentAsString()).get("options").get(0);
        assertEquals(9600, opt25.get("pricing").get("totalPriceCents").asLong());

        // 48 hours: 2 cycles -> $96.00 (9600 cents)
        MvcResult r48 = owner.unsafe(get("/api/trips/{tripId}/drafts/{draftId}/rentals?pickupAt=2027-03-02T10:00:00-08:00&returnAt=2027-03-04T10:00:00-08:00", tripId, draftId), null)
                .andExpect(status().isOk()).andExpect(jsonPath("$.billingCycles").value(2)).andReturn();
        JsonNode opt48 = JSON.readTree(r48.getResponse().getContentAsString()).get("options").get(0);
        assertEquals(9600, opt48.get("pricing").get("totalPriceCents").asLong());

        // 49 hours: 3 cycles -> $144.00 (14400 cents)
        MvcResult r49 = owner.unsafe(get("/api/trips/{tripId}/drafts/{draftId}/rentals?pickupAt=2027-03-02T10:00:00-08:00&returnAt=2027-03-04T11:00:00-08:00", tripId, draftId), null)
                .andExpect(status().isOk()).andExpect(jsonPath("$.billingCycles").value(3)).andReturn();
        JsonNode opt49 = JSON.readTree(r49.getResponse().getContentAsString()).get("options").get(0);
        assertEquals(14400, opt49.get("pricing").get("totalPriceCents").asLong());
    }

    @Test
    void selectDraftRentalPersistsSelectionAndAdvancesVersions() throws Exception {
        Client owner = register("select-rental-test@example.test");
        MvcResult tripResult = owner.unsafe(post("/api/trips"), tripJson("destination-sfo", "2027-03-02", "2027-03-06", List.of(30), 100000L))
                .andExpect(status().isCreated()).andReturn();
        String tripId = jsonField(tripResult, "id");
        String draftId = JSON.readTree(tripResult.getResponse().getContentAsString()).get("drafts").get(0).get("id").asString();

        long unitId = jdbc.queryForObject("SELECT id FROM rental_unit WHERE catalog_key = 'rental-unit-sfo-economy-01'", Long.class);

        String pickup = "2027-03-02T10:00:00-08:00";
        String ret = "2027-03-06T10:00:00-08:00";

        MvcResult putResult = owner.unsafe(put("/api/trips/{tripId}/drafts/{draftId}/rentals", tripId, draftId), """
                {
                    "expectedVersion": 0,
                    "expectedDraftVersion": 0,
                    "rentalUnitId": %d,
                    "pickupAt": "%s",
                    "returnAt": "%s"
                }
                """.formatted(unitId, pickup, ret))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.version").value(1))
                .andExpect(jsonPath("$.drafts[0].version").value(1))
                .andExpect(jsonPath("$.drafts[0].selections.rental.rentalUnitId").value(unitId))
                .andExpect(jsonPath("$.drafts[0].selections.rental.locationName").value("Harborline Mobility at SFO"))
                .andExpect(jsonPath("$.drafts[0].selections.rental.vehicleClassName").value("SFO Economy"))
                .andExpect(jsonPath("$.drafts[0].selections.rental.unitIdentifier").value("SFO-ECO-01"))
                .andExpect(jsonPath("$.drafts[0].selections.rental.dailyBasePriceCents").value(4277))
                .andExpect(jsonPath("$.drafts[0].selections.rental.dailyTaxCents").value(343))
                .andExpect(jsonPath("$.drafts[0].selections.rental.dailyFeeCents").value(180))
                .andReturn();
    }

    @Test
    void selectDraftRentalRejectsWhenDriverUnder25() throws Exception {
        Client owner = register("driver-under25-test@example.test");
        MvcResult tripResult = owner.unsafe(post("/api/trips"), tripJson("destination-sfo", "2027-03-02", "2027-03-06", List.of(24, 21), 100000L))
                .andExpect(status().isCreated()).andReturn();
        String tripId = jsonField(tripResult, "id");
        String draftId = JSON.readTree(tripResult.getResponse().getContentAsString()).get("drafts").get(0).get("id").asString();

        long unitId = jdbc.queryForObject("SELECT id FROM rental_unit WHERE catalog_key = 'rental-unit-sfo-economy-01'", Long.class);

        String pickup = "2027-03-02T10:00:00-08:00";
        String ret = "2027-03-06T10:00:00-08:00";

        owner.unsafe(put("/api/trips/{tripId}/drafts/{draftId}/rentals", tripId, draftId), """
                {
                    "expectedVersion": 0,
                    "expectedDraftVersion": 0,
                    "rentalUnitId": %d,
                    "pickupAt": "%s",
                    "returnAt": "%s"
                }
                """.formatted(unitId, pickup, ret))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.fields.travelerAges").value("Rental cars require at least one traveler aged 25 or older. Please add traveler ages in Trip Details to select a car."));
    }

    @Test
    void selectDraftRentalRejectsWhenUnitOccupiedOrInvalidDates() throws Exception {
        Client owner = register("invalid-selection-test@example.test");
        MvcResult tripResult = owner.unsafe(post("/api/trips"), tripJson("destination-sfo", "2027-03-02", "2027-03-06", List.of(30), 100000L))
                .andExpect(status().isCreated()).andReturn();
        String tripId = jsonField(tripResult, "id");
        String draftId = JSON.readTree(tripResult.getResponse().getContentAsString()).get("drafts").get(0).get("id").asString();

        long sfoUnitId = jdbc.queryForObject("SELECT id FROM rental_unit WHERE catalog_key = 'rental-unit-sfo-economy-01'", Long.class);
        long mucUnitId = jdbc.queryForObject("SELECT id FROM rental_unit WHERE catalog_key = 'rental-unit-muc-economy-01'", Long.class);

        // 1. Mismatched destination (MUC unit for SFO trip)
        owner.unsafe(put("/api/trips/{tripId}/drafts/{draftId}/rentals", tripId, draftId), """
                {
                    "expectedVersion": 0,
                    "expectedDraftVersion": 0,
                    "rentalUnitId": %d,
                    "pickupAt": "2027-03-02T10:00:00-08:00",
                    "returnAt": "2027-03-06T10:00:00-08:00"
                }
                """.formatted(mucUnitId))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.fields.rentalUnitId").value("Selected rental car does not match trip destination."));

        // 2. Dates outside trip interval
        owner.unsafe(put("/api/trips/{tripId}/drafts/{draftId}/rentals", tripId, draftId), """
                {
                    "expectedVersion": 0,
                    "expectedDraftVersion": 0,
                    "rentalUnitId": %d,
                    "pickupAt": "2027-03-01T10:00:00-08:00",
                    "returnAt": "2027-03-06T10:00:00-08:00"
                }
                """.formatted(sfoUnitId))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.fields.pickupAt").value("Pickup date must fall within the trip dates at the destination airport."));

        // 3. Return before pickup
        owner.unsafe(put("/api/trips/{tripId}/drafts/{draftId}/rentals", tripId, draftId), """
                {
                    "expectedVersion": 0,
                    "expectedDraftVersion": 0,
                    "rentalUnitId": %d,
                    "pickupAt": "2027-03-03T10:00:00-08:00",
                    "returnAt": "2027-03-02T10:00:00-08:00"
                }
                """.formatted(sfoUnitId))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.fields.returnAt").value("Return time must be strictly after pickup time."));

        // 4. Overlapping active occupancy
        OffsetDateTime occPickup = OffsetDateTime.parse("2027-03-03T10:00:00-08:00");
        OffsetDateTime occReturn = OffsetDateTime.parse("2027-03-04T10:00:00-08:00");
        jdbc.update("INSERT INTO rental_unit_occupancy (rental_unit_id, pickup_at, return_at, occupancy_status) VALUES (?, ?, ?, 'ACTIVE')",
                sfoUnitId, occPickup, occReturn);

        owner.unsafe(put("/api/trips/{tripId}/drafts/{draftId}/rentals", tripId, draftId), """
                {
                    "expectedVersion": 0,
                    "expectedDraftVersion": 0,
                    "rentalUnitId": %d,
                    "pickupAt": "2027-03-02T10:00:00-08:00",
                    "returnAt": "2027-03-05T10:00:00-08:00"
                }
                """.formatted(sfoUnitId))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.fields.rentalUnitId").value("Selected rental car is unavailable for the requested interval."));
    }

    @Test
    void replaceDraftRentalSelectionUpdatesDraftWithoutDuplicateRows() throws Exception {
        Client owner = register("replace-rental-test@example.test");
        MvcResult tripResult = owner.unsafe(post("/api/trips"), tripJson("destination-sfo", "2027-03-02", "2027-03-06", List.of(30), 100000L))
                .andExpect(status().isCreated()).andReturn();
        String tripId = jsonField(tripResult, "id");
        String draftId = JSON.readTree(tripResult.getResponse().getContentAsString()).get("drafts").get(0).get("id").asString();

        long ecoUnitId = jdbc.queryForObject("SELECT id FROM rental_unit WHERE catalog_key = 'rental-unit-sfo-economy-01'", Long.class);
        long suvUnitId = jdbc.queryForObject("SELECT id FROM rental_unit WHERE catalog_key = 'rental-unit-sfo-suv-01'", Long.class);

        // 1. Select Economy (v0 -> v1)
        owner.unsafe(put("/api/trips/{tripId}/drafts/{draftId}/rentals", tripId, draftId), """
                {
                    "expectedVersion": 0,
                    "expectedDraftVersion": 0,
                    "rentalUnitId": %d,
                    "pickupAt": "2027-03-02T10:00:00-08:00",
                    "returnAt": "2027-03-06T10:00:00-08:00"
                }
                """.formatted(ecoUnitId))
                .andExpect(status().isOk());

        // 2. Select SUV on same draft (v1 -> v2)
        owner.unsafe(put("/api/trips/{tripId}/drafts/{draftId}/rentals", tripId, draftId), """
                {
                    "expectedVersion": 1,
                    "expectedDraftVersion": 1,
                    "rentalUnitId": %d,
                    "pickupAt": "2027-03-02T11:00:00-08:00",
                    "returnAt": "2027-03-06T11:00:00-08:00"
                }
                """.formatted(suvUnitId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.version").value(2))
                .andExpect(jsonPath("$.drafts[0].version").value(2))
                .andExpect(jsonPath("$.drafts[0].selections.rental.rentalUnitId").value(suvUnitId))
                .andExpect(jsonPath("$.drafts[0].selections.rental.vehicleClassName").value("SFO SUV"));

        // Verify exactly 1 row in detour_trip_draft_rental_selection for this draft
        Long internalDraftId = jdbc.queryForObject("SELECT id FROM detour_trip_draft WHERE public_id = ?", Long.class, UUID.fromString(draftId));
        Integer rowCount = jdbc.queryForObject("SELECT COUNT(*) FROM detour_trip_draft_rental_selection WHERE draft_id = ?", Integer.class, internalDraftId);
        assertEquals(1, rowCount);
        Long persistedUnitId = jdbc.queryForObject("SELECT rental_unit_id FROM detour_trip_draft_rental_selection WHERE draft_id = ?", Long.class, internalDraftId);
        assertEquals(suvUnitId, persistedUnitId);
    }

    @Test
    void removeDraftRentalSelectionDeletesSelectionAndAdvancesVersions() throws Exception {
        Client owner = register("remove-rental-test@example.test");
        MvcResult tripResult = owner.unsafe(post("/api/trips"), tripJson("destination-sfo", "2027-03-02", "2027-03-06", List.of(30), 100000L))
                .andExpect(status().isCreated()).andReturn();
        String tripId = jsonField(tripResult, "id");
        String draftId = JSON.readTree(tripResult.getResponse().getContentAsString()).get("drafts").get(0).get("id").asString();

        long ecoUnitId = jdbc.queryForObject("SELECT id FROM rental_unit WHERE catalog_key = 'rental-unit-sfo-economy-01'", Long.class);

        // Select Economy (v0 -> v1)
        owner.unsafe(put("/api/trips/{tripId}/drafts/{draftId}/rentals", tripId, draftId), """
                {
                    "expectedVersion": 0,
                    "expectedDraftVersion": 0,
                    "rentalUnitId": %d,
                    "pickupAt": "2027-03-02T10:00:00-08:00",
                    "returnAt": "2027-03-06T10:00:00-08:00"
                }
                """.formatted(ecoUnitId))
                .andExpect(status().isOk());

        // Remove selection (v1 -> v2)
        owner.unsafe(delete("/api/trips/{tripId}/drafts/{draftId}/rentals", tripId, draftId), """
                {
                    "expectedVersion": 1,
                    "expectedDraftVersion": 1
                }
                """)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.version").value(2))
                .andExpect(jsonPath("$.drafts[0].version").value(2))
                .andExpect(jsonPath("$.drafts[0].selections.rental").doesNotExist());

        // Verify row deleted from detour_trip_draft_rental_selection
        Long internalDraftId = jdbc.queryForObject("SELECT id FROM detour_trip_draft WHERE public_id = ?", Long.class, UUID.fromString(draftId));
        Integer rowCount = jdbc.queryForObject("SELECT COUNT(*) FROM detour_trip_draft_rental_selection WHERE draft_id = ?", Integer.class, internalDraftId);
        assertEquals(0, rowCount);
    }

    @Test
    void draftRentalMutationsEnforceOptimisticConcurrency() throws Exception {
        Client owner = register("concurrency-rental-test@example.test");
        MvcResult tripResult = owner.unsafe(post("/api/trips"), tripJson("destination-sfo", "2027-03-02", "2027-03-06", List.of(30), 100000L))
                .andExpect(status().isCreated()).andReturn();
        String tripId = jsonField(tripResult, "id");
        String draftId = JSON.readTree(tripResult.getResponse().getContentAsString()).get("drafts").get(0).get("id").asString();

        long unitId = jdbc.queryForObject("SELECT id FROM rental_unit WHERE catalog_key = 'rental-unit-sfo-economy-01'", Long.class);

        // 1. Conflict on PUT (wrong expectedVersion)
        owner.unsafe(put("/api/trips/{tripId}/drafts/{draftId}/rentals", tripId, draftId), """
                {
                    "expectedVersion": 999,
                    "expectedDraftVersion": 0,
                    "rentalUnitId": %d,
                    "pickupAt": "2027-03-02T10:00:00-08:00",
                    "returnAt": "2027-03-06T10:00:00-08:00"
                }
                """.formatted(unitId))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("VERSION_CONFLICT"));

        // 2. Conflict on PUT (wrong expectedDraftVersion)
        owner.unsafe(put("/api/trips/{tripId}/drafts/{draftId}/rentals", tripId, draftId), """
                {
                    "expectedVersion": 0,
                    "expectedDraftVersion": 999,
                    "rentalUnitId": %d,
                    "pickupAt": "2027-03-02T10:00:00-08:00",
                    "returnAt": "2027-03-06T10:00:00-08:00"
                }
                """.formatted(unitId))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("VERSION_CONFLICT"));

        // 3. Conflict on DELETE (wrong expectedVersion)
        owner.unsafe(delete("/api/trips/{tripId}/drafts/{draftId}/rentals", tripId, draftId), """
                {
                    "expectedVersion": 999,
                    "expectedDraftVersion": 0
                }
                """)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("VERSION_CONFLICT"));

        // 4. Conflict on DELETE (wrong expectedDraftVersion)
        owner.unsafe(delete("/api/trips/{tripId}/drafts/{draftId}/rentals", tripId, draftId), """
                {
                    "expectedVersion": 0,
                    "expectedDraftVersion": 999
                }
                """)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("VERSION_CONFLICT"));
    }

    @Test
    void rentalEndpointsEnforceCrossUserIsolation() throws Exception {
        Client userA = register("usera-rental-test@example.test");
        Client userB = register("userb-rental-test@example.test");

        MvcResult tripResult = userA.unsafe(post("/api/trips"), tripJson("destination-sfo", "2027-03-02", "2027-03-06", List.of(30), 100000L))
                .andExpect(status().isCreated()).andReturn();
        String tripId = jsonField(tripResult, "id");
        String draftId = JSON.readTree(tripResult.getResponse().getContentAsString()).get("drafts").get(0).get("id").asString();

        long unitId = jdbc.queryForObject("SELECT id FROM rental_unit WHERE catalog_key = 'rental-unit-sfo-economy-01'", Long.class);

        // 1. User B search on User A trip/draft -> 404
        userB.unsafe(get("/api/trips/{tripId}/drafts/{draftId}/rentals?pickupAt=2027-03-02T10:00:00-08:00&returnAt=2027-03-06T10:00:00-08:00", tripId, draftId), null)
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("RESOURCE_NOT_FOUND"));

        userB.unsafe(get("/api/trips/{tripId}/rentals?pickupAt=2027-03-02T10:00:00-08:00&returnAt=2027-03-06T10:00:00-08:00", tripId), null)
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("RESOURCE_NOT_FOUND"));

        // 2. User B select on User A draft -> 404
        userB.unsafe(put("/api/trips/{tripId}/drafts/{draftId}/rentals", tripId, draftId), """
                {
                    "expectedVersion": 0,
                    "expectedDraftVersion": 0,
                    "rentalUnitId": %d,
                    "pickupAt": "2027-03-02T10:00:00-08:00",
                    "returnAt": "2027-03-06T10:00:00-08:00"
                }
                """.formatted(unitId))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("RESOURCE_NOT_FOUND"));

        // 3. User B delete on User A draft -> 404
        userB.unsafe(delete("/api/trips/{tripId}/drafts/{draftId}/rentals", tripId, draftId), """
                {
                    "expectedVersion": 0,
                    "expectedDraftVersion": 0
                }
                """)
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("RESOURCE_NOT_FOUND"));

        // 4. Nonexistent IDs return identical 404
        UUID nonTrip = UUID.randomUUID();
        UUID nonDraft = UUID.randomUUID();
        userB.unsafe(get("/api/trips/{tripId}/drafts/{draftId}/rentals?pickupAt=2027-03-02T10:00:00-08:00&returnAt=2027-03-06T10:00:00-08:00", nonTrip, nonDraft), null)
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("RESOURCE_NOT_FOUND"));
    }

    private static String tripJson(String destinationKey, String startDate, String endDate, List<Integer> ages, Long budgetCents) {
        String budgetStr = budgetCents == null ? "null" : budgetCents.toString();
        String agesStr = ages == null ? "null" : ages.toString();
        int count = ages == null ? 2 : ages.size();
        return """
                {
                    "destinationKey": "%s",
                    "startDate": "%s",
                    "endDate": "%s",
                    "travelerCount": %d,
                    "travelerAges": %s,
                    "budgetCents": %s
                }
                """.formatted(destinationKey, startDate, endDate, count, agesStr, budgetStr);
    }

    private static String jsonField(MvcResult result, String field) throws Exception {
        return JSON.readTree(result.getResponse().getContentAsString()).get(field).asString();
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
        private org.springframework.test.web.servlet.ResultActions unsafe(
                org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder request,
                String body) throws Exception {
            request.cookie(csrf).header("X-XSRF-TOKEN", csrf.getValue()).contentType(MediaType.APPLICATION_JSON);
            if (session != null) request.session(session);
            if (body != null) request.content(body);
            return mockMvc.perform(request);
        }
    }
}
