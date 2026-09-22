package app.detour.trip;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

/** Deliberately component-level facts only: no canonical total or availability decision. */
public record AlternativeResponse(UUID id, String lifecycle, Long version, DraftSelectionResponse selections, ItineraryTallyResponse tally) {
    public AlternativeResponse(UUID id, String lifecycle, Long version, DraftSelectionResponse selections) {
        this(id, lifecycle, version, selections, null);
    }
}
record DraftSelectionResponse(AirfareComponentResponse airfare, StayComponentResponse stay, RentalComponentResponse rental) { }
record PlannedResponse(UUID id, DraftSelectionResponse selections, ItineraryTallyResponse tally) {
    public PlannedResponse(UUID id, DraftSelectionResponse selections) {
        this(id, selections, null);
    }
}

record AirfareComponentResponse(long outboundFlightInstanceId, long returnFlightInstanceId, String outboundDescription,
        String returnDescription, long outboundBaseFareCents, long outboundTaxCents, long outboundFeeCents,
        long returnBaseFareCents, long returnTaxCents, long returnFeeCents,
        String outboundCarrierName, String outboundFlightNumber, Integer outboundStopCount,
        String outboundLayoverAirportCode, Integer outboundLayoverDurationMinutes,
        OffsetDateTime outboundDepartureTime, OffsetDateTime outboundArrivalTime,
        String outboundDepartureTimeZone, String outboundArrivalTimeZone, Integer outboundDurationMinutes,
        String returnCarrierName, String returnFlightNumber, Integer returnStopCount,
        String returnLayoverAirportCode, Integer returnLayoverDurationMinutes,
        OffsetDateTime returnDepartureTime, OffsetDateTime returnArrivalTime,
        String returnDepartureTimeZone, String returnArrivalTimeZone, Integer returnDurationMinutes,
        Integer totalDurationMinutes) {
    AirfareComponentResponse(long outboundFlightInstanceId, long returnFlightInstanceId, String outboundDescription,
            String returnDescription, long outboundBaseFareCents, long outboundTaxCents, long outboundFeeCents,
            long returnBaseFareCents, long returnTaxCents, long returnFeeCents) {
        this(outboundFlightInstanceId, returnFlightInstanceId, outboundDescription, returnDescription,
                outboundBaseFareCents, outboundTaxCents, outboundFeeCents,
                returnBaseFareCents, returnTaxCents, returnFeeCents,
                null, null, null, null, null, null, null, null, null, null,
                null, null, null, null, null, null, null, null, null, null, null);
    }
}

record StayComponentResponse(long accommodationUnitId, int unitCount, String propertyName, String unitName,
        List<StayNightResponse> nights, String propertyCategory, String locationDescription,
        Integer distanceToCityCenterMeters, Integer guestCapacity, Integer requiredRoomCount) {
    StayComponentResponse(long accommodationUnitId, int unitCount, String propertyName, String unitName,
            List<StayNightResponse> nights) {
        this(accommodationUnitId, unitCount, propertyName, unitName, nights, null, null, null, null, null);
    }
}

record StayNightResponse(LocalDate date, long basePriceCents, long taxCents, long feeCents) { }

record RentalComponentResponse(long rentalUnitId, OffsetDateTime pickupAt, OffsetDateTime returnAt, String locationName,
        String vehicleClassName, String unitIdentifier, long dailyBasePriceCents, long dailyTaxCents,
        long dailyFeeCents, String vehicleCategory) {
    RentalComponentResponse(long rentalUnitId, OffsetDateTime pickupAt, OffsetDateTime returnAt, String locationName,
            String vehicleClassName, String unitIdentifier, long dailyBasePriceCents, long dailyTaxCents,
            long dailyFeeCents) {
        this(rentalUnitId, pickupAt, returnAt, locationName, vehicleClassName, unitIdentifier,
                dailyBasePriceCents, dailyTaxCents, dailyFeeCents, null);
    }
}

record RevisionSummaryResponse(List<ComponentRemovalResponse> removals, List<ComponentAdjustmentResponse> adjustments) {
    public RevisionSummaryResponse {
        removals = removals == null ? List.of() : List.copyOf(removals);
        adjustments = adjustments == null ? List.of() : List.copyOf(adjustments);
    }
}

record ComponentRemovalResponse(UUID draftId, String component, String reason) { }

record ComponentAdjustmentResponse(UUID draftId, String component, String changeType,
        Integer previousUnitCount, Integer newUnitCount, Long previousPriceCents, Long newPriceCents,
        String reason) { }
