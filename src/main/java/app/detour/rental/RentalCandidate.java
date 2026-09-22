package app.detour.rental;

public record RentalCandidate(
        long unitId,
        String unitCatalogKey,
        String unitIdentifier,
        long vehicleClassId,
        String vehicleClassCatalogKey,
        String vehicleClassName,
        String vehicleCategory,
        long dailyBasePriceCents,
        long dailyTaxCents,
        long dailyFeeCents,
        long locationId,
        String locationCatalogKey,
        String locationName,
        long destinationId,
        long airportId,
        String airportIataCode
) { }
