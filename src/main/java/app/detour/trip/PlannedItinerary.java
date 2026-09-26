package app.detour.trip;

import java.util.UUID;
import java.time.LocalDate;

public record PlannedItinerary(long id, UUID publicId, DraftSelections selections,
        String name, LocalDate startDate, LocalDate endDate, long version) implements TripAlternative {
    public PlannedItinerary(long id, UUID publicId, DraftSelections selections) {
        this(id, publicId, selections, null, null, null, 0);
    }
    @Override public String lifecycle() { return "PLANNED"; }
}
