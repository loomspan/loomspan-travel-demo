package demo.wayfarer;

import java.util.List;
import java.util.Map;

public final class Contracts {
    private Contracts() {}
    public record TripRequest(String origin, String destination, String outboundDate, String returnDate,
        int partySize, int rooms, long budgetCents, String hotelReadyBy, String leaveHotelNoEarlierThan,
        String returnToOriginBy, List<String> allowedModes, List<String> priorities) {}
    public record PlanningInput(String assessmentId, TripRequest request) {}
    public record ServiceOption(String id, String mode, String direction, String departs, String arrives, long farePerPersonCents, int seats) {}
    public record Transfer(int minutes, long partyCents) {}
    public record HotelOption(String id, String name, long nightlyRoomCents, int roomCapacity, int roomsPerNight,
        String checkIn, boolean quietRoom, String roomDescription, Map<String, Transfer> transfers) {}
    public record ModeRules(int departureBufferMinutes, int arrivalBufferMinutes, int bostonTransferMinutes, long bostonTransferPartyCents) {}
    public record Snapshot(TripRequest request, List<ServiceOption> services, List<HotelOption> hotels, Map<String, ModeRules> modes) {}
    public record Violation(String code, String message) {}
    public record CheckedTrip(String candidateId, String outboundServiceId, String returnServiceId, String hotelId,
        String hotelName, String roomDescription, boolean quietRoom, String outboundMode, String returnMode,
        long transportCents, long lodgingCents, long transferCents, long totalCents, int totalTransferMinutes,
        String startAt, String outboundDepartsAt, String outboundArrivesAt, String hotelArrivalAt, String hotelReadyAt,
        String leaveHotelAt, String returnDepartsAt, String returnArrivesAt, String returnToOriginAt, List<Violation> violations) {}
    public record Evaluation(List<CheckedTrip> trips, List<Violation> blockers, int consideredCount, boolean complete) {}
    public record SelectionOptions(String recommendedCandidateId, List<String> alternativeCandidateIds) {}
    public record EvaluatedRequest(SelectionOptions selections, TripRequest request, Evaluation evaluation, String recoveryServiceId) {}
    public record SearchResult(List<ServiceOption> outbound, List<ServiceOption> returnOptions, boolean complete) {}
    public record HotelSearch(List<HotelOption> hotels, boolean complete) {}
    public record Choice(String candidateId, String rationale) {}
    public record ModelResult(String assessmentId, String status, Choice recommended, Choice alternative, String explanation) {}
    public record Proposal(String status, CheckedTrip recommended, CheckedTrip alternative, String recommendedReason,
        String alternativeReason, String explanation, List<Violation> blockers, int consideredCount, int feasibleCount) {}
    public record EventSummary(String timestamp, String type, String frameId, String route) {}
    public record AssessmentView(String id, String tripId, int revision, String status, Proposal result, String error,
        String sessionId, List<EventSummary> events, String createdAt, String baseBookingId, int catalogVersion, String recoveryServiceId, List<RecoverySuggestion> recoverySuggestions) {}
    public record BookingView(String id, String tripId, String assessmentId, String candidateId, CheckedTrip quote, String createdAt) {}
    public record BookingChange(BookingView before, BookingView after) {}
    public record Cancellation(String serviceId, String createdAt) {}
    public record RecoverySuggestion(TripRequest request, List<String> changes) {}
    public record CancellationCommand(String bookingId, String serviceId) {}
    public record TripView(String id, int revision, TripRequest request, List<AssessmentView> assessments, BookingView booking, String createdAt, List<BookingChange> changes, Cancellation disruption, int catalogVersion) {}
    public record TripSummary(String id, int revision, boolean booked, String createdAt, boolean needsAttention) {}
    public record BookingCommand(String assessmentId, String candidateId, String idempotencyKey) {}
    public record RevisionCommand(int revision, TripRequest request) {}
    public record AssessmentCommand(int revision) {}
}
