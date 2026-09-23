package app.detour.trip;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface TripRepository {
    Optional<Destination> findSupportedDestination(String key);

    void createAggregate(long ownerUserId, UUID tripPublicId, Destination destination, LocalDate startDate,
            LocalDate endDate, int travelerCount, List<Integer> travelerAges, Long budgetCents, String label,
            UUID draftPublicId);

    Optional<Trip> findByPublicIdAndOwnerUserId(UUID publicId, long ownerUserId);

    List<Trip> findAllByOwnerUserId(long ownerUserId);

    void deleteTrip(long tripId, long ownerUserId);

    boolean hasBookingHistory(long tripId);

    int activeBookingCount(long tripId);

    boolean advanceVersion(long tripId, long ownerUserId, long expectedVersion);

    boolean advanceVersionForDraft(long tripId, long ownerUserId, long expectedVersion, long draftId,
            long expectedDraftVersion);

    boolean advanceVersionForDraftMutation(long tripId, long ownerUserId, long expectedVersion, long draftId,
            long expectedDraftVersion);

    void saveDraftAirfareSelection(long draftId, long outboundFlightInstanceId, long returnFlightInstanceId);

    void replaceSharedDetails(long tripId, Destination destination, LocalDate startDate, LocalDate endDate,
            int travelerCount, List<Integer> travelerAges, Long budgetCents, String label);

    void insertDraft(long tripId, UUID publicId);

    void deleteDraft(long tripId, long draftId);

    void insertDraftCopy(long tripId, UUID publicId, DraftSelections selections);

    void insertPlanned(long tripId, UUID publicId, DraftSelections selections);

    void deletePlanned(long tripId, long plannedId);

    void createAggregateWithDrafts(long ownerUserId, UUID tripPublicId, Destination destination, LocalDate startDate,
            LocalDate endDate, int travelerCount, List<Integer> travelerAges, Long budgetCents, String label,
            List<DraftCreationSpec> drafts);

    void deleteDraftAirfareSelection(long draftId);

    void saveDraftStaySelection(long draftId, long accommodationUnitId, int unitCount);

    void deleteDraftStaySelection(long draftId);

    void saveDraftRentalSelection(long draftId, long rentalUnitId, OffsetDateTime pickupAt, OffsetDateTime returnAt);

    void deleteDraftRentalSelection(long draftId);

    void updateDraftStayUnitCount(long draftId, int unitCount);

    AirfareRevalidation revalidateAirfare(long destinationId, LocalDate startDate, LocalDate endDate,
            int newTravelerCount, int oldTravelerCount, AirfareSelection selection, UUID draftPublicId);

    StayRevalidation revalidateStay(long destinationId, LocalDate startDate, LocalDate endDate,
            LocalDate oldStartDate, LocalDate oldEndDate, int newTravelerCount, int oldTravelerCount,
            StaySelection selection, UUID draftPublicId);

    RentalRevalidation revalidateRental(long destinationId, LocalDate startDate, LocalDate endDate,
            List<Integer> ages, RentalSelection selection, UUID draftPublicId);

    /** Returns only components structurally consistent with the supplied Trip. */
    DraftSelections resolveSelectionsForPromotion(Trip trip, TripDraft draft);

    record DraftCreationSpec(UUID draftPublicId, DraftSelections selections) { }
    record AirfareRevalidation(boolean valid, AirfareSelection retained, ComponentRemovalResponse removal, ComponentAdjustmentResponse adjustment) { }
    record StayRevalidation(boolean valid, int newUnitCount, ComponentRemovalResponse removal, ComponentAdjustmentResponse adjustment) { }
    record RentalRevalidation(boolean valid, RentalSelection retained, ComponentRemovalResponse removal, ComponentAdjustmentResponse adjustment) { }
}
