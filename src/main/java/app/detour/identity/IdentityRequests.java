package app.detour.identity;

public final class IdentityRequests {
    private IdentityRequests() {
    }

    public record Registration(String email, String password) {
        @Override
        public String toString() {
            return "Registration[redacted]";
        }
    }

    public record Login(String email, String password) {
        @Override
        public String toString() {
            return "Login[redacted]";
        }
    }

    public record PasswordChange(String currentPassword, String newPassword) {
        @Override
        public String toString() {
            return "PasswordChange[redacted]";
        }
    }
}
