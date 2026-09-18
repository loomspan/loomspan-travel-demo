package app.detour.catalog.persistence;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import org.h2.api.Trigger;

/** Preserves valid direct and one-stop segment shape for schedules and dated instances. */
public final class FlightSegmentIntegrityTrigger implements Trigger {
    private static final int ID = 0;
    private static final int PARENT_ID = 1;
    private static final int SEGMENT_ORDINAL = 2;

    private String tableName;

    @Override
    public void init(Connection connection, String schemaName, String triggerName, String tableName,
            boolean before, int type) {
        this.tableName = tableName;
    }

    @Override
    public void fire(Connection connection, Object[] oldRow, Object[] newRow) throws SQLException {
        if ("FLIGHT_SCHEDULE_SEGMENT".equalsIgnoreCase(tableName)) {
            if (newRow == null) {
                validateScheduleSegmentDeletion(connection, oldRow);
            } else {
                validateScheduleSegment(connection, oldRow, newRow);
            }
        } else if ("FLIGHT_SCHEDULE".equalsIgnoreCase(tableName)) {
            validateScheduleRouteUpdate(connection, newRow);
        } else if ("FLIGHT_INSTANCE_SEGMENT".equalsIgnoreCase(tableName)) {
            if (newRow == null) {
                validateInstanceSegmentDeletion(connection, oldRow);
            } else {
                validateInstanceSegment(connection, oldRow, newRow);
            }
        } else {
            throw new SQLException("Unsupported flight segment trigger table: " + tableName);
        }
    }

    private void validateScheduleSegment(Connection connection, Object[] oldRow, Object[] newRow) throws SQLException {
        long scheduleId = ((Number) newRow[PARENT_ID]).longValue();
        int ordinal = ((Number) newRow[SEGMENT_ORDINAL]).intValue();
        long originAirportId = ((Number) newRow[3]).longValue();
        long destinationAirportId = ((Number) newRow[4]).longValue();
        try (PreparedStatement statement = connection.prepareStatement("""
                SELECT segment_count, origin_airport_id, destination_airport_id
                FROM flight_schedule
                WHERE id = ?
                """)) {
            statement.setLong(1, scheduleId);
            try (ResultSet rows = statement.executeQuery()) {
                if (!rows.next()) {
                    throw new SQLException("Flight schedule segment references no schedule", "23503");
                }
                int segmentCount = rows.getInt(1);
                long scheduleOriginId = rows.getLong(2);
                long scheduleDestinationId = rows.getLong(3);
                if (ordinal > segmentCount || (ordinal == 1 && originAirportId != scheduleOriginId)
                        || (ordinal == segmentCount && destinationAirportId != scheduleDestinationId)) {
                    throw new SQLException("Flight schedule segment does not match the direct/one-stop route", "23514");
                }
            }
        }
        if (ordinal == 2) {
            try (PreparedStatement statement = connection.prepareStatement("""
                    SELECT destination_airport_id
                    FROM flight_schedule_segment
                    WHERE flight_schedule_id = ? AND segment_ordinal = 1 AND id <> ?
                    """)) {
                statement.setLong(1, scheduleId);
                statement.setObject(2, rowId(oldRow, newRow));
                try (ResultSet rows = statement.executeQuery()) {
                    if (!rows.next() || rows.getLong(1) != originAirportId) {
                        throw new SQLException("One-stop flight schedule segments must connect", "23514");
                    }
                }
            }
        } else {
            try (PreparedStatement statement = connection.prepareStatement("""
                    SELECT origin_airport_id
                    FROM flight_schedule_segment
                    WHERE flight_schedule_id = ? AND segment_ordinal = 2 AND id <> ?
                    """)) {
                statement.setLong(1, scheduleId);
                statement.setObject(2, rowId(oldRow, newRow));
                try (ResultSet rows = statement.executeQuery()) {
                    if (rows.next() && rows.getLong(1) != destinationAirportId) {
                        throw new SQLException("One-stop flight schedule segments must connect", "23514");
                    }
                }
            }
        }
    }

    private void validateScheduleRouteUpdate(Connection connection, Object[] newRow) throws SQLException {
        long scheduleId = ((Number) newRow[ID]).longValue();
        long originAirportId = ((Number) newRow[5]).longValue();
        long destinationAirportId = ((Number) newRow[6]).longValue();
        int segmentCount = ((Number) newRow[8]).intValue();
        try (PreparedStatement statement = connection.prepareStatement("""
                SELECT segment_ordinal, origin_airport_id, destination_airport_id
                FROM flight_schedule_segment
                WHERE flight_schedule_id = ?
                """)) {
            statement.setLong(1, scheduleId);
            try (ResultSet rows = statement.executeQuery()) {
                int actualSegmentCount = 0;
                while (rows.next()) {
                    actualSegmentCount++;
                    int ordinal = rows.getInt(1);
                    if ((ordinal == 1 && rows.getLong(2) != originAirportId)
                            || (ordinal == segmentCount
                            && rows.getLong(3) != destinationAirportId)) {
                        throw new SQLException("Flight schedule route must match its segment endpoints", "23514");
                    }
                }
                if (actualSegmentCount != 0 && actualSegmentCount != segmentCount) {
                    throw new SQLException("Flight schedule segment count must match its defined segments", "23514");
                }
            }
        }
    }

    private void validateScheduleSegmentDeletion(Connection connection, Object[] oldRow) throws SQLException {
        validateDeletionPreservesCompletedShape(connection, "flight_schedule_segment", "flight_schedule_id",
                ((Number) oldRow[PARENT_ID]).longValue(), "flight_schedule", "segment_count");
    }

    private void validateInstanceSegment(Connection connection, Object[] oldRow, Object[] newRow) throws SQLException {
        long instanceId = ((Number) newRow[PARENT_ID]).longValue();
        int ordinal = ((Number) newRow[SEGMENT_ORDINAL]).intValue();
        OffsetDateTime departureAt = (OffsetDateTime) newRow[3];
        OffsetDateTime arrivalAt = (OffsetDateTime) newRow[4];
        try (PreparedStatement statement = connection.prepareStatement("""
                SELECT schedule.segment_count
                FROM flight_instance instance
                JOIN flight_schedule schedule ON schedule.id = instance.flight_schedule_id
                WHERE instance.id = ?
                """)) {
            statement.setLong(1, instanceId);
            try (ResultSet rows = statement.executeQuery()) {
                if (!rows.next()) {
                    throw new SQLException("Flight instance segment references no instance", "23503");
                }
                if (ordinal > rows.getInt(1)) {
                    throw new SQLException("Flight instance has too many segments", "23514");
                }
            }
        }
        if (ordinal == 2) {
            try (PreparedStatement statement = connection.prepareStatement("""
                    SELECT arrival_at
                    FROM flight_instance_segment
                    WHERE flight_instance_id = ? AND segment_ordinal = 1 AND id <> ?
                    """)) {
                statement.setLong(1, instanceId);
                statement.setObject(2, rowId(oldRow, newRow));
                try (ResultSet rows = statement.executeQuery()) {
                    if (!rows.next() || departureAt.isBefore(rows.getObject(1, OffsetDateTime.class))) {
                        throw new SQLException("One-stop flight instance segments must have a feasible layover", "23514");
                    }
                }
            }
        } else {
            try (PreparedStatement statement = connection.prepareStatement("""
                    SELECT departure_at
                    FROM flight_instance_segment
                    WHERE flight_instance_id = ? AND segment_ordinal = 2 AND id <> ?
                    """)) {
                statement.setLong(1, instanceId);
                statement.setObject(2, rowId(oldRow, newRow));
                try (ResultSet rows = statement.executeQuery()) {
                    if (rows.next() && arrivalAt.isAfter(rows.getObject(1, OffsetDateTime.class))) {
                        throw new SQLException("One-stop flight instance segments must have a feasible layover", "23514");
                    }
                }
            }
        }
    }

    private void validateInstanceSegmentDeletion(Connection connection, Object[] oldRow) throws SQLException {
        long instanceId = ((Number) oldRow[PARENT_ID]).longValue();
        try (PreparedStatement statement = connection.prepareStatement("""
                SELECT instance.segment_count, COUNT(segment.id)
                FROM flight_instance instance
                LEFT JOIN flight_instance_segment segment ON segment.flight_instance_id = instance.id
                WHERE instance.id = ?
                GROUP BY instance.segment_count
                """)) {
            statement.setLong(1, instanceId);
            try (ResultSet rows = statement.executeQuery()) {
                if (rows.next() && rows.getInt(1) == rows.getInt(2)) {
                    throw new SQLException("Completed flight instance segments cannot be deleted", "23514");
                }
            }
        }
    }

    private void validateDeletionPreservesCompletedShape(Connection connection, String segmentTable, String parentColumn,
            long parentId, String parentTable, String segmentCountColumn) throws SQLException {
        String sql = "SELECT parent." + segmentCountColumn + ", COUNT(segment.id) "
                + "FROM " + parentTable + " parent "
                + "LEFT JOIN " + segmentTable + " segment ON segment." + parentColumn + " = parent.id "
                + "WHERE parent.id = ? GROUP BY parent." + segmentCountColumn;
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setLong(1, parentId);
            try (ResultSet rows = statement.executeQuery()) {
                if (rows.next() && rows.getInt(1) == rows.getInt(2)) {
                    throw new SQLException("Completed flight schedule segments cannot be deleted", "23514");
                }
            }
        }
    }

    private Object rowId(Object[] oldRow, Object[] newRow) {
        return oldRow == null || oldRow[ID] == null ? -1L : oldRow[ID];
    }

    @Override
    public void close() {
        // No resources are retained.
    }

    @Override
    public void remove() {
        // No resources are retained.
    }
}
