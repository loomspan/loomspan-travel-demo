package app.detour.trip;

import java.util.UUID;

public record DraftResponse(UUID id, long version, DraftSelectionResponse selections) {
    public DraftResponse(UUID id, long version) { this(id, version, null); }
}
