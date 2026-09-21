package app.detour.trip;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public record TripResponse(UUID id, String destinationKey, String destinationName, String originAirportCode,
        LocalDate startDate, LocalDate endDate, int travelerCount, List<Integer> travelerAges, Long budgetCents,
        String label, long version, List<DraftResponse> drafts, List<PlannedResponse> planned,
        List<AlternativeResponse> alternatives, RevisionSummaryResponse revisionSummary) {

    public TripResponse(UUID id, String destinationKey, String destinationName, String originAirportCode,
            LocalDate startDate, LocalDate endDate, int travelerCount, List<Integer> travelerAges, Long budgetCents,
            String label, long version, List<DraftResponse> drafts, List<PlannedResponse> planned,
            List<AlternativeResponse> alternatives) {
        this(id, destinationKey, destinationName, originAirportCode, startDate, endDate, travelerCount, travelerAges,
                budgetCents, label, version, drafts, planned, alternatives, null);
    }
}
