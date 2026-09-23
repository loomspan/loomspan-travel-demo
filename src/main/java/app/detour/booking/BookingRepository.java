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

    boolean decrementStayInventory(long accommodationUnitId, LocalDate date, int unitsToDecrement);

    long insertRentalOccupancy(long rentalUnitId, OffsetDateTime pickupAt, OffsetDateTime returnAt);

    long insertBooking(BookingRecord booking);

    void copySnapshotsFromPlanned(long bookingId, long plannedItineraryId);

    Optional<BookingRecord> findActiveBookingRecordByTripId(long tripId);

    List<BookingRecord> findBookingRecordsByTripId(long tripId);

    Optional<BookingRecord> findByTripIdAndIdempotencyKey(long tripId, String idempotencyKey);

    DraftSelections loadBookingSelections(long bookingId);

    boolean hasActiveBooking(long tripId);

    int activeBookingCount(long tripId);

    boolean hasBookingHistory(long tripId);
}
