package app.detour.trip;

import app.detour.api.ApiException;
import tools.jackson.databind.JsonNode;
import java.math.BigInteger;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class TripService {
    /** Ages are inclusive 0..120; adult readiness belongs to planned promotion, not Draft creation. */
    static final int MAX_TRAVELER_AGE = 120;
    /** $1,000,000.00 is product-safe and far below the persisted BIGINT limit. */
    static final long MAX_BUDGET_CENTS = 100_000_000L;
    private static final LocalDate FIRST_SUPPORTED_DATE = LocalDate.of(2027, 3, 1);
    private static final LocalDate LAST_SUPPORTED_DATE = LocalDate.of(2027, 3, 31);
    private final TripRepository trips;

    TripService(TripRepository trips) {
        this.trips = trips;
    }

    @Transactional
    public TripResponse create(long ownerUserId, TripRequests.Create request) {
        if (request == null) throw validation("request", "A request body is required.");
        String destinationKey = required(request.destinationKey(), "destinationKey");
        Destination destination = trips.findSupportedDestination(destinationKey)
                .orElseThrow(() -> validation("destinationKey", "Choose a supported destination."));
        LocalDate startDate = required(request.startDate(), "startDate");
        LocalDate endDate = required(request.endDate(), "endDate");
        int travelerCount = required(request.travelerCount(), "travelerCount");
        validateDates(startDate, endDate);
        if (travelerCount < 1 || travelerCount > 8) throw validation("travelerCount", "Traveler count must be between 1 and 8.");
        List<Integer> ages = validateAges(request.travelerAges(), travelerCount);
        Long budgetCents = validateBudget(request.budgetCents());
        UUID tripId = UUID.randomUUID();
        UUID draftId = UUID.randomUUID();
        String label = label(destination.name(), startDate, endDate);
        trips.createAggregate(ownerUserId, tripId, destination, startDate, endDate, travelerCount, ages, budgetCents, label, draftId);
        return response(trips.findByPublicIdAndOwnerUserId(tripId, ownerUserId).orElseThrow());
    }

    public TripResponse detail(long ownerUserId, String tripId) {
        UUID publicId;
        try {
            publicId = UUID.fromString(tripId);
        } catch (IllegalArgumentException exception) {
            throw notFound();
        }
        return trips.findByPublicIdAndOwnerUserId(publicId, ownerUserId).map(this::response).orElseThrow(this::notFound);
    }

    @Transactional
    public TripResponse replaceSharedDetails(long ownerUserId, String tripId, TripRequests.SharedDetailsUpdate request) {
        if (request == null) throw validation("request", "A request body is required.");
        Trip trip = ownedTrip(ownerUserId, tripId);
        String destinationKey = required(request.destinationKey(), "destinationKey");
        Destination destination = trips.findSupportedDestination(destinationKey).orElseThrow(() -> validation("destinationKey", "Choose a supported destination."));
        LocalDate startDate = required(request.startDate(), "startDate");
        LocalDate endDate = required(request.endDate(), "endDate");
        int travelerCount = required(request.travelerCount(), "travelerCount");
        validateDates(startDate, endDate);
        if (travelerCount < 1 || travelerCount > 8) throw validation("travelerCount", "Traveler count must be between 1 and 8.");
        List<Integer> ages = validateAges(request.travelerAges(), travelerCount);
        Long budgetCents = validateBudget(request.budgetCents());
        if (!trips.advanceVersion(trip.id(), ownerUserId, request.expectedVersion())) throw parentConflict(ownerUserId, trip.publicId());
        trips.replaceSharedDetails(trip.id(), destination, startDate, endDate, travelerCount, ages, budgetCents, label(destination.name(), startDate, endDate));
        return response(trips.findByPublicIdAndOwnerUserId(trip.publicId(), ownerUserId).orElseThrow());
    }

    @Transactional
    public TripResponse createDraft(long ownerUserId, String tripId, TripRequests.DraftCreate request) {
        if (request == null) throw validation("request", "A request body is required.");
        Trip trip = ownedTrip(ownerUserId, tripId);
        if (!trips.advanceVersion(trip.id(), ownerUserId, request.expectedVersion())) throw parentConflict(ownerUserId, trip.publicId());
        trips.insertDraft(trip.id(), UUID.randomUUID());
        return response(trips.findByPublicIdAndOwnerUserId(trip.publicId(), ownerUserId).orElseThrow());
    }

    @Transactional
    public TripResponse duplicateDraft(long ownerUserId, String tripId, String draftId, TripRequests.DraftMutation request) {
        if (request == null) throw validation("request", "A request body is required.");
        Trip trip = ownedTrip(ownerUserId, tripId);
        TripDraft draft = ownedDraft(trip, draftId);
        if (!trips.advanceVersionForDraft(trip.id(), ownerUserId, request.expectedVersion(), draft.id(), request.expectedDraftVersion())) {
            throw mutationConflict(ownerUserId, trip.publicId(), draft.publicId(), request.expectedDraftVersion());
        }
        trips.insertDraft(trip.id(), UUID.randomUUID());
        return response(trips.findByPublicIdAndOwnerUserId(trip.publicId(), ownerUserId).orElseThrow());
    }

    @Transactional
    public TripResponse deleteDraft(long ownerUserId, String tripId, String draftId, TripRequests.DraftMutation request) {
        if (request == null) throw validation("request", "A request body is required.");
        Trip trip = ownedTrip(ownerUserId, tripId);
        TripDraft draft = ownedDraft(trip, draftId);
        if (!trips.advanceVersionForDraft(trip.id(), ownerUserId, request.expectedVersion(), draft.id(), request.expectedDraftVersion())) {
            throw mutationConflict(ownerUserId, trip.publicId(), draft.publicId(), request.expectedDraftVersion());
        }
        trips.deleteDraft(trip.id(), draft.id());
        return response(trips.findByPublicIdAndOwnerUserId(trip.publicId(), ownerUserId).orElseThrow());
    }

    private Trip ownedTrip(long ownerUserId, String tripId) {
        try { return trips.findByPublicIdAndOwnerUserId(UUID.fromString(tripId), ownerUserId).orElseThrow(this::notFound); }
        catch (IllegalArgumentException exception) { throw notFound(); }
    }

    private TripDraft ownedDraft(Trip trip, String draftId) {
        try {
            UUID publicId = UUID.fromString(draftId);
            return trip.drafts().stream().filter(draft -> draft.publicId().equals(publicId)).findFirst().orElseThrow(this::notFound);
        } catch (IllegalArgumentException exception) { throw notFound(); }
    }

    private ApiException parentConflict(long ownerUserId, UUID tripId) {
        long current = trips.findByPublicIdAndOwnerUserId(tripId, ownerUserId).orElseThrow(this::notFound).version();
        return new ApiException(409, "VERSION_CONFLICT", "The Trip has changed. Reload before saving.", Map.of("currentVersion", Long.toString(current)));
    }

    private ApiException mutationConflict(long ownerUserId, UUID tripId, UUID draftId, long expectedDraftVersion) {
        Trip current = trips.findByPublicIdAndOwnerUserId(tripId, ownerUserId).orElseThrow(this::notFound);
        TripDraft draft = current.drafts().stream().filter(candidate -> candidate.publicId().equals(draftId))
                .findFirst().orElseThrow(this::notFound);
        if (draft.version() != expectedDraftVersion) throw draftConflict(draft.version());
        throw new ApiException(409, "VERSION_CONFLICT", "The Trip has changed. Reload before saving.",
                Map.of("currentVersion", Long.toString(current.version())));
    }

    private static ApiException draftConflict(long current) {
        return new ApiException(409, "VERSION_CONFLICT", "The Draft has changed. Reload before saving.", Map.of("currentDraftVersion", Long.toString(current)));
    }

    private static List<Integer> validateAges(List<Integer> suppliedAges, int travelerCount) {
        if (suppliedAges == null) return java.util.Collections.nCopies(travelerCount, null);
        if (suppliedAges.size() != travelerCount) throw validation("travelerAges", "Provide exactly one age for each traveler.");
        List<Integer> ages = new ArrayList<>(suppliedAges.size());
        for (Integer age : suppliedAges) {
            if (age == null || age < 0 || age > MAX_TRAVELER_AGE) {
                throw validation("travelerAges", "Traveler ages must be whole numbers between 0 and 120.");
            }
            ages.add(age);
        }
        return List.copyOf(ages);
    }

    private static Long validateBudget(JsonNode budget) {
        if (budget == null || budget.isNull()) return null;
        if (!budget.isIntegralNumber()) throw validation("budgetCents", "Budget must be an integer number of cents.");
        BigInteger value = budget.bigIntegerValue();
        if (value.signum() < 0 || value.compareTo(BigInteger.valueOf(MAX_BUDGET_CENTS)) > 0) {
            throw validation("budgetCents", "Budget must be between 0 and 100000000 cents.");
        }
        return value.longValueExact();
    }

    private static void validateDates(LocalDate startDate, LocalDate endDate) {
        if (startDate.isBefore(FIRST_SUPPORTED_DATE) || endDate.isAfter(LAST_SUPPORTED_DATE)) {
            throw validation("dates", "Dates must fall between March 1 and March 31, 2027.");
        }
        long nights = ChronoUnit.DAYS.between(startDate, endDate);
        if (nights < 1 || nights > 14) throw validation("dates", "Trip length must be between 1 and 14 nights.");
    }

    private static String label(String destinationName, LocalDate startDate, LocalDate endDate) {
        return destinationName + " \u2014 " + startDate.getMonth().getDisplayName(java.time.format.TextStyle.SHORT, java.util.Locale.US)
                + " " + startDate.getDayOfMonth() + "\u2013" + endDate.getDayOfMonth() + ", " + startDate.getYear();
    }

    private TripResponse response(Trip trip) {
        return new TripResponse(trip.publicId(), trip.destination().key(), trip.destination().name(), "PDX", trip.startDate(),
                trip.endDate(), trip.travelerCount(), trip.travelerAges(), trip.budgetCents(), trip.label(), trip.version(),
                trip.drafts().stream().map(draft -> new DraftResponse(draft.publicId(), draft.version())).toList());
    }

    private static <T> T required(T value, String field) {
        if (value == null || value instanceof String text && text.isBlank()) throw validation(field, "This field is required.");
        return value;
    }

    private static ApiException validation(String field, String message) {
        return new ApiException(400, "VALIDATION_FAILED", "One or more fields are invalid.", Map.of(field, message));
    }

    private ApiException notFound() {
        return new ApiException(404, "RESOURCE_NOT_FOUND", "The requested resource was not found.");
    }
}
