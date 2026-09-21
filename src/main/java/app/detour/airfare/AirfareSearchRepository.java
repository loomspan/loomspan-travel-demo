package app.detour.airfare;

import app.detour.airfare.AirfareSearchResponses.FlightLegResponse;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface AirfareSearchRepository {
    List<FlightLegResponse> findOutboundLegs(long destinationId, LocalDate serviceDate, int minSeats);

    List<FlightLegResponse> findReturnLegs(long destinationId, LocalDate serviceDate, int minSeats);

    Optional<FlightLegResponse> findLegById(long flightInstanceId);

    Optional<String> findAirportIataCodeForDestination(long destinationId);
}
