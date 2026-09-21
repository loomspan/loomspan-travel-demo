package app.detour.trip;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

/** Deliberately component-level facts only: no canonical total or availability decision. */
public record AlternativeResponse(UUID id, String lifecycle, Long version, DraftSelectionResponse selections) { }
record DraftSelectionResponse(AirfareComponentResponse airfare, StayComponentResponse stay, RentalComponentResponse rental) { }
record PlannedResponse(UUID id, DraftSelectionResponse selections) { }
record AirfareComponentResponse(long outboundFlightInstanceId, long returnFlightInstanceId, String outboundDescription,
        String returnDescription, long outboundBaseFareCents, long outboundTaxCents, long outboundFeeCents,
        long returnBaseFareCents, long returnTaxCents, long returnFeeCents) { }
record StayComponentResponse(long accommodationUnitId, int unitCount, String propertyName, String unitName, List<StayNightResponse> nights) { }
record StayNightResponse(LocalDate date, long basePriceCents, long taxCents, long feeCents) { }
record RentalComponentResponse(long rentalUnitId, OffsetDateTime pickupAt, OffsetDateTime returnAt, String locationName,
        String vehicleClassName, String unitIdentifier, long dailyBasePriceCents, long dailyTaxCents, long dailyFeeCents) { }
