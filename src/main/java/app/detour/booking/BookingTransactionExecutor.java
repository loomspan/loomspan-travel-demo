package app.detour.booking;

import app.detour.api.ApiException;
import app.detour.common.ClockConfiguration;
import app.detour.trip.AirfareSelection;
import app.detour.trip.DraftSelectionResponse;
import app.detour.trip.DraftSelections;
import app.detour.trip.ItineraryTallyEngine;
import app.detour.trip.ItineraryTallyResponse;
import app.detour.trip.PlannedItinerary;
import app.detour.trip.RentalSelection;
import app.detour.trip.StayNight;
import app.detour.trip.StaySelection;
import app.detour.trip.Trip;
import app.detour.trip.TripRepository;
import app.detour.trip.TripRequests;
import app.detour.trip.TripResponse;
import app.detour.trip.TripService;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class BookingTransactionExecutor {
    private final BookingRepository bookingRepository;
    private final TripRepository tripRepository;
    private final ItineraryTallyEngine tallyEngine;
    private final Clock clock;
    private final TripService tripService;

    public BookingTransactionExecutor(BookingRepository bookingRepository,
                                    TripRepository tripRepository,
                                    ItineraryTallyEngine tallyEngine,
                                    Clock clock,
                                    TripService tripService) {
        this.bookingRepository = bookingRepository;
        this.tripRepository = tripRepository;
        this.tallyEngine = tallyEngine;
        this.clock = clock;
        this.tripService = tripService;
    }

    @Transactional
    public BookingResponse executeBookingTransaction(long ownerUserId, Trip trip, TripRequests.BookingCreate request) {
        // 1. Expiration check
        Instant departureMidnight = trip.startDate().atStartOfDay(ClockConfiguration.PDX_ZONE).toInstant();
        if (!clock.instant().isBefore(departureMidnight)) {
            throw new ApiException(400, "TRIP_EXPIRED", "Cannot book an expired trip.");
        }

        // 2. Trip canceled check
        if ("CANCELED".equals(trip.status())) {
            throw new ApiException(409, "TRIP_CANCELED", "This trip has been canceled and cannot be booked.");
        }

        // 3. Expected version check
        if (trip.version() != request.expectedVersion()) {
            throw new ApiException(409, "VERSION_CONFLICT", "The trip was modified by another operation. Please refresh and try again.");
        }

        // 4. Active booking check
        if (bookingRepository.hasActiveBooking(trip.id())) {
            throw new ApiException(409, "ALREADY_BOOKED", "This trip already has an active booking.");
        }

        // 4. Resolve Planned Itinerary
        PlannedItinerary planned = trip.planned().stream()
                .filter(p -> p.publicId().equals(request.plannedItineraryId()))
                .findFirst()
                .orElseThrow(this::notFound);

        // 5. Reservable components check
        DraftSelections selections = planned.selections();
        if (selections == null || (selections.airfare() == null && selections.stay() == null && selections.rental() == null)) {
            throw new ApiException(400, "NO_RESERVABLE_COMPONENTS", "The selected itinerary contains no reservable components.");
        }

        // 6. Deterministic locking & availability check
        Map<String, String> conflicts = new LinkedHashMap<>();

        // Lock & check Airfare
        if (selections.airfare() != null) {
            AirfareSelection airfare = selections.airfare();
            List<Long> flightInstanceIds = List.of(airfare.outboundFlightInstanceId(), airfare.returnFlightInstanceId());
            var flightLocks = bookingRepository.lockFlightInstances(flightInstanceIds);
            Map<Long, Integer> seatMap = flightLocks.stream()
                    .collect(Collectors.toMap(BookingRepository.FlightSeatsLock::flightInstanceId, BookingRepository.FlightSeatsLock::availableSeats));
            int outboundSeats = seatMap.getOrDefault(airfare.outboundFlightInstanceId(), 0);
            int returnSeats = seatMap.getOrDefault(airfare.returnFlightInstanceId(), 0);
            if (outboundSeats < trip.travelerCount() || returnSeats < trip.travelerCount()) {
                conflicts.put("airfare", "Selected flight does not have enough available seats for party size.");
            }
        }

        // Lock & check Stay
        if (selections.stay() != null) {
            StaySelection stay = selections.stay();
            LocalDate minDate = stay.nights().stream().map(StayNight::date).min(LocalDate::compareTo).orElse(trip.startDate());
            LocalDate maxDate = stay.nights().stream().map(StayNight::date).max(LocalDate::compareTo).map(d -> d.plusDays(1)).orElse(trip.endDate());
            var stayLocks = bookingRepository.lockStayNightlyInventory(stay.accommodationUnitId(), minDate, maxDate);
            Map<LocalDate, Integer> stayMap = stayLocks.stream()
                    .collect(Collectors.toMap(BookingRepository.StayNightlyLock::nightDate, BookingRepository.StayNightlyLock::availableInventory));
            for (StayNight night : stay.nights()) {
                int available = stayMap.getOrDefault(night.date(), 0);
                if (available < stay.unitCount()) {
                    conflicts.put("stay", "Selected accommodation is sold out for one or more requested nights.");
                    break;
                }
            }
        }

        // Lock & check Rental
        if (selections.rental() != null) {
            RentalSelection rental = selections.rental();
            bookingRepository.lockRentalUnit(rental.rentalUnitId());
            if (!bookingRepository.isRentalAvailable(rental.rentalUnitId(), rental.pickupAt(), rental.returnAt())) {
                conflicts.put("rental", "Selected rental vehicle is no longer available for the requested interval.");
            }
        }

        if (!conflicts.isEmpty()) {
            throw new ApiException(409, "INVENTORY_CONFLICT", "One or more selected components are unavailable.", Map.copyOf(conflicts));
        }

        // 7. Apply inventory decrements
        if (selections.airfare() != null) {
            bookingRepository.decrementFlightSeats(selections.airfare().outboundFlightInstanceId(), trip.travelerCount());
            bookingRepository.decrementFlightSeats(selections.airfare().returnFlightInstanceId(), trip.travelerCount());
        }

        if (selections.stay() != null) {
            for (StayNight night : selections.stay().nights()) {
                bookingRepository.decrementStayInventory(selections.stay().accommodationUnitId(), night.date(), selections.stay().unitCount());
            }
        }

        Long rentalOccupancyId = null;
        if (selections.rental() != null) {
            rentalOccupancyId = bookingRepository.insertRentalOccupancy(selections.rental().rentalUnitId(), selections.rental().pickupAt(), selections.rental().returnAt());
        }

        // 8. Generate confirmation references & tally
        String bookingRef = BookingReferenceGenerator.generateBookingReference();
        String airfareRef = selections.airfare() != null ? BookingReferenceGenerator.generateAirfareReference() : null;
        String stayRef = selections.stay() != null ? BookingReferenceGenerator.generateStayReference() : null;
        String rentalRef = selections.rental() != null ? BookingReferenceGenerator.generateRentalReference() : null;

        ItineraryTallyResponse tally = tallyEngine.calculateTally(selections, trip.travelerCount(), trip.budgetCents());
        OffsetDateTime now = OffsetDateTime.ofInstant(clock.instant(), ZoneOffset.UTC);

        BookingRecord record = new BookingRecord(
                0L,
                UUID.randomUUID(),
                trip.id(),
                planned.id(),
                bookingRef,
                "ACTIVE",
                tally.grandTotalCents(),
                request.idempotencyKey(),
                now,
                null,
                airfareRef,
                stayRef,
                rentalRef,
                rentalOccupancyId
        );
        long bookingId = bookingRepository.insertBooking(record);
        bookingRepository.copySnapshotsFromPlanned(bookingId, planned.id());

        // 9. Advance trip version
        boolean advanced = tripRepository.advanceVersion(trip.id(), ownerUserId, request.expectedVersion());
        if (!advanced) {
            throw new ApiException(409, "VERSION_CONFLICT", "The trip was modified by another operation. Please refresh and try again.");
        }

        DraftSelectionResponse selectionResponse = TripService.selectionResponse(selections);
        return new BookingResponse(
                record.publicId(),
                trip.publicId(),
                planned.publicId(),
                bookingRef,
                "ACTIVE",
                tally.grandTotalCents(),
                request.idempotencyKey(),
                now,
                null,
                airfareRef,
                stayRef,
                rentalRef,
                selectionResponse,
                tally
        );
    }

    @Transactional
    public TripResponse executeCancelBookingTransaction(long ownerUserId, Trip trip, UUID bookingPublicId, TripRequests.Cancel request) {
        if (request == null) {
            throw new ApiException(400, "VALIDATION_FAILED", "A request body is required.", Map.of("request", "A request body is required."));
        }

        // 1. Expiration check: booking cannot be canceled if the trip is Expired (isExpired(startDate) in America/Los_Angeles). Reject with HTTP 400.
        Instant departureMidnight = trip.startDate().atStartOfDay(ClockConfiguration.PDX_ZONE).toInstant();
        if (!clock.instant().isBefore(departureMidnight)) {
            throw new ApiException(400, "TRIP_EXPIRED", "Cannot cancel a booking on an expired trip.");
        }

        // 2. Trip canceled check
        if ("CANCELED".equals(trip.status())) {
            throw new ApiException(409, "TRIP_CANCELED", "This trip has been canceled.");
        }

        // 3. Expected version check
        if (trip.version() != request.expectedVersion()) {
            throw new ApiException(409, "VERSION_CONFLICT", "The trip was modified by another operation. Please refresh and try again.");
        }

        // 4. Lock and retrieve the booking row for the trip
        BookingRecord record = bookingRepository.findBookingRecordByTripIdAndPublicIdForUpdate(trip.id(), bookingPublicId)
                .orElseThrow(this::notFound);

        // 5. Verify status = 'ACTIVE'
        if (!"ACTIVE".equals(record.status())) {
            throw new ApiException(409, "BOOKING_NOT_ACTIVE", "Only active bookings can be canceled.");
        }

        // 6. Restore reserved inventory
        restoreInventory(record, trip);

        // 7. Update detour_booking: set status = 'CANCELED' and canceled_at = CURRENT_TIMESTAMP
        OffsetDateTime now = OffsetDateTime.ofInstant(clock.instant(), ZoneOffset.UTC);
        bookingRepository.updateBookingStatus(record.id(), "CANCELED", now);

        // 8. Advance detour_trip.version
        boolean advanced = tripRepository.advanceVersion(trip.id(), ownerUserId, request.expectedVersion());
        if (!advanced) {
            throw new ApiException(409, "VERSION_CONFLICT", "The trip was modified by another operation. Please refresh and try again.");
        }

        // 9. Return updated TripResponse showing trip ACTIVE and booking CANCELED
        Trip updatedTrip = tripRepository.findByPublicIdAndOwnerUserId(trip.publicId(), ownerUserId)
                .orElseThrow(this::notFound);
        return tripService.toResponse(updatedTrip);
    }

    @Transactional
    public TripResponse executeCancelTripTransaction(long ownerUserId, Trip trip, TripRequests.Cancel request) {
        if (request == null) {
            throw new ApiException(400, "VALIDATION_FAILED", "A request body is required.", Map.of("request", "A request body is required."));
        }

        // 1. Expiration check: Allowed only before the trip becomes Expired (!isExpired(startDate)).
        Instant departureMidnight = trip.startDate().atStartOfDay(ClockConfiguration.PDX_ZONE).toInstant();
        if (!clock.instant().isBefore(departureMidnight)) {
            throw new ApiException(400, "TRIP_EXPIRED", "Cannot cancel an expired trip.");
        }

        // 2. Check if trip already canceled
        if ("CANCELED".equals(trip.status())) {
            throw new ApiException(409, "TRIP_ALREADY_CANCELED", "This trip has already been canceled.");
        }

        // 3. Expected version check
        if (trip.version() != request.expectedVersion()) {
            throw new ApiException(409, "VERSION_CONFLICT", "The trip was modified by another operation. Please refresh and try again.");
        }

        // 4. Allowed when the trip has booking history (one or more active or canceled bookings in detour_booking)
        if (!bookingRepository.hasBookingHistory(trip.id())) {
            throw new ApiException(400, "NO_BOOKING_HISTORY", "Trips without booking history cannot be canceled. Use Delete Trip instead.");
        }

        // 5. If the trip has an ACTIVE booking:
        Optional<BookingRecord> activeBooking = bookingRepository.findActiveBookingRecordByTripIdForUpdate(trip.id());
        if (activeBooking.isPresent()) {
            BookingRecord record = activeBooking.get();
            restoreInventory(record, trip);
            OffsetDateTime now = OffsetDateTime.ofInstant(clock.instant(), ZoneOffset.UTC);
            bookingRepository.updateBookingStatus(record.id(), "CANCELED", now);
        }

        // 6. Set detour_trip.status = 'CANCELED' and advance version
        boolean canceled = tripRepository.cancelTrip(trip.id(), ownerUserId, request.expectedVersion());
        if (!canceled) {
            throw new ApiException(409, "VERSION_CONFLICT", "The trip was modified by another operation. Please refresh and try again.");
        }

        // 7. Return updated TripResponse showing trip status CANCELED
        Trip updatedTrip = tripRepository.findByPublicIdAndOwnerUserId(trip.publicId(), ownerUserId)
                .orElseThrow(this::notFound);
        return tripService.toResponse(updatedTrip);
    }

    private void restoreInventory(BookingRecord record, Trip trip) {
        DraftSelections selections = bookingRepository.loadBookingSelections(record.id());
        if (selections == null) {
            return;
        }

        // Flights: increment available_seats = available_seats + traveler_count for outbound and return flight_instance rows
        if (selections.airfare() != null) {
            AirfareSelection airfare = selections.airfare();
            List<Long> flightInstanceIds = List.of(airfare.outboundFlightInstanceId(), airfare.returnFlightInstanceId());
            bookingRepository.lockFlightInstances(flightInstanceIds);
            bookingRepository.incrementFlightSeats(airfare.outboundFlightInstanceId(), trip.travelerCount());
            bookingRepository.incrementFlightSeats(airfare.returnFlightInstanceId(), trip.travelerCount());
        }

        // Stay: increment available_inventory = available_inventory + unit_count for every reserved night in accommodation_nightly_inventory
        if (selections.stay() != null) {
            StaySelection stay = selections.stay();
            LocalDate minDate = stay.nights().stream().map(StayNight::date).min(LocalDate::compareTo).orElse(trip.startDate());
            LocalDate maxDate = stay.nights().stream().map(StayNight::date).max(LocalDate::compareTo).map(d -> d.plusDays(1)).orElse(trip.endDate());
            bookingRepository.lockStayNightlyInventory(stay.accommodationUnitId(), minDate, maxDate);
            for (StayNight night : stay.nights()) {
                bookingRepository.incrementStayInventory(stay.accommodationUnitId(), night.date(), stay.unitCount());
            }
        }

        // Rental car: update the corresponding rental_unit_occupancy row from occupancy_status = 'ACTIVE' to occupancy_status = 'RELEASED'
        if (record.rentalOccupancyId() != null) {
            bookingRepository.releaseRentalOccupancy(record.rentalOccupancyId());
        }
    }

    private ApiException notFound() {
        return new ApiException(404, "RESOURCE_NOT_FOUND", "Resource not found.");
    }
}
