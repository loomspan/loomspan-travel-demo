package app.detour.rental;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

public final class RentalSearchResponses {
    private RentalSearchResponses() {
    }

    public record RentalPricingResponse(
            int billingCycles,
            long dailyBasePriceCents,
            long dailyTaxCents,
            long dailyFeeCents,
            long dailyTotalPriceCents,
            long totalBasePriceCents,
            long totalTaxCents,
            long totalFeeCents,
            long totalPriceCents
    ) { }

    public record RentalOptionResponse(
            long rentalUnitId,
            String unitCatalogKey,
            String unitIdentifier,
            long vehicleClassId,
            String vehicleClassCatalogKey,
            String vehicleClassName,
            String vehicleCategory,
            long locationId,
            String locationCatalogKey,
            String locationName,
            String airportIataCode,
            RentalPricingResponse pricing,
            Boolean fitsBudget
    ) { }

    public record RentalSearchResponse(
            UUID tripId,
            UUID draftId,
            String destinationKey,
            OffsetDateTime pickupAt,
            OffsetDateTime returnAt,
            int billingCycles,
            boolean driverEligible,
            boolean selectionDisabled,
            String disabledReason,
            String explanation,
            Long availableTripBudgetCents,
            RentalSort sort,
            List<RentalOptionResponse> options
    ) {
        public RentalSearchResponse {
            options = options == null ? List.of() : List.copyOf(options);
        }
    }
}
