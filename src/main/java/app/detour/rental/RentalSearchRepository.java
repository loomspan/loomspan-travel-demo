package app.detour.rental;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

public interface RentalSearchRepository {
    List<RentalCandidate> findAvailableUnits(long destinationId, OffsetDateTime pickupAt, OffsetDateTime returnAt);

    Optional<RentalCandidate> findUnitById(long rentalUnitId);

    boolean isUnitAvailable(long rentalUnitId, OffsetDateTime pickupAt, OffsetDateTime returnAt);

    Optional<String> findDestinationAirportTimeZone(long destinationId);
}
