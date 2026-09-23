package app.detour.booking;

import java.security.SecureRandom;

public final class BookingReferenceGenerator {
    private static final String ALPHANUMERIC = "ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789";
    private static final SecureRandom RANDOM = new SecureRandom();

    private BookingReferenceGenerator() {
    }

    public static String generateBookingReference() {
        return "DT-" + randomCode(6);
    }

    public static String generateAirfareReference() {
        return "FL-" + randomCode(6);
    }

    public static String generateStayReference() {
        return "HT-" + randomCode(6);
    }

    public static String generateRentalReference() {
        return "RC-" + randomCode(6);
    }

    private static String randomCode(int length) {
        StringBuilder sb = new StringBuilder(length);
        for (int i = 0; i < length; i++) {
            int index = RANDOM.nextInt(ALPHANUMERIC.length());
            sb.append(ALPHANUMERIC.charAt(index));
        }
        return sb.toString();
    }
}
