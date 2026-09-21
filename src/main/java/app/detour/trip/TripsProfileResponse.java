package app.detour.trip;

import java.util.List;

public record TripsProfileResponse(List<TripProfileSummary> upcoming, List<TripProfileSummary> past) {
}
