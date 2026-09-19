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
        ConfigurableApplicationContext first = start(databaseUrl);
        try {
            URI base = baseUri(first);
            String csrf = csrfCookie(base);
            HttpResponse<String> registration = request(base, "POST", "/api/auth/register", csrf,
                    "{\"email\":\"restart-trip@example.test\",\"password\":\"aaaaaaaaaaaa\"}").send();
            assertEquals(201, registration.statusCode());
            sessionCookie = cookie(registration, "JSESSIONID");
            HttpResponse<String> created = request(base, "POST", "/api/trips", sessionCookie + "|" + csrf,
                    "{\"destinationKey\":\"destination-muc\",\"startDate\":\"2027-03-01\",\"endDate\":\"2027-03-02\",\"travelerCount\":1,\"budgetCents\":0}").send();
            assertEquals(201, created.statusCode());
            var body = tools.jackson.databind.json.JsonMapper.builder().build().readTree(created.body());
            tripId = body.get("id").asString();
            draftId = body.get("drafts").get(0).get("id").asString();
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
            assertEquals(draftId, body.get("drafts").get(0).get("id").asString());
            assertEquals(0, body.get("budgetCents").asInt());
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
