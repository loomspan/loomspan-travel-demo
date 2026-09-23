package app.detour.trip;

import java.util.UUID;

/** Immutable alternative source.  Future Booking snapshots can implement the same boundary. */
public sealed interface TripAlternative permits TripDraft, PlannedItinerary {
    long id();
    UUID publicId();
    String lifecycle();
}
