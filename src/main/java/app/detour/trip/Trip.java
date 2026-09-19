package app.detour.trip;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

record Trip(long id, UUID publicId, long ownerUserId, Destination destination, LocalDate startDate, LocalDate endDate,
        int travelerCount, List<Integer> travelerAges, Long budgetCents, String label, long version,
        List<TripDraft> drafts) {
}

record Destination(long id, String key, String name) {
}
