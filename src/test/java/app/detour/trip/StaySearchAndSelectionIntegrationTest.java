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
class StaySearchAndSelectionIntegrationTest {
    private static final String DATABASE_URL = "jdbc:h2:mem:stay_api_" + UUID.randomUUID() + ";DB_CLOSE_DELAY=-1";
    private static final JsonMapper JSON = JsonMapper.builder().build();

    @DynamicPropertySource
    static void datasourceProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", () -> DATABASE_URL);
    }

    @Autowired private MockMvc mockMvc;
    @Autowired private JdbcTemplate jdbc;

    @Test
    void searchStaysRequiresMandatoryAccommodationType() throws Exception {
        Client owner = register("mandatory-type-test@example.test");
        MvcResult tripResult = owner.unsafe(post("/api/trips"), tripJson("destination-sfo", "2027-03-01", "2027-03-05", 2, 100000L))
                .andExpect(status().isCreated()).andReturn();
        String tripId = jsonField(tripResult, "id");
        String draftId = JSON.readTree(tripResult.getResponse().getContentAsString()).get("drafts").get(0).get("id").asString();

        // 1. Omitted type parameter
        owner.unsafe(get("/api/trips/{tripId}/drafts/{draftId}/stays", tripId, draftId), null)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.fields.type").value("Choose a supported accommodation type."));

        // 2. Empty type parameter
        owner.unsafe(get("/api/trips/{tripId}/drafts/{draftId}/stays?type=", tripId, draftId), null)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.fields.type").value("Choose a supported accommodation type."));

        // 3. Unsupported type parameter
        owner.unsafe(get("/api/trips/{tripId}/drafts/{draftId}/stays?type=RESORT", tripId, draftId), null)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.fields.type").value("Choose a supported accommodation type."));

        // 4. Case-insensitive supported type succeeds
        owner.unsafe(get("/api/trips/{tripId}/drafts/{draftId}/stays?type=hotel", tripId, draftId), null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accommodationType").value("HOTEL"));
    }

    @Test
    void searchStaysReturnsAvailableUnitsForDestinationAndDates() throws Exception {
        Client owner = register("destination-dates-test@example.test");
        MvcResult tripResult = owner.unsafe(post("/api/trips"), tripJson("destination-sfo", "2027-03-01", "2027-03-05", 2, 100000L))
                .andExpect(status().isCreated()).andReturn();
        String tripId = jsonField(tripResult, "id");
        String draftId = JSON.readTree(tripResult.getResponse().getContentAsString()).get("drafts").get(0).get("id").asString();

        // Draft-scoped route
        MvcResult draftResult = owner.unsafe(get("/api/trips/{tripId}/drafts/{draftId}/stays?type=HOTEL", tripId, draftId), null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tripId").value(tripId))
                .andExpect(jsonPath("$.draftId").value(draftId))
                .andExpect(jsonPath("$.destinationKey").value("destination-sfo"))
                .andExpect(jsonPath("$.options.length()").value(2))
                .andReturn();

        JsonNode options = JSON.readTree(draftResult.getResponse().getContentAsString()).get("options");
        for (JsonNode opt : options) {
            assertTrue(opt.get("propertyCatalogKey").asString().startsWith("stay-sfo-"));
            assertEquals("HOTEL", opt.get("propertyCategory").asString());
            assertTrue(opt.get("pricing").get("nightCount").asInt() == 4);
        }

        // Trip-scoped route
        owner.unsafe(get("/api/trips/{tripId}/stays?type=HOTEL", tripId), null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tripId").value(tripId))
                .andExpect(jsonPath("$.draftId").doesNotExist())
                .andExpect(jsonPath("$.options.length()").value(2));

    }

    @Test
    void searchStaysCalculatesRoomCountForPartySize() throws Exception {
        Client owner = register("room-count-test@example.test");

        // Party of 1: Harbor (cap 2) requires 1 room, Summit (cap 4) requires 1 room
        MvcResult t1 = owner.unsafe(post("/api/trips"), tripJson("destination-sfo", "2027-03-01", "2027-03-05", 1, 500000L))
                .andExpect(status().isCreated()).andReturn();
        String tripId1 = jsonField(t1, "id");
        MvcResult res1 = owner.unsafe(get("/api/trips/{tripId}/stays?type=HOTEL", tripId1), null)
                .andExpect(status().isOk()).andReturn();
        JsonNode opts1 = JSON.readTree(res1.getResponse().getContentAsString()).get("options");
        for (JsonNode opt : opts1) {
            assertEquals(1, opt.get("pricing").get("requiredRooms").asInt());
        }

        // Party of 2: Harbor (cap 2) requires 1 room, Summit (cap 4) requires 1 room
        MvcResult t2 = owner.unsafe(post("/api/trips"), tripJson("destination-sfo", "2027-03-01", "2027-03-05", 2, 500000L))
                .andExpect(status().isCreated()).andReturn();
        String tripId2 = jsonField(t2, "id");
        MvcResult res2 = owner.unsafe(get("/api/trips/{tripId}/stays?type=HOTEL", tripId2), null)
                .andExpect(status().isOk()).andReturn();
        JsonNode opts2 = JSON.readTree(res2.getResponse().getContentAsString()).get("options");
        for (JsonNode opt : opts2) {
            assertEquals(1, opt.get("pricing").get("requiredRooms").asInt());
        }

        // Party of 3: Harbor (cap 2) requires ceil(3/2) = 2 rooms; Summit (cap 4) requires ceil(3/4) = 1 room
        MvcResult t3 = owner.unsafe(post("/api/trips"), tripJson("destination-sfo", "2027-03-01", "2027-03-05", 3, 500000L))
                .andExpect(status().isCreated()).andReturn();
        String tripId3 = jsonField(t3, "id");
        MvcResult res3 = owner.unsafe(get("/api/trips/{tripId}/stays?type=HOTEL", tripId3), null)
                .andExpect(status().isOk()).andReturn();
        JsonNode opts3 = JSON.readTree(res3.getResponse().getContentAsString()).get("options");
        for (JsonNode opt : opts3) {
            if ("stay-sfo-hotel-harbor".equals(opt.get("propertyCatalogKey").asString())) {
                assertEquals(2, opt.get("pricing").get("requiredRooms").asInt());
            } else if ("stay-sfo-hotel-summit".equals(opt.get("propertyCatalogKey").asString())) {
                assertEquals(1, opt.get("pricing").get("requiredRooms").asInt());
            }
        }

        // Party of 5: Harbor (cap 2) requires ceil(5/2) = 3 rooms; Summit (cap 4) requires ceil(5/4) = 2 rooms
        MvcResult t5 = owner.unsafe(post("/api/trips"), tripJson("destination-sfo", "2027-03-01", "2027-03-05", 5, 500000L))
                .andExpect(status().isCreated()).andReturn();
        String tripId5 = jsonField(t5, "id");
        MvcResult res5 = owner.unsafe(get("/api/trips/{tripId}/stays?type=HOTEL", tripId5), null)
                .andExpect(status().isOk()).andReturn();
        JsonNode opts5 = JSON.readTree(res5.getResponse().getContentAsString()).get("options");
        for (JsonNode opt : opts5) {
            if ("stay-sfo-hotel-harbor".equals(opt.get("propertyCatalogKey").asString())) {
                assertEquals(3, opt.get("pricing").get("requiredRooms").asInt());
            } else if ("stay-sfo-hotel-summit".equals(opt.get("propertyCatalogKey").asString())) {
                assertEquals(2, opt.get("pricing").get("requiredRooms").asInt());
            }
        }
    }

    @Test
    void searchStaysExcludesVacationRentalsWhenCapacityExceeded() throws Exception {
        Client owner = register("vacation-rental-test@example.test");

        // SFO vacation rentals: Sunset Courtyard Cottage (cap 4), Presidio Grand Home (cap 8)
        // 1. Party of 4: both Sunset (cap 4) and Presidio (cap 8) should be returned, each with requiredRooms = 1
        MvcResult t4 = owner.unsafe(post("/api/trips"), tripJson("destination-sfo", "2027-03-01", "2027-03-05", 4, 500000L))
                .andExpect(status().isCreated()).andReturn();
        String tripId4 = jsonField(t4, "id");
        MvcResult res4 = owner.unsafe(get("/api/trips/{tripId}/stays?type=VACATION_RENTAL", tripId4), null)
                .andExpect(status().isOk()).andReturn();
        JsonNode opts4 = JSON.readTree(res4.getResponse().getContentAsString()).get("options");
        assertEquals(2, opts4.size());
        for (JsonNode opt : opts4) {
            assertEquals(1, opt.get("pricing").get("requiredRooms").asInt());
        }

        // 2. Party of 5: Sunset (cap 4) is excluded; only Presidio (cap 8) is returned
        MvcResult t5 = owner.unsafe(post("/api/trips"), tripJson("destination-sfo", "2027-03-01", "2027-03-05", 5, 500000L))
                .andExpect(status().isCreated()).andReturn();
        String tripId5 = jsonField(t5, "id");
        MvcResult res5 = owner.unsafe(get("/api/trips/{tripId}/stays?type=VACATION_RENTAL", tripId5), null)
                .andExpect(status().isOk()).andReturn();
        JsonNode opts5 = JSON.readTree(res5.getResponse().getContentAsString()).get("options");
        assertEquals(1, opts5.size());
        assertEquals("stay-sfo-rental-presidio", opts5.get(0).get("propertyCatalogKey").asString());
        assertEquals(1, opts5.get(0).get("pricing").get("requiredRooms").asInt());
    }

    @Test
    void searchStaysExcludesUnitsWithInsufficientInventoryOnAnyNight() throws Exception {
        Client owner = register("inventory-filter-test@example.test");
        MvcResult tripResult = owner.unsafe(post("/api/trips"), tripJson("destination-sfo", "2027-03-01", "2027-03-05", 2, 500000L))
                .andExpect(status().isCreated()).andReturn();
        String tripId = jsonField(tripResult, "id");

        Long harborUnitId = jdbc.queryForObject("SELECT id FROM accommodation_unit WHERE catalog_key = 'stay-unit-sfo-hotel-harbor'", Long.class);
        assertNotNull(harborUnitId);

        // Before modification: 2 hotel options
        MvcResult initialResult = owner.unsafe(get("/api/trips/{tripId}/stays?type=HOTEL", tripId), null)
                .andExpect(status().isOk()).andReturn();
        assertEquals(2, JSON.readTree(initialResult.getResponse().getContentAsString()).get("options").size());

        // Zero out inventory for Harbor on night 3 (2027-03-03)
        jdbc.update("UPDATE accommodation_nightly_inventory SET available_inventory = 0 WHERE accommodation_unit_id = ? AND night_date = DATE '2027-03-03'", harborUnitId);
        try {
            MvcResult modifiedResult = owner.unsafe(get("/api/trips/{tripId}/stays?type=HOTEL", tripId), null)
                    .andExpect(status().isOk()).andReturn();
            JsonNode options = JSON.readTree(modifiedResult.getResponse().getContentAsString()).get("options");
            assertEquals(1, options.size());
            assertEquals("stay-sfo-hotel-summit", options.get(0).get("propertyCatalogKey").asString());
        } finally {
            // Restore inventory
            jdbc.update("UPDATE accommodation_nightly_inventory SET available_inventory = inventory_capacity WHERE accommodation_unit_id = ? AND night_date = DATE '2027-03-03'", harborUnitId);
        }

        // After restoring: 2 hotel options again
        MvcResult restoredResult = owner.unsafe(get("/api/trips/{tripId}/stays?type=HOTEL", tripId), null)
                .andExpect(status().isOk()).andReturn();
        assertEquals(2, JSON.readTree(restoredResult.getResponse().getContentAsString()).get("options").size());
    }

    @Test
    void searchStaysProvidesCompletePricingAndNightlyBreakdown() throws Exception {
        Client owner = register("pricing-breakdown-test@example.test");
        // 4-night stay, party of 3 -> Harbor requires 2 rooms
        MvcResult tripResult = owner.unsafe(post("/api/trips"), tripJson("destination-sfo", "2027-03-01", "2027-03-05", 3, 500000L))
                .andExpect(status().isCreated()).andReturn();
        String tripId = jsonField(tripResult, "id");

        MvcResult searchResult = owner.unsafe(get("/api/trips/{tripId}/stays?type=HOTEL", tripId), null)
                .andExpect(status().isOk()).andReturn();

        JsonNode options = JSON.readTree(searchResult.getResponse().getContentAsString()).get("options");
        JsonNode harborOption = null;
        for (JsonNode opt : options) {
            if ("stay-sfo-hotel-harbor".equals(opt.get("propertyCatalogKey").asString())) {
                harborOption = opt;
                break;
            }
        }
        assertNotNull(harborOption);

        JsonNode pricing = harborOption.get("pricing");
        assertEquals(2, pricing.get("requiredRooms").asInt());
        assertEquals(4, pricing.get("nightCount").asInt());

        // Harbor fixture rates: base = 17129, tax = 1371, fee = 500 -> 19000/night/room
        // For 4 nights per room:
        // perRoomBase: 4 * 17129 = 68516
        // perRoomTax: 4 * 1371 = 5484
        // perRoomFee: 4 * 500 = 2000
        // perRoomTotal: 76000
        assertEquals(68516L, pricing.get("perRoomBasePriceCents").asLong());
        assertEquals(5484L, pricing.get("perRoomTaxCents").asLong());
        assertEquals(2000L, pricing.get("perRoomFeeCents").asLong());
        assertEquals(76000L, pricing.get("perRoomTotalPriceCents").asLong());

        // For 2 rooms:
        // totalBase: 2 * 68516 = 137032
        // totalTax: 2 * 5484 = 10968
        // totalFee: 2 * 2000 = 4000
        // totalPrice: 2 * 76000 = 152000
        assertEquals(137032L, pricing.get("totalBasePriceCents").asLong());
        assertEquals(10968L, pricing.get("totalTaxCents").asLong());
        assertEquals(4000L, pricing.get("totalFeeCents").asLong());
        assertEquals(152000L, pricing.get("totalPriceCents").asLong());

        // Nightly breakdown itemized records
        JsonNode nights = pricing.get("nights");
        assertEquals(4, nights.size());
        assertEquals("2027-03-01", nights.get(0).get("date").asString());
        assertEquals(17129L, nights.get(0).get("basePriceCents").asLong());
        assertEquals(1371L, nights.get(0).get("taxCents").asLong());
        assertEquals(500L, nights.get(0).get("feeCents").asLong());
        assertEquals(19000L, nights.get(0).get("totalCents").asLong());
    }

    @Test
    void searchStaysCalculatesAvailableBudgetExcludingExistingStay() throws Exception {
        Client owner = register("available-budget-test@example.test");
        // Trip budget: 200,000 cents ($2,000)
        MvcResult tripResult = owner.unsafe(post("/api/trips"), tripJson("destination-sfo", "2027-03-01", "2027-03-05", 2, 200000L))
                .andExpect(status().isCreated()).andReturn();
        String tripId = jsonField(tripResult, "id");
        String draftId = JSON.readTree(tripResult.getResponse().getContentAsString()).get("drafts").get(0).get("id").asString();

        // 1. Initial search: no selections on draft -> available budget = 200,000 cents
        owner.unsafe(get("/api/trips/{tripId}/drafts/{draftId}/stays?type=HOTEL", tripId, draftId), null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.availableTripBudgetCents").value(200000L));

        // 2. Select airfare on the draft
        MvcResult airfareSearchResult = owner.unsafe(get("/api/trips/{tripId}/drafts/{draftId}/airfare", tripId, draftId), null)
                .andExpect(status().isOk()).andReturn();
        JsonNode firstFlight = JSON.readTree(airfareSearchResult.getResponse().getContentAsString()).get("options").get(0);
        long outId = firstFlight.get("outbound").get("flightInstanceId").asLong();
        long retId = firstFlight.get("returnFlight").get("flightInstanceId").asLong();
        long partyFlightTotal = firstFlight.get("pricing").get("partyTotalPriceCents").asLong();

        MvcResult airfareSelectResult = owner.unsafe(put("/api/trips/{tripId}/drafts/{draftId}/airfare", tripId, draftId), """
                {"expectedVersion":0,"expectedDraftVersion":0,"outboundFlightInstanceId":%d,"returnFlightInstanceId":%d}
                """.formatted(outId, retId))
                .andExpect(status().isOk()).andReturn();

        long tripVersion = JSON.readTree(airfareSelectResult.getResponse().getContentAsString()).get("version").asLong();
        long draftVersion = JSON.readTree(airfareSelectResult.getResponse().getContentAsString()).get("drafts").get(0).get("version").asLong();

        // 3. Search stays after airfare selection -> available budget = 200,000 - partyFlightTotal
        long expectedBudgetAfterFlight = 200000L - partyFlightTotal;
        owner.unsafe(get("/api/trips/{tripId}/drafts/{draftId}/stays?type=HOTEL", tripId, draftId), null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.availableTripBudgetCents").value(expectedBudgetAfterFlight));

        // 4. Also select a stay on the draft
        Long harborUnitId = jdbc.queryForObject("SELECT id FROM accommodation_unit WHERE catalog_key = 'stay-unit-sfo-hotel-harbor'", Long.class);
        owner.unsafe(put("/api/trips/{tripId}/drafts/{draftId}/stays", tripId, draftId), """
                {"expectedVersion":%d,"expectedDraftVersion":%d,"accommodationUnitId":%d}
                """.formatted(tripVersion, draftVersion, harborUnitId))
                .andExpect(status().isOk());

        // 5. Search stays again to replace the stay: existing stay must NOT be subtracted
        owner.unsafe(get("/api/trips/{tripId}/drafts/{draftId}/stays?type=HOTEL", tripId, draftId), null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.availableTripBudgetCents").value(expectedBudgetAfterFlight));
    }

    @Test
    void searchStaysDefaultRankingPlacesWithinBudgetBeforeOverBudget() throws Exception {
        Client owner = register("budget-tiering-test@example.test");
        // 4 nights, 2 travelers.
        // Harbor total = 76,000 cents; rating = 4.3; distance = 550m
        // Summit total = 130,000 cents; rating = 4.7; distance = 1400m
        // Setting budget to 100,000 cents:
        // Harbor fits (Tier 1); Summit does not fit (Tier 2).
        MvcResult tripResult = owner.unsafe(post("/api/trips"), tripJson("destination-sfo", "2027-03-01", "2027-03-05", 2, 100000L))
                .andExpect(status().isCreated()).andReturn();
        String tripId = jsonField(tripResult, "id");

        MvcResult searchResult = owner.unsafe(get("/api/trips/{tripId}/stays?type=HOTEL", tripId), null)
                .andExpect(status().isOk()).andReturn();

        JsonNode options = JSON.readTree(searchResult.getResponse().getContentAsString()).get("options");
        assertEquals(2, options.size());

        // First option is Harbor (fitsBudget = true) despite lower rating
        assertEquals("stay-sfo-hotel-harbor", options.get(0).get("propertyCatalogKey").asString());
        assertTrue(options.get(0).get("fitsBudget").asBoolean());

        // Second option is Summit (fitsBudget = false)
        assertEquals("stay-sfo-hotel-summit", options.get(1).get("propertyCatalogKey").asString());
        assertFalse(options.get(1).get("fitsBudget").asBoolean());
    }

    @Test
    void searchStaysDefaultRankingWithoutTripBudgetOrdersDirectly() throws Exception {
        Client owner = register("no-budget-ranking-test@example.test");
        // Trip with budgetCents = null
        MvcResult tripResult = owner.unsafe(post("/api/trips"), tripJson("destination-sfo", "2027-03-01", "2027-03-05", 2, null))
                .andExpect(status().isCreated()).andReturn();
        String tripId = jsonField(tripResult, "id");

        MvcResult searchResult = owner.unsafe(get("/api/trips/{tripId}/stays?type=HOTEL", tripId), null)
                .andExpect(status().isOk()).andReturn();

        JsonNode response = JSON.readTree(searchResult.getResponse().getContentAsString());
        assertTrue(response.get("availableTripBudgetCents").isNull());

        JsonNode options = response.get("options");
        assertEquals(2, options.size());

        // When budget is null, budget tiering is omitted and results order directly by rating desc:
        // Summit rating (4.7) > Harbor rating (4.3)
        assertEquals("stay-sfo-hotel-summit", options.get(0).get("propertyCatalogKey").asString());
        assertTrue(options.get(0).get("fitsBudget").isNull());

        assertEquals("stay-sfo-hotel-harbor", options.get(1).get("propertyCatalogKey").asString());
        assertTrue(options.get(1).get("fitsBudget").isNull());
    }

    @Test
    void searchStaysSortOverridesOrderResultsDeterministically() throws Exception {
        Client owner = register("sort-overrides-test@example.test");
        // SFO Hotels: Harbor ($760, rating 4.3, dist 550m), Summit ($1300, rating 4.7, dist 1400m)
        MvcResult tripResult = owner.unsafe(post("/api/trips"), tripJson("destination-sfo", "2027-03-01", "2027-03-05", 2, 500000L))
                .andExpect(status().isCreated()).andReturn();
        String tripId = jsonField(tripResult, "id");

        // 1. LOWEST_PRICE: Harbor ($760) before Summit ($1300)
        MvcResult priceResult = owner.unsafe(get("/api/trips/{tripId}/stays?type=HOTEL&sort=LOWEST_PRICE", tripId), null)
                .andExpect(status().isOk()).andReturn();
        JsonNode priceOpts = JSON.readTree(priceResult.getResponse().getContentAsString()).get("options");
        assertEquals("stay-sfo-hotel-harbor", priceOpts.get(0).get("propertyCatalogKey").asString());
        assertEquals("stay-sfo-hotel-summit", priceOpts.get(1).get("propertyCatalogKey").asString());

        // 2. HIGHEST_RATING: Summit (4.7) before Harbor (4.3)
        MvcResult ratingResult = owner.unsafe(get("/api/trips/{tripId}/stays?type=HOTEL&sort=HIGHEST_RATING", tripId), null)
                .andExpect(status().isOk()).andReturn();
        JsonNode ratingOpts = JSON.readTree(ratingResult.getResponse().getContentAsString()).get("options");
        assertEquals("stay-sfo-hotel-summit", ratingOpts.get(0).get("propertyCatalogKey").asString());
        assertEquals("stay-sfo-hotel-harbor", ratingOpts.get(1).get("propertyCatalogKey").asString());

        // 3. NEAREST_CITY_CENTER: Harbor (550m) before Summit (1400m)
        MvcResult distanceResult = owner.unsafe(get("/api/trips/{tripId}/stays?type=HOTEL&sort=NEAREST_CITY_CENTER", tripId), null)
                .andExpect(status().isOk()).andReturn();
        JsonNode distOpts = JSON.readTree(distanceResult.getResponse().getContentAsString()).get("options");
        assertEquals("stay-sfo-hotel-harbor", distOpts.get(0).get("propertyCatalogKey").asString());
        assertEquals("stay-sfo-hotel-summit", distOpts.get(1).get("propertyCatalogKey").asString());

        // 4. Invalid sort override
        owner.unsafe(get("/api/trips/{tripId}/stays?type=HOTEL&sort=CHEAPEST", tripId), null)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.fields.sort").value("Choose a supported sort option."));
    }

    @Test
    void selectDraftStayPersistsUnitAndCalculatedRoomCount() throws Exception {
        Client owner = register("persist-selection-test@example.test");
        MvcResult tripResult = owner.unsafe(post("/api/trips"), tripJson("destination-sfo", "2027-03-01", "2027-03-05", 2, 500000L))
                .andExpect(status().isCreated()).andReturn();
        String tripId = jsonField(tripResult, "id");
        String draftId = JSON.readTree(tripResult.getResponse().getContentAsString()).get("drafts").get(0).get("id").asString();

        Long harborUnitId = jdbc.queryForObject("SELECT id FROM accommodation_unit WHERE catalog_key = 'stay-unit-sfo-hotel-harbor'", Long.class);

        // Perform PUT /api/trips/{tripId}/drafts/{draftId}/stay
        MvcResult selectResult = owner.unsafe(put("/api/trips/{tripId}/drafts/{draftId}/stays", tripId, draftId), """
                {"expectedVersion":0,"expectedDraftVersion":0,"accommodationUnitId":%d}
                """.formatted(harborUnitId))
                .andExpect(status().isOk()).andReturn();

        JsonNode response = JSON.readTree(selectResult.getResponse().getContentAsString());
        assertEquals(1, response.get("version").asLong());

        JsonNode draft = response.get("drafts").get(0);
        assertEquals(1, draft.get("version").asLong());

        JsonNode stay = draft.get("selections").get("stay");
        assertNotNull(stay);
        assertEquals(harborUnitId.longValue(), stay.get("accommodationUnitId").asLong());
        assertEquals(1, stay.get("unitCount").asInt());
        assertEquals("Harbor Civic Hotel", stay.get("propertyName").asString());
        assertEquals("Harbor King Room", stay.get("unitName").asString());

        JsonNode nights = stay.get("nights");
        assertEquals(4, nights.size());
        assertEquals("2027-03-01", nights.get(0).get("date").asString());
        assertEquals(17129L, nights.get(0).get("basePriceCents").asLong());

        // Verify DB row
        Integer count = jdbc.queryForObject("SELECT COUNT(*) FROM detour_trip_draft_stay_selection WHERE draft_id = (SELECT id FROM detour_trip_draft WHERE public_id = ?)",
                Integer.class, UUID.fromString(draftId));
        assertEquals(1, count);
    }

    @Test
    void selectDraftStayReplacesExistingSelectionWithoutDuplicates() throws Exception {
        Client owner = register("replace-selection-test@example.test");
        MvcResult tripResult = owner.unsafe(post("/api/trips"), tripJson("destination-sfo", "2027-03-01", "2027-03-05", 2, 500000L))
                .andExpect(status().isCreated()).andReturn();
        String tripId = jsonField(tripResult, "id");
        String draftId = JSON.readTree(tripResult.getResponse().getContentAsString()).get("drafts").get(0).get("id").asString();

        Long harborUnitId = jdbc.queryForObject("SELECT id FROM accommodation_unit WHERE catalog_key = 'stay-unit-sfo-hotel-harbor'", Long.class);
        Long summitUnitId = jdbc.queryForObject("SELECT id FROM accommodation_unit WHERE catalog_key = 'stay-unit-sfo-hotel-summit'", Long.class);

        // 1. Select Harbor
        owner.unsafe(put("/api/trips/{tripId}/drafts/{draftId}/stays", tripId, draftId), """
                {"expectedVersion":0,"expectedDraftVersion":0,"accommodationUnitId":%d}
                """.formatted(harborUnitId))
                .andExpect(status().isOk());

        // 2. Replace with Summit
        MvcResult replaceResult = owner.unsafe(put("/api/trips/{tripId}/drafts/{draftId}/stays", tripId, draftId), """
                {"expectedVersion":1,"expectedDraftVersion":1,"accommodationUnitId":%d}
                """.formatted(summitUnitId))
                .andExpect(status().isOk()).andReturn();

        JsonNode response = JSON.readTree(replaceResult.getResponse().getContentAsString());
        assertEquals(2, response.get("version").asLong());
        JsonNode draft = response.get("drafts").get(0);
        assertEquals(2, draft.get("version").asLong());
        assertEquals("Summit Family Suites", draft.get("selections").get("stay").get("propertyName").asString());

        // 3. Exactly 1 row in detour_trip_draft_stay_selection
        Integer count = jdbc.queryForObject("SELECT COUNT(*) FROM detour_trip_draft_stay_selection WHERE draft_id = (SELECT id FROM detour_trip_draft WHERE public_id = ?)",
                Integer.class, UUID.fromString(draftId));
        assertEquals(1, count);
    }

    @Test
    void removeDraftStayDeletesSelectionAndAdvancesVersions() throws Exception {
        Client owner = register("remove-selection-test@example.test");
        MvcResult tripResult = owner.unsafe(post("/api/trips"), tripJson("destination-sfo", "2027-03-01", "2027-03-05", 2, 500000L))
                .andExpect(status().isCreated()).andReturn();
        String tripId = jsonField(tripResult, "id");
        String draftId = JSON.readTree(tripResult.getResponse().getContentAsString()).get("drafts").get(0).get("id").asString();

        Long harborUnitId = jdbc.queryForObject("SELECT id FROM accommodation_unit WHERE catalog_key = 'stay-unit-sfo-hotel-harbor'", Long.class);

        // 1. Select Harbor
        owner.unsafe(put("/api/trips/{tripId}/drafts/{draftId}/stays", tripId, draftId), """
                {"expectedVersion":0,"expectedDraftVersion":0,"accommodationUnitId":%d}
                """.formatted(harborUnitId))
                .andExpect(status().isOk());

        // 2. Remove stay selection
        MvcResult removeResult = owner.unsafe(delete("/api/trips/{tripId}/drafts/{draftId}/stays", tripId, draftId), """
                {"expectedVersion":1,"expectedDraftVersion":1}
                """)
                .andExpect(status().isOk()).andReturn();

        JsonNode response = JSON.readTree(removeResult.getResponse().getContentAsString());
        assertEquals(2, response.get("version").asLong());
        JsonNode draft = response.get("drafts").get(0);
        assertEquals(2, draft.get("version").asLong());
        assertTrue(draft.get("selections").get("stay").isNull());

        // 3. DB row deleted
        Integer count = jdbc.queryForObject("SELECT COUNT(*) FROM detour_trip_draft_stay_selection WHERE draft_id = (SELECT id FROM detour_trip_draft WHERE public_id = ?)",
                Integer.class, UUID.fromString(draftId));
        assertEquals(0, count);
    }

    @Test
    void selectionMutationsEnforceOptimisticConcurrencyOnTripAndDraft() throws Exception {
        Client owner = register("concurrency-test@example.test");
        MvcResult tripResult = owner.unsafe(post("/api/trips"), tripJson("destination-sfo", "2027-03-01", "2027-03-05", 2, 500000L))
                .andExpect(status().isCreated()).andReturn();
        String tripId = jsonField(tripResult, "id");
        String draftId = JSON.readTree(tripResult.getResponse().getContentAsString()).get("drafts").get(0).get("id").asString();

        Long harborUnitId = jdbc.queryForObject("SELECT id FROM accommodation_unit WHERE catalog_key = 'stay-unit-sfo-hotel-harbor'", Long.class);

        // 1. PUT with stale expectedVersion
        owner.unsafe(put("/api/trips/{tripId}/drafts/{draftId}/stays", tripId, draftId), """
                {"expectedVersion":99,"expectedDraftVersion":0,"accommodationUnitId":%d}
                """.formatted(harborUnitId))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("VERSION_CONFLICT"));

        // 2. PUT with stale expectedDraftVersion
        owner.unsafe(put("/api/trips/{tripId}/drafts/{draftId}/stays", tripId, draftId), """
                {"expectedVersion":0,"expectedDraftVersion":99,"accommodationUnitId":%d}
                """.formatted(harborUnitId))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("VERSION_CONFLICT"));

        // 3. Successful PUT advances versions to 1
        owner.unsafe(put("/api/trips/{tripId}/drafts/{draftId}/stays", tripId, draftId), """
                {"expectedVersion":0,"expectedDraftVersion":0,"accommodationUnitId":%d}
                """.formatted(harborUnitId))
                .andExpect(status().isOk());

        // 4. DELETE with stale expectedVersion
        owner.unsafe(delete("/api/trips/{tripId}/drafts/{draftId}/stays", tripId, draftId), """
                {"expectedVersion":99,"expectedDraftVersion":1}
                """)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("VERSION_CONFLICT"));

        // 5. DELETE with stale expectedDraftVersion
        owner.unsafe(delete("/api/trips/{tripId}/drafts/{draftId}/stays", tripId, draftId), """
                {"expectedVersion":1,"expectedDraftVersion":99}
                """)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("VERSION_CONFLICT"));
    }

    @Test
    void unownedTripOrDraftRejectsSearchAndMutationWithNotFound() throws Exception {
        Client owner = register("owner-user@example.test");
        Client intruder = register("intruder-user@example.test");

        MvcResult tripResult = owner.unsafe(post("/api/trips"), tripJson("destination-sfo", "2027-03-01", "2027-03-05", 2, 500000L))
                .andExpect(status().isCreated()).andReturn();
        String tripId = jsonField(tripResult, "id");
        String draftId = JSON.readTree(tripResult.getResponse().getContentAsString()).get("drafts").get(0).get("id").asString();

        Long harborUnitId = jdbc.queryForObject("SELECT id FROM accommodation_unit WHERE catalog_key = 'stay-unit-sfo-hotel-harbor'", Long.class);

        // 1. Search on foreign trip/draft returns 404
        intruder.unsafe(get("/api/trips/{tripId}/drafts/{draftId}/stays?type=HOTEL", tripId, draftId), null)
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("RESOURCE_NOT_FOUND"));

        intruder.unsafe(get("/api/trips/{tripId}/stays?type=HOTEL", tripId), null)
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("RESOURCE_NOT_FOUND"));

        // 2. Selection mutation on foreign draft returns 404
        intruder.unsafe(put("/api/trips/{tripId}/drafts/{draftId}/stays", tripId, draftId), """
                {"expectedVersion":0,"expectedDraftVersion":0,"accommodationUnitId":%d}
                """.formatted(harborUnitId))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("RESOURCE_NOT_FOUND"));

        // 3. Removal on foreign draft returns 404
        intruder.unsafe(delete("/api/trips/{tripId}/drafts/{draftId}/stays", tripId, draftId), """
                {"expectedVersion":0,"expectedDraftVersion":0}
                """)
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("RESOURCE_NOT_FOUND"));

        // 4. Response shape matches non-existent UUID
        UUID nonexistentTrip = UUID.randomUUID();
        UUID nonexistentDraft = UUID.randomUUID();
        intruder.unsafe(get("/api/trips/{tripId}/drafts/{draftId}/stays?type=HOTEL", nonexistentTrip, nonexistentDraft), null)
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
