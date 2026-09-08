package demo.wayfarer;

public class ApiProblem extends RuntimeException {
    final int status;
    final String code;
    public ApiProblem(int status, String code, String message) { super(message); this.status = status; this.code = code; }
    static ApiProblem invalid(String message) { return new ApiProblem(400, "INVALID_REQUEST", message); }
    static ApiProblem conflict(String message) { return new ApiProblem(409, "CONFLICT", message); }
    static ApiProblem missing() { return new ApiProblem(404, "NOT_FOUND", "The requested trip or assessment was not found."); }
}
