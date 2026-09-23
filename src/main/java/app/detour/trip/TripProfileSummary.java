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
        String temporalStatus,
        int draftCount,
        int plannedCount,
        int expiredAlternativeCount,
        int bookedCount,
        boolean hasBookingHistory,
        String primaryBookingReference,
        List<AlternativeProfileSummary> alternatives) {
}
