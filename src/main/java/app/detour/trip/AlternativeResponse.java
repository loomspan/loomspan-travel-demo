package app.detour.trip;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

/** Deliberately component-level facts only: no canonical total or availability decision. */
public record AlternativeResponse(UUID id, String lifecycle, Long version, DraftSelectionResponse selections, ItineraryTallyResponse tally) {
    public AlternativeResponse(UUID id, String lifecycle, Long version, DraftSelectionResponse selections) {
        this(id, lifecycle, version, selections, null);
    }
}
record PlannedResponse(UUID id, DraftSelectionResponse selections, ItineraryTallyResponse tally) {
    public PlannedResponse(UUID id, DraftSelectionResponse selections) {
        this(id, selections, null);
    }
}

record RevisionSummaryResponse(List<ComponentRemovalResponse> removals, List<ComponentAdjustmentResponse> adjustments) {
    public RevisionSummaryResponse {
        removals = removals == null ? List.of() : List.copyOf(removals);
        adjustments = adjustments == null ? List.of() : List.copyOf(adjustments);
    }
}

record ComponentRemovalResponse(UUID draftId, String component, String reason) { }

record ComponentAdjustmentResponse(UUID draftId, String component, String changeType,
        Integer previousUnitCount, Integer newUnitCount, Long previousPriceCents, Long newPriceCents,
        String reason) { }
