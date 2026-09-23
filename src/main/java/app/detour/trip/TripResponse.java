package app.detour.trip;

import app.detour.booking.BookingResponse;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public record TripResponse(UUID id, String destinationKey, String destinationName, String originAirportCode,
        LocalDate startDate, LocalDate endDate, int travelerCount, List<Integer> travelerAges, Long budgetCents,
        String label, String status, long version, List<DraftResponse> drafts, List<PlannedResponse> planned,
        List<AlternativeResponse> alternatives, RevisionSummaryResponse revisionSummary, ItineraryTallyResponse tally,
        BookingResponse booking) {

    public TripResponse(UUID id, String destinationKey, String destinationName, String originAirportCode,
            LocalDate startDate, LocalDate endDate, int travelerCount, List<Integer> travelerAges, Long budgetCents,
            String label, String status, long version, List<DraftResponse> drafts, List<PlannedResponse> planned,
            List<AlternativeResponse> alternatives, RevisionSummaryResponse revisionSummary, ItineraryTallyResponse tally) {
        this(id, destinationKey, destinationName, originAirportCode, startDate, endDate, travelerCount, travelerAges,
                budgetCents, label, status, version, drafts, planned, alternatives, revisionSummary, tally, null);
    }

    public TripResponse(UUID id, String destinationKey, String destinationName, String originAirportCode,
            LocalDate startDate, LocalDate endDate, int travelerCount, List<Integer> travelerAges, Long budgetCents,
            String label, long version, List<DraftResponse> drafts, List<PlannedResponse> planned,
            List<AlternativeResponse> alternatives, RevisionSummaryResponse revisionSummary, ItineraryTallyResponse tally) {
        this(id, destinationKey, destinationName, originAirportCode, startDate, endDate, travelerCount, travelerAges,
                budgetCents, label, "ACTIVE", version, drafts, planned, alternatives, revisionSummary, tally, null);
    }

    public TripResponse(UUID id, String destinationKey, String destinationName, String originAirportCode,
            LocalDate startDate, LocalDate endDate, int travelerCount, List<Integer> travelerAges, Long budgetCents,
            String label, long version, List<DraftResponse> drafts, List<PlannedResponse> planned,
            List<AlternativeResponse> alternatives, RevisionSummaryResponse revisionSummary) {
        this(id, destinationKey, destinationName, originAirportCode, startDate, endDate, travelerCount, travelerAges,
                budgetCents, label, "ACTIVE", version, drafts, planned, alternatives, revisionSummary, null, null);
    }

    public TripResponse(UUID id, String destinationKey, String destinationName, String originAirportCode,
            LocalDate startDate, LocalDate endDate, int travelerCount, List<Integer> travelerAges, Long budgetCents,
            String label, long version, List<DraftResponse> drafts, List<PlannedResponse> planned,
            List<AlternativeResponse> alternatives) {
        this(id, destinationKey, destinationName, originAirportCode, startDate, endDate, travelerCount, travelerAges,
                budgetCents, label, "ACTIVE", version, drafts, planned, alternatives, null, null, null);
    }
}
