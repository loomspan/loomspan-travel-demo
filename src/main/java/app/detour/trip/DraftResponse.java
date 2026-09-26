package app.detour.trip;

import java.util.UUID;
import java.time.LocalDate;

public record DraftResponse(UUID id, long version, DraftSelectionResponse selections, ItineraryTallyResponse tally,
        LocalDate startDate, LocalDate endDate) {
    public DraftResponse(UUID id, long version, DraftSelectionResponse selections, ItineraryTallyResponse tally) {
        this(id, version, selections, tally, null, null);
    }
    public DraftResponse(UUID id, long version) {
        this(id, version, null, null, null, null);
    }

    public DraftResponse(UUID id, long version, DraftSelectionResponse selections) {
        this(id, version, selections, null, null, null);
    }
}

