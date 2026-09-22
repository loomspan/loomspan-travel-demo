package app.detour.trip;

import java.util.UUID;

public record DraftResponse(UUID id, long version, DraftSelectionResponse selections, ItineraryTallyResponse tally) {
    public DraftResponse(UUID id, long version) {
        this(id, version, null, null);
    }

    public DraftResponse(UUID id, long version, DraftSelectionResponse selections) {
        this(id, version, selections, null);
    }
}

