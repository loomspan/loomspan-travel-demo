package app.detour.trip;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

/** Immutable alternative source.  Future Booking snapshots can implement the same boundary. */
sealed interface TripAlternative permits TripDraft, PlannedItinerary {
    long id();
    UUID publicId();
    String lifecycle();
}

record DraftSelections(AirfareSelection airfare, StaySelection stay, RentalSelection rental) {
    static final DraftSelections EMPTY = new DraftSelections(null, null, null);
}

record AirfareSelection(long outboundFlightInstanceId, long returnFlightInstanceId, String outboundDescription,
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
    AirfareSelection(long outboundFlightInstanceId, long returnFlightInstanceId, String outboundDescription,
            String returnDescription, long outboundBaseFareCents, long outboundTaxCents, long outboundFeeCents,
            long returnBaseFareCents, long returnTaxCents, long returnFeeCents) {
        this(outboundFlightInstanceId, returnFlightInstanceId, outboundDescription, returnDescription,
                outboundBaseFareCents, outboundTaxCents, outboundFeeCents,
                returnBaseFareCents, returnTaxCents, returnFeeCents,
                null, null, null, null, null, null, null, null, null, null,
                null, null, null, null, null, null, null, null, null, null, null);
    }
}

record StaySelection(long accommodationUnitId, int unitCount, String propertyName, String unitName,
        List<StayNight> nights, String propertyCategory, String locationDescription,
        Integer distanceToCityCenterMeters, Integer guestCapacity, Integer requiredRoomCount) {
    StaySelection(long accommodationUnitId, int unitCount, String propertyName, String unitName,
            List<StayNight> nights) {
        this(accommodationUnitId, unitCount, propertyName, unitName, nights, null, null, null, null, null);
    }
}

record StayNight(LocalDate date, long basePriceCents, long taxCents, long feeCents) { }

record RentalSelection(long rentalUnitId, OffsetDateTime pickupAt, OffsetDateTime returnAt, String locationName,
        String vehicleClassName, String unitIdentifier, long dailyBasePriceCents, long dailyTaxCents,
        long dailyFeeCents, String vehicleCategory) {
    RentalSelection(long rentalUnitId, OffsetDateTime pickupAt, OffsetDateTime returnAt, String locationName,
            String vehicleClassName, String unitIdentifier, long dailyBasePriceCents, long dailyTaxCents,
            long dailyFeeCents) {
        this(rentalUnitId, pickupAt, returnAt, locationName, vehicleClassName, unitIdentifier,
                dailyBasePriceCents, dailyTaxCents, dailyFeeCents, null);
    }
}

record PlannedItinerary(long id, UUID publicId, DraftSelections selections) implements TripAlternative {
    @Override public String lifecycle() { return "PLANNED"; }
}
