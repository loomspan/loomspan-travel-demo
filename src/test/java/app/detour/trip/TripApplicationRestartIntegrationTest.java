package app.detour.trip;

import static org.junit.jupiter.api.Assertions.assertEquals;

import app.detour.DetourApplication;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Path;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.web.server.context.WebServerApplicationContext;
import org.springframework.context.ConfigurableApplicationContext;

class TripApplicationRestartIntegrationTest {
    @TempDir Path temporaryDirectory;

    @Test
    void persistsTripAndDraftAcrossApplicationRestart() throws Exception {
        String databaseUrl = "jdbc:h2:file:" + temporaryDirectory.resolve("trip-" + UUID.randomUUID()).toAbsolutePath().toString().replace('\\', '/')
                + ";DB_CLOSE_ON_EXIT=FALSE;LOCK_TIMEOUT=10000";
        String sessionCookie;
        String tripId;
        String draftId;
        String plannedId;
        String duplicatedDraftId;
        long copiedFare;
        ConfigurableApplicationContext first = start(databaseUrl);
        try {
            URI base = baseUri(first);
            String csrf = csrfCookie(base);
            HttpResponse<String> registration = request(base, "POST", "/api/auth/register", csrf,
                    "{\"email\":\"restart-trip@example.test\",\"password\":\"aaaaaaaaaaaa\"}").send();
            assertEquals(201, registration.statusCode());
            sessionCookie = cookie(registration, "JSESSIONID");
            HttpResponse<String> created = request(base, "POST", "/api/trips", sessionCookie + "|" + csrf,
                    "{\"destinationKey\":\"destination-sfo\",\"startDate\":\"2027-03-10\",\"endDate\":\"2027-03-14\",\"travelerCount\":2,\"travelerAges\":[25,30],\"budgetCents\":50000}").send();
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
            copiedFare = promotedBody.get("planned").get(0).get("selections").get("airfare").get("outboundBaseFareCents").asLong();

            HttpResponse<String> duplicated = request(base, "POST", "/api/trips/" + tripId + "/alternatives/" + plannedId + "/duplicate", sessionCookie + "|" + csrf,
                    "{\"expectedVersion\":1}").send();
            assertEquals(201, duplicated.statusCode());
            var dupBody = tools.jackson.databind.json.JsonMapper.builder().build().readTree(duplicated.body());
            duplicatedDraftId = dupBody.get("drafts").get(1).get("id").asString();

            // Mutate catalog to verify restart does not reload live catalog
            jdbc.update("UPDATE flight_instance SET base_fare_cents = base_fare_cents + 10000");
        } finally {
            first.close();
        }
        ConfigurableApplicationContext second = start(databaseUrl);
        try {
            URI base = baseUri(second);
            assertEquals(401, request(base, "GET", "/api/trips/" + tripId, sessionCookie, null).send().statusCode());
            String csrf = csrfCookie(base);
            HttpResponse<String> login = request(base, "POST", "/api/auth/login", csrf,
                    "{\"email\":\"restart-trip@example.test\",\"password\":\"aaaaaaaaaaaa\"}").send();
            assertEquals(204, login.statusCode());
            String newSession = cookie(login, "JSESSIONID");
            HttpResponse<String> detail = request(base, "GET", "/api/trips/" + tripId, newSession, null).send();
            assertEquals(200, detail.statusCode());
            var body = tools.jackson.databind.json.JsonMapper.builder().build().readTree(detail.body());
            assertEquals(tripId, body.get("id").asString());
            assertEquals(2, body.get("version").asInt());
            assertEquals(1, body.get("planned").size());
            assertEquals(plannedId, body.get("planned").get(0).get("id").asString());
            assertEquals(copiedFare, body.get("planned").get(0).get("selections").get("airfare").get("outboundBaseFareCents").asLong());
            assertEquals("Summit Family Suites", body.get("planned").get(0).get("selections").get("stay").get("propertyName").asString());
            assertEquals(2, body.get("drafts").size());
            assertEquals(draftId, body.get("drafts").get(0).get("id").asString());
            assertEquals(duplicatedDraftId, body.get("drafts").get(1).get("id").asString());
            assertEquals(0, body.get("drafts").get(1).get("version").asInt());
        } finally {
            second.close();
        }
    }

    private ConfigurableApplicationContext start(String databaseUrl) {
        return new SpringApplicationBuilder(DetourApplication.class)
                .run("--server.address=127.0.0.1", "--server.port=0", "--spring.datasource.url=" + databaseUrl);
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
