package app.detour.trip;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import jakarta.servlet.http.Cookie;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.UUID;
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
