package app.detour.trip;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public record TripProfileSummary(
        UUID id,
        String destinationKey,
        String destinationName,
        LocalDate startDate,
        LocalDate endDate,
        String label,
        long version,
        String status,
        String temporalStatus,
        int draftCount,
        int plannedCount,
        int expiredAlternativeCount,
        int bookedCount,
        boolean hasBookingHistory,
        String primaryBookingReference,
        List<AlternativeProfileSummary> alternatives, String name) {

    public TripProfileSummary(UUID id, String destinationKey, String destinationName,
            LocalDate startDate, LocalDate endDate, String label, long version, String status,
            String temporalStatus, int draftCount, int plannedCount, int expiredAlternativeCount,
            int bookedCount, boolean hasBookingHistory, String primaryBookingReference,
            List<AlternativeProfileSummary> alternatives) {
        this(id, destinationKey, destinationName, startDate, endDate, label, version, status,
                temporalStatus, draftCount, plannedCount, expiredAlternativeCount, bookedCount,
                hasBookingHistory, primaryBookingReference, alternatives, label);
    }

    public TripProfileSummary(
            UUID id,
            String destinationKey,
            String destinationName,
            LocalDate startDate,
            LocalDate endDate,
            String label,
            long version,
            String temporalStatus,
            int draftCount,
            int plannedCount,
            int expiredAlternativeCount,
            int bookedCount,
            boolean hasBookingHistory,
            String primaryBookingReference,
            List<AlternativeProfileSummary> alternatives) {
        this(id, destinationKey, destinationName, startDate, endDate, label, version, "ACTIVE", temporalStatus,
                draftCount, plannedCount, expiredAlternativeCount, bookedCount, hasBookingHistory, primaryBookingReference, alternatives, label);
    }
}
