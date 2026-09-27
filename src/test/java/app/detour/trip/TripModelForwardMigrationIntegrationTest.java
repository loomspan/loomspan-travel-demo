package app.detour.trip;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.util.UUID;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationVersion;
import org.junit.jupiter.api.Test;

class TripModelForwardMigrationIntegrationTest {

    @Test
    void freshDatabaseHasNamedDatedModel() throws Exception {
        String url = url("fresh");
        Flyway flyway = latest(url);
        flyway.migrate();
        assertEquals("20", flyway.info().current().getVersion().getVersion());
        try (Connection c = DriverManager.getConnection(url, "sa", "")) {
            assertEquals(0, count(c, "SELECT COUNT(*) FROM detour_trip_draft"));
            assertTrue(hasColumn(c, "DETOUR_TRIP", "NAME"));
            assertTrue(hasColumn(c, "DETOUR_TRIP_DRAFT", "START_DATE"));
            assertTrue(hasColumn(c, "DETOUR_PLANNED_ITINERARY", "VERSION"));
        }
    }

    @Test
    void migratesPopulatedDraftsWithoutChangingBookedReferences() throws Exception {
        String url = url("upgrade");
        oldLineage(url);
        UUID oldPlanned = UUID.randomUUID();
        long workingCandidate;
        long copiedCandidate;
        long bookedPlannedId;
        long copiedStayCandidate;
        long copiedAirfareCandidate;
        long copiedConnectingAirfareCandidate;
        try (Connection c = DriverManager.getConnection(url, "sa", "")) {
            long user = insert(c, "INSERT INTO detour_user (canonical_email, password_hash, created_at) VALUES ('migration@example.test', 'hash', ?)", OffsetDateTime.parse("2027-01-01T00:00:00Z"));
            long destination = scalar(c, "SELECT MIN(id) FROM catalog_destination");
            long tripA = insert(c, "INSERT INTO detour_trip (public_id, owner_user_id, catalog_destination_id, start_date, end_date, traveler_count, display_label) VALUES (?, ?, ?, DATE '2027-03-10', DATE '2027-03-14', 2, 'Portland to Anywhere')", UUID.randomUUID(), user, destination);
            long tripB = insert(c, "INSERT INTO detour_trip (public_id, owner_user_id, catalog_destination_id, start_date, end_date, traveler_count, display_label) VALUES (?, ?, ?, DATE '2027-03-10', DATE '2027-03-14', 1, 'Portland to Anywhere')", UUID.randomUUID(), user, destination);
            long tripC = insert(c, "INSERT INTO detour_trip (public_id, owner_user_id, catalog_destination_id, start_date, end_date, traveler_count, display_label) VALUES (?, ?, ?, DATE '2027-03-10', DATE '2027-03-14', 1, 'No Draft Yet')", UUID.randomUUID(), user, destination);
            insert(c, "INSERT INTO detour_trip_traveler (trip_id, traveler_ordinal, age) VALUES (?, 1, NULL)", tripA);
            insert(c, "INSERT INTO detour_trip_traveler (trip_id, traveler_ordinal, age) VALUES (?, 2, 42)", tripA);
            insert(c, "INSERT INTO detour_trip_traveler (trip_id, traveler_ordinal, age) VALUES (?, 1, 30)", tripB);
            insert(c, "INSERT INTO detour_trip_traveler (trip_id, traveler_ordinal, age) VALUES (?, 1, 40)", tripC);
            insert(c, "INSERT INTO detour_trip_draft (public_id, trip_id) VALUES (?, ?)", UUID.randomUUID(), tripA); // oldest empty is not Working
            workingCandidate = insert(c, "INSERT INTO detour_trip_draft (public_id, trip_id) VALUES (?, ?)", UUID.randomUUID(), tripA);
            copiedCandidate = insert(c, "INSERT INTO detour_trip_draft (public_id, trip_id) VALUES (?, ?)", UUID.randomUUID(), tripA);
            copiedStayCandidate = insert(c, "INSERT INTO detour_trip_draft (public_id, trip_id) VALUES (?, ?)", UUID.randomUUID(), tripA);
            copiedAirfareCandidate = insert(c, "INSERT INTO detour_trip_draft (public_id, trip_id) VALUES (?, ?)", UUID.randomUUID(), tripA);
            copiedConnectingAirfareCandidate = insert(c, "INSERT INTO detour_trip_draft (public_id, trip_id) VALUES (?, ?)", UUID.randomUUID(), tripA);
            insert(c, "INSERT INTO detour_trip_draft (public_id, trip_id) VALUES (?, ?)", UUID.randomUUID(), tripA);
            long stayUnit = scalar(c, "SELECT accommodation_unit_id FROM accommodation_nightly_inventory WHERE night_date >= DATE '2027-03-10' AND night_date < DATE '2027-03-14' GROUP BY accommodation_unit_id HAVING COUNT(*) = 4 ORDER BY accommodation_unit_id LIMIT 1");
            long rentalUnit = scalar(c, "SELECT MIN(id) FROM rental_unit");
            insert(c, "INSERT INTO detour_trip_draft_stay_selection (draft_id, accommodation_unit_id, unit_count) VALUES (?, ?, 1)", workingCandidate, stayUnit);
            insert(c, "INSERT INTO detour_trip_draft_rental_selection (draft_id, rental_unit_id, pickup_at, return_at) VALUES (?, ?, ?, ?)", copiedCandidate, rentalUnit, OffsetDateTime.parse("2027-03-10T12:00:00Z"), OffsetDateTime.parse("2027-03-14T12:00:00Z"));
            insert(c, "INSERT INTO detour_trip_draft_stay_selection (draft_id, accommodation_unit_id, unit_count) VALUES (?, ?, 1)", copiedStayCandidate, stayUnit);
            long outbound = scalar(c, "SELECT MIN(fi.id) FROM flight_instance fi JOIN flight_schedule fs ON fs.id = fi.flight_schedule_id JOIN catalog_airport origin ON origin.id = fs.origin_airport_id JOIN catalog_airport destination ON destination.id = fs.destination_airport_id WHERE origin.iata_code = 'PDX' AND destination.destination_id = " + destination + " AND fi.service_date = DATE '2027-03-10'");
            long inbound = scalar(c, "SELECT MIN(fi.id) FROM flight_instance fi JOIN flight_schedule fs ON fs.id = fi.flight_schedule_id JOIN catalog_airport origin ON origin.id = fs.origin_airport_id JOIN catalog_airport destination ON destination.id = fs.destination_airport_id WHERE origin.destination_id = " + destination + " AND destination.iata_code = 'PDX' AND fi.service_date = DATE '2027-03-14'");
            insert(c, "INSERT INTO detour_trip_draft_airfare_selection (draft_id, outbound_flight_instance_id, return_flight_instance_id) VALUES (?, ?, ?)", copiedAirfareCandidate, outbound, inbound);
            long connectingOutbound = scalar(c, "SELECT MIN(fi.id) FROM flight_instance fi JOIN flight_schedule fs ON fs.id = fi.flight_schedule_id JOIN catalog_airport origin ON origin.id = fs.origin_airport_id JOIN catalog_airport destination ON destination.id = fs.destination_airport_id WHERE origin.iata_code = 'PDX' AND destination.destination_id = " + destination + " AND fi.service_date = DATE '2027-03-10' AND fs.stop_count = 1");
            long connectingInbound = scalar(c, "SELECT MIN(fi.id) FROM flight_instance fi JOIN flight_schedule fs ON fs.id = fi.flight_schedule_id JOIN catalog_airport origin ON origin.id = fs.origin_airport_id JOIN catalog_airport destination ON destination.id = fs.destination_airport_id WHERE origin.destination_id = " + destination + " AND destination.iata_code = 'PDX' AND fi.service_date = DATE '2027-03-14' AND fs.stop_count = 1");
            insert(c, "INSERT INTO detour_trip_draft_airfare_selection (draft_id, outbound_flight_instance_id, return_flight_instance_id) VALUES (?, ?, ?)", copiedConnectingAirfareCandidate, connectingOutbound, connectingInbound);
            bookedPlannedId = insert(c, "INSERT INTO detour_planned_itinerary (public_id, trip_id) VALUES (?, ?)", oldPlanned, tripB);
            insert(c, "INSERT INTO detour_booking (public_id, trip_id, planned_itinerary_id, booking_reference, status, grand_total_cents, idempotency_key, created_at) VALUES (?, ?, ?, 'BOOK-A', 'ACTIVE', 1234, 'key-a', ?)", UUID.randomUUID(), tripB, bookedPlannedId, OffsetDateTime.parse("2027-01-02T00:00:00Z"));
            insert(c, "INSERT INTO detour_booking (public_id, trip_id, planned_itinerary_id, booking_reference, status, grand_total_cents, idempotency_key, created_at, canceled_at) VALUES (?, ?, ?, 'BOOK-C', 'CANCELED', 1234, 'key-c', ?, ?)", UUID.randomUUID(), tripB, bookedPlannedId, OffsetDateTime.parse("2027-01-01T00:00:00Z"), OffsetDateTime.parse("2027-01-01T01:00:00Z"));
        }
        // Simulate an installation that already ran V19 and renamed a Saved option.
        Flyway.configure().dataSource(url, "sa", "").locations("classpath:db/migration")
                .target(MigrationVersion.fromVersion("19")).load().migrate();
        try (Connection c = DriverManager.getConnection(url, "sa", "")) {
            try (PreparedStatement statement = c.prepareStatement("UPDATE detour_planned_itinerary SET name = 'Beach escape' WHERE id = ?")) {
                statement.setLong(1, bookedPlannedId);
                statement.executeUpdate();
            }
        }
        latest(url).migrate();
        try (Connection c = DriverManager.getConnection(url, "sa", "")) {
            assertEquals(3, count(c, "SELECT COUNT(*) FROM detour_trip_draft"));
            assertEquals(0, count(c, "SELECT COUNT(*) FROM (SELECT trip_id FROM detour_trip_draft GROUP BY trip_id HAVING COUNT(*) <> 1)"));
            assertEquals(workingCandidate, scalar(c, "SELECT id FROM detour_trip_draft WHERE trip_id = (SELECT trip_id FROM detour_trip_draft WHERE id = " + workingCandidate + ")"));
            assertEquals(5, count(c, "SELECT COUNT(*) FROM detour_planned_itinerary"));
            assertEquals(1, count(c, "SELECT COUNT(*) FROM detour_planned_rental_snapshot r JOIN detour_planned_itinerary p ON p.id = r.planned_itinerary_id WHERE p.name = 'Recovered option " + copiedCandidate + "'"));
            assertEquals(4, count(c, "SELECT COUNT(*) FROM detour_planned_stay_night_snapshot"));
            assertEquals(1, count(c, "SELECT COUNT(*) FROM detour_planned_stay_snapshot s JOIN detour_planned_itinerary p ON p.id = s.planned_itinerary_id WHERE p.name = 'Recovered option " + copiedStayCandidate + "'"));
            assertEquals(1, count(c, "SELECT COUNT(*) FROM detour_planned_airfare_snapshot a JOIN detour_planned_itinerary p ON p.id = a.planned_itinerary_id WHERE p.name = 'Recovered option " + copiedAirfareCandidate + "' AND a.outbound_base_fare_cents > 0 AND a.return_base_fare_cents > 0"));
            assertEquals(1, count(c, "SELECT COUNT(*) FROM detour_planned_airfare_snapshot a JOIN detour_planned_itinerary p ON p.id = a.planned_itinerary_id WHERE p.name = 'Recovered option " + copiedAirfareCandidate + "' AND a.outbound_carrier_name IS NOT NULL AND a.return_carrier_name IS NOT NULL AND a.outbound_flight_number IS NOT NULL AND a.return_flight_number IS NOT NULL AND a.outbound_departure_time IS NOT NULL AND a.return_arrival_time IS NOT NULL AND a.outbound_departure_timezone IS NOT NULL AND a.return_arrival_timezone IS NOT NULL AND a.outbound_duration_minutes > 0 AND a.return_duration_minutes > 0 AND a.total_duration_minutes = a.outbound_duration_minutes + a.return_duration_minutes"));
            assertEquals(1, count(c, "SELECT COUNT(*) FROM detour_planned_airfare_snapshot a JOIN detour_planned_itinerary p ON p.id = a.planned_itinerary_id WHERE p.name = 'Recovered option " + copiedConnectingAirfareCandidate + "' AND a.outbound_stop_count = 1 AND a.return_stop_count = 1 AND a.outbound_layover_airport_code IS NOT NULL AND a.return_layover_airport_code IS NOT NULL AND a.outbound_layover_duration_minutes > 0 AND a.return_layover_duration_minutes > 0 AND a.total_duration_minutes = a.outbound_duration_minutes + a.return_duration_minutes"));
            assertEquals(1, count(c, "SELECT COUNT(*) FROM detour_planned_stay_snapshot s JOIN detour_planned_itinerary p ON p.id = s.planned_itinerary_id WHERE p.name = 'Recovered option " + copiedStayCandidate + "' AND s.property_name IS NOT NULL AND s.unit_name IS NOT NULL AND s.property_category IS NOT NULL AND s.location_description IS NOT NULL AND s.guest_capacity > 0 AND s.required_room_count > 0"));
            assertEquals(1, count(c, "SELECT COUNT(*) FROM detour_planned_rental_snapshot r JOIN detour_planned_itinerary p ON p.id = r.planned_itinerary_id WHERE p.name = 'Recovered option " + copiedCandidate + "' AND r.location_name IS NOT NULL AND r.vehicle_class_name IS NOT NULL AND r.unit_identifier IS NOT NULL AND r.vehicle_category IS NOT NULL AND r.daily_base_price_cents > 0 AND r.pickup_at < r.return_at"));
            assertEquals(2, count(c, "SELECT COUNT(*) FROM detour_booking WHERE planned_itinerary_id = " + bookedPlannedId));
            assertEquals(1, count(c, "SELECT COUNT(*) FROM detour_planned_itinerary WHERE id = " + bookedPlannedId + " AND public_id = '" + oldPlanned + "'"));
            assertEquals(1, count(c, "SELECT COUNT(*) FROM detour_planned_itinerary WHERE id = " + bookedPlannedId + " AND name = 'Beach escape'"));
            assertEquals(2, count(c, "SELECT COUNT(*) FROM detour_trip WHERE name = 'Portland to Anywhere'"));
            assertEquals(1, count(c, "SELECT COUNT(*) FROM detour_trip_traveler WHERE age IS NULL"));
            assertEquals(1, count(c, "SELECT COUNT(*) FROM detour_trip_draft WHERE start_date = DATE '2027-03-10' AND end_date = DATE '2027-03-14' AND id = " + workingCandidate));
            assertEquals(1, count(c, "SELECT COUNT(*) FROM detour_planned_itinerary WHERE name = 'Recovered option " + copiedCandidate + "' AND start_date = DATE '2027-03-10' AND end_date = DATE '2027-03-14'"));
        }
    }

    @Test
    void refusesToDiscardStayWhenCatalogNightIsMissing() throws Exception {
        String url = url("missing-night");
        oldLineage(url);
        try (Connection c = DriverManager.getConnection(url, "sa", "")) {
            long user = insert(c, "INSERT INTO detour_user (canonical_email, password_hash, created_at) VALUES ('missing@example.test', 'hash', ?)", OffsetDateTime.parse("2027-01-01T00:00:00Z"));
            long destination = scalar(c, "SELECT MIN(id) FROM catalog_destination");
            long trip = insert(c, "INSERT INTO detour_trip (public_id, owner_user_id, catalog_destination_id, start_date, end_date, traveler_count, display_label) VALUES (?, ?, ?, DATE '2027-03-10', DATE '2027-03-14', 1, 'Missing night')", UUID.randomUUID(), user, destination);
            long working = insert(c, "INSERT INTO detour_trip_draft (public_id, trip_id) VALUES (?, ?)", UUID.randomUUID(), trip);
            long surplus = insert(c, "INSERT INTO detour_trip_draft (public_id, trip_id) VALUES (?, ?)", UUID.randomUUID(), trip);
            long unit = scalar(c, "SELECT MIN(id) FROM accommodation_unit");
            insert(c, "INSERT INTO detour_trip_draft_stay_selection (draft_id, accommodation_unit_id, unit_count) VALUES (?, ?, 1)", working, unit);
            insert(c, "INSERT INTO detour_trip_draft_stay_selection (draft_id, accommodation_unit_id, unit_count) VALUES (?, ?, 1)", surplus, unit);
            // The fixture's stay is missing at least one selected night.
            try (PreparedStatement statement = c.prepareStatement("DELETE FROM accommodation_nightly_inventory WHERE accommodation_unit_id = ? AND night_date = DATE '2027-03-11'")) {
                statement.setLong(1, unit);
                statement.executeUpdate();
            }
        }
        assertThrows(Exception.class, () -> latest(url).migrate());
        try (Connection c = DriverManager.getConnection(url, "sa", "")) {
            assertEquals(2, count(c, "SELECT COUNT(*) FROM detour_trip_draft"));
            assertEquals(2, count(c, "SELECT COUNT(*) FROM detour_trip_draft_stay_selection"));
        }
    }

    private String url(String name) { return "jdbc:h2:file:./target/v19-" + name + "-" + UUID.randomUUID() + ";DB_CLOSE_ON_EXIT=FALSE"; }
    private void oldLineage(String url) { Flyway.configure().dataSource(url, "sa", "").locations("classpath:db/migration").target(MigrationVersion.fromVersion("18")).load().migrate(); }
    private Flyway latest(String url) { return Flyway.configure().dataSource(url, "sa", "").locations("classpath:db/migration").load(); }

    private long insert(Connection c, String sql, Object... values) throws SQLException {
        try (PreparedStatement p = c.prepareStatement(sql, java.sql.Statement.RETURN_GENERATED_KEYS)) {
            for (int i = 0; i < values.length; i++) p.setObject(i + 1, values[i]);
            p.executeUpdate();
            try (ResultSet keys = p.getGeneratedKeys()) { return keys.next() ? keys.getLong(1) : -1; }
        }
    }
    private long scalar(Connection c, String sql) throws SQLException {
        try (ResultSet r = c.createStatement().executeQuery(sql)) { assertTrue(r.next()); return r.getLong(1); }
    }
    private int count(Connection c, String sql) throws SQLException { return (int) scalar(c, sql); }
    private boolean hasColumn(Connection c, String table, String column) throws SQLException {
        try (ResultSet columns = c.getMetaData().getColumns(null, null, table, column)) { return columns.next(); }
    }
}
