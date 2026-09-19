package app.detour.catalog;

import java.io.IOException;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.UUID;
import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import org.flywaydb.core.Flyway;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowCallbackHandler;
import org.springframework.jdbc.datasource.SingleConnectionDataSource;

/** Generates a deterministic reviewer-facing summary from a fresh in-memory fixture catalog. */
public final class CatalogFixtureSummary {
    private CatalogFixtureSummary() {
    }

    public static void main(String[] args) {
        System.out.print(render());
    }

    public static String render() {
        StringBuilder output = new StringBuilder();
        try {
            writeTo(output);
        } catch (IOException exception) {
            throw new IllegalStateException("Could not write catalog fixture summary", exception);
        }
        return output.toString();
    }

    public static void writeTo(Appendable output) throws IOException {
        String url = "jdbc:h2:mem:catalog_fixture_summary_" + UUID.randomUUID();
        SingleConnectionDataSource dataSource = new SingleConnectionDataSource(url, "sa", "", true);
        try {
            migrateQuietly(dataSource);
            JdbcTemplate jdbc = new JdbcTemplate(dataSource);
            line(output, "Phase 2 deterministic catalog fixture summary");
            line(output, "Counts: destinations=" + count(jdbc, "catalog_destination") + ", airports=" + count(jdbc, "catalog_airport")
                    + ", flights=" + count(jdbc, "flight_instance") + ", stay properties=" + count(jdbc, "accommodation_property")
                    + ", stay nights=" + count(jdbc, "accommodation_nightly_inventory") + ", rental units=" + count(jdbc, "rental_unit"));
            line(output, "Flights:");
            jdbc.query("""
                SELECT destination.catalog_key, schedule.stop_count, instance.catalog_key, origin.iata_code, final_destination.iata_code,
                       first_leg.departure_at, last_leg.arrival_at, instance.available_seats,
                       instance.base_fare_cents + instance.tax_cents + instance.fee_cents
                FROM flight_instance instance JOIN flight_schedule schedule ON schedule.id = instance.flight_schedule_id
                JOIN flight_instance_segment first_leg ON first_leg.flight_instance_id = instance.id AND first_leg.segment_ordinal = 1
                JOIN flight_instance_segment last_leg ON last_leg.flight_instance_id = instance.id AND last_leg.segment_ordinal = instance.segment_count
                JOIN flight_schedule_segment first_schedule ON first_schedule.flight_schedule_id = schedule.id AND first_schedule.segment_ordinal = 1
                JOIN flight_schedule_segment last_schedule ON last_schedule.flight_schedule_id = schedule.id AND last_schedule.segment_ordinal = instance.segment_count
                JOIN catalog_airport origin ON origin.id = first_schedule.origin_airport_id
                JOIN catalog_airport final_destination ON final_destination.id = last_schedule.destination_airport_id
                JOIN catalog_destination destination ON destination.id = final_destination.destination_id
                WHERE schedule.catalog_key IN (
                    'airfare-out-sfo-d1', 'airfare-out-sfo-c1',
                    'airfare-out-muc-d1', 'airfare-out-muc-c1',
                    'airfare-out-mex-d1', 'airfare-out-mex-c1'
                ) AND instance.service_date = DATE '2027-03-01'
                ORDER BY destination.catalog_key, schedule.stop_count, instance.catalog_key
                    """, (RowCallbackHandler) rs -> line(output, "  " + rs.getString(1)
                    + (rs.getInt(2) == 0 ? " direct " : " connection ") + rs.getString(3)
                    + " route=" + rs.getString(4) + "-" + rs.getString(5)
                    + " depart=" + rs.getObject(6) + " arrive=" + rs.getObject(7)
                    + " available-seats=" + rs.getInt(8) + " total=" + rs.getLong(9)));
            line(output, "Stays:");
            jdbc.query("""
                SELECT destination.catalog_key, property.property_category, property.catalog_key, property.name, unit.guest_capacity,
                       unit.inventory_capacity, property.guest_rating, property.distance_to_city_center_meters,
                       nightly.base_price_cents + nightly.tax_cents + nightly.fee_cents
                FROM accommodation_property property
                JOIN catalog_destination destination ON destination.id = property.destination_id
                JOIN accommodation_unit unit ON unit.accommodation_property_id = property.id
                JOIN accommodation_nightly_inventory nightly ON nightly.accommodation_unit_id = unit.id AND nightly.night_date = DATE '2027-03-01'
                WHERE property.catalog_key IN (
                    SELECT MIN(candidate.catalog_key) FROM accommodation_property candidate GROUP BY candidate.destination_id, candidate.property_category
                )
                ORDER BY destination.catalog_key, property.property_category
                    """, (RowCallbackHandler) rs -> line(output, "  " + rs.getString(1) + " " + rs.getString(2) + " " + rs.getString(3)
                    + " total=" + rs.getLong(9) + " capacity=" + rs.getInt(5) + " inventory=" + rs.getInt(6)
                    + " rating=" + rs.getBigDecimal(7) + " distance-m=" + rs.getInt(8) + " name=" + rs.getString(4)));
            line(output, "Rentals:");
            jdbc.query("""
                SELECT destination.catalog_key, vehicle_class.vehicle_category, vehicle_class.catalog_key,
                       vehicle_class.daily_base_price_cents + vehicle_class.daily_tax_cents + vehicle_class.daily_fee_cents,
                       COUNT(unit.id), airport.time_zone_id
                FROM rental_vehicle_class vehicle_class
                JOIN rental_location location ON location.id = vehicle_class.rental_location_id
                JOIN catalog_destination destination ON destination.id = location.destination_id
                JOIN catalog_airport airport ON airport.id = location.airport_id
                JOIN rental_unit unit ON unit.rental_vehicle_class_id = vehicle_class.id
                GROUP BY destination.catalog_key, vehicle_class.vehicle_category, vehicle_class.catalog_key,
                         vehicle_class.daily_base_price_cents, vehicle_class.daily_tax_cents, vehicle_class.daily_fee_cents, airport.time_zone_id
                ORDER BY destination.catalog_key, vehicle_class.vehicle_category
                    """, (RowCallbackHandler) rs -> {
                ZoneId zone = ZoneId.of(rs.getString(6));
                LocalDateTime localPickup = LocalDateTime.of(2027, 3, 10, 10, 0);
                LocalDateTime localReturn = LocalDateTime.of(2027, 3, 11, 11, 0);
                line(output, "  " + rs.getString(1) + " " + rs.getString(2) + " " + rs.getString(3)
                        + " daily-total=" + rs.getLong(4) + " units=" + rs.getInt(5)
                        + " local-interval=" + localPickup.atZone(zone).toOffsetDateTime()
                        + " -> " + localReturn.atZone(zone).toOffsetDateTime());
            });
        } finally {
            dataSource.destroy();
        }
    }

    private static void migrateQuietly(SingleConnectionDataSource dataSource) {
        Logger flywayLogger = (Logger) LoggerFactory.getLogger("org.flywaydb");
        Level originalLevel = flywayLogger.getLevel();
        flywayLogger.setLevel(Level.OFF);
        try {
            Flyway.configure().dataSource(dataSource).locations("classpath:db/migration").load().migrate();
        } finally {
            flywayLogger.setLevel(originalLevel);
        }
    }

    private static int count(JdbcTemplate jdbc, String table) {
        return jdbc.queryForObject("SELECT COUNT(*) FROM " + table, Integer.class);
    }

    private static void line(Appendable output, String line) {
        try {
            output.append(line).append(System.lineSeparator());
        } catch (IOException exception) {
            throw new IllegalStateException("Could not write catalog fixture summary", exception);
        }
    }
}
