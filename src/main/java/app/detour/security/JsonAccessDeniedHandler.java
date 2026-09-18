package app.detour.security;

import tools.jackson.databind.json.JsonMapper;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.stereotype.Component;

@Component
class JsonAccessDeniedHandler implements AccessDeniedHandler {
    private final JsonMapper objectMapper;

    JsonAccessDeniedHandler(JsonMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public void handle(HttpServletRequest request, HttpServletResponse response, AccessDeniedException exception)
            throws IOException, ServletException {
        JsonSecurityErrorWriter.write(response, objectMapper, HttpStatus.FORBIDDEN.value(), "CSRF_INVALID",
                "A valid CSRF token is required.");
    }
}
