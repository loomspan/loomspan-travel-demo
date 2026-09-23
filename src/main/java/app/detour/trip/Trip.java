package app.detour.trip;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public record Trip(long id, UUID publicId, long ownerUserId, Destination destination, LocalDate startDate, LocalDate endDate,
        int travelerCount, List<Integer> travelerAges, Long budgetCents, String label, String status, long version,
        List<TripDraft> drafts, List<PlannedItinerary> planned) {

    public Trip(long id, UUID publicId, long ownerUserId, Destination destination, LocalDate startDate, LocalDate endDate,
            int travelerCount, List<Integer> travelerAges, Long budgetCents, String label, long version,
            List<TripDraft> drafts, List<PlannedItinerary> planned) {
        this(id, publicId, ownerUserId, destination, startDate, endDate, travelerCount, travelerAges, budgetCents, label, "ACTIVE", version, drafts, planned);
    }
}
