package app.detour.booking;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashSet;
import java.util.Set;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;

class BookingReferenceGeneratorTest {
    private static final Pattern BOOKING_PATTERN = Pattern.compile("^DT-[A-Z0-9]{6}$");
    private static final Pattern AIRFARE_PATTERN = Pattern.compile("^FL-[A-Z0-9]{6}$");
    private static final Pattern STAY_PATTERN = Pattern.compile("^HT-[A-Z0-9]{6}$");
    private static final Pattern RENTAL_PATTERN = Pattern.compile("^RC-[A-Z0-9]{6}$");

    @Test
    void generatedReferencesMatchExpectedFormats() {
        for (int i = 0; i < 100; i++) {
            String bookingRef = BookingReferenceGenerator.generateBookingReference();
            String airfareRef = BookingReferenceGenerator.generateAirfareReference();
            String stayRef = BookingReferenceGenerator.generateStayReference();
            String rentalRef = BookingReferenceGenerator.generateRentalReference();

            assertTrue(BOOKING_PATTERN.matcher(bookingRef).matches(), "Booking reference format mismatch: " + bookingRef);
            assertTrue(AIRFARE_PATTERN.matcher(airfareRef).matches(), "Airfare reference format mismatch: " + airfareRef);
            assertTrue(STAY_PATTERN.matcher(stayRef).matches(), "Stay reference format mismatch: " + stayRef);
            assertTrue(RENTAL_PATTERN.matcher(rentalRef).matches(), "Rental reference format mismatch: " + rentalRef);
        }
    }

    @Test
    void generatedReferencesHaveHighEntropyWithoutCollisions() {
        int sampleSize = 10_000;
        Set<String> unique = new HashSet<>(sampleSize);
        for (int i = 0; i < sampleSize; i++) {
            unique.add(BookingReferenceGenerator.generateBookingReference());
        }
        assertEquals(sampleSize, unique.size(), "Collision detected in 10,000 generated booking references");
    }
}
