package app.detour.airfare;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

public final class AirfareSearchResponses {
    private AirfareSearchResponses() {
    }

    public record LayoverResponse(
            String airportCode,
            String airportName,
            long durationMinutes
    ) { }

    public record FlightLegResponse(
            long flightInstanceId,
            String catalogKey,
            String carrier,
            String flightNumber,
            int stopCount,
            String originAirportCode,
            String originAirportName,
            String destinationAirportCode,
            String destinationAirportName,
            OffsetDateTime departureTime,
            OffsetDateTime arrivalTime,
            String departureTimeZone,
            String arrivalTimeZone,
            long durationMinutes,
            int availableSeats,
            long baseFareCents,
            long taxCents,
            long feeCents,
            long totalFareCents,
            LayoverResponse layover
    ) { }

    public record PartyPricingResponse(
            int travelerCount,
            long perTravelerBaseFareCents,
            long perTravelerTaxCents,
            long perTravelerFeeCents,
            long perTravelerTotalCents,
            long partyBaseFareCents,
            long partyTaxCents,
            long partyFeeCents,
            long partyTotalPriceCents
    ) { }

    public record FlightCombinationResponse(
            String combinationKey,
            FlightLegResponse outbound,
            FlightLegResponse returnFlight,
            long totalDurationMinutes,
            boolean direct,
            PartyPricingResponse pricing
    ) { }

    public record AirfareSearchResponse(
            UUID tripId,
            UUID draftId,
            String destinationKey,
            String originAirportCode,
            String destinationAirportCode,
            LocalDate startDate,
            LocalDate endDate,
            int travelerCount,
            boolean directOnly,
            AirfareSort sort,
            List<FlightCombinationResponse> options
    ) {
        public AirfareSearchResponse {
            options = options == null ? List.of() : List.copyOf(options);
        }
    }
}
