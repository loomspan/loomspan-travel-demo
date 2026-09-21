package app.detour.trip;

import static org.junit.jupiter.api.Assertions.assertEquals;
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
class AirfareSearchAndSelectionIntegrationTest {
    private static final String DATABASE_URL = "jdbc:h2:mem:airfare_api_" + UUID.randomUUID() + ";DB_CLOSE_DELAY=-1";
    private static final JsonMapper JSON = JsonMapper.builder().build();

    @DynamicPropertySource
    static void datasourceProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", () -> DATABASE_URL);
    }

    @Autowired private MockMvc mockMvc;
    @Autowired private JdbcTemplate jdbc;

    @Test
    void searchRoundTripAirfareReturnsAvailableCombinationsForTripDestinationAndDates() throws Exception {
        Client owner = register("search-test@example.test");
        MvcResult tripResult = owner.unsafe(post("/api/trips"), tripJson("destination-sfo", "2027-03-01", "2027-03-05", 2))
                .andExpect(status().isCreated()).andReturn();

        String tripId = jsonField(tripResult, "id");
        String draftId = JSON.readTree(tripResult.getResponse().getContentAsString())
                .get("drafts").get(0).get("id").asString();

        // 1. Search under draft path
        MvcResult draftSearchResult = owner.unsafe(get("/api/trips/{tripId}/drafts/{draftId}/airfare", tripId, draftId), null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tripId").value(tripId))
                .andExpect(jsonPath("$.draftId").value(draftId))
                .andExpect(jsonPath("$.destinationKey").value("destination-sfo"))
                .andExpect(jsonPath("$.originAirportCode").value("PDX"))
                .andExpect(jsonPath("$.destinationAirportCode").value("SFO"))
                .andExpect(jsonPath("$.startDate").value("2027-03-01"))
                .andExpect(jsonPath("$.endDate").value("2027-03-05"))
                .andExpect(jsonPath("$.travelerCount").value(2))
                .andExpect(jsonPath("$.options.length()").value(16))
                .andReturn();

        JsonNode options = JSON.readTree(draftSearchResult.getResponse().getContentAsString()).get("options");
        JsonNode firstOption = options.get(0);
        assertTrue(firstOption.hasNonNull("combinationKey"));
        assertTrue(firstOption.get("totalDurationMinutes").asLong() > 0);
        assertTrue(firstOption.has("direct"));

        JsonNode outbound = firstOption.get("outbound");
        assertEquals("PDX", outbound.get("originAirportCode").asString());
        assertEquals("SFO", outbound.get("destinationAirportCode").asString());
        assertEquals("America/Los_Angeles", outbound.get("departureTimeZone").asString());
        assertEquals("America/Los_Angeles", outbound.get("arrivalTimeZone").asString());
        assertTrue(outbound.get("carrier").asString().equals("Cascade Skies") || outbound.get("carrier").asString().equals("Meridian Air"));
        assertTrue(outbound.get("flightNumber").asString().startsWith("CS") || outbound.get("flightNumber").asString().startsWith("MA"));
        assertTrue(outbound.get("availableSeats").asInt() >= 2);
        assertTrue(outbound.get("totalFareCents").asLong() > 0);

        JsonNode ret = firstOption.get("returnFlight");
        assertEquals("SFO", ret.get("originAirportCode").asString());
        assertEquals("PDX", ret.get("destinationAirportCode").asString());
        assertTrue(ret.get("availableSeats").asInt() >= 2);

        // 2. Search under trip path (draftId is null)
        owner.unsafe(get("/api/trips/{tripId}/airfare", tripId), null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tripId").value(tripId))
                .andExpect(jsonPath("$.draftId").doesNotExist())
                .andExpect(jsonPath("$.options.length()").value(16));
    }

    @Test
    void searchExcludesCombinationsWithInsufficientAvailableSeats() throws Exception {
        Client owner = register("capacity-test@example.test");
        MvcResult tripResult = owner.unsafe(post("/api/trips"), tripJson("destination-sfo", "2027-03-02", "2027-03-06", 6))
                .andExpect(status().isCreated()).andReturn();
        String tripId = jsonField(tripResult, "id");
        String draftId = JSON.readTree(tripResult.getResponse().getContentAsString()).get("drafts").get(0).get("id").asString();

        // Reduce available seats on one outbound instance to 5 (less than traveler count of 6)
        Long restrictedInstanceId = jdbc.queryForObject("""
                SELECT instance.id FROM flight_instance instance
                JOIN flight_schedule schedule ON schedule.id = instance.flight_schedule_id
                WHERE schedule.catalog_key = 'airfare-out-sfo-d1' AND instance.service_date = DATE '2027-03-02'
                """, Long.class);
        assertNotNull(restrictedInstanceId);
        jdbc.update("UPDATE flight_instance SET available_seats = 5 WHERE id = ?", restrictedInstanceId);

        try {
            MvcResult searchResult = owner.unsafe(get("/api/trips/{tripId}/drafts/{draftId}/airfare", tripId, draftId), null)
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.options.length()").value(12)) // 3 outbound x 4 return = 12
                    .andReturn();

            JsonNode options = JSON.readTree(searchResult.getResponse().getContentAsString()).get("options");
            for (JsonNode option : options) {
                long outId = option.get("outbound").get("flightInstanceId").asLong();
                assertTrue(outId != restrictedInstanceId, "Option should not contain flight with insufficient capacity");
            }
        } finally {
            jdbc.update("UPDATE flight_instance SET available_seats = seat_capacity WHERE id = ?", restrictedInstanceId);
        }
    }

    @Test
    void directOnlyFilterRestrictsResultsToDirectFlights() throws Exception {
        Client owner = register("direct-filter-test@example.test");
        MvcResult tripResult = owner.unsafe(post("/api/trips"), tripJson("destination-sfo", "2027-03-03", "2027-03-07", 2))
                .andExpect(status().isCreated()).andReturn();
        String tripId = jsonField(tripResult, "id");

        // directOnly=true returns 4 direct-only combinations (2 direct outbound x 2 direct return)
        MvcResult directResult = owner.unsafe(get("/api/trips/{tripId}/airfare?directOnly=true", tripId), null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.directOnly").value(true))
                .andExpect(jsonPath("$.options.length()").value(4))
                .andReturn();

        JsonNode options = JSON.readTree(directResult.getResponse().getContentAsString()).get("options");
        for (JsonNode option : options) {
            assertTrue(option.get("direct").asBoolean(), "Every option must be direct when directOnly=true");
            assertEquals(0, option.get("outbound").get("stopCount").asInt());
            assertEquals(0, option.get("returnFlight").get("stopCount").asInt());
            assertTrue(option.get("outbound").get("layover").isNull());
            assertTrue(option.get("returnFlight").get("layover").isNull());
        }

        // directOnly=false returns 16 combinations
        owner.unsafe(get("/api/trips/{tripId}/airfare?directOnly=false", tripId), null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.directOnly").value(false))
                .andExpect(jsonPath("$.options.length()").value(16));
    }

    @Test
    void defaultRankingOrdersDirectFirstThenPriceThenDurationThenTieBreaker() throws Exception {
        Client owner = register("default-sort-test@example.test");
        MvcResult tripResult = owner.unsafe(post("/api/trips"), tripJson("destination-sfo", "2027-03-04", "2027-03-08", 2))
                .andExpect(status().isCreated()).andReturn();
        String tripId = jsonField(tripResult, "id");

        MvcResult result = owner.unsafe(get("/api/trips/{tripId}/airfare", tripId), null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.options.length()").value(16))
                .andReturn();

        JsonNode options = JSON.readTree(result.getResponse().getContentAsString()).get("options");

        // The first 4 options must be direct flights
        for (int i = 0; i < 4; i++) {
            assertTrue(options.get(i).get("direct").asBoolean(), "First 4 options must be direct");
        }
        // The remaining 12 options must have direct=false
        for (int i = 4; i < 16; i++) {
            assertTrue(!options.get(i).get("direct").asBoolean(), "Remaining 12 options must be connecting");
        }

        // Within direct options, party price must be non-decreasing
        for (int i = 0; i < 3; i++) {
            long priceA = options.get(i).get("pricing").get("partyTotalPriceCents").asLong();
            long priceB = options.get(i + 1).get("pricing").get("partyTotalPriceCents").asLong();
            assertTrue(priceA <= priceB, "Direct options must be sorted by price");
        }

        // Within connecting options, party price must be non-decreasing
        for (int i = 4; i < 15; i++) {
            long priceA = options.get(i).get("pricing").get("partyTotalPriceCents").asLong();
            long priceB = options.get(i + 1).get("pricing").get("partyTotalPriceCents").asLong();
            assertTrue(priceA <= priceB, "Connecting options must be sorted by price");
        }
    }

    @Test
    void sortOverridesApplyDeterministicallyWithCombinationKeyTieBreaker() throws Exception {
        Client owner = register("sort-overrides-test@example.test");
        MvcResult tripResult = owner.unsafe(post("/api/trips"), tripJson("destination-muc", "2027-03-05", "2027-03-10", 2))
                .andExpect(status().isCreated()).andReturn();
        String tripId = jsonField(tripResult, "id");

        // 1. LOWEST_PRICE
        MvcResult priceResult = owner.unsafe(get("/api/trips/{tripId}/airfare?sort=LOWEST_PRICE", tripId), null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sort").value("LOWEST_PRICE"))
                .andReturn();
        JsonNode priceOptions = JSON.readTree(priceResult.getResponse().getContentAsString()).get("options");
        for (int i = 0; i < priceOptions.size() - 1; i++) {
            long p1 = priceOptions.get(i).get("pricing").get("partyTotalPriceCents").asLong();
            long p2 = priceOptions.get(i + 1).get("pricing").get("partyTotalPriceCents").asLong();
            assertTrue(p1 <= p2, "LOWEST_PRICE must have non-decreasing partyTotalPriceCents");
        }

        // 2. SHORTEST_DURATION
        MvcResult durResult = owner.unsafe(get("/api/trips/{tripId}/airfare?sort=SHORTEST_DURATION", tripId), null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sort").value("SHORTEST_DURATION"))
                .andReturn();
        JsonNode durOptions = JSON.readTree(durResult.getResponse().getContentAsString()).get("options");
        for (int i = 0; i < durOptions.size() - 1; i++) {
            long d1 = durOptions.get(i).get("totalDurationMinutes").asLong();
            long d2 = durOptions.get(i + 1).get("totalDurationMinutes").asLong();
            assertTrue(d1 <= d2, "SHORTEST_DURATION must have non-decreasing totalDurationMinutes");
        }

        // 3. EARLIEST_DEPARTURE
        MvcResult depResult = owner.unsafe(get("/api/trips/{tripId}/airfare?sort=EARLIEST_DEPARTURE", tripId), null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sort").value("EARLIEST_DEPARTURE"))
                .andReturn();
        JsonNode depOptions = JSON.readTree(depResult.getResponse().getContentAsString()).get("options");
        for (int i = 0; i < depOptions.size() - 1; i++) {
            OffsetDateTime t1 = OffsetDateTime.parse(depOptions.get(i).get("outbound").get("departureTime").asString());
            OffsetDateTime t2 = OffsetDateTime.parse(depOptions.get(i + 1).get("outbound").get("departureTime").asString());
            assertTrue(!t1.isAfter(t2), "EARLIEST_DEPARTURE must have non-decreasing departure times");
        }

        // 4. FEWEST_STOPS
        MvcResult stopResult = owner.unsafe(get("/api/trips/{tripId}/airfare?sort=FEWEST_STOPS", tripId), null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sort").value("FEWEST_STOPS"))
                .andReturn();
        JsonNode stopOptions = JSON.readTree(stopResult.getResponse().getContentAsString()).get("options");
        for (int i = 0; i < stopOptions.size() - 1; i++) {
            int s1 = stopOptions.get(i).get("outbound").get("stopCount").asInt() + stopOptions.get(i).get("returnFlight").get("stopCount").asInt();
            int s2 = stopOptions.get(i + 1).get("outbound").get("stopCount").asInt() + stopOptions.get(i + 1).get("returnFlight").get("stopCount").asInt();
            assertTrue(s1 <= s2, "FEWEST_STOPS must have non-decreasing stop counts");
        }

        // 5. Invalid sort parameter returns 400 VALIDATION_FAILED
        owner.unsafe(get("/api/trips/{tripId}/airfare?sort=UNSUPPORTED", tripId), null)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.fields.sort").value("Choose a supported sort option."));
    }

    @Test
    void partyPricingCalculatesExactTotalsInCentsWithTransparentBreakdown() throws Exception {
        Client owner = register("pricing-test@example.test");

        for (int partySize : new int[] { 1, 3, 8 }) {
            MvcResult tripResult = owner.unsafe(post("/api/trips"), tripJson("destination-mex", "2027-03-06", "2027-03-12", partySize))
                    .andExpect(status().isCreated()).andReturn();
            String tripId = jsonField(tripResult, "id");

            MvcResult searchResult = owner.unsafe(get("/api/trips/{tripId}/airfare", tripId), null)
                    .andExpect(status().isOk()).andReturn();
            JsonNode options = JSON.readTree(searchResult.getResponse().getContentAsString()).get("options");

            for (JsonNode option : options) {
                JsonNode outbound = option.get("outbound");
                JsonNode ret = option.get("returnFlight");
                JsonNode pricing = option.get("pricing");

                long outBase = outbound.get("baseFareCents").asLong();
                long outTax = outbound.get("taxCents").asLong();
                long outFee = outbound.get("feeCents").asLong();
                long outTotal = outbound.get("totalFareCents").asLong();
                assertEquals(outBase + outTax + outFee, outTotal);

                long retBase = ret.get("baseFareCents").asLong();
                long retTax = ret.get("taxCents").asLong();
                long retFee = ret.get("feeCents").asLong();
                long retTotal = ret.get("totalFareCents").asLong();
                assertEquals(retBase + retTax + retFee, retTotal);

                assertEquals(partySize, pricing.get("travelerCount").asInt());
                assertEquals(outBase + retBase, pricing.get("perTravelerBaseFareCents").asLong());
                assertEquals(outTax + retTax, pricing.get("perTravelerTaxCents").asLong());
                assertEquals(outFee + retFee, pricing.get("perTravelerFeeCents").asLong());
                assertEquals(outTotal + retTotal, pricing.get("perTravelerTotalCents").asLong());

                assertEquals((long) partySize * (outBase + retBase), pricing.get("partyBaseFareCents").asLong());
                assertEquals((long) partySize * (outTax + retTax), pricing.get("partyTaxCents").asLong());
                assertEquals((long) partySize * (outFee + retFee), pricing.get("partyFeeCents").asLong());
                assertEquals((long) partySize * (outTotal + retTotal), pricing.get("partyTotalPriceCents").asLong());
            }
        }
    }

    @Test
    void saveDraftAirfarePersistsSelectionAdvancesDraftAndTripVersionAndReturnsTripResponse() throws Exception {
        Client owner = register("selection-save-test@example.test");
        MvcResult tripResult = owner.unsafe(post("/api/trips"), tripJson("destination-sfo", "2027-03-01", "2027-03-05", 2))
                .andExpect(status().isCreated()).andReturn();
        String tripId = jsonField(tripResult, "id");
        String draftId = JSON.readTree(tripResult.getResponse().getContentAsString()).get("drafts").get(0).get("id").asString();

        // Get search options to choose flight instances
        MvcResult searchResult = owner.unsafe(get("/api/trips/{tripId}/drafts/{draftId}/airfare", tripId, draftId), null)
                .andExpect(status().isOk()).andReturn();
        JsonNode firstOption = JSON.readTree(searchResult.getResponse().getContentAsString()).get("options").get(0);
        long outInstanceId = firstOption.get("outbound").get("flightInstanceId").asLong();
        long retInstanceId = firstOption.get("returnFlight").get("flightInstanceId").asLong();

        // Save airfare selection on draft
        String putBody = """
                {
                    "expectedVersion": 0,
                    "expectedDraftVersion": 0,
                    "outboundFlightInstanceId": %d,
                    "returnFlightInstanceId": %d
                }
                """.formatted(outInstanceId, retInstanceId);

        MvcResult putResult = owner.unsafe(put("/api/trips/{tripId}/drafts/{draftId}/airfare", tripId, draftId), putBody)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.version").value(1))
                .andExpect(jsonPath("$.drafts[0].version").value(1))
                .andExpect(jsonPath("$.drafts[0].selections.airfare.outboundFlightInstanceId").value(outInstanceId))
                .andExpect(jsonPath("$.drafts[0].selections.airfare.returnFlightInstanceId").value(retInstanceId))
                .andExpect(jsonPath("$.drafts[0].selections.airfare.outboundDescription").isString())
                .andExpect(jsonPath("$.drafts[0].selections.airfare.returnDescription").isString())
                .andReturn();

        JsonNode putBodyTree = JSON.readTree(putResult.getResponse().getContentAsString());
        JsonNode airfare = putBodyTree.get("drafts").get(0).get("selections").get("airfare");
        assertTrue(airfare.get("outboundBaseFareCents").asLong() > 0);
        assertTrue(airfare.get("returnBaseFareCents").asLong() > 0);

        // Verify relational persistence
        assertEquals(1, jdbc.queryForObject(
                "SELECT COUNT(*) FROM detour_trip_draft_airfare_selection WHERE draft_id = (SELECT id FROM detour_trip_draft WHERE public_id = ?)",
                Integer.class, UUID.fromString(draftId)));
    }

    @Test
    void replaceDraftAirfareReplacesSelectionCleanlyWithoutOrphans() throws Exception {
        Client owner = register("selection-replace-test@example.test");
        MvcResult tripResult = owner.unsafe(post("/api/trips"), tripJson("destination-sfo", "2027-03-01", "2027-03-05", 2))
                .andExpect(status().isCreated()).andReturn();
        String tripId = jsonField(tripResult, "id");
        String draftId = JSON.readTree(tripResult.getResponse().getContentAsString()).get("drafts").get(0).get("id").asString();

        MvcResult searchResult = owner.unsafe(get("/api/trips/{tripId}/drafts/{draftId}/airfare", tripId, draftId), null)
                .andExpect(status().isOk()).andReturn();
        JsonNode options = JSON.readTree(searchResult.getResponse().getContentAsString()).get("options");

        long out1 = options.get(0).get("outbound").get("flightInstanceId").asLong();
        long ret1 = options.get(0).get("returnFlight").get("flightInstanceId").asLong();
        long out2 = options.get(1).get("outbound").get("flightInstanceId").asLong();
        long ret2 = options.get(1).get("returnFlight").get("flightInstanceId").asLong();

        // 1. Save selection 1
        owner.unsafe(put("/api/trips/{tripId}/drafts/{draftId}/airfare", tripId, draftId), """
                {"expectedVersion":0,"expectedDraftVersion":0,"outboundFlightInstanceId":%d,"returnFlightInstanceId":%d}
                """.formatted(out1, ret1))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.version").value(1))
                .andExpect(jsonPath("$.drafts[0].version").value(1))
                .andExpect(jsonPath("$.drafts[0].selections.airfare.outboundFlightInstanceId").value(out1));

        assertEquals(1, jdbc.queryForObject(
                "SELECT COUNT(*) FROM detour_trip_draft_airfare_selection WHERE draft_id = (SELECT id FROM detour_trip_draft WHERE public_id = ?)",
                Integer.class, UUID.fromString(draftId)));

        // 2. Replace with selection 2 (advances trip to 2 and draft to 2)
        owner.unsafe(put("/api/trips/{tripId}/drafts/{draftId}/airfare", tripId, draftId), """
                {"expectedVersion":1,"expectedDraftVersion":1,"outboundFlightInstanceId":%d,"returnFlightInstanceId":%d}
                """.formatted(out2, ret2))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.version").value(2))
                .andExpect(jsonPath("$.drafts[0].version").value(2))
                .andExpect(jsonPath("$.drafts[0].selections.airfare.outboundFlightInstanceId").value(out2))
                .andExpect(jsonPath("$.drafts[0].selections.airfare.returnFlightInstanceId").value(ret2));

        // Still exactly 1 row, cleanly replaced without duplicates
        assertEquals(1, jdbc.queryForObject(
                "SELECT COUNT(*) FROM detour_trip_draft_airfare_selection WHERE draft_id = (SELECT id FROM detour_trip_draft WHERE public_id = ?)",
                Integer.class, UUID.fromString(draftId)));
    }

    @Test
    void removeDraftAirfareDeletesSelectionAdvancesDraftAndTripVersion() throws Exception {
        Client owner = register("selection-remove-test@example.test");
        MvcResult tripResult = owner.unsafe(post("/api/trips"), tripJson("destination-sfo", "2027-03-01", "2027-03-05", 2))
                .andExpect(status().isCreated()).andReturn();
        String tripId = jsonField(tripResult, "id");
        String draftId = JSON.readTree(tripResult.getResponse().getContentAsString()).get("drafts").get(0).get("id").asString();

        MvcResult searchResult = owner.unsafe(get("/api/trips/{tripId}/drafts/{draftId}/airfare", tripId, draftId), null)
                .andExpect(status().isOk()).andReturn();
        JsonNode firstOption = JSON.readTree(searchResult.getResponse().getContentAsString()).get("options").get(0);
        long out = firstOption.get("outbound").get("flightInstanceId").asLong();
        long ret = firstOption.get("returnFlight").get("flightInstanceId").asLong();

        // Save selection
        owner.unsafe(put("/api/trips/{tripId}/drafts/{draftId}/airfare", tripId, draftId), """
                {"expectedVersion":0,"expectedDraftVersion":0,"outboundFlightInstanceId":%d,"returnFlightInstanceId":%d}
                """.formatted(out, ret))
                .andExpect(status().isOk());

        // Remove selection
        owner.unsafe(delete("/api/trips/{tripId}/drafts/{draftId}/airfare", tripId, draftId), """
                {"expectedVersion":1,"expectedDraftVersion":1}
                """)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.version").value(2))
                .andExpect(jsonPath("$.drafts[0].version").value(2))
                .andExpect(jsonPath("$.drafts[0].selections.airfare").doesNotExist());

        // Database row deleted
        assertEquals(0, jdbc.queryForObject(
                "SELECT COUNT(*) FROM detour_trip_draft_airfare_selection WHERE draft_id = (SELECT id FROM detour_trip_draft WHERE public_id = ?)",
                Integer.class, UUID.fromString(draftId)));
    }

    @Test
    void selectionMutationsEnforceOptimisticConcurrencyOnTripAndDraft() throws Exception {
        Client owner = register("concurrency-test@example.test");
        MvcResult tripResult = owner.unsafe(post("/api/trips"), tripJson("destination-sfo", "2027-03-01", "2027-03-05", 2))
                .andExpect(status().isCreated()).andReturn();
        String tripId = jsonField(tripResult, "id");
        String draftId = JSON.readTree(tripResult.getResponse().getContentAsString()).get("drafts").get(0).get("id").asString();

        MvcResult searchResult = owner.unsafe(get("/api/trips/{tripId}/drafts/{draftId}/airfare", tripId, draftId), null)
                .andExpect(status().isOk()).andReturn();
        JsonNode firstOption = JSON.readTree(searchResult.getResponse().getContentAsString()).get("options").get(0);
        long out = firstOption.get("outbound").get("flightInstanceId").asLong();
        long ret = firstOption.get("returnFlight").get("flightInstanceId").asLong();

        // 1. Stale expectedVersion on PUT
        owner.unsafe(put("/api/trips/{tripId}/drafts/{draftId}/airfare", tripId, draftId), """
                {"expectedVersion":99,"expectedDraftVersion":0,"outboundFlightInstanceId":%d,"returnFlightInstanceId":%d}
                """.formatted(out, ret))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("VERSION_CONFLICT"))
                .andExpect(jsonPath("$.fields.currentVersion").value("0"));

        // 2. Stale expectedDraftVersion on PUT
        owner.unsafe(put("/api/trips/{tripId}/drafts/{draftId}/airfare", tripId, draftId), """
                {"expectedVersion":0,"expectedDraftVersion":99,"outboundFlightInstanceId":%d,"returnFlightInstanceId":%d}
                """.formatted(out, ret))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("VERSION_CONFLICT"))
                .andExpect(jsonPath("$.fields.currentDraftVersion").value("0"));

        // Confirm database was untouched
        assertEquals(0, jdbc.queryForObject(
                "SELECT COUNT(*) FROM detour_trip_draft_airfare_selection WHERE draft_id = (SELECT id FROM detour_trip_draft WHERE public_id = ?)",
                Integer.class, UUID.fromString(draftId)));

        // Perform valid save: trip version -> 1, draft version -> 1
        owner.unsafe(put("/api/trips/{tripId}/drafts/{draftId}/airfare", tripId, draftId), """
                {"expectedVersion":0,"expectedDraftVersion":0,"outboundFlightInstanceId":%d,"returnFlightInstanceId":%d}
                """.formatted(out, ret))
                .andExpect(status().isOk());

        // 3. Stale expectedVersion on DELETE
        owner.unsafe(delete("/api/trips/{tripId}/drafts/{draftId}/airfare", tripId, draftId), """
                {"expectedVersion":99,"expectedDraftVersion":1}
                """)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("VERSION_CONFLICT"))
                .andExpect(jsonPath("$.fields.currentVersion").value("1"));

        // 4. Stale expectedDraftVersion on DELETE
        owner.unsafe(delete("/api/trips/{tripId}/drafts/{draftId}/airfare", tripId, draftId), """
                {"expectedVersion":1,"expectedDraftVersion":99}
                """)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("VERSION_CONFLICT"))
                .andExpect(jsonPath("$.fields.currentDraftVersion").value("1"));

        // Selection still present in DB
        assertEquals(1, jdbc.queryForObject(
                "SELECT COUNT(*) FROM detour_trip_draft_airfare_selection WHERE draft_id = (SELECT id FROM detour_trip_draft WHERE public_id = ?)",
                Integer.class, UUID.fromString(draftId)));
    }

    @Test
    void unownedTripOrDraftRejectsSearchAndMutationWithNotFound() throws Exception {
        Client owner = register("owner-a@example.test");
        Client intruder = register("intruder-b@example.test");

        MvcResult tripResult = owner.unsafe(post("/api/trips"), tripJson("destination-sfo", "2027-03-01", "2027-03-05", 2))
                .andExpect(status().isCreated()).andReturn();
        String tripId = jsonField(tripResult, "id");
        String draftId = JSON.readTree(tripResult.getResponse().getContentAsString()).get("drafts").get(0).get("id").asString();

        UUID randomTrip = UUID.randomUUID();
        UUID randomDraft = UUID.randomUUID();

        // 1. Search draft
        String foreignSearch = intruder.unsafe(get("/api/trips/{tripId}/drafts/{draftId}/airfare", tripId, draftId), null)
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("RESOURCE_NOT_FOUND"))
                .andReturn().getResponse().getContentAsString();
        String unknownSearch = intruder.unsafe(get("/api/trips/{tripId}/drafts/{draftId}/airfare", randomTrip, randomDraft), null)
                .andExpect(status().isNotFound())
                .andReturn().getResponse().getContentAsString();
        assertEquals(foreignSearch, unknownSearch);

        // 2. Search trip
        String foreignTripSearch = intruder.unsafe(get("/api/trips/{tripId}/airfare", tripId), null)
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("RESOURCE_NOT_FOUND"))
                .andReturn().getResponse().getContentAsString();
        String unknownTripSearch = intruder.unsafe(get("/api/trips/{tripId}/airfare", randomTrip), null)
                .andExpect(status().isNotFound())
                .andReturn().getResponse().getContentAsString();
        assertEquals(foreignTripSearch, unknownTripSearch);

        // 3. Put selection
        String putBody = "{\"expectedVersion\":0,\"expectedDraftVersion\":0,\"outboundFlightInstanceId\":1,\"returnFlightInstanceId\":2}";
        String foreignPut = intruder.unsafe(put("/api/trips/{tripId}/drafts/{draftId}/airfare", tripId, draftId), putBody)
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("RESOURCE_NOT_FOUND"))
                .andReturn().getResponse().getContentAsString();
        String unknownPut = intruder.unsafe(put("/api/trips/{tripId}/drafts/{draftId}/airfare", randomTrip, randomDraft), putBody)
                .andExpect(status().isNotFound())
                .andReturn().getResponse().getContentAsString();
        assertEquals(foreignPut, unknownPut);

        // 4. Delete selection
        String delBody = "{\"expectedVersion\":0,\"expectedDraftVersion\":0}";
        String foreignDel = intruder.unsafe(delete("/api/trips/{tripId}/drafts/{draftId}/airfare", tripId, draftId), delBody)
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("RESOURCE_NOT_FOUND"))
                .andReturn().getResponse().getContentAsString();
        String unknownDel = intruder.unsafe(delete("/api/trips/{tripId}/drafts/{draftId}/airfare", randomTrip, randomDraft), delBody)
                .andExpect(status().isNotFound())
                .andReturn().getResponse().getContentAsString();
        assertEquals(foreignDel, unknownDel);
    }

    @Test
    void invalidFlightSelectionRejectsWithValidationError() throws Exception {
        Client owner = register("validation-errors-test@example.test");
        MvcResult tripResult = owner.unsafe(post("/api/trips"), tripJson("destination-sfo", "2027-03-01", "2027-03-05", 2))
                .andExpect(status().isCreated()).andReturn();
        String tripId = jsonField(tripResult, "id");
        String draftId = JSON.readTree(tripResult.getResponse().getContentAsString()).get("drafts").get(0).get("id").asString();

        MvcResult searchResult = owner.unsafe(get("/api/trips/{tripId}/drafts/{draftId}/airfare", tripId, draftId), null)
                .andExpect(status().isOk()).andReturn();
        JsonNode firstOption = JSON.readTree(searchResult.getResponse().getContentAsString()).get("options").get(0);
        long out = firstOption.get("outbound").get("flightInstanceId").asLong();
        long ret = firstOption.get("returnFlight").get("flightInstanceId").asLong();

        // 1. Identical outbound and return flight instances
        owner.unsafe(put("/api/trips/{tripId}/drafts/{draftId}/airfare", tripId, draftId), """
                {"expectedVersion":0,"expectedDraftVersion":0,"outboundFlightInstanceId":%d,"returnFlightInstanceId":%d}
                """.formatted(out, out))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.fields.outboundFlightInstanceId").value("Outbound and return flight instances must be distinct."));

        // 2. Non-existent flight instance
        owner.unsafe(put("/api/trips/{tripId}/drafts/{draftId}/airfare", tripId, draftId), """
                {"expectedVersion":0,"expectedDraftVersion":0,"outboundFlightInstanceId":999999,"returnFlightInstanceId":%d}
                """.formatted(ret))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.fields.outboundFlightInstanceId").exists());

        // 3. Flight instance from wrong date / wrong destination
        Long mucInstanceId = jdbc.queryForObject("""
                SELECT instance.id FROM flight_instance instance
                JOIN flight_schedule schedule ON schedule.id = instance.flight_schedule_id
                WHERE schedule.catalog_key = 'airfare-out-muc-d1' AND instance.service_date = DATE '2027-03-01'
                """, Long.class);
        assertNotNull(mucInstanceId);

        owner.unsafe(put("/api/trips/{tripId}/drafts/{draftId}/airfare", tripId, draftId), """
                {"expectedVersion":0,"expectedDraftVersion":0,"outboundFlightInstanceId":%d,"returnFlightInstanceId":%d}
                """.formatted(mucInstanceId, ret))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.fields.outboundFlightInstanceId").value("Selected outbound flight does not match trip destination, dates, or party size."));
    }

    private static String tripJson(String destinationKey, String startDate, String endDate, int travelerCount) {
        return """
                {
                    "destinationKey": "%s",
                    "startDate": "%s",
                    "endDate": "%s",
                    "travelerCount": %d,
                    "travelerAges": %s,
                    "budgetCents": 500000
                }
                """.formatted(destinationKey, startDate, endDate, travelerCount,
                java.util.Collections.nCopies(travelerCount, 30).toString());
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
