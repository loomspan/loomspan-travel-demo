package app.detour.booking;

import app.detour.trip.DraftSelections;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public interface BookingRepository {
    record FlightSeatsLock(long flightInstanceId, int availableSeats) { }
    record StayNightlyLock(LocalDate nightDate, int availableInventory) { }

    List<FlightSeatsLock> lockFlightInstances(List<Long> flightInstanceIds);

    List<StayNightlyLock> lockStayNightlyInventory(long accommodationUnitId, LocalDate startDate, LocalDate endDate);

    void lockRentalUnit(long rentalUnitId);

    boolean isRentalAvailable(long rentalUnitId, OffsetDateTime pickupAt, OffsetDateTime returnAt);

    boolean decrementFlightSeats(long flightInstanceId, int seatsToDecrement);

    boolean incrementFlightSeats(long flightInstanceId, int seatsToIncrement);

    boolean decrementStayInventory(long accommodationUnitId, LocalDate date, int unitsToDecrement);

    boolean incrementStayInventory(long accommodationUnitId, LocalDate date, int unitsToIncrement);

    long insertRentalOccupancy(long rentalUnitId, OffsetDateTime pickupAt, OffsetDateTime returnAt);

    void releaseRentalOccupancy(long rentalOccupancyId);

    long insertBooking(BookingRecord booking);

    void updateBookingStatus(long bookingId, String status, OffsetDateTime canceledAt);

    void copySnapshotsFromPlanned(long bookingId, long plannedItineraryId);

    Optional<BookingRecord> findActiveBookingRecordByTripId(long tripId);

    Optional<BookingRecord> findActiveBookingRecordByTripIdForUpdate(long tripId);

    Optional<BookingRecord> findBookingRecordByTripIdAndPublicIdForUpdate(long tripId, java.util.UUID bookingPublicId);

    Optional<BookingRecord> findPrimaryBookingRecordByTripId(long tripId);

    List<BookingRecord> findBookingRecordsByTripId(long tripId);

    Optional<BookingRecord> findByTripIdAndIdempotencyKey(long tripId, String idempotencyKey);

    DraftSelections loadBookingSelections(long bookingId);

    boolean hasActiveBooking(long tripId);

    int activeBookingCount(long tripId);

    boolean hasBookingHistory(long tripId);

    boolean isPlannedItineraryActivelyBooked(long plannedItineraryId);
}
