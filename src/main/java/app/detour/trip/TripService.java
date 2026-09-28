package app.detour.trip;

import app.detour.airfare.AirfareSearchRepository;
import app.detour.airfare.AirfareSearchResponses.AirfareSearchResponse;
import app.detour.airfare.AirfareSearchService;
import app.detour.airfare.AirfareSort;
import app.detour.api.ApiException;
import app.detour.booking.BookingRecord;
import app.detour.booking.BookingRepository;
import app.detour.booking.BookingResponse;
import app.detour.common.ClockConfiguration;
import app.detour.rental.RentalSearchRepository;
import app.detour.rental.RentalSearchResponses.RentalSearchResponse;
import app.detour.rental.RentalSearchService;
import app.detour.rental.RentalSort;
import app.detour.stay.AccommodationType;
import app.detour.stay.StaySearchRepository;
import app.detour.stay.StaySearchResponses.StaySearchResponse;
import app.detour.stay.StaySearchService;
import app.detour.stay.StaySort;
import tools.jackson.databind.JsonNode;
import java.math.BigInteger;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.format.DateTimeParseException;
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
    private final AirfareSearchService airfareSearchService;
    private final AirfareSearchRepository airfareSearchRepository;
    private final StaySearchService staySearchService;
    private final StaySearchRepository staySearchRepository;
    private final RentalSearchService rentalSearchService;
    private final RentalSearchRepository rentalSearchRepository;
    private final ItineraryTallyEngine tallyEngine;
    private final Clock clock;
    private final BookingRepository bookingRepository;

    TripService(
            TripRepository trips,
            AirfareSearchService airfareSearchService,
            AirfareSearchRepository airfareSearchRepository,
            StaySearchService staySearchService,
            StaySearchRepository staySearchRepository,
            RentalSearchService rentalSearchService,
            RentalSearchRepository rentalSearchRepository,
            ItineraryTallyEngine tallyEngine,
            Clock clock,
            BookingRepository bookingRepository) {
        this.trips = trips;
        this.airfareSearchService = airfareSearchService;
        this.airfareSearchRepository = airfareSearchRepository;
        this.staySearchService = staySearchService;
        this.staySearchRepository = staySearchRepository;
        this.rentalSearchService = rentalSearchService;
        this.rentalSearchRepository = rentalSearchRepository;
        this.tallyEngine = tallyEngine;
        this.clock = clock;
        this.bookingRepository = bookingRepository;
    }

    @Transactional
    public TripResponse create(long ownerUserId, TripRequests.Create request) {
        if (request == null) throw validation("request", "A request body is required.");
        String name = validateName(request.name());
        String destinationKey = required(request.destinationKey(), "destinationKey");
        Destination destination = trips.findSupportedDestination(destinationKey)
                .orElseThrow(() -> validation("destinationKey", "Choose a supported destination."));
        LocalDate startDate = required(request.startDate(), "startDate");
        LocalDate endDate = required(request.endDate(), "endDate");
        int travelerCount = required(request.travelerCount(), "travelerCount");
        validateDates(startDate, endDate);
        if (travelerCount < 1 || travelerCount > 8) throw validation("travelerCount", "Traveler count must be between 1 and 8.");
        List<Integer> ages = validateAges(request.travelerAges(), travelerCount);
        if (ages.stream().anyMatch(java.util.Objects::isNull)) throw validation("travelerAges", "Provide exactly one age for each traveler.");
        Long budgetCents = validateBudget(request.budgetCents());
        UUID tripId = UUID.randomUUID();
        UUID draftId = UUID.randomUUID();
        trips.createAggregate(ownerUserId, tripId, destination, startDate, endDate, travelerCount, ages, budgetCents, name, draftId);
        return response(trips.findByPublicIdAndOwnerUserId(tripId, ownerUserId).orElseThrow());
    }

    @Transactional
    public TripResponse rename(long ownerUserId, String tripId, TripRequests.Rename request) {
        Trip trip = ownedTrip(ownerUserId, tripId);
        requireActiveTrip(trip);
        String name = validateName(request.name());
        if (!trips.advanceVersion(trip.id(), ownerUserId, request.expectedVersion())) throw parentConflict(ownerUserId, trip.publicId());
        trips.rename(trip.id(), name);
        return response(trips.findByPublicIdAndOwnerUserId(trip.publicId(), ownerUserId).orElseThrow());
    }

    @Transactional
    public TripResponse changeWorkingDates(long ownerUserId, String tripId, TripRequests.WorkingDates request) {
        Trip trip = ownedTrip(ownerUserId, tripId);
        requireActiveTrip(trip);
        TripDraft draft = trip.drafts().get(0);
        LocalDate startDate = required(request.startDate(), "startDate");
        LocalDate endDate = required(request.endDate(), "endDate");
        validateDates(startDate, endDate);
        if (trip.version() != request.expectedVersion() || draft.version() != request.expectedDraftVersion()) {
            throw mutationConflict(ownerUserId, trip.publicId(), draft.publicId(), request.expectedDraftVersion());
        }
        if (draft.startDate().equals(startDate) && draft.endDate().equals(endDate)) return response(trip);
        if (!trips.advanceVersionForDraft(trip.id(), ownerUserId, request.expectedVersion(), draft.id(), request.expectedDraftVersion())) {
            throw mutationConflict(ownerUserId, trip.publicId(), draft.publicId(), request.expectedDraftVersion());
        }
        List<ComponentRemovalResponse> removals = new ArrayList<>();
        List<ComponentAdjustmentResponse> adjustments = new ArrayList<>();
        DraftSelections selections = draft.selections();
        if (selections.airfare() != null) {
            var result = trips.revalidateAirfare(trip.destination().id(), startDate, endDate,
                    trip.travelerCount(), trip.travelerCount(), selections.airfare(), draft.publicId());
            if (!result.valid()) { trips.deleteDraftAirfareSelection(draft.id()); removals.add(result.removal()); }
            else if (result.adjustment() != null) adjustments.add(result.adjustment());
        }
        if (selections.stay() != null) {
            var result = trips.revalidateStay(trip.destination().id(), startDate, endDate, draft.startDate(), draft.endDate(),
                    trip.travelerCount(), trip.travelerCount(), selections.stay(), draft.publicId());
            if (!result.valid()) { trips.deleteDraftStaySelection(draft.id()); removals.add(result.removal()); }
            else {
                if (result.newUnitCount() != selections.stay().unitCount()) trips.updateDraftStayUnitCount(draft.id(), result.newUnitCount());
                if (result.adjustment() != null) adjustments.add(result.adjustment());
            }
        }
        if (selections.rental() != null) {
            var result = trips.revalidateRental(trip.destination().id(), startDate, endDate,
                    trip.travelerAges(), selections.rental(), draft.publicId());
            if (!result.valid()) { trips.deleteDraftRentalSelection(draft.id()); removals.add(result.removal()); }
            else if (result.adjustment() != null) adjustments.add(result.adjustment());
        }
        trips.updateWorkingDates(trip.id(), draft.id(), startDate, endDate);
        return response(trips.findByPublicIdAndOwnerUserId(trip.publicId(), ownerUserId).orElseThrow(),
                new RevisionSummaryResponse(removals, adjustments));
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

    public boolean isPast(LocalDate endDate) {
        LocalDate today = LocalDate.ofInstant(clock.instant(), ClockConfiguration.PDX_ZONE);
        return today.isAfter(endDate);
    }

    public boolean isExpired(LocalDate startDate) {
        Instant departureMidnight = startDate.atStartOfDay(ClockConfiguration.PDX_ZONE).toInstant();
        return !clock.instant().isBefore(departureMidnight);
    }

    private void requireActiveTrip(Trip trip) {
        if ("CANCELED".equals(trip.status())) {
            throw new ApiException(409, "TRIP_CANCELED", "This trip has been canceled and cannot be modified.");
        }
    }

    public TripsProfileResponse tripsProfile(long ownerUserId) {
        List<Trip> allTrips = trips.findAllByOwnerUserId(ownerUserId);
        List<TripProfileSummary> upcoming = new ArrayList<>();
        List<TripProfileSummary> past = new ArrayList<>();

        for (Trip trip : allTrips) {
            boolean pastTrip = isPast(trip.drafts().get(0).endDate())
                    && trip.planned().stream().allMatch(option -> isPast(option.endDate()));
            String temporalStatus = pastTrip ? "PAST" : "UPCOMING";

            boolean expired = isExpired(trip.drafts().get(0).startDate());
            int draftCount = trip.drafts().size();
            int plannedCount = trip.planned().size();
            int expiredCount = expired ? draftCount : 0;

            List<AlternativeProfileSummary> alternatives = new ArrayList<>();
            for (TripDraft draft : trip.drafts()) {
                alternatives.add(new AlternativeProfileSummary(
                        draft.publicId(),
                        draft.lifecycle(),
                        draft.version(),
                        expired ? "EXPIRED" : "DRAFT",
                        expired, "Working plan", draft.startDate(), draft.endDate()));
            }
            for (PlannedItinerary planned : trip.planned()) {
                boolean optionExpired = isExpired(planned.startDate());
                if (optionExpired) expiredCount++;
                alternatives.add(new AlternativeProfileSummary(
                        planned.publicId(),
                        planned.lifecycle(),
                        planned.version(),
                        optionExpired ? "EXPIRED" : "PLANNED",
                        optionExpired, planned.name(), planned.startDate(), planned.endDate(),
                        bookingRepository.isPlannedItineraryBooked(planned.id())));
            }

            TripProfileSummary summary = new TripProfileSummary(
                    trip.publicId(),
                    trip.destination().key(),
                    trip.destination().name(),
                    trip.startDate(),
                    trip.endDate(),
                    trip.label(),
                    trip.version(),
                    trip.status(),
                    temporalStatus,
                    draftCount,
                    plannedCount,
                    expiredCount,
                    trips.activeBookingCount(trip.id()),
                    trips.hasBookingHistory(trip.id()),
                    trips.findPrimaryBookingReference(trip.id()).orElse(null),
                    List.copyOf(alternatives));

            if (pastTrip) {
                past.add(summary);
            } else {
                upcoming.add(summary);
            }
        }

        java.util.Collections.reverse(past);
        return new TripsProfileResponse(List.copyOf(upcoming), List.copyOf(past));
    }

    @Transactional
    public TripResponse replaceSharedDetails(long ownerUserId, String tripId, TripRequests.SharedDetailsUpdate request) {
        if (request == null) throw validation("request", "A request body is required.");
        Trip trip = ownedTrip(ownerUserId, tripId);
        requireActiveTrip(trip);
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
        if (!trip.planned().isEmpty() && (destinationChanged || travelersChanged)) {
            throw new ApiException(409, "IMMUTABLE_TRIP_PARTY", "Destination and travelers are shared by Saved options. Create a revised Trip to change them.");
        }
        TripDraft working = trip.drafts().get(0);
        long expectedDraftVersion = request.expectedDraftVersion() == null ? working.version() : request.expectedDraftVersion();
        if (trip.version() != request.expectedVersion() || working.version() != expectedDraftVersion) {
            throw mutationConflict(ownerUserId, trip.publicId(), working.publicId(), expectedDraftVersion);
        }
        if (!destinationChanged && !datesChanged && !travelersChanged && java.util.Objects.equals(trip.budgetCents(), budgetCents)) {
            return response(trip);
        }
        if (!trips.advanceVersionForDraft(trip.id(), ownerUserId, request.expectedVersion(), working.id(), expectedDraftVersion)) {
            throw mutationConflict(ownerUserId, trip.publicId(), working.publicId(), expectedDraftVersion);
        }
        trips.replaceSharedDetails(trip.id(), destination, startDate, endDate, travelerCount, ages, budgetCents, trip.label());

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
        Trip trip = ownedTrip(ownerUserId, tripId);
        requireActiveTrip(trip);
        if (trip.version() != request.expectedVersion()) throw parentConflict(ownerUserId, trip.publicId());
        throw new ApiException(409, "WORKING_PLAN_EXISTS", "Each Trip has one Working plan.");
    }

    @Transactional
    public TripResponse duplicateDraft(long ownerUserId, String tripId, String draftId, TripRequests.DraftMutation request) {
        Trip trip = ownedTrip(ownerUserId, tripId);
        requireActiveTrip(trip);
        TripDraft draft = ownedDraft(trip, draftId);
        if (trip.version() != request.expectedVersion() || draft.version() != request.expectedDraftVersion()) {
            throw mutationConflict(ownerUserId, trip.publicId(), draft.publicId(), request.expectedDraftVersion());
        }
        throw new ApiException(409, "WORKING_PLAN_EXISTS", "Each Trip has one Working plan.");
    }

    @Transactional
    public TripResponse deleteDraft(long ownerUserId, String tripId, String draftId, TripRequests.DraftMutation request) {
        Trip trip = ownedTrip(ownerUserId, tripId);
        requireActiveTrip(trip);
        TripDraft draft = ownedDraft(trip, draftId);
        if (trip.version() != request.expectedVersion() || draft.version() != request.expectedDraftVersion()) {
            throw mutationConflict(ownerUserId, trip.publicId(), draft.publicId(), request.expectedDraftVersion());
        }
        throw new ApiException(409, "WORKING_PLAN_REQUIRED", "The Working plan cannot be deleted.");
    }

    @Transactional
    public TripResponse promoteDraft(long ownerUserId, String tripId, String draftId, TripRequests.Promotion request) {
        Trip trip = ownedTrip(ownerUserId, tripId);
        requireActiveTrip(trip);
        ownedDraft(trip, draftId);
        throw new ApiException(409, "USE_NAMED_OPTION", "Save a named option from the Working plan instead.");
    }

    @Transactional
    public TripResponse saveOption(long ownerUserId, String tripId, TripRequests.OptionSave request) {
        if (request == null) throw validation("request", "A request body is required.");
        Trip trip = ownedTrip(ownerUserId, tripId);
        requireActiveTrip(trip);
        TripDraft working = trip.drafts().get(0);
        String name = validateName(request.name());
        DraftSelections snapshot = validOptionSnapshot(trip, working);
        if (!trips.advanceVersionForDraft(trip.id(), ownerUserId, request.expectedVersion(),
                working.id(), request.expectedDraftVersion())) {
            throw mutationConflict(ownerUserId, trip.publicId(), working.publicId(), request.expectedDraftVersion());
        }
        trips.insertPlanned(trip.id(), UUID.randomUUID(), name, working.startDate(), working.endDate(), snapshot);
        return response(trips.findByPublicIdAndOwnerUserId(trip.publicId(), ownerUserId).orElseThrow());
    }

    @Transactional
    public TripResponse updateOption(long ownerUserId, String tripId, String optionId, TripRequests.OptionUpdate request) {
        if (request == null) throw validation("request", "A request body is required.");
        Trip trip = ownedTrip(ownerUserId, tripId);
        requireActiveTrip(trip);
        PlannedItinerary option = ownedOption(trip, optionId);
        TripDraft working = trip.drafts().get(0);
        String name = validateName(request.name());
        if (bookingRepository.isPlannedItineraryBooked(option.id())) {
            throw new ApiException(409, "IMMUTABLE_BOOKED_OPTION", "An option with booking history cannot be updated.");
        }
        if (working.version() != request.expectedDraftVersion()) {
            throw draftConflict(working.version());
        }
        DraftSelections snapshot = validOptionSnapshot(trip, working);
        if (!trips.advanceVersionForOption(trip.id(), ownerUserId, request.expectedVersion(),
                option.id(), request.expectedOptionVersion())) {
            throw parentConflict(ownerUserId, trip.publicId());
        }
        trips.replacePlannedSnapshots(option.id(), name, working.startDate(), working.endDate(), snapshot);
        return response(trips.findByPublicIdAndOwnerUserId(trip.publicId(), ownerUserId).orElseThrow(),
                optionReplacementSummary(working.publicId(), option.selections(), snapshot, trip.travelerCount()));
    }

    @Transactional
    public TripResponse renameOption(long ownerUserId, String tripId, String optionId, TripRequests.OptionRename request) {
        if (request == null) throw validation("request", "A request body is required.");
        Trip trip = ownedTrip(ownerUserId, tripId);
        requireActiveTrip(trip);
        PlannedItinerary option = ownedOption(trip, optionId);
        String name = validateName(request.name());
        if (bookingRepository.isPlannedItineraryBooked(option.id())) {
            throw new ApiException(409, "IMMUTABLE_BOOKED_OPTION", "An option with booking history cannot be renamed.");
        }
        if (!trips.advanceVersionForOption(trip.id(), ownerUserId, request.expectedVersion(),
                option.id(), request.expectedOptionVersion())) {
            throw parentConflict(ownerUserId, trip.publicId());
        }
        trips.renameOption(option.id(), name);
        return response(trips.findByPublicIdAndOwnerUserId(trip.publicId(), ownerUserId).orElseThrow());
    }

    @Transactional
    public TripResponse loadOption(long ownerUserId, String tripId, String optionId, TripRequests.OptionLoad request) {
        if (request == null) throw validation("request", "A request body is required.");
        Trip trip = ownedTrip(ownerUserId, tripId);
        requireActiveTrip(trip);
        PlannedItinerary option = ownedOption(trip, optionId);
        TripDraft working = trip.drafts().get(0);
        if (option.version() != request.expectedOptionVersion()) {
            throw new ApiException(409, "VERSION_CONFLICT", "The option has changed. Reload before opening.",
                    Map.of("currentOptionVersion", Long.toString(option.version())));
        }
        DraftSelections source = option.selections();
        if (source == null || (source.airfare() == null && source.stay() == null && source.rental() == null)) {
            throw validation("components", "This option has no usable selected component.");
        }
        boolean different = !working.startDate().equals(option.startDate()) || !working.endDate().equals(option.endDate())
                || !sameSelectionIdentities(working.selections(), source);
        if (different && !request.replaceWorking()) {
            throw new ApiException(409, "WORKING_REPLACEMENT_REQUIRED",
                    "Opening this option will replace the current Working plan. Confirm replacement or save it as a new option first.");
        }
        if (!trips.advanceVersionForDraft(trip.id(), ownerUserId, request.expectedVersion(),
                working.id(), request.expectedDraftVersion())) {
            throw mutationConflict(ownerUserId, trip.publicId(), working.publicId(), request.expectedDraftVersion());
        }
        List<ComponentRemovalResponse> removals = new ArrayList<>();
        List<ComponentAdjustmentResponse> adjustments = new ArrayList<>();
        AirfareSelection airfare = null;
        StaySelection stay = null;
        RentalSelection rental = null;
        if (source.airfare() != null) {
            var checked = trips.revalidateAirfare(trip.destination().id(), option.startDate(), option.endDate(),
                    trip.travelerCount(), trip.travelerCount(), source.airfare(), working.publicId());
            if (checked.valid()) {
                airfare = new AirfareSelection(source.airfare().outboundFlightInstanceId(),
                        source.airfare().returnFlightInstanceId(), null, null, 0, 0, 0, 0, 0, 0);
                if (checked.adjustment() != null) adjustments.add(checked.adjustment());
            } else if (checked.removal() != null) removals.add(checked.removal());
        }
        if (source.stay() != null) {
            var checked = trips.revalidateStay(trip.destination().id(), option.startDate(), option.endDate(),
                    option.startDate(), option.endDate(), trip.travelerCount(), trip.travelerCount(),
                    source.stay(), working.publicId());
            if (checked.valid()) {
                stay = new StaySelection(source.stay().accommodationUnitId(), checked.newUnitCount(), null, null, List.of());
                if (checked.adjustment() != null) adjustments.add(checked.adjustment());
            } else if (checked.removal() != null) removals.add(checked.removal());
        }
        if (source.rental() != null) {
            var checked = trips.revalidateRental(trip.destination().id(), option.startDate(), option.endDate(),
                    trip.travelerAges(), source.rental(), working.publicId());
            if (checked.valid()) {
                rental = new RentalSelection(source.rental().rentalUnitId(), source.rental().pickupAt(),
                        source.rental().returnAt(), null, null, null, 0, 0, 0);
                if (checked.adjustment() != null) adjustments.add(checked.adjustment());
            } else if (checked.removal() != null) removals.add(checked.removal());
        }
        if (airfare == null && stay == null && rental == null) {
            throw validation("components", "This option no longer has any usable selected component.");
        }
        DraftSelections copy = new DraftSelections(
                airfare, stay, rental);
        trips.replaceWorkingSelections(trip.id(), working.id(), option.startDate(), option.endDate(), copy);
        return response(trips.findByPublicIdAndOwnerUserId(trip.publicId(), ownerUserId).orElseThrow(),
                new RevisionSummaryResponse(removals, adjustments));
    }

    private DraftSelections validOptionSnapshot(Trip trip, TripDraft working) {
        if (isExpired(working.startDate())) {
            throw new ApiException(400, "ALTERNATIVE_EXPIRED", "An option cannot be saved after its departure date.");
        }
        DraftSelections selections = working.selections();
        if (selections == null || (selections.airfare() == null && selections.stay() == null && selections.rental() == null)) {
            throw validation("components", "Select at least one component before saving an option.");
        }
        DraftSelections resolved = trips.resolveSelectionsForOption(trip, selections, working.startDate(), working.endDate());
        if (selections.airfare() != null && resolved.airfare() == null) throw validation("airfare", "Selected flights no longer match these dates.");
        if (selections.stay() != null && resolved.stay() == null) throw validation("stay", "Selected stay no longer matches these dates.");
        if (selections.rental() != null && resolved.rental() == null) throw validation("rental", "Selected car no longer matches these dates.");
        return resolved;
    }

    private RevisionSummaryResponse optionReplacementSummary(UUID workingId, DraftSelections before,
            DraftSelections after, int travelers) {
        List<ComponentRemovalResponse> removals = new ArrayList<>();
        List<ComponentAdjustmentResponse> adjustments = new ArrayList<>();
        if (before.airfare() != null && after.airfare() == null) {
            removals.add(new ComponentRemovalResponse(workingId, "airfare", "Flight removed from this option."));
        } else if (before.airfare() != null && after.airfare() != null) {
            long oldPrice = tallyEngine.calculateAirfareTotal(before.airfare(), travelers);
            long newPrice = tallyEngine.calculateAirfareTotal(after.airfare(), travelers);
            if (oldPrice != newPrice) adjustments.add(new ComponentAdjustmentResponse(workingId, "airfare", "PRICE",
                    null, null, oldPrice, newPrice, "Flight price refreshed for this option."));
        }
        if (before.stay() != null && after.stay() == null) {
            removals.add(new ComponentRemovalResponse(workingId, "stay", "Stay removed from this option."));
        } else if (before.stay() != null && after.stay() != null) {
            long oldPrice = tallyEngine.calculateStayTotal(before.stay());
            long newPrice = tallyEngine.calculateStayTotal(after.stay());
            if (oldPrice != newPrice || before.stay().unitCount() != after.stay().unitCount()) {
                adjustments.add(new ComponentAdjustmentResponse(workingId, "stay", "ROOM_COUNT_AND_PRICE",
                        before.stay().unitCount(), after.stay().unitCount(), oldPrice, newPrice,
                        "Stay rooms and price refreshed for this option."));
            }
        }
        if (before.rental() != null && after.rental() == null) {
            removals.add(new ComponentRemovalResponse(workingId, "rental", "Car removed from this option."));
        } else if (before.rental() != null && after.rental() != null) {
            long oldPrice = tallyEngine.calculateRentalTotal(before.rental());
            long newPrice = tallyEngine.calculateRentalTotal(after.rental());
            if (oldPrice != newPrice) adjustments.add(new ComponentAdjustmentResponse(workingId, "rental", "PRICE",
                    null, null, oldPrice, newPrice, "Car price refreshed for this option."));
        }
        return new RevisionSummaryResponse(removals, adjustments);
    }

    private PlannedItinerary ownedOption(Trip trip, String optionId) {
        try {
            UUID publicId = UUID.fromString(optionId);
            return trip.planned().stream().filter(candidate -> candidate.publicId().equals(publicId))
                    .findFirst().orElseThrow(this::notFound);
        } catch (IllegalArgumentException exception) { throw notFound(); }
    }

    private static boolean sameSelectionIdentities(DraftSelections a, DraftSelections b) {
        if (a == null || b == null) return a == b;
        return java.util.Objects.equals(a.airfare() == null ? null : List.of(a.airfare().outboundFlightInstanceId(), a.airfare().returnFlightInstanceId()),
                    b.airfare() == null ? null : List.of(b.airfare().outboundFlightInstanceId(), b.airfare().returnFlightInstanceId()))
                && java.util.Objects.equals(a.stay() == null ? null : List.of(a.stay().accommodationUnitId(), a.stay().unitCount()),
                    b.stay() == null ? null : List.of(b.stay().accommodationUnitId(), b.stay().unitCount()))
                && java.util.Objects.equals(a.rental() == null ? null : List.of(a.rental().rentalUnitId(), a.rental().pickupAt(), a.rental().returnAt()),
                    b.rental() == null ? null : List.of(b.rental().rentalUnitId(), b.rental().pickupAt(), b.rental().returnAt()));
    }

    @Transactional
    public TripResponse duplicateAlternative(long ownerUserId, String tripId, String alternativeId, TripRequests.AlternativeDuplicate request) {
        if (request == null) throw validation("request", "A request body is required.");
        Trip trip = ownedTrip(ownerUserId, tripId);
        requireActiveTrip(trip);
        ownedAlternative(trip, alternativeId);
        if (trip.version() != request.expectedVersion()) throw parentConflict(ownerUserId, trip.publicId());
        throw new ApiException(409, "WORKING_PLAN_EXISTS", "Each Trip has one Working plan.");
    }

    @Transactional
    public TripResponse deleteAlternative(long ownerUserId, String tripId, String alternativeId, TripRequests.AlternativeDelete request) {
        if (request == null) throw validation("request", "A request body is required.");
        Trip trip = ownedTrip(ownerUserId, tripId);
        requireActiveTrip(trip);
        TripAlternative source = ownedAlternative(trip, alternativeId);
        if (source instanceof TripDraft draft) {
            if (request.expectedDraftVersion() == null) throw validation("expectedDraftVersion", "This field is required for a Draft source.");
            if (!trips.advanceVersionForDraft(trip.id(), ownerUserId, request.expectedVersion(), draft.id(), request.expectedDraftVersion())) throw mutationConflict(ownerUserId, trip.publicId(), draft.publicId(), request.expectedDraftVersion());
            throw new ApiException(409, "WORKING_PLAN_REQUIRED", "The Working plan cannot be deleted.");
        } else {
            if (bookingRepository.isPlannedItineraryBooked(source.id())) {
                throw new ApiException(409, "CANNOT_DELETE_BOOKED_ALTERNATIVE", "Cannot delete a Saved option with booking history.");
            }
            if (!Boolean.TRUE.equals(request.confirmed())) throw validation("confirmed", "Set confirmed to true before deleting a Planned alternative.");
            if (request.expectedDraftVersion() != null) throw validation("expectedDraftVersion", "Planned alternatives do not have a Draft version.");
            if (!trips.advanceVersion(trip.id(), ownerUserId, request.expectedVersion())) throw parentConflict(ownerUserId, trip.publicId());
            trips.deletePlanned(trip.id(), source.id());
        }
        return response(trips.findByPublicIdAndOwnerUserId(trip.publicId(), ownerUserId).orElseThrow());
    }

    @Transactional
    public void deleteTrip(long ownerUserId, String tripId, TripRequests.TripDelete request) {
        if (request == null) throw validation("request", "A request body is required.");
        Trip trip = ownedTrip(ownerUserId, tripId);
        if (!Boolean.TRUE.equals(request.confirmed())) {
            throw validation("confirmed", "Set confirmed to true before deleting a Trip.");
        }
        if (trips.hasBookingHistory(trip.id())) {
            throw new ApiException(409, "CANNOT_DELETE_BOOKED_TRIP", "Trips with booking history cannot be permanently deleted.");
        }
        if (trip.version() != request.expectedVersion()) {
            throw parentConflict(ownerUserId, trip.publicId());
        }
        if (trip.drafts().size() != request.expectedDraftCount() || trip.planned().size() != request.expectedPlannedCount()) {
            throw new ApiException(409, "STALE_CONFIRMATION", "The Trip alternative counts have changed since confirmation.");
        }
        trips.deleteTrip(trip.id(), ownerUserId);
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
        if (ages.stream().anyMatch(java.util.Objects::isNull)) throw validation("travelerAges", "Provide exactly one age for each traveler.");
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
                    var result = trips.revalidateStay(destination.id(), startDate, endDate, plannedSource.startDate(),
                            plannedSource.endDate(), travelerCount, sourceTrip.travelerCount(), sourceSelections.stay(), newDraftPublicId);
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
        String label = sourceTrip.label();
        trips.createAggregate(ownerUserId, newTripPublicId, destination, startDate, endDate, travelerCount,
                ages, budgetCents, label, UUID.randomUUID());
        Trip newTrip = trips.findByPublicIdAndOwnerUserId(newTripPublicId, ownerUserId).orElseThrow();
        for (int index = 0; index < draftsToCreate.size(); index++) {
            TripRepository.DraftCreationSpec spec = draftsToCreate.get(index);
            TripDraft source = new TripDraft(0, spec.draftPublicId(), 0, spec.selections(), startDate, endDate);
            DraftSelections snapshot = trips.resolveSelectionsForOption(newTrip, source.selections(), startDate, endDate);
            trips.insertPlanned(newTrip.id(), UUID.randomUUID(), selectedPlanned.get(index).name(), startDate, endDate, snapshot);
        }
        newTrip = trips.findByPublicIdAndOwnerUserId(newTripPublicId, ownerUserId).orElseThrow();
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

    private static String validateName(String supplied) {
        if (supplied == null || supplied.isBlank()) throw validation("name", "Enter a Trip name.");
        String name = supplied.trim();
        if (name.length() > 300) throw validation("name", "Trip name must be 300 characters or fewer.");
        return name;
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

    public TripResponse toResponse(Trip trip) {
        return response(trip, null);
    }

    public BookingResponse toBookingResponse(BookingRecord record, Trip trip) {
        DraftSelections selections = bookingRepository.loadBookingSelections(record.id());
        ItineraryTallyResponse tally = tallyEngine.calculateTally(selections, trip.travelerCount(), trip.budgetCents());
        DraftSelectionResponse selectionResponse = selectionResponse(selections);
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

    private TripResponse response(Trip trip) {
        return response(trip, null);
    }

    private TripResponse response(Trip trip, RevisionSummaryResponse revisionSummary) {
        List<DraftResponse> drafts = trip.drafts().stream().map(draft -> {
            ItineraryTallyResponse tally = tallyEngine.calculateTally(draft.selections(), trip.travelerCount(), trip.budgetCents());
            return new DraftResponse(draft.publicId(), draft.version(), selectionResponse(draft.selections()), tally,
                    draft.startDate(), draft.endDate());
        }).toList();

        List<PlannedResponse> planned = trip.planned().stream().map(item -> {
            ItineraryTallyResponse tally = tallyEngine.calculateTally(item.selections(), trip.travelerCount(), trip.budgetCents());
            return new PlannedResponse(item.publicId(), selectionResponse(item.selections()), tally,
                    item.name(), item.startDate(), item.endDate(), item.version(),
                    bookingRepository.isPlannedItineraryBooked(item.id()));
        }).toList();

        List<AlternativeResponse> alternatives = new ArrayList<>();
        trip.drafts().forEach(draft -> {
            ItineraryTallyResponse tally = tallyEngine.calculateTally(draft.selections(), trip.travelerCount(), trip.budgetCents());
            alternatives.add(new AlternativeResponse(draft.publicId(), draft.lifecycle(), draft.version(), selectionResponse(draft.selections()), tally,
                    "Working plan", draft.startDate(), draft.endDate()));
        });
        trip.planned().forEach(item -> {
            ItineraryTallyResponse tally = tallyEngine.calculateTally(item.selections(), trip.travelerCount(), trip.budgetCents());
            alternatives.add(new AlternativeResponse(item.publicId(), item.lifecycle(), item.version(), selectionResponse(item.selections()), tally,
                    item.name(), item.startDate(), item.endDate()));
        });

        ItineraryTallyResponse tripTally;
        if (!trip.drafts().isEmpty()) {
            tripTally = tallyEngine.calculateTally(trip.drafts().get(0).selections(), trip.travelerCount(), trip.budgetCents());
        } else if (!trip.planned().isEmpty()) {
            tripTally = tallyEngine.calculateTally(trip.planned().get(0).selections(), trip.travelerCount(), trip.budgetCents());
        } else {
            tripTally = tallyEngine.calculateTally(null, trip.travelerCount(), trip.budgetCents());
        }

        BookingResponse booking = bookingRepository.findPrimaryBookingRecordByTripId(trip.id())
                .map(r -> toBookingResponse(r, trip))
                .orElse(null);

        return new TripResponse(trip.publicId(), trip.destination().key(), trip.destination().name(), "PDX", trip.startDate(),
                trip.endDate(), trip.travelerCount(), trip.travelerAges(), trip.budgetCents(), trip.label(), trip.status(), trip.version(),
                drafts, planned, List.copyOf(alternatives), revisionSummary, tripTally, booking);
    }

    public static DraftSelectionResponse selectionResponse(DraftSelections selections) {
        if (selections == null) return null;
        AirfareSelection airfare = selections.airfare(); StaySelection stay = selections.stay(); RentalSelection rental = selections.rental();
        return new DraftSelectionResponse(
                airfare == null ? null : new AirfareComponentResponse(
                        airfare.outboundFlightInstanceId(), airfare.returnFlightInstanceId(),
                        airfare.outboundDescription(), airfare.returnDescription(),
                        airfare.outboundBaseFareCents(), airfare.outboundTaxCents(), airfare.outboundFeeCents(),
                        airfare.returnBaseFareCents(), airfare.returnTaxCents(), airfare.returnFeeCents(),
                        airfare.outboundCarrierName(), airfare.outboundFlightNumber(), airfare.outboundStopCount(),
                        airfare.outboundLayoverAirportCode(), airfare.outboundLayoverDurationMinutes(),
                        airfare.outboundDepartureTime(), airfare.outboundArrivalTime(),
                        airfare.outboundDepartureTimeZone(), airfare.outboundArrivalTimeZone(), airfare.outboundDurationMinutes(),
                        airfare.returnCarrierName(), airfare.returnFlightNumber(), airfare.returnStopCount(),
                        airfare.returnLayoverAirportCode(), airfare.returnLayoverDurationMinutes(),
                        airfare.returnDepartureTime(), airfare.returnArrivalTime(),
                        airfare.returnDepartureTimeZone(), airfare.returnArrivalTimeZone(), airfare.returnDurationMinutes(),
                        airfare.totalDurationMinutes()),
                stay == null ? null : new StayComponentResponse(
                        stay.accommodationUnitId(), stay.unitCount(), stay.propertyName(), stay.unitName(),
                        stay.nights().stream().map(n -> new StayNightResponse(n.date(), n.basePriceCents(), n.taxCents(), n.feeCents())).toList(),
                        stay.propertyCategory(), stay.locationDescription(), stay.distanceToCityCenterMeters(),
                        stay.guestCapacity(), stay.requiredRoomCount()),
                rental == null ? null : new RentalComponentResponse(
                        rental.rentalUnitId(), rental.pickupAt(), rental.returnAt(), rental.locationName(),
                        rental.vehicleClassName(), rental.unitIdentifier(), rental.dailyBasePriceCents(),
                        rental.dailyTaxCents(), rental.dailyFeeCents(), rental.vehicleCategory()));
    }

    public DraftReadinessResponse inspectDraftReadiness(long ownerUserId, String tripId, String draftId) {
        Trip trip = ownedTrip(ownerUserId, tripId);
        TripDraft draft = ownedDraft(trip, draftId);
        return evaluateDraftReadiness(trip, draft);
    }

    public DraftReadinessResponse evaluateDraftReadiness(Trip trip, TripDraft draft) {
        Map<String, String> issues = evaluateDraftBlockingIssues(trip, draft);
        ItineraryTallyResponse tally = tallyEngine.calculateTally(draft.selections(), trip.travelerCount(), trip.budgetCents());
        boolean isOverBudget = tally.isOverBudget();
        long budgetOverageCents = isOverBudget && tally.budgetOverageCents() != null ? tally.budgetOverageCents() : 0L;
        boolean requiresOverageAcknowledgment = isOverBudget;
        boolean ready = issues.isEmpty() && !requiresOverageAcknowledgment;
        return new DraftReadinessResponse(ready, issues, isOverBudget, budgetOverageCents, requiresOverageAcknowledgment);
    }

    private Map<String, String> evaluateDraftBlockingIssues(Trip trip, TripDraft draft) {
        Map<String, String> issues = new java.util.LinkedHashMap<>();
        if (trip.destination() == null) {
            issues.put("destination", "Select a destination before planning.");
        }
        if (draft.startDate() == null || draft.endDate() == null) {
            issues.put("dates", "Trip dates must be specified.");
        } else if (draft.startDate().isBefore(FIRST_SUPPORTED_DATE) || draft.endDate().isAfter(LAST_SUPPORTED_DATE)
                || !draft.startDate().isBefore(draft.endDate())
                || ChronoUnit.DAYS.between(draft.startDate(), draft.endDate()) < 1
                || ChronoUnit.DAYS.between(draft.startDate(), draft.endDate()) > 14) {
            issues.put("dates", "Trip dates must be between March 1 and March 31, 2027, with a duration between 1 and 14 nights.");
        } else if (isExpired(draft.startDate())) {
            issues.put("dates", "Trip departure date has passed.");
        }

        if (trip.travelerCount() < 1 || trip.travelerCount() > 8) {
            issues.put("travelerCount", "Traveler count must be between 1 and 8.");
        }

        if (trip.travelerAges() == null || trip.travelerAges().size() != trip.travelerCount()
                || trip.travelerAges().stream().anyMatch(a -> a == null || a < 0 || a > MAX_TRAVELER_AGE)) {
            issues.put("travelerAges", "Provide exact ages for every traveler before planning.");
        } else if (trip.travelerAges().stream().noneMatch(a -> a >= 18)) {
            issues.put("adult", "At least one traveler must be an adult before planning.");
        }

        if (trip.budgetCents() == null || trip.budgetCents() < 0) {
            issues.put("budgetCents", "Provide a budget before planning.");
        }

        DraftSelections selections = draft.selections();
        if (selections == null || (selections.airfare() == null && selections.stay() == null && selections.rental() == null)) {
            issues.put("components", "Select at least one structurally valid reservable component before planning.");
        }

        if (selections != null && selections.airfare() != null) {
            AirfareSelection af = selections.airfare();
            var outOpt = airfareSearchRepository.findLegById(af.outboundFlightInstanceId());
            var retOpt = airfareSearchRepository.findLegById(af.returnFlightInstanceId());
            if (outOpt.isEmpty() || retOpt.isEmpty()) {
                issues.put("airfare", "Selected flight was not found or is unavailable.");
            } else {
                var out = outOpt.get();
                var ret = retOpt.get();
                String destAirportIata = trip.destination() != null
                        ? airfareSearchRepository.findAirportIataCodeForDestination(trip.destination().id()).orElse(null)
                        : null;
                LocalDate outDate = out.departureTime().atZoneSameInstant(java.time.ZoneId.of(out.departureTimeZone())).toLocalDate();
                LocalDate retDate = ret.departureTime().atZoneSameInstant(java.time.ZoneId.of(ret.departureTimeZone())).toLocalDate();
                if (!"PDX".equals(out.originAirportCode()) || destAirportIata == null || !out.destinationAirportCode().equals(destAirportIata)
                        || draft.startDate() == null || !outDate.equals(draft.startDate())
                        || !ret.originAirportCode().equals(destAirportIata) || !"PDX".equals(ret.destinationAirportCode())
                        || draft.endDate() == null || !retDate.equals(draft.endDate())) {
                    issues.put("airfare", "Selected flights do not match trip route or dates.");
                } else if (out.availableSeats() < trip.travelerCount() || ret.availableSeats() < trip.travelerCount()) {
                    issues.put("airfare", "Selected flight does not have enough available seats for party size.");
                }
            }
        }

        if (selections != null && selections.stay() != null) {
            StaySelection st = selections.stay();
            if (draft.startDate() != null && draft.endDate() != null && draft.startDate().isBefore(draft.endDate())) {
                var stayOpt = staySearchRepository.findCandidateById(st.accommodationUnitId(), draft.startDate(), draft.endDate());
                if (stayOpt.isEmpty() || (trip.destination() != null && stayOpt.get().destinationId() != trip.destination().id())) {
                    issues.put("stay", "Selected accommodation unit was not found or does not match destination.");
                } else {
                    var candidate = stayOpt.get();
                    if (candidate.guestCapacity() * st.unitCount() < trip.travelerCount()) {
                        issues.put("stay", "Selected accommodation unit capacity is insufficient for party size.");
                    } else {
                        long expectedNights = ChronoUnit.DAYS.between(draft.startDate(), draft.endDate());
                        if (candidate.nights().size() != expectedNights || candidate.nights().stream().anyMatch(n -> n.availableInventory() < st.unitCount())) {
                            issues.put("stay", "Selected accommodation has insufficient inventory for the requested dates.");
                        }
                    }
                }
            } else {
                issues.put("stay", "Valid trip dates are required to evaluate accommodation availability.");
            }
        }

        if (selections != null && selections.rental() != null) {
            RentalSelection rn = selections.rental();
            var rentalOpt = rentalSearchRepository.findUnitById(rn.rentalUnitId());
            if (rentalOpt.isEmpty()) {
                issues.put("rental", "Selected rental car unit was not found.");
            } else {
                var unit = rentalOpt.get();
                if (trip.destination() != null && unit.destinationId() != trip.destination().id()) {
                    issues.put("rental", "Selected rental car does not match trip destination.");
                } else if (!rn.pickupAt().isBefore(rn.returnAt())) {
                    issues.put("rental", "Rental pickup time must be before return time.");
                } else if (draft.startDate() != null && draft.endDate() != null
                        && (rn.pickupAt().toLocalDate().isBefore(draft.startDate()) || rn.returnAt().toLocalDate().isAfter(draft.endDate()))) {
                    issues.put("rental", "Rental dates must be within the trip interval.");
                } else if (trip.travelerAges() == null || trip.travelerAges().stream().noneMatch(a -> a != null && a >= 25)) {
                    issues.put("rental", "At least one traveler must be 25 or older to rent a vehicle.");
                } else if (!rentalSearchRepository.isUnitAvailable(rn.rentalUnitId(), rn.pickupAt(), rn.returnAt())) {
                    issues.put("rental", "Selected rental car is not available for the requested interval.");
                }
            }
        }

        return issues;
    }

    public AirfareSearchResponse searchAirfare(long ownerUserId, String tripId, String draftId, boolean directOnly, String sortStr) {
        Trip trip = ownedTrip(ownerUserId, tripId);
        TripDraft draft = null;
        if (draftId != null) {
            draft = ownedDraft(trip, draftId);
        }
        AirfareSort sort = AirfareSort.from(sortStr);
        TripDraft working = draft != null ? draft : trip.drafts().get(0);
        return airfareSearchService.search(
                trip.destination().id(),
                trip.destination().key(),
                working.startDate(),
                working.endDate(),
                trip.travelerCount(),
                trip.publicId(),
                draft != null ? draft.publicId() : null,
                directOnly,
                sort
        );
    }

    public AirfareSearchResponse searchPublicAirfare(String destinationKey, LocalDate startDate, LocalDate endDate,
            int travelerCount, boolean directOnly, String sortStr) {
        Destination destination = validatePublicSearch(destinationKey, startDate, endDate, travelerCount);
        return airfareSearchService.search(destination.id(), destination.key(), startDate, endDate,
                travelerCount, null, null, directOnly, AirfareSort.from(sortStr));
    }

    public StaySearchResponse searchPublicStays(String destinationKey, LocalDate startDate, LocalDate endDate,
            int travelerCount, Long budgetCents, String typeStr, String sortStr) {
        Destination destination = validatePublicSearch(destinationKey, startDate, endDate, travelerCount);
        if (budgetCents != null && (budgetCents < 0 || budgetCents > MAX_BUDGET_CENTS))
            throw validation("budgetCents", "Budget must be between 0 and 100000000 cents.");
        return staySearchService.search(destination.id(), destination.key(), startDate, endDate,
                travelerCount, null, null, AccommodationType.from(typeStr), StaySort.from(sortStr), budgetCents);
    }

    private Destination validatePublicSearch(String destinationKey, LocalDate startDate, LocalDate endDate, int travelerCount) {
        Destination destination = trips.findSupportedDestination(required(destinationKey, "destinationKey"))
                .orElseThrow(() -> validation("destinationKey", "Choose a supported destination."));
        validateDates(required(startDate, "startDate"), required(endDate, "endDate"));
        if (travelerCount < 1 || travelerCount > 8) throw validation("travelerCount", "Traveler count must be between 1 and 8.");
        return destination;
    }

    @Transactional
    public TripResponse selectDraftAirfare(long ownerUserId, String tripId, String draftId, TripRequests.AirfareSelectionRequest request) {
        if (request == null) throw validation("request", "A request body is required.");
        Trip trip = ownedTrip(ownerUserId, tripId);
        requireActiveTrip(trip);
        TripDraft draft = ownedDraft(trip, draftId);

        if (request.outboundFlightInstanceId() == request.returnFlightInstanceId()) {
            throw validation("outboundFlightInstanceId", "Outbound and return flight instances must be distinct.");
        }

        var outboundOpt = airfareSearchRepository.findLegById(request.outboundFlightInstanceId());
        var returnOpt = airfareSearchRepository.findLegById(request.returnFlightInstanceId());

        if (outboundOpt.isEmpty()) {
            throw validation("outboundFlightInstanceId", "Selected outbound flight was not found or is unavailable.");
        }
        if (returnOpt.isEmpty()) {
            throw validation("returnFlightInstanceId", "Selected return flight was not found or is unavailable.");
        }

        var outbound = outboundOpt.get();
        var returnFlight = returnOpt.get();

        String expectedDestAirport = airfareSearchRepository.findAirportIataCodeForDestination(trip.destination().id()).orElse("");

        boolean outboundMatches = "PDX".equals(outbound.originAirportCode())
                && expectedDestAirport.equals(outbound.destinationAirportCode())
                && draft.startDate().equals(outbound.departureTime().atZoneSameInstant(java.time.ZoneId.of(outbound.departureTimeZone())).toLocalDate())
                && outbound.availableSeats() >= trip.travelerCount();

        if (!outboundMatches) {
            throw validation("outboundFlightInstanceId", "Selected outbound flight does not match trip destination, dates, or party size.");
        }

        boolean returnMatches = expectedDestAirport.equals(returnFlight.originAirportCode())
                && "PDX".equals(returnFlight.destinationAirportCode())
                && draft.endDate().equals(returnFlight.departureTime().atZoneSameInstant(java.time.ZoneId.of(returnFlight.departureTimeZone())).toLocalDate())
                && returnFlight.availableSeats() >= trip.travelerCount();

        if (!returnMatches) {
            throw validation("returnFlightInstanceId", "Selected return flight does not match trip destination, dates, or party size.");
        }

        if (!trips.advanceVersionForDraftMutation(trip.id(), ownerUserId, request.expectedVersion(), draft.id(), request.expectedDraftVersion())) {
            throw mutationConflict(ownerUserId, trip.publicId(), draft.publicId(), request.expectedDraftVersion());
        }

        trips.saveDraftAirfareSelection(draft.id(), request.outboundFlightInstanceId(), request.returnFlightInstanceId());
        return response(trips.findByPublicIdAndOwnerUserId(trip.publicId(), ownerUserId).orElseThrow());
    }

    @Transactional
    public TripResponse removeDraftAirfare(long ownerUserId, String tripId, String draftId, TripRequests.DraftMutation request) {
        if (request == null) throw validation("request", "A request body is required.");
        Trip trip = ownedTrip(ownerUserId, tripId);
        requireActiveTrip(trip);
        TripDraft draft = ownedDraft(trip, draftId);

        if (!trips.advanceVersionForDraftMutation(trip.id(), ownerUserId, request.expectedVersion(), draft.id(), request.expectedDraftVersion())) {
            throw mutationConflict(ownerUserId, trip.publicId(), draft.publicId(), request.expectedDraftVersion());
        }

        trips.deleteDraftAirfareSelection(draft.id());
        return response(trips.findByPublicIdAndOwnerUserId(trip.publicId(), ownerUserId).orElseThrow());
    }

    public StaySearchResponse searchStays(long ownerUserId, String tripId, String draftId, String typeStr, String sortStr) {
        Trip trip = ownedTrip(ownerUserId, tripId);
        TripDraft draft = null;
        if (draftId != null) {
            draft = ownedDraft(trip, draftId);
        }
        AccommodationType type = AccommodationType.from(typeStr);
        StaySort sort = StaySort.from(sortStr);

        Long availableBudgetCents = tallyEngine.calculateAvailableStaySearchBudget(
                trip.budgetCents(),
                draft != null ? draft.selections() : null,
                trip.travelerCount()
        );
        TripDraft working = draft != null ? draft : trip.drafts().get(0);

        return staySearchService.search(
                trip.destination().id(),
                trip.destination().key(),
                working.startDate(),
                working.endDate(),
                trip.travelerCount(),
                trip.publicId(),
                draft != null ? draft.publicId() : null,
                type,
                sort,
                availableBudgetCents
        );
    }

    @Transactional
    public TripResponse selectDraftStay(long ownerUserId, String tripId, String draftId, TripRequests.StaySelectionRequest request) {
        if (request == null) throw validation("request", "A request body is required.");
        Trip trip = ownedTrip(ownerUserId, tripId);
        requireActiveTrip(trip);
        TripDraft draft = ownedDraft(trip, draftId);

        var candidateOpt = staySearchRepository.findCandidateById(request.accommodationUnitId(), draft.startDate(), draft.endDate());
        if (candidateOpt.isEmpty()) {
            throw validation("accommodationUnitId", "Selected accommodation unit was not found or is unavailable.");
        }
        var candidate = candidateOpt.get();

        if (candidate.destinationId() != trip.destination().id()) {
            throw validation("accommodationUnitId", "Selected accommodation does not match trip destination.");
        }

        int requiredRooms;
        if ("WHOLE_PROPERTY".equals(candidate.unitKind()) || "VACATION_RENTAL".equals(candidate.propertyCategory())) {
            if (candidate.guestCapacity() < trip.travelerCount()) {
                throw validation("accommodationUnitId", "Selected accommodation capacity is insufficient for " + trip.travelerCount() + " travelers.");
            }
            requiredRooms = 1;
        } else {
            requiredRooms = (int) Math.ceil((double) trip.travelerCount() / candidate.guestCapacity());
        }

        if (request.unitCount() != null && request.unitCount() != requiredRooms) {
            throw validation("unitCount", "Unit count must be " + requiredRooms + " for " + trip.travelerCount() + " travelers.");
        }

        long requiredNights = java.time.temporal.ChronoUnit.DAYS.between(draft.startDate(), draft.endDate());
        if (candidate.nights().size() != requiredNights) {
            throw validation("accommodationUnitId", "Selected accommodation has no inventory for trip dates.");
        }
        boolean hasSufficientInventory = candidate.nights().stream()
                .allMatch(night -> night.availableInventory() >= requiredRooms);
        if (!hasSufficientInventory) {
            throw validation("accommodationUnitId", "Selected accommodation has insufficient inventory for the trip dates.");
        }

        if (!trips.advanceVersionForDraftMutation(trip.id(), ownerUserId, request.expectedVersion(), draft.id(), request.expectedDraftVersion())) {
            throw mutationConflict(ownerUserId, trip.publicId(), draft.publicId(), request.expectedDraftVersion());
        }

        trips.saveDraftStaySelection(draft.id(), candidate.unitId(), requiredRooms);
        return response(trips.findByPublicIdAndOwnerUserId(trip.publicId(), ownerUserId).orElseThrow());
    }

    @Transactional
    public TripResponse removeDraftStay(long ownerUserId, String tripId, String draftId, TripRequests.DraftMutation request) {
        if (request == null) throw validation("request", "A request body is required.");
        Trip trip = ownedTrip(ownerUserId, tripId);
        requireActiveTrip(trip);
        TripDraft draft = ownedDraft(trip, draftId);

        if (!trips.advanceVersionForDraftMutation(trip.id(), ownerUserId, request.expectedVersion(), draft.id(), request.expectedDraftVersion())) {
            throw mutationConflict(ownerUserId, trip.publicId(), draft.publicId(), request.expectedDraftVersion());
        }

        trips.deleteDraftStaySelection(draft.id());
        return response(trips.findByPublicIdAndOwnerUserId(trip.publicId(), ownerUserId).orElseThrow());
    }

    public RentalSearchResponse searchRentals(long ownerUserId, String tripId, String draftId, String pickupAtStr, String returnAtStr, String sortStr) {
        Trip trip = ownedTrip(ownerUserId, tripId);
        TripDraft draft = null;
        if (draftId != null) {
            draft = ownedDraft(trip, draftId);
        }

        if (pickupAtStr == null || pickupAtStr.isBlank()) {
            throw validation("pickupAt", "Pickup time is required.");
        }
        if (returnAtStr == null || returnAtStr.isBlank()) {
            throw validation("returnAt", "Return time is required.");
        }

        OffsetDateTime pickupAt;
        try {
            pickupAt = OffsetDateTime.parse(pickupAtStr);
        } catch (DateTimeParseException e) {
            try {
                pickupAt = java.time.LocalDateTime.parse(pickupAtStr).atOffset(java.time.ZoneOffset.UTC);
            } catch (DateTimeParseException ignored) {
                throw validation("pickupAt", "Enter a valid ISO timestamp.");
            }
        }

        OffsetDateTime returnAt;
        try {
            returnAt = OffsetDateTime.parse(returnAtStr);
        } catch (DateTimeParseException e) {
            try {
                returnAt = java.time.LocalDateTime.parse(returnAtStr).atOffset(java.time.ZoneOffset.UTC);
            } catch (DateTimeParseException ignored) {
                throw validation("returnAt", "Enter a valid ISO timestamp.");
            }
        }

        RentalSort sort = RentalSort.from(sortStr);

        Long availableBudgetCents = tallyEngine.calculateAvailableRentalSearchBudget(
                trip.budgetCents(),
                draft != null ? draft.selections() : null,
                trip.travelerCount()
        );
        TripDraft working = draft != null ? draft : trip.drafts().get(0);

        return rentalSearchService.search(
                trip.destination().id(),
                trip.destination().key(),
                working.startDate(),
                working.endDate(),
                trip.travelerAges(),
                trip.publicId(),
                draft != null ? draft.publicId() : null,
                pickupAt,
                returnAt,
                sort,
                availableBudgetCents
        );
    }

    @Transactional
    public TripResponse selectDraftRental(long ownerUserId, String tripId, String draftId, TripRequests.RentalSelectionRequest request) {
        if (request == null) throw validation("request", "A request body is required.");
        Trip trip = ownedTrip(ownerUserId, tripId);
        requireActiveTrip(trip);
        TripDraft draft = ownedDraft(trip, draftId);

        if (!rentalSearchService.isDriverEligible(trip.travelerAges())) {
            throw validation("travelerAges", RentalSearchService.DRIVER_AGE_EXPLANATION);
        }

        var unitOpt = rentalSearchRepository.findUnitById(request.rentalUnitId());
        if (unitOpt.isEmpty()) {
            throw validation("rentalUnitId", "Selected rental car was not found.");
        }
        var unit = unitOpt.get();
        if (unit.destinationId() != trip.destination().id()) {
            throw validation("rentalUnitId", "Selected rental car does not match trip destination.");
        }

        rentalSearchService.validateInterval(trip.destination().id(), draft.startDate(), draft.endDate(), request.pickupAt(), request.returnAt());

        if (!rentalSearchRepository.isUnitAvailable(unit.unitId(), request.pickupAt(), request.returnAt())) {
            throw validation("rentalUnitId", "Selected rental car is unavailable for the requested interval.");
        }

        if (!trips.advanceVersionForDraftMutation(trip.id(), ownerUserId, request.expectedVersion(), draft.id(), request.expectedDraftVersion())) {
            throw mutationConflict(ownerUserId, trip.publicId(), draft.publicId(), request.expectedDraftVersion());
        }

        trips.saveDraftRentalSelection(draft.id(), unit.unitId(), request.pickupAt(), request.returnAt());
        return response(trips.findByPublicIdAndOwnerUserId(trip.publicId(), ownerUserId).orElseThrow());
    }

    @Transactional
    public TripResponse removeDraftRental(long ownerUserId, String tripId, String draftId, TripRequests.DraftMutation request) {
        if (request == null) throw validation("request", "A request body is required.");
        Trip trip = ownedTrip(ownerUserId, tripId);
        requireActiveTrip(trip);
        TripDraft draft = ownedDraft(trip, draftId);

        if (!trips.advanceVersionForDraftMutation(trip.id(), ownerUserId, request.expectedVersion(), draft.id(), request.expectedDraftVersion())) {
            throw mutationConflict(ownerUserId, trip.publicId(), draft.publicId(), request.expectedDraftVersion());
        }

        trips.deleteDraftRentalSelection(draft.id());
        return response(trips.findByPublicIdAndOwnerUserId(trip.publicId(), ownerUserId).orElseThrow());
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
