package app.detour.stay;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public final class StaySearchResponses {
    private StaySearchResponses() {
    }

    public record StayNightPricingResponse(
            LocalDate date,
            long basePriceCents,
            long taxCents,
            long feeCents,
            long totalCents,
            int availableInventory
    ) { }

    public record StayPricingResponse(
            int requiredRooms,
            int nightCount,
            long perRoomBasePriceCents,
            long perRoomTaxCents,
            long perRoomFeeCents,
            long perRoomTotalPriceCents,
            long totalBasePriceCents,
            long totalTaxCents,
            long totalFeeCents,
            long totalPriceCents,
            List<StayNightPricingResponse> nights
    ) {
        public StayPricingResponse {
            nights = nights == null ? List.of() : List.copyOf(nights);
        }
    }

    public record StayOptionResponse(
            long accommodationUnitId,
            long propertyId,
            String propertyCatalogKey,
            String unitCatalogKey,
            String propertyName,
            String unitName,
            String propertyCategory,
            String unitKind,
            String locationDescription,
            BigDecimal guestRating,
            int distanceToCityCenterMeters,
            BigDecimal latitude,
            BigDecimal longitude,
            int guestCapacity,
            int inventoryCapacity,
            StayPricingResponse pricing,
            Boolean fitsBudget
    ) { }

    public record StaySearchResponse(
            UUID tripId,
            UUID draftId,
            String destinationKey,
            AccommodationType accommodationType,
            LocalDate startDate,
            LocalDate endDate,
            int travelerCount,
            Long availableTripBudgetCents,
            StaySort sort,
            List<StayOptionResponse> options
    ) {
        public StaySearchResponse {
            options = options == null ? List.of() : List.copyOf(options);
        }
    }
}
