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

        boolean destinationChanged = !trip.destination().key().equals(destinationKey);
        boolean datesChanged = !trip.startDate().equals(startDate) || !trip.endDate().equals(endDate);
        boolean travelersChanged = trip.travelerCount() != travelerCount || !java.util.Objects.equals(trip.travelerAges(), ages);

        if (!trip.planned().isEmpty()) {
            if (destinationChanged || datesChanged || travelersChanged) {
                throw new ApiException(409, "IMMUTABLE_TRIP", "Trips with Planned alternatives cannot change destination, dates, or travelers in place. Create a revised trip instead.");
            }
            if (!trips.advanceVersion(trip.id(), ownerUserId, request.expectedVersion())) throw parentConflict(ownerUserId, trip.publicId());
            trips.replaceSharedDetails(trip.id(), destination, startDate, endDate, travelerCount, ages, budgetCents, trip.label());
            return response(trips.findByPublicIdAndOwnerUserId(trip.publicId(), ownerUserId).orElseThrow());
        }

        if (!trips.advanceVersion(trip.id(), ownerUserId, request.expectedVersion())) throw parentConflict(ownerUserId, trip.publicId());
        trips.replaceSharedDetails(trip.id(), destination, startDate, endDate, travelerCount, ages, budgetCents, label(destination.name(), startDate, endDate));

        List<ComponentRemovalResponse> removals = new ArrayList<>();
        List<ComponentAdjustmentResponse> adjustments = new ArrayList<>();

        for (TripDraft draft : trip.drafts()) {
            DraftSelections selections = draft.selections();
            if (selections != null) {
                if (selections.airfare() != null) {
                    var result = trips.revalidateAirfare(destination.id(), startDate, endDate, travelerCount, trip.travelerCount(), selections.airfare(), draft.publicId());
                    if (!result.valid()) {
                        trips.deleteDraftAirfareSelection(draft.id());
                        if (result.removal() != null) removals.add(result.removal());
                    } else if (result.adjustment() != null) {
                        adjustments.add(result.adjustment());
                    }
                }
                if (selections.stay() != null) {
                    var result = trips.revalidateStay(destination.id(), startDate, endDate, trip.startDate(), trip.endDate(), travelerCount, trip.travelerCount(), selections.stay(), draft.publicId());
                    if (!result.valid()) {
                        trips.deleteDraftStaySelection(draft.id());
                        if (result.removal() != null) removals.add(result.removal());
                    } else {
                        if (result.newUnitCount() != selections.stay().unitCount()) {
                            trips.updateDraftStayUnitCount(draft.id(), result.newUnitCount());
                        }
                        if (result.adjustment() != null) {
                            adjustments.add(result.adjustment());
                        }
                    }
                }
                if (selections.rental() != null) {
                    var result = trips.revalidateRental(destination.id(), startDate, endDate, ages, selections.rental(), draft.publicId());
                    if (!result.valid()) {
                        trips.deleteDraftRentalSelection(draft.id());
                        if (result.removal() != null) removals.add(result.removal());
                    } else if (result.adjustment() != null) {
                        adjustments.add(result.adjustment());
                    }
                }
            }
        }

        RevisionSummaryResponse summary = new RevisionSummaryResponse(removals, adjustments);
        return response(trips.findByPublicIdAndOwnerUserId(trip.publicId(), ownerUserId).orElseThrow(), summary);
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
        trips.insertDraftCopy(trip.id(), UUID.randomUUID(), draft.selections());
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

    @Transactional
    public TripResponse promoteDraft(long ownerUserId, String tripId, String draftId, TripRequests.Promotion request) {
        if (request == null) throw validation("request", "A request body is required.");
        Trip trip = ownedTrip(ownerUserId, tripId);
        TripDraft draft = ownedDraft(trip, draftId);
        DraftSelections resolved = trips.resolveSelectionsForPromotion(trip, draft);
        Map<String, String> issues = readinessIssues(trip, resolved);
        if (!issues.isEmpty()) throw new ApiException(400, "PLANNING_NOT_READY", "The Draft is not ready to be planned.", issues);
        if (!trips.advanceVersionForDraft(trip.id(), ownerUserId, request.expectedVersion(), draft.id(), request.expectedDraftVersion())) {
            throw mutationConflict(ownerUserId, trip.publicId(), draft.publicId(), request.expectedDraftVersion());
        }
        trips.insertPlanned(trip.id(), UUID.randomUUID(), resolved);
        return response(trips.findByPublicIdAndOwnerUserId(trip.publicId(), ownerUserId).orElseThrow());
    }

    @Transactional
    public TripResponse duplicateAlternative(long ownerUserId, String tripId, String alternativeId, TripRequests.AlternativeDuplicate request) {
        if (request == null) throw validation("request", "A request body is required.");
        Trip trip = ownedTrip(ownerUserId, tripId);
        TripAlternative source = ownedAlternative(trip, alternativeId);
        if (source instanceof TripDraft draft) {
            if (request.expectedDraftVersion() == null) throw validation("expectedDraftVersion", "This field is required for a Draft source.");
            if (!trips.advanceVersionForDraft(trip.id(), ownerUserId, request.expectedVersion(), draft.id(), request.expectedDraftVersion())) {
                throw mutationConflict(ownerUserId, trip.publicId(), draft.publicId(), request.expectedDraftVersion());
            }
            trips.insertDraftCopy(trip.id(), UUID.randomUUID(), draft.selections());
        } else {
            if (request.expectedDraftVersion() != null) throw validation("expectedDraftVersion", "Planned alternatives do not have a Draft version.");
            if (!trips.advanceVersion(trip.id(), ownerUserId, request.expectedVersion())) throw parentConflict(ownerUserId, trip.publicId());
            trips.insertDraftCopy(trip.id(), UUID.randomUUID(), ((PlannedItinerary) source).selections());
        }
        return response(trips.findByPublicIdAndOwnerUserId(trip.publicId(), ownerUserId).orElseThrow());
    }

    @Transactional
    public TripResponse deleteAlternative(long ownerUserId, String tripId, String alternativeId, TripRequests.AlternativeDelete request) {
        if (request == null) throw validation("request", "A request body is required.");
        Trip trip = ownedTrip(ownerUserId, tripId);
        TripAlternative source = ownedAlternative(trip, alternativeId);
        if (source instanceof TripDraft draft) {
            if (request.expectedDraftVersion() == null) throw validation("expectedDraftVersion", "This field is required for a Draft source.");
            if (!trips.advanceVersionForDraft(trip.id(), ownerUserId, request.expectedVersion(), draft.id(), request.expectedDraftVersion())) throw mutationConflict(ownerUserId, trip.publicId(), draft.publicId(), request.expectedDraftVersion());
            trips.deleteDraft(trip.id(), draft.id());
        } else {
            if (!Boolean.TRUE.equals(request.confirmed())) throw validation("confirmed", "Set confirmed to true before deleting a Planned alternative.");
            if (request.expectedDraftVersion() != null) throw validation("expectedDraftVersion", "Planned alternatives do not have a Draft version.");
            if (!trips.advanceVersion(trip.id(), ownerUserId, request.expectedVersion())) throw parentConflict(ownerUserId, trip.publicId());
            trips.deletePlanned(trip.id(), source.id());
        }
        return response(trips.findByPublicIdAndOwnerUserId(trip.publicId(), ownerUserId).orElseThrow());
    }

    @Transactional
    public TripResponse duplicateTrip(long ownerUserId, String tripId, TripRequests.TripRevision request) {
        if (request == null) throw validation("request", "A request body is required.");
        Trip sourceTrip = ownedTrip(ownerUserId, tripId);
        if (sourceTrip.version() != request.expectedVersion()) {
            throw parentConflict(ownerUserId, sourceTrip.publicId());
        }

        String destinationKey = required(request.destinationKey(), "destinationKey");
        Destination destination = trips.findSupportedDestination(destinationKey)
                .orElseThrow(() -> validation("destinationKey", "Choose a supported destination."));
        LocalDate startDate = required(request.startDate(), "startDate");
        LocalDate endDate = required(request.endDate(), "endDate");
        validateDates(startDate, endDate);
        int travelerCount = required(request.travelerCount(), "travelerCount");
        if (travelerCount < 1 || travelerCount > 8) throw validation("travelerCount", "Traveler count must be between 1 and 8.");
        List<Integer> ages = validateAges(request.travelerAges(), travelerCount);
        Long budgetCents = validateBudget(request.budgetCents());

        List<UUID> sourceIds = request.sourcePlannedItineraryIds();
        List<PlannedItinerary> selectedPlanned = new ArrayList<>();
        for (UUID sourceId : sourceIds) {
            PlannedItinerary planned = sourceTrip.planned().stream()
                    .filter(candidate -> candidate.publicId().equals(sourceId))
                    .findFirst()
                    .orElseThrow(this::notFound);
            selectedPlanned.add(planned);
        }

        List<ComponentRemovalResponse> removals = new ArrayList<>();
        List<ComponentAdjustmentResponse> adjustments = new ArrayList<>();
        List<TripRepository.DraftCreationSpec> draftsToCreate = new ArrayList<>();

        for (PlannedItinerary plannedSource : selectedPlanned) {
            UUID newDraftPublicId = UUID.randomUUID();
            DraftSelections sourceSelections = plannedSource.selections();
            AirfareSelection retainedAirfare = null;
            StaySelection retainedStay = null;
            RentalSelection retainedRental = null;

            if (sourceSelections != null) {
                if (sourceSelections.airfare() != null) {
                    var result = trips.revalidateAirfare(destination.id(), startDate, endDate, travelerCount,
                            sourceTrip.travelerCount(), sourceSelections.airfare(), newDraftPublicId);
                    if (result.valid()) {
                        retainedAirfare = new AirfareSelection(sourceSelections.airfare().outboundFlightInstanceId(),
                                sourceSelections.airfare().returnFlightInstanceId(), null, null, 0, 0, 0, 0, 0, 0);
                        if (result.adjustment() != null) adjustments.add(result.adjustment());
                    } else if (result.removal() != null) {
                        removals.add(result.removal());
                    }
                }
                if (sourceSelections.stay() != null) {
                    var result = trips.revalidateStay(destination.id(), startDate, endDate, sourceTrip.startDate(),
                            sourceTrip.endDate(), travelerCount, sourceTrip.travelerCount(), sourceSelections.stay(), newDraftPublicId);
                    if (result.valid()) {
                        retainedStay = new StaySelection(sourceSelections.stay().accommodationUnitId(), result.newUnitCount(),
                                null, null, List.of());
                        if (result.adjustment() != null) adjustments.add(result.adjustment());
                    } else if (result.removal() != null) {
                        removals.add(result.removal());
                    }
                }
                if (sourceSelections.rental() != null) {
                    var result = trips.revalidateRental(destination.id(), startDate, endDate, ages,
                            sourceSelections.rental(), newDraftPublicId);
                    if (result.valid()) {
                        retainedRental = new RentalSelection(sourceSelections.rental().rentalUnitId(),
                                sourceSelections.rental().pickupAt(), sourceSelections.rental().returnAt(),
                                null, null, null, 0, 0, 0);
                        if (result.adjustment() != null) adjustments.add(result.adjustment());
                    } else if (result.removal() != null) {
                        removals.add(result.removal());
                    }
                }
            }

            draftsToCreate.add(new TripRepository.DraftCreationSpec(newDraftPublicId,
                    new DraftSelections(retainedAirfare, retainedStay, retainedRental)));
        }

        UUID newTripPublicId = UUID.randomUUID();
        String label = label(destination.name(), startDate, endDate);
        trips.createAggregateWithDrafts(ownerUserId, newTripPublicId, destination, startDate, endDate, travelerCount,
                ages, budgetCents, label, draftsToCreate);

        Trip newTrip = trips.findByPublicIdAndOwnerUserId(newTripPublicId, ownerUserId).orElseThrow();
        RevisionSummaryResponse summary = new RevisionSummaryResponse(removals, adjustments);
        return response(newTrip, summary);
    }

    private Trip ownedTrip(long ownerUserId, String tripId) {
        try { return trips.findByPublicIdAndOwnerUserId(UUID.fromString(tripId), ownerUserId).orElseThrow(this::notFound); }
        catch (IllegalArgumentException exception) { throw notFound(); }
    }

    private TripDraft ownedDraft(Trip trip, String draftId) {
        try {
            UUID publicId = UUID.fromString(draftId);
            TripDraft draft = trip.drafts().stream().filter(candidate -> candidate.publicId().equals(publicId)).findFirst().orElse(null);
            if (draft != null) return draft;
            if (trip.planned().stream().anyMatch(candidate -> candidate.publicId().equals(publicId))) {
                throw new ApiException(409, "IMMUTABLE_ALTERNATIVE", "Planned alternatives cannot be changed in place.");
            }
            throw notFound();
        } catch (IllegalArgumentException exception) { throw notFound(); }
    }

    private TripAlternative ownedAlternative(Trip trip, String alternativeId) {
        try {
            UUID publicId = UUID.fromString(alternativeId);
            return java.util.stream.Stream.concat(trip.drafts().stream(), trip.planned().stream())
                    .filter(candidate -> candidate.publicId().equals(publicId)).findFirst().orElseThrow(this::notFound);
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
        return response(trip, null);
    }

    private TripResponse response(Trip trip, RevisionSummaryResponse revisionSummary) {
        List<DraftResponse> drafts = trip.drafts().stream().map(draft -> new DraftResponse(draft.publicId(), draft.version(), selectionResponse(draft.selections()))).toList();
        List<PlannedResponse> planned = trip.planned().stream().map(item -> new PlannedResponse(item.publicId(), selectionResponse(item.selections()))).toList();
        List<AlternativeResponse> alternatives = new ArrayList<>();
        trip.drafts().forEach(draft -> alternatives.add(new AlternativeResponse(draft.publicId(), draft.lifecycle(), draft.version(), selectionResponse(draft.selections()))));
        trip.planned().forEach(item -> alternatives.add(new AlternativeResponse(item.publicId(), item.lifecycle(), null, selectionResponse(item.selections()))));
        return new TripResponse(trip.publicId(), trip.destination().key(), trip.destination().name(), "PDX", trip.startDate(),
                trip.endDate(), trip.travelerCount(), trip.travelerAges(), trip.budgetCents(), trip.label(), trip.version(),
                drafts, planned, List.copyOf(alternatives), revisionSummary);
    }

    private static DraftSelectionResponse selectionResponse(DraftSelections selections) {
        if (selections == null) return null;
        AirfareSelection airfare = selections.airfare(); StaySelection stay = selections.stay(); RentalSelection rental = selections.rental();
        return new DraftSelectionResponse(airfare == null ? null : new AirfareComponentResponse(airfare.outboundFlightInstanceId(), airfare.returnFlightInstanceId(), airfare.outboundDescription(), airfare.returnDescription(), airfare.outboundBaseFareCents(), airfare.outboundTaxCents(), airfare.outboundFeeCents(), airfare.returnBaseFareCents(), airfare.returnTaxCents(), airfare.returnFeeCents()),
                stay == null ? null : new StayComponentResponse(stay.accommodationUnitId(), stay.unitCount(), stay.propertyName(), stay.unitName(), stay.nights().stream().map(n -> new StayNightResponse(n.date(), n.basePriceCents(), n.taxCents(), n.feeCents())).toList()),
                rental == null ? null : new RentalComponentResponse(rental.rentalUnitId(), rental.pickupAt(), rental.returnAt(), rental.locationName(), rental.vehicleClassName(), rental.unitIdentifier(), rental.dailyBasePriceCents(), rental.dailyTaxCents(), rental.dailyFeeCents()));
    }

    private static Map<String, String> readinessIssues(Trip trip, DraftSelections selections) {
        Map<String, String> issues = new java.util.LinkedHashMap<>();
        if (trip.travelerAges() == null) issues.put("travelerAges", "Provide exact ages for every traveler before planning.");
        else if (trip.travelerAges().stream().noneMatch(age -> age >= 18)) issues.put("adult", "At least one traveler must be an adult before planning.");
        if (trip.budgetCents() == null) issues.put("budgetCents", "Provide a budget before planning.");
        if (selections.airfare() == null && selections.stay() == null && selections.rental() == null) issues.put("components", "Select at least one structurally valid reservable component before planning.");
        return issues;
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
