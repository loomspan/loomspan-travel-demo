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
        long returnBaseFareCents, long returnTaxCents, long returnFeeCents) { }

record StaySelection(long accommodationUnitId, int unitCount, String propertyName, String unitName,
        List<StayNight> nights) { }

record StayNight(LocalDate date, long basePriceCents, long taxCents, long feeCents) { }

record RentalSelection(long rentalUnitId, OffsetDateTime pickupAt, OffsetDateTime returnAt, String locationName,
        String vehicleClassName, String unitIdentifier, long dailyBasePriceCents, long dailyTaxCents,
        long dailyFeeCents) { }

record PlannedItinerary(long id, UUID publicId, DraftSelections selections) implements TripAlternative {
    @Override public String lifecycle() { return "PLANNED"; }
}
