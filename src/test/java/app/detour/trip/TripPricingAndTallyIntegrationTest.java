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
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

@SpringBootTest
@AutoConfigureMockMvc
class TripPricingAndTallyIntegrationTest {
    private static final String DATABASE_URL = "jdbc:h2:mem:trip_pricing_tally_" + UUID.randomUUID() + ";DB_CLOSE_DELAY=-1";
    private static final JsonMapper JSON = JsonMapper.builder().build();

    @DynamicPropertySource
    static void datasourceProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", () -> DATABASE_URL);
    }

    @Autowired private MockMvc mockMvc;

    @Test
    void initialTripCreation_withBudget_returnsAuthoritativeZeroStateTally() throws Exception {
        Client owner = register("zero-state-tally@example.test");
        String payload = tripJson("destination-sfo", "2027-03-01", "2027-03-05", 2, 200_000L);

        owner.unsafe(post("/api/trips"), payload)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.tally.airfareTotalCents").value(0))
                .andExpect(jsonPath("$.tally.stayTotalCents").value(0))
                .andExpect(jsonPath("$.tally.rentalTotalCents").value(0))
                .andExpect(jsonPath("$.tally.grandTotalCents").value(0))
                .andExpect(jsonPath("$.tally.remainingBudgetCents").value(200_000))
                .andExpect(jsonPath("$.tally.budgetOverageCents").value(0))
                .andExpect(jsonPath("$.tally.isOverBudget").value(false))
                .andExpect(jsonPath("$.drafts[0].tally.airfareTotalCents").value(0))
                .andExpect(jsonPath("$.drafts[0].tally.stayTotalCents").value(0))
                .andExpect(jsonPath("$.drafts[0].tally.rentalTotalCents").value(0))
                .andExpect(jsonPath("$.drafts[0].tally.grandTotalCents").value(0))
                .andExpect(jsonPath("$.drafts[0].tally.remainingBudgetCents").value(200_000))
                .andExpect(jsonPath("$.drafts[0].tally.budgetOverageCents").value(0))
                .andExpect(jsonPath("$.drafts[0].tally.isOverBudget").value(false))
                .andExpect(jsonPath("$.alternatives[0].tally.grandTotalCents").value(0));
    }

    @Test
    void initialTripCreation_withoutBudget_returnsNullBudgetMetrics() throws Exception {
        Client owner = register("null-budget-tally@example.test");
        String payload = tripJson("destination-sfo", "2027-03-01", "2027-03-05", 2, null);

        owner.unsafe(post("/api/trips"), payload)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.tally.airfareTotalCents").value(0))
                .andExpect(jsonPath("$.tally.stayTotalCents").value(0))
                .andExpect(jsonPath("$.tally.rentalTotalCents").value(0))
                .andExpect(jsonPath("$.tally.grandTotalCents").value(0))
                .andExpect(jsonPath("$.tally.remainingBudgetCents").doesNotExist())
                .andExpect(jsonPath("$.tally.budgetOverageCents").doesNotExist())
                .andExpect(jsonPath("$.tally.isOverBudget").value(false))
                .andExpect(jsonPath("$.drafts[0].tally.remainingBudgetCents").doesNotExist())
                .andExpect(jsonPath("$.drafts[0].tally.budgetOverageCents").doesNotExist())
                .andExpect(jsonPath("$.drafts[0].tally.isOverBudget").value(false));
    }

    @Test
    void budgetValidationBoundaries_acceptsZeroAndRejectsNegativeOrOverMax() throws Exception {
        Client owner = register("budget-bounds-test@example.test");

        // 1. Budget = 0 cents is valid
        owner.unsafe(post("/api/trips"), tripJson("destination-sfo", "2027-03-01", "2027-03-05", 2, 0L))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.budgetCents").value(0))
                .andExpect(jsonPath("$.tally.grandTotalCents").value(0))
                .andExpect(jsonPath("$.tally.remainingBudgetCents").value(0))
                .andExpect(jsonPath("$.tally.budgetOverageCents").value(0))
                .andExpect(jsonPath("$.tally.isOverBudget").value(false));

        // 2. Budget = -1 cents rejected
        owner.unsafe(post("/api/trips"), tripJson("destination-sfo", "2027-03-01", "2027-03-05", 2, -1L))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.fields.budgetCents").value("Budget must be between 0 and 100000000 cents."));

        // 3. Budget = 100_000_001 cents rejected
        owner.unsafe(post("/api/trips"), tripJson("destination-sfo", "2027-03-01", "2027-03-05", 2, 100_000_001L))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.fields.budgetCents").value("Budget must be between 0 and 100000000 cents."));

        // 4. Budget = 100_000_000 cents (max) is valid
        owner.unsafe(post("/api/trips"), tripJson("destination-sfo", "2027-03-01", "2027-03-05", 2, 100_000_000L))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.budgetCents").value(100_000_000))
                .andExpect(jsonPath("$.tally.remainingBudgetCents").value(100_000_000));
    }

    @Test
    void airfareSelection_recalculatesAirfareTotalAndBudgetPosition() throws Exception {
        Client owner = register("airfare-pricing-test@example.test");
        long budget = 200_000L;
        int travelers = 2;
        MvcResult tripResult = owner.unsafe(post("/api/trips"), tripJson("destination-sfo", "2027-03-01", "2027-03-05", travelers, budget))
                .andExpect(status().isCreated()).andReturn();
        String tripId = jsonField(tripResult, "id");
        String draftId = JSON.readTree(tripResult.getResponse().getContentAsString()).get("drafts").get(0).get("id").asString();

        // Search airfare
        MvcResult searchRes = owner.unsafe(get("/api/trips/{tripId}/drafts/{draftId}/airfare", tripId, draftId), null)
                .andExpect(status().isOk()).andReturn();
        JsonNode firstOption = JSON.readTree(searchRes.getResponse().getContentAsString()).get("options").get(0);
        long outId = firstOption.get("outbound").get("flightInstanceId").asLong();
        long retId = firstOption.get("returnFlight").get("flightInstanceId").asLong();
        long expectedFlightTotal = firstOption.get("pricing").get("partyTotalPriceCents").asLong();

        // Select airfare
        String selectPayload = """
                {"expectedVersion":0,"expectedDraftVersion":0,"outboundFlightInstanceId":%d,"returnFlightInstanceId":%d}
                """.formatted(outId, retId);
        owner.unsafe(put("/api/trips/{tripId}/drafts/{draftId}/airfare", tripId, draftId), selectPayload)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tally.airfareTotalCents").value(expectedFlightTotal))
                .andExpect(jsonPath("$.tally.stayTotalCents").value(0))
                .andExpect(jsonPath("$.tally.rentalTotalCents").value(0))
                .andExpect(jsonPath("$.tally.grandTotalCents").value(expectedFlightTotal))
                .andExpect(jsonPath("$.tally.remainingBudgetCents").value(budget - expectedFlightTotal))
                .andExpect(jsonPath("$.tally.budgetOverageCents").value(0))
                .andExpect(jsonPath("$.tally.isOverBudget").value(false))
                .andExpect(jsonPath("$.drafts[0].tally.airfareTotalCents").value(expectedFlightTotal))
                .andExpect(jsonPath("$.drafts[0].tally.grandTotalCents").value(expectedFlightTotal));
    }

    @Test
    void staySelection_recalculatesStayTotalAndBudgetPosition() throws Exception {
        Client owner = register("stay-pricing-test@example.test");
        long budget = 200_000L;
        int travelers = 2;
        MvcResult tripResult = owner.unsafe(post("/api/trips"), tripJson("destination-sfo", "2027-03-01", "2027-03-05", travelers, budget))
                .andExpect(status().isCreated()).andReturn();
        String tripId = jsonField(tripResult, "id");
        String draftId = JSON.readTree(tripResult.getResponse().getContentAsString()).get("drafts").get(0).get("id").asString();

        // Search stay
        MvcResult searchRes = owner.unsafe(get("/api/trips/{tripId}/drafts/{draftId}/stays?type=HOTEL", tripId, draftId), null)
                .andExpect(status().isOk()).andReturn();
        JsonNode firstStay = JSON.readTree(searchRes.getResponse().getContentAsString()).get("options").get(0);
        long unitId = firstStay.get("accommodationUnitId").asLong();
        long expectedStayTotal = firstStay.get("pricing").get("totalPriceCents").asLong();

        // Select stay
        String selectPayload = """
                {"expectedVersion":0,"expectedDraftVersion":0,"accommodationUnitId":%d,"unitCount":1}
                """.formatted(unitId);
        owner.unsafe(put("/api/trips/{tripId}/drafts/{draftId}/stays", tripId, draftId), selectPayload)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tally.airfareTotalCents").value(0))
                .andExpect(jsonPath("$.tally.stayTotalCents").value(expectedStayTotal))
                .andExpect(jsonPath("$.tally.rentalTotalCents").value(0))
                .andExpect(jsonPath("$.tally.grandTotalCents").value(expectedStayTotal))
                .andExpect(jsonPath("$.tally.remainingBudgetCents").value(budget - expectedStayTotal))
                .andExpect(jsonPath("$.tally.budgetOverageCents").value(0))
                .andExpect(jsonPath("$.tally.isOverBudget").value(false))
                .andExpect(jsonPath("$.drafts[0].tally.stayTotalCents").value(expectedStayTotal));
    }

    @Test
    void rentalSelection_recalculatesRentalTotalAndBudgetPosition() throws Exception {
        Client owner = register("rental-pricing-test@example.test");
        long budget = 200_000L;
        int travelers = 2;
        MvcResult tripResult = owner.unsafe(post("/api/trips"), tripJson("destination-sfo", "2027-03-02", "2027-03-06", travelers, budget))
                .andExpect(status().isCreated()).andReturn();
        String tripId = jsonField(tripResult, "id");
        String draftId = JSON.readTree(tripResult.getResponse().getContentAsString()).get("drafts").get(0).get("id").asString();

        String pickupAt = "2027-03-02T10:00:00-08:00";
        String returnAt = "2027-03-06T10:00:00-08:00";

        // Search rental
        MvcResult searchRes = owner.unsafe(get("/api/trips/{tripId}/drafts/{draftId}/rentals?pickupAt={pickup}&returnAt={ret}", tripId, draftId, pickupAt, returnAt), null)
                .andExpect(status().isOk()).andReturn();
        JsonNode firstRental = JSON.readTree(searchRes.getResponse().getContentAsString()).get("options").get(0);
        long rentalUnitId = firstRental.get("rentalUnitId").asLong();
        long expectedRentalTotal = firstRental.get("pricing").get("totalPriceCents").asLong();

        // Select rental
        String selectPayload = """
                {"expectedVersion":0,"expectedDraftVersion":0,"rentalUnitId":%d,"pickupAt":"%s","returnAt":"%s"}
                """.formatted(rentalUnitId, pickupAt, returnAt);
        owner.unsafe(put("/api/trips/{tripId}/drafts/{draftId}/rentals", tripId, draftId), selectPayload)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tally.airfareTotalCents").value(0))
                .andExpect(jsonPath("$.tally.stayTotalCents").value(0))
                .andExpect(jsonPath("$.tally.rentalTotalCents").value(expectedRentalTotal))
                .andExpect(jsonPath("$.tally.grandTotalCents").value(expectedRentalTotal))
                .andExpect(jsonPath("$.tally.remainingBudgetCents").value(budget - expectedRentalTotal))
                .andExpect(jsonPath("$.tally.budgetOverageCents").value(0))
                .andExpect(jsonPath("$.tally.isOverBudget").value(false))
                .andExpect(jsonPath("$.drafts[0].tally.rentalTotalCents").value(expectedRentalTotal));
    }

    @Test
    void allSevenComponentCombinations_verifyConsistentServerCalculatedTallies() throws Exception {
        Client owner = register("combinations-test@example.test");
        long budget = 500_000L;
        int travelers = 2;
        MvcResult tripResult = owner.unsafe(post("/api/trips"), tripJson("destination-sfo", "2027-03-02", "2027-03-06", travelers, budget))
                .andExpect(status().isCreated()).andReturn();
        String tripId = jsonField(tripResult, "id");
        String draftId = JSON.readTree(tripResult.getResponse().getContentAsString()).get("drafts").get(0).get("id").asString();

        // 1. Get components to select
        MvcResult airfareSearch = owner.unsafe(get("/api/trips/{tripId}/drafts/{draftId}/airfare", tripId, draftId), null)
                .andExpect(status().isOk()).andReturn();
        JsonNode flight = JSON.readTree(airfareSearch.getResponse().getContentAsString()).get("options").get(0);
        long outId = flight.get("outbound").get("flightInstanceId").asLong();
        long retId = flight.get("returnFlight").get("flightInstanceId").asLong();
        long airfareTotal = flight.get("pricing").get("partyTotalPriceCents").asLong();

        MvcResult staySearch = owner.unsafe(get("/api/trips/{tripId}/drafts/{draftId}/stays?type=HOTEL", tripId, draftId), null)
                .andExpect(status().isOk()).andReturn();
        JsonNode stayOpt = JSON.readTree(staySearch.getResponse().getContentAsString()).get("options").get(0);
        long accommodationUnitId = stayOpt.get("accommodationUnitId").asLong();
        long stayTotal = stayOpt.get("pricing").get("totalPriceCents").asLong();

        String pickup = "2027-03-02T10:00:00-08:00";
        String returnAt = "2027-03-06T10:00:00-08:00";
        MvcResult rentalSearch = owner.unsafe(get("/api/trips/{tripId}/drafts/{draftId}/rentals?pickupAt={p}&returnAt={r}", tripId, draftId, pickup, returnAt), null)
                .andExpect(status().isOk()).andReturn();
        JsonNode carOpt = JSON.readTree(rentalSearch.getResponse().getContentAsString()).get("options").get(0);
        long rentalUnitId = carOpt.get("rentalUnitId").asLong();
        long rentalTotal = carOpt.get("pricing").get("totalPriceCents").asLong();

        long tripVersion = 0;
        long draftVersion = 0;

        // --- Combination 1: Airfare only ---
        MvcResult c1Result = owner.unsafe(put("/api/trips/{tripId}/drafts/{draftId}/airfare", tripId, draftId), """
                {"expectedVersion":%d,"expectedDraftVersion":%d,"outboundFlightInstanceId":%d,"returnFlightInstanceId":%d}
                """.formatted(tripVersion, draftVersion, outId, retId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tally.grandTotalCents").value(airfareTotal))
                .andExpect(jsonPath("$.tally.airfareTotalCents").value(airfareTotal))
                .andExpect(jsonPath("$.tally.stayTotalCents").value(0))
                .andExpect(jsonPath("$.tally.rentalTotalCents").value(0))
                .andReturn();
        tripVersion = JSON.readTree(c1Result.getResponse().getContentAsString()).get("version").asLong();
        draftVersion = JSON.readTree(c1Result.getResponse().getContentAsString()).get("drafts").get(0).get("version").asLong();

        // --- Combination 2: Airfare + Stay ---
        MvcResult c2Result = owner.unsafe(put("/api/trips/{tripId}/drafts/{draftId}/stays", tripId, draftId), """
                {"expectedVersion":%d,"expectedDraftVersion":%d,"accommodationUnitId":%d,"unitCount":1}
                """.formatted(tripVersion, draftVersion, accommodationUnitId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tally.grandTotalCents").value(airfareTotal + stayTotal))
                .andExpect(jsonPath("$.tally.airfareTotalCents").value(airfareTotal))
                .andExpect(jsonPath("$.tally.stayTotalCents").value(stayTotal))
                .andExpect(jsonPath("$.tally.rentalTotalCents").value(0))
                .andReturn();
        tripVersion = JSON.readTree(c2Result.getResponse().getContentAsString()).get("version").asLong();
        draftVersion = JSON.readTree(c2Result.getResponse().getContentAsString()).get("drafts").get(0).get("version").asLong();

        // --- Combination 3: All three (Airfare + Stay + Rental) ---
        MvcResult c3Result = owner.unsafe(put("/api/trips/{tripId}/drafts/{draftId}/rentals", tripId, draftId), """
                {"expectedVersion":%d,"expectedDraftVersion":%d,"rentalUnitId":%d,"pickupAt":"%s","returnAt":"%s"}
                """.formatted(tripVersion, draftVersion, rentalUnitId, pickup, returnAt))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tally.grandTotalCents").value(airfareTotal + stayTotal + rentalTotal))
                .andExpect(jsonPath("$.tally.airfareTotalCents").value(airfareTotal))
                .andExpect(jsonPath("$.tally.stayTotalCents").value(stayTotal))
                .andExpect(jsonPath("$.tally.rentalTotalCents").value(rentalTotal))
                .andExpect(jsonPath("$.tally.remainingBudgetCents").value(budget - (airfareTotal + stayTotal + rentalTotal)))
                .andReturn();
        tripVersion = JSON.readTree(c3Result.getResponse().getContentAsString()).get("version").asLong();
        draftVersion = JSON.readTree(c3Result.getResponse().getContentAsString()).get("drafts").get(0).get("version").asLong();

        // --- Combination 4: Stay + Rental (remove airfare) ---
        MvcResult c4Result = owner.unsafe(delete("/api/trips/{tripId}/drafts/{draftId}/airfare", tripId, draftId), """
                {"expectedVersion":%d,"expectedDraftVersion":%d}
                """.formatted(tripVersion, draftVersion))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tally.grandTotalCents").value(stayTotal + rentalTotal))
                .andExpect(jsonPath("$.tally.airfareTotalCents").value(0))
                .andExpect(jsonPath("$.tally.stayTotalCents").value(stayTotal))
                .andExpect(jsonPath("$.tally.rentalTotalCents").value(rentalTotal))
                .andReturn();
        tripVersion = JSON.readTree(c4Result.getResponse().getContentAsString()).get("version").asLong();
        draftVersion = JSON.readTree(c4Result.getResponse().getContentAsString()).get("drafts").get(0).get("version").asLong();

        // --- Combination 5: Rental only (remove stay) ---
        MvcResult c5Result = owner.unsafe(delete("/api/trips/{tripId}/drafts/{draftId}/stays", tripId, draftId), """
                {"expectedVersion":%d,"expectedDraftVersion":%d}
                """.formatted(tripVersion, draftVersion))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tally.grandTotalCents").value(rentalTotal))
                .andExpect(jsonPath("$.tally.airfareTotalCents").value(0))
                .andExpect(jsonPath("$.tally.stayTotalCents").value(0))
                .andExpect(jsonPath("$.tally.rentalTotalCents").value(rentalTotal))
                .andReturn();
        tripVersion = JSON.readTree(c5Result.getResponse().getContentAsString()).get("version").asLong();
        draftVersion = JSON.readTree(c5Result.getResponse().getContentAsString()).get("drafts").get(0).get("version").asLong();

        // --- Combination 6: Airfare + Rental (add airfare) ---
        MvcResult c6Result = owner.unsafe(put("/api/trips/{tripId}/drafts/{draftId}/airfare", tripId, draftId), """
                {"expectedVersion":%d,"expectedDraftVersion":%d,"outboundFlightInstanceId":%d,"returnFlightInstanceId":%d}
                """.formatted(tripVersion, draftVersion, outId, retId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tally.grandTotalCents").value(airfareTotal + rentalTotal))
                .andExpect(jsonPath("$.tally.airfareTotalCents").value(airfareTotal))
                .andExpect(jsonPath("$.tally.stayTotalCents").value(0))
                .andExpect(jsonPath("$.tally.rentalTotalCents").value(rentalTotal))
                .andReturn();
        tripVersion = JSON.readTree(c6Result.getResponse().getContentAsString()).get("version").asLong();
        draftVersion = JSON.readTree(c6Result.getResponse().getContentAsString()).get("drafts").get(0).get("version").asLong();

        // --- Combination 7: Stay only (remove rental, remove airfare, add stay) ---
        MvcResult removeRentalRes = owner.unsafe(delete("/api/trips/{tripId}/drafts/{draftId}/rentals", tripId, draftId), """
                {"expectedVersion":%d,"expectedDraftVersion":%d}
                """.formatted(tripVersion, draftVersion))
                .andExpect(status().isOk()).andReturn();
        tripVersion = JSON.readTree(removeRentalRes.getResponse().getContentAsString()).get("version").asLong();
        draftVersion = JSON.readTree(removeRentalRes.getResponse().getContentAsString()).get("drafts").get(0).get("version").asLong();

        MvcResult removeAirfareRes = owner.unsafe(delete("/api/trips/{tripId}/drafts/{draftId}/airfare", tripId, draftId), """
                {"expectedVersion":%d,"expectedDraftVersion":%d}
                """.formatted(tripVersion, draftVersion))
                .andExpect(status().isOk()).andReturn();
        tripVersion = JSON.readTree(removeAirfareRes.getResponse().getContentAsString()).get("version").asLong();
        draftVersion = JSON.readTree(removeAirfareRes.getResponse().getContentAsString()).get("drafts").get(0).get("version").asLong();

        owner.unsafe(put("/api/trips/{tripId}/drafts/{draftId}/stays", tripId, draftId), """
                {"expectedVersion":%d,"expectedDraftVersion":%d,"accommodationUnitId":%d,"unitCount":1}
                """.formatted(tripVersion, draftVersion, accommodationUnitId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tally.grandTotalCents").value(stayTotal))
                .andExpect(jsonPath("$.tally.airfareTotalCents").value(0))
                .andExpect(jsonPath("$.tally.stayTotalCents").value(stayTotal))
                .andExpect(jsonPath("$.tally.rentalTotalCents").value(0));
    }

    @Test
    void grandTotalExceedingBudget_setsIsOverBudgetAndOverageCents() throws Exception {
        Client owner = register("overage-test@example.test");
        long budget = 10_000L; // Low budget $100.00
        int travelers = 2;
        MvcResult tripResult = owner.unsafe(post("/api/trips"), tripJson("destination-sfo", "2027-03-01", "2027-03-05", travelers, budget))
                .andExpect(status().isCreated()).andReturn();
        String tripId = jsonField(tripResult, "id");
        String draftId = JSON.readTree(tripResult.getResponse().getContentAsString()).get("drafts").get(0).get("id").asString();

        // Search and select airfare (around $200-$400, well above $100)
        MvcResult searchRes = owner.unsafe(get("/api/trips/{tripId}/drafts/{draftId}/airfare", tripId, draftId), null)
                .andExpect(status().isOk()).andReturn();
        JsonNode flight = JSON.readTree(searchRes.getResponse().getContentAsString()).get("options").get(0);
        long outId = flight.get("outbound").get("flightInstanceId").asLong();
        long retId = flight.get("returnFlight").get("flightInstanceId").asLong();
        long flightTotal = flight.get("pricing").get("partyTotalPriceCents").asLong();
        assertTrue(flightTotal > budget, "Flight cost should exceed low budget");

        owner.unsafe(put("/api/trips/{tripId}/drafts/{draftId}/airfare", tripId, draftId), """
                {"expectedVersion":0,"expectedDraftVersion":0,"outboundFlightInstanceId":%d,"returnFlightInstanceId":%d}
                """.formatted(outId, retId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tally.grandTotalCents").value(flightTotal))
                .andExpect(jsonPath("$.tally.remainingBudgetCents").value(0))
                .andExpect(jsonPath("$.tally.budgetOverageCents").value(flightTotal - budget))
                .andExpect(jsonPath("$.tally.isOverBudget").value(true))
                .andExpect(jsonPath("$.drafts[0].tally.remainingBudgetCents").value(0))
                .andExpect(jsonPath("$.drafts[0].tally.budgetOverageCents").value(flightTotal - budget))
                .andExpect(jsonPath("$.drafts[0].tally.isOverBudget").value(true));
    }

    @Test
    void availableBudgetInComponentSearches_excludesSearchedComponent() throws Exception {
        Client owner = register("search-budget-exclusion@example.test");
        long budget = 200_000L;
        int travelers = 2;
        MvcResult tripResult = owner.unsafe(post("/api/trips"), tripJson("destination-sfo", "2027-03-02", "2027-03-06", travelers, budget))
                .andExpect(status().isCreated()).andReturn();
        String tripId = jsonField(tripResult, "id");
        String draftId = JSON.readTree(tripResult.getResponse().getContentAsString()).get("drafts").get(0).get("id").asString();

        // 1. Initial search: no selections -> available budget = 200,000
        owner.unsafe(get("/api/trips/{tripId}/drafts/{draftId}/stays?type=HOTEL", tripId, draftId), null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.availableTripBudgetCents").value(200_000L));

        // 2. Select airfare
        MvcResult airfareSearch = owner.unsafe(get("/api/trips/{tripId}/drafts/{draftId}/airfare", tripId, draftId), null).andReturn();
        JsonNode flight = JSON.readTree(airfareSearch.getResponse().getContentAsString()).get("options").get(0);
        long outId = flight.get("outbound").get("flightInstanceId").asLong();
        long retId = flight.get("returnFlight").get("flightInstanceId").asLong();
        long flightTotal = flight.get("pricing").get("partyTotalPriceCents").asLong();

        MvcResult selectAirfareRes = owner.unsafe(put("/api/trips/{tripId}/drafts/{draftId}/airfare", tripId, draftId), """
                {"expectedVersion":0,"expectedDraftVersion":0,"outboundFlightInstanceId":%d,"returnFlightInstanceId":%d}
                """.formatted(outId, retId))
                .andExpect(status().isOk()).andReturn();
        long tripVersion = JSON.readTree(selectAirfareRes.getResponse().getContentAsString()).get("version").asLong();
        long draftVersion = JSON.readTree(selectAirfareRes.getResponse().getContentAsString()).get("drafts").get(0).get("version").asLong();

        // 3. Search stays: available budget is 200,000 - flightTotal
        long expectedBudgetAfterFlight = budget - flightTotal;
        owner.unsafe(get("/api/trips/{tripId}/drafts/{draftId}/stays?type=HOTEL", tripId, draftId), null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.availableTripBudgetCents").value(expectedBudgetAfterFlight));

        // 4. Select a stay
        MvcResult staySearch = owner.unsafe(get("/api/trips/{tripId}/drafts/{draftId}/stays?type=HOTEL", tripId, draftId), null).andReturn();
        JsonNode stayOpt = JSON.readTree(staySearch.getResponse().getContentAsString()).get("options").get(0);
        long unitId = stayOpt.get("accommodationUnitId").asLong();

        MvcResult selectStayRes = owner.unsafe(put("/api/trips/{tripId}/drafts/{draftId}/stays", tripId, draftId), """
                {"expectedVersion":%d,"expectedDraftVersion":%d,"accommodationUnitId":%d,"unitCount":1}
                """.formatted(tripVersion, draftVersion, unitId))
                .andExpect(status().isOk()).andReturn();
        tripVersion = JSON.readTree(selectStayRes.getResponse().getContentAsString()).get("version").asLong();
        draftVersion = JSON.readTree(selectStayRes.getResponse().getContentAsString()).get("drafts").get(0).get("version").asLong();

        // 5. Search stays again (to replace stay): existing stay MUST NOT be deducted!
        owner.unsafe(get("/api/trips/{tripId}/drafts/{draftId}/stays?type=HOTEL", tripId, draftId), null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.availableTripBudgetCents").value(expectedBudgetAfterFlight));

        // 6. Search rentals: available budget deducts airfare AND stay
        long stayTotal = stayOpt.get("pricing").get("totalPriceCents").asLong();
        long expectedBudgetForRentals = budget - flightTotal - stayTotal;
        String pickup = "2027-03-02T10:00:00-08:00";
        String returnAt = "2027-03-06T10:00:00-08:00";
        owner.unsafe(get("/api/trips/{tripId}/drafts/{draftId}/rentals?pickupAt={p}&returnAt={r}", tripId, draftId, pickup, returnAt), null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.availableTripBudgetCents").value(expectedBudgetForRentals));
    }

    @Test
    void promoteDraftToPlanned_preservesAuthoritativeTallyOnPlannedResponse() throws Exception {
        Client owner = register("promote-tally-test@example.test");
        long budget = 250_000L;
        int travelers = 2;
        MvcResult tripResult = owner.unsafe(post("/api/trips"), tripJson("destination-sfo", "2027-03-01", "2027-03-05", travelers, budget))
                .andExpect(status().isCreated()).andReturn();
        String tripId = jsonField(tripResult, "id");
        String draftId = JSON.readTree(tripResult.getResponse().getContentAsString()).get("drafts").get(0).get("id").asString();

        // Select airfare on draft
        MvcResult airfareSearch = owner.unsafe(get("/api/trips/{tripId}/drafts/{draftId}/airfare", tripId, draftId), null).andReturn();
        JsonNode flight = JSON.readTree(airfareSearch.getResponse().getContentAsString()).get("options").get(0);
        long outId = flight.get("outbound").get("flightInstanceId").asLong();
        long retId = flight.get("returnFlight").get("flightInstanceId").asLong();
        long flightTotal = flight.get("pricing").get("partyTotalPriceCents").asLong();

        MvcResult putResult = owner.unsafe(put("/api/trips/{tripId}/drafts/{draftId}/airfare", tripId, draftId), """
                {"expectedVersion":0,"expectedDraftVersion":0,"outboundFlightInstanceId":%d,"returnFlightInstanceId":%d}
                """.formatted(outId, retId))
                .andExpect(status().isOk()).andReturn();

        long tripVersion = JSON.readTree(putResult.getResponse().getContentAsString()).get("version").asLong();
        long draftVersion = JSON.readTree(putResult.getResponse().getContentAsString()).get("drafts").get(0).get("version").asLong();

        // Promote draft to planned
        String planPayload = """
                {"expectedVersion":%d,"expectedDraftVersion":%d}
                """.formatted(tripVersion, draftVersion);
        MvcResult planResult = owner.unsafe(post("/api/trips/{tripId}/drafts/{draftId}/plan", tripId, draftId), planPayload)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.planned.length()").value(1))
                .andExpect(jsonPath("$.planned[0].tally.grandTotalCents").value(flightTotal))
                .andExpect(jsonPath("$.planned[0].tally.airfareTotalCents").value(flightTotal))
                .andExpect(jsonPath("$.planned[0].tally.remainingBudgetCents").value(budget - flightTotal))
                .andExpect(jsonPath("$.planned[0].tally.budgetOverageCents").value(0))
                .andExpect(jsonPath("$.planned[0].tally.isOverBudget").value(false))
                .andReturn();

        // Check alternatives array has tallies for both DRAFT and PLANNED
        JsonNode alts = JSON.readTree(planResult.getResponse().getContentAsString()).get("alternatives");
        assertTrue(alts.size() >= 2);
        for (JsonNode alt : alts) {
            assertTrue(alt.hasNonNull("tally"));
            assertTrue(alt.get("tally").hasNonNull("grandTotalCents"));
        }
    }

    @Test
    void multiUserIsolation_userCannotAccessOtherUserTripOrTally() throws Exception {
        Client owner = register("owner-iso-test@example.test");
        Client intruder = register("intruder-iso-test@example.test");

        MvcResult tripResult = owner.unsafe(post("/api/trips"), tripJson("destination-sfo", "2027-03-01", "2027-03-05", 2, 100_000L))
                .andExpect(status().isCreated()).andReturn();
        String tripId = jsonField(tripResult, "id");
        String draftId = JSON.readTree(tripResult.getResponse().getContentAsString()).get("drafts").get(0).get("id").asString();

        // 1. Intruder cannot GET trip details (and thus cannot read tallies)
        intruder.unsafe(get("/api/trips/{tripId}", tripId), null)
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("RESOURCE_NOT_FOUND"));

        // 2. Intruder cannot search draft airfare
        intruder.unsafe(get("/api/trips/{tripId}/drafts/{draftId}/airfare", tripId, draftId), null)
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("RESOURCE_NOT_FOUND"));

        // 3. Intruder cannot search draft stays
        intruder.unsafe(get("/api/trips/{tripId}/drafts/{draftId}/stays?type=HOTEL", tripId, draftId), null)
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("RESOURCE_NOT_FOUND"));

        // 4. Intruder cannot search draft rentals
        intruder.unsafe(get("/api/trips/{tripId}/drafts/{draftId}/rentals", tripId, draftId), null)
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("RESOURCE_NOT_FOUND"));
    }

    private static String tripJson(String destinationKey, String startDate, String endDate, int travelerCount, Long budgetCents) {
        String budgetStr = budgetCents == null ? "null" : budgetCents.toString();
        return """
                {
                    "destinationKey": "%s",
                    "startDate": "%s",
                    "endDate": "%s",
                    "travelerCount": %d,
                    "travelerAges": %s,
                    "budgetCents": %s
                }
                """.formatted(destinationKey, startDate, endDate, travelerCount,
                java.util.Collections.nCopies(travelerCount, 30).toString(), budgetStr);
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
