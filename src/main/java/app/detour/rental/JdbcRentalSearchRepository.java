package app.detour.rental;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class JdbcRentalSearchRepository implements RentalSearchRepository {
    private final JdbcTemplate jdbc;

    public JdbcRentalSearchRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public List<RentalCandidate> findAvailableUnits(long destinationId, OffsetDateTime pickupAt, OffsetDateTime returnAt) {
        String sql = """
                SELECT u.id AS unit_id,
                       u.catalog_key AS unit_catalog_key,
                       u.unit_identifier,
                       c.id AS vehicle_class_id,
                       c.catalog_key AS vehicle_class_catalog_key,
                       c.name AS vehicle_class_name,
                       c.vehicle_category,
                       c.daily_base_price_cents,
                       c.daily_tax_cents,
                       c.daily_fee_cents,
                       loc.id AS location_id,
                       loc.catalog_key AS location_catalog_key,
                       loc.name AS location_name,
                       loc.destination_id,
                       loc.airport_id,
                       a.iata_code AS airport_iata_code
                FROM rental_unit u
                JOIN rental_vehicle_class c ON c.id = u.rental_vehicle_class_id
                JOIN rental_location loc ON loc.id = c.rental_location_id
                JOIN catalog_airport a ON a.id = loc.airport_id
                WHERE loc.destination_id = ?
                  AND NOT EXISTS (
                      SELECT 1
                      FROM rental_unit_occupancy occ
                      WHERE occ.rental_unit_id = u.id
                        AND occ.occupancy_status = 'ACTIVE'
                        AND occ.pickup_at < ?
                        AND ? < occ.return_at
                  )
                ORDER BY c.vehicle_category ASC, u.catalog_key ASC
                """;
        return jdbc.query(sql, this::mapCandidate, destinationId, returnAt, pickupAt);
    }

    @Override
    public Optional<RentalCandidate> findUnitById(long rentalUnitId) {
        String sql = """
                SELECT u.id AS unit_id,
                       u.catalog_key AS unit_catalog_key,
                       u.unit_identifier,
                       c.id AS vehicle_class_id,
                       c.catalog_key AS vehicle_class_catalog_key,
                       c.name AS vehicle_class_name,
                       c.vehicle_category,
                       c.daily_base_price_cents,
                       c.daily_tax_cents,
                       c.daily_fee_cents,
                       loc.id AS location_id,
                       loc.catalog_key AS location_catalog_key,
                       loc.name AS location_name,
                       loc.destination_id,
                       loc.airport_id,
                       a.iata_code AS airport_iata_code
                FROM rental_unit u
                JOIN rental_vehicle_class c ON c.id = u.rental_vehicle_class_id
                JOIN rental_location loc ON loc.id = c.rental_location_id
                JOIN catalog_airport a ON a.id = loc.airport_id
                WHERE u.id = ?
                """;
        return jdbc.query(sql, this::mapCandidate, rentalUnitId).stream().findFirst();
    }

    @Override
    public boolean isUnitAvailable(long rentalUnitId, OffsetDateTime pickupAt, OffsetDateTime returnAt) {
        String sql = """
                SELECT COUNT(*)
                FROM rental_unit_occupancy occ
                WHERE occ.rental_unit_id = ?
                  AND occ.occupancy_status = 'ACTIVE'
                  AND occ.pickup_at < ?
                  AND ? < occ.return_at
                """;
        Integer count = jdbc.queryForObject(sql, Integer.class, rentalUnitId, returnAt, pickupAt);
        return count != null && count == 0;
    }

    @Override
    public Optional<String> findDestinationAirportTimeZone(long destinationId) {
        String sql = """
                SELECT a.time_zone_id
                FROM rental_location loc
                JOIN catalog_airport a ON a.id = loc.airport_id
                WHERE loc.destination_id = ?
                """;
        return jdbc.query(sql, (rs, rowNum) -> rs.getString("time_zone_id"), destinationId).stream().findFirst();
    }

    private RentalCandidate mapCandidate(ResultSet rs, int rowNum) throws SQLException {
        return new RentalCandidate(
                rs.getLong("unit_id"),
                rs.getString("unit_catalog_key"),
                rs.getString("unit_identifier"),
                rs.getLong("vehicle_class_id"),
                rs.getString("vehicle_class_catalog_key"),
                rs.getString("vehicle_class_name"),
                rs.getString("vehicle_category"),
                rs.getLong("daily_base_price_cents"),
                rs.getLong("daily_tax_cents"),
                rs.getLong("daily_fee_cents"),
                rs.getLong("location_id"),
                rs.getString("location_catalog_key"),
                rs.getString("location_name"),
                rs.getLong("destination_id"),
                rs.getLong("airport_id"),
                rs.getString("airport_iata_code")
        );
    }
}
