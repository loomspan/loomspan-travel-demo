package app.detour.booking;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import app.detour.DetourApplication;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.web.server.context.WebServerApplicationContext;
import org.springframework.context.ConfigurableApplicationContext;

class BookingApplicationRestartIntegrationTest {
    @TempDir Path temporaryDirectory;

    @Test
    void persistsBookingAndSnapshotsAcrossApplicationRestart() throws Exception {
        String databaseUrl = "jdbc:h2:file:" + temporaryDirectory.resolve("booking-" + UUID.randomUUID()).toAbsolutePath().toString().replace('\\', '/')
                + ";DB_CLOSE_ON_EXIT=FALSE;LOCK_TIMEOUT=10000";
        String sessionCookie;
        String tripId;
        String draftId;
        String plannedId;
        String originalBookingRef;
        String originalAirfareRef;
        String originalStayRef;
        long originalGrandTotal;

        ConfigurableApplicationContext first = start(databaseUrl);
        try {
            URI base = baseUri(first);
            String csrf = csrfCookie(base);
            HttpResponse<String> registration = request(base, "POST", "/api/auth/register", csrf,
                    "{\"email\":\"restart-booking@example.test\",\"password\":\"aaaaaaaaaaaa\"}").send();
            assertEquals(201, registration.statusCode());
            sessionCookie = cookie(registration, "JSESSIONID");

            HttpResponse<String> created = request(base, "POST", "/api/trips", sessionCookie + "|" + csrf,
                    "{\"destinationKey\":\"destination-sfo\",\"startDate\":\"2027-03-10\",\"endDate\":\"2027-03-14\",\"travelerCount\":2,\"travelerAges\":[25,25],\"budgetCents\":500000}").send();
            assertEquals(201, created.statusCode());
            var body = tools.jackson.databind.json.JsonMapper.builder().build().readTree(created.body());
            tripId = body.get("id").asString();
            draftId = body.get("drafts").get(0).get("id").asString();

            org.springframework.jdbc.core.JdbcTemplate jdbc = first.getBean(org.springframework.jdbc.core.JdbcTemplate.class);
            jdbc.update("""
                    INSERT INTO detour_trip_draft_airfare_selection (draft_id, outbound_flight_instance_id, return_flight_instance_id)
                    VALUES ((SELECT id FROM detour_trip_draft WHERE public_id = ?),
                        (SELECT instance.id FROM flight_instance instance JOIN flight_schedule schedule ON schedule.id = instance.flight_schedule_id WHERE schedule.catalog_key = 'airfare-out-sfo-d1' AND instance.service_date = DATE '2027-03-10'),
                        (SELECT instance.id FROM flight_instance instance JOIN flight_schedule schedule ON schedule.id = instance.flight_schedule_id WHERE schedule.catalog_key = 'airfare-in-sfo-d1' AND instance.service_date = DATE '2027-03-14'))
                    """, UUID.fromString(draftId));
            jdbc.update("""
                    INSERT INTO detour_trip_draft_stay_selection (draft_id, accommodation_unit_id, unit_count)
                    VALUES ((SELECT id FROM detour_trip_draft WHERE public_id = ?),
                        (SELECT id FROM accommodation_unit WHERE catalog_key = 'stay-unit-sfo-hotel-summit'), 1)
                    """, UUID.fromString(draftId));

            HttpResponse<String> promoted = request(base, "POST", "/api/trips/" + tripId + "/drafts/" + draftId + "/plan", sessionCookie + "|" + csrf,
                    "{\"expectedVersion\":0,\"expectedDraftVersion\":0}").send();
            assertEquals(201, promoted.statusCode());
            var promotedBody = tools.jackson.databind.json.JsonMapper.builder().build().readTree(promoted.body());
            plannedId = promotedBody.get("planned").get(0).get("id").asString();

            HttpResponse<String> booked = request(base, "POST", "/api/trips/" + tripId + "/bookings", sessionCookie + "|" + csrf,
                    "{\"plannedItineraryId\":\"" + plannedId + "\",\"expectedVersion\":1,\"idempotencyKey\":\"restart-test-key\"}").send();
            assertEquals(201, booked.statusCode());
            var bookedBody = tools.jackson.databind.json.JsonMapper.builder().build().readTree(booked.body());

            originalBookingRef = bookedBody.get("bookingReference").asString();
            originalAirfareRef = bookedBody.get("airfareReference").asString();
            originalStayRef = bookedBody.get("stayReference").asString();
            originalGrandTotal = bookedBody.get("grandTotalCents").asLong();

            assertNotNull(originalBookingRef);
            assertNotNull(originalAirfareRef);
            assertNotNull(originalStayRef);

            // Mutate live catalog tables to prove that booking snapshots do not read live catalog data
            jdbc.update("UPDATE flight_instance SET base_fare_cents = base_fare_cents + 50000");
            jdbc.update("UPDATE accommodation_property SET name = 'Mutated Live Hotel Name'");
        } finally {
            first.close();
        }

        ConfigurableApplicationContext second = start(databaseUrl);
        try {
            URI base = baseUri(second);
            String csrf = csrfCookie(base);
            HttpResponse<String> login = request(base, "POST", "/api/auth/login", csrf,
                    "{\"email\":\"restart-booking@example.test\",\"password\":\"aaaaaaaaaaaa\"}").send();
            assertEquals(204, login.statusCode());
            String newSession = cookie(login, "JSESSIONID");

            // 1. Verify active booking retrieval survived restart
            HttpResponse<String> activeRes = request(base, "GET", "/api/trips/" + tripId + "/bookings/active", newSession, null).send();
            assertEquals(200, activeRes.statusCode());
            var activeBody = tools.jackson.databind.json.JsonMapper.builder().build().readTree(activeRes.body());

            assertEquals(originalBookingRef, activeBody.get("bookingReference").asString());
            assertEquals("ACTIVE", activeBody.get("status").asString());
            assertEquals(originalAirfareRef, activeBody.get("airfareReference").asString());
            assertEquals(originalStayRef, activeBody.get("stayReference").asString());
            assertEquals(originalGrandTotal, activeBody.get("grandTotalCents").asLong());

            // Verify frozen property name in stay snapshot is NOT the mutated live catalog value
            assertEquals("Summit Family Suites", activeBody.get("selections").get("stay").get("propertyName").asString());

            // 2. Verify booking history survived restart
            HttpResponse<String> historyRes = request(base, "GET", "/api/trips/" + tripId + "/bookings", newSession, null).send();
            assertEquals(200, historyRes.statusCode());
            var historyBody = tools.jackson.databind.json.JsonMapper.builder().build().readTree(historyRes.body());
            assertEquals(1, historyBody.size());
            assertEquals(originalBookingRef, historyBody.get(0).get("bookingReference").asString());

            // 3. Verify trips profile summary reflects bookedCount = 1 and hasBookingHistory = true
            HttpResponse<String> profileRes = request(base, "GET", "/api/trips", newSession, null).send();
            assertEquals(200, profileRes.statusCode());
            var profileBody = tools.jackson.databind.json.JsonMapper.builder().build().readTree(profileRes.body());
            assertEquals(1, profileBody.get("upcoming").get(0).get("bookedCount").asInt());
            assertTrue(profileBody.get("upcoming").get(0).get("hasBookingHistory").asBoolean());
        } finally {
            second.close();
        }
    }

    private ConfigurableApplicationContext start(String databaseUrl) {
        List<String> args = new ArrayList<>(List.of("--server.address=127.0.0.1", "--server.port=0", "--spring.datasource.url=" + databaseUrl));
        return new SpringApplicationBuilder(DetourApplication.class).run(args.toArray(String[]::new));
    }

    private static URI baseUri(ConfigurableApplicationContext context) {
        return URI.create("http://127.0.0.1:" + ((WebServerApplicationContext) context).getWebServer().getPort());
    }

    private static String csrfCookie(URI base) throws Exception {
        HttpResponse<String> response = request(base, "GET", "/", null, null).send();
        assertEquals(200, response.statusCode());
        return cookie(response, "XSRF-TOKEN");
    }

    private static String cookie(HttpResponse<String> response, String name) {
        return response.headers().allValues("set-cookie").stream().filter(value -> value.startsWith(name + "="))
                .findFirst().orElseThrow(() -> new AssertionError("Missing " + name + " cookie"));
    }

    private static PendingRequest request(URI base, String method, String path, String cookie, String body) {
        HttpRequest.Builder builder = HttpRequest.newBuilder(base.resolve(path));
        if (cookie != null) {
            String[] cookies = cookie.split("\\|", 2);
            String primaryPair = cookies[0].substring(0, cookies[0].indexOf(';'));
            String csrfCookie = cookies.length == 2 ? cookies[1] : cookies[0];
            String csrfPair = csrfCookie.substring(0, csrfCookie.indexOf(';'));
            builder.header("Cookie", cookies.length == 2 ? primaryPair + "; " + csrfPair : primaryPair);
            if (csrfCookie.startsWith("XSRF-TOKEN=")) builder.header("X-XSRF-TOKEN", csrfPair.substring("XSRF-TOKEN=".length()));
        }
        if (body == null) builder.method(method, HttpRequest.BodyPublishers.noBody());
        else builder.header("Content-Type", "application/json").method(method, HttpRequest.BodyPublishers.ofString(body));
        return new PendingRequest(builder.build());
    }

    private record PendingRequest(HttpRequest request) {
        HttpResponse<String> send() throws Exception {
            return HttpClient.newHttpClient().send(request, HttpResponse.BodyHandlers.ofString());
        }
    }
}
