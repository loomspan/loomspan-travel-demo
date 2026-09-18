package app.detour.catalog.persistence;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.Objects;
import org.h2.api.Trigger;

/** Prevents a retained catalog identifier from changing after its row is created. */
public final class ImmutableCatalogKeyTrigger implements Trigger {
    private static final int CATALOG_KEY = 1;

    @Override
    public void init(Connection connection, String schemaName, String triggerName, String tableName,
            boolean before, int type) {
        // The catalog-key column is the second column on every registered catalog table.
    }

    @Override
    public void fire(Connection connection, Object[] oldRow, Object[] newRow) throws SQLException {
        if (!Objects.equals(oldRow[CATALOG_KEY], newRow[CATALOG_KEY])) {
            throw new SQLException("Catalog keys are immutable once created", "23514");
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
