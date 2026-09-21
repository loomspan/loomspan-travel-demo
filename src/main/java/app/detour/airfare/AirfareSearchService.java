package app.detour.airfare;

import app.detour.airfare.AirfareSearchResponses.AirfareSearchResponse;
import app.detour.airfare.AirfareSearchResponses.FlightCombinationResponse;
import app.detour.airfare.AirfareSearchResponses.FlightLegResponse;
import app.detour.airfare.AirfareSearchResponses.PartyPricingResponse;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;

@Service
public class AirfareSearchService {
    private final AirfareSearchRepository airfareSearchRepository;

    public AirfareSearchService(AirfareSearchRepository airfareSearchRepository) {
        this.airfareSearchRepository = airfareSearchRepository;
    }

    public AirfareSearchResponse search(
            long destinationId,
            String destinationKey,
            LocalDate startDate,
            LocalDate endDate,
            int travelerCount,
            UUID tripId,
            UUID draftId,
            boolean directOnly,
            AirfareSort sort) {

        AirfareSort effectiveSort = (sort == null) ? AirfareSort.DEFAULT : sort;
        List<FlightLegResponse> outboundLegs = airfareSearchRepository.findOutboundLegs(destinationId, startDate, travelerCount);
        List<FlightLegResponse> returnLegs = airfareSearchRepository.findReturnLegs(destinationId, endDate, travelerCount);

        if (directOnly) {
            outboundLegs = outboundLegs.stream().filter(leg -> leg.stopCount() == 0).toList();
            returnLegs = returnLegs.stream().filter(leg -> leg.stopCount() == 0).toList();
        }

        List<FlightCombinationResponse> combinations = new ArrayList<>();
        for (FlightLegResponse outbound : outboundLegs) {
            for (FlightLegResponse returnFlight : returnLegs) {
                String combinationKey = outbound.catalogKey() + "__" + returnFlight.catalogKey();
                boolean direct = outbound.stopCount() == 0 && returnFlight.stopCount() == 0;
                long totalDurationMinutes = outbound.durationMinutes() + returnFlight.durationMinutes();

                long perTravelerBase = outbound.baseFareCents() + returnFlight.baseFareCents();
                long perTravelerTax = outbound.taxCents() + returnFlight.taxCents();
                long perTravelerFee = outbound.feeCents() + returnFlight.feeCents();
                long perTravelerTotal = outbound.totalFareCents() + returnFlight.totalFareCents();

                long partyBase = (long) travelerCount * perTravelerBase;
                long partyTax = (long) travelerCount * perTravelerTax;
                long partyFee = (long) travelerCount * perTravelerFee;
                long partyTotal = (long) travelerCount * perTravelerTotal;

                PartyPricingResponse pricing = new PartyPricingResponse(
                        travelerCount,
                        perTravelerBase,
                        perTravelerTax,
                        perTravelerFee,
                        perTravelerTotal,
                        partyBase,
                        partyTax,
                        partyFee,
                        partyTotal
                );

                combinations.add(new FlightCombinationResponse(
                        combinationKey,
                        outbound,
                        returnFlight,
                        totalDurationMinutes,
                        direct,
                        pricing
                ));
            }
        }

        Comparator<FlightCombinationResponse> comparator = switch (effectiveSort) {
            case DEFAULT -> Comparator
                    .comparing((FlightCombinationResponse c) -> !c.direct())
                    .thenComparingLong(c -> c.pricing().partyTotalPriceCents())
                    .thenComparingLong(FlightCombinationResponse::totalDurationMinutes)
                    .thenComparing(FlightCombinationResponse::combinationKey);
            case LOWEST_PRICE -> Comparator
                    .comparingLong((FlightCombinationResponse c) -> c.pricing().partyTotalPriceCents())
                    .thenComparing(FlightCombinationResponse::combinationKey);
            case SHORTEST_DURATION -> Comparator
                    .comparingLong(FlightCombinationResponse::totalDurationMinutes)
                    .thenComparing(FlightCombinationResponse::combinationKey);
            case EARLIEST_DEPARTURE -> Comparator
                    .comparing((FlightCombinationResponse c) -> c.outbound().departureTime())
                    .thenComparing(FlightCombinationResponse::combinationKey);
            case FEWEST_STOPS -> Comparator
                    .comparingInt((FlightCombinationResponse c) -> c.outbound().stopCount() + c.returnFlight().stopCount())
                    .thenComparing(FlightCombinationResponse::combinationKey);
        };

        combinations.sort(comparator);

        String destinationAirportCode = airfareSearchRepository.findAirportIataCodeForDestination(destinationId)
                .orElseGet(() -> combinations.isEmpty() ? "" : combinations.get(0).outbound().destinationAirportCode());

        return new AirfareSearchResponse(
                tripId,
                draftId,
                destinationKey,
                "PDX",
                destinationAirportCode,
                startDate,
                endDate,
                travelerCount,
                directOnly,
                effectiveSort,
                combinations
        );
    }
}
