package app.detour.identity;

import app.detour.api.ApiException;
import java.util.Map;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class IdentityService {
    private final DetourUserRepository users;
    private final PasswordEncoder passwordEncoder;

    IdentityService(DetourUserRepository users, PasswordEncoder passwordEncoder) {
        this.users = users;
        this.passwordEncoder = passwordEncoder;
    }

    @Transactional
    public DetourUserPrincipal register(IdentityRequests.Registration request) {
        String email = validEmail(request == null ? null : request.email());
        String password = validPassword(request == null ? null : request.password(), "password");
        try {
            DetourUser user = users.create(email, passwordEncoder.encode(password));
            return principal(user);
        } catch (DuplicateKeyException exception) {
            throw new ApiException(409, "EMAIL_UNAVAILABLE", "An account cannot be created with that email.");
        }
    }

    public DetourUserPrincipal authenticate(IdentityRequests.Login request) {
        String email = EmailCanonicalizer.canonicalize(request == null ? null : request.email());
        String password = request == null ? null : request.password();
        if (!EmailCanonicalizer.isAccepted(email) || !PasswordPolicy.isAccepted(password)) {
            throw authenticationFailed();
        }
        return users.findByCanonicalEmail(email)
                .filter(user -> passwordEncoder.matches(password, user.passwordHash()))
                .map(this::principal)
                .orElseThrow(this::authenticationFailed);
    }

    public ProfileResponse profile(long userId) {
        DetourUser user = users.findById(userId)
                .orElseThrow(() -> new ApiException(404, "RESOURCE_NOT_FOUND", "The requested resource was not found."));
        return new ProfileResponse(user.canonicalEmail());
    }

    @Transactional
    public void changePassword(long userId, IdentityRequests.PasswordChange request) {
        String newPassword = validPassword(request == null ? null : request.newPassword(), "newPassword");
        String currentPassword = request == null ? null : request.currentPassword();
        DetourUser user = users.findById(userId)
                .orElseThrow(() -> new ApiException(404, "RESOURCE_NOT_FOUND", "The requested resource was not found."));
        if (currentPassword == null || !passwordEncoder.matches(currentPassword, user.passwordHash())) {
            throw new ApiException(400, "CURRENT_PASSWORD_INVALID", "The current password could not be verified.");
        }
        if (!users.updatePassword(user.id(), passwordEncoder.encode(newPassword))) {
            throw new ApiException(404, "RESOURCE_NOT_FOUND", "The requested resource was not found.");
        }
    }

    private static String validEmail(String input) {
        String email = EmailCanonicalizer.canonicalize(input);
        if (!EmailCanonicalizer.isAccepted(email)) {
            throw validation("email", "Enter a valid email address.");
        }
        return email;
    }

    private static String validPassword(String password, String field) {
        if (!PasswordPolicy.isAccepted(password)) {
            throw validation(field, "Password must contain 12 to 128 characters.");
        }
        return password;
    }

    private static ApiException validation(String field, String message) {
        return new ApiException(400, "VALIDATION_FAILED", "One or more fields are invalid.", Map.of(field, message));
    }

    private ApiException authenticationFailed() {
        return new ApiException(401, "AUTHENTICATION_FAILED", "Email or password is incorrect.");
    }

    private DetourUserPrincipal principal(DetourUser user) {
        return new DetourUserPrincipal(user.id(), user.canonicalEmail());
    }
}
