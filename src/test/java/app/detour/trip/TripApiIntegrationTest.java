package app.detour.trip;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
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
import app.detour.common.ClockConfiguration;
import java.time.Instant;
import java.time.ZonedDateTime;
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
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

@SpringBootTest
@AutoConfigureMockMvc
@Import(TestClockConfiguration.class)
class TripApiIntegrationTest {
    private static final String DATABASE_URL = "jdbc:h2:mem:trip_api_" + UUID.randomUUID() + ";DB_CLOSE_DELAY=-1";

    @DynamicPropertySource
    static void datasourceProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", () -> DATABASE_URL);
    }

    @Autowired private MockMvc mockMvc;
    @Autowired private JdbcTemplate jdbc;
    @MockitoSpyBean private TripRepository trips;
    @Autowired private TestClockConfiguration.TestClock testClock;

    @AfterEach
    void resetClock() {
        testClock.reset();
    }

    @Test
    void savesOneNamedOptionFromBudgetlessWorkingPlanAndRejectsEmptyWorkingPlan() throws Exception {
        Client owner = register("budgetless-option@example.test");
        MvcResult created = owner.unsafe(post("/api/trips"), validRequest("\"travelerAges\":[25,30]"))
                .andExpect(status().isCreated()).andReturn();
        String tripId = jsonField(created, "id");
        String draftId = tools.jackson.databind.json.JsonMapper.builder().build()
                .readTree(created.getResponse().getContentAsString()).get("workingPlan").get("id").asString();
        owner.unsafe(post("/api/trips/{tripId}/options", tripId),
                "{\"name\":\"Spring stay\",\"expectedVersion\":0,\"expectedDraftVersion\":0}")
                .andExpect(status().isBadRequest());
        insertSfoStaySelection(draftId);
        owner.unsafe(post("/api/trips/{tripId}/options", tripId),
                "{\"name\":\"Spring stay\",\"expectedVersion\":0,\"expectedDraftVersion\":0}")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.savedOptions.length()").value(1))
                .andExpect(jsonPath("$.savedOptions[0].name").value("Spring stay"))
                .andExpect(jsonPath("$.savedOptions[0].startDate").value("2027-03-10"))
                .andExpect(jsonPath("$.workingPlan.id").value(draftId))
                .andExpect(jsonPath("$.savedOptions[0].tally.remainingBudgetCents").doesNotExist());
    }

    @Test
    void loadsAndReplacesOnlyTheChosenUnbookedOption() throws Exception {
        Client owner = register("option-lifecycle@example.test");
        MvcResult created = owner.unsafe(post("/api/trips"), validRequest("\"travelerAges\":[25,30]"))
                .andExpect(status().isCreated()).andReturn();
        String tripId = jsonField(created, "id");
        String draftId = tools.jackson.databind.json.JsonMapper.builder().build()
                .readTree(created.getResponse().getContentAsString()).get("workingPlan").get("id").asString();
        insertSfoStaySelection(draftId);
        MvcResult saved = owner.unsafe(post("/api/trips/{tripId}/options", tripId),
                "{\"name\":\"First\",\"expectedVersion\":0,\"expectedDraftVersion\":0}")
                .andExpect(status().isCreated()).andReturn();
        String optionId = tools.jackson.databind.json.JsonMapper.builder().build()
                .readTree(saved.getResponse().getContentAsString()).get("savedOptions").get(0).get("id").asString();
        owner.unsafe(put("/api/trips/{tripId}/working-dates", tripId),
                "{\"expectedVersion\":1,\"expectedDraftVersion\":0,\"startDate\":\"2027-03-15\",\"endDate\":\"2027-03-19\"}")
                .andExpect(status().isOk());
        owner.unsafe(post("/api/trips/{tripId}/options/{optionId}/load", tripId, optionId),
                "{\"expectedVersion\":2,\"expectedDraftVersion\":1,\"expectedOptionVersion\":0,\"replaceWorking\":false}")
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("WORKING_REPLACEMENT_REQUIRED"));
        owner.unsafe(post("/api/trips/{tripId}/options/{optionId}/load", tripId, optionId),
                "{\"expectedVersion\":2,\"expectedDraftVersion\":1,\"expectedOptionVersion\":0,\"replaceWorking\":true}")
                .andExpect(status().isOk()).andExpect(jsonPath("$.workingPlan.startDate").value("2027-03-10"))
                .andExpect(jsonPath("$.savedOptions[0].startDate").value("2027-03-10"))
                .andExpect(jsonPath("$.workingPlan.id").value(draftId));
        owner.unsafe(put("/api/trips/{tripId}/options/{optionId}", tripId, optionId),
                "{\"name\":\"Updated\",\"expectedVersion\":3,\"expectedDraftVersion\":2,\"expectedOptionVersion\":0}")
                .andExpect(status().isOk()).andExpect(jsonPath("$.savedOptions.length()").value(1))
                .andExpect(jsonPath("$.savedOptions[0].id").value(optionId))
                .andExpect(jsonPath("$.savedOptions[0].name").value("Updated"))
                .andExpect(jsonPath("$.savedOptions[0].version").value(1));
        long tripRowId = jdbc.queryForObject("SELECT id FROM detour_trip WHERE public_id = ?", Long.class, UUID.fromString(tripId));
        long optionRowId = jdbc.queryForObject("SELECT id FROM detour_planned_itinerary WHERE public_id = ?", Long.class, UUID.fromString(optionId));
        UUID bookingId = UUID.randomUUID();
        jdbc.update("INSERT INTO detour_booking (public_id, trip_id, planned_itinerary_id, booking_reference, status, grand_total_cents, idempotency_key, created_at, canceled_at) VALUES (?, ?, ?, ?, 'CANCELED', 100, ?, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)",
                bookingId, tripRowId, optionRowId, "HIST-" + bookingId.toString().substring(0, 8), bookingId.toString());
        owner.unsafe(put("/api/trips/{tripId}/options/{optionId}", tripId, optionId),
                "{\"name\":\"Must fail\",\"expectedVersion\":4,\"expectedDraftVersion\":2,\"expectedOptionVersion\":1}")
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("IMMUTABLE_BOOKED_OPTION"));
        assertEquals("Updated", jdbc.queryForObject("SELECT name FROM detour_planned_itinerary WHERE id = ?", String.class, optionRowId));
    }

    @Test
    void optionRenameChangesOnlyNameAndRejectsStaleForeignAndBookedChanges() throws Exception {
        Client owner = register("rename-option-owner@example.test");
        Client other = register("rename-option-other@example.test");
        MvcResult created = owner.unsafe(post("/api/trips"), validRequest("\"travelerAges\":[25,30]"))
                .andExpect(status().isCreated()).andReturn();
        String tripId = jsonField(created, "id");
        var mapper = tools.jackson.databind.json.JsonMapper.builder().build();
        String draftId = mapper.readTree(created.getResponse().getContentAsString()).get("workingPlan").get("id").asString();
        insertSfoStaySelection(draftId);
        MvcResult saved = owner.unsafe(post("/api/trips/{tripId}/options", tripId),
                "{\"name\":\"Original\",\"expectedVersion\":0,\"expectedDraftVersion\":0}")
                .andExpect(status().isCreated()).andReturn();
        var before = mapper.readTree(saved.getResponse().getContentAsString()).get("savedOptions").get(0);
        String optionId = before.get("id").asString();
        long optionRowId = jdbc.queryForObject("SELECT id FROM detour_planned_itinerary WHERE public_id = ?", Long.class, UUID.fromString(optionId));
        int nightsBefore = jdbc.queryForObject("SELECT COUNT(*) FROM detour_planned_stay_night_snapshot WHERE planned_itinerary_id = ?", Integer.class, optionRowId);

        String rename = "{\"name\":\"  Later stay  \",\"expectedVersion\":1,\"expectedOptionVersion\":0}";
        other.unsafe(put("/api/trips/{tripId}/options/{optionId}/name", tripId, optionId), rename)
                .andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("RESOURCE_NOT_FOUND"));
        owner.unsafe(put("/api/trips/{tripId}/options/{optionId}/name", tripId, optionId),
                "{\"name\":\" \",\"expectedVersion\":1,\"expectedOptionVersion\":0}")
                .andExpect(status().isBadRequest());
        MvcResult renamed = owner.unsafe(put("/api/trips/{tripId}/options/{optionId}/name", tripId, optionId), rename)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.version").value(2))
                .andExpect(jsonPath("$.savedOptions[0].name").value("Later stay"))
                .andExpect(jsonPath("$.savedOptions[0].version").value(1)).andReturn();
        var after = mapper.readTree(renamed.getResponse().getContentAsString()).get("savedOptions").get(0);
        assertEquals(before.get("startDate"), after.get("startDate"));
        assertEquals(before.get("endDate"), after.get("endDate"));
        assertEquals(before.get("selections"), after.get("selections"));
        assertEquals(before.get("tally"), after.get("tally"));
        assertEquals(nightsBefore, jdbc.queryForObject("SELECT COUNT(*) FROM detour_planned_stay_night_snapshot WHERE planned_itinerary_id = ?", Integer.class, optionRowId));

        owner.unsafe(put("/api/trips/{tripId}/options/{optionId}/name", tripId, optionId), rename)
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("VERSION_CONFLICT"));
        owner.unsafe(put("/api/trips/{tripId}/options/{optionId}/name", tripId, optionId),
                "{\"name\":\"Stale option\",\"expectedVersion\":2,\"expectedOptionVersion\":0}")
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("VERSION_CONFLICT"));
        assertEquals("Later stay", jdbc.queryForObject("SELECT name FROM detour_planned_itinerary WHERE id = ?", String.class, optionRowId));

        long tripRowId = jdbc.queryForObject("SELECT id FROM detour_trip WHERE public_id = ?", Long.class, UUID.fromString(tripId));
        UUID bookingId = UUID.randomUUID();
        String reference = "HIST-" + bookingId.toString().substring(0, 8);
        jdbc.update("INSERT INTO detour_booking (public_id, trip_id, planned_itinerary_id, booking_reference, status, grand_total_cents, idempotency_key, created_at, canceled_at) VALUES (?, ?, ?, ?, 'CANCELED', 100, ?, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)",
                bookingId, tripRowId, optionRowId, reference, bookingId.toString());
        owner.unsafe(get("/api/trips/{tripId}", tripId), null).andExpect(status().isOk())
                .andExpect(jsonPath("$.savedOptions[0].booked").value(true));
        owner.unsafe(get("/api/trips"), null).andExpect(status().isOk())
                .andExpect(jsonPath("$.upcoming[0].alternatives[1].booked").value(true));
        owner.unsafe(put("/api/trips/{tripId}/options/{optionId}/name", tripId, optionId),
                "{\"name\":\"Booked change\",\"expectedVersion\":2,\"expectedOptionVersion\":1}")
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("IMMUTABLE_BOOKED_OPTION"));
        assertEquals(reference, jdbc.queryForObject("SELECT booking_reference FROM detour_booking WHERE planned_itinerary_id = ?", String.class, optionRowId));
        assertEquals("Later stay", jdbc.queryForObject("SELECT name FROM detour_planned_itinerary WHERE id = ?", String.class, optionRowId));
    }

    @Test
    void sharedWorkingEditsAreNoOpAwareAndGuardBothVersions() throws Exception {
        Client owner = register("working-versions@example.test");
        MvcResult created = owner.unsafe(post("/api/trips"), validRequest("\"travelerAges\":[25,30]"))
                .andExpect(status().isCreated()).andReturn();
        String tripId = jsonField(created, "id");
        String body = "{\"expectedVersion\":0,\"expectedDraftVersion\":0,\"destinationKey\":\"destination-sfo\","
                + "\"startDate\":\"2027-03-10\",\"endDate\":\"2027-03-14\",\"travelerCount\":2,"
                + "\"travelerAges\":[25,30],\"budgetCents\":null}";
        owner.unsafe(put("/api/trips/{tripId}", tripId), body)
                .andExpect(status().isOk()).andExpect(jsonPath("$.version").value(0))
                .andExpect(jsonPath("$.workingPlan.version").value(0));
        String changed = body.replace("2027-03-10", "2027-03-15").replace("2027-03-14", "2027-03-19");
        owner.unsafe(put("/api/trips/{tripId}", tripId), changed)
                .andExpect(status().isOk()).andExpect(jsonPath("$.version").value(1))
                .andExpect(jsonPath("$.workingPlan.version").value(1))
                .andExpect(jsonPath("$.workingPlan.startDate").value("2027-03-15"));
        owner.unsafe(put("/api/trips/{tripId}", tripId), changed)
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("VERSION_CONFLICT"));
        assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM detour_trip_draft WHERE trip_id = (SELECT id FROM detour_trip WHERE public_id = ?)",
                Integer.class, UUID.fromString(tripId)));
    }

    @Test
    void optionReplacementFailureRollsBackVersionsAndSnapshotGraph() throws Exception {
        Client owner = register("option-rollback@example.test");
        MvcResult created = owner.unsafe(post("/api/trips"), validRequest("\"travelerAges\":[25,30]"))
                .andExpect(status().isCreated()).andReturn();
        String tripId = jsonField(created, "id");
        String draftId = tools.jackson.databind.json.JsonMapper.builder().build()
                .readTree(created.getResponse().getContentAsString()).get("workingPlan").get("id").asString();
        insertSfoStaySelection(draftId);
        MvcResult saved = owner.unsafe(post("/api/trips/{tripId}/options", tripId),
                "{\"name\":\"Stable\",\"expectedVersion\":0,\"expectedDraftVersion\":0}")
                .andExpect(status().isCreated()).andReturn();
        String optionId = tools.jackson.databind.json.JsonMapper.builder().build()
                .readTree(saved.getResponse().getContentAsString()).get("savedOptions").get(0).get("id").asString();
        long rowId = jdbc.queryForObject("SELECT id FROM detour_planned_itinerary WHERE public_id = ?", Long.class, UUID.fromString(optionId));
        int nightsBefore = jdbc.queryForObject("SELECT COUNT(*) FROM detour_planned_stay_night_snapshot WHERE planned_itinerary_id = ?", Integer.class, rowId);
        org.mockito.Mockito.doThrow(new IllegalStateException("forced snapshot write failure"))
                .when(trips).replacePlannedSnapshots(org.mockito.ArgumentMatchers.eq(rowId),
                        org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.any(),
                        org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any());
        owner.unsafe(put("/api/trips/{tripId}/options/{optionId}", tripId, optionId),
                "{\"name\":\"Should roll back\",\"expectedVersion\":1,\"expectedDraftVersion\":0,\"expectedOptionVersion\":0}")
                .andExpect(status().is5xxServerError());
        assertEquals(1L, jdbc.queryForObject("SELECT version FROM detour_trip WHERE public_id = ?", Long.class, UUID.fromString(tripId)));
        assertEquals(0L, jdbc.queryForObject("SELECT version FROM detour_planned_itinerary WHERE id = ?", Long.class, rowId));
        assertEquals("Stable", jdbc.queryForObject("SELECT name FROM detour_planned_itinerary WHERE id = ?", String.class, rowId));
        assertEquals(nightsBefore, jdbc.queryForObject("SELECT COUNT(*) FROM detour_planned_stay_night_snapshot WHERE planned_itinerary_id = ?", Integer.class, rowId));
    }

    @Test
    void optionReplacementRefreshesCatalogPriceAndReportsAdjustment() throws Exception {
        Client owner = register("option-reprice@example.test");
        MvcResult created = owner.unsafe(post("/api/trips"), validRequest("\"travelerAges\":[25,30]"))
                .andExpect(status().isCreated()).andReturn();
        String tripId = jsonField(created, "id");
        String draftId = tools.jackson.databind.json.JsonMapper.builder().build()
                .readTree(created.getResponse().getContentAsString()).get("workingPlan").get("id").asString();
        insertSfoStaySelection(draftId);
        MvcResult saved = owner.unsafe(post("/api/trips/{tripId}/options", tripId),
                "{\"name\":\"Stay\",\"expectedVersion\":0,\"expectedDraftVersion\":0}")
                .andExpect(status().isCreated()).andReturn();
        String optionId = tools.jackson.databind.json.JsonMapper.builder().build()
                .readTree(saved.getResponse().getContentAsString()).get("savedOptions").get(0).get("id").asString();
        long optionRowId = jdbc.queryForObject("SELECT id FROM detour_planned_itinerary WHERE public_id = ?", Long.class, UUID.fromString(optionId));
        long unitId = jdbc.queryForObject("SELECT accommodation_unit_id FROM detour_planned_stay_snapshot WHERE planned_itinerary_id = ?", Long.class, optionRowId);
        long oldPrice = jdbc.queryForObject("SELECT base_price_cents FROM accommodation_nightly_inventory WHERE accommodation_unit_id = ? AND night_date = DATE '2027-03-10'", Long.class, unitId);
        try {
            jdbc.update("UPDATE accommodation_nightly_inventory SET base_price_cents = base_price_cents + 100 WHERE accommodation_unit_id = ? AND night_date = DATE '2027-03-10'", unitId);
            owner.unsafe(put("/api/trips/{tripId}/options/{optionId}", tripId, optionId),
                    "{\"name\":\"Stay repriced\",\"expectedVersion\":1,\"expectedDraftVersion\":0,\"expectedOptionVersion\":0}")
                    .andExpect(status().isOk()).andExpect(jsonPath("$.revisionSummary.adjustments.length()").value(1));
            assertEquals(oldPrice + 100, jdbc.queryForObject("SELECT base_price_cents FROM detour_planned_stay_night_snapshot WHERE planned_itinerary_id = ? AND night_date = DATE '2027-03-10'", Long.class, optionRowId));
        } finally {
            jdbc.update("UPDATE accommodation_nightly_inventory SET base_price_cents = ? WHERE accommodation_unit_id = ? AND night_date = DATE '2027-03-10'", oldPrice, unitId);
        }
    }

    @Test
    void namedTripHasOneDatedWorkingPlanAndVersionedRename() throws Exception {
        Client owner = register("named-trip@example.test");
        Client other = register("named-trip-other@example.test");
        MvcResult created = owner.unsafe(post("/api/trips"), validRequest("\"name\":\"  Family spring  \""))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.name").value("Family spring"))
                .andExpect(jsonPath("$.workingPlan.startDate").value("2027-03-10"))
                .andExpect(jsonPath("$.workingPlan.endDate").value("2027-03-14"))
                .andExpect(jsonPath("$.savedOptions.length()").value(0)).andReturn();
        String tripId = jsonField(created, "id");
        String workingId = tools.jackson.databind.json.JsonMapper.builder().build()
                .readTree(created.getResponse().getContentAsString()).get("workingPlan").get("id").asString();
        owner.unsafe(post("/api/trips"), validRequest("\"name\":\"Family spring\""))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("DUPLICATE_TRIP_NAME"));
        MvcResult another = owner.unsafe(post("/api/trips"), validRequest("\"name\":\"Other trip\""))
                .andExpect(status().isCreated()).andReturn();
        owner.unsafe(put("/api/trips/{tripId}/name", jsonField(another, "id")),
                "{\"expectedVersion\":0,\"name\":\"family SPRING\"}")
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("DUPLICATE_TRIP_NAME"));
        other.unsafe(post("/api/trips"), validRequest("\"name\":\"Family spring\""))
                .andExpect(status().isCreated());
        owner.unsafe(put("/api/trips/{tripId}/name", tripId), "{\"expectedVersion\":0,\"name\":\"  Summer ideas  \"}")
                .andExpect(status().isOk()).andExpect(jsonPath("$.name").value("Summer ideas"))
                .andExpect(jsonPath("$.id").value(tripId)).andExpect(jsonPath("$.workingPlan.id").value(workingId));
        owner.unsafe(put("/api/trips/{tripId}/name", tripId), "{\"expectedVersion\":0,\"name\":\"Stale\"}")
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("VERSION_CONFLICT"));
        other.unsafe(put("/api/trips/{tripId}/name", tripId), "{\"expectedVersion\":1,\"name\":\"Foreign\"}")
                .andExpect(status().isNotFound());
        owner.unsafe(put("/api/trips/{tripId}/working-dates", tripId),
                "{\"expectedVersion\":1,\"expectedDraftVersion\":0,\"startDate\":\"2027-03-15\",\"endDate\":\"2027-03-19\"}")
                .andExpect(status().isOk()).andExpect(jsonPath("$.workingPlan.startDate").value("2027-03-15"))
                .andExpect(jsonPath("$.startDate").value("2027-03-15"))
                .andExpect(jsonPath("$.workingPlan.version").value(1))
                .andExpect(jsonPath("$.travelerAges[0]").value(25));
        owner.unsafe(put("/api/trips/{tripId}/working-dates", tripId),
                "{\"expectedVersion\":1,\"expectedDraftVersion\":0,\"startDate\":\"2027-03-16\",\"endDate\":\"2027-03-20\"}")
                .andExpect(status().isConflict());
        assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM detour_trip_draft WHERE trip_id = (SELECT id FROM detour_trip WHERE public_id = ?)", Integer.class, UUID.fromString(tripId)));
    }

    @Test
    void creationRejectsMissingNameAndIncompleteAgesWithoutWritingTrip() throws Exception {
        Client owner = register("required-ages@example.test");
        int before = count("detour_trip");
        owner.unsafe(post("/api/trips"), "{\"destinationKey\":\"destination-sfo\",\"startDate\":\"2027-03-10\",\"endDate\":\"2027-03-14\",\"travelerCount\":2,\"travelerAges\":[25,25]}")
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.fields.name").exists());
        owner.unsafe(post("/api/trips"), validRequest("\"travelerAges\":[25,null]"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.fields.travelerAges").exists());
        owner.unsafe(post("/api/trips"), "{\"name\":\"Ages needed\",\"destinationKey\":\"destination-sfo\",\"startDate\":\"2027-03-10\",\"endDate\":\"2027-03-14\",\"travelerCount\":2}")
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.fields.travelerAges").exists());
        assertEquals(before, count("detour_trip"));
    }

    @Test
    void generatedTripNameFollowsDateChangesAndAvoidsExistingNames() throws Exception {
        Client owner = register("dated-trip-name@example.test");
        String base = "San Francisco - 2027-03-10 to 2027-03-14";
        String target = "San Francisco - 2027-03-15 to 2027-03-19";
        owner.unsafe(post("/api/trips"), validRequest("\"name\":\"" + target + "\""))
                .andExpect(status().isCreated());
        MvcResult created = owner.unsafe(post("/api/trips"), validRequest("\"name\":\"" + base + "\""))
                .andExpect(status().isCreated()).andReturn();
        owner.unsafe(put("/api/trips/{tripId}/working-dates", jsonField(created, "id")),
                "{\"expectedVersion\":0,\"expectedDraftVersion\":0,\"startDate\":\"2027-03-15\",\"endDate\":\"2027-03-19\"}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value(target + " - A"));
    }

    @Test
    void savedOptionsKeepIndependentDatesNamesAndStaySnapshots() throws Exception {
        Client owner = register("dated-options@example.test");
        MvcResult created = owner.unsafe(post("/api/trips"), validRequest("\"travelerAges\":[30,12],\"budgetCents\":500000"))
                .andExpect(status().isCreated()).andReturn();
        String tripId = jsonField(created, "id");
        String draftId = tools.jackson.databind.json.JsonMapper.builder().build()
                .readTree(created.getResponse().getContentAsString()).get("workingPlan").get("id").asString();
        insertSfoStaySelection(draftId);
        MvcResult first = owner.unsafe(post("/api/trips/{tripId}/options", tripId),
                "{\"name\":\"Saved option\",\"expectedVersion\":0,\"expectedDraftVersion\":0}").andExpect(status().isCreated()).andReturn();
        String firstId = tools.jackson.databind.json.JsonMapper.builder().build()
                .readTree(first.getResponse().getContentAsString()).get("savedOptions").get(0).get("id").asString();
        owner.unsafe(put("/api/trips/{tripId}/working-dates", tripId),
                "{\"expectedVersion\":1,\"expectedDraftVersion\":0,\"startDate\":\"2027-03-15\",\"endDate\":\"2027-03-19\"}")
                .andExpect(status().isOk()).andExpect(jsonPath("$.revisionSummary.removals.length()").value(0));
        MvcResult second = owner.unsafe(post("/api/trips/{tripId}/options", tripId),
                "{\"name\":\"Saved option\",\"expectedVersion\":2,\"expectedDraftVersion\":1}")
                .andExpect(status().isCreated()).andExpect(jsonPath("$.savedOptions.length()").value(2)).andReturn();
        var options = tools.jackson.databind.json.JsonMapper.builder().build()
                .readTree(second.getResponse().getContentAsString()).get("savedOptions");
        String secondId = options.get(1).get("id").asString();
        jdbc.update("UPDATE detour_planned_itinerary SET name = 'Early stay' WHERE public_id = ?", UUID.fromString(firstId));
        jdbc.update("UPDATE detour_planned_itinerary SET name = 'Late stay' WHERE public_id = ?", UUID.fromString(secondId));
        owner.unsafe(get("/api/trips/{tripId}", tripId), null).andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Spring trip"))
                .andExpect(jsonPath("$.workingPlan.startDate").value("2027-03-15"))
                .andExpect(jsonPath("$.savedOptions[0].name").value("Early stay"))
                .andExpect(jsonPath("$.savedOptions[0].startDate").value("2027-03-10"))
                .andExpect(jsonPath("$.savedOptions[0].selections.stay.nights[0].date").value("2027-03-10"))
                .andExpect(jsonPath("$.savedOptions[1].name").value("Late stay"))
                .andExpect(jsonPath("$.savedOptions[1].startDate").value("2027-03-15"))
                .andExpect(jsonPath("$.savedOptions[1].selections.stay.nights[0].date").value("2027-03-15"))
                .andExpect(jsonPath("$.travelerAges[0]").value(30))
                .andExpect(jsonPath("$.drafts.length()").value(1));

        long tripRowId = jdbc.queryForObject("SELECT id FROM detour_trip WHERE public_id = ?", Long.class, UUID.fromString(tripId));
        long ownerId = jdbc.queryForObject("SELECT owner_user_id FROM detour_trip WHERE id = ?", Long.class, tripRowId);
        long earlyOptionId = jdbc.queryForObject("SELECT id FROM detour_planned_itinerary WHERE public_id = ?", Long.class, UUID.fromString(firstId));
        long lateOptionId = jdbc.queryForObject("SELECT id FROM detour_planned_itinerary WHERE public_id = ?", Long.class, UUID.fromString(secondId));
        long frozenNightPrice = jdbc.queryForObject("SELECT base_price_cents FROM detour_planned_stay_night_snapshot WHERE planned_itinerary_id = ? ORDER BY night_date LIMIT 1", Long.class, earlyOptionId);
        UUID bookingId = UUID.randomUUID();
        jdbc.update("INSERT INTO detour_booking (public_id, trip_id, planned_itinerary_id, booking_reference, status, grand_total_cents, idempotency_key, created_at, canceled_at) VALUES (?, ?, ?, ?, 'CANCELED', 100, ?, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)",
                bookingId, tripRowId, earlyOptionId, "HIST-" + bookingId.toString().substring(0, 8), bookingId.toString());
        long bookingRowId = jdbc.queryForObject("SELECT id FROM detour_booking WHERE public_id = ?", Long.class, bookingId);
        jdbc.update("INSERT INTO detour_booking_stay_snapshot (booking_id, accommodation_unit_id, unit_count, property_name, unit_name) SELECT ?, accommodation_unit_id, unit_count, property_name, unit_name FROM detour_planned_stay_snapshot WHERE planned_itinerary_id = ?",
                bookingRowId, earlyOptionId);
        jdbc.update("INSERT INTO detour_booking_stay_night_snapshot (booking_id, night_date, base_price_cents, tax_cents, fee_cents) SELECT ?, night_date, base_price_cents, tax_cents, fee_cents FROM detour_planned_stay_night_snapshot WHERE planned_itinerary_id = ?",
                bookingRowId, earlyOptionId);

        assertFalse(trips.advanceVersionForOption(tripRowId, ownerId, 3, earlyOptionId, 0));
        assertTrue(trips.advanceVersionForOption(tripRowId, ownerId, 3, lateOptionId, 0));
        trips.renameOption(lateOptionId, "Revised late stay");
        assertEquals("Early stay", jdbc.queryForObject("SELECT name FROM detour_planned_itinerary WHERE id = ?", String.class, earlyOptionId));
        assertEquals(frozenNightPrice, jdbc.queryForObject("SELECT base_price_cents FROM detour_planned_stay_night_snapshot WHERE planned_itinerary_id = ? ORDER BY night_date LIMIT 1", Long.class, earlyOptionId));
        assertEquals(earlyOptionId, jdbc.queryForObject("SELECT planned_itinerary_id FROM detour_booking WHERE public_id = ?", Long.class, bookingId));
        assertEquals(frozenNightPrice, jdbc.queryForObject("SELECT base_price_cents FROM detour_booking_stay_night_snapshot WHERE booking_id = ? ORDER BY night_date LIMIT 1", Long.class, bookingRowId));
        assertEquals("Revised late stay", jdbc.queryForObject("SELECT name FROM detour_planned_itinerary WHERE id = ?", String.class, lateOptionId));
    }

    @Test
    void publicShellDoesNotAuthorizeTripReadsOrMutations() throws Exception {
        Client owner = register("public-boundary@example.test");
        MvcResult created = owner.unsafe(post("/api/trips"), validRequest(""))
                .andExpect(status().isCreated()).andReturn();
        String tripId = jsonField(created, "id");
        var body = tools.jackson.databind.json.JsonMapper.builder().build().readTree(created.getResponse().getContentAsString());
        String draftId = body.get("drafts").get(0).get("id").asString();
        Client guest = anonymous();

        mockMvc.perform(get("/api/trips").cookie(guest.csrf)).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/trips/{tripId}", tripId).cookie(guest.csrf)).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/trips/{tripId}/drafts/{draftId}/airfare", tripId, draftId).cookie(guest.csrf))
                .andExpect(status().isUnauthorized());
        guest.unsafe(post("/api/trips"), validRequest("")).andExpect(status().isUnauthorized());
        guest.unsafe(put("/api/trips/{tripId}/drafts/{draftId}/airfare", tripId, draftId), "{}")
                .andExpect(status().isUnauthorized());
        guest.unsafe(post("/api/trips/{tripId}/bookings", tripId), "{}")
                .andExpect(status().isUnauthorized());
        mockMvc.perform(post("/api/trips").session(owner.session).cookie(owner.csrf)
                        .contentType(MediaType.APPLICATION_JSON).content(validRequest("")))
                .andExpect(status().isForbidden());
    }

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
                .andExpect(jsonPath("$.label").value("Spring trip"))
                .andExpect(jsonPath("$.name").value("Spring trip"))
                .andExpect(jsonPath("$.workingPlan.startDate").value("2027-03-10"))
                .andExpect(jsonPath("$.savedOptions.length()").value(0))
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
                "{\"name\":\"Test trip\",\"destinationKey\":\"destination-sfo\",\"endDate\":\"2027-03-14\",\"travelerCount\":2}",
                "{\"name\":\"Test trip\",\"destinationKey\":\"destination-sfo\",\"startDate\":\"2027-03-10\",\"travelerCount\":2}",
                "{\"name\":\"Test trip\",\"destinationKey\":\"destination-sfo\",\"startDate\":\"2027-03-10\",\"endDate\":\"2027-03-14\"}",
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
    void requiresKnownAgesAndPreservesAbsentBudgetSeparatelyFromZero() throws Exception {
        Client owner = register("optionals@example.test");
        owner.unsafe(post("/api/trips"), validRequest(""))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.travelerAges[0]").value(25))
                .andExpect(jsonPath("$.budgetCents").doesNotExist());
        owner.unsafe(post("/api/trips"), validRequest("\"name\":\"Spring trip B\",\"travelerAges\":[0,120],\"budgetCents\":100000000"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.travelerAges[0]").value(0))
                .andExpect(jsonPath("$.travelerAges[1]").value(120))
                .andExpect(jsonPath("$.budgetCents").value(100000000));
        owner.unsafe(post("/api/trips"), validRequest("\"name\":\"Spring trip C\",\"travelerAges\":[0,17],\"budgetCents\":0"))
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
                .andExpect(status().isCreated()).andExpect(jsonPath("$.name").value("Unaccepted custom name"));
        other.unsafe(post("/api/trips"), validRequest(""))
                .andExpect(status().isCreated());
        assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM detour_trip WHERE owner_user_id = (SELECT id FROM detour_user WHERE canonical_email = 'first-trip@example.test')", Integer.class));
        assertEquals(2, jdbc.queryForObject("SELECT COUNT(*) FROM detour_trip WHERE owner_user_id = (SELECT id FROM detour_user WHERE canonical_email = 'second-trip@example.test')", Integer.class));
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
    void preventsAdditionalWorkingDrafts() throws Exception {
        Client owner = register("alternatives@example.test");
        MvcResult created = owner.unsafe(post("/api/trips"), validRequest("")).andExpect(status().isCreated()).andReturn();
        String tripId = jsonField(created, "id");
        String sourceId = tools.jackson.databind.json.JsonMapper.builder().build().readTree(created.getResponse().getContentAsString()).get("drafts").get(0).get("id").asString();
        owner.unsafe(post("/api/trips/{tripId}/drafts", tripId), "{\"expectedVersion\":0}")
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("WORKING_PLAN_EXISTS"));
        assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM detour_trip_draft draft JOIN detour_trip trip ON trip.id = draft.trip_id WHERE trip.public_id = ?", Integer.class, UUID.fromString(tripId)));
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
        owner.unsafe(post("/api/trips/{tripId}/drafts/{draftId}/duplicate", tripId, sourceId),
                "{\"expectedVersion\":1,\"expectedDraftVersion\":1}")
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("WORKING_PLAN_EXISTS"));
        owner.unsafe(delete("/api/trips/{tripId}/drafts/{draftId}", tripId, sourceId),
                "{\"expectedVersion\":1,\"expectedDraftVersion\":1}")
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("WORKING_PLAN_REQUIRED"));
        owner.unsafe(get("/api/trips/{tripId}", tripId), null)
                .andExpect(status().isOk()).andExpect(jsonPath("$.version").value(1))
                .andExpect(jsonPath("$.workingPlan.id").value(sourceId));
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
        owner.unsafe(post("/api/trips/{tripId}/drafts", tripId), "{\"expectedVersion\":0}")
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("WORKING_PLAN_EXISTS"));
        mockMvc.perform(get("/api/trips/{tripId}", tripId).session(owner.session).cookie(owner.csrf))
                .andExpect(status().isOk()).andExpect(jsonPath("$.version").value(0)).andExpect(jsonPath("$.drafts.length()").value(1));
        owner.unsafe(post("/api/trips/{tripId}/drafts", tripId), "{\"expectedVersion\":0}")
                .andExpect(status().isConflict());
    }

    @Test
    void promotesAReadyDraftAsAnIndependentImmutablePlannedSnapshot() throws Exception {
        Client owner = register("planned@example.test");
        MvcResult created = owner.unsafe(post("/api/trips"), validRequest("\"travelerAges\":[18,12],\"budgetCents\":0")).andExpect(status().isCreated()).andReturn();
        String tripId = jsonField(created, "id");
        String draftId = tools.jackson.databind.json.JsonMapper.builder().build().readTree(created.getResponse().getContentAsString()).get("drafts").get(0).get("id").asString();
        insertSfoAirfareSelection(draftId);
        MvcResult promoted = owner.unsafe(post("/api/trips/{tripId}/options", tripId), "{\"name\":\"Saved option\",\"expectedVersion\":0,\"expectedDraftVersion\":0}")
                .andExpect(status().isCreated()).andExpect(jsonPath("$.version").value(1)).andExpect(jsonPath("$.drafts.length()").value(1))
                .andExpect(jsonPath("$.planned.length()").value(1)).andExpect(jsonPath("$.planned[0].selections.airfare.outboundDescription").isString()).andReturn();
        var promotedBody = tools.jackson.databind.json.JsonMapper.builder().build().readTree(promoted.getResponse().getContentAsString());
        String plannedId = promotedBody.get("planned").get(0).get("id").asString();
        long copiedFare = promotedBody.get("planned").get(0).get("selections").get("airfare").get("outboundBaseFareCents").asLong();
        jdbc.update("UPDATE flight_instance SET base_fare_cents = base_fare_cents + 999 WHERE id = (SELECT outbound_flight_instance_id FROM detour_trip_draft_airfare_selection WHERE draft_id = (SELECT id FROM detour_trip_draft WHERE public_id = ?))", UUID.fromString(draftId));
        owner.unsafe(get("/api/trips/{tripId}", tripId), null).andExpect(status().isOk()).andExpect(jsonPath("$.planned[0].id").value(plannedId))
                .andExpect(jsonPath("$.planned[0].selections.airfare.outboundBaseFareCents").value(copiedFare));
        owner.unsafe(post("/api/trips/{tripId}/alternatives/{alternativeId}/duplicate", tripId, plannedId), "{\"expectedVersion\":1}")
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("WORKING_PLAN_EXISTS"));

        // Save the same Working plan again: multiple Saved options can coexist under one Trip.
        MvcResult secondPromoted = owner.unsafe(post("/api/trips/{tripId}/options", tripId), "{\"name\":\"Saved option\",\"expectedVersion\":1,\"expectedDraftVersion\":0}")
                .andExpect(status().isCreated()).andExpect(jsonPath("$.version").value(2)).andExpect(jsonPath("$.planned.length()").value(2)).andReturn();
        String secondPlannedId = tools.jackson.databind.json.JsonMapper.builder().build().readTree(secondPromoted.getResponse().getContentAsString()).get("planned").get(1).get("id").asString();

        owner.unsafe(post("/api/trips/{tripId}/drafts/{draftId}/duplicate", tripId, plannedId), "{\"expectedVersion\":2,\"expectedDraftVersion\":0}")
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("IMMUTABLE_ALTERNATIVE"));
        owner.unsafe(delete("/api/trips/{tripId}/alternatives/{alternativeId}", tripId, plannedId), "{\"expectedVersion\":2}")
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.fields.confirmed").exists());
        owner.unsafe(delete("/api/trips/{tripId}/alternatives/{alternativeId}", tripId, plannedId), "{\"expectedVersion\":2,\"confirmed\":true}")
                .andExpect(status().isOk()).andExpect(jsonPath("$.planned.length()").value(1))
                .andExpect(jsonPath("$.planned[0].id").value(secondPlannedId))
                .andExpect(jsonPath("$.drafts.length()").value(1));
    }

    @Test
    void reportsWorkingReadinessIssuesAndRejectsLegacyPromotionWithoutMutation() throws Exception {
        Client owner = register("not-ready@example.test");
        MvcResult created = owner.unsafe(post("/api/trips"), validRequest("")).andExpect(status().isCreated()).andReturn();
        var body = tools.jackson.databind.json.JsonMapper.builder().build().readTree(created.getResponse().getContentAsString());
        String firstTripId = body.get("id").asString();
        String firstDraftId = body.get("drafts").get(0).get("id").asString();
        owner.unsafe(get("/api/trips/{tripId}/drafts/{draftId}/readiness", firstTripId, firstDraftId), null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.blockingIssues.travelerAges").doesNotExist())
                .andExpect(jsonPath("$.blockingIssues.budgetCents").exists())
                .andExpect(jsonPath("$.blockingIssues.components").exists());
        int optionsBefore = count("detour_planned_itinerary");
        owner.unsafe(post("/api/trips/{tripId}/drafts/{draftId}/plan", firstTripId, firstDraftId),
                "{\"expectedVersion\":0,\"expectedDraftVersion\":0}")
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("USE_NAMED_OPTION"));
        assertEquals(optionsBefore, count("detour_planned_itinerary"));
        owner.unsafe(get("/api/trips/{tripId}", firstTripId), null)
                .andExpect(status().isOk()).andExpect(jsonPath("$.version").value(0))
                .andExpect(jsonPath("$.savedOptions.length()").value(0));

        MvcResult minorTrip = owner.unsafe(post("/api/trips"), validRequest("\"name\":\"Minors trip\",\"travelerAges\":[17,12]")).andExpect(status().isCreated()).andReturn();
        var minorBody = tools.jackson.databind.json.JsonMapper.builder().build().readTree(minorTrip.getResponse().getContentAsString());
        owner.unsafe(get("/api/trips/{tripId}/drafts/{draftId}/readiness", minorBody.get("id").asString(),
                minorBody.get("drafts").get(0).get("id").asString()), null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.blockingIssues.adult").value("At least one traveler must be an adult before planning."))
                .andExpect(jsonPath("$.blockingIssues.budgetCents").exists())
                .andExpect(jsonPath("$.blockingIssues.components").exists())
                .andExpect(jsonPath("$.blockingIssues.travelerAges").doesNotExist());
    }

    @Test
    void promotesEachStructurallyValidSelectionKindWithoutCanonicalPricing() throws Exception {
        Client owner = register("all-kinds@example.test");
        // Test Stay selection only
        MvcResult createdStay = owner.unsafe(post("/api/trips"), validRequest("\"travelerAges\":[30,25],\"budgetCents\":500000")).andExpect(status().isCreated()).andReturn();
        String tripId1 = jsonField(createdStay, "id");
        String draftId1 = tools.jackson.databind.json.JsonMapper.builder().build().readTree(createdStay.getResponse().getContentAsString()).get("drafts").get(0).get("id").asString();
        insertSfoStaySelection(draftId1);
        owner.unsafe(post("/api/trips/{tripId}/options", tripId1), "{\"name\":\"Saved option\",\"expectedVersion\":0,\"expectedDraftVersion\":0}")
                .andExpect(status().isCreated()).andExpect(jsonPath("$.planned.length()").value(1))
                .andExpect(jsonPath("$.planned[0].selections.stay.propertyName").value("Summit Family Suites"))
                .andExpect(jsonPath("$.planned[0].selections.stay.unitCount").value(1))
                .andExpect(jsonPath("$.planned[0].selections.stay.nights.length()").value(4))
                .andExpect(jsonPath("$.planned[0].selections.airfare").doesNotExist())
                .andExpect(jsonPath("$.planned[0].selections.rental").doesNotExist());

        // Test Rental selection only
        MvcResult createdRental = owner.unsafe(post("/api/trips"), validRequest("\"name\":\"Rental trip\",\"travelerAges\":[30,25],\"budgetCents\":500000")).andExpect(status().isCreated()).andReturn();
        String tripId2 = jsonField(createdRental, "id");
        String draftId2 = tools.jackson.databind.json.JsonMapper.builder().build().readTree(createdRental.getResponse().getContentAsString()).get("drafts").get(0).get("id").asString();
        insertSfoRentalSelection(draftId2);
        owner.unsafe(post("/api/trips/{tripId}/options", tripId2), "{\"name\":\"Saved option\",\"expectedVersion\":0,\"expectedDraftVersion\":0}")
                .andExpect(status().isCreated()).andExpect(jsonPath("$.planned.length()").value(1))
                .andExpect(jsonPath("$.planned[0].selections.rental.locationName").value("Harborline Mobility at SFO"))
                .andExpect(jsonPath("$.planned[0].selections.rental.vehicleClassName").value("SFO Economy"))
                .andExpect(jsonPath("$.planned[0].selections.rental.unitIdentifier").value("SFO-ECO-01"))
                .andExpect(jsonPath("$.planned[0].selections.rental.dailyBasePriceCents").value(4277))
                .andExpect(jsonPath("$.planned[0].selections.airfare").doesNotExist())
                .andExpect(jsonPath("$.planned[0].selections.stay").doesNotExist());

        // Test All three selections together
        MvcResult createdAll = owner.unsafe(post("/api/trips"), validRequest("\"name\":\"All selections trip\",\"travelerAges\":[30,25],\"budgetCents\":500000")).andExpect(status().isCreated()).andReturn();
        String tripId3 = jsonField(createdAll, "id");
        String draftId3 = tools.jackson.databind.json.JsonMapper.builder().build().readTree(createdAll.getResponse().getContentAsString()).get("drafts").get(0).get("id").asString();
        insertSfoAirfareSelection(draftId3);
        insertSfoStaySelection(draftId3);
        insertSfoRentalSelection(draftId3);
        owner.unsafe(post("/api/trips/{tripId}/options", tripId3), "{\"name\":\"Saved option\",\"expectedVersion\":0,\"expectedDraftVersion\":0}")
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

        MvcResult promoted = owner.unsafe(post("/api/trips/{tripId}/options", tripId), "{\"name\":\"Saved option\",\"expectedVersion\":0,\"expectedDraftVersion\":0}")
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

        owner.unsafe(post("/api/trips/{tripId}/alternatives/{alternativeId}/duplicate", tripId, draftId),
                "{\"expectedVersion\":0,\"expectedDraftVersion\":0}")
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("WORKING_PLAN_EXISTS"));

        // Promote first draft
        MvcResult promoted = owner.unsafe(post("/api/trips/{tripId}/options", tripId), "{\"name\":\"Saved option\",\"expectedVersion\":0,\"expectedDraftVersion\":0}")
                .andExpect(status().isCreated()).andExpect(jsonPath("$.version").value(1)).andReturn();
        String plannedId = tools.jackson.databind.json.JsonMapper.builder().build().readTree(promoted.getResponse().getContentAsString()).get("planned").get(0).get("id").asString();

        owner.unsafe(post("/api/trips/{tripId}/alternatives/{alternativeId}/duplicate", tripId, plannedId),
                "{\"expectedVersion\":1}").andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("WORKING_PLAN_EXISTS"));
        owner.unsafe(get("/api/trips/{tripId}", tripId), null).andExpect(status().isOk())
                .andExpect(jsonPath("$.workingPlan.id").value(draftId))
                .andExpect(jsonPath("$.planned[0].id").value(plannedId))
                .andExpect(jsonPath("$.version").value(1));
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
        var promoted = owner.unsafe(post("/api/trips/{tripId}/options", tripId), "{\"name\":\"Saved option\",\"expectedVersion\":0,\"expectedDraftVersion\":0}").andReturn();
        String plannedId = tools.jackson.databind.json.JsonMapper.builder().build().readTree(promoted.getResponse().getContentAsString()).get("planned").get(0).get("id").asString();

        // 1. Plan route
        String foreignPlan = other.unsafe(post("/api/trips/{tripId}/options", tripId), "{\"name\":\"Saved option\",\"expectedVersion\":0,\"expectedDraftVersion\":0}")
                .andExpect(status().isNotFound()).andReturn().getResponse().getContentAsString();
        String unknownPlan = other.unsafe(post("/api/trips/{tripId}/options", UUID.randomUUID()), "{\"name\":\"Saved option\",\"expectedVersion\":0,\"expectedDraftVersion\":0}")
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
            owner.unsafe(post("/api/trips/{tripId}/options", tripId), "{\"name\":\"Saved option\",\"expectedVersion\":0,\"expectedDraftVersion\":0}")
                    .andExpect(status().isInternalServerError()).andExpect(jsonPath("$.code").value("INTERNAL_ERROR"));
        } finally {
            jdbc.execute("DROP TRIGGER fail_planned_airfare");
        }

        // Verify version is still 0 and no planned rows exist
        owner.unsafe(get("/api/trips/{tripId}", tripId), null).andExpect(status().isOk())
                .andExpect(jsonPath("$.version").value(0)).andExpect(jsonPath("$.planned.length()").value(0));
        assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM detour_planned_itinerary WHERE trip_id = (SELECT id FROM detour_trip WHERE public_id = ?)", Integer.class, UUID.fromString(tripId)));

        // Retry promotion succeeds
        owner.unsafe(post("/api/trips/{tripId}/options", tripId), "{\"name\":\"Saved option\",\"expectedVersion\":0,\"expectedDraftVersion\":0}")
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
                return first.unsafe(post("/api/trips/{tripId}/options", tripId), "{\"name\":\"Saved option\",\"expectedVersion\":0,\"expectedDraftVersion\":0}").andReturn().getResponse().getStatus();
            });
            Future<Integer> f2 = workers.submit(() -> {
                planBarrier.await();
                return second.unsafe(post("/api/trips/{tripId}/options", tripId), "{\"name\":\"Saved option\",\"expectedVersion\":0,\"expectedDraftVersion\":0}").andReturn().getResponse().getStatus();
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
            assertEquals(0, java.util.List.of(s1, s2).stream().filter(s -> s == 201).count());
            assertEquals(2, java.util.List.of(s1, s2).stream().filter(s -> s == 409).count());
        }

        first.unsafe(get("/api/trips/{tripId}", tripId), null).andExpect(status().isOk())
                .andExpect(jsonPath("$.version").value(1))
                .andExpect(jsonPath("$.drafts.length()").value(1))
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
        MvcResult promoted = owner.unsafe(post("/api/trips/{tripId}/options", tripId), "{\"name\":\"Saved option\",\"expectedVersion\":0,\"expectedDraftVersion\":0}")
                .andExpect(status().isCreated()).andReturn();
        String plannedId = tools.jackson.databind.json.JsonMapper.builder().build().readTree(promoted.getResponse().getContentAsString()).get("planned").get(0).get("id").asString();

        assertEquals(initialSeats, jdbc.queryForObject("SELECT available_seats FROM flight_instance WHERE id = (SELECT outbound_flight_instance_id FROM detour_trip_draft_airfare_selection WHERE draft_id = (SELECT id FROM detour_trip_draft WHERE public_id = ?))", Integer.class, UUID.fromString(draftId)));
        assertEquals(initialStayInv, jdbc.queryForObject("SELECT available_inventory FROM accommodation_nightly_inventory WHERE accommodation_unit_id = (SELECT accommodation_unit_id FROM detour_trip_draft_stay_selection WHERE draft_id = (SELECT id FROM detour_trip_draft WHERE public_id = ?)) AND night_date = DATE '2027-03-10'", Integer.class, UUID.fromString(draftId)));
        assertEquals(initialOccupancyCount, count("rental_unit_occupancy"));

        // 2. Duplicate Planned is rejected because the Working plan is unique.
        owner.unsafe(post("/api/trips/{tripId}/alternatives/{alternativeId}/duplicate", tripId, plannedId), "{\"expectedVersion\":1}")
                .andExpect(status().isConflict());

        assertEquals(initialSeats, jdbc.queryForObject("SELECT available_seats FROM flight_instance WHERE id = (SELECT outbound_flight_instance_id FROM detour_trip_draft_airfare_selection WHERE draft_id = (SELECT id FROM detour_trip_draft WHERE public_id = ?))", Integer.class, UUID.fromString(draftId)));
        assertEquals(initialStayInv, jdbc.queryForObject("SELECT available_inventory FROM accommodation_nightly_inventory WHERE accommodation_unit_id = (SELECT accommodation_unit_id FROM detour_trip_draft_stay_selection WHERE draft_id = (SELECT id FROM detour_trip_draft WHERE public_id = ?)) AND night_date = DATE '2027-03-10'", Integer.class, UUID.fromString(draftId)));
        assertEquals(initialOccupancyCount, count("rental_unit_occupancy"));

        // 3. Delete Planned
        owner.unsafe(delete("/api/trips/{tripId}/alternatives/{alternativeId}", tripId, plannedId), "{\"expectedVersion\":1,\"confirmed\":true}")
                .andExpect(status().isOk());

        assertEquals(initialSeats, jdbc.queryForObject("SELECT available_seats FROM flight_instance WHERE id = (SELECT outbound_flight_instance_id FROM detour_trip_draft_airfare_selection WHERE draft_id = (SELECT id FROM detour_trip_draft WHERE public_id = ?))", Integer.class, UUID.fromString(draftId)));
        assertEquals(initialStayInv, jdbc.queryForObject("SELECT available_inventory FROM accommodation_nightly_inventory WHERE accommodation_unit_id = (SELECT accommodation_unit_id FROM detour_trip_draft_stay_selection WHERE draft_id = (SELECT id FROM detour_trip_draft WHERE public_id = ?)) AND night_date = DATE '2027-03-10'", Integer.class, UUID.fromString(draftId)));
        assertEquals(initialOccupancyCount, count("rental_unit_occupancy"));
    }

    @Test
    void rejectsInPlaceTravelDetailEditsWhenPlannedAlternativesExist() throws Exception {
        Client owner = register("immutable-planned-test@example.test");
        MvcResult created = owner.unsafe(post("/api/trips"),
                validRequest("\"travelerAges\":[25,30],\"budgetCents\":50000"))
                .andExpect(status().isCreated()).andReturn();
        String tripId = jsonField(created, "id");
        String draftId = tools.jackson.databind.json.JsonMapper.builder().build()
                .readTree(created.getResponse().getContentAsString()).get("drafts").get(0).get("id").asString();

        insertSfoAirfareSelection(draftId);

        owner.unsafe(post("/api/trips/{tripId}/options", tripId),
                "{\"name\":\"Saved option\",\"expectedVersion\":0,\"expectedDraftVersion\":0}")
                .andExpect(status().isCreated());

        owner.unsafe(put("/api/trips/{tripId}", tripId),
                "{\"expectedVersion\":1,\"destinationKey\":\"destination-muc\",\"startDate\":\"2027-03-10\",\"endDate\":\"2027-03-14\",\"travelerCount\":2,\"travelerAges\":[25,30],\"budgetCents\":50000}")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("IMMUTABLE_TRIP_PARTY"));
    }

    @Test
    void budgetOnlyUpdatePreservesPlannedSnapshotsAndEnforcesConcurrency() throws Exception {
        Client owner = register("budget-concurrency-test@example.test");
        MvcResult created = owner.unsafe(post("/api/trips"),
                validRequest("\"travelerAges\":[25,30],\"budgetCents\":50000"))
                .andExpect(status().isCreated()).andReturn();
        String tripId = jsonField(created, "id");
        String draftId = tools.jackson.databind.json.JsonMapper.builder().build()
                .readTree(created.getResponse().getContentAsString()).get("drafts").get(0).get("id").asString();

        insertSfoAirfareSelection(draftId);
        insertSfoStaySelection(draftId);
        insertSfoRentalSelection(draftId);

        owner.unsafe(post("/api/trips/{tripId}/options", tripId),
                "{\"name\":\"Saved option\",\"expectedVersion\":0,\"expectedDraftVersion\":0}")
                .andExpect(status().isCreated());

        // Stale expectedVersion returns 409 VERSION_CONFLICT
        owner.unsafe(put("/api/trips/{tripId}", tripId),
                "{\"expectedVersion\":0,\"destinationKey\":\"destination-sfo\",\"startDate\":\"2027-03-10\",\"endDate\":\"2027-03-14\",\"travelerCount\":2,\"travelerAges\":[25,30],\"budgetCents\":75000}")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("VERSION_CONFLICT"))
                .andExpect(jsonPath("$.fields.currentVersion").value(1));

        // In-place budget update succeeds
        owner.unsafe(put("/api/trips/{tripId}", tripId),
                "{\"expectedVersion\":1,\"destinationKey\":\"destination-sfo\",\"startDate\":\"2027-03-10\",\"endDate\":\"2027-03-14\",\"travelerCount\":2,\"travelerAges\":[25,30],\"budgetCents\":75000}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.version").value(2))
                .andExpect(jsonPath("$.budgetCents").value(75000))
                .andExpect(jsonPath("$.planned.length()").value(1))
                .andExpect(jsonPath("$.planned[0].selections.airfare").exists())
                .andExpect(jsonPath("$.planned[0].selections.stay").exists())
                .andExpect(jsonPath("$.planned[0].selections.rental").exists());

        // Budget update with 0 cents
        owner.unsafe(put("/api/trips/{tripId}", tripId),
                "{\"expectedVersion\":2,\"destinationKey\":\"destination-sfo\",\"startDate\":\"2027-03-10\",\"endDate\":\"2027-03-14\",\"travelerCount\":2,\"travelerAges\":[25,30],\"budgetCents\":0}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.version").value(3))
                .andExpect(jsonPath("$.budgetCents").value(0));

        // Budget update with null budget
        owner.unsafe(put("/api/trips/{tripId}", tripId),
                "{\"expectedVersion\":3,\"destinationKey\":\"destination-sfo\",\"startDate\":\"2027-03-10\",\"endDate\":\"2027-03-14\",\"travelerCount\":2,\"travelerAges\":[25,30],\"budgetCents\":null}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.version").value(4))
                .andExpect(jsonPath("$.budgetCents").doesNotExist());
    }

    @Test
    void inPlaceTravelerEditRevalidatesCapacityRoomCountAndEligibility() throws Exception {
        Client owner = register("traveler-edit-revalidate@example.test");
        MvcResult created = owner.unsafe(post("/api/trips"),
                validRequest("\"travelerAges\":[25,30],\"budgetCents\":50000"))
                .andExpect(status().isCreated()).andReturn();
        String tripId = jsonField(created, "id");
        String draftId = tools.jackson.databind.json.JsonMapper.builder().build()
                .readTree(created.getResponse().getContentAsString()).get("drafts").get(0).get("id").asString();

        // Harbor hotel has room capacity = 2, inventory capacity = 6
        insertSfoHarborStaySelection(draftId);
        insertSfoRentalSelection(draftId);

        // Update travelerCount to 3, travelerAges to [12, 14, 16] (no adult >= 25)
        owner.unsafe(put("/api/trips/{tripId}", tripId),
                "{\"expectedVersion\":0,\"destinationKey\":\"destination-sfo\",\"startDate\":\"2027-03-10\",\"endDate\":\"2027-03-14\",\"travelerCount\":3,\"travelerAges\":[12,14,16],\"budgetCents\":50000}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.version").value(1))
                .andExpect(jsonPath("$.travelerCount").value(3))
                .andExpect(jsonPath("$.revisionSummary.removals.length()").value(1))
                .andExpect(jsonPath("$.revisionSummary.removals[0].component").value("rental"))
                .andExpect(jsonPath("$.revisionSummary.removals[0].reason").value("Rental cars require at least one driver aged 25 or older."))
                .andExpect(jsonPath("$.revisionSummary.adjustments.length()").value(1))
                .andExpect(jsonPath("$.revisionSummary.adjustments[0].component").value("stay"))
                .andExpect(jsonPath("$.revisionSummary.adjustments[0].previousUnitCount").value(1))
                .andExpect(jsonPath("$.revisionSummary.adjustments[0].newUnitCount").value(2))
                .andExpect(jsonPath("$.drafts[0].selections.stay.unitCount").value(2))
                .andExpect(jsonPath("$.drafts[0].selections.rental").doesNotExist());
    }

    @Test
    void inPlaceTravelerIncreaseExceedingFlightCapacityRemovesAirfare() throws Exception {
        Client owner = register("flight-capacity-test@example.test");
        MvcResult created = owner.unsafe(post("/api/trips"),
                validRequest("\"travelerAges\":[25,30],\"budgetCents\":50000"))
                .andExpect(status().isCreated()).andReturn();
        String tripId = jsonField(created, "id");
        String draftId = tools.jackson.databind.json.JsonMapper.builder().build()
                .readTree(created.getResponse().getContentAsString()).get("drafts").get(0).get("id").asString();

        insertSfoAirfareSelection(draftId);

        // Reduce available seats on outbound flight to 2
        jdbc.update("UPDATE flight_instance SET available_seats = 2 WHERE id = (SELECT outbound_flight_instance_id FROM detour_trip_draft_airfare_selection WHERE draft_id = (SELECT id FROM detour_trip_draft WHERE public_id = ?))", UUID.fromString(draftId));

        // Update traveler count to 3
        owner.unsafe(put("/api/trips/{tripId}", tripId),
                "{\"expectedVersion\":0,\"destinationKey\":\"destination-sfo\",\"startDate\":\"2027-03-10\",\"endDate\":\"2027-03-14\",\"travelerCount\":3,\"travelerAges\":[25,30,35],\"budgetCents\":50000}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.travelerCount").value(3))
                .andExpect(jsonPath("$.revisionSummary.removals.length()").value(1))
                .andExpect(jsonPath("$.revisionSummary.removals[0].component").value("airfare"))
                .andExpect(jsonPath("$.revisionSummary.removals[0].reason").value("Flight seat capacity is insufficient for 3 travelers."))
                .andExpect(jsonPath("$.drafts[0].selections.airfare").doesNotExist());

        jdbc.update("UPDATE flight_instance SET available_seats = 48 WHERE service_date = DATE '2027-03-10' AND flight_schedule_id = (SELECT id FROM flight_schedule WHERE catalog_key = 'airfare-out-sfo-d1')");
    }

    @Test
    void inPlaceDestinationAndDateEditRemovesIncompatibleComponents() throws Exception {
        Client owner = register("dest-date-edit-revalidate@example.test");
        MvcResult created = owner.unsafe(post("/api/trips"),
                validRequest("\"travelerAges\":[25,30],\"budgetCents\":50000"))
                .andExpect(status().isCreated()).andReturn();
        String tripId = jsonField(created, "id");
        String draftId = tools.jackson.databind.json.JsonMapper.builder().build()
                .readTree(created.getResponse().getContentAsString()).get("drafts").get(0).get("id").asString();

        insertSfoAirfareSelection(draftId);
        insertSfoStaySelection(draftId);
        insertSfoRentalSelection(draftId);

        // Change destination to Munich (destination-muc)
        owner.unsafe(put("/api/trips/{tripId}", tripId),
                "{\"expectedVersion\":0,\"destinationKey\":\"destination-muc\",\"startDate\":\"2027-03-10\",\"endDate\":\"2027-03-14\",\"travelerCount\":2,\"travelerAges\":[25,30],\"budgetCents\":50000}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.destinationKey").value("destination-muc"))
                .andExpect(jsonPath("$.revisionSummary.removals.length()").value(3))
                .andExpect(jsonPath("$.drafts[0].selections.airfare").doesNotExist())
                .andExpect(jsonPath("$.drafts[0].selections.stay").doesNotExist())
                .andExpect(jsonPath("$.drafts[0].selections.rental").doesNotExist());

        // Verify DB selection tables have no rows for this draft
        assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM detour_trip_draft_airfare_selection WHERE draft_id = (SELECT id FROM detour_trip_draft WHERE public_id = ?)", Integer.class, UUID.fromString(draftId)));
        assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM detour_trip_draft_stay_selection WHERE draft_id = (SELECT id FROM detour_trip_draft WHERE public_id = ?)", Integer.class, UUID.fromString(draftId)));
        assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM detour_trip_draft_rental_selection WHERE draft_id = (SELECT id FROM detour_trip_draft WHERE public_id = ?)", Integer.class, UUID.fromString(draftId)));
    }

    @Test
    void selectivelyDuplicatesActiveTripFromPlannedSourcesIntoNewDrafts() throws Exception {
        Client owner = register("selective-dup-success@example.test");
        MvcResult created = owner.unsafe(post("/api/trips"),
                validRequest("\"travelerAges\":[25,30],\"budgetCents\":50000"))
                .andExpect(status().isCreated()).andReturn();
        String tripId = jsonField(created, "id");
        String draft1Id = tools.jackson.databind.json.JsonMapper.builder().build()
                .readTree(created.getResponse().getContentAsString()).get("drafts").get(0).get("id").asString();

        insertSfoAirfareSelection(draft1Id);
        insertSfoStaySelection(draft1Id);

        // Save the first named option.
        owner.unsafe(post("/api/trips/{tripId}/options", tripId),
                "{\"name\":\"Flights and stay\",\"expectedVersion\":0,\"expectedDraftVersion\":0}")
                .andExpect(status().isCreated());

        // Revise the same Working plan and save it again.
        jdbc.update("DELETE FROM detour_trip_draft_airfare_selection WHERE draft_id = (SELECT id FROM detour_trip_draft WHERE public_id = ?)", UUID.fromString(draft1Id));
        insertSfoRentalSelection(draft1Id);

        MvcResult plan2Result = owner.unsafe(post("/api/trips/{tripId}/options", tripId),
                "{\"name\":\"Stay and car\",\"expectedVersion\":1,\"expectedDraftVersion\":0}")
                .andExpect(status().isCreated()).andReturn();

        var plannedList = tools.jackson.databind.json.JsonMapper.builder().build()
                .readTree(plan2Result.getResponse().getContentAsString()).get("planned");
        String planned1Id = plannedList.get(0).get("id").asString();
        String planned2Id = plannedList.get(1).get("id").asString();

        // Duplicate active trip from both planned snapshots
        MvcResult duplicated = owner.unsafe(post("/api/trips/{tripId}/duplicate", tripId),
                "{\"expectedVersion\":2,\"destinationKey\":\"destination-sfo\",\"startDate\":\"2027-03-10\",\"endDate\":\"2027-03-14\",\"travelerCount\":2,\"travelerAges\":[25,30],\"budgetCents\":80000,\"sourcePlannedItineraryIds\":[\"" + planned1Id + "\",\"" + planned2Id + "\"]}")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.version").value(0))
                .andExpect(jsonPath("$.name").value("San Francisco - 2027-03-10 to 2027-03-14"))
                .andExpect(jsonPath("$.budgetCents").value(80000))
                .andExpect(jsonPath("$.drafts.length()").value(1))
                .andExpect(jsonPath("$.planned.length()").value(2))
                .andExpect(jsonPath("$.savedOptions[0].name").value("Flights and stay"))
                .andExpect(jsonPath("$.savedOptions[1].name").value("Stay and car"))
                .andExpect(jsonPath("$.alternatives.length()").value(3))
                .andReturn();

        owner.unsafe(post("/api/trips/{tripId}/duplicate", tripId),
                "{\"expectedVersion\":2,\"destinationKey\":\"destination-sfo\",\"startDate\":\"2027-03-10\",\"endDate\":\"2027-03-14\",\"travelerCount\":2,\"travelerAges\":[25,30],\"budgetCents\":80000,\"sourcePlannedItineraryIds\":[\"" + planned1Id + "\",\"" + planned2Id + "\"]}")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.name").value("San Francisco - 2027-03-10 to 2027-03-14 - A"));

        String newTripId = jsonField(duplicated, "id");
        org.junit.jupiter.api.Assertions.assertNotEquals(tripId, newTripId);

        // Verify source Trip is completely unchanged
        owner.unsafe(get("/api/trips/{tripId}", tripId), null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.version").value(2))
                .andExpect(jsonPath("$.planned.length()").value(2))
                .andExpect(jsonPath("$.budgetCents").value(50000));
    }

    @Test
    void selectiveDuplicationPrunesIncompatibleComponentsWithStructuredSummary() throws Exception {
        Client owner = register("selective-dup-prune@example.test");
        MvcResult created = owner.unsafe(post("/api/trips"),
                validRequest("\"travelerAges\":[25,30],\"budgetCents\":50000"))
                .andExpect(status().isCreated()).andReturn();
        String tripId = jsonField(created, "id");
        String draftId = tools.jackson.databind.json.JsonMapper.builder().build()
                .readTree(created.getResponse().getContentAsString()).get("drafts").get(0).get("id").asString();

        insertSfoAirfareSelection(draftId);
        insertSfoStaySelection(draftId);
        insertSfoRentalSelection(draftId);

        MvcResult plannedResult = owner.unsafe(post("/api/trips/{tripId}/options", tripId),
                "{\"name\":\"Saved option\",\"expectedVersion\":0,\"expectedDraftVersion\":0}")
                .andExpect(status().isCreated()).andReturn();
        String plannedId = tools.jackson.databind.json.JsonMapper.builder().build()
                .readTree(plannedResult.getResponse().getContentAsString()).get("planned").get(0).get("id").asString();

        // Duplicate with destinationKey: "destination-muc"
        owner.unsafe(post("/api/trips/{tripId}/duplicate", tripId),
                "{\"expectedVersion\":1,\"destinationKey\":\"destination-muc\",\"startDate\":\"2027-03-10\",\"endDate\":\"2027-03-14\",\"travelerCount\":2,\"travelerAges\":[25,30],\"budgetCents\":50000,\"sourcePlannedItineraryIds\":[\"" + plannedId + "\"]}")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.destinationKey").value("destination-muc"))
                .andExpect(jsonPath("$.drafts.length()").value(1))
                .andExpect(jsonPath("$.revisionSummary.removals.length()").value(3))
                .andExpect(jsonPath("$.drafts[0].selections.airfare").doesNotExist())
                .andExpect(jsonPath("$.drafts[0].selections.stay").doesNotExist())
                .andExpect(jsonPath("$.drafts[0].selections.rental").doesNotExist());
    }

    @Test
    void selectiveDuplicationRejectsInvalidOrUnauthorizedSourcesWithoutDisclosure() throws Exception {
        Client userA = register("user-a-dup@example.test");
        Client userB = register("user-b-dup@example.test");

        // User A creates Trip 1 with Planned itinerary
        MvcResult tripA1 = userA.unsafe(post("/api/trips"),
                validRequest("\"travelerAges\":[25,30],\"budgetCents\":50000"))
                .andExpect(status().isCreated()).andReturn();
        String tripA1Id = jsonField(tripA1, "id");
        String draftA1Id = tools.jackson.databind.json.JsonMapper.builder().build()
                .readTree(tripA1.getResponse().getContentAsString()).get("drafts").get(0).get("id").asString();
        insertSfoAirfareSelection(draftA1Id);
        MvcResult planA1Result = userA.unsafe(post("/api/trips/{tripId}/options", tripA1Id),
                "{\"name\":\"Saved option\",\"expectedVersion\":0,\"expectedDraftVersion\":0}").andExpect(status().isCreated()).andReturn();
        String plannedA1Id = tools.jackson.databind.json.JsonMapper.builder().build()
                .readTree(planA1Result.getResponse().getContentAsString()).get("planned").get(0).get("id").asString();

        // User A creates Trip 2 with Planned itinerary
        MvcResult tripA2 = userA.unsafe(post("/api/trips"),
                validRequest("\"name\":\"Second spring trip\",\"travelerAges\":[25,30],\"budgetCents\":50000"))
                .andExpect(status().isCreated()).andReturn();
        String tripA2Id = jsonField(tripA2, "id");
        String draftA2Id = tools.jackson.databind.json.JsonMapper.builder().build()
                .readTree(tripA2.getResponse().getContentAsString()).get("drafts").get(0).get("id").asString();
        insertSfoAirfareSelection(draftA2Id);
        MvcResult planA2Result = userA.unsafe(post("/api/trips/{tripId}/options", tripA2Id),
                "{\"name\":\"Saved option\",\"expectedVersion\":0,\"expectedDraftVersion\":0}").andExpect(status().isCreated()).andReturn();
        String plannedA2Id = tools.jackson.databind.json.JsonMapper.builder().build()
                .readTree(planA2Result.getResponse().getContentAsString()).get("planned").get(0).get("id").asString();

        int initialTripCount = count("detour_trip");

        // 1. Empty source list -> 400 VALIDATION_FAILED
        userA.unsafe(post("/api/trips/{tripId}/duplicate", tripA1Id),
                "{\"expectedVersion\":1,\"destinationKey\":\"destination-sfo\",\"startDate\":\"2027-03-10\",\"endDate\":\"2027-03-14\",\"travelerCount\":2,\"travelerAges\":[25,30],\"sourcePlannedItineraryIds\":[]}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.fields.sourcePlannedItineraryIds").value("Select at least one Planned alternative to copy."));

        // 2. Duplicate source IDs -> 400 VALIDATION_FAILED
        userA.unsafe(post("/api/trips/{tripId}/duplicate", tripA1Id),
                "{\"expectedVersion\":1,\"destinationKey\":\"destination-sfo\",\"startDate\":\"2027-03-10\",\"endDate\":\"2027-03-14\",\"travelerCount\":2,\"travelerAges\":[25,30],\"sourcePlannedItineraryIds\":[\"" + plannedA1Id + "\",\"" + plannedA1Id + "\"]}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.fields.sourcePlannedItineraryIds").value("Duplicate source alternatives are not allowed."));

        // 3. Draft ID passed instead of Planned ID -> 404 RESOURCE_NOT_FOUND
        String newDraftId = draftA1Id;
        userA.unsafe(post("/api/trips/{tripId}/duplicate", tripA1Id),
                "{\"expectedVersion\":1,\"destinationKey\":\"destination-sfo\",\"startDate\":\"2027-03-10\",\"endDate\":\"2027-03-14\",\"travelerCount\":2,\"travelerAges\":[25,30],\"sourcePlannedItineraryIds\":[\"" + newDraftId + "\"]}")
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("RESOURCE_NOT_FOUND"));

        // 4. Cross-trip Planned ID (from Trip A2 passed to Trip A1) -> 404 RESOURCE_NOT_FOUND
        userA.unsafe(post("/api/trips/{tripId}/duplicate", tripA1Id),
                "{\"expectedVersion\":1,\"destinationKey\":\"destination-sfo\",\"startDate\":\"2027-03-10\",\"endDate\":\"2027-03-14\",\"travelerCount\":2,\"travelerAges\":[25,30],\"sourcePlannedItineraryIds\":[\"" + plannedA2Id + "\"]}")
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("RESOURCE_NOT_FOUND"));

        // 5. Foreign user's Planned ID (User B attempts to duplicate User A's planned ID) -> 404
        MvcResult tripB = userB.unsafe(post("/api/trips"),
                validRequest("\"travelerAges\":[25,30],\"budgetCents\":50000"))
                .andExpect(status().isCreated()).andReturn();
        String tripBId = jsonField(tripB, "id");
        userB.unsafe(post("/api/trips/{tripId}/duplicate", tripBId),
                "{\"expectedVersion\":0,\"destinationKey\":\"destination-sfo\",\"startDate\":\"2027-03-10\",\"endDate\":\"2027-03-14\",\"travelerCount\":2,\"travelerAges\":[25,30],\"sourcePlannedItineraryIds\":[\"" + plannedA1Id + "\"]}")
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("RESOURCE_NOT_FOUND"));

        // 6. Random non-existent UUID -> identical 404 response structure (nondisclosure)
        userB.unsafe(post("/api/trips/{tripId}/duplicate", tripBId),
                "{\"expectedVersion\":0,\"destinationKey\":\"destination-sfo\",\"startDate\":\"2027-03-10\",\"endDate\":\"2027-03-14\",\"travelerCount\":2,\"travelerAges\":[25,30],\"sourcePlannedItineraryIds\":[\"" + UUID.randomUUID() + "\"]}")
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("RESOURCE_NOT_FOUND"));

        // Assert atomicity: no partial trip created in DB from any failed attempt
        assertEquals(initialTripCount + 1, count("detour_trip"));
    }

    @Test
    void selectiveDuplicationEnforcesOptimisticConcurrency() throws Exception {
        Client owner = register("concurrency-dup@example.test");
        MvcResult created = owner.unsafe(post("/api/trips"),
                validRequest("\"travelerAges\":[25,30],\"budgetCents\":50000"))
                .andExpect(status().isCreated()).andReturn();
        String tripId = jsonField(created, "id");
        String draftId = tools.jackson.databind.json.JsonMapper.builder().build()
                .readTree(created.getResponse().getContentAsString()).get("drafts").get(0).get("id").asString();

        insertSfoAirfareSelection(draftId);
        MvcResult planResult = owner.unsafe(post("/api/trips/{tripId}/options", tripId),
                "{\"name\":\"Saved option\",\"expectedVersion\":0,\"expectedDraftVersion\":0}").andExpect(status().isCreated()).andReturn();
        String plannedId = tools.jackson.databind.json.JsonMapper.builder().build()
                .readTree(planResult.getResponse().getContentAsString()).get("planned").get(0).get("id").asString();

        int tripCountBefore = count("detour_trip");

        // Stale expectedVersion: 0 (current version is 1)
        owner.unsafe(post("/api/trips/{tripId}/duplicate", tripId),
                "{\"expectedVersion\":0,\"destinationKey\":\"destination-sfo\",\"startDate\":\"2027-03-10\",\"endDate\":\"2027-03-14\",\"travelerCount\":2,\"travelerAges\":[25,30],\"sourcePlannedItineraryIds\":[\"" + plannedId + "\"]}")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("VERSION_CONFLICT"))
                .andExpect(jsonPath("$.fields.currentVersion").value(1));

        // No new trip created
        assertEquals(tripCountBefore, count("detour_trip"));
    }

    private void insertSfoHarborStaySelection(String draftId) {
        jdbc.update("""
                INSERT INTO detour_trip_draft_stay_selection (draft_id, accommodation_unit_id, unit_count)
                VALUES ((SELECT id FROM detour_trip_draft WHERE public_id = ?),
                    (SELECT id FROM accommodation_unit WHERE catalog_key = 'stay-unit-sfo-hotel-harbor'), 1)
                """, UUID.fromString(draftId));
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
        return "{\"name\":\"Spring trip\",\"destinationKey\":\"destination-sfo\",\"startDate\":\"2027-03-10\",\"endDate\":\"2027-03-14\",\"travelerCount\":2,\"travelerAges\":[25,25]" + suffix + "}";
    }

    @Test
    void profileProjectionReturnsOwnerTripsPartitionedByDateWithAccurateCounts() throws Exception {
        Client owner = register("profile-projection@example.test");
        owner.unsafe(post("/api/trips"), validRequest("\"travelerAges\":[25,30],\"budgetCents\":50000"))
                .andExpect(status().isCreated());

        mockMvc.perform(get("/api/trips").session(owner.session).cookie(owner.csrf))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.upcoming.length()").value(1))
                .andExpect(jsonPath("$.upcoming[0].draftCount").value(1))
                .andExpect(jsonPath("$.upcoming[0].plannedCount").value(0))
                .andExpect(jsonPath("$.upcoming[0].expiredAlternativeCount").value(0))
                .andExpect(jsonPath("$.past.length()").value(0));

        mockMvc.perform(get("/api/profile").session(owner.session).cookie(owner.csrf))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value("profile-projection@example.test"))
                .andExpect(jsonPath("$.upcoming.length()").value(1))
                .andExpect(jsonPath("$.past.length()").value(0));
    }

    @Test
    void profileProjectionPartitionsUpcomingAndPastWithDeterministicSort() throws Exception {
        testClock.setInstant(Instant.parse("2027-03-08T20:00:00Z")); // 12:00:00-08:00
        Client owner = register("profile-sorting@example.test");

        // Trip 1: March 10–14, 2027 (Upcoming)
        MvcResult trip1Res = owner.unsafe(post("/api/trips"), "{\"name\":\"Test trip\",\"destinationKey\":\"destination-sfo\",\"startDate\":\"2027-03-10\",\"endDate\":\"2027-03-14\",\"travelerCount\":2,\"travelerAges\":[25,30],\"budgetCents\":50000}").andExpect(status().isCreated()).andReturn();
        String trip1Id = jsonField(trip1Res, "id");

        // Trip 2: March 10–16, 2027 (Upcoming, same start date as Trip 1)
        MvcResult trip2Res = owner.unsafe(post("/api/trips"), "{\"name\":\"Test trip B\",\"destinationKey\":\"destination-sfo\",\"startDate\":\"2027-03-10\",\"endDate\":\"2027-03-16\",\"travelerCount\":2,\"travelerAges\":[25,30],\"budgetCents\":50000}").andExpect(status().isCreated()).andReturn();
        String trip2Id = jsonField(trip2Res, "id");

        // Trip 3: March 2–5, 2027 (Past, end date March 5 has passed)
        MvcResult trip3Res = owner.unsafe(post("/api/trips"), "{\"name\":\"Test trip C\",\"destinationKey\":\"destination-sfo\",\"startDate\":\"2027-03-02\",\"endDate\":\"2027-03-05\",\"travelerCount\":2,\"travelerAges\":[25,30],\"budgetCents\":50000}").andExpect(status().isCreated()).andReturn();
        String trip3Id = jsonField(trip3Res, "id");

        // Query profile
        mockMvc.perform(get("/api/trips").session(owner.session).cookie(owner.csrf))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.upcoming.length()").value(2))
                .andExpect(jsonPath("$.upcoming[0].id").value(trip1Id))
                .andExpect(jsonPath("$.upcoming[0].temporalStatus").value("UPCOMING"))
                .andExpect(jsonPath("$.upcoming[1].id").value(trip2Id))
                .andExpect(jsonPath("$.upcoming[1].temporalStatus").value("UPCOMING"))
                .andExpect(jsonPath("$.past.length()").value(1))
                .andExpect(jsonPath("$.past[0].id").value(trip3Id))
                .andExpect(jsonPath("$.past[0].temporalStatus").value("PAST"));

        // Boundary edge case: Exactly on the end date (2027-03-05T23:59:59.999-08:00): Trip 3 is still Upcoming
        testClock.setInstant(ZonedDateTime.of(2027, 3, 5, 23, 59, 59, 999_000_000, ClockConfiguration.PDX_ZONE).toInstant());
        mockMvc.perform(get("/api/trips").session(owner.session).cookie(owner.csrf))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.upcoming.length()").value(3))
                .andExpect(jsonPath("$.past.length()").value(0));

        // Immediately after the end date (2027-03-06T00:00:00.000-08:00): Trip 3 transitions to Past
        testClock.setInstant(ZonedDateTime.of(2027, 3, 6, 0, 0, 0, 0, ClockConfiguration.PDX_ZONE).toInstant());
        mockMvc.perform(get("/api/trips").session(owner.session).cookie(owner.csrf))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.upcoming.length()").value(2))
                .andExpect(jsonPath("$.past.length()").value(1))
                .andExpect(jsonPath("$.past[0].id").value(trip3Id));
    }

    @Test
    void clockControlsExpirationAtDepartureMidnightAndBlocksPromotion() throws Exception {
        Client owner = register("expiration-promotion@example.test");
        MvcResult tripRes = owner.unsafe(post("/api/trips"), "{\"name\":\"Test trip\",\"destinationKey\":\"destination-sfo\",\"startDate\":\"2027-03-10\",\"endDate\":\"2027-03-15\",\"travelerCount\":2,\"travelerAges\":[25,30],\"budgetCents\":50000}").andExpect(status().isCreated()).andReturn();
        String tripId = jsonField(tripRes, "id");
        var tripJson = tools.jackson.databind.json.JsonMapper.builder().build().readTree(tripRes.getResponse().getContentAsString());
        String draft1Id = tripJson.get("drafts").get(0).get("id").asString();

        jdbc.update("""
                INSERT INTO detour_trip_draft_airfare_selection (draft_id, outbound_flight_instance_id, return_flight_instance_id)
                VALUES ((SELECT id FROM detour_trip_draft WHERE public_id = ?),
                    (SELECT instance.id FROM flight_instance instance JOIN flight_schedule schedule ON schedule.id = instance.flight_schedule_id WHERE schedule.catalog_key = 'airfare-out-sfo-d1' AND instance.service_date = DATE '2027-03-10'),
                    (SELECT instance.id FROM flight_instance instance JOIN flight_schedule schedule ON schedule.id = instance.flight_schedule_id WHERE schedule.catalog_key = 'airfare-in-sfo-d1' AND instance.service_date = DATE '2027-03-15'))
                """, UUID.fromString(draft1Id));
        jdbc.update("""
                INSERT INTO detour_trip_draft_stay_selection (draft_id, accommodation_unit_id, unit_count)
                VALUES ((SELECT id FROM detour_trip_draft WHERE public_id = ?),
                    (SELECT id FROM accommodation_unit WHERE catalog_key = 'stay-unit-sfo-hotel-summit'), 1)
                """, UUID.fromString(draft1Id));

        testClock.setInstant(ZonedDateTime.of(2027, 3, 9, 23, 59, 59, 999_000_000, ClockConfiguration.PDX_ZONE).toInstant());

        mockMvc.perform(get("/api/trips").session(owner.session).cookie(owner.csrf))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.upcoming[0].expiredAlternativeCount").value(0))
                .andExpect(jsonPath("$.upcoming[0].alternatives[0].expired").value(false))
                .andExpect(jsonPath("$.upcoming[0].alternatives[0].status").value("DRAFT"));

        owner.unsafe(post("/api/trips/" + tripId + "/options"), "{\"name\":\"Saved option\",\"expectedVersion\":0,\"expectedDraftVersion\":0}")
                .andExpect(status().isCreated());

        testClock.setInstant(ZonedDateTime.of(2027, 3, 10, 0, 0, 0, 0, ClockConfiguration.PDX_ZONE).toInstant());

        mockMvc.perform(get("/api/trips").session(owner.session).cookie(owner.csrf))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.upcoming[0].draftCount").value(1))
                .andExpect(jsonPath("$.upcoming[0].plannedCount").value(1))
                .andExpect(jsonPath("$.upcoming[0].expiredAlternativeCount").value(2))
                .andExpect(jsonPath("$.upcoming[0].alternatives[0].expired").value(true))
                .andExpect(jsonPath("$.upcoming[0].alternatives[0].status").value("EXPIRED"))
                .andExpect(jsonPath("$.upcoming[0].alternatives[1].expired").value(true))
                .andExpect(jsonPath("$.upcoming[0].alternatives[1].status").value("EXPIRED"));

        owner.unsafe(post("/api/trips/" + tripId + "/options"), "{\"name\":\"Saved option\",\"expectedVersion\":1,\"expectedDraftVersion\":0}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("ALTERNATIVE_EXPIRED"));

        // DST boundary test: departure date 2027-03-15 around March 14 DST change
        MvcResult dstTripRes = owner.unsafe(post("/api/trips"), "{\"name\":\"DST trip\",\"destinationKey\":\"destination-sfo\",\"startDate\":\"2027-03-15\",\"endDate\":\"2027-03-20\",\"travelerCount\":2,\"travelerAges\":[25,30],\"budgetCents\":50000}").andExpect(status().isCreated()).andReturn();
        String dstTripId = jsonField(dstTripRes, "id");
        testClock.setInstant(ZonedDateTime.of(2027, 3, 14, 23, 59, 59, 999_000_000, ClockConfiguration.PDX_ZONE).toInstant());
        mockMvc.perform(get("/api/trips").session(owner.session).cookie(owner.csrf))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.upcoming[?(@.id == '" + dstTripId + "')].expiredAlternativeCount").value(0));

        testClock.setInstant(ZonedDateTime.of(2027, 3, 15, 0, 0, 0, 0, ClockConfiguration.PDX_ZONE).toInstant());
        mockMvc.perform(get("/api/trips").session(owner.session).cookie(owner.csrf))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.upcoming[?(@.id == '" + dstTripId + "')].expiredAlternativeCount").value(1));
    }

    @Test
    void deletesNeverBookedTripAtomicallyWithCascadeAndPreservesCatalog() throws Exception {
        Client owner = register("trip-delete@example.test");
        MvcResult tripRes = owner.unsafe(post("/api/trips"), "{\"name\":\"Test trip\",\"destinationKey\":\"destination-sfo\",\"startDate\":\"2027-03-10\",\"endDate\":\"2027-03-15\",\"travelerCount\":2,\"travelerAges\":[25,30],\"budgetCents\":50000}").andExpect(status().isCreated()).andReturn();
        String tripId = jsonField(tripRes, "id");
        var tripJson = tools.jackson.databind.json.JsonMapper.builder().build().readTree(tripRes.getResponse().getContentAsString());
        String draft1Id = tripJson.get("drafts").get(0).get("id").asString();

        jdbc.update("""
                INSERT INTO detour_trip_draft_airfare_selection (draft_id, outbound_flight_instance_id, return_flight_instance_id)
                VALUES ((SELECT id FROM detour_trip_draft WHERE public_id = ?),
                    (SELECT instance.id FROM flight_instance instance JOIN flight_schedule schedule ON schedule.id = instance.flight_schedule_id WHERE schedule.catalog_key = 'airfare-out-sfo-d1' AND instance.service_date = DATE '2027-03-10'),
                    (SELECT instance.id FROM flight_instance instance JOIN flight_schedule schedule ON schedule.id = instance.flight_schedule_id WHERE schedule.catalog_key = 'airfare-in-sfo-d1' AND instance.service_date = DATE '2027-03-15'))
                """, UUID.fromString(draft1Id));
        jdbc.update("""
                INSERT INTO detour_trip_draft_stay_selection (draft_id, accommodation_unit_id, unit_count)
                VALUES ((SELECT id FROM detour_trip_draft WHERE public_id = ?),
                    (SELECT id FROM accommodation_unit WHERE catalog_key = 'stay-unit-sfo-hotel-summit'), 1)
                """, UUID.fromString(draft1Id));

        // Promote draft 1 to planned (Trip now has 1 draft, 1 planned, version 1)
        owner.unsafe(post("/api/trips/" + tripId + "/options"), "{\"name\":\"Saved option\",\"expectedVersion\":0,\"expectedDraftVersion\":0}")
                .andExpect(status().isCreated());

        // The Working plan remains the only Draft after promotion.

        int flightsBefore = count("flight_instance");
        int staysBefore = count("accommodation_nightly_inventory");
        int rentalsBefore = count("rental_unit_occupancy");

        owner.unsafe(delete("/api/trips/" + tripId), "{\"expectedVersion\":1,\"expectedDraftCount\":1,\"expectedPlannedCount\":1,\"confirmed\":true}")
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/trips/{tripId}", tripId).session(owner.session).cookie(owner.csrf))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("RESOURCE_NOT_FOUND"));

        assertEquals(0, (int) jdbc.queryForObject("SELECT COUNT(*) FROM detour_trip WHERE public_id = ?", Integer.class, UUID.fromString(tripId)));
        assertEquals(0, (int) jdbc.queryForObject("SELECT COUNT(*) FROM detour_trip_traveler WHERE trip_id NOT IN (SELECT id FROM detour_trip)", Integer.class));
        assertEquals(0, (int) jdbc.queryForObject("SELECT COUNT(*) FROM detour_trip_draft WHERE trip_id NOT IN (SELECT id FROM detour_trip)", Integer.class));
        assertEquals(0, (int) jdbc.queryForObject("SELECT COUNT(*) FROM detour_planned_itinerary WHERE trip_id NOT IN (SELECT id FROM detour_trip)", Integer.class));

        assertEquals(flightsBefore, count("flight_instance"));
        assertEquals(staysBefore, count("accommodation_nightly_inventory"));
        assertEquals(rentalsBefore, count("rental_unit_occupancy"));
    }

    @Test
    void tripDeletionRejectsStaleConfirmationAndVersionConflicts() throws Exception {
        Client owner = register("trip-delete-guards@example.test");
        MvcResult tripRes = owner.unsafe(post("/api/trips"), "{\"name\":\"Test trip\",\"destinationKey\":\"destination-sfo\",\"startDate\":\"2027-03-10\",\"endDate\":\"2027-03-15\",\"travelerCount\":2,\"travelerAges\":[25,30],\"budgetCents\":50000}").andExpect(status().isCreated()).andReturn();
        String tripId = jsonField(tripRes, "id");

        owner.unsafe(delete("/api/trips/" + tripId), "{\"expectedVersion\":0,\"expectedDraftCount\":2,\"expectedPlannedCount\":0,\"confirmed\":true}")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("STALE_CONFIRMATION"));

        owner.unsafe(delete("/api/trips/" + tripId), "{\"expectedVersion\":0,\"expectedDraftCount\":1,\"expectedPlannedCount\":1,\"confirmed\":true}")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("STALE_CONFIRMATION"));

        owner.unsafe(delete("/api/trips/" + tripId), "{\"expectedVersion\":99,\"expectedDraftCount\":1,\"expectedPlannedCount\":0,\"confirmed\":true}")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("VERSION_CONFLICT"));

        owner.unsafe(delete("/api/trips/" + tripId), "{\"expectedVersion\":0,\"expectedDraftCount\":1,\"expectedPlannedCount\":0,\"confirmed\":false}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));

        mockMvc.perform(get("/api/trips/{tripId}", tripId).session(owner.session).cookie(owner.csrf))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.version").value(0))
                .andExpect(jsonPath("$.drafts.length()").value(1));
    }

    @Test
    void tripOperationsEnforceOwnershipAndNondisclosure() throws Exception {
        Client ownerA = register("owner-a@example.test");
        Client ownerB = register("owner-b@example.test");

        MvcResult tripARes = ownerA.unsafe(post("/api/trips"), "{\"name\":\"Test trip\",\"destinationKey\":\"destination-sfo\",\"startDate\":\"2027-03-10\",\"endDate\":\"2027-03-15\",\"travelerCount\":2,\"travelerAges\":[25,30],\"budgetCents\":50000}").andExpect(status().isCreated()).andReturn();
        String tripAId = jsonField(tripARes, "id");

        mockMvc.perform(get("/api/trips").session(ownerB.session).cookie(ownerB.csrf))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.upcoming.length()").value(0))
                .andExpect(jsonPath("$.past.length()").value(0));

        ownerB.unsafe(delete("/api/trips/" + tripAId), "{\"expectedVersion\":0,\"expectedDraftCount\":1,\"expectedPlannedCount\":0,\"confirmed\":true}")
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("RESOURCE_NOT_FOUND"));

        mockMvc.perform(get("/api/trips/{tripId}", tripAId).session(ownerA.session).cookie(ownerA.csrf))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(tripAId));
    }

    @Test
    void tripDeletionBlocksWhenBookingHistoryPresent() throws Exception {
        Client owner = register("booked-guard@example.test");
        MvcResult tripRes = owner.unsafe(post("/api/trips"), "{\"name\":\"Test trip\",\"destinationKey\":\"destination-sfo\",\"startDate\":\"2027-03-10\",\"endDate\":\"2027-03-14\",\"travelerCount\":2,\"travelerAges\":[25,30],\"budgetCents\":500000}").andExpect(status().isCreated()).andReturn();
        String tripId = jsonField(tripRes, "id");
        String draftId = tools.jackson.databind.json.JsonMapper.builder().build().readTree(tripRes.getResponse().getContentAsString()).get("drafts").get(0).get("id").asString();

        insertSfoAirfareSelection(draftId);
        MvcResult planRes = owner.unsafe(post("/api/trips/{tripId}/options", tripId),
                "{\"name\":\"Saved option\",\"expectedVersion\":0,\"expectedDraftVersion\":0}")
                .andExpect(status().isCreated()).andReturn();
        String plannedId = tools.jackson.databind.json.JsonMapper.builder().build().readTree(planRes.getResponse().getContentAsString()).get("planned").get(0).get("id").asString();

        owner.unsafe(post("/api/trips/{tripId}/bookings", tripId),
                "{\"plannedItineraryId\":\"" + plannedId + "\",\"expectedVersion\":1,\"idempotencyKey\":\"guard-booking-key\"}")
                .andExpect(status().isCreated());

        owner.unsafe(delete("/api/trips/" + tripId), "{\"expectedVersion\":2,\"expectedDraftCount\":1,\"expectedPlannedCount\":1,\"confirmed\":true}")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CANNOT_DELETE_BOOKED_TRIP"));

        mockMvc.perform(get("/api/trips/{tripId}", tripId).session(owner.session).cookie(owner.csrf))
                .andExpect(status().isOk());

        jdbc.update("DELETE FROM detour_booking WHERE trip_id = (SELECT id FROM detour_trip WHERE public_id = ?)", UUID.fromString(tripId));
        jdbc.update("UPDATE flight_instance SET available_seats = 48 WHERE service_date = DATE '2027-03-10' AND flight_schedule_id = (SELECT id FROM flight_schedule WHERE catalog_key = 'airfare-out-sfo-d1')");
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
