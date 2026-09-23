package app.detour.trip;

import java.util.UUID;

public record TripDraft(long id, UUID publicId, long version, DraftSelections selections) implements TripAlternative {
    @Override public String lifecycle() { return "DRAFT"; }
}
