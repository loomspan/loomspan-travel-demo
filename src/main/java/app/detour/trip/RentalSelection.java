package app.detour.trip;

import java.time.OffsetDateTime;

public record RentalSelection(long rentalUnitId, OffsetDateTime pickupAt, OffsetDateTime returnAt, String locationName,
        String vehicleClassName, String unitIdentifier, long dailyBasePriceCents, long dailyTaxCents,
        long dailyFeeCents, String vehicleCategory) {
    public RentalSelection(long rentalUnitId, OffsetDateTime pickupAt, OffsetDateTime returnAt, String locationName,
            String vehicleClassName, String unitIdentifier, long dailyBasePriceCents, long dailyTaxCents,
            long dailyFeeCents) {
        this(rentalUnitId, pickupAt, returnAt, locationName, vehicleClassName, unitIdentifier,
                dailyBasePriceCents, dailyTaxCents, dailyFeeCents, null);
    }
}
