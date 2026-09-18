package app.detour.identity;

import java.time.OffsetDateTime;

/** Internal persisted identity. Do not serialize this type in HTTP responses. */
public record DetourUser(long id, String canonicalEmail, String passwordHash, OffsetDateTime createdAt) {
}
