package app.detour.trip;

import java.time.OffsetDateTime;

public record RentalComponentResponse(long rentalUnitId, OffsetDateTime pickupAt, OffsetDateTime returnAt, String locationName,
        String vehicleClassName, String unitIdentifier, long dailyBasePriceCents, long dailyTaxCents,
        long dailyFeeCents, String vehicleCategory) {
    public RentalComponentResponse(long rentalUnitId, OffsetDateTime pickupAt, OffsetDateTime returnAt, String locationName,
            String vehicleClassName, String unitIdentifier, long dailyBasePriceCents, long dailyTaxCents,
            long dailyFeeCents) {
        this(rentalUnitId, pickupAt, returnAt, locationName, vehicleClassName, unitIdentifier,
                dailyBasePriceCents, dailyTaxCents, dailyFeeCents, null);
    }
}
