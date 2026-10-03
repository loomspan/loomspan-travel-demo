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

    private TripPlan ownedPlan(Trip trip, String id) {
        try { UUID uuid = UUID.fromString(id); return trip.plans().stream().filter(p -> p.publicId().equals(uuid)).findFirst().orElseThrow(this::notFound); }
        catch (IllegalArgumentException e) { throw notFound(); }
    }
    private void advancePlan(long owner, Trip trip, TripPlan plan, long version, long childVersion) {
        if (!trips.advanceVersionForOption(trip.id(), owner, version, plan.id(), childVersion)) throw parentConflict(owner, trip.publicId());
    }
    @Transactional
    public TripResponse createPlan(long owner, String id, TripRequests.PlanCreate r) {
        Trip trip = ownedTrip(owner, id); requireActiveTrip(trip);
        String name = validateName(r.name());
        LocalDate start = required(r.startDate(), "startDate"), end = required(r.endDate(), "endDate"); validateDates(start, end);
        int count = required(r.travelerCount(), "travelerCount"); if (count < 1 || count > 8) throw validation("travelerCount", "Traveler count must be between 1 and 8.");
        List<Integer> ages = validateAges(r.travelerAges(), count);
        if (!trips.advanceVersion(trip.id(), owner, r.expectedVersion())) throw parentConflict(owner, trip.publicId());
        trips.insertPlan(trip.id(), UUID.randomUUID(), name, start, end, count, ages, new DraftSelections(null, null, null));
        return response(trips.findByPublicIdAndOwnerUserId(trip.publicId(), owner).orElseThrow());
    }
    private DraftSelections purchased(Trip trip, TripPlan plan) {
        AirfareSelection airfare = null; StaySelection stay = null; RentalSelection rental = null;
        for (BookingRecord booking : bookingRepository.findBookingRecordsByTripId(trip.id())) {
            if (booking.plannedItineraryId() == null || booking.plannedItineraryId() != plan.id()) continue;
            DraftSelections selected = bookingRepository.loadBookingSelections(booking.id());
            if (selected.airfare() != null) airfare = selected.airfare();
            if (selected.stay() != null) stay = selected.stay();
            if (selected.rental() != null) rental = selected.rental();
        }
        return new DraftSelections(airfare, stay, rental);
    }
    private void requireUnlocked(Trip trip, TripPlan plan, String component) {
        DraftSelections locked = purchased(trip, plan);
        boolean protectedSlot = switch(component) { case "airfare" -> locked.airfare() != null; case "stay" -> locked.stay() != null; default -> locked.rental() != null; };
        if (protectedSlot) throw new ApiException(409, "CONFIRMED_COMPONENT_LOCKED", "Purchased components cannot be changed or removed.");
    }
    @Transactional
    public TripResponse savePlan(long owner, String id, String planId, TripRequests.PlanSave r) { return savePlanInternal(owner, id, planId, r, false); }
    private TripResponse savePlanInternal(long owner, String id, String planId, TripRequests.PlanSave r, boolean forceRevalidate) {
        Trip trip = ownedTrip(owner, id); requireActiveTrip(trip); TripPlan plan = ownedPlan(trip, planId);
        LocalDate start = required(r.startDate(), "startDate"), end = required(r.endDate(), "endDate"); validateDates(start, end);
        int count = required(r.travelerCount(), "travelerCount"); if (count < 1 || count > 8) throw validation("travelerCount", "Traveler count must be between 1 and 8.");
        List<Integer> ages = validateAges(r.travelerAges(), count);
        TripPlan updated = new TripPlan(plan.id(), plan.publicId(), plan.name(), start, end, count, ages, plan.version(), plan.selections());
        Trip context = trip.withPlanContext(updated);
        DraftSelections locked = purchased(trip, plan);
        DraftSelections requested = r.selections() == null ? plan.selections() : TripRequests.planSelections(r.selections());
        if (requested.stay() != null && plan.selections().stay() != null && requested.stay().accommodationUnitId() == plan.selections().stay().accommodationUnitId() && requested.stay().unitCount() == plan.selections().stay().unitCount()) requested = new DraftSelections(requested.airfare(), plan.selections().stay(), requested.rental());
        if (r.selections() != null) {
            if (locked.airfare() != null && !sameSelectionIdentities(new DraftSelections(locked.airfare(), null, null), new DraftSelections(requested.airfare(), null, null))) requireUnlocked(trip, plan, "airfare");
            if (locked.stay() != null && (requested.stay() == null || requested.stay().accommodationUnitId() != locked.stay().accommodationUnitId() || requested.stay().unitCount() != locked.stay().unitCount())) requireUnlocked(trip, plan, "stay");
            if (locked.rental() != null && (requested.rental() == null || requested.rental().rentalUnitId() != locked.rental().rentalUnitId() || !requested.rental().pickupAt().equals(locked.rental().pickupAt()) || !requested.rental().returnAt().equals(locked.rental().returnAt()))) requireUnlocked(trip, plan, "rental");
        }
        DraftSelections unconfirmed = new DraftSelections(locked.airfare() == null ? requested.airfare() : null, locked.stay() == null ? requested.stay() : null, locked.rental() == null ? requested.rental() : null);
        List<ComponentRemovalResponse> removals = new ArrayList<>(); List<ComponentAdjustmentResponse> adjustments = new ArrayList<>();
        AirfareSelection airfare = unconfirmed.airfare(); StaySelection stay = unconfirmed.stay(); RentalSelection rental = unconfirmed.rental();
        if (airfare != null && (forceRevalidate || count != plan.travelerCount() || !start.equals(plan.startDate()) || !end.equals(plan.endDate()) || r.selections() != null)) {
            var checked = trips.revalidateAirfare(trip.destination().id(), start, end, count, plan.travelerCount(), airfare, plan.publicId());
            if (!checked.valid()) { airfare = null; removals.add(checked.removal()); } else if (checked.adjustment() != null) adjustments.add(checked.adjustment());
        }
        if (stay != null && (forceRevalidate || count != plan.travelerCount() || !start.equals(plan.startDate()) || !end.equals(plan.endDate()) || r.selections() != null)) {
            var checked = trips.revalidateStay(trip.destination().id(), start, end, plan.startDate(), plan.endDate(), count, plan.travelerCount(), stay, plan.publicId());
            if (!checked.valid()) { stay = null; removals.add(checked.removal()); }
            else { stay = new StaySelection(stay.accommodationUnitId(), checked.newUnitCount(), null, null, List.of()); if (checked.adjustment() != null) adjustments.add(checked.adjustment()); }
        }
        if (rental != null) { var checked = trips.revalidateRental(trip.destination().id(), start, end, ages, rental, plan.publicId()); if (!checked.valid()) { rental = null; removals.add(checked.removal()); } }
        boolean changedContext = forceRevalidate || count != plan.travelerCount() || !start.equals(plan.startDate()) || !end.equals(plan.endDate()) || r.selections() != null;
        DraftSelections resolved = changedContext ? trips.resolveSelectionsForOption(context, new DraftSelections(airfare, stay, rental), start, end) : new DraftSelections(airfare, stay, rental);
        DraftSelections selections = new DraftSelections(locked.airfare() != null ? locked.airfare() : resolved.airfare(), locked.stay() != null ? locked.stay() : resolved.stay(), locked.rental() != null ? locked.rental() : resolved.rental());
        advancePlan(owner, trip, plan, r.expectedVersion(), r.expectedPlanVersion());
        trips.replacePlannedSnapshots(plan.id(), plan.name(), start, end, selections); trips.updatePlanParty(plan.id(), count, ages);
        return response(trips.findByPublicIdAndOwnerUserId(trip.publicId(), owner).orElseThrow(), new RevisionSummaryResponse(removals, adjustments));
    }
    @Transactional
    public TripResponse planAction(long owner, String id, String planId, TripRequests.PlanAction r, String action) {
        Trip trip = ownedTrip(owner, id); requireActiveTrip(trip);
        if (trip.version() != r.expectedVersion()) throw parentConflict(owner, trip.publicId());
        TripPlan plan = ownedPlan(trip, planId);
        String name = action.equals("name") || action.equals("copy") ? validateName(r.name()) : null;
        TripPlan replacement = null;
        if (action.equals("delete")) {
            if (!Boolean.TRUE.equals(r.confirmed())) throw validation("confirmed", "Confirm deletion.");
            if (bookingRepository.isPlannedItineraryBooked(plan.id())) throw new ApiException(409, "CANNOT_DELETE_BOOKED_PLAN", "This plan contains confirmed bookings or booking history and can't be deleted.");
            if (r.expectedPlanCount() == null || r.expectedPlanCount() != trip.plans().size()) throw new ApiException(409, "STALE_CONFIRMATION", "The plan count changed. Review deletion again.");
            if (trip.plans().size() == 1) {
                if (!Boolean.TRUE.equals(r.deleteTrip())) throw validation("deleteTrip", "This is the only plan for this trip. Delete the trip?");
                if (trips.hasBookingHistory(trip.id())) throw new ApiException(409, "CANNOT_DELETE_BOOKED_TRIP", "Trips with booking history cannot be deleted.");
                advancePlan(owner, trip, plan, r.expectedVersion(), r.expectedPlanVersion()); trips.deleteTrip(trip.id(), owner); return null;
            }
            if (plan.publicId().equals(trip.primaryPlanId())) {
                if (r.replacementPrimaryPlanId() == null) throw validation("replacementPrimaryPlanId", "Choose another existing plan as primary.");
                replacement = ownedPlan(trip, r.replacementPrimaryPlanId().toString());
                if (replacement.id() == plan.id()) throw validation("replacementPrimaryPlanId", "Choose a different plan.");
            }
        }
        if (action.equals("copy") || action.equals("primary")) {
            if (!trips.advanceVersionForDraft(trip.id(), owner, r.expectedVersion(), plan.id(), r.expectedPlanVersion())) throw parentConflict(owner, trip.publicId());
        } else advancePlan(owner, trip, plan, r.expectedVersion(), r.expectedPlanVersion());
        switch(action) {
            case "name" -> trips.renameOption(plan.id(), name);
            case "copy" -> trips.insertPlan(trip.id(), UUID.randomUUID(), name, plan.startDate(), plan.endDate(), plan.travelerCount(), plan.travelerAges(), plan.selections());
            case "primary" -> trips.setPrimary(trip.id(), plan.id());
            case "delete" -> { if (replacement != null) trips.setPrimary(trip.id(), replacement.id()); trips.deletePlanned(trip.id(), plan.id()); }
            default -> throw new IllegalArgumentException("Unknown plan action");
        }
        return response(trips.findByPublicIdAndOwnerUserId(trip.publicId(), owner).orElseThrow());
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
        trips.lockOwnerForNaming(ownerUserId);
        requireUniqueName(ownerUserId, name);
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
        trips.lockOwnerForNaming(ownerUserId);
        if (!trip.label().equalsIgnoreCase(name)) requireUniqueName(ownerUserId, name);
        if (!trips.advanceVersion(trip.id(), ownerUserId, request.expectedVersion())) throw parentConflict(ownerUserId, trip.publicId());
        trips.rename(trip.id(), name);
        return response(trips.findByPublicIdAndOwnerUserId(trip.publicId(), ownerUserId).orElseThrow());
    }

    @Transactional
    public TripResponse changeWorkingDates(long owner, String id, TripRequests.WorkingDates r) {
        Trip trip = ownedTrip(owner, id); TripPlan p = trip.primary();
        return savePlan(owner, id, p.publicId().toString(), new TripRequests.PlanSave(r.expectedVersion(), r.expectedDraftVersion(), r.startDate(), r.endDate(), p.travelerCount(), p.travelerAges(), null));
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
        Instant now = clock.instant();
        LocalDate today = LocalDate.ofInstant(now, ClockConfiguration.PDX_ZONE);
        List<Trip> allTrips = trips.findAllByOwnerUserId(ownerUserId);
        List<TripProfileSummary> upcoming = new ArrayList<>();
        List<TripProfileSummary> past = new ArrayList<>();

        for (Trip trip : allTrips) {
            boolean pastTrip = today.isAfter(trip.endDate());
            boolean inProgress = "ACTIVE".equals(trip.status())
                    && !today.isBefore(trip.startDate()) && !pastTrip;
            String temporalStatus = pastTrip ? "PAST" : "UPCOMING";

            boolean expired = !now.isBefore(trip.drafts().get(0).startDate()
                    .atStartOfDay(ClockConfiguration.PDX_ZONE).toInstant());
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
                        expired, trip.primary().name(), draft.startDate(), draft.endDate(),
                        bookingRepository.isPlannedItineraryBooked(trip.primary().id())));
            }
            for (PlannedItinerary planned : trip.planned()) {
                boolean optionExpired = !now.isBefore(planned.startDate()
                        .atStartOfDay(ClockConfiguration.PDX_ZONE).toInstant());
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
                    List.copyOf(alternatives), trip.label(), inProgress);

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
    public TripResponse replaceSharedDetails(long owner, String id, TripRequests.SharedDetailsUpdate r) {
        Trip trip = ownedTrip(owner, id); requireActiveTrip(trip); TripPlan primary = trip.primary();
        Destination destination = trips.findSupportedDestination(required(r.destinationKey(), "destinationKey")).orElseThrow(() -> validation("destinationKey", "Choose a supported destination."));
        boolean destinationChanged = destination.id() != trip.destination().id();
        if (destinationChanged && (trip.plans().size() > 1 || trips.hasBookingHistory(trip.id()))) throw new ApiException(409, "IMMUTABLE_TRIP_PARTY", "Destination cannot change while alternatives or booking history exist.");
        Long budget = validateBudget(r.budgetCents());
        long expectedChild = r.expectedDraftVersion() == null ? primary.version() : r.expectedDraftVersion();
        if (trip.version() != r.expectedVersion() || primary.version() != expectedChild) throw parentConflict(owner, trip.publicId());
        if (!destinationChanged && java.util.Objects.equals(budget, trip.budgetCents()) && java.util.Objects.equals(r.startDate(), primary.startDate()) && java.util.Objects.equals(r.endDate(), primary.endDate()) && java.util.Objects.equals(r.travelerCount(), primary.travelerCount()) && java.util.Objects.equals(r.travelerAges(), primary.travelerAges())) return response(trip);
        // Settings and primary save share one transaction and the same aggregate CAS.
        trips.updateTripSettings(trip.id(), destination, budget);
        return savePlanInternal(owner, id, primary.publicId().toString(), new TripRequests.PlanSave(r.expectedVersion(), r.expectedDraftVersion() == null ? primary.version() : r.expectedDraftVersion(), r.startDate(), r.endDate(), r.travelerCount(), r.travelerAges(), null), destinationChanged);
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
        trips.insertPlan(trip.id(), UUID.randomUUID(), name, working.startDate(), working.endDate(), trip.primary().travelerCount(), trip.primary().travelerAges(), snapshot);
        return response(trips.findByPublicIdAndOwnerUserId(trip.publicId(), ownerUserId).orElseThrow());
    }

    @Transactional
    public TripResponse updateOption(long ownerUserId, String tripId, String optionId, TripRequests.OptionUpdate request) {
        ownedPlan(ownedTrip(ownerUserId, tripId), optionId);
        throw new ApiException(409, "PLAN_WORKFLOW_RETIRED", "Edit the selected plan directly through the plans API.");
    }

    @Transactional
    public TripResponse renameOption(long owner, String id, String optionId, TripRequests.OptionRename r) {
        return planAction(owner, id, optionId, new TripRequests.PlanAction(r.expectedVersion(), r.expectedOptionVersion(), r.name(), null, null, null, null), "name");
    }

    @Transactional
    public TripResponse loadOption(long ownerUserId, String tripId, String optionId, TripRequests.OptionLoad request) {
        ownedPlan(ownedTrip(ownerUserId, tripId), optionId);
        throw new ApiException(409, "PLAN_WORKFLOW_RETIRED", "Edit the selected plan directly through the plans API.");
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
            PlannedItinerary planned = sourceTrip.plans().stream().map(TripPlan::asPlanned)
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

        trips.lockOwnerForNaming(ownerUserId);
        String label = request.name() == null
                ? suggestName(ownerUserId, destination.name(), startDate, endDate)
                : validateName(request.name());
        requireUniqueName(ownerUserId, label);
        UUID newTripPublicId = UUID.randomUUID();
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

    private TripDraft ownedDraft(Trip trip, String draftId) { return ownedPlan(trip, draftId).asDraft(); }

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
        TripDraft draft = ownedDraft(current, draftId.toString());
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

    private void requireUniqueName(long ownerUserId, String name) {
        if (trips.findNamesByOwnerUserId(ownerUserId).stream().anyMatch(existing -> existing.equalsIgnoreCase(name))) {
            throw new ApiException(409, "DUPLICATE_TRIP_NAME", "A Trip with this name already exists. Choose another name.",
                    Map.of("name", "A Trip with this name already exists."));
        }
    }

    private String suggestName(long ownerUserId, String city, LocalDate startDate, LocalDate endDate) {
        String base = label(city, startDate, endDate);
        List<String> names = trips.findNamesByOwnerUserId(ownerUserId);
        if (names.stream().noneMatch(name -> name.equalsIgnoreCase(base))) return base;
        for (int index = 1; ; index++) {
            int value = index;
            StringBuilder suffix = new StringBuilder();
            while (value > 0) {
                value--;
                suffix.insert(0, (char) ('A' + value % 26));
                value /= 26;
            }
            String candidate = base + " - " + suffix;
            if (names.stream().noneMatch(name -> name.equalsIgnoreCase(candidate))) return candidate;
        }
    }

    private String updatedGeneratedName(long ownerUserId, Trip trip, Destination destination,
            LocalDate startDate, LocalDate endDate) {
        String oldBase = trip.destination().name() + " - " + trip.startDate() + " to " + trip.endDate();
        String base = label(trip.destination().name(), trip.startDate(), trip.endDate());
        if (!trip.label().equals(oldBase) && !trip.label().matches(java.util.regex.Pattern.quote(oldBase) + " - [A-Z]+")
                && !trip.label().equals(base) && !trip.label().matches(java.util.regex.Pattern.quote(base) + " - [A-Z]+")) {
            return trip.label();
        }
        trips.lockOwnerForNaming(ownerUserId);
        return suggestName(ownerUserId, destination.name(), startDate, endDate);
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

    static String label(String destinationName, LocalDate startDate, LocalDate endDate) {
        var formatter = java.time.format.DateTimeFormatter.ofPattern("MMM d", java.util.Locale.US);
        String first = startDate.format(formatter);
        String last = endDate.format(formatter);
        String dates;
        if (startDate.getYear() != endDate.getYear()) {
            dates = first + ", " + startDate.getYear() + " – " + last + ", " + endDate.getYear();
        } else if (startDate.getMonth() != endDate.getMonth()) {
            dates = first + " – " + last + ", " + endDate.getYear();
        } else if (startDate.equals(endDate)) {
            dates = first + ", " + startDate.getYear();
        } else {
            dates = first + "–" + endDate.getDayOfMonth() + ", " + startDate.getYear();
        }
        return destinationName + " — " + dates;
    }

    public TripResponse toResponse(Trip trip) {
        return response(trip, null);
    }

    public BookingResponse toBookingResponse(BookingRecord record, Trip trip) {
        DraftSelections selections = bookingRepository.loadBookingSelections(record.id());
        ItineraryTallyResponse tally = tallyEngine.calculateTally(selections, record.purchasedTravelerCount(), record.purchasedBudgetCents());
        DraftSelectionResponse selectionResponse = selectionResponse(selections);
        UUID plannedPublicId = record.plannedItineraryId() != null
                ? trip.plans().stream()
                        .filter(p -> p.id() == record.plannedItineraryId().longValue())
                        .map(TripPlan::publicId)
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
                tally, record.purchasedStartDate(), record.purchasedEndDate(), record.purchasedTravelerCount(), record.purchasedTravelerAges()
        );
    }

    private TripResponse response(Trip trip) {
        return response(trip, null);
    }

    private int airfareTravelerCount(Trip trip, TripPlan plan) {
        for (BookingRecord b : bookingRepository.findBookingRecordsByTripId(trip.id())) {
            if (b.plannedItineraryId() != null && b.plannedItineraryId() == plan.id() && bookingRepository.loadBookingSelections(b.id()).airfare() != null) return b.purchasedTravelerCount();
        }
        return plan.travelerCount();
    }
    private ItineraryTallyResponse planTally(Trip trip, TripPlan plan) {
        return tallyEngine.calculateTally(plan.selections(), airfareTravelerCount(trip, plan), trip.budgetCents());
    }
    private TripResponse response(Trip trip, RevisionSummaryResponse revisionSummary) {
        List<PlanResponse> plans = trip.plans().stream().map(p -> {
            DraftSelections locked = purchased(trip, p); List<String> locks = new ArrayList<>();
            if (locked.airfare() != null) locks.add("airfare"); if (locked.stay() != null) locks.add("stay"); if (locked.rental() != null) locks.add("rental");
            BookingResponse purchase = bookingRepository.findBookingRecordsByTripId(trip.id()).stream()
                .filter(b -> b.plannedItineraryId() != null && b.plannedItineraryId() == p.id()).findFirst().map(b -> toBookingResponse(b, trip)).orElse(null);
            return new PlanResponse(p.publicId(), p.name(), p.startDate(), p.endDate(), p.travelerCount(), p.travelerAges(), p.version(), selectionResponse(p.selections()), planTally(trip, p), p.publicId().equals(trip.primaryPlanId()), purchase != null, List.copyOf(locks), purchase);
        }).toList();
        TripPlan primary = trip.primary();
        DraftResponse working = new DraftResponse(primary.publicId(), primary.version(), selectionResponse(primary.selections()), planTally(trip, primary), primary.startDate(), primary.endDate());
        List<DraftResponse> drafts = List.of(working);
        List<PlannedResponse> planned = trip.plans().stream().filter(p -> !p.publicId().equals(trip.primaryPlanId()))
            .map(p -> new PlannedResponse(p.publicId(), selectionResponse(p.selections()), planTally(trip, p), p.name(), p.startDate(), p.endDate(), p.version(), bookingRepository.isPlannedItineraryBooked(p.id()))).toList();
        List<AlternativeResponse> alternatives = trip.plans().stream().map(p -> new AlternativeResponse(p.publicId(), "PLANNED", p.version(), selectionResponse(p.selections()), planTally(trip, p), p.name(), p.startDate(), p.endDate())).toList();
        BookingResponse booking = bookingRepository.findPrimaryBookingRecordByTripId(trip.id()).map(b -> toBookingResponse(b, trip)).orElse(null);
        return new TripResponse(trip.publicId(), trip.destination().key(), trip.destination().name(), "PDX", primary.startDate(), primary.endDate(), primary.travelerCount(), primary.travelerAges(), trip.budgetCents(), trip.label(), trip.status(), trip.version(), drafts, planned, alternatives, revisionSummary, planTally(trip, primary), booking, trip.label(), working, planned, plans, trip.primaryPlanId());
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
        trip = trip.withPlanContext(ownedPlan(trip, draftId));
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
        trip = trip.withPlanContext(ownedPlan(trip, working.publicId().toString()));
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
        TripPlan plan = ownedPlan(trip, draftId); requireUnlocked(trip, plan, "airfare"); trip = trip.withPlanContext(plan);

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
        TripPlan plan = ownedPlan(trip, draftId); requireUnlocked(trip, plan, "airfare"); trip = trip.withPlanContext(plan);

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
                draft != null ? airfareTravelerCount(trip, ownedPlan(trip, draft.publicId().toString())) : trip.travelerCount()
        );
        TripDraft working = draft != null ? draft : trip.drafts().get(0);
        trip = trip.withPlanContext(ownedPlan(trip, working.publicId().toString()));

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
        TripPlan plan = ownedPlan(trip, draftId); requireUnlocked(trip, plan, "stay"); trip = trip.withPlanContext(plan);

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
        TripPlan plan = ownedPlan(trip, draftId); requireUnlocked(trip, plan, "stay"); trip = trip.withPlanContext(plan);

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
                draft != null ? airfareTravelerCount(trip, ownedPlan(trip, draft.publicId().toString())) : trip.travelerCount()
        );
        TripDraft working = draft != null ? draft : trip.drafts().get(0);
        trip = trip.withPlanContext(ownedPlan(trip, working.publicId().toString()));

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
        TripPlan plan = ownedPlan(trip, draftId); requireUnlocked(trip, plan, "rental"); trip = trip.withPlanContext(plan);

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
        TripPlan plan = ownedPlan(trip, draftId); requireUnlocked(trip, plan, "rental"); trip = trip.withPlanContext(plan);

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
