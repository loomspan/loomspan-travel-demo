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
import app.detour.trip.TripService;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
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

    public BookingTransactionExecutor(BookingRepository bookingRepository,
                                    TripRepository tripRepository,
                                    ItineraryTallyEngine tallyEngine,
                                    Clock clock) {
        this.bookingRepository = bookingRepository;
        this.tripRepository = tripRepository;
        this.tallyEngine = tallyEngine;
        this.clock = clock;
    }

    @Transactional
    public BookingResponse executeBookingTransaction(long ownerUserId, Trip trip, TripRequests.BookingCreate request) {
        // 1. Expiration check
        Instant departureMidnight = trip.startDate().atStartOfDay(ClockConfiguration.PDX_ZONE).toInstant();
        if (!clock.instant().isBefore(departureMidnight)) {
            throw new ApiException(400, "TRIP_EXPIRED", "Cannot book an expired trip.");
        }

        // 2. Expected version check
        if (trip.version() != request.expectedVersion()) {
            throw new ApiException(409, "VERSION_CONFLICT", "The trip was modified by another operation. Please refresh and try again.");
        }

        // 3. Active booking check
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

    private ApiException notFound() {
        return new ApiException(404, "RESOURCE_NOT_FOUND", "Resource not found.");
    }
}
