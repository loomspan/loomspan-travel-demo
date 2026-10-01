package app.detour.trip;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import jakarta.servlet.http.Cookie;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
@Import(TestClockConfiguration.class)
class IndependentTripPlanIntegrationTest {
    static final String DB = "jdbc:h2:mem:independent_" + UUID.randomUUID() + ";DB_CLOSE_DELAY=-1";
    @DynamicPropertySource static void db(DynamicPropertyRegistry r) { r.add("spring.datasource.url", () -> DB); }
    @Autowired MockMvc mvc;
    @Autowired org.springframework.jdbc.core.JdbcTemplate jdbc;
    @Autowired TestClockConfiguration.TestClock clock;
    @Autowired javax.sql.DataSource dataSource;
    @Autowired org.springframework.transaction.PlatformTransactionManager transactionManager;
    @org.springframework.test.context.bean.override.mockito.MockitoSpyBean TripRepository trips;
    static final tools.jackson.databind.json.JsonMapper JSON = tools.jackson.databind.json.JsonMapper.builder().build();
    static final String DETAILS = "\"startDate\":\"2027-03-10\",\"endDate\":\"2027-03-14\",\"travelerCount\":2,\"travelerAges\":[25,30]";
    @org.junit.jupiter.api.AfterEach void reset() { clock.reset(); }

    record Client(MockHttpSession session, Cookie csrf) { }
    Client client() throws Exception {
        Cookie csrf = mvc.perform(get("/")).andReturn().getResponse().getCookie("XSRF-TOKEN");
        var response = mvc.perform(post("/api/auth/register").cookie(csrf).header("X-XSRF-TOKEN", csrf.getValue())
            .contentType(MediaType.APPLICATION_JSON).content("{\"email\":\"" + UUID.randomUUID() + "@example.test\",\"password\":\"aaaaaaaaaaaa\"}"))
            .andExpect(status().isCreated()).andReturn();
        return new Client((MockHttpSession) response.getRequest().getSession(false), csrf);
    }
    org.springframework.test.web.servlet.ResultActions call(Client c, String method, String path, String body) throws Exception {
        var request = org.springframework.test.web.servlet.request.MockMvcRequestBuilders.request(org.springframework.http.HttpMethod.valueOf(method), path)
            .session(c.session()).cookie(c.csrf()).header("X-XSRF-TOKEN", c.csrf().getValue()).contentType(MediaType.APPLICATION_JSON);
        if (body != null) request.content(body);
        return mvc.perform(request);
    }
    tools.jackson.databind.JsonNode data(org.springframework.test.web.servlet.ResultActions response) throws Exception { return JSON.readTree(response.andReturn().getResponse().getContentAsString()); }
    tools.jackson.databind.JsonNode create(Client c) throws Exception { return data(call(c, "POST", "/api/trips", "{\"name\":\"" + UUID.randomUUID() + "\",\"destinationKey\":\"destination-sfo\"," + DETAILS + "}").andExpect(status().isCreated())); }
    String path(tools.jackson.databind.JsonNode trip) { return "/api/trips/" + trip.get("id").asString(); }
    String planPath(tools.jackson.databind.JsonNode trip, tools.jackson.databind.JsonNode plan) { return path(trip) + "/plans/" + plan.get("id").asString(); }
    String versions(tools.jackson.databind.JsonNode trip, tools.jackson.databind.JsonNode plan) { return "\"expectedVersion\":" + trip.get("version").asLong() + ",\"expectedPlanVersion\":" + plan.get("version").asLong(); }
    void invariant(String id) {
        org.junit.jupiter.api.Assertions.assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM detour_trip t JOIN detour_planned_itinerary p ON p.id = t.primary_plan_id AND p.trip_id = t.id WHERE t.public_id = ?", Integer.class, UUID.fromString(id)));
    }

    @Test void profileOrdersByCurrentPrimaryDatesAfterSaveAndPromotion() throws Exception {
        Client c = client(); var first = create(c); var second = create(c);
        var primary = first.get("plans").get(0);
        first = data(call(c, "PUT", planPath(first, primary), "{" + versions(first, primary)
            + ",\"startDate\":\"2027-03-15\",\"endDate\":\"2027-03-19\",\"travelerCount\":2,\"travelerAges\":[25,30]}").andExpect(status().isOk()));
        var profile = data(call(c, "GET", "/api/trips", null).andExpect(status().isOk()));
        org.junit.jupiter.api.Assertions.assertEquals(second.get("id"), profile.get("upcoming").get(0).get("id"));
        org.junit.jupiter.api.Assertions.assertEquals(first.get("id"), profile.get("upcoming").get(1).get("id"));
        second = data(call(c, "POST", path(second) + "/plans", "{\"expectedVersion\":0,\"name\":\"Later primary\",\"startDate\":\"2027-03-20\",\"endDate\":\"2027-03-24\",\"travelerCount\":2,\"travelerAges\":[25,30]}").andExpect(status().isCreated()));
        var alternative = second.get("plans").get(1);
        second = data(call(c, "PUT", planPath(second, alternative) + "/primary", "{" + versions(second, alternative) + "}").andExpect(status().isOk()));
        profile = data(call(c, "GET", "/api/trips", null).andExpect(status().isOk()));
        org.junit.jupiter.api.Assertions.assertEquals(first.get("id"), profile.get("upcoming").get(0).get("id"));
        org.junit.jupiter.api.Assertions.assertEquals(second.get("id"), profile.get("upcoming").get(1).get("id"));
    }

    @Test void createCopyRenamePromoteAndDeletePreserveIndependentIdentities() throws Exception {
        Client c = client(), other = client(); var trip = create(c); var primary = trip.get("plans").get(0);
        String base = path(trip), originalId = primary.get("id").asString();
        trip = data(call(c, "POST", base + "/plans", "{\"expectedVersion\":0,\"name\":\"Alternative\"," + DETAILS + "}").andExpect(status().isCreated()));
        var option = trip.get("plans").get(1);
        call(other, "PUT", planPath(trip, option) + "/primary", "{" + versions(trip, option) + "}").andExpect(status().isNotFound());
        trip = data(call(c, "POST", planPath(trip, option) + "/copy", "{" + versions(trip, option) + ",\"name\":\"Copy\"}").andExpect(status().isCreated()));
        org.junit.jupiter.api.Assertions.assertEquals(primary, trip.get("plans").get(0));
        org.junit.jupiter.api.Assertions.assertEquals(option, trip.get("plans").get(1));
        var copy = trip.get("plans").get(2);
        org.junit.jupiter.api.Assertions.assertNotEquals(option.get("id"), copy.get("id"));
        org.junit.jupiter.api.Assertions.assertFalse(copy.get("booked").asBoolean());
        trip = data(call(c, "PUT", planPath(trip, option) + "/name", "{" + versions(trip, option) + ",\"name\":\"Renamed\"}").andExpect(status().isOk()));
        option = trip.get("plans").get(1);
        var promoted = data(call(c, "PUT", planPath(trip, option) + "/primary", "{" + versions(trip, option) + "}").andExpect(status().isOk()));
        org.junit.jupiter.api.Assertions.assertEquals(option.get("id"), promoted.get("primaryPlanId"));
        org.junit.jupiter.api.Assertions.assertEquals("Renamed", promoted.get("plans").get(0).get("name").asString());
        invariant(trip.get("id").asString());
        call(c, "PUT", planPath(trip, option) + "/primary", "{" + versions(trip, option) + "}").andExpect(status().isConflict());
        call(c, "DELETE", planPath(promoted, promoted.get("plans").get(0)), "{" + versions(promoted, promoted.get("plans").get(0)) + ",\"confirmed\":true,\"expectedPlanCount\":3}").andExpect(status().isBadRequest());
        var deleted = data(call(c, "DELETE", planPath(promoted, promoted.get("plans").get(0)), "{" + versions(promoted, promoted.get("plans").get(0)) + ",\"confirmed\":true,\"expectedPlanCount\":3,\"replacementPrimaryPlanId\":\"" + originalId + "\"}").andExpect(status().isOk()));
        org.junit.jupiter.api.Assertions.assertEquals(originalId, deleted.get("primaryPlanId").asString());
        invariant(trip.get("id").asString());
        call(c, "PUT", planPath(deleted, deleted.get("plans").get(0)), "{" + versions(deleted, deleted.get("plans").get(0)) + "," + DETAILS + ",\"primary\":true}").andExpect(status().isBadRequest());
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(booleans = {false, true})
    void hydrationRemainsConsistentWhenPrimaryIsReplacedAndDeleted(boolean profileRead) throws Exception {
        Client c = client(); var trip = create(c);
        trip = data(call(c, "POST", path(trip) + "/plans", "{\"expectedVersion\":0,\"name\":\"Replacement\"," + DETAILS + "}").andExpect(status().isCreated()));
        var primary = trip.get("plans").get(0); var replacement = trip.get("plans").get(1);
        UUID tripId = UUID.fromString(trip.get("id").asString());
        long owner = jdbc.queryForObject("SELECT owner_user_id FROM detour_trip WHERE public_id = ?", Long.class, tripId);
        String endpoint = planPath(trip, primary);
        String deletion = "{" + versions(trip, primary) + ",\"confirmed\":true,\"expectedPlanCount\":2,\"replacementPrimaryPlanId\":\"" + replacement.get("id").asString() + "\"}";
        var readingParent = new java.util.concurrent.CountDownLatch(1);
        var writerFinished = new java.util.concurrent.CountDownLatch(1);
        var coordinatedJdbc = new org.springframework.jdbc.core.JdbcTemplate(dataSource) {
            @Override public <T> java.util.List<T> query(String sql, org.springframework.jdbc.core.RowMapper<T> mapper, Object... args) {
                if (!sql.contains("FROM detour_trip trip JOIN catalog_destination")) return super.query(sql, mapper, args);
                return super.query(sql, (row, number) -> {
                    readingParent.countDown();
                    try { writerFinished.await(500, java.util.concurrent.TimeUnit.MILLISECONDS); }
                    catch (InterruptedException e) { Thread.currentThread().interrupt(); throw new java.sql.SQLException(e); }
                    return mapper.mapRow(row, number);
                }, args);
            }
        };
        try (var pool = java.util.concurrent.Executors.newSingleThreadExecutor()) {
            var writer = pool.submit(() -> {
                try {
                    org.junit.jupiter.api.Assertions.assertTrue(readingParent.await(10, java.util.concurrent.TimeUnit.SECONDS));
                    return call(c, "DELETE", endpoint, deletion).andReturn().getResponse().getStatus();
                } finally { writerFinished.countDown(); }
            });
            Trip read = new org.springframework.transaction.support.TransactionTemplate(transactionManager).execute(status -> {
                JdbcTripRepository reader = new JdbcTripRepository(coordinatedJdbc);
                return profileRead ? reader.findAllByOwnerUserId(owner).get(0) : reader.findByPublicIdAndOwnerUserId(tripId, owner).orElseThrow();
            });
            org.junit.jupiter.api.Assertions.assertEquals(UUID.fromString(primary.get("id").asString()), read.primaryPlanId());
            org.junit.jupiter.api.Assertions.assertEquals(2, read.plans().size());
            org.junit.jupiter.api.Assertions.assertEquals(200, writer.get(10, java.util.concurrent.TimeUnit.SECONDS));
        }
        var fresh = data(call(c, "GET", "/api/trips/" + tripId, null).andExpect(status().isOk()));
        org.junit.jupiter.api.Assertions.assertEquals(replacement.get("id"), fresh.get("primaryPlanId"));
        invariant(tripId.toString());
    }

    @Test void tripDuplicationAcceptsCanonicalPrimaryWithoutPurchaseAssociations() throws Exception {
        Client c = client(); var original = create(c); String sourceId = original.get("primaryPlanId").asString();
        var duplicate = data(call(c, "POST", path(original) + "/duplicate", "{\"expectedVersion\":0,\"destinationKey\":\"destination-sfo\",\"startDate\":\"2027-03-10\",\"endDate\":\"2027-03-14\",\"travelerCount\":2,\"travelerAges\":[25,30],\"sourcePlannedItineraryIds\":[\"" + sourceId + "\"]}").andExpect(status().isCreated()));
        org.junit.jupiter.api.Assertions.assertNotEquals(original.get("id"), duplicate.get("id"));
        org.junit.jupiter.api.Assertions.assertTrue(duplicate.get("booking").isNull());
        org.junit.jupiter.api.Assertions.assertEquals(original, data(call(c, "GET", path(original), null)));
        invariant(duplicate.get("id").asString());
    }

    @Test void soleDeletionRequiresExplicitConsentAndRollsBackFailures() throws Exception {
        Client c = client(); var trip = create(c); var plan = trip.get("plans").get(0); String id = trip.get("id").asString();
        String body = "{" + versions(trip, plan) + ",\"confirmed\":true,\"expectedPlanCount\":1";
        call(c, "DELETE", planPath(trip, plan), body + "}").andExpect(status().isBadRequest()); invariant(id);
        org.mockito.Mockito.doThrow(new IllegalStateException("injected trip delete failure")).when(trips).deleteTrip(org.mockito.ArgumentMatchers.anyLong(), org.mockito.ArgumentMatchers.anyLong());
        call(c, "DELETE", planPath(trip, plan), body + ",\"deleteTrip\":true}").andExpect(status().is5xxServerError()); invariant(id);
        org.junit.jupiter.api.Assertions.assertEquals(0L, jdbc.queryForObject("SELECT version FROM detour_trip WHERE public_id = ?", Long.class, UUID.fromString(id)));
        org.mockito.Mockito.doCallRealMethod().when(trips).deleteTrip(org.mockito.ArgumentMatchers.anyLong(), org.mockito.ArgumentMatchers.anyLong());
        call(c, "DELETE", planPath(trip, plan), body + ",\"deleteTrip\":true}").andExpect(status().isNoContent());
        call(c, "GET", path(trip), null).andExpect(status().isNotFound());
    }

    @Test void confirmedStayAndRentalRemainLockedWhileMissingAirfareCanBeAdded() throws Exception {
        Client c = client(); var trip = create(c); String base = path(trip); var plan = trip.get("plans").get(0); String planId = plan.get("id").asString();
        long row = jdbc.queryForObject("SELECT id FROM detour_planned_itinerary WHERE public_id = ?", Long.class, UUID.fromString(planId));
        long stay = jdbc.queryForObject("SELECT id FROM accommodation_unit WHERE catalog_key = 'stay-unit-sfo-hotel-summit'", Long.class);
        long rental = jdbc.queryForObject("SELECT id FROM rental_unit WHERE catalog_key = 'rental-unit-sfo-economy-01'", Long.class);
        trips.saveDraftStaySelection(row, stay, 1);
        trips.saveDraftRentalSelection(row, rental, java.time.OffsetDateTime.parse("2027-03-10T10:00:00-08:00"), java.time.OffsetDateTime.parse("2027-03-14T10:00:00-07:00"));
        var booked = data(call(c, "POST", base + "/bookings", "{\"plannedItineraryId\":\"" + planId + "\",\"expectedVersion\":0,\"idempotencyKey\":\"stay-rental\"}").andExpect(status().isCreated()));
        trip = data(call(c, "GET", base, null)); plan = trip.get("plans").get(0);
        var edited = data(call(c, "PUT", planPath(trip, plan), "{" + versions(trip, plan) + ",\"startDate\":\"2027-03-15\",\"endDate\":\"2027-03-19\",\"travelerCount\":3,\"travelerAges\":[25,30,8]}").andExpect(status().isOk()));
        org.junit.jupiter.api.Assertions.assertEquals(booked.get("selections"), edited.get("plans").get(0).get("selections"));
        for (String slot : java.util.List.of("stays", "rentals")) call(c, "DELETE", base + "/plans/" + planId + "/" + slot, "{\"expectedVersion\":2,\"expectedDraftVersion\":1}").andExpect(status().isConflict());
        var flights = data(call(c, "GET", base + "/plans/" + planId + "/airfare", null).andExpect(status().isOk()));
        var choice = flights.get("options").get(0);
        var added = data(call(c, "PUT", base + "/plans/" + planId + "/airfare", "{\"expectedVersion\":2,\"expectedDraftVersion\":1,\"outboundFlightInstanceId\":" + choice.get("outbound").get("flightInstanceId").asLong() + ",\"returnFlightInstanceId\":" + choice.get("returnFlight").get("flightInstanceId").asLong() + "}").andExpect(status().isOk()));
        org.junit.jupiter.api.Assertions.assertEquals(booked, added.get("booking"));
        org.junit.jupiter.api.Assertions.assertNotNull(added.get("plans").get(0).get("selections").get("airfare"));
        call(c, "POST", base + "/bookings", "{\"plannedItineraryId\":\"" + planId + "\",\"expectedVersion\":3,\"idempotencyKey\":\"no-incremental-purchase\"}").andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("ALREADY_BOOKED"));
        call(c, "POST", base + "/bookings/" + booked.get("id").asString() + "/cancel", "{\"expectedVersion\":3}").andExpect(status().isOk());
        call(c, "DELETE", base + "/plans/" + planId + "/stays", "{\"expectedVersion\":4,\"expectedDraftVersion\":2}").andExpect(status().isConflict());
    }

    @Test void primaryDeletionRollsBackPointerAndTwoSameVersionPromotionsHaveOneWinner() throws Exception {
        Client c = client(); var trip = create(c);
        trip = data(call(c, "POST", path(trip) + "/plans", "{\"expectedVersion\":0,\"name\":\"B\"," + DETAILS + "}").andExpect(status().isCreated()));
        var primary = trip.get("plans").get(0); var option = trip.get("plans").get(1); String id = trip.get("id").asString();
        org.mockito.Mockito.doThrow(new IllegalStateException("injected child delete failure")).when(trips).deletePlanned(org.mockito.ArgumentMatchers.anyLong(), org.mockito.ArgumentMatchers.anyLong());
        call(c, "DELETE", planPath(trip, primary), "{" + versions(trip, primary) + ",\"confirmed\":true,\"expectedPlanCount\":2,\"replacementPrimaryPlanId\":\"" + option.get("id").asString() + "\"}").andExpect(status().is5xxServerError());
        org.junit.jupiter.api.Assertions.assertEquals(primary.get("id").asString(), data(call(c, "GET", path(trip), null)).get("primaryPlanId").asString()); invariant(id);
        org.mockito.Mockito.doCallRealMethod().when(trips).deletePlanned(org.mockito.ArgumentMatchers.anyLong(), org.mockito.ArgumentMatchers.anyLong());
        String endpoint = planPath(trip, option) + "/primary", body = "{" + versions(trip, option) + "}";
        var barrier = new java.util.concurrent.CyclicBarrier(2);
        try (var pool = java.util.concurrent.Executors.newFixedThreadPool(2)) {
            java.util.concurrent.Callable<Integer> action = () -> {barrier.await(); return call(c, "PUT", endpoint, body).andReturn().getResponse().getStatus();};
            var a = pool.submit(action); var b = pool.submit(action); var results = java.util.List.of(a.get(), b.get());
            org.junit.jupiter.api.Assertions.assertEquals(1L, results.stream().filter(status -> status == 200).count());
            org.junit.jupiter.api.Assertions.assertEquals(1L, results.stream().filter(status -> status == 409).count());
        }
        invariant(id);
    }

    @Test void promotionVersusDeletionAndPurchaseVersusDeletionKeepReachablePrimary() throws Exception {
        for (boolean purchase : java.util.List.of(false, true)) {
            Client c = client(); var trip = create(c); String id = trip.get("id").asString();
            trip = data(call(c, "POST", path(trip) + "/plans", "{\"expectedVersion\":0,\"name\":\"B\"," + DETAILS + "}").andExpect(status().isCreated()));
            var target = trip.get("plans").get(1); String targetId = target.get("id").asString();
            long row = jdbc.queryForObject("SELECT id FROM detour_planned_itinerary WHERE public_id = ?", Long.class, UUID.fromString(targetId));
            long outbound = jdbc.queryForObject("SELECT i.id FROM flight_instance i JOIN flight_schedule s ON s.id = i.flight_schedule_id WHERE s.catalog_key = 'airfare-out-sfo-d1' AND i.service_date = DATE '2027-03-10'", Long.class);
            long inbound = jdbc.queryForObject("SELECT i.id FROM flight_instance i JOIN flight_schedule s ON s.id = i.flight_schedule_id WHERE s.catalog_key = 'airfare-in-sfo-d1' AND i.service_date = DATE '2027-03-14'", Long.class);
            trips.saveDraftAirfareSelection(row, outbound, inbound);
            String base = path(trip), endpoint = planPath(trip, target);
            String deletion = "{" + versions(trip, target) + ",\"confirmed\":true,\"expectedPlanCount\":2}";
            String mutation = purchase ? "{\"expectedVersion\":1,\"plannedItineraryId\":\"" + targetId + "\",\"idempotencyKey\":\"race\"}" : "{" + versions(trip, target) + "}";
            var barrier = new java.util.concurrent.CyclicBarrier(2);
            try (var pool = java.util.concurrent.Executors.newFixedThreadPool(2)) {
                var a = pool.submit(() -> {barrier.await(); return call(c, "DELETE", endpoint, deletion).andReturn().getResponse().getStatus();});
                var b = pool.submit(() -> {barrier.await(); return call(c, purchase ? "POST" : "PUT", purchase ? base + "/bookings" : endpoint + "/primary", mutation).andReturn().getResponse().getStatus();});
                var results = java.util.List.of(a.get(), b.get());
                org.junit.jupiter.api.Assertions.assertEquals(1L, results.stream().filter(code -> code == 200 || code == 201).count());
                org.junit.jupiter.api.Assertions.assertEquals(1L, results.stream().filter(code -> code == 409 || code == 404).count());
            }
            invariant(id);
        }
    }

    @Test void purchasedFlightsRemainFrozenWhileDatesAndPartyChangeAndCopyIsUnconfirmed() throws Exception {
        Client c = client(); var trip = create(c); var plan = trip.get("plans").get(0);
        String id = trip.get("id").asString(), planId = plan.get("id").asString();
        long row = jdbc.queryForObject("SELECT id FROM detour_planned_itinerary WHERE public_id = ?", Long.class, UUID.fromString(planId));
        long outbound = jdbc.queryForObject("SELECT i.id FROM flight_instance i JOIN flight_schedule s ON s.id = i.flight_schedule_id WHERE s.catalog_key = 'airfare-out-sfo-d1' AND i.service_date = DATE '2027-03-10'", Long.class);
        long inbound = jdbc.queryForObject("SELECT i.id FROM flight_instance i JOIN flight_schedule s ON s.id = i.flight_schedule_id WHERE s.catalog_key = 'airfare-in-sfo-d1' AND i.service_date = DATE '2027-03-14'", Long.class);
        int seats = jdbc.queryForObject("SELECT available_seats FROM flight_instance WHERE id = ?", Integer.class, outbound);
        trips.saveDraftAirfareSelection(row, outbound, inbound);
        var booked = data(call(c, "POST", path(trip) + "/bookings", "{\"plannedItineraryId\":\"" + planId + "\",\"expectedVersion\":0,\"idempotencyKey\":\"freeze\"}").andExpect(status().isCreated()));
        trip = data(call(c, "GET", path(trip), null)); plan = trip.get("plans").get(0);
        var edited = data(call(c, "PUT", planPath(trip, plan), "{" + versions(trip, plan) + ",\"startDate\":\"2027-03-15\",\"endDate\":\"2027-03-19\",\"travelerCount\":3,\"travelerAges\":[25,30,8]}").andExpect(status().isOk()));
        org.junit.jupiter.api.Assertions.assertEquals(booked, edited.get("booking"));
        org.junit.jupiter.api.Assertions.assertEquals(booked.get("tally").get("airfareTotalCents"), edited.get("plans").get(0).get("tally").get("airfareTotalCents"));
        call(c, "DELETE", path(trip) + "/drafts/" + planId + "/airfare", "{\"expectedVersion\":2,\"expectedDraftVersion\":1}").andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("CONFIRMED_COMPONENT_LOCKED"));
        call(c, "PUT", planPath(edited, edited.get("plans").get(0)), "{" + versions(edited, edited.get("plans").get(0)) + "," + DETAILS + ",\"selections\":{}}").andExpect(status().isConflict());
        var copied = data(call(c, "POST", planPath(edited, edited.get("plans").get(0)) + "/copy", "{" + versions(edited, edited.get("plans").get(0)) + ",\"name\":\"Unconfirmed copy\"}").andExpect(status().isCreated()));
        org.junit.jupiter.api.Assertions.assertFalse(copied.get("plans").get(1).get("booked").asBoolean());
        org.junit.jupiter.api.Assertions.assertEquals(0, copied.get("plans").get(1).get("lockedComponents").size());
        var promoted = data(call(c, "PUT", planPath(copied, copied.get("plans").get(1)) + "/primary", "{" + versions(copied, copied.get("plans").get(1)) + "}").andExpect(status().isOk()));
        var back = data(call(c, "PUT", path(trip) + "/plans/" + planId + "/primary", "{\"expectedVersion\":4,\"expectedPlanVersion\":1}").andExpect(status().isOk()));
        org.junit.jupiter.api.Assertions.assertEquals(booked, back.get("booking")); invariant(id);
        var profile = data(call(c, "GET", "/api/trips", null));
        org.junit.jupiter.api.Assertions.assertEquals(back.get("plans").get(0).get("name"), profile.get("upcoming").get(0).get("alternatives").get(0).get("name"));
        org.junit.jupiter.api.Assertions.assertTrue(profile.get("upcoming").get(0).get("alternatives").get(0).get("booked").asBoolean());
        clock.setInstant(java.time.ZonedDateTime.of(2027, 3, 11, 12, 0, 0, 0, app.detour.common.ClockConfiguration.PDX_ZONE).toInstant());
        call(c, "POST", path(trip) + "/bookings/" + booked.get("id").asString() + "/cancel", "{\"expectedVersion\":5}").andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("TRIP_EXPIRED"));
        clock.reset();
        var canceled = data(call(c, "POST", path(trip) + "/bookings/" + booked.get("id").asString() + "/cancel", "{\"expectedVersion\":5}").andExpect(status().isOk()));
        call(c, "POST", path(trip) + "/bookings", "{\"plannedItineraryId\":\"" + copied.get("plans").get(1).get("id").asString() + "\",\"expectedVersion\":6,\"idempotencyKey\":\"invalid-copy\"}").andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("PLAN_SELECTIONS_INVALID"));
        org.junit.jupiter.api.Assertions.assertEquals(seats, jdbc.queryForObject("SELECT available_seats FROM flight_instance WHERE id = ?", Integer.class, outbound));
        call(c, "DELETE", path(trip) + "/plans/" + planId, "{\"expectedVersion\":6,\"expectedPlanVersion\":1,\"confirmed\":true,\"expectedPlanCount\":2}").andExpect(status().isConflict());
        call(c, "DELETE", path(trip) + "/drafts/" + planId + "/airfare", "{\"expectedVersion\":6,\"expectedDraftVersion\":1}").andExpect(status().isConflict());
        org.junit.jupiter.api.Assertions.assertEquals(booked.get("selections"), canceled.get("booking").get("selections"));
    }

    @Test void searchesAndTalliesUseLatestPurchasedAirfareParty() throws Exception {
        Client c = client(); var trip = create(c); var plan = trip.get("plans").get(0);
        String base = path(trip), planId = plan.get("id").asString();
        jdbc.update("UPDATE detour_trip SET budget_cents = 500000 WHERE public_id = ?", UUID.fromString(trip.get("id").asString()));
        long row = jdbc.queryForObject("SELECT id FROM detour_planned_itinerary WHERE public_id = ?", Long.class, UUID.fromString(planId));
        long outbound = jdbc.queryForObject("SELECT i.id FROM flight_instance i JOIN flight_schedule s ON s.id = i.flight_schedule_id WHERE s.catalog_key = 'airfare-out-sfo-d1' AND i.service_date = DATE '2027-03-10'", Long.class);
        long inbound = jdbc.queryForObject("SELECT i.id FROM flight_instance i JOIN flight_schedule s ON s.id = i.flight_schedule_id WHERE s.catalog_key = 'airfare-in-sfo-d1' AND i.service_date = DATE '2027-03-14'", Long.class);
        trips.saveDraftAirfareSelection(row, outbound, inbound);
        var booked = data(call(c, "POST", base + "/bookings", "{\"plannedItineraryId\":\"" + planId + "\",\"expectedVersion\":0,\"idempotencyKey\":\"budget-first\"}").andExpect(status().isCreated()));
        trip = data(call(c, "GET", base, null)); plan = trip.get("plans").get(0);
        trip = data(call(c, "PUT", planPath(trip, plan), "{" + versions(trip, plan) + ",\"startDate\":\"2027-03-10\",\"endDate\":\"2027-03-14\",\"travelerCount\":3,\"travelerAges\":[25,30,8]}").andExpect(status().isOk()));
        long purchasedFare = booked.get("tally").get("airfareTotalCents").asLong();
        call(c, "GET", base + "/plans/" + planId + "/stays?type=HOTEL", null).andExpect(status().isOk()).andExpect(jsonPath("$.availableTripBudgetCents").value(500000 - purchasedFare));
        call(c, "GET", base + "/plans/" + planId + "/rentals?pickupAt=2027-03-10T10:00:00Z&returnAt=2027-03-14T10:00:00Z", null).andExpect(status().isOk()).andExpect(jsonPath("$.availableTripBudgetCents").value(500000 - purchasedFare));
        call(c, "POST", base + "/bookings/" + booked.get("id").asString() + "/cancel", "{\"expectedVersion\":2}").andExpect(status().isOk());
        var latest = data(call(c, "POST", base + "/bookings", "{\"plannedItineraryId\":\"" + planId + "\",\"expectedVersion\":3,\"idempotencyKey\":\"budget-next\"}").andExpect(status().isCreated()));
        var detail = data(call(c, "GET", base, null));
        org.junit.jupiter.api.Assertions.assertEquals(latest.get("tally").get("airfareTotalCents"), detail.get("plans").get(0).get("tally").get("airfareTotalCents"));
    }

    @Test void savingAlternativeChangesOnlyItsOwnDatesPartyAndSelections() throws Exception {
        Cookie csrf = mvc.perform(get("/")).andReturn().getResponse().getCookie("XSRF-TOKEN");
        var registered = mvc.perform(post("/api/auth/register").cookie(csrf).header("X-XSRF-TOKEN", csrf.getValue())
            .contentType(MediaType.APPLICATION_JSON).content("{\"email\":\"independent@example.test\",\"password\":\"aaaaaaaaaaaa\"}"))
            .andExpect(status().isCreated()).andReturn();
        var session = (MockHttpSession) registered.getRequest().getSession(false);
        var created = mvc.perform(post("/api/trips").session(session).cookie(csrf).header("X-XSRF-TOKEN", csrf.getValue())
            .contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"Spring\",\"destinationKey\":\"destination-sfo\",\"startDate\":\"2027-03-10\",\"endDate\":\"2027-03-14\",\"travelerCount\":2,\"travelerAges\":[25,30]}"))
            .andExpect(status().isCreated()).andReturn();
        var mapper = tools.jackson.databind.json.JsonMapper.builder().build();
        var initial = mapper.readTree(created.getResponse().getContentAsString());
        String trip = initial.get("id").asString();
        var added = mvc.perform(post("/api/trips/" + trip + "/plans").session(session).cookie(csrf).header("X-XSRF-TOKEN", csrf.getValue())
            .contentType(MediaType.APPLICATION_JSON).content("{\"expectedVersion\":0,\"name\":\"Later\",\"startDate\":\"2027-03-15\",\"endDate\":\"2027-03-19\",\"travelerCount\":3,\"travelerAges\":[25,30,8]}"))
            .andExpect(status().isCreated()).andReturn();
        var detail = mapper.readTree(added.getResponse().getContentAsString());
        String alternative = detail.get("plans").get(1).get("id").asString();
        mvc.perform(put("/api/trips/" + trip + "/plans/" + alternative).session(session).cookie(csrf).header("X-XSRF-TOKEN", csrf.getValue())
            .contentType(MediaType.APPLICATION_JSON).content("{\"expectedVersion\":1,\"expectedPlanVersion\":0,\"startDate\":\"2027-03-16\",\"endDate\":\"2027-03-20\",\"travelerCount\":1,\"travelerAges\":[40]}"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.plans[1].id").value(alternative))
            .andExpect(jsonPath("$.plans[1].name").value("Later"))
            .andExpect(jsonPath("$.plans[1].travelerCount").value(1))
            .andExpect(jsonPath("$.plans[0].startDate").value("2027-03-10"))
            .andExpect(jsonPath("$.plans[0].travelerCount").value(2));
    }
}
