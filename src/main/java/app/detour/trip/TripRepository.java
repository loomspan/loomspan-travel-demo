package app.detour.trip;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

interface TripRepository {
    Optional<Destination> findSupportedDestination(String key);

    void createAggregate(long ownerUserId, UUID tripPublicId, Destination destination, LocalDate startDate,
            LocalDate endDate, int travelerCount, List<Integer> travelerAges, Long budgetCents, String label,
            UUID draftPublicId);

    Optional<Trip> findByPublicIdAndOwnerUserId(UUID publicId, long ownerUserId);

    boolean advanceVersion(long tripId, long ownerUserId, long expectedVersion);

    boolean advanceVersionForDraft(long tripId, long ownerUserId, long expectedVersion, long draftId,
            long expectedDraftVersion);

    void replaceSharedDetails(long tripId, Destination destination, LocalDate startDate, LocalDate endDate,
            int travelerCount, List<Integer> travelerAges, Long budgetCents, String label);

    void insertDraft(long tripId, UUID publicId);

    void deleteDraft(long tripId, long draftId);

    void insertDraftCopy(long tripId, UUID publicId, DraftSelections selections);

    void insertPlanned(long tripId, UUID publicId, DraftSelections selections);

    void deletePlanned(long tripId, long plannedId);

    /** Returns only components structurally consistent with the supplied Trip. */
    DraftSelections resolveSelectionsForPromotion(Trip trip, TripDraft draft);
}
