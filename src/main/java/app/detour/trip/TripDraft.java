package app.detour.trip;

import java.util.UUID;
import java.time.LocalDate;

public record TripDraft(long id, UUID publicId, long version, DraftSelections selections,
        LocalDate startDate, LocalDate endDate) implements TripAlternative {
    public TripDraft(long id, UUID publicId, long version, DraftSelections selections) {
        this(id, publicId, version, selections, null, null);
    }
    @Override public String lifecycle() { return "DRAFT"; }
}
