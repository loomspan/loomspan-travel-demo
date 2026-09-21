package app.detour.identity;

import app.detour.trip.TripProfileSummary;
import java.util.List;

public record ProfileResponse(String email, List<TripProfileSummary> upcoming, List<TripProfileSummary> past) {
    public ProfileResponse(String email) {
        this(email, List.of(), List.of());
    }
}
