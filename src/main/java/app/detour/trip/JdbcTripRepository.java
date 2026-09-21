package app.detour.trip;

import java.sql.PreparedStatement;
import java.sql.Statement;
import java.time.LocalDate;
import java.time.OffsetDateTime;
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
        insertDraft(tripId, draftPublicId);
    }

    @Override public Optional<Trip> findByPublicIdAndOwnerUserId(UUID publicId, long ownerUserId) {
        return jdbc.query("""
                SELECT trip.id, trip.public_id, trip.owner_user_id, destination.id AS destination_id, destination.catalog_key, destination.name AS destination_name,
                       trip.start_date, trip.end_date, trip.traveler_count, trip.budget_cents, trip.display_label, trip.version
                FROM detour_trip trip JOIN catalog_destination destination ON destination.id = trip.catalog_destination_id
                WHERE trip.public_id = ? AND trip.owner_user_id = ?""", (r, n) -> loadTrip(r.getLong("id"), r, ownerUserId), publicId, ownerUserId).stream().findFirst();
    }

    private Trip loadTrip(long tripId, java.sql.ResultSet row, long ownerUserId) throws java.sql.SQLException {
        List<Integer> ages = jdbc.query("SELECT age FROM detour_trip_traveler WHERE trip_id = ? ORDER BY traveler_ordinal", (r, n) -> (Integer) r.getObject("age"), tripId);
        List<Integer> knownAges = ages.stream().allMatch(java.util.Objects::nonNull) ? List.copyOf(ages) : null;
        List<TripDraft> drafts = jdbc.query("SELECT id, public_id, version FROM detour_trip_draft WHERE trip_id = ? ORDER BY id", (r, n) -> {
            long draftId = r.getLong("id"); return new TripDraft(draftId, r.getObject("public_id", UUID.class), r.getLong("version"), loadDraftSelections(draftId));
        }, tripId);
        List<PlannedItinerary> planned = jdbc.query("SELECT id, public_id FROM detour_planned_itinerary WHERE trip_id = ? ORDER BY id", (r, n) -> {
            long plannedId = r.getLong("id"); return new PlannedItinerary(plannedId, r.getObject("public_id", UUID.class), loadPlannedSelections(plannedId));
        }, tripId);
        Long budget = (Long) row.getObject("budget_cents");
        return new Trip(tripId, row.getObject("public_id", UUID.class), ownerUserId,
                new Destination(row.getLong("destination_id"), row.getString("catalog_key"), row.getString("destination_name")),
                row.getObject("start_date", LocalDate.class), row.getObject("end_date", LocalDate.class), row.getInt("traveler_count"), knownAges,
                budget, row.getString("display_label"), row.getLong("version"), List.copyOf(drafts), List.copyOf(planned));
    }

    private DraftSelections loadDraftSelections(long draftId) {
        AirfareSelection airfare = jdbc.query("SELECT outbound_flight_instance_id, return_flight_instance_id FROM detour_trip_draft_airfare_selection WHERE draft_id = ?",
                (r, n) -> new AirfareSelection(r.getLong(1), r.getLong(2), null, null, 0, 0, 0, 0, 0, 0), draftId).stream().findFirst().orElse(null);
        StaySelection stay = jdbc.query("SELECT accommodation_unit_id, unit_count FROM detour_trip_draft_stay_selection WHERE draft_id = ?",
                (r,n) -> new StaySelection(r.getLong(1), r.getInt(2), null, null, List.of()), draftId).stream().findFirst().orElse(null);
        RentalSelection rental = jdbc.query("SELECT rental_unit_id, pickup_at, return_at FROM detour_trip_draft_rental_selection WHERE draft_id = ?",
                (r,n) -> new RentalSelection(r.getLong(1), r.getObject(2, OffsetDateTime.class), r.getObject(3, OffsetDateTime.class), null, null, null, 0, 0, 0), draftId).stream().findFirst().orElse(null);
        return new DraftSelections(airfare, stay, rental);
    }

    private DraftSelections loadPlannedSelections(long plannedId) {
        AirfareSelection airfare = jdbc.query("SELECT outbound_flight_instance_id, return_flight_instance_id, outbound_description, return_description, outbound_base_fare_cents, outbound_tax_cents, outbound_fee_cents, return_base_fare_cents, return_tax_cents, return_fee_cents FROM detour_planned_airfare_snapshot WHERE planned_itinerary_id = ?", (r,n) -> new AirfareSelection(r.getLong(1),r.getLong(2),r.getString(3),r.getString(4),r.getLong(5),r.getLong(6),r.getLong(7),r.getLong(8),r.getLong(9),r.getLong(10)), plannedId).stream().findFirst().orElse(null);
        StaySelection stay = jdbc.query("SELECT accommodation_unit_id, unit_count, property_name, unit_name FROM detour_planned_stay_snapshot WHERE planned_itinerary_id = ?", (r,n) -> {
            List<StayNight> nights = jdbc.query("SELECT night_date, base_price_cents, tax_cents, fee_cents FROM detour_planned_stay_night_snapshot WHERE planned_itinerary_id = ? ORDER BY night_date", (night, ignored) -> new StayNight(night.getObject(1, LocalDate.class), night.getLong(2), night.getLong(3), night.getLong(4)), plannedId);
            return new StaySelection(r.getLong(1), r.getInt(2), r.getString(3), r.getString(4), List.copyOf(nights));
        }, plannedId).stream().findFirst().orElse(null);
        RentalSelection rental = jdbc.query("SELECT rental_unit_id, pickup_at, return_at, location_name, vehicle_class_name, unit_identifier, daily_base_price_cents, daily_tax_cents, daily_fee_cents FROM detour_planned_rental_snapshot WHERE planned_itinerary_id = ?",
                (r,n) -> new RentalSelection(r.getLong(1),r.getObject(2,OffsetDateTime.class),r.getObject(3,OffsetDateTime.class),r.getString(4),r.getString(5),r.getString(6),r.getLong(7),r.getLong(8),r.getLong(9)), plannedId).stream().findFirst().orElse(null);
        return new DraftSelections(airfare, stay, rental);
    }

    @Override public boolean advanceVersion(long tripId, long ownerUserId, long expectedVersion) { return jdbc.update("UPDATE detour_trip SET version = version + 1 WHERE id = ? AND owner_user_id = ? AND version = ?", tripId, ownerUserId, expectedVersion) == 1; }
    @Override public boolean advanceVersionForDraft(long tripId, long ownerUserId, long expectedVersion, long draftId, long expectedDraftVersion) { return jdbc.update("UPDATE detour_trip SET version = version + 1 WHERE id = ? AND owner_user_id = ? AND version = ? AND EXISTS (SELECT 1 FROM detour_trip_draft WHERE id = ? AND trip_id = ? AND version = ?)", tripId, ownerUserId, expectedVersion, draftId, tripId, expectedDraftVersion) == 1; }

    @Override public void replaceSharedDetails(long tripId, Destination destination, LocalDate startDate, LocalDate endDate, int travelerCount, List<Integer> ages, Long budget, String label) {
        jdbc.update("UPDATE detour_trip SET catalog_destination_id = ?, start_date = ?, end_date = ?, traveler_count = ?, budget_cents = ?, display_label = ? WHERE id = ?", destination.id(), startDate, endDate, travelerCount, budget, label, tripId);
        jdbc.update("DELETE FROM detour_trip_traveler WHERE trip_id = ?", tripId);
        for (int ordinal = 0; ordinal < travelerCount; ordinal++) jdbc.update("INSERT INTO detour_trip_traveler (trip_id, traveler_ordinal, age) VALUES (?, ?, ?)", tripId, ordinal + 1, ages.get(ordinal));
    }
    @Override public void insertDraft(long tripId, UUID publicId) { jdbc.update("INSERT INTO detour_trip_draft (public_id, trip_id, version) VALUES (?, ?, 0)", publicId, tripId); }
    @Override public void deleteDraft(long tripId, long draftId) { jdbc.update("DELETE FROM detour_trip_draft WHERE trip_id = ? AND id = ?", tripId, draftId); }
    @Override public void deletePlanned(long tripId, long plannedId) { jdbc.update("DELETE FROM detour_planned_itinerary WHERE trip_id = ? AND id = ?", tripId, plannedId); }

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
    @Override public void insertPlanned(long tripId, UUID publicId, DraftSelections s) {
        KeyHolder keys = new GeneratedKeyHolder(); jdbc.update(c -> { PreparedStatement p = c.prepareStatement("INSERT INTO detour_planned_itinerary (public_id, trip_id) VALUES (?, ?)", Statement.RETURN_GENERATED_KEYS); p.setObject(1, publicId); p.setLong(2, tripId); return p; }, keys);
        Number key = keys.getKeys() == null ? null : (Number) keys.getKeys().get("ID"); if (key == null) throw new IllegalStateException("Planned insert did not return a key"); long id = key.longValue();
        if (s.airfare() != null) { AirfareSelection a=s.airfare(); jdbc.update("INSERT INTO detour_planned_airfare_snapshot (planned_itinerary_id,outbound_flight_instance_id,return_flight_instance_id,outbound_description,return_description,outbound_base_fare_cents,outbound_tax_cents,outbound_fee_cents,return_base_fare_cents,return_tax_cents,return_fee_cents) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)", id,a.outboundFlightInstanceId(),a.returnFlightInstanceId(),a.outboundDescription(),a.returnDescription(),a.outboundBaseFareCents(),a.outboundTaxCents(),a.outboundFeeCents(),a.returnBaseFareCents(),a.returnTaxCents(),a.returnFeeCents()); }
        if (s.stay() != null) { StaySelection st=s.stay(); jdbc.update("INSERT INTO detour_planned_stay_snapshot (planned_itinerary_id, accommodation_unit_id, unit_count, property_name, unit_name) VALUES (?, ?, ?, ?, ?)", id,st.accommodationUnitId(),st.unitCount(),st.propertyName(),st.unitName()); for (StayNight n: st.nights()) jdbc.update("INSERT INTO detour_planned_stay_night_snapshot (planned_itinerary_id, night_date, base_price_cents, tax_cents, fee_cents) VALUES (?, ?, ?, ?, ?)", id,n.date(),n.basePriceCents(),n.taxCents(),n.feeCents()); }
        if (s.rental() != null) { RentalSelection r=s.rental(); jdbc.update("INSERT INTO detour_planned_rental_snapshot (planned_itinerary_id,rental_unit_id,pickup_at,return_at,location_name,vehicle_class_name,unit_identifier,daily_base_price_cents,daily_tax_cents,daily_fee_cents) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)", id,r.rentalUnitId(),r.pickupAt(),r.returnAt(),r.locationName(),r.vehicleClassName(),r.unitIdentifier(),r.dailyBasePriceCents(),r.dailyTaxCents(),r.dailyFeeCents()); }
    }

    @Override public DraftSelections resolveSelectionsForPromotion(Trip trip, TripDraft draft) {
        DraftSelections raw = draft.selections();
        AirfareSelection airfare = raw.airfare() == null ? null : resolveAirfare(trip, raw.airfare());
        StaySelection stay = raw.stay() == null ? null : resolveStay(trip, raw.stay());
        RentalSelection rental = raw.rental() == null ? null : resolveRental(trip, raw.rental());
        return new DraftSelections(airfare, stay, rental);
    }
    private AirfareSelection resolveAirfare(Trip trip, AirfareSelection selected) { return jdbc.query("""
            SELECT out_i.id, in_i.id, out_s.flight_number, in_s.flight_number, out_i.base_fare_cents, out_i.tax_cents, out_i.fee_cents, in_i.base_fare_cents, in_i.tax_cents, in_i.fee_cents
            FROM flight_instance out_i JOIN flight_schedule out_s ON out_s.id=out_i.flight_schedule_id JOIN catalog_airport out_o ON out_o.id=out_s.origin_airport_id JOIN catalog_airport out_d ON out_d.id=out_s.destination_airport_id
            JOIN flight_instance in_i ON in_i.id=? JOIN flight_schedule in_s ON in_s.id=in_i.flight_schedule_id JOIN catalog_airport in_o ON in_o.id=in_s.origin_airport_id JOIN catalog_airport in_d ON in_d.id=in_s.destination_airport_id
            WHERE out_i.id=? AND out_o.iata_code='PDX' AND out_d.destination_id=? AND out_i.service_date=? AND in_o.destination_id=? AND in_d.iata_code='PDX' AND in_i.service_date=?""",
            (r,n)->new AirfareSelection(r.getLong(1),r.getLong(2),"Flight "+r.getString(3),"Flight "+r.getString(4),r.getLong(5),r.getLong(6),r.getLong(7),r.getLong(8),r.getLong(9),r.getLong(10)), selected.returnFlightInstanceId(),selected.outboundFlightInstanceId(),trip.destination().id(),trip.startDate(),trip.destination().id(),trip.endDate()).stream().findFirst().orElse(null); }
    private StaySelection resolveStay(Trip trip, StaySelection selected) { return jdbc.query("""
            SELECT unit.id, unit.guest_capacity, property.name, unit.name FROM accommodation_unit unit JOIN accommodation_property property ON property.id=unit.accommodation_property_id
            WHERE unit.id=? AND property.destination_id=? AND unit.guest_capacity * ? >= ? AND
            (SELECT COUNT(*) FROM accommodation_nightly_inventory nightly WHERE nightly.accommodation_unit_id=unit.id AND nightly.night_date >= ? AND nightly.night_date < ?) = DATEDIFF('DAY', ?, ?)""",
            (r,n)-> { List<StayNight> nights=jdbc.query("SELECT night_date,base_price_cents,tax_cents,fee_cents FROM accommodation_nightly_inventory WHERE accommodation_unit_id=? AND night_date >= ? AND night_date < ? ORDER BY night_date",(x,i)->new StayNight(x.getObject(1,LocalDate.class),x.getLong(2),x.getLong(3),x.getLong(4)),selected.accommodationUnitId(),trip.startDate(),trip.endDate()); return new StaySelection(r.getLong(1),selected.unitCount(),r.getString(3),r.getString(4),List.copyOf(nights)); }, selected.accommodationUnitId(),trip.destination().id(),selected.unitCount(),trip.travelerCount(),trip.startDate(),trip.endDate(),trip.startDate(),trip.endDate()).stream().findFirst().orElse(null); }
    private RentalSelection resolveRental(Trip trip, RentalSelection selected) { return jdbc.query("""
            SELECT unit.id, location.name, class.name, unit.unit_identifier, class.daily_base_price_cents, class.daily_tax_cents, class.daily_fee_cents
            FROM rental_unit unit JOIN rental_vehicle_class class ON class.id=unit.rental_vehicle_class_id JOIN rental_location location ON location.id=class.rental_location_id
            WHERE unit.id=? AND location.destination_id=? AND CAST(? AS DATE) >= ? AND CAST(? AS DATE) <= ?""",
            (r,n)->new RentalSelection(r.getLong(1),selected.pickupAt(),selected.returnAt(),r.getString(2),r.getString(3),r.getString(4),r.getLong(5),r.getLong(6),r.getLong(7)),selected.rentalUnitId(),trip.destination().id(),selected.pickupAt(),trip.startDate(),selected.returnAt(),trip.endDate()).stream().findFirst().orElse(null); }
}
