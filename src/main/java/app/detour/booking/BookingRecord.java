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
        Long rentalOccupancyId,
        java.time.LocalDate purchasedStartDate, java.time.LocalDate purchasedEndDate, int purchasedTravelerCount, java.util.List<Integer> purchasedTravelerAges, Long purchasedBudgetCents
) {
    public BookingRecord(long id, UUID publicId, long tripId, Long plannedItineraryId, String bookingReference, String status, long grandTotalCents, String idempotencyKey, OffsetDateTime createdAt, OffsetDateTime canceledAt, String airfareReference, String stayReference, String rentalReference, Long rentalOccupancyId) {
        this(id, publicId, tripId, plannedItineraryId, bookingReference, status, grandTotalCents, idempotencyKey, createdAt, canceledAt, airfareReference, stayReference, rentalReference, rentalOccupancyId, null, null, 0, null, null);
    }
}
