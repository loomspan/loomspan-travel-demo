package app.detour.security;

import tools.jackson.databind.json.JsonMapper;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import app.detour.api.ApiError;
import org.springframework.http.MediaType;

final class JsonSecurityErrorWriter {
    private JsonSecurityErrorWriter() {
    }

    static void write(HttpServletResponse response, JsonMapper objectMapper, int status, String code, String message)
            throws IOException {
        response.setStatus(status);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        objectMapper.writeValue(response.getOutputStream(), ApiError.of(code, message));
    }
}
