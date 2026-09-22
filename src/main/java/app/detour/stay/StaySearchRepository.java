package app.detour.stay;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface StaySearchRepository {
    List<StayCandidate> findCandidates(long destinationId, AccommodationType type, LocalDate startDate, LocalDate endDate);

    Optional<StayCandidate> findCandidateById(long accommodationUnitId, LocalDate startDate, LocalDate endDate);

    Optional<Long> findRentalDailyTotal(long rentalUnitId);
}
