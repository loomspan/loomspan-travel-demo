package app.detour.identity;

import java.sql.PreparedStatement;
import java.sql.Statement;
import java.time.OffsetDateTime;
import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;

@Repository
class JdbcDetourUserRepository implements DetourUserRepository {
    private final JdbcTemplate jdbcTemplate;

    JdbcDetourUserRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public DetourUser create(String canonicalEmail, String passwordHash) {
        KeyHolder keys = new GeneratedKeyHolder();
        jdbcTemplate.update(connection -> {
            PreparedStatement statement = connection.prepareStatement(
                    "INSERT INTO detour_user (canonical_email, password_hash) VALUES (?, ?)",
                    Statement.RETURN_GENERATED_KEYS);
            statement.setString(1, canonicalEmail);
            statement.setString(2, passwordHash);
            return statement;
        }, keys);
        Number key = keys.getKeys() == null ? null : (Number) keys.getKeys().get("ID");
        if (key == null) {
            throw new IllegalStateException("Identity insert did not return a key");
        }
        return findById(key.longValue()).orElseThrow();
    }

    @Override
    public Optional<DetourUser> findByCanonicalEmail(String canonicalEmail) {
        return jdbcTemplate.query("SELECT id, canonical_email, password_hash, created_at FROM detour_user WHERE canonical_email = ?",
                (resultSet, rowNum) -> user(resultSet.getLong("id"), resultSet.getString("canonical_email"),
                        resultSet.getString("password_hash"), resultSet.getObject("created_at", OffsetDateTime.class)),
                canonicalEmail).stream().findFirst();
    }

    @Override
    public Optional<DetourUser> findById(long id) {
        return jdbcTemplate.query("SELECT id, canonical_email, password_hash, created_at FROM detour_user WHERE id = ?",
                (resultSet, rowNum) -> user(resultSet.getLong("id"), resultSet.getString("canonical_email"),
                        resultSet.getString("password_hash"), resultSet.getObject("created_at", OffsetDateTime.class)),
                id).stream().findFirst();
    }

    @Override
    public boolean updatePassword(long id, String passwordHash) {
        return jdbcTemplate.update("UPDATE detour_user SET password_hash = ? WHERE id = ?", passwordHash, id) == 1;
    }

    private static DetourUser user(long id, String email, String hash, OffsetDateTime createdAt) {
        return new DetourUser(id, email, hash, createdAt);
    }
}
