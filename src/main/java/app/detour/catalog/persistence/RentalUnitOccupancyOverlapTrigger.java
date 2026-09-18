package app.detour.catalog.persistence;

import java.sql.Connection;
import java.sql.SQLException;
import org.h2.api.Trigger;

/** Enforces half-open active occupancy exclusion for one physical rental unit. */
public final class RentalUnitOccupancyOverlapTrigger implements Trigger {
    private static final int ID = 0;
    private static final int RENTAL_UNIT_ID = 1;
    private static final int PICKUP_AT = 2;
    private static final int RETURN_AT = 3;
    private static final int OCCUPANCY_STATUS = 4;

    @Override
    public void init(Connection connection, String schemaName, String triggerName, String tableName,
            boolean before, int type) {
        // All state is held in the database; no trigger instance state is needed.
    }

    @Override
    public void fire(Connection connection, Object[] oldRow, Object[] newRow) throws SQLException {
        if (!"ACTIVE".equals(newRow[OCCUPANCY_STATUS])) {
            return;
        }
        try (var unitLock = connection.prepareStatement("SELECT id FROM rental_unit WHERE id = ? FOR UPDATE")) {
            unitLock.setObject(1, newRow[RENTAL_UNIT_ID]);
            unitLock.executeQuery();
        }
        try (var overlap = connection.prepareStatement("""
                SELECT 1
                FROM rental_unit_occupancy
                WHERE rental_unit_id = ?
                  AND occupancy_status = 'ACTIVE'
                  AND id <> ?
                  AND pickup_at < ?
                  AND ? < return_at
                """)) {
            overlap.setObject(1, newRow[RENTAL_UNIT_ID]);
            overlap.setObject(2, newRow[ID] == null ? -1L : newRow[ID]);
            overlap.setObject(3, newRow[RETURN_AT]);
            overlap.setObject(4, newRow[PICKUP_AT]);
            try (var rows = overlap.executeQuery()) {
                if (rows.next()) {
                    throw new SQLException("Active rental-unit occupancy overlaps an existing active interval", "23505");
                }
            }
        }
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
