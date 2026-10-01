package app.detour.trip;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
/** A mutable planning identity; purchase snapshots are stored separately. */
public record TripPlan(long id, UUID publicId, String name, LocalDate startDate, LocalDate endDate,
        int travelerCount, List<Integer> travelerAges, long version, DraftSelections selections) {
    public TripDraft asDraft() { return new TripDraft(id, publicId, version, selections, startDate, endDate); }
    public PlannedItinerary asPlanned() { return new PlannedItinerary(id, publicId, selections, name, startDate, endDate, version); }
}
