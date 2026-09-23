package app.detour.trip;

import java.time.OffsetDateTime;

public record AirfareComponentResponse(long outboundFlightInstanceId, long returnFlightInstanceId, String outboundDescription,
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
    public AirfareComponentResponse(long outboundFlightInstanceId, long returnFlightInstanceId, String outboundDescription,
            String returnDescription, long outboundBaseFareCents, long outboundTaxCents, long outboundFeeCents,
            long returnBaseFareCents, long returnTaxCents, long returnFeeCents) {
        this(outboundFlightInstanceId, returnFlightInstanceId, outboundDescription, returnDescription,
                outboundBaseFareCents, outboundTaxCents, outboundFeeCents,
                returnBaseFareCents, returnTaxCents, returnFeeCents,
                null, null, null, null, null, null, null, null, null, null,
                null, null, null, null, null, null, null, null, null, null, null);
    }
}
