package app.detour.booking;

import java.time.OffsetDateTime;
import java.util.UUID;

public record BookingRecord(
        long id,
        UUID publicId,
        long tripId,
        Long plannedItineraryId,
        String bookingReference,
        String status,
        long grandTotalCents,
        String idempotencyKey,
        OffsetDateTime createdAt,
        OffsetDateTime canceledAt,
        String airfareReference,
        String stayReference,
        String rentalReference,
        Long rentalOccupancyId
) {
}
