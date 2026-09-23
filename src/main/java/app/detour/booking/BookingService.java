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
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class BookingService {
    private final BookingRepository bookingRepository;
    private final TripRepository tripRepository;
    private final ItineraryTallyEngine tallyEngine;
    private final Clock clock;
    private final BookingTransactionExecutor transactionExecutor;

    public BookingService(BookingRepository bookingRepository,
                          TripRepository tripRepository,
                          ItineraryTallyEngine tallyEngine,
                          Clock clock,
                          BookingTransactionExecutor transactionExecutor) {
        this.bookingRepository = bookingRepository;
        this.tripRepository = tripRepository;
        this.tallyEngine = tallyEngine;
        this.clock = clock;
        this.transactionExecutor = transactionExecutor;
    }

    public record BookingOutcome(BookingResponse response, boolean created) {}

    public BookingOutcome book(long ownerUserId, String tripPublicId, TripRequests.BookingCreate request) {
        if (request == null) {
            throw new ApiException(400, "VALIDATION_FAILED", "A request body is required.", Map.of("request", "A request body is required."));
        }
        Trip trip = ownedTrip(ownerUserId, tripPublicId);

        // Fast idempotency check before transaction
        var existingBooking = bookingRepository.findByTripIdAndIdempotencyKey(trip.id(), request.idempotencyKey());
        if (existingBooking.isPresent()) {
            return new BookingOutcome(toBookingResponse(existingBooking.get(), trip), false);
        }

        try {
            return new BookingOutcome(transactionExecutor.executeBookingTransaction(ownerUserId, trip, request), true);
        } catch (DuplicateKeyException ex) {
            // Concurrent race recovery
            var concurrentBooking = bookingRepository.findByTripIdAndIdempotencyKey(trip.id(), request.idempotencyKey());
            if (concurrentBooking.isPresent()) {
                return new BookingOutcome(toBookingResponse(concurrentBooking.get(), trip), false);
            }
            if (bookingRepository.hasActiveBooking(trip.id())) {
                throw new ApiException(409, "ALREADY_BOOKED", "This trip already has an active booking.");
            }
            throw ex;
        }
    }

    public BookingResponse getActiveBooking(long ownerUserId, String tripPublicId) {
        Trip trip = ownedTrip(ownerUserId, tripPublicId);
        BookingRecord record = bookingRepository.findActiveBookingRecordByTripId(trip.id())
                .orElseThrow(this::notFound);
        return toBookingResponse(record, trip);
    }

    public List<BookingResponse> getBookingHistory(long ownerUserId, String tripPublicId) {
        Trip trip = ownedTrip(ownerUserId, tripPublicId);
        List<BookingRecord> records = bookingRepository.findBookingRecordsByTripId(trip.id());
        return records.stream().map(r -> toBookingResponse(r, trip)).toList();
    }

    public TripResponse cancelBooking(long ownerUserId, String tripPublicId, String bookingPublicId, TripRequests.Cancel request) {
        Trip trip = ownedTrip(ownerUserId, tripPublicId);
        UUID bookingUuid;
        try {
            bookingUuid = UUID.fromString(bookingPublicId);
        } catch (IllegalArgumentException ex) {
            throw notFound();
        }
        return transactionExecutor.executeCancelBookingTransaction(ownerUserId, trip, bookingUuid, request);
    }

    public TripResponse cancelTrip(long ownerUserId, String tripPublicId, TripRequests.Cancel request) {
        Trip trip = ownedTrip(ownerUserId, tripPublicId);
        return transactionExecutor.executeCancelTripTransaction(ownerUserId, trip, request);
    }

    private BookingResponse toBookingResponse(BookingRecord record, Trip trip) {
        DraftSelections selections = bookingRepository.loadBookingSelections(record.id());
        ItineraryTallyResponse tally = tallyEngine.calculateTally(selections, trip.travelerCount(), trip.budgetCents());
        DraftSelectionResponse selectionResponse = TripService.selectionResponse(selections);
        UUID plannedPublicId = record.plannedItineraryId() != null
                ? trip.planned().stream()
                        .filter(p -> p.id() == record.plannedItineraryId().longValue())
                        .map(PlannedItinerary::publicId)
                        .findFirst()
                        .orElse(null)
                : null;

        return new BookingResponse(
                record.publicId(),
                trip.publicId(),
                plannedPublicId,
                record.bookingReference(),
                record.status(),
                record.grandTotalCents(),
                record.idempotencyKey(),
                record.createdAt(),
                record.canceledAt(),
                record.airfareReference(),
                record.stayReference(),
                record.rentalReference(),
                selectionResponse,
                tally
        );
    }

    private Trip ownedTrip(long ownerUserId, String tripId) {
        try {
            return tripRepository.findByPublicIdAndOwnerUserId(UUID.fromString(tripId), ownerUserId)
                    .orElseThrow(this::notFound);
        } catch (IllegalArgumentException ex) {
            throw notFound();
        }
    }

    private ApiException notFound() {
        return new ApiException(404, "RESOURCE_NOT_FOUND", "The requested resource was not found.");
    }
}
