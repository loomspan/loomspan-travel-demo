package app.detour.api;

import java.util.Map;

public class ApiException extends RuntimeException {
    private final int status;
    private final String code;
    private final Map<String, String> fields;

    public ApiException(int status, String code, String message) {
        this(status, code, message, Map.of());
    }

    public ApiException(int status, String code, String message, Map<String, String> fields) {
        super(message);
        this.status = status;
        this.code = code;
        this.fields = fields;
    }

    public int status() {
        return status;
    }

    public String code() {
        return code;
    }

    public Map<String, String> fields() {
        return fields;
    }
}
