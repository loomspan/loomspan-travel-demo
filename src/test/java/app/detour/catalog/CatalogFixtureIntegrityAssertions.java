package app.detour.catalog;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;

/** Assertions for the complete deterministic Phase 2 fixture catalog. */
final class CatalogFixtureIntegrityAssertions {
    private static final List<String> DESTINATIONS = List.of("sfo", "muc", "mex");
    private static final List<String> STAY_TYPES = List.of("HOTEL", "BED_AND_BREAKFAST", "VACATION_RENTAL");
    private static final List<String> RENTAL_CLASSES = List.of("ECONOMY", "STANDARD", "SUV");

    private CatalogFixtureIntegrityAssertions() {
    }

    static void assertValid(JdbcTemplate jdbc) {
        AirfareFixtureIntegrityAssertions.assertValid(jdbc);
        assertStays(jdbc);
        assertRentals(jdbc);
    }

    private static void assertStays(JdbcTemplate jdbc) {
        assertEquals(18, count(jdbc, "accommodation_property"));
        assertEquals(18, count(jdbc, "accommodation_unit"));
        assertEquals(540, count(jdbc, "accommodation_nightly_inventory"));
        for (String destination : DESTINATIONS) {
            for (String type : STAY_TYPES) {
                assertEquals(2, jdbc.queryForObject("""
                        SELECT COUNT(*) FROM accommodation_property property
                        JOIN catalog_destination destination ON destination.id = property.destination_id
                        WHERE destination.catalog_key = ? AND property.property_category = ?
                        """, Integer.class, "destination-" + destination, type), destination + " " + type + " property count");
            }
        }
        assertEquals(18, jdbc.queryForObject("SELECT COUNT(DISTINCT catalog_key) FROM accommodation_property", Integer.class));
        assertEquals(18, jdbc.queryForObject("SELECT COUNT(DISTINCT catalog_key) FROM accommodation_unit", Integer.class));
        jdbc.query("""
                SELECT property.catalog_key, property.property_category, property.name, property.location_description,
                       property.guest_rating, property.distance_to_city_center_meters, unit.unit_kind, unit.guest_capacity,
                       unit.inventory_capacity, COUNT(nightly.night_date), MIN(nightly.night_date), MAX(nightly.night_date),
                       MIN(nightly.available_inventory), MIN(nightly.base_price_cents + nightly.tax_cents + nightly.fee_cents)
                FROM accommodation_property property
                JOIN accommodation_unit unit ON unit.accommodation_property_id = property.id
                JOIN accommodation_nightly_inventory nightly ON nightly.accommodation_unit_id = unit.id
                GROUP BY property.catalog_key, property.property_category, property.name, property.location_description,
                         property.guest_rating, property.distance_to_city_center_meters, unit.unit_kind, unit.guest_capacity,
                         unit.inventory_capacity
                """, rs -> {
            String key = rs.getString(1);
            String category = rs.getString(2);
            assertFalse(rs.getString(3).isBlank(), key + " name");
            assertFalse(rs.getString(4).isBlank(), key + " location");
            assertTrue(rs.getBigDecimal(5).doubleValue() > 0, key + " rating");
            assertTrue(rs.getInt(6) >= 0, key + " distance");
            assertEquals("VACATION_RENTAL".equals(category) ? "WHOLE_PROPERTY" : "ROOM", rs.getString(7), key + " unit kind");
            assertEquals(30, rs.getInt(10), key + " night count");
            assertEquals(LocalDate.of(2027, 3, 1), rs.getObject(11, LocalDate.class), key + " first night");
            assertEquals(LocalDate.of(2027, 3, 30), rs.getObject(12, LocalDate.class), key + " last night");
            assertTrue(rs.getInt(13) > 0, key + " availability");
            assertTrue(rs.getLong(14) > 0, key + " nightly total");
        });
        for (int partySize = 1; partySize <= 8; partySize++) {
            int party = partySize;
            assertTrue(jdbc.queryForObject("""
                    SELECT COUNT(*) > 0 FROM accommodation_property property
                    JOIN accommodation_unit unit ON unit.accommodation_property_id = property.id
                    JOIN accommodation_nightly_inventory nightly ON nightly.accommodation_unit_id = unit.id
                    WHERE nightly.night_date = DATE '2027-03-10'
                      AND ((unit.unit_kind = 'ROOM' AND nightly.available_inventory >= CEILING(? * 1.0 / unit.guest_capacity))
                           OR (unit.unit_kind = 'WHOLE_PROPERTY' AND unit.guest_capacity >= ? AND nightly.available_inventory >= 1))
                    """, Boolean.class, party, party), "party " + party + " fit");
        }
        assertTrue(jdbc.queryForObject("SELECT COUNT(DISTINCT guest_rating) FROM accommodation_property", Integer.class) > 2, "rating variation");
        assertTrue(jdbc.queryForObject("SELECT COUNT(DISTINCT distance_to_city_center_meters) FROM accommodation_property", Integer.class) > 2, "distance variation");
        assertTrue(jdbc.queryForObject("SELECT COUNT(DISTINCT base_price_cents + tax_cents + fee_cents) FROM accommodation_nightly_inventory", Integer.class) > 2, "price variation");
    }

    private static void assertRentals(JdbcTemplate jdbc) {
        assertEquals(3, count(jdbc, "rental_location"));
        assertEquals(9, count(jdbc, "rental_vehicle_class"));
        assertEquals(21, count(jdbc, "rental_unit"));
        assertEquals(0, count(jdbc, "rental_unit_occupancy"));
        for (String destination : DESTINATIONS) {
            String destinationKey = "destination-" + destination;
            assertEquals(1, jdbc.queryForObject("""
                    SELECT COUNT(*) FROM rental_location location
                    JOIN catalog_destination destination ON destination.id = location.destination_id
                    JOIN catalog_airport airport ON airport.id = location.airport_id
                    WHERE destination.catalog_key = ? AND airport.destination_id = destination.id
                    """, Integer.class, destinationKey), destination + " airport location");
            for (String category : RENTAL_CLASSES) {
                assertTrue(jdbc.queryForObject("""
                        SELECT COUNT(*) > 0 FROM rental_vehicle_class vehicle_class
                        JOIN rental_location location ON location.id = vehicle_class.rental_location_id
                        JOIN catalog_destination destination ON destination.id = location.destination_id
                        JOIN rental_unit unit ON unit.rental_vehicle_class_id = vehicle_class.id
                        WHERE destination.catalog_key = ? AND vehicle_class.vehicle_category = ?
                        """, Boolean.class, destinationKey, category), destination + " " + category + " unit");
            }
        }
        assertEquals(9, jdbc.queryForObject("SELECT COUNT(DISTINCT catalog_key) FROM rental_vehicle_class", Integer.class));
        assertEquals(21, jdbc.queryForObject("SELECT COUNT(DISTINCT catalog_key) FROM rental_unit", Integer.class));
        jdbc.query("""
                SELECT vehicle_class.catalog_key, vehicle_class.daily_base_price_cents, vehicle_class.daily_tax_cents,
                       vehicle_class.daily_fee_cents, airport.time_zone_id
                FROM rental_vehicle_class vehicle_class
                JOIN rental_location location ON location.id = vehicle_class.rental_location_id
                JOIN catalog_airport airport ON airport.id = location.airport_id
                """, rs -> {
            long total = rs.getLong(2) + rs.getLong(3) + rs.getLong(4);
            assertTrue(total > 0, rs.getString(1) + " daily total");
            ZoneId.of(rs.getString(5));
        });
        assertTrue(jdbc.queryForObject("SELECT COUNT(DISTINCT daily_base_price_cents + daily_tax_cents + daily_fee_cents) FROM rental_vehicle_class", Integer.class) > 2);
    }

    static long dailyRentalTotal(JdbcTemplate jdbc, String classKey) {
        return jdbc.queryForObject("SELECT daily_base_price_cents + daily_tax_cents + daily_fee_cents FROM rental_vehicle_class WHERE catalog_key = ?", Long.class, classKey);
    }

    static long rentalTotalForInterval(JdbcTemplate jdbc, String classKey, OffsetDateTime pickup, OffsetDateTime returned) {
        long hours = Duration.between(pickup, returned).toHours();
        if (Duration.between(pickup, returned).toMinutes() % 60 != 0) {
            hours++;
        }
        return ((hours + 23) / 24) * dailyRentalTotal(jdbc, classKey);
    }

    private static int count(JdbcTemplate jdbc, String table) {
        return jdbc.queryForObject("SELECT COUNT(*) FROM " + table, Integer.class);
    }
}
