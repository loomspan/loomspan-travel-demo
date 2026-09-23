package app.detour.trip;

import java.util.UUID;

public record PlannedItinerary(long id, UUID publicId, DraftSelections selections) implements TripAlternative {
    @Override public String lifecycle() { return "PLANNED"; }
}
