package app.detour.stay;

import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class JdbcStaySearchRepository implements StaySearchRepository {
    private final JdbcTemplate jdbc;

    public JdbcStaySearchRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public List<StayCandidate> findCandidates(long destinationId, AccommodationType type, LocalDate startDate, LocalDate endDate) {
        String sql = """
                SELECT p.id AS property_id,
                       p.catalog_key AS property_catalog_key,
                       p.name AS property_name,
                       p.property_category,
                       p.location_description,
                       p.guest_rating,
                       p.distance_to_city_center_meters,
                       p.latitude,
                       p.longitude,
                       p.destination_id,
                       u.id AS unit_id,
                       u.catalog_key AS unit_catalog_key,
                       u.name AS unit_name,
                       u.unit_kind,
                       u.guest_capacity,
                       u.inventory_capacity
                FROM accommodation_property p
                JOIN accommodation_unit u ON u.accommodation_property_id = p.id
                WHERE p.destination_id = ? AND p.property_category = ?
                ORDER BY p.id ASC, u.id ASC
                """;

        return jdbc.query(sql, (rs, rowNum) -> mapCandidate(rs, startDate, endDate), destinationId, type.name());
    }

    @Override
    public Optional<StayCandidate> findCandidateById(long accommodationUnitId, LocalDate startDate, LocalDate endDate) {
        String sql = """
                SELECT p.id AS property_id,
                       p.catalog_key AS property_catalog_key,
                       p.name AS property_name,
                       p.property_category,
                       p.location_description,
                       p.guest_rating,
                       p.distance_to_city_center_meters,
                       p.latitude,
                       p.longitude,
                       p.destination_id,
                       u.id AS unit_id,
                       u.catalog_key AS unit_catalog_key,
                       u.name AS unit_name,
                       u.unit_kind,
                       u.guest_capacity,
                       u.inventory_capacity
                FROM accommodation_property p
                JOIN accommodation_unit u ON u.accommodation_property_id = p.id
                WHERE u.id = ?
                """;

        return jdbc.query(sql, (rs, rowNum) -> mapCandidate(rs, startDate, endDate), accommodationUnitId).stream().findFirst();
    }

    @Override
    public Optional<Long> findRentalDailyTotal(long rentalUnitId) {
        String sql = """
                SELECT c.daily_base_price_cents + c.daily_tax_cents + c.daily_fee_cents AS daily_total
                FROM rental_unit u
                JOIN rental_vehicle_class c ON c.id = u.rental_vehicle_class_id
                WHERE u.id = ?
                """;
        return jdbc.query(sql, (rs, rowNum) -> rs.getLong("daily_total"), rentalUnitId).stream().findFirst();
    }

    private StayCandidate mapCandidate(ResultSet rs, LocalDate startDate, LocalDate endDate) throws SQLException {
        long unitId = rs.getLong("unit_id");
        List<StayCandidate.CandidateNight> nights = loadNights(unitId, startDate, endDate);

        return new StayCandidate(
                rs.getLong("property_id"),
                rs.getString("property_catalog_key"),
                rs.getString("property_name"),
                rs.getString("property_category"),
                rs.getString("location_description"),
                rs.getBigDecimal("guest_rating"),
                rs.getInt("distance_to_city_center_meters"),
                rs.getBigDecimal("latitude"),
                rs.getBigDecimal("longitude"),
                rs.getLong("destination_id"),
                unitId,
                rs.getString("unit_catalog_key"),
                rs.getString("unit_name"),
                rs.getString("unit_kind"),
                rs.getInt("guest_capacity"),
                rs.getInt("inventory_capacity"),
                nights
        );
    }

    private List<StayCandidate.CandidateNight> loadNights(long unitId, LocalDate startDate, LocalDate endDate) {
        if (startDate == null || endDate == null) {
            return List.of();
        }
        String sql = """
                SELECT night_date, available_inventory, base_price_cents, tax_cents, fee_cents
                FROM accommodation_nightly_inventory
                WHERE accommodation_unit_id = ? AND night_date >= ? AND night_date < ?
                ORDER BY night_date ASC
                """;
        return jdbc.query(sql, (rs, rowNum) -> new StayCandidate.CandidateNight(
                rs.getObject("night_date", LocalDate.class),
                rs.getInt("available_inventory"),
                rs.getLong("base_price_cents"),
                rs.getLong("tax_cents"),
                rs.getLong("fee_cents")
        ), unitId, startDate, endDate);
    }
}
