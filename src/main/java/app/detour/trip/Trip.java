package app.detour.trip;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public record Trip(long id, UUID publicId, long ownerUserId, Destination destination, LocalDate startDate, LocalDate endDate,
        int travelerCount, List<Integer> travelerAges, Long budgetCents, String label, String status, long version,
        List<TripDraft> drafts, List<PlannedItinerary> planned, List<TripPlan> plans, UUID primaryPlanId) {

    public Trip(long id, UUID publicId, long ownerUserId, Destination destination, LocalDate startDate, LocalDate endDate,
            int travelerCount, List<Integer> travelerAges, Long budgetCents, String label, long version,
            List<TripDraft> drafts, List<PlannedItinerary> planned) {
        this(id, publicId, ownerUserId, destination, startDate, endDate, travelerCount, travelerAges, budgetCents, label, "ACTIVE", version, drafts, planned, List.of(), null);
    }

    public Trip(long id, UUID publicId, long ownerUserId, Destination destination, LocalDate startDate, LocalDate endDate,
            int travelerCount, List<Integer> travelerAges, Long budgetCents, String label, String status, long version,
            List<TripDraft> drafts, List<PlannedItinerary> planned) {
        this(id, publicId, ownerUserId, destination, startDate, endDate, travelerCount, travelerAges, budgetCents,
                label, status, version, drafts, planned, List.of(), null);
    }
    public TripPlan primary() { return plans.stream().filter(p -> p.publicId().equals(primaryPlanId)).findFirst().orElseThrow(); }
    public Trip withPlanContext(TripPlan p) {
        return new Trip(id, publicId, ownerUserId, destination, p.startDate(), p.endDate(), p.travelerCount(), p.travelerAges(),
                budgetCents, label, status, version, drafts, planned, plans, primaryPlanId);
    }
    public String name() { return label; }
}
