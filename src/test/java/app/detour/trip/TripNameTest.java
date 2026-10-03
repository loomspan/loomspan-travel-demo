package app.detour.trip;

import java.time.LocalDate;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;

class TripNameTest {
    @Test
    void formatsDateRangesWithoutRepeatingSharedMonthsOrYears() {
        assertName("2027-03-13", "2027-03-20", "Mar 13–20, 2027");
        assertName("2027-03-28", "2027-04-03", "Mar 28 – Apr 3, 2027");
        assertName("2027-12-28", "2028-01-03", "Dec 28, 2027 – Jan 3, 2028");
        assertName("2027-03-03", "2027-03-06", "Mar 3–6, 2027");
        assertName("2027-03-13", "2027-03-13", "Mar 13, 2027");
    }

    private void assertName(String start, String end, String dates) {
        assertEquals("San Francisco — " + dates,
                TripService.label("San Francisco", LocalDate.parse(start), LocalDate.parse(end)));
    }
}
