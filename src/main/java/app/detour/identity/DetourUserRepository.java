package app.detour.identity;

import java.util.Optional;

public interface DetourUserRepository {
    DetourUser create(String canonicalEmail, String passwordHash);

    Optional<DetourUser> findByCanonicalEmail(String canonicalEmail);

    Optional<DetourUser> findById(long id);

    boolean updatePassword(long id, String passwordHash);
}
