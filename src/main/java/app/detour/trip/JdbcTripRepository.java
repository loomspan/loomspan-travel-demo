package app.detour.trip;

import java.sql.PreparedStatement;
import java.sql.Statement;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;

@Repository
class JdbcTripRepository implements TripRepository {
    private static final List<String> SUPPORTED_DESTINATION_KEYS = List.of("destination-sfo", "destination-muc", "destination-mex");
    private final JdbcTemplate jdbc;

    JdbcTripRepository(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    @Override public Optional<Destination> findSupportedDestination(String key) {
        if (!SUPPORTED_DESTINATION_KEYS.contains(key)) return Optional.empty();
        return jdbc.query("SELECT id, catalog_key, name FROM catalog_destination WHERE catalog_key = ?",
                (r, n) -> new Destination(r.getLong("id"), r.getString("catalog_key"), r.getString("name")), key).stream().findFirst();
    }

    @Override public void createAggregate(long ownerUserId, UUID tripPublicId, Destination destination, LocalDate startDate,
            LocalDate endDate, int travelerCount, List<Integer> travelerAges, Long budgetCents, String label, UUID draftPublicId) {
        createAggregateWithDrafts(ownerUserId, tripPublicId, destination, startDate, endDate, travelerCount, travelerAges, budgetCents, label,
                List.of(new DraftCreationSpec(draftPublicId, new DraftSelections(null, null, null))));
    }

    @Override public void createAggregateWithDrafts(long ownerUserId, UUID tripPublicId, Destination destination, LocalDate startDate,
            LocalDate endDate, int travelerCount, List<Integer> travelerAges, Long budgetCents, String label, List<DraftCreationSpec> drafts) {
        KeyHolder keys = new GeneratedKeyHolder();
        jdbc.update(c -> {
            PreparedStatement s = c.prepareStatement("""
                    INSERT INTO detour_trip (public_id, owner_user_id, catalog_destination_id, start_date, end_date, traveler_count, budget_cents, display_label, version)
                    VALUES (?, ?, ?, ?, ?, ?, ?, ?, 0)""", Statement.RETURN_GENERATED_KEYS);
            s.setObject(1, tripPublicId); s.setLong(2, ownerUserId); s.setLong(3, destination.id()); s.setObject(4, startDate); s.setObject(5, endDate);
            s.setInt(6, travelerCount); if (budgetCents == null) s.setNull(7, java.sql.Types.BIGINT); else s.setLong(7, budgetCents); s.setString(8, label); return s;
        }, keys);
        Number key = keys.getKeys() == null ? null : (Number) keys.getKeys().get("ID");
        if (key == null) throw new IllegalStateException("Trip insert did not return a key");
        long tripId = key.longValue();
        for (int ordinal = 0; ordinal < travelerCount; ordinal++) jdbc.update("INSERT INTO detour_trip_traveler (trip_id, traveler_ordinal, age) VALUES (?, ?, ?)", tripId, ordinal + 1, travelerAges.get(ordinal));
        for (DraftCreationSpec spec : drafts) {
            insertDraftCopy(tripId, spec.draftPublicId(), spec.selections());
        }
    }

    @Override public Optional<Trip> findByPublicIdAndOwnerUserId(UUID publicId, long ownerUserId) {
        return jdbc.query("""
                SELECT trip.id, trip.public_id, trip.owner_user_id, destination.id AS destination_id, destination.catalog_key, destination.name AS destination_name,
                       trip.start_date, trip.end_date, trip.traveler_count, trip.budget_cents, trip.display_label, trip.version
                FROM detour_trip trip JOIN catalog_destination destination ON destination.id = trip.catalog_destination_id
                WHERE trip.public_id = ? AND trip.owner_user_id = ?""", (r, n) -> loadTrip(r.getLong("id"), r, ownerUserId), publicId, ownerUserId).stream().findFirst();
    }

    @Override public List<Trip> findAllByOwnerUserId(long ownerUserId) {
        return jdbc.query("""
                SELECT trip.id, trip.public_id, trip.owner_user_id, destination.id AS destination_id, destination.catalog_key, destination.name AS destination_name,
                       trip.start_date, trip.end_date, trip.traveler_count, trip.budget_cents, trip.display_label, trip.version
                FROM detour_trip trip JOIN catalog_destination destination ON destination.id = trip.catalog_destination_id
                WHERE trip.owner_user_id = ?
                ORDER BY trip.start_date ASC, trip.id ASC""", (r, n) -> loadTrip(r.getLong("id"), r, ownerUserId), ownerUserId);
    }

    private Trip loadTrip(long tripId, java.sql.ResultSet row, long ownerUserId) throws java.sql.SQLException {
        List<Integer> ages = jdbc.query("SELECT age FROM detour_trip_traveler WHERE trip_id = ? ORDER BY traveler_ordinal", (r, n) -> (Integer) r.getObject("age"), tripId);
        List<Integer> knownAges = ages.stream().allMatch(java.util.Objects::nonNull) ? List.copyOf(ages) : null;
        LocalDate startDate = row.getObject("start_date", LocalDate.class);
        LocalDate endDate = row.getObject("end_date", LocalDate.class);
        List<TripDraft> drafts = jdbc.query("SELECT id, public_id, version FROM detour_trip_draft WHERE trip_id = ? ORDER BY id", (r, n) -> {
            long draftId = r.getLong("id"); return new TripDraft(draftId, r.getObject("public_id", UUID.class), r.getLong("version"), loadDraftSelections(draftId, startDate, endDate));
        }, tripId);
        List<PlannedItinerary> planned = jdbc.query("SELECT id, public_id FROM detour_planned_itinerary WHERE trip_id = ? ORDER BY id", (r, n) -> {
            long plannedId = r.getLong("id"); return new PlannedItinerary(plannedId, r.getObject("public_id", UUID.class), loadPlannedSelections(plannedId));
        }, tripId);
        Long budget = (Long) row.getObject("budget_cents");
        return new Trip(tripId, row.getObject("public_id", UUID.class), ownerUserId,
                new Destination(row.getLong("destination_id"), row.getString("catalog_key"), row.getString("destination_name")),
                startDate, endDate, row.getInt("traveler_count"), knownAges,
                budget, row.getString("display_label"), row.getLong("version"), List.copyOf(drafts), List.copyOf(planned));
    }

    private DraftSelections loadDraftSelections(long draftId, LocalDate startDate, LocalDate endDate) {
        AirfareSelection airfare = jdbc.query("""
                SELECT a.outbound_flight_instance_id, a.return_flight_instance_id,
                       out_s.flight_number, in_s.flight_number,
                       out_i.base_fare_cents, out_i.tax_cents, out_i.fee_cents,
                       in_i.base_fare_cents, in_i.tax_cents, in_i.fee_cents
                FROM detour_trip_draft_airfare_selection a
                JOIN flight_instance out_i ON out_i.id = a.outbound_flight_instance_id
                JOIN flight_schedule out_s ON out_s.id = out_i.flight_schedule_id
                JOIN flight_instance in_i ON in_i.id = a.return_flight_instance_id
                JOIN flight_schedule in_s ON in_s.id = in_i.flight_schedule_id
                WHERE a.draft_id = ?
                """,
                (r, n) -> new AirfareSelection(
                        r.getLong(1),
                        r.getLong(2),
                        "Flight " + r.getString(3),
                        "Flight " + r.getString(4),
                        r.getLong(5),
                        r.getLong(6),
                        r.getLong(7),
                        r.getLong(8),
                        r.getLong(9),
                        r.getLong(10)
                ), draftId).stream().findFirst().orElse(null);
        StaySelection stay = jdbc.query("""
                SELECT s.accommodation_unit_id, s.unit_count, p.name AS property_name, u.name AS unit_name
                FROM detour_trip_draft_stay_selection s
                JOIN accommodation_unit u ON u.id = s.accommodation_unit_id
                JOIN accommodation_property p ON p.id = u.accommodation_property_id
                WHERE s.draft_id = ?
                """,
                (r, n) -> {
                    long unitId = r.getLong(1);
                    int unitCount = r.getInt(2);
                    String propName = r.getString(3);
                    String uName = r.getString(4);
                    List<StayNight> nights = (startDate != null && endDate != null)
                            ? jdbc.query("""
                                    SELECT night_date, base_price_cents, tax_cents, fee_cents
                                    FROM accommodation_nightly_inventory
                                    WHERE accommodation_unit_id = ? AND night_date >= ? AND night_date < ?
                                    ORDER BY night_date ASC
                                    """,
                                    (nr, ni) -> new StayNight(nr.getObject(1, LocalDate.class), nr.getLong(2), nr.getLong(3), nr.getLong(4)),
                                    unitId, startDate, endDate)
                            : List.of();
                    return new StaySelection(unitId, unitCount, propName, uName, List.copyOf(nights));
                }, draftId).stream().findFirst().orElse(null);
        RentalSelection rental = jdbc.query("""
                SELECT r.rental_unit_id, r.pickup_at, r.return_at,
                       loc.name AS location_name, c.name AS vehicle_class_name, u.unit_identifier,
                       c.daily_base_price_cents, c.daily_tax_cents, c.daily_fee_cents
                FROM detour_trip_draft_rental_selection r
                JOIN rental_unit u ON u.id = r.rental_unit_id
                JOIN rental_vehicle_class c ON c.id = u.rental_vehicle_class_id
                JOIN rental_location loc ON loc.id = c.rental_location_id
                WHERE r.draft_id = ?
                """,
                (r, n) -> new RentalSelection(
                        r.getLong(1),
                        r.getObject(2, OffsetDateTime.class),
                        r.getObject(3, OffsetDateTime.class),
                        r.getString(4),
                        r.getString(5),
                        r.getString(6),
                        r.getLong(7),
                        r.getLong(8),
                        r.getLong(9)
                ), draftId).stream().findFirst().orElse(null);
        return new DraftSelections(airfare, stay, rental);
    }

    private DraftSelections loadPlannedSelections(long plannedId) {
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
                FROM detour_planned_airfare_snapshot WHERE planned_itinerary_id = ?
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
                ), plannedId).stream().findFirst().orElse(null);

        StaySelection stay = jdbc.query("""
                SELECT accommodation_unit_id, unit_count, property_name, unit_name,
                       property_category, location_description, distance_to_city_center_meters,
                       guest_capacity, required_room_count
                FROM detour_planned_stay_snapshot WHERE planned_itinerary_id = ?
                """, (r, n) -> {
            List<StayNight> nights = jdbc.query("""
                    SELECT night_date, base_price_cents, tax_cents, fee_cents
                    FROM detour_planned_stay_night_snapshot WHERE planned_itinerary_id = ? ORDER BY night_date
                    """, (night, ignored) -> new StayNight(night.getObject(1, LocalDate.class), night.getLong(2), night.getLong(3), night.getLong(4)), plannedId);
            return new StaySelection(
                    r.getLong(1), r.getInt(2), r.getString(3), r.getString(4), List.copyOf(nights),
                    r.getString(5), r.getString(6), (Integer) r.getObject(7), (Integer) r.getObject(8), (Integer) r.getObject(9)
            );
        }, plannedId).stream().findFirst().orElse(null);

        RentalSelection rental = jdbc.query("""
                SELECT rental_unit_id, pickup_at, return_at, location_name, vehicle_class_name,
                       unit_identifier, daily_base_price_cents, daily_tax_cents, daily_fee_cents, vehicle_category
                FROM detour_planned_rental_snapshot WHERE planned_itinerary_id = ?
                """, (r, n) -> new RentalSelection(
                        r.getLong(1), r.getObject(2, OffsetDateTime.class), r.getObject(3, OffsetDateTime.class),
                        r.getString(4), r.getString(5), r.getString(6), r.getLong(7), r.getLong(8), r.getLong(9),
                        r.getString(10)
                ), plannedId).stream().findFirst().orElse(null);

        return new DraftSelections(airfare, stay, rental);
    }

    @Override public boolean advanceVersion(long tripId, long ownerUserId, long expectedVersion) { return jdbc.update("UPDATE detour_trip SET version = version + 1 WHERE id = ? AND owner_user_id = ? AND version = ?", tripId, ownerUserId, expectedVersion) == 1; }
    @Override public boolean advanceVersionForDraft(long tripId, long ownerUserId, long expectedVersion, long draftId, long expectedDraftVersion) { return jdbc.update("UPDATE detour_trip SET version = version + 1 WHERE id = ? AND owner_user_id = ? AND version = ? AND EXISTS (SELECT 1 FROM detour_trip_draft WHERE id = ? AND trip_id = ? AND version = ?)", tripId, ownerUserId, expectedVersion, draftId, tripId, expectedDraftVersion) == 1; }

    @Override public boolean advanceVersionForDraftMutation(long tripId, long ownerUserId, long expectedVersion, long draftId, long expectedDraftVersion) {
        int updatedTrip = jdbc.update("""
                UPDATE detour_trip SET version = version + 1
                WHERE id = ? AND owner_user_id = ? AND version = ?
                  AND EXISTS (SELECT 1 FROM detour_trip_draft WHERE id = ? AND trip_id = ? AND version = ?)
                """, tripId, ownerUserId, expectedVersion, draftId, tripId, expectedDraftVersion);
        if (updatedTrip != 1) {
            return false;
        }
        int updatedDraft = jdbc.update("""
                UPDATE detour_trip_draft SET version = version + 1
                WHERE id = ? AND trip_id = ? AND version = ?
                """, draftId, tripId, expectedDraftVersion);
        return updatedDraft == 1;
    }

    @Override public void saveDraftAirfareSelection(long draftId, long outboundFlightInstanceId, long returnFlightInstanceId) {
        jdbc.update("DELETE FROM detour_trip_draft_airfare_selection WHERE draft_id = ?", draftId);
        jdbc.update("INSERT INTO detour_trip_draft_airfare_selection (draft_id, outbound_flight_instance_id, return_flight_instance_id) VALUES (?, ?, ?)",
                draftId, outboundFlightInstanceId, returnFlightInstanceId);
    }

    @Override public void replaceSharedDetails(long tripId, Destination destination, LocalDate startDate, LocalDate endDate, int travelerCount, List<Integer> ages, Long budget, String label) {
        jdbc.update("UPDATE detour_trip SET catalog_destination_id = ?, start_date = ?, end_date = ?, traveler_count = ?, budget_cents = ?, display_label = ? WHERE id = ?", destination.id(), startDate, endDate, travelerCount, budget, label, tripId);
        jdbc.update("DELETE FROM detour_trip_traveler WHERE trip_id = ?", tripId);
        for (int ordinal = 0; ordinal < travelerCount; ordinal++) jdbc.update("INSERT INTO detour_trip_traveler (trip_id, traveler_ordinal, age) VALUES (?, ?, ?)", tripId, ordinal + 1, ages.get(ordinal));
    }
    @Override public void insertDraft(long tripId, UUID publicId) { jdbc.update("INSERT INTO detour_trip_draft (public_id, trip_id, version) VALUES (?, ?, 0)", publicId, tripId); }
    @Override public void deleteDraft(long tripId, long draftId) { jdbc.update("DELETE FROM detour_trip_draft WHERE trip_id = ? AND id = ?", tripId, draftId); }
    @Override public void deletePlanned(long tripId, long plannedId) { jdbc.update("DELETE FROM detour_planned_itinerary WHERE trip_id = ? AND id = ?", tripId, plannedId); }
    @Override public void deleteTrip(long tripId, long ownerUserId) { jdbc.update("DELETE FROM detour_trip WHERE id = ? AND owner_user_id = ?", tripId, ownerUserId); }
    @Override
    public boolean hasBookingHistory(long tripId) {
        Integer count = jdbc.queryForObject("SELECT COUNT(*) FROM detour_booking WHERE trip_id = ?", Integer.class, tripId);
        return count != null && count > 0;
    }

    @Override
    public int activeBookingCount(long tripId) {
        Integer count = jdbc.queryForObject("SELECT COUNT(*) FROM detour_booking WHERE trip_id = ? AND status = 'ACTIVE'", Integer.class, tripId);
        return count != null ? count : 0;
    }

    @Override public void insertDraftCopy(long tripId, UUID publicId, DraftSelections selections) {
        insertDraft(tripId, publicId);
        long draftId = jdbc.queryForObject("SELECT id FROM detour_trip_draft WHERE public_id = ?", Long.class, publicId);
        insertDraftSelections(draftId, selections);
    }
    private void insertDraftSelections(long draftId, DraftSelections selections) {
        if (selections.airfare() != null) jdbc.update("INSERT INTO detour_trip_draft_airfare_selection (draft_id, outbound_flight_instance_id, return_flight_instance_id) VALUES (?, ?, ?)", draftId, selections.airfare().outboundFlightInstanceId(), selections.airfare().returnFlightInstanceId());
        if (selections.stay() != null) jdbc.update("INSERT INTO detour_trip_draft_stay_selection (draft_id, accommodation_unit_id, unit_count) VALUES (?, ?, ?)", draftId, selections.stay().accommodationUnitId(), selections.stay().unitCount());
        if (selections.rental() != null) jdbc.update("INSERT INTO detour_trip_draft_rental_selection (draft_id, rental_unit_id, pickup_at, return_at) VALUES (?, ?, ?, ?)", draftId, selections.rental().rentalUnitId(), selections.rental().pickupAt(), selections.rental().returnAt());
    }
    @Override
    public void insertPlanned(long tripId, UUID publicId, DraftSelections s) {
        KeyHolder keys = new GeneratedKeyHolder();
        jdbc.update(c -> {
            PreparedStatement p = c.prepareStatement(
                    "INSERT INTO detour_planned_itinerary (public_id, trip_id) VALUES (?, ?)",
                    Statement.RETURN_GENERATED_KEYS);
            p.setObject(1, publicId);
            p.setLong(2, tripId);
            return p;
        }, keys);
        Number key = keys.getKeys() == null ? null : (Number) keys.getKeys().get("ID");
        if (key == null) throw new IllegalStateException("Planned insert did not return a key");
        long id = key.longValue();

        if (s.airfare() != null) {
            AirfareSelection a = s.airfare();
            jdbc.update("""
                    INSERT INTO detour_planned_airfare_snapshot (
                        planned_itinerary_id, outbound_flight_instance_id, return_flight_instance_id,
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
                    ) VALUES (
                        ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?,
                        ?, ?, ?, ?, ?, ?, ?, ?, ?, ?,
                        ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?
                    )
                    """,
                    id, a.outboundFlightInstanceId(), a.returnFlightInstanceId(),
                    a.outboundDescription(), a.returnDescription(),
                    a.outboundBaseFareCents(), a.outboundTaxCents(), a.outboundFeeCents(),
                    a.returnBaseFareCents(), a.returnTaxCents(), a.returnFeeCents(),
                    a.outboundCarrierName(), a.outboundFlightNumber(), a.outboundStopCount(),
                    a.outboundLayoverAirportCode(), a.outboundLayoverDurationMinutes(),
                    a.outboundDepartureTime(), a.outboundArrivalTime(),
                    a.outboundDepartureTimeZone(), a.outboundArrivalTimeZone(), a.outboundDurationMinutes(),
                    a.returnCarrierName(), a.returnFlightNumber(), a.returnStopCount(),
                    a.returnLayoverAirportCode(), a.returnLayoverDurationMinutes(),
                    a.returnDepartureTime(), a.returnArrivalTime(),
                    a.returnDepartureTimeZone(), a.returnArrivalTimeZone(), a.returnDurationMinutes(),
                    a.totalDurationMinutes());
        }

        if (s.stay() != null) {
            StaySelection st = s.stay();
            jdbc.update("""
                    INSERT INTO detour_planned_stay_snapshot (
                        planned_itinerary_id, accommodation_unit_id, unit_count, property_name, unit_name,
                        property_category, location_description, distance_to_city_center_meters,
                        guest_capacity, required_room_count
                    ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                    """,
                    id, st.accommodationUnitId(), st.unitCount(), st.propertyName(), st.unitName(),
                    st.propertyCategory(), st.locationDescription(), st.distanceToCityCenterMeters(),
                    st.guestCapacity(), st.requiredRoomCount() != null ? st.requiredRoomCount() : st.unitCount());
            for (StayNight n : st.nights()) {
                jdbc.update("""
                        INSERT INTO detour_planned_stay_night_snapshot (
                            planned_itinerary_id, night_date, base_price_cents, tax_cents, fee_cents
                        ) VALUES (?, ?, ?, ?, ?)
                        """, id, n.date(), n.basePriceCents(), n.taxCents(), n.feeCents());
            }
        }

        if (s.rental() != null) {
            RentalSelection r = s.rental();
            jdbc.update("""
                    INSERT INTO detour_planned_rental_snapshot (
                        planned_itinerary_id, rental_unit_id, pickup_at, return_at, location_name,
                        vehicle_class_name, unit_identifier, daily_base_price_cents, daily_tax_cents,
                        daily_fee_cents, vehicle_category
                    ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                    """,
                    id, r.rentalUnitId(), r.pickupAt(), r.returnAt(), r.locationName(),
                    r.vehicleClassName(), r.unitIdentifier(), r.dailyBasePriceCents(), r.dailyTaxCents(),
                    r.dailyFeeCents(), r.vehicleCategory());
        }
    }

    @Override public DraftSelections resolveSelectionsForPromotion(Trip trip, TripDraft draft) {
        DraftSelections raw = draft.selections();
        AirfareSelection airfare = raw.airfare() == null ? null : resolveAirfare(trip, raw.airfare());
        StaySelection stay = raw.stay() == null ? null : resolveStay(trip, raw.stay());
        RentalSelection rental = raw.rental() == null ? null : resolveRental(trip, raw.rental());
        return new DraftSelections(airfare, stay, rental);
    }

    private AirfareSelection resolveAirfare(Trip trip, AirfareSelection selected) {
        return jdbc.query("""
                SELECT out_i.id AS out_id, in_i.id AS in_id,
                       out_s.flight_number AS out_fn, in_s.flight_number AS in_fn,
                       out_i.base_fare_cents AS out_base, out_i.tax_cents AS out_tax, out_i.fee_cents AS out_fee,
                       in_i.base_fare_cents AS in_base, in_i.tax_cents AS in_tax, in_i.fee_cents AS in_fee,
                       out_sup.name AS out_carrier, out_s.stop_count AS out_stops,
                       out_lay.iata_code AS out_lay_code,
                       TIMESTAMPDIFF(MINUTE, out_seg1.arrival_at, out_seg2.departure_at) AS out_lay_dur,
                       out_seg1.departure_at AS out_dep_time,
                       COALESCE(out_seg2.arrival_at, out_seg1.arrival_at) AS out_arr_time,
                       out_orig.time_zone_id AS out_dep_tz,
                       out_dest.time_zone_id AS out_arr_tz,
                       in_sup.name AS in_carrier, in_s.stop_count AS in_stops,
                       in_lay.iata_code AS in_lay_code,
                       TIMESTAMPDIFF(MINUTE, in_seg1.arrival_at, in_seg2.departure_at) AS in_lay_dur,
                       in_seg1.departure_at AS in_dep_time,
                       COALESCE(in_seg2.arrival_at, in_seg1.arrival_at) AS in_arr_time,
                       in_orig.time_zone_id AS in_dep_tz,
                       in_dest.time_zone_id AS in_arr_tz
                FROM flight_instance out_i
                JOIN flight_schedule out_s ON out_s.id = out_i.flight_schedule_id
                JOIN catalog_supplier out_sup ON out_sup.id = out_s.supplier_id
                JOIN catalog_airport out_orig ON out_orig.id = out_s.origin_airport_id
                JOIN catalog_airport out_dest ON out_dest.id = out_s.destination_airport_id
                JOIN flight_instance_segment out_seg1 ON out_seg1.flight_instance_id = out_i.id AND out_seg1.segment_ordinal = 1
                LEFT JOIN flight_instance_segment out_seg2 ON out_seg2.flight_instance_id = out_i.id AND out_seg2.segment_ordinal = 2
                LEFT JOIN flight_schedule_segment out_sched1 ON out_sched1.flight_schedule_id = out_s.id AND out_sched1.segment_ordinal = 1 AND out_s.stop_count = 1
                LEFT JOIN catalog_airport out_lay ON out_lay.id = out_sched1.destination_airport_id
                JOIN flight_instance in_i ON in_i.id = ?
                JOIN flight_schedule in_s ON in_s.id = in_i.flight_schedule_id
                JOIN catalog_supplier in_sup ON in_sup.id = in_s.supplier_id
                JOIN catalog_airport in_orig ON in_orig.id = in_s.origin_airport_id
                JOIN catalog_airport in_dest ON in_dest.id = in_s.destination_airport_id
                JOIN flight_instance_segment in_seg1 ON in_seg1.flight_instance_id = in_i.id AND in_seg1.segment_ordinal = 1
                LEFT JOIN flight_instance_segment in_seg2 ON in_seg2.flight_instance_id = in_i.id AND in_seg2.segment_ordinal = 2
                LEFT JOIN flight_schedule_segment in_sched1 ON in_sched1.flight_schedule_id = in_s.id AND in_sched1.segment_ordinal = 1 AND in_s.stop_count = 1
                LEFT JOIN catalog_airport in_lay ON in_lay.id = in_sched1.destination_airport_id
                WHERE out_i.id = ? AND out_orig.iata_code = 'PDX' AND out_dest.destination_id = ? AND out_i.service_date = ?
                  AND in_orig.destination_id = ? AND in_dest.iata_code = 'PDX' AND in_i.service_date = ?
                """,
                (r, n) -> {
                    OffsetDateTime outDep = r.getObject("out_dep_time", OffsetDateTime.class);
                    OffsetDateTime outArr = r.getObject("out_arr_time", OffsetDateTime.class);
                    OffsetDateTime inDep = r.getObject("in_dep_time", OffsetDateTime.class);
                    OffsetDateTime inArr = r.getObject("in_arr_time", OffsetDateTime.class);
                    int outDur = (outDep != null && outArr != null) ? (int) java.time.Duration.between(outDep, outArr).toMinutes() : 0;
                    int inDur = (inDep != null && inArr != null) ? (int) java.time.Duration.between(inDep, inArr).toMinutes() : 0;
                    Integer outLayDur = r.getObject("out_lay_dur") != null ? ((Number) r.getObject("out_lay_dur")).intValue() : null;
                    Integer inLayDur = r.getObject("in_lay_dur") != null ? ((Number) r.getObject("in_lay_dur")).intValue() : null;
                    return new AirfareSelection(
                            r.getLong("out_id"), r.getLong("in_id"),
                            "Flight " + r.getString("out_fn"), "Flight " + r.getString("in_fn"),
                            r.getLong("out_base"), r.getLong("out_tax"), r.getLong("out_fee"),
                            r.getLong("in_base"), r.getLong("in_tax"), r.getLong("in_fee"),
                            r.getString("out_carrier"), r.getString("out_fn"), r.getInt("out_stops"),
                            r.getString("out_lay_code"), outLayDur,
                            outDep, outArr,
                            r.getString("out_dep_tz"), r.getString("out_arr_tz"), outDur,
                            r.getString("in_carrier"), r.getString("in_fn"), r.getInt("in_stops"),
                            r.getString("in_lay_code"), inLayDur,
                            inDep, inArr,
                            r.getString("in_dep_tz"), r.getString("in_arr_tz"), inDur,
                            outDur + inDur
                    );
                },
                selected.returnFlightInstanceId(), selected.outboundFlightInstanceId(),
                trip.destination().id(), trip.startDate(), trip.destination().id(), trip.endDate())
                .stream().findFirst().orElse(null);
    }

    private StaySelection resolveStay(Trip trip, StaySelection selected) {
        return jdbc.query("""
                SELECT unit.id, unit.guest_capacity, property.name, unit.name,
                       property.property_category, property.location_description,
                       property.distance_to_city_center_meters
                FROM accommodation_unit unit
                JOIN accommodation_property property ON property.id = unit.accommodation_property_id
                WHERE unit.id = ? AND property.destination_id = ? AND unit.guest_capacity * ? >= ? AND
                (SELECT COUNT(*) FROM accommodation_nightly_inventory nightly WHERE nightly.accommodation_unit_id = unit.id AND nightly.night_date >= ? AND nightly.night_date < ?) = DATEDIFF('DAY', ?, ?)
                """,
                (r, n) -> {
                    List<StayNight> nights = jdbc.query("""
                            SELECT night_date, base_price_cents, tax_cents, fee_cents
                            FROM accommodation_nightly_inventory
                            WHERE accommodation_unit_id = ? AND night_date >= ? AND night_date < ?
                            ORDER BY night_date ASC
                            """,
                            (x, i) -> new StayNight(x.getObject(1, LocalDate.class), x.getLong(2), x.getLong(3), x.getLong(4)),
                            selected.accommodationUnitId(), trip.startDate(), trip.endDate());
                    int guestCapacity = r.getInt(2);
                    int unitCount = selected.unitCount();
                    int requiredRooms = (int) Math.ceil((double) trip.travelerCount() / guestCapacity);
                    return new StaySelection(
                            r.getLong(1), unitCount, r.getString(3), r.getString(4), List.copyOf(nights),
                            r.getString(5), r.getString(6), (Integer) r.getObject(7), guestCapacity, requiredRooms);
                },
                selected.accommodationUnitId(), trip.destination().id(), selected.unitCount(), trip.travelerCount(),
                trip.startDate(), trip.endDate(), trip.startDate(), trip.endDate())
                .stream().findFirst().orElse(null);
    }

    private RentalSelection resolveRental(Trip trip, RentalSelection selected) {
        return jdbc.query("""
                SELECT unit.id, location.name, class.name, unit.unit_identifier,
                       class.daily_base_price_cents, class.daily_tax_cents, class.daily_fee_cents,
                       class.vehicle_category
                FROM rental_unit unit
                JOIN rental_vehicle_class class ON class.id = unit.rental_vehicle_class_id
                JOIN rental_location location ON location.id = class.rental_location_id
                WHERE unit.id = ? AND location.destination_id = ? AND CAST(? AS DATE) >= ? AND CAST(? AS DATE) <= ?
                """,
                (r, n) -> new RentalSelection(
                        r.getLong(1), selected.pickupAt(), selected.returnAt(),
                        r.getString(2), r.getString(3), r.getString(4),
                        r.getLong(5), r.getLong(6), r.getLong(7),
                        r.getString(8)),
                selected.rentalUnitId(), trip.destination().id(), selected.pickupAt(), trip.startDate(),
                selected.returnAt(), trip.endDate())
                .stream().findFirst().orElse(null);
    }
    @Override public void deleteDraftAirfareSelection(long draftId) {
        jdbc.update("DELETE FROM detour_trip_draft_airfare_selection WHERE draft_id = ?", draftId);
    }
    @Override public void saveDraftStaySelection(long draftId, long accommodationUnitId, int unitCount) {
        jdbc.update("DELETE FROM detour_trip_draft_stay_selection WHERE draft_id = ?", draftId);
        jdbc.update("INSERT INTO detour_trip_draft_stay_selection (draft_id, accommodation_unit_id, unit_count) VALUES (?, ?, ?)",
                draftId, accommodationUnitId, unitCount);
    }
    @Override public void deleteDraftStaySelection(long draftId) {
        jdbc.update("DELETE FROM detour_trip_draft_stay_selection WHERE draft_id = ?", draftId);
    }
    @Override public void saveDraftRentalSelection(long draftId, long rentalUnitId, OffsetDateTime pickupAt, OffsetDateTime returnAt) {
        jdbc.update("DELETE FROM detour_trip_draft_rental_selection WHERE draft_id = ?", draftId);
        jdbc.update("INSERT INTO detour_trip_draft_rental_selection (draft_id, rental_unit_id, pickup_at, return_at) VALUES (?, ?, ?, ?)",
                draftId, rentalUnitId, pickupAt, returnAt);
    }
    @Override public void deleteDraftRentalSelection(long draftId) {
        jdbc.update("DELETE FROM detour_trip_draft_rental_selection WHERE draft_id = ?", draftId);
    }
    @Override public void updateDraftStayUnitCount(long draftId, int unitCount) {
        jdbc.update("UPDATE detour_trip_draft_stay_selection SET unit_count = ? WHERE draft_id = ?", unitCount, draftId);
    }

    @Override
    public AirfareRevalidation revalidateAirfare(long destinationId, LocalDate startDate, LocalDate endDate,
            int newTravelerCount, int oldTravelerCount, AirfareSelection selection, UUID draftPublicId) {
        if (selection == null) return new AirfareRevalidation(false, null, null, null);
        List<AirfareFlightData> rows = jdbc.query("""
                SELECT out_i.id, in_i.id,
                       out_i.service_date AS out_date, out_i.available_seats AS out_seats,
                       out_i.base_fare_cents + out_i.tax_cents + out_i.fee_cents AS out_total,
                       in_i.service_date AS in_date, in_i.available_seats AS in_seats,
                       in_i.base_fare_cents + in_i.tax_cents + in_i.fee_cents AS in_total,
                       out_orig.iata_code AS out_orig_iata, out_dest.destination_id AS out_dest_dest_id,
                       in_orig.destination_id AS in_orig_dest_id, in_dest.iata_code AS in_dest_iata
                FROM flight_instance out_i
                JOIN flight_schedule out_s ON out_s.id = out_i.flight_schedule_id
                JOIN catalog_airport out_orig ON out_orig.id = out_s.origin_airport_id
                JOIN catalog_airport out_dest ON out_dest.id = out_s.destination_airport_id
                JOIN flight_instance in_i ON in_i.id = ?
                JOIN flight_schedule in_s ON in_s.id = in_i.flight_schedule_id
                JOIN catalog_airport in_orig ON in_orig.id = in_s.origin_airport_id
                JOIN catalog_airport in_dest ON in_dest.id = in_s.destination_airport_id
                WHERE out_i.id = ?""",
                (r, n) -> new AirfareFlightData(
                        r.getObject("out_date", LocalDate.class), r.getInt("out_seats"), r.getLong("out_total"),
                        r.getObject("in_date", LocalDate.class), r.getInt("in_seats"), r.getLong("in_total"),
                        r.getString("out_orig_iata"), r.getLong("out_dest_dest_id"),
                        r.getLong("in_orig_dest_id"), r.getString("in_dest_iata")),
                selection.returnFlightInstanceId(), selection.outboundFlightInstanceId());

        if (rows.isEmpty()) {
            return new AirfareRevalidation(false, null,
                    new ComponentRemovalResponse(draftPublicId, "airfare", "Flights do not match revised destination or dates."), null);
        }
        AirfareFlightData data = rows.get(0);
        boolean routeAndDatesMatch = "PDX".equals(data.outOrigIata()) && data.outDestDestId() == destinationId
                && startDate.equals(data.outDate())
                && data.inOrigDestId() == destinationId && "PDX".equals(data.inDestIata())
                && endDate.equals(data.inDate());

        if (!routeAndDatesMatch) {
            return new AirfareRevalidation(false, null,
                    new ComponentRemovalResponse(draftPublicId, "airfare", "Flights do not match revised destination or dates."), null);
        }

        if (data.outSeats() < newTravelerCount || data.inSeats() < newTravelerCount) {
            return new AirfareRevalidation(false, null,
                    new ComponentRemovalResponse(draftPublicId, "airfare", "Flight seat capacity is insufficient for " + newTravelerCount + " travelers."), null);
        }

        long flightPerSeat = data.outTotal() + data.inTotal();
        long oldPrice = (long) oldTravelerCount * flightPerSeat;
        long newPrice = (long) newTravelerCount * flightPerSeat;
        ComponentAdjustmentResponse adjustment = null;
        if (oldPrice != newPrice) {
            adjustment = new ComponentAdjustmentResponse(draftPublicId, "airfare", "PRICE", null, null, oldPrice, newPrice,
                    "Airfare repriced for " + newTravelerCount + " travelers.");
        }
        return new AirfareRevalidation(true, selection, null, adjustment);
    }

    @Override
    public StayRevalidation revalidateStay(long destinationId, LocalDate startDate, LocalDate endDate,
            LocalDate oldStartDate, LocalDate oldEndDate, int newTravelerCount, int oldTravelerCount,
            StaySelection selection, UUID draftPublicId) {
        if (selection == null) return new StayRevalidation(false, 0, null, null);
        List<StayUnitData> unitRows = jdbc.query("""
                SELECT unit.id, unit.unit_kind, unit.guest_capacity, unit.inventory_capacity, property.destination_id
                FROM accommodation_unit unit
                JOIN accommodation_property property ON property.id = unit.accommodation_property_id
                WHERE unit.id = ?""",
                (r, n) -> new StayUnitData(r.getLong("id"), r.getString("unit_kind"), r.getInt("guest_capacity"),
                        r.getInt("inventory_capacity"), r.getLong("destination_id")),
                selection.accommodationUnitId());

        if (unitRows.isEmpty()) {
            return new StayRevalidation(false, 0,
                    new ComponentRemovalResponse(draftPublicId, "stay", "Accommodation does not match revised destination."), null);
        }
        StayUnitData unit = unitRows.get(0);
        if (unit.destinationId() != destinationId) {
            return new StayRevalidation(false, 0,
                    new ComponentRemovalResponse(draftPublicId, "stay", "Accommodation does not match revised destination."), null);
        }

        long requiredNights = ChronoUnit.DAYS.between(startDate, endDate);
        List<StayNightData> nights = jdbc.query("""
                SELECT night_date, available_inventory, base_price_cents + tax_cents + fee_cents AS night_total
                FROM accommodation_nightly_inventory
                WHERE accommodation_unit_id = ? AND night_date >= ? AND night_date < ?
                ORDER BY night_date""",
                (r, n) -> new StayNightData(r.getObject("night_date", LocalDate.class), r.getInt("available_inventory"), r.getLong("night_total")),
                selection.accommodationUnitId(), startDate, endDate);

        if (nights.size() != requiredNights) {
            return new StayRevalidation(false, 0,
                    new ComponentRemovalResponse(draftPublicId, "stay", "Accommodation has no inventory for revised dates."), null);
        }

        int minAvailable = nights.stream().mapToInt(StayNightData::availableInventory).min().orElse(0);
        int requiredRooms;
        if ("WHOLE_PROPERTY".equals(unit.unitKind())) {
            if (newTravelerCount > unit.guestCapacity()) {
                return new StayRevalidation(false, 0,
                        new ComponentRemovalResponse(draftPublicId, "stay", "Traveler count exceeds whole-property capacity (" + unit.guestCapacity() + ")."), null);
            }
            if (minAvailable < 1) {
                return new StayRevalidation(false, 0,
                        new ComponentRemovalResponse(draftPublicId, "stay", "Accommodation has insufficient inventory for revised dates."), null);
            }
            requiredRooms = 1;
        } else {
            requiredRooms = (int) Math.ceil((double) newTravelerCount / unit.guestCapacity());
            if (minAvailable < requiredRooms) {
                return new StayRevalidation(false, 0,
                        new ComponentRemovalResponse(draftPublicId, "stay", "Insufficient room inventory (" + minAvailable + " available, " + requiredRooms + " required) for " + newTravelerCount + " travelers."), null);
            }
        }

        long newNightlySum = nights.stream().mapToLong(StayNightData::nightTotal).sum();
        long newPrice = (long) requiredRooms * newNightlySum;

        long oldPrice;
        if (selection.nights() != null && !selection.nights().isEmpty()) {
            oldPrice = (long) selection.unitCount() * selection.nights().stream().mapToLong(n -> n.basePriceCents() + n.taxCents() + n.feeCents()).sum();
        } else if (oldStartDate != null && oldEndDate != null) {
            Long oldSum = jdbc.queryForObject("""
                    SELECT COALESCE(SUM(base_price_cents + tax_cents + fee_cents), 0)
                    FROM accommodation_nightly_inventory
                    WHERE accommodation_unit_id = ? AND night_date >= ? AND night_date < ?""",
                    Long.class, selection.accommodationUnitId(), oldStartDate, oldEndDate);
            oldPrice = (long) selection.unitCount() * (oldSum == null ? 0L : oldSum);
        } else {
            oldPrice = newPrice;
        }

        ComponentAdjustmentResponse adjustment = null;
        if (requiredRooms != selection.unitCount()) {
            adjustment = new ComponentAdjustmentResponse(draftPublicId, "stay", "ROOM_COUNT_AND_PRICE",
                    selection.unitCount(), requiredRooms, oldPrice, newPrice,
                    "Adjusted to " + requiredRooms + " rooms for " + newTravelerCount + " travelers.");
        } else if (oldPrice != newPrice) {
            adjustment = new ComponentAdjustmentResponse(draftPublicId, "stay", "PRICE",
                    selection.unitCount(), requiredRooms, oldPrice, newPrice, "Stay repriced for revised dates.");
        }

        return new StayRevalidation(true, requiredRooms, null, adjustment);
    }

    @Override
    public RentalRevalidation revalidateRental(long destinationId, LocalDate startDate, LocalDate endDate,
            List<Integer> ages, RentalSelection selection, UUID draftPublicId) {
        if (selection == null) return new RentalRevalidation(false, null, null, null);
        List<RentalUnitData> unitRows = jdbc.query("""
                SELECT unit.id, location.destination_id, class.catalog_key,
                       class.daily_base_price_cents + class.daily_tax_cents + class.daily_fee_cents AS daily_total
                FROM rental_unit unit
                JOIN rental_vehicle_class class ON class.id = unit.rental_vehicle_class_id
                JOIN rental_location location ON location.id = class.rental_location_id
                WHERE unit.id = ?""",
                (r, n) -> new RentalUnitData(r.getLong("id"), r.getLong("destination_id"), r.getString("catalog_key"),
                        r.getLong("daily_total")),
                selection.rentalUnitId());

        if (unitRows.isEmpty()) {
            return new RentalRevalidation(false, null,
                    new ComponentRemovalResponse(draftPublicId, "rental", "Rental location does not match revised destination."), null);
        }
        RentalUnitData unit = unitRows.get(0);
        if (unit.destinationId() != destinationId) {
            return new RentalRevalidation(false, null,
                    new ComponentRemovalResponse(draftPublicId, "rental", "Rental location does not match revised destination."), null);
        }

        LocalDate pickupDate = selection.pickupAt().toLocalDate();
        LocalDate returnDate = selection.returnAt().toLocalDate();
        if (pickupDate.isBefore(startDate) || returnDate.isAfter(endDate) || !selection.pickupAt().isBefore(selection.returnAt())) {
            return new RentalRevalidation(false, null,
                    new ComponentRemovalResponse(draftPublicId, "rental", "Rental dates fall outside revised trip dates."), null);
        }

        boolean hasDriver = ages != null && ages.stream().anyMatch(age -> age != null && age >= 25);
        if (!hasDriver) {
            return new RentalRevalidation(false, null,
                    new ComponentRemovalResponse(draftPublicId, "rental", "Rental cars require at least one driver aged 25 or older."), null);
        }

        return new RentalRevalidation(true, selection, null, null);
    }

    private record AirfareFlightData(LocalDate outDate, int outSeats, long outTotal, LocalDate inDate, int inSeats, long inTotal, String outOrigIata, long outDestDestId, long inOrigDestId, String inDestIata) { }
    private record StayUnitData(long id, String unitKind, int guestCapacity, int inventoryCapacity, long destinationId) { }
    private record StayNightData(LocalDate date, int availableInventory, long nightTotal) { }
    private record RentalUnitData(long id, long destinationId, String catalogKey, long dailyTotal) { }
}
