package app.detour.trip;

import java.time.OffsetDateTime;
import org.springframework.jdbc.core.JdbcTemplate;

/** Converts the existing catalog-ID fixtures into fully described canonical snapshots. */
public final class TestPlanSelections {
    private TestPlanSelections() { }
    public static int insert(JdbcTemplate jdbc, String sql, Object... args) {
        int values = sql.indexOf("VALUES");
        String expression = sql.substring(values + 6).trim();
        String query = "SELECT " + expression.substring(1, expression.lastIndexOf(')'));
        query = query.replace("detour_trip_draft WHERE", "detour_planned_itinerary WHERE");
        Object[] row = jdbc.queryForObject(query, (r, n) -> {
            Object[] result = new Object[r.getMetaData().getColumnCount()];
            for (int i = 0; i < result.length; i++) result[i] = r.getObject(i + 1);
            return result;
        }, args);
        long id = ((Number) row[0]).longValue();
        JdbcTripRepository repository = new JdbcTripRepository(jdbc);
        if (sql.contains("airfare")) repository.saveDraftAirfareSelection(id, ((Number) row[1]).longValue(), ((Number) row[2]).longValue());
        else if (sql.contains("stay")) repository.saveDraftStaySelection(id, ((Number) row[1]).longValue(), ((Number) row[2]).intValue());
        else repository.saveDraftRentalSelection(id, ((Number) row[1]).longValue(), (OffsetDateTime) row[2], (OffsetDateTime) row[3]);
        return 1;
    }
}
