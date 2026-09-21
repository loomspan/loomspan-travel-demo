package app.detour.trip;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import jakarta.servlet.http.Cookie;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.UUID;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
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

@SpringBootTest
@AutoConfigureMockMvc
class TripApiIntegrationTest {
    private static final String DATABASE_URL = "jdbc:h2:mem:trip_api_" + UUID.randomUUID() + ";DB_CLOSE_DELAY=-1";

    @DynamicPropertySource
    static void datasourceProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", () -> DATABASE_URL);
    }

    @Autowired private MockMvc mockMvc;
    @Autowired private JdbcTemplate jdbc;

    @Test
    void createsOwnedTripAndInitialComponentEmptyDraft() throws Exception {
        Client owner = register("trip-owner@example.test");
        int tripsBefore = count("detour_trip");
        int travelersBefore = count("detour_trip_traveler");
        int draftsBefore = count("detour_trip_draft");
        MvcResult result = owner.unsafe(post("/api/trips"), validRequest("\"travelerAges\":[17,17],\"budgetCents\":0"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").isString())
                .andExpect(jsonPath("$.destinationKey").value("destination-sfo"))
                .andExpect(jsonPath("$.destinationName").value("San Francisco"))
                .andExpect(jsonPath("$.originAirportCode").value("PDX"))
                .andExpect(jsonPath("$.label").value("San Francisco \u2014 Mar 10\u201314, 2027"))
                .andExpect(jsonPath("$.version").value(0))
                .andExpect(jsonPath("$.travelerAges[0]").value(17))
                .andExpect(jsonPath("$.budgetCents").value(0))
                .andExpect(jsonPath("$.drafts.length()").value(1))
                .andExpect(jsonPath("$.drafts[0].id").isString())
                .andExpect(jsonPath("$.drafts[0].version").value(0))
                .andExpect(jsonPath("$.ownerUserId").doesNotExist())
                .andExpect(jsonPath("$.components").doesNotExist())
                .andReturn();
        String tripId = jsonField(result, "id");
        mockMvc.perform(get("/api/trips/{tripId}", tripId).session(owner.session).cookie(owner.csrf))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(tripId))
                .andExpect(jsonPath("$.drafts.length()").value(1));
        assertEquals(tripsBefore + 1, count("detour_trip"));
        assertEquals(travelersBefore + 2, count("detour_trip_traveler"));
        assertEquals(draftsBefore + 1, count("detour_trip_draft"));
    }

    @Test
    void validatesEnvelopeAndNeverPersistsPartialAggregate() throws Exception {
        Client owner = register("validation@example.test");
        int tripsBefore = count("detour_trip");
        int draftsBefore = count("detour_trip_draft");
        int travelersBefore = count("detour_trip_traveler");
        String[] invalidBodies = {
                "{\"startDate\":\"2027-03-10\",\"endDate\":\"2027-03-14\",\"travelerCount\":2}",
                "{\"destinationKey\":\"destination-sfo\",\"endDate\":\"2027-03-14\",\"travelerCount\":2}",
                "{\"destinationKey\":\"destination-sfo\",\"startDate\":\"2027-03-10\",\"travelerCount\":2}",
                "{\"destinationKey\":\"destination-sfo\",\"startDate\":\"2027-03-10\",\"endDate\":\"2027-03-14\"}",
                validRequest("\"destinationKey\":\"destination-other\""),
                validRequest("\"startDate\":\"not-a-date\""),
                validRequest("\"startDate\":\"2027-02-28\""),
                validRequest("\"endDate\":\"2027-03-10\""),
                validRequest("\"endDate\":\"2027-03-09\""),
                validRequest("\"endDate\":\"2027-03-25\""),
                validRequest("\"travelerCount\":0"),
                validRequest("\"travelerCount\":9"),
                validRequest("\"travelerAges\":[17]"),
                validRequest("\"travelerAges\":[-1,17]"),
                validRequest("\"travelerAges\":[17,121]"),
                validRequest("\"travelerAges\":[17,null]"),
                validRequest("\"travelerAges\":[17,1.5]"),
                validRequest("\"travelerAges\":[17,\"twelve\"]"),
                validRequest("\"budgetCents\":-1"),
                validRequest("\"budgetCents\":100000001"),
                validRequest("\"budgetCents\":1.5"),
                validRequest("\"budgetCents\":1e3"),
                validRequest("\"budgetCents\":\"100\"")
        };
        for (String body : invalidBodies) {
            owner.unsafe(post("/api/trips"), body).andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
        }
        owner.unsafe(post("/api/trips"), "{not-json").andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("MALFORMED_REQUEST"));
        assertEquals(tripsBefore, count("detour_trip"));
        assertEquals(draftsBefore, count("detour_trip_draft"));
        assertEquals(travelersBefore, count("detour_trip_traveler"));
    }

    @Test
    void preservesUnknownAgesAndAbsentBudgetSeparatelyFromZero() throws Exception {
        Client owner = register("optionals@example.test");
        owner.unsafe(post("/api/trips"), validRequest(""))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.travelerAges").doesNotExist())
                .andExpect(jsonPath("$.budgetCents").doesNotExist());
        owner.unsafe(post("/api/trips"), validRequest("\"travelerAges\":[0,120],\"budgetCents\":100000000"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.travelerAges[0]").value(0))
                .andExpect(jsonPath("$.travelerAges[1]").value(120))
                .andExpect(jsonPath("$.budgetCents").value(100000000));
        owner.unsafe(post("/api/trips"), validRequest("\"travelerAges\":[0,17],\"budgetCents\":0"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.budgetCents").value(0));
    }

    @Test
    void doesNotDiscloseForeignTripAndBindsCreationToPrincipal() throws Exception {
        Client owner = register("first-trip@example.test");
        Client other = register("second-trip@example.test");
        String tripId = jsonField(owner.unsafe(post("/api/trips"), validRequest(""))
                .andExpect(status().isCreated()).andReturn(), "id");
        String foreign = mockMvc.perform(get("/api/trips/{tripId}", tripId).session(other.session).cookie(other.csrf))
                .andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("RESOURCE_NOT_FOUND"))
                .andExpect(jsonPath("$.destinationKey").doesNotExist()).andReturn().getResponse().getContentAsString();
        String unknown = mockMvc.perform(get("/api/trips/{tripId}", UUID.randomUUID()).session(other.session).cookie(other.csrf))
                .andExpect(status().isNotFound()).andReturn().getResponse().getContentAsString();
        assertEquals(foreign, unknown);
        other.unsafe(post("/api/trips"), validRequest("\"ownerUserId\":1"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
        other.unsafe(post("/api/trips"), validRequest("\"name\":\"Unaccepted custom name\""))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
        other.unsafe(post("/api/trips"), validRequest(""))
                .andExpect(status().isCreated());
        assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM detour_trip WHERE owner_user_id = (SELECT id FROM detour_user WHERE canonical_email = 'first-trip@example.test')", Integer.class));
        assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM detour_trip WHERE owner_user_id = (SELECT id FROM detour_user WHERE canonical_email = 'second-trip@example.test')", Integer.class));
    }

    @Test
    void rollsBackParentAndTravelersWhenDraftPersistenceFails() throws Exception {
        Client owner = register("rollback@example.test");
        int tripsBefore = count("detour_trip");
        int travelersBefore = count("detour_trip_traveler");
        int draftsBefore = count("detour_trip_draft");
        jdbc.execute("CREATE TRIGGER fail_trip_draft BEFORE INSERT ON detour_trip_draft FOR EACH ROW CALL 'app.detour.trip.TripApiIntegrationTest$FailingDraftTrigger'");
        try {
            owner.unsafe(post("/api/trips"), validRequest("\"travelerAges\":[10,12]"))
                    .andExpect(status().isInternalServerError()).andExpect(jsonPath("$.code").value("INTERNAL_ERROR"));
        } finally {
            jdbc.execute("DROP TRIGGER fail_trip_draft");
        }
        assertEquals(tripsBefore, count("detour_trip"));
        assertEquals(travelersBefore, count("detour_trip_traveler"));
        assertEquals(draftsBefore, count("detour_trip_draft"));
    }

    @Test
    void concurrentFailedCreatesDoNotLeaveOrphans() throws Exception {
        Client owner = register("concurrent@example.test");
        int tripsBefore = count("detour_trip");
        int travelersBefore = count("detour_trip_traveler");
        int draftsBefore = count("detour_trip_draft");
        try (ExecutorService workers = Executors.newFixedThreadPool(4)) {
            java.util.List<Future<Integer>> responses = new java.util.ArrayList<>();
            for (int index = 0; index < 8; index++) {
                responses.add(workers.submit(() -> owner.unsafe(post("/api/trips"), validRequest("\"budgetCents\":-1"))
                        .andReturn().getResponse().getStatus()));
            }
            for (Future<Integer> response : responses) assertEquals(400, response.get());
        }
        assertEquals(tripsBefore, count("detour_trip"));
        assertEquals(travelersBefore, count("detour_trip_traveler"));
        assertEquals(draftsBefore, count("detour_trip_draft"));
        assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM detour_trip trip LEFT JOIN detour_trip_draft draft ON draft.trip_id = trip.id WHERE draft.id IS NULL", Integer.class));
    }

    @Test
    void createsAdditionalComponentEmptyDraftOnlyWhenExplicitlyRequested() throws Exception {
        Client owner = register("alternatives@example.test");
        MvcResult created = owner.unsafe(post("/api/trips"), validRequest("")).andExpect(status().isCreated()).andReturn();
        String tripId = jsonField(created, "id");
        String sourceId = tools.jackson.databind.json.JsonMapper.builder().build().readTree(created.getResponse().getContentAsString()).get("drafts").get(0).get("id").asString();
        owner.unsafe(post("/api/trips/{tripId}/drafts", tripId), "{\"expectedVersion\":0}")
                .andExpect(status().isCreated()).andExpect(jsonPath("$.version").value(1))
                .andExpect(jsonPath("$.drafts.length()").value(2)).andExpect(jsonPath("$.drafts[0].id").value(sourceId))
                .andExpect(jsonPath("$.drafts[1].version").value(0));
        assertEquals(2, jdbc.queryForObject("SELECT COUNT(*) FROM detour_trip_draft draft JOIN detour_trip trip ON trip.id = draft.trip_id WHERE trip.public_id = ?", Integer.class, UUID.fromString(tripId)));
    }

    @Test
    void updatesDuplicatesDeletesAndConflictsWithoutLostWrites() throws Exception {
        Client owner = register("versioned@example.test");
        MvcResult created = owner.unsafe(post("/api/trips"), validRequest("\"budgetCents\":0")).andReturn();
        var initial = tools.jackson.databind.json.JsonMapper.builder().build().readTree(created.getResponse().getContentAsString());
        String tripId = initial.get("id").asString();
        String sourceId = initial.get("drafts").get(0).get("id").asString();
        owner.unsafe(put("/api/trips/{tripId}", tripId), sharedUpdate(0, "destination-muc", 1, "null"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.destinationKey").value("destination-muc"))
                .andExpect(jsonPath("$.travelerAges").doesNotExist()).andExpect(jsonPath("$.budgetCents").doesNotExist())
                .andExpect(jsonPath("$.version").value(1));
        owner.unsafe(put("/api/trips/{tripId}", tripId), sharedUpdate(0, "destination-mex", 1, "0"))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("VERSION_CONFLICT"))
                .andExpect(jsonPath("$.fields.currentVersion").value("1"));
        MvcResult duplicated = owner.unsafe(post("/api/trips/{tripId}/drafts/{draftId}/duplicate", tripId, sourceId),
                "{\"expectedVersion\":1,\"expectedDraftVersion\":0}").andExpect(status().isCreated())
                .andExpect(jsonPath("$.version").value(2)).andExpect(jsonPath("$.drafts.length()").value(2)).andReturn();
        String duplicateId = tools.jackson.databind.json.JsonMapper.builder().build().readTree(duplicated.getResponse().getContentAsString()).get("drafts").get(1).get("id").asString();
        owner.unsafe(delete("/api/trips/{tripId}/drafts/{draftId}", tripId, sourceId), "{\"expectedVersion\":2,\"expectedDraftVersion\":0}")
                .andExpect(status().isOk()).andExpect(jsonPath("$.version").value(3)).andExpect(jsonPath("$.drafts.length()").value(1))
                .andExpect(jsonPath("$.drafts[0].id").value(duplicateId));
        owner.unsafe(delete("/api/trips/{tripId}/drafts/{draftId}", tripId, duplicateId), "{\"expectedVersion\":3,\"expectedDraftVersion\":0}")
                .andExpect(status().isOk()).andExpect(jsonPath("$.drafts.length()").value(0)).andExpect(jsonPath("$.version").value(4));
        owner.unsafe(post("/api/trips/{tripId}/drafts", tripId), "{\"expectedVersion\":4}")
                .andExpect(status().isCreated()).andExpect(jsonPath("$.drafts.length()").value(1));
    }

    @Test
    void mutationTargetsDoNotDiscloseForeignTrips() throws Exception {
        Client owner = register("owner-mutation@example.test");
        Client other = register("other-mutation@example.test");
        var created = owner.unsafe(post("/api/trips"), validRequest("")).andReturn();
        var body = tools.jackson.databind.json.JsonMapper.builder().build().readTree(created.getResponse().getContentAsString());
        String tripId = body.get("id").asString();
        String draftId = body.get("drafts").get(0).get("id").asString();
        String foreign = other.unsafe(post("/api/trips/{tripId}/drafts/{draftId}/duplicate", tripId, draftId), "{\"expectedVersion\":0,\"expectedDraftVersion\":0}")
                .andExpect(status().isNotFound()).andReturn().getResponse().getContentAsString();
        String unknown = other.unsafe(post("/api/trips/{tripId}/drafts/{draftId}/duplicate", UUID.randomUUID(), UUID.randomUUID()), "{\"expectedVersion\":0,\"expectedDraftVersion\":0}")
                .andExpect(status().isNotFound()).andReturn().getResponse().getContentAsString();
        assertEquals(foreign, unknown);
    }

    @Test
    void concurrentSameVersionSharedSavesAllowOnlyOneWinner() throws Exception {
        Client first = register("concurrent-save@example.test");
        Client second = login("concurrent-save@example.test");
        String tripId = jsonField(first.unsafe(post("/api/trips"), validRequest("")).andExpect(status().isCreated()).andReturn(), "id");
        CyclicBarrier barrier = new CyclicBarrier(2);
        try (ExecutorService workers = Executors.newFixedThreadPool(2)) {
            Future<Integer> firstResult = workers.submit(() -> {
                barrier.await();
                return first.unsafe(put("/api/trips/{tripId}", tripId), sharedUpdate(0, "destination-muc", 1, "null"))
                        .andReturn().getResponse().getStatus();
            });
            Future<Integer> secondResult = workers.submit(() -> {
                barrier.await();
                return second.unsafe(put("/api/trips/{tripId}", tripId), sharedUpdate(0, "destination-mex", 1, "0"))
                        .andReturn().getResponse().getStatus();
            });
            int firstStatus = firstResult.get();
            int secondStatus = secondResult.get();
            assertEquals(1, java.util.List.of(firstStatus, secondStatus).stream().filter(status -> status == 200).count());
            assertEquals(1, java.util.List.of(firstStatus, secondStatus).stream().filter(status -> status == 409).count());
        }
        mockMvc.perform(get("/api/trips/{tripId}", tripId).session(first.session).cookie(first.csrf))
                .andExpect(status().isOk()).andExpect(jsonPath("$.version").value(1));
    }

    @Test
    void rejectsStaleDraftVersionAndRollsBackFailedAlternativeInsertBeforeRetry() throws Exception {
        Client owner = register("draft-guard@example.test");
        MvcResult created = owner.unsafe(post("/api/trips"), validRequest("")).andExpect(status().isCreated()).andReturn();
        var body = tools.jackson.databind.json.JsonMapper.builder().build().readTree(created.getResponse().getContentAsString());
        String tripId = body.get("id").asString();
        String sourceId = body.get("drafts").get(0).get("id").asString();
        jdbc.update("UPDATE detour_trip_draft SET version = 1 WHERE public_id = ?", UUID.fromString(sourceId));
        owner.unsafe(post("/api/trips/{tripId}/drafts/{draftId}/duplicate", tripId, sourceId),
                        "{\"expectedVersion\":0,\"expectedDraftVersion\":0}")
                .andExpect(status().isConflict()).andExpect(jsonPath("$.fields.currentDraftVersion").value("1"));
        jdbc.update("UPDATE detour_trip_draft SET version = 0 WHERE public_id = ?", UUID.fromString(sourceId));
        jdbc.execute("CREATE TRIGGER fail_alternative_draft BEFORE INSERT ON detour_trip_draft FOR EACH ROW CALL 'app.detour.trip.TripApiIntegrationTest$FailingDraftTrigger'");
        try {
            owner.unsafe(post("/api/trips/{tripId}/drafts", tripId), "{\"expectedVersion\":0}")
                    .andExpect(status().isInternalServerError()).andExpect(jsonPath("$.code").value("INTERNAL_ERROR"));
        } finally {
            jdbc.execute("DROP TRIGGER fail_alternative_draft");
        }
        mockMvc.perform(get("/api/trips/{tripId}", tripId).session(owner.session).cookie(owner.csrf))
                .andExpect(status().isOk()).andExpect(jsonPath("$.version").value(0)).andExpect(jsonPath("$.drafts.length()").value(1));
        owner.unsafe(post("/api/trips/{tripId}/drafts", tripId), "{\"expectedVersion\":0}")
                .andExpect(status().isCreated()).andExpect(jsonPath("$.version").value(1)).andExpect(jsonPath("$.drafts.length()").value(2));
    }

    @Test
    void promotesAReadyDraftAsAnIndependentImmutablePlannedSnapshot() throws Exception {
        Client owner = register("planned@example.test");
        MvcResult created = owner.unsafe(post("/api/trips"), validRequest("\"travelerAges\":[18,12],\"budgetCents\":0")).andExpect(status().isCreated()).andReturn();
        String tripId = jsonField(created, "id");
        String draftId = tools.jackson.databind.json.JsonMapper.builder().build().readTree(created.getResponse().getContentAsString()).get("drafts").get(0).get("id").asString();
        insertSfoAirfareSelection(draftId);
        MvcResult promoted = owner.unsafe(post("/api/trips/{tripId}/drafts/{draftId}/plan", tripId, draftId), "{\"expectedVersion\":0,\"expectedDraftVersion\":0}")
                .andExpect(status().isCreated()).andExpect(jsonPath("$.version").value(1)).andExpect(jsonPath("$.drafts.length()").value(1))
                .andExpect(jsonPath("$.planned.length()").value(1)).andExpect(jsonPath("$.planned[0].selections.airfare.outboundDescription").isString()).andReturn();
        var promotedBody = tools.jackson.databind.json.JsonMapper.builder().build().readTree(promoted.getResponse().getContentAsString());
        String plannedId = promotedBody.get("planned").get(0).get("id").asString();
        long copiedFare = promotedBody.get("planned").get(0).get("selections").get("airfare").get("outboundBaseFareCents").asLong();
        jdbc.update("UPDATE flight_instance SET base_fare_cents = base_fare_cents + 999 WHERE id = (SELECT outbound_flight_instance_id FROM detour_trip_draft_airfare_selection WHERE draft_id = (SELECT id FROM detour_trip_draft WHERE public_id = ?))", UUID.fromString(draftId));
        owner.unsafe(get("/api/trips/{tripId}", tripId), null).andExpect(status().isOk()).andExpect(jsonPath("$.planned[0].id").value(plannedId))
                .andExpect(jsonPath("$.planned[0].selections.airfare.outboundBaseFareCents").value(copiedFare));
        MvcResult duplicated = owner.unsafe(post("/api/trips/{tripId}/alternatives/{alternativeId}/duplicate", tripId, plannedId), "{\"expectedVersion\":1}")
                .andExpect(status().isCreated()).andExpect(jsonPath("$.version").value(2)).andExpect(jsonPath("$.drafts.length()").value(2)).andReturn();
        String secondDraftId = tools.jackson.databind.json.JsonMapper.builder().build().readTree(duplicated.getResponse().getContentAsString()).get("drafts").get(1).get("id").asString();

        // Promote the second draft: verify multiple Planned alternatives can coexist under one Trip
        MvcResult secondPromoted = owner.unsafe(post("/api/trips/{tripId}/drafts/{draftId}/plan", tripId, secondDraftId), "{\"expectedVersion\":2,\"expectedDraftVersion\":0}")
                .andExpect(status().isCreated()).andExpect(jsonPath("$.version").value(3)).andExpect(jsonPath("$.planned.length()").value(2)).andReturn();
        String secondPlannedId = tools.jackson.databind.json.JsonMapper.builder().build().readTree(secondPromoted.getResponse().getContentAsString()).get("planned").get(1).get("id").asString();

        owner.unsafe(post("/api/trips/{tripId}/drafts/{draftId}/duplicate", tripId, plannedId), "{\"expectedVersion\":3,\"expectedDraftVersion\":0}")
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("IMMUTABLE_ALTERNATIVE"));
        owner.unsafe(delete("/api/trips/{tripId}/alternatives/{alternativeId}", tripId, plannedId), "{\"expectedVersion\":3}")
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.fields.confirmed").exists());
        owner.unsafe(delete("/api/trips/{tripId}/alternatives/{alternativeId}", tripId, plannedId), "{\"expectedVersion\":3,\"confirmed\":true}")
                .andExpect(status().isOk()).andExpect(jsonPath("$.planned.length()").value(1))
                .andExpect(jsonPath("$.planned[0].id").value(secondPlannedId))
                .andExpect(jsonPath("$.drafts.length()").value(2));
    }

    @Test
    void reportsAllKnownPromotionReadinessIssuesTogether() throws Exception {
        Client owner = register("not-ready@example.test");
        MvcResult created = owner.unsafe(post("/api/trips"), validRequest("")).andExpect(status().isCreated()).andReturn();
        var body = tools.jackson.databind.json.JsonMapper.builder().build().readTree(created.getResponse().getContentAsString());
        owner.unsafe(post("/api/trips/{tripId}/drafts/{draftId}/plan", body.get("id").asString(), body.get("drafts").get(0).get("id").asString()), "{\"expectedVersion\":0,\"expectedDraftVersion\":0}")
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("PLANNING_NOT_READY"))
                .andExpect(jsonPath("$.fields.travelerAges").exists()).andExpect(jsonPath("$.fields.budgetCents").exists()).andExpect(jsonPath("$.fields.components").exists());

        MvcResult minorTrip = owner.unsafe(post("/api/trips"), validRequest("\"travelerAges\":[17,12]")).andExpect(status().isCreated()).andReturn();
        var minorBody = tools.jackson.databind.json.JsonMapper.builder().build().readTree(minorTrip.getResponse().getContentAsString());
        owner.unsafe(post("/api/trips/{tripId}/drafts/{draftId}/plan", minorBody.get("id").asString(), minorBody.get("drafts").get(0).get("id").asString()), "{\"expectedVersion\":0,\"expectedDraftVersion\":0}")
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("PLANNING_NOT_READY"))
                .andExpect(jsonPath("$.fields.adult").value("At least one traveler must be an adult before planning."))
                .andExpect(jsonPath("$.fields.budgetCents").exists())
                .andExpect(jsonPath("$.fields.components").exists())
                .andExpect(jsonPath("$.fields.travelerAges").doesNotExist());
    }

    @Test
    void promotesEachStructurallyValidSelectionKindWithoutCanonicalPricing() throws Exception {
        Client owner = register("all-kinds@example.test");
        // Test Stay selection only
        MvcResult createdStay = owner.unsafe(post("/api/trips"), validRequest("\"travelerAges\":[30,25],\"budgetCents\":500000")).andExpect(status().isCreated()).andReturn();
        String tripId1 = jsonField(createdStay, "id");
        String draftId1 = tools.jackson.databind.json.JsonMapper.builder().build().readTree(createdStay.getResponse().getContentAsString()).get("drafts").get(0).get("id").asString();
        insertSfoStaySelection(draftId1);
        owner.unsafe(post("/api/trips/{tripId}/drafts/{draftId}/plan", tripId1, draftId1), "{\"expectedVersion\":0,\"expectedDraftVersion\":0}")
                .andExpect(status().isCreated()).andExpect(jsonPath("$.planned.length()").value(1))
                .andExpect(jsonPath("$.planned[0].selections.stay.propertyName").value("Summit Family Suites"))
                .andExpect(jsonPath("$.planned[0].selections.stay.unitCount").value(1))
                .andExpect(jsonPath("$.planned[0].selections.stay.nights.length()").value(4))
                .andExpect(jsonPath("$.planned[0].selections.airfare").doesNotExist())
                .andExpect(jsonPath("$.planned[0].selections.rental").doesNotExist());

        // Test Rental selection only
        MvcResult createdRental = owner.unsafe(post("/api/trips"), validRequest("\"travelerAges\":[30,25],\"budgetCents\":500000")).andExpect(status().isCreated()).andReturn();
        String tripId2 = jsonField(createdRental, "id");
        String draftId2 = tools.jackson.databind.json.JsonMapper.builder().build().readTree(createdRental.getResponse().getContentAsString()).get("drafts").get(0).get("id").asString();
        insertSfoRentalSelection(draftId2);
        owner.unsafe(post("/api/trips/{tripId}/drafts/{draftId}/plan", tripId2, draftId2), "{\"expectedVersion\":0,\"expectedDraftVersion\":0}")
                .andExpect(status().isCreated()).andExpect(jsonPath("$.planned.length()").value(1))
                .andExpect(jsonPath("$.planned[0].selections.rental.locationName").value("Harborline Mobility at SFO"))
                .andExpect(jsonPath("$.planned[0].selections.rental.vehicleClassName").value("SFO Economy"))
                .andExpect(jsonPath("$.planned[0].selections.rental.unitIdentifier").value("SFO-ECO-01"))
                .andExpect(jsonPath("$.planned[0].selections.rental.dailyBasePriceCents").value(4277))
                .andExpect(jsonPath("$.planned[0].selections.airfare").doesNotExist())
                .andExpect(jsonPath("$.planned[0].selections.stay").doesNotExist());

        // Test All three selections together
        MvcResult createdAll = owner.unsafe(post("/api/trips"), validRequest("\"travelerAges\":[30,25],\"budgetCents\":500000")).andExpect(status().isCreated()).andReturn();
        String tripId3 = jsonField(createdAll, "id");
        String draftId3 = tools.jackson.databind.json.JsonMapper.builder().build().readTree(createdAll.getResponse().getContentAsString()).get("drafts").get(0).get("id").asString();
        insertSfoAirfareSelection(draftId3);
        insertSfoStaySelection(draftId3);
        insertSfoRentalSelection(draftId3);
        owner.unsafe(post("/api/trips/{tripId}/drafts/{draftId}/plan", tripId3, draftId3), "{\"expectedVersion\":0,\"expectedDraftVersion\":0}")
                .andExpect(status().isCreated()).andExpect(jsonPath("$.planned.length()").value(1))
                .andExpect(jsonPath("$.planned[0].selections.airfare.outboundDescription").isString())
                .andExpect(jsonPath("$.planned[0].selections.stay.propertyName").isString())
                .andExpect(jsonPath("$.planned[0].selections.rental.locationName").isString());
    }

    @Test
    void plannedSnapshotReadsCopiedContentAfterDraftAndCatalogMutation() throws Exception {
        Client owner = register("snapshot-stability@example.test");
        MvcResult created = owner.unsafe(post("/api/trips"), validRequest("\"travelerAges\":[25,25],\"budgetCents\":100000")).andExpect(status().isCreated()).andReturn();
        String tripId = jsonField(created, "id");
        String draftId = tools.jackson.databind.json.JsonMapper.builder().build().readTree(created.getResponse().getContentAsString()).get("drafts").get(0).get("id").asString();
        insertSfoAirfareSelection(draftId);
        insertSfoStaySelection(draftId);
        insertSfoRentalSelection(draftId);

        MvcResult promoted = owner.unsafe(post("/api/trips/{tripId}/drafts/{draftId}/plan", tripId, draftId), "{\"expectedVersion\":0,\"expectedDraftVersion\":0}")
                .andExpect(status().isCreated()).andReturn();
        var promotedTree = tools.jackson.databind.json.JsonMapper.builder().build().readTree(promoted.getResponse().getContentAsString());
        String plannedId = promotedTree.get("planned").get(0).get("id").asString();
        long origFare = promotedTree.get("planned").get(0).get("selections").get("airfare").get("outboundBaseFareCents").asLong();
        long origStayPrice = promotedTree.get("planned").get(0).get("selections").get("stay").get("nights").get(0).get("basePriceCents").asLong();
        String origPropertyName = promotedTree.get("planned").get(0).get("selections").get("stay").get("propertyName").asString();
        long origRentalDailyPrice = promotedTree.get("planned").get(0).get("selections").get("rental").get("dailyBasePriceCents").asLong();

        // Mutate catalog entries
        jdbc.update("UPDATE flight_instance SET base_fare_cents = base_fare_cents + 5000");
        jdbc.update("UPDATE accommodation_nightly_inventory SET base_price_cents = base_price_cents + 5000");
        jdbc.update("UPDATE accommodation_property SET name = 'Renamed Property'");
        jdbc.update("UPDATE rental_vehicle_class SET daily_base_price_cents = daily_base_price_cents + 5000");

        // Mutate source draft selection in DB (change unit_count)
        jdbc.update("UPDATE detour_trip_draft_stay_selection SET unit_count = 5 WHERE draft_id = (SELECT id FROM detour_trip_draft WHERE public_id = ?)", UUID.fromString(draftId));

        // Reload Trip detail and assert Planned snapshot is identical to original copied facts
        owner.unsafe(get("/api/trips/{tripId}", tripId), null).andExpect(status().isOk())
                .andExpect(jsonPath("$.planned[0].id").value(plannedId))
                .andExpect(jsonPath("$.planned[0].selections.airfare.outboundBaseFareCents").value(origFare))
                .andExpect(jsonPath("$.planned[0].selections.stay.propertyName").value(origPropertyName))
                .andExpect(jsonPath("$.planned[0].selections.stay.unitCount").value(1))
                .andExpect(jsonPath("$.planned[0].selections.stay.nights[0].basePriceCents").value(origStayPrice))
                .andExpect(jsonPath("$.planned[0].selections.rental.dailyBasePriceCents").value(origRentalDailyPrice));
    }

    @Test
    void duplicatesDraftAndPlannedSourcesIntoIndependentDraftCopies() throws Exception {
        Client owner = register("duplicate-sources@example.test");
        MvcResult created = owner.unsafe(post("/api/trips"), validRequest("\"travelerAges\":[40,40],\"budgetCents\":200000")).andExpect(status().isCreated()).andReturn();
        String tripId = jsonField(created, "id");
        String draftId = tools.jackson.databind.json.JsonMapper.builder().build().readTree(created.getResponse().getContentAsString()).get("drafts").get(0).get("id").asString();
        insertSfoAirfareSelection(draftId);
        insertSfoStaySelection(draftId);

        // Duplicate mutable Draft source
        MvcResult duplicatedDraft = owner.unsafe(post("/api/trips/{tripId}/alternatives/{alternativeId}/duplicate", tripId, draftId),
                "{\"expectedVersion\":0,\"expectedDraftVersion\":0}").andExpect(status().isCreated())
                .andExpect(jsonPath("$.version").value(1)).andExpect(jsonPath("$.drafts.length()").value(2)).andReturn();
        var dupDraftTree = tools.jackson.databind.json.JsonMapper.builder().build().readTree(duplicatedDraft.getResponse().getContentAsString());
        String newDraftId = dupDraftTree.get("drafts").get(1).get("id").asString();
        org.junit.jupiter.api.Assertions.assertNotEquals(draftId, newDraftId);
        assertEquals(0, dupDraftTree.get("drafts").get(1).get("version").asInt());

        // Promote first draft
        MvcResult promoted = owner.unsafe(post("/api/trips/{tripId}/drafts/{draftId}/plan", tripId, draftId), "{\"expectedVersion\":1,\"expectedDraftVersion\":0}")
                .andExpect(status().isCreated()).andExpect(jsonPath("$.version").value(2)).andReturn();
        String plannedId = tools.jackson.databind.json.JsonMapper.builder().build().readTree(promoted.getResponse().getContentAsString()).get("planned").get(0).get("id").asString();

        // Duplicate Planned snapshot source
        MvcResult duplicatedPlanned = owner.unsafe(post("/api/trips/{tripId}/alternatives/{alternativeId}/duplicate", tripId, plannedId),
                "{\"expectedVersion\":2}").andExpect(status().isCreated())
                .andExpect(jsonPath("$.version").value(3)).andExpect(jsonPath("$.drafts.length()").value(3)).andReturn();
        var dupPlannedTree = tools.jackson.databind.json.JsonMapper.builder().build().readTree(duplicatedPlanned.getResponse().getContentAsString());
        String newestDraftId = dupPlannedTree.get("drafts").get(2).get("id").asString();
        org.junit.jupiter.api.Assertions.assertNotEquals(draftId, newestDraftId);
        org.junit.jupiter.api.Assertions.assertNotEquals(newDraftId, newestDraftId);
        assertEquals(0, dupPlannedTree.get("drafts").get(2).get("version").asInt());

        // Assert planned source was not modified
        assertEquals(plannedId, dupPlannedTree.get("planned").get(0).get("id").asString());
    }

    @Test
    void alternativeLifecycleMutationsDoNotRevealForeignTargets() throws Exception {
        Client owner = register("owner-isolation@example.test");
        Client other = register("other-isolation@example.test");
        var created = owner.unsafe(post("/api/trips"), validRequest("\"travelerAges\":[20,20],\"budgetCents\":100000")).andReturn();
        var body = tools.jackson.databind.json.JsonMapper.builder().build().readTree(created.getResponse().getContentAsString());
        String tripId = body.get("id").asString();
        String draftId = body.get("drafts").get(0).get("id").asString();
        insertSfoAirfareSelection(draftId);
        var promoted = owner.unsafe(post("/api/trips/{tripId}/drafts/{draftId}/plan", tripId, draftId), "{\"expectedVersion\":0,\"expectedDraftVersion\":0}").andReturn();
        String plannedId = tools.jackson.databind.json.JsonMapper.builder().build().readTree(promoted.getResponse().getContentAsString()).get("planned").get(0).get("id").asString();

        // 1. Plan route
        String foreignPlan = other.unsafe(post("/api/trips/{tripId}/drafts/{draftId}/plan", tripId, draftId), "{\"expectedVersion\":0,\"expectedDraftVersion\":0}")
                .andExpect(status().isNotFound()).andReturn().getResponse().getContentAsString();
        String unknownPlan = other.unsafe(post("/api/trips/{tripId}/drafts/{draftId}/plan", UUID.randomUUID(), UUID.randomUUID()), "{\"expectedVersion\":0,\"expectedDraftVersion\":0}")
                .andExpect(status().isNotFound()).andReturn().getResponse().getContentAsString();
        assertEquals(foreignPlan, unknownPlan);

        // 2. Alternative Duplicate route
        String foreignDup = other.unsafe(post("/api/trips/{tripId}/alternatives/{alternativeId}/duplicate", tripId, plannedId), "{\"expectedVersion\":1}")
                .andExpect(status().isNotFound()).andReturn().getResponse().getContentAsString();
        String unknownDup = other.unsafe(post("/api/trips/{tripId}/alternatives/{alternativeId}/duplicate", UUID.randomUUID(), UUID.randomUUID()), "{\"expectedVersion\":1}")
                .andExpect(status().isNotFound()).andReturn().getResponse().getContentAsString();
        assertEquals(foreignDup, unknownDup);

        // 3. Alternative Delete route
        String foreignDel = other.unsafe(delete("/api/trips/{tripId}/alternatives/{alternativeId}", tripId, plannedId), "{\"expectedVersion\":1,\"confirmed\":true}")
                .andExpect(status().isNotFound()).andReturn().getResponse().getContentAsString();
        String unknownDel = other.unsafe(delete("/api/trips/{tripId}/alternatives/{alternativeId}", UUID.randomUUID(), UUID.randomUUID()), "{\"expectedVersion\":1,\"confirmed\":true}")
                .andExpect(status().isNotFound()).andReturn().getResponse().getContentAsString();
        assertEquals(foreignDel, unknownDel);

        // 4. Draft Delete route with Planned ID
        owner.unsafe(delete("/api/trips/{tripId}/drafts/{draftId}", tripId, plannedId), "{\"expectedVersion\":1,\"expectedDraftVersion\":0}")
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("IMMUTABLE_ALTERNATIVE"));
    }

    @Test
    void rollsBackPromotionAndDuplicateGraphWritesOnFailure() throws Exception {
        Client owner = register("rollback-test@example.test");
        MvcResult created = owner.unsafe(post("/api/trips"), validRequest("\"travelerAges\":[20,20],\"budgetCents\":100000")).andExpect(status().isCreated()).andReturn();
        String tripId = jsonField(created, "id");
        String draftId = tools.jackson.databind.json.JsonMapper.builder().build().readTree(created.getResponse().getContentAsString()).get("drafts").get(0).get("id").asString();
        insertSfoAirfareSelection(draftId);

        jdbc.execute("CREATE TRIGGER fail_planned_airfare BEFORE INSERT ON detour_planned_airfare_snapshot FOR EACH ROW CALL \"app.detour.trip.TripApiIntegrationTest$FailingDraftTrigger\"");
        try {
            owner.unsafe(post("/api/trips/{tripId}/drafts/{draftId}/plan", tripId, draftId), "{\"expectedVersion\":0,\"expectedDraftVersion\":0}")
                    .andExpect(status().isInternalServerError()).andExpect(jsonPath("$.code").value("INTERNAL_ERROR"));
        } finally {
            jdbc.execute("DROP TRIGGER fail_planned_airfare");
        }

        // Verify version is still 0 and no planned rows exist
        owner.unsafe(get("/api/trips/{tripId}", tripId), null).andExpect(status().isOk())
                .andExpect(jsonPath("$.version").value(0)).andExpect(jsonPath("$.planned.length()").value(0));
        assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM detour_planned_itinerary WHERE trip_id = (SELECT id FROM detour_trip WHERE public_id = ?)", Integer.class, UUID.fromString(tripId)));

        // Retry promotion succeeds
        owner.unsafe(post("/api/trips/{tripId}/drafts/{draftId}/plan", tripId, draftId), "{\"expectedVersion\":0,\"expectedDraftVersion\":0}")
                .andExpect(status().isCreated()).andExpect(jsonPath("$.version").value(1)).andExpect(jsonPath("$.planned.length()").value(1));
    }

    @Test
    void sameVersionPromotionAndDuplicateRacesHaveOneCompleteWinner() throws Exception {
        Client first = register("race-user@example.test");
        Client second = login("race-user@example.test");
        MvcResult created = first.unsafe(post("/api/trips"), validRequest("\"travelerAges\":[25,25],\"budgetCents\":200000")).andExpect(status().isCreated()).andReturn();
        String tripId = jsonField(created, "id");
        String draftId = tools.jackson.databind.json.JsonMapper.builder().build().readTree(created.getResponse().getContentAsString()).get("drafts").get(0).get("id").asString();
        insertSfoAirfareSelection(draftId);

        // Racing promotion
        CyclicBarrier planBarrier = new CyclicBarrier(2);
        try (ExecutorService workers = Executors.newFixedThreadPool(2)) {
            Future<Integer> f1 = workers.submit(() -> {
                planBarrier.await();
                return first.unsafe(post("/api/trips/{tripId}/drafts/{draftId}/plan", tripId, draftId), "{\"expectedVersion\":0,\"expectedDraftVersion\":0}").andReturn().getResponse().getStatus();
            });
            Future<Integer> f2 = workers.submit(() -> {
                planBarrier.await();
                return second.unsafe(post("/api/trips/{tripId}/drafts/{draftId}/plan", tripId, draftId), "{\"expectedVersion\":0,\"expectedDraftVersion\":0}").andReturn().getResponse().getStatus();
            });
            int s1 = f1.get();
            int s2 = f2.get();
            assertEquals(1, java.util.List.of(s1, s2).stream().filter(s -> s == 201).count());
            assertEquals(1, java.util.List.of(s1, s2).stream().filter(s -> s == 409).count());
        }

        var tripBody = tools.jackson.databind.json.JsonMapper.builder().build().readTree(
                first.unsafe(get("/api/trips/{tripId}", tripId), null).andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        assertEquals(1, tripBody.get("version").asInt());
        assertEquals(1, tripBody.get("planned").size());
        String plannedId = tripBody.get("planned").get(0).get("id").asString();

        // Racing duplication of Planned alternative
        CyclicBarrier dupBarrier = new CyclicBarrier(2);
        try (ExecutorService workers = Executors.newFixedThreadPool(2)) {
            Future<Integer> f1 = workers.submit(() -> {
                dupBarrier.await();
                return first.unsafe(post("/api/trips/{tripId}/alternatives/{alternativeId}/duplicate", tripId, plannedId), "{\"expectedVersion\":1}").andReturn().getResponse().getStatus();
            });
            Future<Integer> f2 = workers.submit(() -> {
                dupBarrier.await();
                return second.unsafe(post("/api/trips/{tripId}/alternatives/{alternativeId}/duplicate", tripId, plannedId), "{\"expectedVersion\":1}").andReturn().getResponse().getStatus();
            });
            int s1 = f1.get();
            int s2 = f2.get();
            assertEquals(1, java.util.List.of(s1, s2).stream().filter(s -> s == 201).count());
            assertEquals(1, java.util.List.of(s1, s2).stream().filter(s -> s == 409).count());
        }

        first.unsafe(get("/api/trips/{tripId}", tripId), null).andExpect(status().isOk())
                .andExpect(jsonPath("$.version").value(2))
                .andExpect(jsonPath("$.drafts.length()").value(2))
                .andExpect(jsonPath("$.planned.length()").value(1));
    }

    @Test
    void plannedLifecycleLeavesCatalogInventoryUntouched() throws Exception {
        Client owner = register("inventory-check@example.test");
        MvcResult created = owner.unsafe(post("/api/trips"), validRequest("\"travelerAges\":[25,25],\"budgetCents\":500000")).andExpect(status().isCreated()).andReturn();
        String tripId = jsonField(created, "id");
        String draftId = tools.jackson.databind.json.JsonMapper.builder().build().readTree(created.getResponse().getContentAsString()).get("drafts").get(0).get("id").asString();
        insertSfoAirfareSelection(draftId);
        insertSfoStaySelection(draftId);
        insertSfoRentalSelection(draftId);

        int initialSeats = jdbc.queryForObject("SELECT available_seats FROM flight_instance WHERE id = (SELECT outbound_flight_instance_id FROM detour_trip_draft_airfare_selection WHERE draft_id = (SELECT id FROM detour_trip_draft WHERE public_id = ?))", Integer.class, UUID.fromString(draftId));
        int initialStayInv = jdbc.queryForObject("SELECT available_inventory FROM accommodation_nightly_inventory WHERE accommodation_unit_id = (SELECT accommodation_unit_id FROM detour_trip_draft_stay_selection WHERE draft_id = (SELECT id FROM detour_trip_draft WHERE public_id = ?)) AND night_date = DATE '2027-03-10'", Integer.class, UUID.fromString(draftId));
        int initialOccupancyCount = count("rental_unit_occupancy");

        // 1. Promotion
        MvcResult promoted = owner.unsafe(post("/api/trips/{tripId}/drafts/{draftId}/plan", tripId, draftId), "{\"expectedVersion\":0,\"expectedDraftVersion\":0}")
                .andExpect(status().isCreated()).andReturn();
        String plannedId = tools.jackson.databind.json.JsonMapper.builder().build().readTree(promoted.getResponse().getContentAsString()).get("planned").get(0).get("id").asString();

        assertEquals(initialSeats, jdbc.queryForObject("SELECT available_seats FROM flight_instance WHERE id = (SELECT outbound_flight_instance_id FROM detour_trip_draft_airfare_selection WHERE draft_id = (SELECT id FROM detour_trip_draft WHERE public_id = ?))", Integer.class, UUID.fromString(draftId)));
        assertEquals(initialStayInv, jdbc.queryForObject("SELECT available_inventory FROM accommodation_nightly_inventory WHERE accommodation_unit_id = (SELECT accommodation_unit_id FROM detour_trip_draft_stay_selection WHERE draft_id = (SELECT id FROM detour_trip_draft WHERE public_id = ?)) AND night_date = DATE '2027-03-10'", Integer.class, UUID.fromString(draftId)));
        assertEquals(initialOccupancyCount, count("rental_unit_occupancy"));

        // 2. Duplicate Planned
        owner.unsafe(post("/api/trips/{tripId}/alternatives/{alternativeId}/duplicate", tripId, plannedId), "{\"expectedVersion\":1}")
                .andExpect(status().isCreated());

        assertEquals(initialSeats, jdbc.queryForObject("SELECT available_seats FROM flight_instance WHERE id = (SELECT outbound_flight_instance_id FROM detour_trip_draft_airfare_selection WHERE draft_id = (SELECT id FROM detour_trip_draft WHERE public_id = ?))", Integer.class, UUID.fromString(draftId)));
        assertEquals(initialStayInv, jdbc.queryForObject("SELECT available_inventory FROM accommodation_nightly_inventory WHERE accommodation_unit_id = (SELECT accommodation_unit_id FROM detour_trip_draft_stay_selection WHERE draft_id = (SELECT id FROM detour_trip_draft WHERE public_id = ?)) AND night_date = DATE '2027-03-10'", Integer.class, UUID.fromString(draftId)));
        assertEquals(initialOccupancyCount, count("rental_unit_occupancy"));

        // 3. Delete Planned
        owner.unsafe(delete("/api/trips/{tripId}/alternatives/{alternativeId}", tripId, plannedId), "{\"expectedVersion\":2,\"confirmed\":true}")
                .andExpect(status().isOk());

        assertEquals(initialSeats, jdbc.queryForObject("SELECT available_seats FROM flight_instance WHERE id = (SELECT outbound_flight_instance_id FROM detour_trip_draft_airfare_selection WHERE draft_id = (SELECT id FROM detour_trip_draft WHERE public_id = ?))", Integer.class, UUID.fromString(draftId)));
        assertEquals(initialStayInv, jdbc.queryForObject("SELECT available_inventory FROM accommodation_nightly_inventory WHERE accommodation_unit_id = (SELECT accommodation_unit_id FROM detour_trip_draft_stay_selection WHERE draft_id = (SELECT id FROM detour_trip_draft WHERE public_id = ?)) AND night_date = DATE '2027-03-10'", Integer.class, UUID.fromString(draftId)));
        assertEquals(initialOccupancyCount, count("rental_unit_occupancy"));
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

    private static String sharedUpdate(long expectedVersion, String destinationKey, int travelerCount, String budget) {
        return "{\"expectedVersion\":" + expectedVersion + ",\"destinationKey\":\"" + destinationKey
                + "\",\"startDate\":\"2027-03-01\",\"endDate\":\"2027-03-02\",\"travelerCount\":" + travelerCount
                + ",\"budgetCents\":" + budget + "}";
    }

    private static String validRequest(String additions) {
        String suffix = additions.isBlank() ? "" : "," + additions;
        return "{\"destinationKey\":\"destination-sfo\",\"startDate\":\"2027-03-10\",\"endDate\":\"2027-03-14\",\"travelerCount\":2" + suffix + "}";
    }

    private int count(String table) {
        return jdbc.queryForObject("SELECT COUNT(*) FROM " + table, Integer.class);
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

    public static final class FailingDraftTrigger implements org.h2.api.Trigger {
        @Override
        public void init(Connection connection, String schemaName, String triggerName, String tableName,
                boolean before, int type) {
        }

        @Override
        public void fire(Connection connection, Object[] oldRow, Object[] newRow) throws SQLException {
            throw new SQLException("Controlled Draft write failure");
        }

        @Override public void close() { }
        @Override public void remove() { }
    }
}
