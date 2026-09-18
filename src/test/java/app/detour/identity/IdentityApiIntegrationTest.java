package app.detour.identity;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.forwardedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import jakarta.servlet.http.Cookie;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

@SpringBootTest
@AutoConfigureMockMvc
class IdentityApiIntegrationTest {
    private static final String DATABASE_URL = "jdbc:h2:mem:identity_api_" + UUID.randomUUID() + ";DB_CLOSE_DELAY=-1";

    @DynamicPropertySource
    static void datasourceProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", () -> DATABASE_URL);
    }

    @Autowired
    private MockMvc mockMvc;

    @Test
    void registersCanonicalAccountAndExposesOnlyItsEmptyProfile() throws Exception {
        Client client = anonymous();
        MvcResult registration = client.unsafe(post("/api/auth/register"),
                        "{\"email\":\"  Ada@Example.test  \",\"password\":\"aaaaaaaaaaaa\"}")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.email").value("ada@example.test"))
                .andExpect(jsonPath("$.id").doesNotExist())
                .andReturn();
        client.session = (MockHttpSession) registration.getRequest().getSession(false);

        mockMvc.perform(get("/api/profile").session(client.session).cookie(client.csrf))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value("ada@example.test"))
                .andExpect(jsonPath("$.password").doesNotExist())
                .andExpect(jsonPath("$.passwordHash").doesNotExist());
    }

    @Test
    void enforcesInclusivePasswordLengthWithoutCompositionRules() throws Exception {
        assertRegistrationStatus("eleven@example.test", "a".repeat(11), 400);
        assertRegistrationStatus("twelve@example.test", "a".repeat(12), 201);
        assertRegistrationStatus("one-twenty-eight@example.test", "a".repeat(128), 201);
        assertRegistrationStatus("one-twenty-nine@example.test", "a".repeat(129), 400);
    }

    @Test
    void normalizesEmailAndUsesGenericAuthenticationFailures() throws Exception {
        Client registered = register("Case@Example.test", "aaaaaaaaaaaa");
        Client duplicate = anonymous();
        duplicate.unsafe(post("/api/auth/register"), "{\"email\":\" case@example.test \",\"password\":\"bbbbbbbbbbbb\"}")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("EMAIL_UNAVAILABLE"));

        Client login = anonymous();
        login.unsafe(post("/api/auth/login"), "{\"email\":\" CASE@example.test \",\"password\":\"aaaaaaaaaaaa\"}")
                .andExpect(status().isNoContent());

        String wrongExisting = anonymous().unsafe(post("/api/auth/login"),
                        "{\"email\":\"case@example.test\",\"password\":\"cccccccccccc\"}")
                .andExpect(status().isUnauthorized()).andReturn().getResponse().getContentAsString();
        String unknown = anonymous().unsafe(post("/api/auth/login"),
                        "{\"email\":\"unknown@example.test\",\"password\":\"cccccccccccc\"}")
                .andExpect(status().isUnauthorized()).andReturn().getResponse().getContentAsString();
        org.junit.jupiter.api.Assertions.assertEquals(wrongExisting, unknown);
    }

    @Test
    void loginLogoutAndPasswordChangeUseOnlyTheActivePrincipalSession() throws Exception {
        Client first = register("first@example.test", "aaaaaaaaaaaa");
        Client second = register("second@example.test", "bbbbbbbbbbbb");

        mockMvc.perform(get("/api/profile").session(first.session).cookie(first.csrf))
                .andExpect(status().isOk()).andExpect(jsonPath("$.email").value("first@example.test"));
        mockMvc.perform(get("/api/profile").session(second.session).cookie(second.csrf))
                .andExpect(status().isOk()).andExpect(jsonPath("$.email").value("second@example.test"));

        first.unsafe(put("/api/profile/password"),
                        "{\"currentPassword\":\"bbbbbbbbbbbb\",\"newPassword\":\"cccccccccccc\"}")
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("CURRENT_PASSWORD_INVALID"));
        first.unsafe(put("/api/profile/password"),
                        "{\"currentPassword\":\"aaaaaaaaaaaa\",\"newPassword\":\"cccccccccccc\"}")
                .andExpect(status().isNoContent());

        anonymous().unsafe(post("/api/auth/login"), "{\"email\":\"first@example.test\",\"password\":\"aaaaaaaaaaaa\"}")
                .andExpect(status().isUnauthorized()).andExpect(jsonPath("$.code").value("AUTHENTICATION_FAILED"));
        anonymous().unsafe(post("/api/auth/login"), "{\"email\":\"first@example.test\",\"password\":\"cccccccccccc\"}")
                .andExpect(status().isNoContent());

        first.unsafe(post("/api/auth/logout"), null).andExpect(status().isNoContent());
        mockMvc.perform(get("/api/profile").session(first.session).cookie(first.csrf))
                .andExpect(status().isUnauthorized()).andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));
        mockMvc.perform(get("/api/profile").session(second.session).cookie(second.csrf))
                .andExpect(status().isOk()).andExpect(jsonPath("$.email").value("second@example.test"));
    }

    @Test
    void requiresCsrfAndReturnsSafeJsonForProtectedAndMalformedRequests() throws Exception {
        mockMvc.perform(get("/"))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/profile"))
                .andExpect(status().isUnauthorized()).andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));
        mockMvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"blocked@example.test\",\"password\":\"aaaaaaaaaaaa\"}"))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("CSRF_INVALID"));

        Client client = anonymous();
        client.unsafe(post("/api/auth/register"), "{not-json")
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("MALFORMED_REQUEST"))
                .andExpect(content().string(not(containsString("not-json"))));
    }

    @Test
    void servesOnlyTheProfileDocumentFallbackWhileKeepingProfileDataProtected() throws Exception {
        mockMvc.perform(get("/profile"))
                .andExpect(status().isOk())
                .andExpect(forwardedUrl("/index.html"));
        mockMvc.perform(get("/api/profile"))
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));
        mockMvc.perform(get("/not-a-client-route"))
                .andExpect(status().isUnauthorized());
        anonymous().unsafe(post("/profile"), null)
                .andExpect(status().isUnauthorized());
    }

    private void assertRegistrationStatus(String email, String password, int expectedStatus) throws Exception {
        anonymous().unsafe(post("/api/auth/register"), "{\"email\":\"" + email + "\",\"password\":\"" + password + "\"}")
                .andExpect(status().is(expectedStatus));
    }

    private Client register(String email, String password) throws Exception {
        Client client = anonymous();
        MvcResult result = client.unsafe(post("/api/auth/register"),
                        "{\"email\":\"" + email + "\",\"password\":\"" + password + "\"}")
                .andExpect(status().isCreated()).andReturn();
        client.session = (MockHttpSession) result.getRequest().getSession(false);
        return client;
    }

    private Client anonymous() throws Exception {
        MvcResult result = mockMvc.perform(get("/")).andExpect(status().isOk()).andReturn();
        Cookie csrf = result.getResponse().getCookie("XSRF-TOKEN");
        org.junit.jupiter.api.Assertions.assertNotNull(csrf, "Public shell must bootstrap CSRF cookie");
        return new Client(csrf);
    }

    private final class Client {
        private MockHttpSession session;
        private final Cookie csrf;

        private Client(Cookie csrf) {
            this.csrf = csrf;
        }

        private org.springframework.test.web.servlet.ResultActions unsafe(
                org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder request, String body) throws Exception {
            request.cookie(csrf).header("X-XSRF-TOKEN", csrf.getValue()).contentType(MediaType.APPLICATION_JSON);
            if (session != null) {
                request.session(session);
            }
            if (body != null) {
                request.content(body);
            }
            return mockMvc.perform(request);
        }
    }
}
