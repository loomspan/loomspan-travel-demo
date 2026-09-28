package app.detour.trip;

import app.detour.airfare.AirfareSearchResponses.AirfareSearchResponse;
import app.detour.stay.StaySearchResponses.StaySearchResponse;
import java.time.LocalDate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/public")
class PublicSearchController {
    private final TripService trips;

    PublicSearchController(TripService trips) { this.trips = trips; }

    @GetMapping("/airfare")
    AirfareSearchResponse airfare(@RequestParam String destinationKey, @RequestParam LocalDate startDate,
            @RequestParam LocalDate endDate, @RequestParam int travelerCount,
            @RequestParam(defaultValue = "false") boolean directOnly,
            @RequestParam(defaultValue = "DEFAULT") String sort) {
        return trips.searchPublicAirfare(destinationKey, startDate, endDate, travelerCount, directOnly, sort);
    }

    @GetMapping("/stays")
    StaySearchResponse stays(@RequestParam String destinationKey, @RequestParam LocalDate startDate,
            @RequestParam LocalDate endDate, @RequestParam int travelerCount,
            @RequestParam(required = false) Long budgetCents,
            @RequestParam(defaultValue = "HOTEL") String type,
            @RequestParam(defaultValue = "DEFAULT") String sort) {
        return trips.searchPublicStays(destinationKey, startDate, endDate, travelerCount, budgetCents, type, sort);
    }
}
