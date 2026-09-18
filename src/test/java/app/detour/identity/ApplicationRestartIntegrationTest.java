package app.detour.identity;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Path;
import java.util.UUID;
import app.detour.DetourApplication;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.boot.web.server.context.WebServerApplicationContext;

class ApplicationRestartIntegrationTest {
    @TempDir
    Path temporaryDirectory;

    @Test
    void persistsAccountButRejectsPreRestartSession() throws Exception {
        String databaseUrl = "jdbc:h2:file:" + temporaryDirectory.resolve("identity-" + UUID.randomUUID()).toAbsolutePath().toString().replace('\\', '/')
                + ";DB_CLOSE_ON_EXIT=FALSE;LOCK_TIMEOUT=10000";
        ConfigurableApplicationContext first = start(databaseUrl);
        String sessionCookie;
        try {
            URI base = baseUri(first);
            String csrfCookie = csrfCookie(base);
            HttpResponse<String> registration = request(base, "POST", "/api/auth/register", csrfCookie,
                    "{\"email\":\"restart@example.test\",\"password\":\"aaaaaaaaaaaa\"}").send();
            assertEquals(201, registration.statusCode());
            sessionCookie = cookie(registration, "JSESSIONID");
            assertTrue(sessionCookie.contains("HttpOnly"));
            assertTrue(sessionCookie.contains("Secure"));
            assertTrue(sessionCookie.contains("SameSite=Lax"));
            assertTrue(sessionCookie.contains("Path=/"));
            assertEquals(200, request(base, "GET", "/api/profile", sessionCookie, null).send().statusCode());
        } finally {
            first.close();
        }

        ConfigurableApplicationContext second = start(databaseUrl);
        try {
            URI base = baseUri(second);
            assertEquals(401, request(base, "GET", "/api/profile", sessionCookie, null).send().statusCode());
            String csrfCookie = csrfCookie(base);
            assertEquals(204, request(base, "POST", "/api/auth/login", csrfCookie,
                    "{\"email\":\"restart@example.test\",\"password\":\"aaaaaaaaaaaa\"}").send().statusCode());
        } finally {
            second.close();
        }
    }

    private ConfigurableApplicationContext start(String databaseUrl) {
        return new SpringApplicationBuilder(DetourApplication.class)
                .run("--server.address=127.0.0.1", "--server.port=0", "--spring.datasource.url=" + databaseUrl);
    }

    private static URI baseUri(ConfigurableApplicationContext context) {
        int port = ((WebServerApplicationContext) context).getWebServer().getPort();
        return URI.create("http://127.0.0.1:" + port);
    }

    private static String csrfCookie(URI base) throws Exception {
        HttpResponse<String> response = request(base, "GET", "/", null, null).send();
        assertEquals(200, response.statusCode());
        return cookie(response, "XSRF-TOKEN");
    }

    private static String cookie(HttpResponse<String> response, String name) {
        return response.headers().allValues("set-cookie").stream()
                .filter(value -> value.startsWith(name + "="))
                .findFirst().orElseThrow(() -> new AssertionError("Missing " + name + " cookie"));
    }

    private static PendingRequest request(URI base, String method, String path, String cookie, String body) {
        HttpRequest.Builder builder = HttpRequest.newBuilder(base.resolve(path));
        if (cookie != null) {
            String pair = cookie.substring(0, cookie.indexOf(';'));
            builder.header("Cookie", pair);
            if (cookie.startsWith("XSRF-TOKEN=")) {
                builder.header("X-XSRF-TOKEN", pair.substring("XSRF-TOKEN=".length()));
            }
        }
        if (body == null) {
            builder.method(method, HttpRequest.BodyPublishers.noBody());
        } else {
            builder.header("Content-Type", "application/json").method(method, HttpRequest.BodyPublishers.ofString(body));
        }
        return new PendingRequest(builder.build());
    }

    private record PendingRequest(HttpRequest request) {
        HttpResponse<String> send() throws Exception {
            return HttpClient.newHttpClient().send(request, HttpResponse.BodyHandlers.ofString());
        }
    }
}
