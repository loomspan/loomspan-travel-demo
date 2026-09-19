package app.detour.catalog;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class CatalogFixtureSummaryIntegrationTest {
    @Test
    void summaryOutputIsDeterministicAndConcise() {
        String first = CatalogFixtureSummary.render();
        String second = CatalogFixtureSummary.render();
        assertEquals(first, second);
        assertTrue(first.contains("Phase 2 deterministic catalog fixture summary"));
        assertTrue(first.contains("stay properties=18"));
        assertTrue(first.contains("stay nights=540"));
        assertTrue(first.contains("rental units=21"));
        for (String destination : new String[] {"destination-sfo", "destination-muc", "destination-mex"}) {
            assertTrue(first.contains(destination), destination);
        }
        assertTrue(first.contains("Flights:"));
        for (String destination : new String[] {"destination-sfo", "destination-muc", "destination-mex"}) {
            assertTrue(first.contains(destination + " direct"), destination + " direct flight");
            assertTrue(first.contains(destination + " connection"), destination + " connection flight");
        }
        assertTrue(first.contains("depart="));
        assertTrue(first.contains("arrive="));
        assertTrue(first.contains("available-seats="));
        assertTrue(first.contains("Stays:"));
        assertTrue(first.contains("Rentals:"));
        assertTrue(first.contains("local-interval="));
        assertTrue(first.contains(" -> "));
        assertFalse(first.contains("stay-unit-sfo-hotel-harbor|2027-03-"));
        assertTrue(first.lines().count() < 30, "summary must remain concise");
    }
}
