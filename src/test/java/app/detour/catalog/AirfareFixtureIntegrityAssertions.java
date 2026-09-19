package app.detour.catalog;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowCallbackHandler;

/** Complete-fixture checks which deliberately supplement the generic schema constraints. */
final class AirfareFixtureIntegrityAssertions {
    private static final Map<String, String> AIRPORT_ZONES = Map.ofEntries(
            Map.entry("PDX", "America/Los_Angeles"), Map.entry("SFO", "America/Los_Angeles"),
            Map.entry("MUC", "Europe/Berlin"), Map.entry("MEX", "America/Mexico_City"),
            Map.entry("SEA", "America/Los_Angeles"), Map.entry("SLC", "America/Denver"),
            Map.entry("ORD", "America/Chicago"), Map.entry("LAX", "America/Los_Angeles"),
            Map.entry("DFW", "America/Chicago"));
    private static final Map<String, List<String>> CONNECTIONS = Map.of(
            "sfo", List.of("SEA", "SLC"), "muc", List.of("SEA", "ORD"), "mex", List.of("LAX", "DFW"));
    private static final Map<String, String> CONNECTION_BY_OPTION = Map.of(
            "sfo-c1", "SEA", "sfo-c2", "SLC", "muc-c1", "SEA", "muc-c2", "ORD",
            "mex-c1", "LAX", "mex-c2", "DFW");

    private AirfareFixtureIntegrityAssertions() {
    }

    static void assertValid(JdbcTemplate jdbc) {
        assertEquals(3, count(jdbc, "catalog_destination"));
        assertEquals(9, count(jdbc, "catalog_airport"));
        assertEquals(2, jdbc.queryForObject(
                "SELECT COUNT(*) FROM catalog_supplier WHERE supplier_category = 'AIRLINE'", Integer.class));
        assertEquals(24, count(jdbc, "flight_schedule"));
        assertEquals(720, count(jdbc, "flight_instance"));
        assertEquals(1080, count(jdbc, "flight_instance_segment"));
        assertAirportZones(jdbc);
        assertCoverage(jdbc);
        assertConnectionPolicy(jdbc);
        assertRows(jdbc);
        assertVariation(jdbc);
    }

    private static void assertAirportZones(JdbcTemplate jdbc) {
        Map<String, String> actual = new LinkedHashMap<>();
        jdbc.query("SELECT iata_code, time_zone_id FROM catalog_airport ORDER BY iata_code",
                (RowCallbackHandler) rs -> actual.put(rs.getString(1), rs.getString(2)));
        assertEquals(AIRPORT_ZONES, actual);
        actual.values().forEach(ZoneId::of);
    }

    private static void assertCoverage(JdbcTemplate jdbc) {
        for (String destination : CONNECTIONS.keySet()) {
            for (String direction : List.of("out", "in")) {
                LocalDate start = LocalDate.of(2027, 3, "out".equals(direction) ? 1 : 2);
                for (int day = 0; day < 30; day++) {
                    LocalDate serviceDate = start.plusDays(day);
                    Integer direct = jdbc.queryForObject("""
                            SELECT COUNT(*) FROM flight_instance i JOIN flight_schedule s ON s.id = i.flight_schedule_id
                            WHERE s.catalog_key LIKE ? AND i.service_date = ? AND s.stop_count = 0
                            """, Integer.class, "airfare-" + direction + "-" + destination + "-%", serviceDate);
                    Integer oneStop = jdbc.queryForObject("""
                            SELECT COUNT(*) FROM flight_instance i JOIN flight_schedule s ON s.id = i.flight_schedule_id
                            WHERE s.catalog_key LIKE ? AND i.service_date = ? AND s.stop_count = 1
                            """, Integer.class, "airfare-" + direction + "-" + destination + "-%", serviceDate);
                    assertEquals(2, direct, direction + " direct " + destination + " " + serviceDate);
                    assertEquals(2, oneStop, direction + " one-stop " + destination + " " + serviceDate);
                }
            }
        }
        assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM flight_instance WHERE service_date < DATE '2027-03-01' OR service_date > DATE '2027-03-31'", Integer.class));
    }

    private static void assertRows(JdbcTemplate jdbc) {
        jdbc.query("""
                SELECT i.catalog_key, i.service_date, i.base_fare_cents, i.tax_cents, i.fee_cents, i.seat_capacity, i.available_seats,
                       s.catalog_key, s.stop_count, ss.segment_ordinal, ss.departure_local_time, ss.arrival_local_time,
                       ss.arrival_day_offset, origin.iata_code, origin.time_zone_id, destination.iata_code, destination.time_zone_id,
                       segment.departure_at, segment.arrival_at
                FROM flight_instance i
                JOIN flight_schedule s ON s.id = i.flight_schedule_id
                JOIN flight_schedule_segment ss ON ss.flight_schedule_id = s.id
                JOIN flight_instance_segment segment ON segment.flight_instance_id = i.id AND segment.segment_ordinal = ss.segment_ordinal
                JOIN catalog_airport origin ON origin.id = ss.origin_airport_id
                JOIN catalog_airport destination ON destination.id = ss.destination_airport_id
                ORDER BY i.catalog_key, ss.segment_ordinal
                """, rs -> {
            String instanceKey = rs.getString(1);
            LocalDate serviceDate = rs.getObject(2, LocalDate.class);
            assertPositivePerTravelerPrice(rs.getLong(3), rs.getLong(4), rs.getLong(5), instanceKey);
            assertPartyCapacity(rs.getInt(6), rs.getInt(7), instanceKey);
            OffsetDateTime departure = rs.getObject(18, OffsetDateTime.class);
            OffsetDateTime arrival = rs.getObject(19, OffsetDateTime.class);
            ZoneId originZone = ZoneId.of(rs.getString(15));
            ZoneId destinationZone = ZoneId.of(rs.getString(17));
            assertFinalArrivalIsWithinMarch(arrival, destinationZone, instanceKey);
            assertEquals(serviceDate, departure.atZoneSameInstant(originZone).toLocalDate(), instanceKey + " departure date");
            assertEquals(rs.getObject(11, LocalTime.class), departure.atZoneSameInstant(originZone).toLocalTime(), instanceKey + " departure time");
            assertEquals(serviceDate.plusDays(rs.getInt(13)), arrival.atZoneSameInstant(destinationZone).toLocalDate(), instanceKey + " arrival date");
            assertEquals(rs.getObject(12, LocalTime.class), arrival.atZoneSameInstant(destinationZone).toLocalTime(), instanceKey + " arrival time");
            assertTrue(departure.isBefore(arrival), instanceKey + " segment chronology");
        });

    }

    static void assertConnectionPolicy(JdbcTemplate jdbc) {
        jdbc.query("""
                SELECT i.catalog_key, s.catalog_key, first_leg.arrival_at, second_leg.departure_at,
                       middle.iata_code, first_origin.iata_code, final_destination.iata_code
                FROM flight_instance i JOIN flight_schedule s ON s.id = i.flight_schedule_id
                JOIN flight_instance_segment first_leg ON first_leg.flight_instance_id = i.id AND first_leg.segment_ordinal = 1
                JOIN flight_instance_segment second_leg ON second_leg.flight_instance_id = i.id AND second_leg.segment_ordinal = 2
                JOIN flight_schedule_segment first_schedule ON first_schedule.flight_schedule_id = s.id AND first_schedule.segment_ordinal = 1
                JOIN flight_schedule_segment second_schedule ON second_schedule.flight_schedule_id = s.id AND second_schedule.segment_ordinal = 2
                JOIN catalog_airport middle ON middle.id = first_schedule.destination_airport_id
                JOIN catalog_airport first_origin ON first_origin.id = first_schedule.origin_airport_id
                JOIN catalog_airport final_destination ON final_destination.id = second_schedule.destination_airport_id
                WHERE s.stop_count = 1
                """, rs -> {
            String scheduleKey = rs.getString(2);
            String[] keyParts = scheduleKey.split("-");
            String destination = keyParts[2];
            String option = keyParts[3];
            assertEquals(CONNECTION_BY_OPTION.get(destination + "-" + option), rs.getString(5), scheduleKey + " connection policy");
            assertEquals(rs.getString(5), jdbc.queryForObject("""
                    SELECT a.iata_code FROM flight_schedule_segment ss JOIN catalog_airport a ON a.id = ss.origin_airport_id
                    WHERE ss.flight_schedule_id = (SELECT id FROM flight_schedule WHERE catalog_key = ?) AND ss.segment_ordinal = 2
                    """, String.class, scheduleKey));
            assertEquals("PDX", scheduleKey.startsWith("airfare-out-") ? rs.getString(6) : rs.getString(7));
            assertMinimumLayover(rs.getObject(3, OffsetDateTime.class), rs.getObject(4, OffsetDateTime.class), rs.getString(1));
        });
    }

    static void assertMinimumLayover(OffsetDateTime arrival, OffsetDateTime departure, String instanceKey) {
        assertTrue(Duration.between(arrival, departure).toMinutes() >= 45, instanceKey + " layover");
    }

    static void assertPositivePerTravelerPrice(long baseFareCents, long taxCents, long feeCents, String instanceKey) {
        assertTrue(baseFareCents + taxCents + feeCents > 0, instanceKey + " price");
    }

    static void assertPartyCapacity(int seatCapacity, int availableSeats, String instanceKey) {
        assertTrue(seatCapacity >= 8 && availableSeats >= 8, instanceKey + " party capacity");
    }

    static void assertFinalArrivalIsWithinMarch(OffsetDateTime arrival, ZoneId destinationZone, String instanceKey) {
        assertTrue(!arrival.atZoneSameInstant(destinationZone).toLocalDate().isAfter(LocalDate.of(2027, 3, 31)),
                instanceKey + " final cutoff");
    }

    private static void assertVariation(JdbcTemplate jdbc) {
        assertTrue(jdbc.queryForObject("SELECT COUNT(DISTINCT base_fare_cents + tax_cents + fee_cents) FROM flight_instance", Integer.class) > 2);
        assertEquals(2, jdbc.queryForObject("SELECT COUNT(DISTINCT stop_count) FROM flight_schedule", Integer.class));
        assertTrue(jdbc.queryForObject("SELECT COUNT(DISTINCT departure_at) FROM flight_instance_segment WHERE segment_ordinal = 1", Integer.class) > 10);
        List<Long> itineraryDurations = jdbc.query("""
                SELECT first_leg.departure_at, last_leg.arrival_at
                FROM flight_instance i
                JOIN flight_instance_segment first_leg ON first_leg.flight_instance_id = i.id AND first_leg.segment_ordinal = 1
                JOIN flight_instance_segment last_leg ON last_leg.flight_instance_id = i.id AND last_leg.segment_ordinal = i.segment_count
                """, (rs, rowNumber) -> Duration.between(
                rs.getObject(1, OffsetDateTime.class), rs.getObject(2, OffsetDateTime.class)).toMinutes());
        assertTrue(itineraryDurations.stream().distinct().count() > 2, "itinerary duration variation");
        List<Itinerary> itineraries = jdbc.query("""
                SELECT i.catalog_key, i.base_fare_cents + i.tax_cents + i.fee_cents, s.stop_count,
                       first_leg.departure_at, last_leg.arrival_at
                FROM flight_instance i
                JOIN flight_schedule s ON s.id = i.flight_schedule_id
                JOIN flight_instance_segment first_leg ON first_leg.flight_instance_id = i.id AND first_leg.segment_ordinal = 1
                JOIN flight_instance_segment last_leg ON last_leg.flight_instance_id = i.id AND last_leg.segment_ordinal = i.segment_count
                """, (rs, rowNumber) -> new Itinerary(
                rs.getString(1), rs.getLong(2), rs.getInt(3), rs.getObject(4, OffsetDateTime.class), rs.getObject(5, OffsetDateTime.class)));
        long lowestPrice = itineraries.stream().mapToLong(Itinerary::allInclusiveCents).min().orElseThrow();
        long shortestDuration = itineraries.stream().mapToLong(itinerary -> Duration.between(
                itinerary.departureAt(), itinerary.arrivalAt()).toMinutes()).min().orElseThrow();
        OffsetDateTime earliestDeparture = itineraries.stream().map(Itinerary::departureAt).min(OffsetDateTime::compareTo).orElseThrow();
        assertTrue(itineraries.stream().filter(itinerary -> itinerary.allInclusiveCents() == lowestPrice)
                .allMatch(itinerary -> itinerary.stopCount() == 1), "lowest-price choices connect");
        assertTrue(itineraries.stream().filter(itinerary -> Duration.between(itinerary.departureAt(), itinerary.arrivalAt()).toMinutes() == shortestDuration)
                .allMatch(itinerary -> itinerary.stopCount() == 0), "shortest choices are direct");
        assertTrue(itineraries.stream().filter(itinerary -> itinerary.departureAt().equals(earliestDeparture))
                .allMatch(itinerary -> itinerary.allInclusiveCents() != lowestPrice && itinerary.stopCount() == 1),
                "earliest departures differ from the lowest-price and fewest-stop choices");
        assertTrue(jdbc.queryForObject("SELECT COUNT(*) = COUNT(DISTINCT catalog_key) FROM flight_instance", Boolean.class));
    }

    private record Itinerary(String catalogKey, long allInclusiveCents, int stopCount,
                             OffsetDateTime departureAt, OffsetDateTime arrivalAt) {
    }

    private static int count(JdbcTemplate jdbc, String table) {
        return jdbc.queryForObject("SELECT COUNT(*) FROM " + table, Integer.class);
    }
}
