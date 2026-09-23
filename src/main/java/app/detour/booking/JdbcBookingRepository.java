package app.detour.booking;

import app.detour.trip.AirfareSelection;
import app.detour.trip.DraftSelections;
import app.detour.trip.RentalSelection;
import app.detour.trip.StayNight;
import app.detour.trip.StaySelection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;

@Repository
public class JdbcBookingRepository implements BookingRepository {
    private final JdbcTemplate jdbc;

    public JdbcBookingRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public List<FlightSeatsLock> lockFlightInstances(List<Long> flightInstanceIds) {
        List<Long> sortedIds = flightInstanceIds.stream().distinct().sorted().toList();
        List<FlightSeatsLock> results = new ArrayList<>(sortedIds.size());
        for (Long id : sortedIds) {
            Integer seats = jdbc.queryForObject("SELECT available_seats FROM flight_instance WHERE id = ? FOR UPDATE", Integer.class, id);
            results.add(new FlightSeatsLock(id, seats != null ? seats : 0));
        }
        return results;
    }

    @Override
    public List<StayNightlyLock> lockStayNightlyInventory(long accommodationUnitId, LocalDate startDate, LocalDate endDate) {
        return jdbc.query("""
                SELECT night_date, available_inventory
                FROM accommodation_nightly_inventory
                WHERE accommodation_unit_id = ?
                  AND night_date >= ?
                  AND night_date < ?
                ORDER BY accommodation_unit_id ASC, night_date ASC FOR UPDATE
                """, (rs, rowNum) -> new StayNightlyLock(
                        rs.getObject("night_date", LocalDate.class),
                        rs.getInt("available_inventory")
                ), accommodationUnitId, startDate, endDate);
    }

    @Override
    public void lockRentalUnit(long rentalUnitId) {
        jdbc.queryForObject("SELECT id FROM rental_unit WHERE id = ? FOR UPDATE", Long.class, rentalUnitId);
    }

    @Override
    public boolean isRentalAvailable(long rentalUnitId, OffsetDateTime pickupAt, OffsetDateTime returnAt) {
        Integer activeOverlapCount = jdbc.queryForObject("""
                SELECT COUNT(*)
                FROM rental_unit_occupancy
                WHERE rental_unit_id = ?
                  AND occupancy_status = 'ACTIVE'
                  AND pickup_at < ?
                  AND return_at > ?
                """, Integer.class, rentalUnitId, returnAt, pickupAt);
        return activeOverlapCount != null && activeOverlapCount == 0;
    }

    @Override
    public boolean decrementFlightSeats(long flightInstanceId, int seatsToDecrement) {
        int rows = jdbc.update(
                "UPDATE flight_instance SET available_seats = available_seats - ? WHERE id = ? AND available_seats >= ?",
                seatsToDecrement, flightInstanceId, seatsToDecrement);
        return rows == 1;
    }

    @Override
    public boolean decrementStayInventory(long accommodationUnitId, LocalDate date, int unitsToDecrement) {
        int rows = jdbc.update(
                "UPDATE accommodation_nightly_inventory SET available_inventory = available_inventory - ? WHERE accommodation_unit_id = ? AND night_date = ? AND available_inventory >= ?",
                unitsToDecrement, accommodationUnitId, date, unitsToDecrement);
        return rows == 1;
    }

    @Override
    public long insertRentalOccupancy(long rentalUnitId, OffsetDateTime pickupAt, OffsetDateTime returnAt) {
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbc.update(connection -> {
            PreparedStatement ps = connection.prepareStatement(
                    "INSERT INTO rental_unit_occupancy (rental_unit_id, pickup_at, return_at, occupancy_status) VALUES (?, ?, ?, 'ACTIVE')",
                    Statement.RETURN_GENERATED_KEYS);
            ps.setLong(1, rentalUnitId);
            ps.setObject(2, pickupAt);
            ps.setObject(3, returnAt);
            return ps;
        }, keyHolder);
        Number key = (Number) (keyHolder.getKeys() != null ? keyHolder.getKeys().get("ID") : keyHolder.getKey());
        if (key == null) throw new IllegalStateException("Failed to insert rental occupancy");
        return key.longValue();
    }

    @Override
    public long insertBooking(BookingRecord booking) {
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbc.update(connection -> {
            PreparedStatement ps = connection.prepareStatement("""
                    INSERT INTO detour_booking (
                        public_id, trip_id, planned_itinerary_id, booking_reference, status,
                        grand_total_cents, idempotency_key, created_at, canceled_at,
                        airfare_reference, stay_reference, rental_reference, rental_occupancy_id
                    ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                    """, Statement.RETURN_GENERATED_KEYS);
            ps.setObject(1, booking.publicId());
            ps.setLong(2, booking.tripId());
            ps.setLong(3, booking.plannedItineraryId());
            ps.setString(4, booking.bookingReference());
            ps.setString(5, booking.status());
            ps.setLong(6, booking.grandTotalCents());
            ps.setString(7, booking.idempotencyKey());
            ps.setObject(8, booking.createdAt());
            ps.setObject(9, booking.canceledAt());
            ps.setString(10, booking.airfareReference());
            ps.setString(11, booking.stayReference());
            ps.setString(12, booking.rentalReference());
            if (booking.rentalOccupancyId() != null) {
                ps.setLong(13, booking.rentalOccupancyId());
            } else {
                ps.setNull(13, java.sql.Types.BIGINT);
            }
            return ps;
        }, keyHolder);
        Number key = (Number) (keyHolder.getKeys() != null ? keyHolder.getKeys().get("ID") : keyHolder.getKey());
        if (key == null) throw new IllegalStateException("Failed to insert booking");
        return key.longValue();
    }

    @Override
    public void copySnapshotsFromPlanned(long bookingId, long plannedItineraryId) {
        jdbc.update("""
                INSERT INTO detour_booking_airfare_snapshot (
                    booking_id, outbound_flight_instance_id, return_flight_instance_id,
                    outbound_description, return_description,
                    outbound_base_fare_cents, outbound_tax_cents, outbound_fee_cents,
                    return_base_fare_cents, return_tax_cents, return_fee_cents,
                    outbound_carrier_name, outbound_flight_number, outbound_stop_count,
                    outbound_layover_airport_code, outbound_layover_duration_minutes,
                    outbound_departure_time, outbound_arrival_time,
                    outbound_departure_timezone, outbound_arrival_timezone, outbound_duration_minutes,
                    return_carrier_name, return_flight_number, return_stop_count,
                    return_layover_airport_code, return_layover_duration_minutes,
                    return_departure_time, return_arrival_time,
                    return_departure_timezone, return_arrival_timezone, return_duration_minutes,
                    total_duration_minutes
                )
                SELECT ?, outbound_flight_instance_id, return_flight_instance_id,
                    outbound_description, return_description,
                    outbound_base_fare_cents, outbound_tax_cents, outbound_fee_cents,
                    return_base_fare_cents, return_tax_cents, return_fee_cents,
                    outbound_carrier_name, outbound_flight_number, outbound_stop_count,
                    outbound_layover_airport_code, outbound_layover_duration_minutes,
                    outbound_departure_time, outbound_arrival_time,
                    outbound_departure_timezone, outbound_arrival_timezone, outbound_duration_minutes,
                    return_carrier_name, return_flight_number, return_stop_count,
                    return_layover_airport_code, return_layover_duration_minutes,
                    return_departure_time, return_arrival_time,
                    return_departure_timezone, return_arrival_timezone, return_duration_minutes,
                    total_duration_minutes
                FROM detour_planned_airfare_snapshot
                WHERE planned_itinerary_id = ?
                """, bookingId, plannedItineraryId);

        jdbc.update("""
                INSERT INTO detour_booking_stay_snapshot (
                    booking_id, accommodation_unit_id, unit_count,
                    property_name, unit_name, property_category, location_description,
                    distance_to_city_center_meters, guest_capacity, required_room_count
                )
                SELECT ?, accommodation_unit_id, unit_count,
                    property_name, unit_name, property_category, location_description,
                    distance_to_city_center_meters, guest_capacity, required_room_count
                FROM detour_planned_stay_snapshot
                WHERE planned_itinerary_id = ?
                """, bookingId, plannedItineraryId);

        jdbc.update("""
                INSERT INTO detour_booking_stay_night_snapshot (
                    booking_id, night_date, base_price_cents, tax_cents, fee_cents
                )
                SELECT ?, night_date, base_price_cents, tax_cents, fee_cents
                FROM detour_planned_stay_night_snapshot
                WHERE planned_itinerary_id = ?
                """, bookingId, plannedItineraryId);

        jdbc.update("""
                INSERT INTO detour_booking_rental_snapshot (
                    booking_id, rental_unit_id, pickup_at, return_at,
                    location_name, vehicle_class_name, unit_identifier,
                    daily_base_price_cents, daily_tax_cents, daily_fee_cents, vehicle_category
                )
                SELECT ?, rental_unit_id, pickup_at, return_at,
                    location_name, vehicle_class_name, unit_identifier,
                    daily_base_price_cents, daily_tax_cents, daily_fee_cents, vehicle_category
                FROM detour_planned_rental_snapshot
                WHERE planned_itinerary_id = ?
                """, bookingId, plannedItineraryId);
    }

    @Override
    public Optional<BookingRecord> findActiveBookingRecordByTripId(long tripId) {
        return jdbc.query("""
                SELECT id, public_id, trip_id, planned_itinerary_id, booking_reference, status,
                       grand_total_cents, idempotency_key, created_at, canceled_at,
                       airfare_reference, stay_reference, rental_reference, rental_occupancy_id
                FROM detour_booking
                WHERE trip_id = ? AND status = 'ACTIVE'
                """, (rs, rowNum) -> mapBookingRecord(rs), tripId).stream().findFirst();
    }

    @Override
    public List<BookingRecord> findBookingRecordsByTripId(long tripId) {
        return jdbc.query("""
                SELECT id, public_id, trip_id, planned_itinerary_id, booking_reference, status,
                       grand_total_cents, idempotency_key, created_at, canceled_at,
                       airfare_reference, stay_reference, rental_reference, rental_occupancy_id
                FROM detour_booking
                WHERE trip_id = ?
                ORDER BY created_at DESC
                """, (rs, rowNum) -> mapBookingRecord(rs), tripId);
    }

    @Override
    public Optional<BookingRecord> findByTripIdAndIdempotencyKey(long tripId, String idempotencyKey) {
        return jdbc.query("""
                SELECT id, public_id, trip_id, planned_itinerary_id, booking_reference, status,
                       grand_total_cents, idempotency_key, created_at, canceled_at,
                       airfare_reference, stay_reference, rental_reference, rental_occupancy_id
                FROM detour_booking
                WHERE trip_id = ? AND idempotency_key = ?
                """, (rs, rowNum) -> mapBookingRecord(rs), tripId, idempotencyKey).stream().findFirst();
    }

    @Override
    public DraftSelections loadBookingSelections(long bookingId) {
        AirfareSelection airfare = jdbc.query("""
                SELECT outbound_flight_instance_id, return_flight_instance_id,
                       outbound_description, return_description,
                       outbound_base_fare_cents, outbound_tax_cents, outbound_fee_cents,
                       return_base_fare_cents, return_tax_cents, return_fee_cents,
                       outbound_carrier_name, outbound_flight_number, outbound_stop_count,
                       outbound_layover_airport_code, outbound_layover_duration_minutes,
                       outbound_departure_time, outbound_arrival_time,
                       outbound_departure_timezone, outbound_arrival_timezone, outbound_duration_minutes,
                       return_carrier_name, return_flight_number, return_stop_count,
                       return_layover_airport_code, return_layover_duration_minutes,
                       return_departure_time, return_arrival_time,
                       return_departure_timezone, return_arrival_timezone, return_duration_minutes,
                       total_duration_minutes
                FROM detour_booking_airfare_snapshot WHERE booking_id = ?
                """, (r, n) -> new AirfareSelection(
                        r.getLong(1), r.getLong(2), r.getString(3), r.getString(4),
                        r.getLong(5), r.getLong(6), r.getLong(7), r.getLong(8), r.getLong(9), r.getLong(10),
                        r.getString(11), r.getString(12), (Integer) r.getObject(13),
                        r.getString(14), (Integer) r.getObject(15),
                        r.getObject(16, OffsetDateTime.class), r.getObject(17, OffsetDateTime.class),
                        r.getString(18), r.getString(19), (Integer) r.getObject(20),
                        r.getString(21), r.getString(22), (Integer) r.getObject(23),
                        r.getString(24), (Integer) r.getObject(25),
                        r.getObject(26, OffsetDateTime.class), r.getObject(27, OffsetDateTime.class),
                        r.getString(28), r.getString(29), (Integer) r.getObject(30),
                        (Integer) r.getObject(31)
                ), bookingId).stream().findFirst().orElse(null);

        StaySelection stay = jdbc.query("""
                SELECT accommodation_unit_id, unit_count, property_name, unit_name,
                       property_category, location_description, distance_to_city_center_meters,
                       guest_capacity, required_room_count
                FROM detour_booking_stay_snapshot WHERE booking_id = ?
                """, (r, n) -> {
            List<StayNight> nights = jdbc.query("""
                    SELECT night_date, base_price_cents, tax_cents, fee_cents
                    FROM detour_booking_stay_night_snapshot WHERE booking_id = ? ORDER BY night_date
                    """, (night, ignored) -> new StayNight(night.getObject(1, LocalDate.class), night.getLong(2), night.getLong(3), night.getLong(4)), bookingId);
            return new StaySelection(
                    r.getLong(1), r.getInt(2), r.getString(3), r.getString(4), List.copyOf(nights),
                    r.getString(5), r.getString(6), (Integer) r.getObject(7), (Integer) r.getObject(8), (Integer) r.getObject(9)
            );
        }, bookingId).stream().findFirst().orElse(null);

        RentalSelection rental = jdbc.query("""
                SELECT rental_unit_id, pickup_at, return_at, location_name, vehicle_class_name,
                       unit_identifier, daily_base_price_cents, daily_tax_cents, daily_fee_cents, vehicle_category
                FROM detour_booking_rental_snapshot WHERE booking_id = ?
                """, (r, n) -> new RentalSelection(
                        r.getLong(1), r.getObject(2, OffsetDateTime.class), r.getObject(3, OffsetDateTime.class),
                        r.getString(4), r.getString(5), r.getString(6), r.getLong(7), r.getLong(8), r.getLong(9),
                        r.getString(10)
                ), bookingId).stream().findFirst().orElse(null);

        return new DraftSelections(airfare, stay, rental);
    }

    @Override
    public boolean hasActiveBooking(long tripId) {
        Integer count = jdbc.queryForObject("SELECT COUNT(*) FROM detour_booking WHERE trip_id = ? AND status = 'ACTIVE'", Integer.class, tripId);
        return count != null && count > 0;
    }

    @Override
    public int activeBookingCount(long tripId) {
        Integer count = jdbc.queryForObject("SELECT COUNT(*) FROM detour_booking WHERE trip_id = ? AND status = 'ACTIVE'", Integer.class, tripId);
        return count != null ? count : 0;
    }

    @Override
    public boolean hasBookingHistory(long tripId) {
        Integer count = jdbc.queryForObject("SELECT COUNT(*) FROM detour_booking WHERE trip_id = ?", Integer.class, tripId);
        return count != null && count > 0;
    }

    private static BookingRecord mapBookingRecord(ResultSet rs) throws SQLException {
        Long rentalOccupancyId = rs.getObject("rental_occupancy_id") != null ? rs.getLong("rental_occupancy_id") : null;
        return new BookingRecord(
                rs.getLong("id"),
                rs.getObject("public_id", UUID.class),
                rs.getLong("trip_id"),
                rs.getLong("planned_itinerary_id"),
                rs.getString("booking_reference"),
                rs.getString("status"),
                rs.getLong("grand_total_cents"),
                rs.getString("idempotency_key"),
                rs.getObject("created_at", OffsetDateTime.class),
                rs.getObject("canceled_at", OffsetDateTime.class),
                rs.getString("airfare_reference"),
                rs.getString("stay_reference"),
                rs.getString("rental_reference"),
                rentalOccupancyId
        );
    }
}
