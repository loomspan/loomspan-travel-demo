package app.detour.airfare;

import app.detour.airfare.AirfareSearchResponses.FlightLegResponse;
import app.detour.airfare.AirfareSearchResponses.LayoverResponse;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Duration;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class JdbcAirfareSearchRepository implements AirfareSearchRepository {
    private static final LocalDate LAST_ARRIVAL_DATE = LocalDate.of(2027, 3, 31);

    private static final String BASE_FLIGHT_QUERY = """
            SELECT i.id AS instance_id,
                   i.catalog_key AS instance_catalog_key,
                   s.flight_number,
                   s.stop_count,
                   sup.name AS supplier_name,
                   orig.iata_code AS origin_code,
                   orig.name AS origin_name,
                   orig.time_zone_id AS origin_tz,
                   dest.iata_code AS destination_code,
                   dest.name AS destination_name,
                   dest.time_zone_id AS destination_tz,
                   seg1.departure_at AS departure_time,
                   COALESCE(seg2.arrival_at, seg1.arrival_at) AS arrival_time,
                   i.available_seats,
                   i.base_fare_cents,
                   i.tax_cents,
                   i.fee_cents,
                   layover_airport.iata_code AS layover_code,
                   layover_airport.name AS layover_name,
                   seg1.arrival_at AS seg1_arrival,
                   seg2.departure_at AS seg2_departure
            FROM flight_instance i
            JOIN flight_schedule s ON s.id = i.flight_schedule_id
            JOIN catalog_supplier sup ON sup.id = s.supplier_id
            JOIN catalog_airport orig ON orig.id = s.origin_airport_id
            JOIN catalog_airport dest ON dest.id = s.destination_airport_id
            JOIN flight_instance_segment seg1 ON seg1.flight_instance_id = i.id AND seg1.segment_ordinal = 1
            LEFT JOIN flight_instance_segment seg2 ON seg2.flight_instance_id = i.id AND seg2.segment_ordinal = 2
            LEFT JOIN flight_schedule_segment sched_seg1 ON sched_seg1.flight_schedule_id = s.id AND sched_seg1.segment_ordinal = 1 AND s.stop_count = 1
            LEFT JOIN catalog_airport layover_airport ON layover_airport.id = sched_seg1.destination_airport_id
            """;

    private final JdbcTemplate jdbc;

    public JdbcAirfareSearchRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public List<FlightLegResponse> findOutboundLegs(long destinationId, LocalDate serviceDate, int minSeats) {
        String sql = BASE_FLIGHT_QUERY + """
                WHERE orig.iata_code = 'PDX'
                  AND dest.destination_id = ?
                  AND i.service_date = ?
                  AND i.available_seats >= ?
                ORDER BY i.id ASC
                """;
        return jdbc.query(sql, (rs, rowNum) -> mapRow(rs), destinationId, serviceDate, minSeats)
                .stream()
                .filter(this::isArrivalWithinLimit)
                .toList();
    }

    @Override
    public List<FlightLegResponse> findReturnLegs(long destinationId, LocalDate serviceDate, int minSeats) {
        String sql = BASE_FLIGHT_QUERY + """
                WHERE orig.destination_id = ?
                  AND dest.iata_code = 'PDX'
                  AND i.service_date = ?
                  AND i.available_seats >= ?
                ORDER BY i.id ASC
                """;
        return jdbc.query(sql, (rs, rowNum) -> mapRow(rs), destinationId, serviceDate, minSeats)
                .stream()
                .filter(this::isArrivalWithinLimit)
                .toList();
    }

    @Override
    public Optional<FlightLegResponse> findLegById(long flightInstanceId) {
        String sql = BASE_FLIGHT_QUERY + " WHERE i.id = ?";
        return jdbc.query(sql, (rs, rowNum) -> mapRow(rs), flightInstanceId)
                .stream()
                .filter(this::isArrivalWithinLimit)
                .findFirst();
    }

    @Override
    public Optional<String> findAirportIataCodeForDestination(long destinationId) {
        return jdbc.query("SELECT iata_code FROM catalog_airport WHERE destination_id = ?",
                (rs, rowNum) -> rs.getString("iata_code"), destinationId)
                .stream()
                .findFirst();
    }

    private boolean isArrivalWithinLimit(FlightLegResponse leg) {
        ZoneId destZone = ZoneId.of(leg.arrivalTimeZone());
        LocalDate arrivalLocalDate = leg.arrivalTime().atZoneSameInstant(destZone).toLocalDate();
        return !arrivalLocalDate.isAfter(LAST_ARRIVAL_DATE);
    }

    private FlightLegResponse mapRow(ResultSet rs) throws SQLException {
        long id = rs.getLong("instance_id");
        String catalogKey = rs.getString("instance_catalog_key");
        String flightNumber = rs.getString("flight_number");
        int stopCount = rs.getInt("stop_count");
        String carrier = rs.getString("supplier_name");
        String originCode = rs.getString("origin_code");
        String originName = rs.getString("origin_name");
        String originTz = rs.getString("origin_tz");
        String destCode = rs.getString("destination_code");
        String destName = rs.getString("destination_name");
        String destTz = rs.getString("destination_tz");
        OffsetDateTime departure = rs.getObject("departure_time", OffsetDateTime.class);
        OffsetDateTime arrival = rs.getObject("arrival_time", OffsetDateTime.class);
        int availableSeats = rs.getInt("available_seats");
        long baseFareCents = rs.getLong("base_fare_cents");
        long taxCents = rs.getLong("tax_cents");
        long feeCents = rs.getLong("fee_cents");
        long totalFareCents = baseFareCents + taxCents + feeCents;
        long durationMinutes = Duration.between(departure, arrival).toMinutes();

        LayoverResponse layover = null;
        if (stopCount == 1) {
            String layoverCode = rs.getString("layover_code");
            String layoverName = rs.getString("layover_name");
            OffsetDateTime seg1Arrival = rs.getObject("seg1_arrival", OffsetDateTime.class);
            OffsetDateTime seg2Departure = rs.getObject("seg2_departure", OffsetDateTime.class);
            long layoverMinutes = Duration.between(seg1Arrival, seg2Departure).toMinutes();
            layover = new LayoverResponse(layoverCode, layoverName, layoverMinutes);
        }

        return new FlightLegResponse(
                id,
                catalogKey,
                carrier,
                flightNumber,
                stopCount,
                originCode,
                originName,
                destCode,
                destName,
                departure,
                arrival,
                originTz,
                destTz,
                durationMinutes,
                availableSeats,
                baseFareCents,
                taxCents,
                feeCents,
                totalFareCents,
                layover
        );
    }
}
