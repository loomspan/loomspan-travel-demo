package app.detour.catalog;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

@SpringBootTest
class CatalogTemporalMoneyRoundTripIntegrationTest {
    private static final String DATABASE_URL = "jdbc:h2:mem:catalog_temporal_" + UUID.randomUUID() + ";DB_CLOSE_DELAY=-1";

    @DynamicPropertySource
    static void datasourceProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", () -> DATABASE_URL);
    }

    @Autowired
    JdbcTemplate jdbc;

    @Test
    void roundTripsUsdCentsAndAirportZonedFlightInstants() {
        CatalogSchemaFixtures.insertMinimumCatalog(jdbc);
        jdbc.update("INSERT INTO catalog_destination (catalog_key, name, country_code, latitude, longitude) VALUES "
                + "('destination-muc', 'Munich', 'DE', 48.1351, 11.5820), ('destination-mex', 'Mexico City', 'MX', 19.4326, -99.1332)");
        jdbc.update("INSERT INTO catalog_airport (catalog_key, iata_code, name, destination_id, latitude, longitude, time_zone_id) VALUES "
                + "('airport-muc', 'MUC', 'Munich Airport', (SELECT id FROM catalog_destination WHERE catalog_key = 'destination-muc'), 48.3538, 11.7861, 'Europe/Berlin'), "
                + "('airport-mex', 'MEX', 'Benito Juarez International', (SELECT id FROM catalog_destination WHERE catalog_key = 'destination-mex'), 19.4361, -99.0719, 'America/Mexico_City')");
        OffsetDateTime departure = OffsetDateTime.parse("2027-03-10T18:10:00-08:00");
        OffsetDateTime arrival = OffsetDateTime.parse("2027-03-11T13:10:00+01:00");
        jdbc.update("UPDATE flight_instance SET base_fare_cents = ?, tax_cents = ?, fee_cents = ? WHERE catalog_key = 'instance-pdx-sfo-20270310'",
                9_223_372_036_000_000_000L, 1234L, 99L);
        jdbc.update("UPDATE flight_instance_segment SET departure_at = ?, arrival_at = ? WHERE flight_instance_id = "
                + "(SELECT id FROM flight_instance WHERE catalog_key = 'instance-pdx-sfo-20270310')", departure, arrival);

        var row = jdbc.queryForMap("SELECT base_fare_cents, tax_cents, fee_cents FROM flight_instance WHERE catalog_key = 'instance-pdx-sfo-20270310'");
        assertEquals(9_223_372_036_000_000_000L, ((Number) row.get("BASE_FARE_CENTS")).longValue());
        assertEquals(1234L, ((Number) row.get("TAX_CENTS")).longValue());
        assertEquals(99L, ((Number) row.get("FEE_CENTS")).longValue());
        OffsetDateTime[] instants = jdbc.queryForObject("SELECT departure_at, arrival_at FROM flight_instance_segment WHERE flight_instance_id = "
                + "(SELECT id FROM flight_instance WHERE catalog_key = 'instance-pdx-sfo-20270310')",
                (resultSet, rowNumber) -> new OffsetDateTime[] {resultSet.getObject(1, OffsetDateTime.class), resultSet.getObject(2, OffsetDateTime.class)});
        assertEquals(departure, instants[0]);
        assertEquals(arrival, instants[1]);
        ZoneId pdx = airportZone("PDX");
        ZoneId sfo = airportZone("SFO");
        ZoneId muc = airportZone("MUC");
        ZoneId mex = airportZone("MEX");
        assertEquals("2027-03-10", departure.atZoneSameInstant(pdx).toLocalDate().toString());
        assertEquals("2027-03-10", departure.atZoneSameInstant(sfo).toLocalDate().toString());
        assertEquals("2027-03-11", arrival.atZoneSameInstant(muc).toLocalDate().toString());
        assertEquals("2027-03-11", arrival.atZoneSameInstant(mex).toLocalDate().toString());
    }

    private ZoneId airportZone(String iataCode) {
        return ZoneId.of(jdbc.queryForObject("SELECT time_zone_id FROM catalog_airport WHERE iata_code = ?", String.class, iataCode));
    }
}
