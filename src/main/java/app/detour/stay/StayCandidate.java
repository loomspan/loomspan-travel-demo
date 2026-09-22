package app.detour.stay;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public record StayCandidate(
        long propertyId,
        String propertyCatalogKey,
        String propertyName,
        String propertyCategory,
        String locationDescription,
        BigDecimal guestRating,
        int distanceToCityCenterMeters,
        BigDecimal latitude,
        BigDecimal longitude,
        long destinationId,
        long unitId,
        String unitCatalogKey,
        String unitName,
        String unitKind,
        int guestCapacity,
        int inventoryCapacity,
        List<CandidateNight> nights
) {
    public StayCandidate {
        nights = nights == null ? List.of() : List.copyOf(nights);
    }

    public record CandidateNight(
            LocalDate date,
            int availableInventory,
            long basePriceCents,
            long taxCents,
            long feeCents
    ) { }
}
