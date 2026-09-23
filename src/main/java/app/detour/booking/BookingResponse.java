package app.detour.booking;

import app.detour.trip.DraftSelectionResponse;
import app.detour.trip.ItineraryTallyResponse;
import java.time.OffsetDateTime;
import java.util.UUID;

public record BookingResponse(
        UUID id,
        UUID tripId,
        UUID plannedItineraryId,
        String bookingReference,
        String status,
        long grandTotalCents,
        String idempotencyKey,
        OffsetDateTime bookedAt,
        OffsetDateTime canceledAt,
        String airfareReference,
        String stayReference,
        String rentalReference,
        DraftSelectionResponse selections,
        ItineraryTallyResponse tally
) {
}
