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

    void lockOwnerForNaming(long ownerUserId);

    List<String> findNamesByOwnerUserId(long ownerUserId);

    void deleteTrip(long tripId, long ownerUserId);

    boolean cancelTrip(long tripId, long ownerUserId, long expectedVersion);

    boolean hasBookingHistory(long tripId);

    int activeBookingCount(long tripId);

    Optional<String> findPrimaryBookingReference(long tripId);

    boolean advanceVersion(long tripId, long ownerUserId, long expectedVersion);

    void rename(long tripId, String name);

    void updateWorkingDates(long tripId, long draftId, LocalDate startDate, LocalDate endDate);

    boolean advanceVersionForOption(long tripId, long ownerUserId, long expectedVersion,
            long optionId, long expectedOptionVersion);

    void renameOption(long optionId, String name);

    void updateOptionDates(long optionId, LocalDate startDate, LocalDate endDate);

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

    void insertPlanned(long tripId, UUID publicId, String name, LocalDate startDate, LocalDate endDate,
            DraftSelections selections);

    void replacePlannedSnapshots(long plannedId, String name, LocalDate startDate, LocalDate endDate,
            DraftSelections selections);

    void replaceWorkingSelections(long tripId, long draftId, LocalDate startDate, LocalDate endDate,
            DraftSelections selections);

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

    /** Resolve current catalog facts for the dated Working plan or target option. */
    DraftSelections resolveSelectionsForOption(Trip trip, DraftSelections selections,
            LocalDate startDate, LocalDate endDate);

    record DraftCreationSpec(UUID draftPublicId, DraftSelections selections) { }
    record AirfareRevalidation(boolean valid, AirfareSelection retained, ComponentRemovalResponse removal, ComponentAdjustmentResponse adjustment) { }
    record StayRevalidation(boolean valid, int newUnitCount, ComponentRemovalResponse removal, ComponentAdjustmentResponse adjustment) { }
    record RentalRevalidation(boolean valid, RentalSelection retained, ComponentRemovalResponse removal, ComponentAdjustmentResponse adjustment) { }
}
