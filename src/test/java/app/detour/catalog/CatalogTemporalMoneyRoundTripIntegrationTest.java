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
    void roundTripsUsdCentsAndAirportZonedFlightStayAndRentalInstants() {
        var row = jdbc.queryForMap("SELECT base_fare_cents, tax_cents, fee_cents FROM flight_instance WHERE catalog_key = 'airfare-out-muc-d1-20270310'");
        assertEquals(58_800L, ((Number) row.get("BASE_FARE_CENTS")).longValue());
        assertEquals(4_670L, ((Number) row.get("TAX_CENTS")).longValue());
        assertEquals(610L, ((Number) row.get("FEE_CENTS")).longValue());
        OffsetDateTime[] instants = jdbc.queryForObject("SELECT departure_at, arrival_at FROM flight_instance_segment WHERE flight_instance_id = "
                + "(SELECT id FROM flight_instance WHERE catalog_key = 'airfare-out-muc-d1-20270310')",
                (resultSet, rowNumber) -> new OffsetDateTime[] {resultSet.getObject(1, OffsetDateTime.class), resultSet.getObject(2, OffsetDateTime.class)});
        ZoneId pdx = airportZone("PDX");
        ZoneId sfo = airportZone("SFO");
        ZoneId muc = airportZone("MUC");
        ZoneId mex = airportZone("MEX");
        assertEquals("2027-03-10", instants[0].atZoneSameInstant(pdx).toLocalDate().toString());
        assertEquals("2027-03-11", instants[1].atZoneSameInstant(muc).toLocalDate().toString());
        assertEquals("America/Los_Angeles", sfo.getId());
        assertEquals("America/Mexico_City", mex.getId());
        var nightly = jdbc.queryForMap("SELECT base_price_cents, tax_cents, fee_cents FROM accommodation_nightly_inventory WHERE accommodation_unit_id = (SELECT id FROM accommodation_unit WHERE catalog_key = 'stay-unit-muc-hotel-isar') AND night_date = DATE '2027-03-10'");
        assertEquals(14_814L, ((Number) nightly.get("BASE_PRICE_CENTS")).longValue());
        assertEquals(1_186L, ((Number) nightly.get("TAX_CENTS")).longValue());
        assertEquals(500L, ((Number) nightly.get("FEE_CENTS")).longValue());
        var rental = jdbc.queryForMap("SELECT daily_base_price_cents, daily_tax_cents, daily_fee_cents FROM rental_vehicle_class WHERE catalog_key = 'rental-mex-suv'");
        assertEquals(7_240L, ((Number) rental.get("DAILY_BASE_PRICE_CENTS")).longValue());
        assertEquals(580L, ((Number) rental.get("DAILY_TAX_CENTS")).longValue());
        assertEquals(380L, ((Number) rental.get("DAILY_FEE_CENTS")).longValue());
        assertEquals("2027-03-10", OffsetDateTime.parse("2027-03-10T10:00:00-08:00").atZoneSameInstant(sfo).toLocalDate().toString());
        assertEquals("2027-03-10", OffsetDateTime.parse("2027-03-10T10:00:00+01:00").atZoneSameInstant(muc).toLocalDate().toString());
        assertEquals("2027-03-10", OffsetDateTime.parse("2027-03-10T10:00:00-06:00").atZoneSameInstant(mex).toLocalDate().toString());
    }

    private ZoneId airportZone(String iataCode) {
        return ZoneId.of(jdbc.queryForObject("SELECT time_zone_id FROM catalog_airport WHERE iata_code = ?", String.class, iataCode));
    }
}
