package app.detour.trip;

import java.sql.PreparedStatement;
import java.sql.Statement;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;

@Repository
class JdbcTripRepository implements TripRepository {
    private static final List<String> SUPPORTED_DESTINATION_KEYS = List.of("destination-sfo", "destination-muc", "destination-mex");
    private final JdbcTemplate jdbcTemplate;

    JdbcTripRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public Optional<Destination> findSupportedDestination(String key) {
        if (!SUPPORTED_DESTINATION_KEYS.contains(key)) {
            return Optional.empty();
        }
        return jdbcTemplate.query("SELECT id, catalog_key, name FROM catalog_destination WHERE catalog_key = ?",
                (row, ignored) -> new Destination(row.getLong("id"), row.getString("catalog_key"), row.getString("name")), key)
                .stream().findFirst();
    }

    @Override
    public void createAggregate(long ownerUserId, UUID tripPublicId, Destination destination, LocalDate startDate,
            LocalDate endDate, int travelerCount, List<Integer> travelerAges, Long budgetCents, String label,
            UUID draftPublicId) {
        KeyHolder keys = new GeneratedKeyHolder();
        jdbcTemplate.update(connection -> {
            PreparedStatement statement = connection.prepareStatement("""
                    INSERT INTO detour_trip (public_id, owner_user_id, catalog_destination_id, start_date, end_date,
                        traveler_count, budget_cents, display_label, version)
                    VALUES (?, ?, ?, ?, ?, ?, ?, ?, 0)
                    """, Statement.RETURN_GENERATED_KEYS);
            statement.setObject(1, tripPublicId);
            statement.setLong(2, ownerUserId);
            statement.setLong(3, destination.id());
            statement.setObject(4, startDate);
            statement.setObject(5, endDate);
            statement.setInt(6, travelerCount);
            if (budgetCents == null) statement.setNull(7, java.sql.Types.BIGINT); else statement.setLong(7, budgetCents);
            statement.setString(8, label);
            return statement;
        }, keys);
        Number key = keys.getKeys() == null ? null : (Number) keys.getKeys().get("ID");
        if (key == null) throw new IllegalStateException("Trip insert did not return a key");
        long tripId = key.longValue();
        for (int ordinal = 0; ordinal < travelerCount; ordinal++) {
            Integer age = travelerAges.get(ordinal);
            jdbcTemplate.update("INSERT INTO detour_trip_traveler (trip_id, traveler_ordinal, age) VALUES (?, ?, ?)",
                    tripId, ordinal + 1, age);
        }
        jdbcTemplate.update("INSERT INTO detour_trip_draft (public_id, trip_id, version) VALUES (?, ?, 0)", draftPublicId, tripId);
    }

    @Override
    public Optional<Trip> findByPublicIdAndOwnerUserId(UUID publicId, long ownerUserId) {
        return jdbcTemplate.query("""
                SELECT trip.id, trip.public_id, trip.owner_user_id, destination.id AS destination_id,
                    destination.catalog_key, destination.name AS destination_name, trip.start_date, trip.end_date,
                    trip.traveler_count, trip.budget_cents, trip.display_label, trip.version
                FROM detour_trip trip
                JOIN catalog_destination destination ON destination.id = trip.catalog_destination_id
                WHERE trip.public_id = ? AND trip.owner_user_id = ?
                """, (row, ignored) -> {
                    long tripId = row.getLong("id");
                    List<Integer> ages = jdbcTemplate.query("SELECT age FROM detour_trip_traveler WHERE trip_id = ? ORDER BY traveler_ordinal",
                            (traveler, travelerRow) -> (Integer) traveler.getObject("age"), tripId);
                    boolean agesKnown = ages.stream().allMatch(java.util.Objects::nonNull);
                    List<TripDraft> drafts = jdbcTemplate.query("SELECT id, public_id, version FROM detour_trip_draft WHERE trip_id = ? ORDER BY id",
                            (draft, draftRow) -> new TripDraft(draft.getLong("id"), draft.getObject("public_id", UUID.class), draft.getLong("version")), tripId);
                    Long budget = (Long) row.getObject("budget_cents");
                    return new Trip(tripId, row.getObject("public_id", UUID.class), row.getLong("owner_user_id"),
                            new Destination(row.getLong("destination_id"), row.getString("catalog_key"), row.getString("destination_name")),
                            row.getObject("start_date", LocalDate.class), row.getObject("end_date", LocalDate.class),
                            row.getInt("traveler_count"), agesKnown ? List.copyOf(ages) : null, budget,
                            row.getString("display_label"), row.getLong("version"), drafts);
                }, publicId, ownerUserId).stream().findFirst();
    }

    @Override
    public boolean advanceVersion(long tripId, long ownerUserId, long expectedVersion) {
        return jdbcTemplate.update("UPDATE detour_trip SET version = version + 1 WHERE id = ? AND owner_user_id = ? AND version = ?",
                tripId, ownerUserId, expectedVersion) == 1;
    }

    @Override
    public boolean advanceVersionForDraft(long tripId, long ownerUserId, long expectedVersion, long draftId,
            long expectedDraftVersion) {
        return jdbcTemplate.update("""
                UPDATE detour_trip SET version = version + 1
                WHERE id = ? AND owner_user_id = ? AND version = ?
                  AND EXISTS (SELECT 1 FROM detour_trip_draft
                              WHERE id = ? AND trip_id = ? AND version = ?)
                """, tripId, ownerUserId, expectedVersion, draftId, tripId, expectedDraftVersion) == 1;
    }

    @Override
    public void replaceSharedDetails(long tripId, Destination destination, LocalDate startDate, LocalDate endDate,
            int travelerCount, List<Integer> travelerAges, Long budgetCents, String label) {
        jdbcTemplate.update("UPDATE detour_trip SET catalog_destination_id = ?, start_date = ?, end_date = ?, traveler_count = ?, budget_cents = ?, display_label = ? WHERE id = ?",
                destination.id(), startDate, endDate, travelerCount, budgetCents, label, tripId);
        jdbcTemplate.update("DELETE FROM detour_trip_traveler WHERE trip_id = ?", tripId);
        for (int ordinal = 0; ordinal < travelerCount; ordinal++) {
            jdbcTemplate.update("INSERT INTO detour_trip_traveler (trip_id, traveler_ordinal, age) VALUES (?, ?, ?)",
                    tripId, ordinal + 1, travelerAges.get(ordinal));
        }
    }

    @Override
    public void insertDraft(long tripId, UUID publicId) {
        jdbcTemplate.update("INSERT INTO detour_trip_draft (public_id, trip_id, version) VALUES (?, ?, 0)", publicId, tripId);
    }

    @Override
    public void deleteDraft(long tripId, long draftId) {
        jdbcTemplate.update("DELETE FROM detour_trip_draft WHERE trip_id = ? AND id = ?", tripId, draftId);
    }
}
