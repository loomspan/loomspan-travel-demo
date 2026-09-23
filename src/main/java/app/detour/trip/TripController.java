package app.detour.trip;

import app.detour.airfare.AirfareSearchResponses.AirfareSearchResponse;
import app.detour.booking.BookingResponse;
import app.detour.booking.BookingService;
import app.detour.rental.RentalSearchResponses.RentalSearchResponse;
import app.detour.stay.StaySearchResponses.StaySearchResponse;
import app.detour.api.ApiException;
import app.detour.identity.DetourUserPrincipal;
import java.util.List;
import java.util.Map;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import tools.jackson.databind.JsonNode;

@RestController
@RequestMapping("/api/trips")
public class TripController {
    private final TripService trips;
    private final BookingService bookingService;

    TripController(TripService trips, BookingService bookingService) {
        this.trips = trips;
        this.bookingService = bookingService;
    }

    @PostMapping
    ResponseEntity<TripResponse> create(@AuthenticationPrincipal DetourUserPrincipal principal,
            @RequestBody JsonNode request) {
        return ResponseEntity.status(201).body(trips.create(requirePrincipal(principal).userId(), TripRequests.from(request)));
    }

    @GetMapping
    TripsProfileResponse list(@AuthenticationPrincipal DetourUserPrincipal principal) {
        return trips.tripsProfile(requirePrincipal(principal).userId());
    }

    @GetMapping("/{tripId}")
    TripResponse detail(@AuthenticationPrincipal DetourUserPrincipal principal, @PathVariable String tripId) {
        return trips.detail(requirePrincipal(principal).userId(), tripId);
    }

    @PutMapping("/{tripId}")
    TripResponse replace(@AuthenticationPrincipal DetourUserPrincipal principal, @PathVariable String tripId, @RequestBody JsonNode request) {
        return trips.replaceSharedDetails(requirePrincipal(principal).userId(), tripId, TripRequests.update(request));
    }

    @PostMapping("/{tripId}/duplicate")
    ResponseEntity<TripResponse> duplicateTrip(@AuthenticationPrincipal DetourUserPrincipal principal, @PathVariable String tripId, @RequestBody JsonNode request) {
        return ResponseEntity.status(201).body(trips.duplicateTrip(requirePrincipal(principal).userId(), tripId, TripRequests.revision(request)));
    }

    @PostMapping("/{tripId}/drafts")
    ResponseEntity<TripResponse> createDraft(@AuthenticationPrincipal DetourUserPrincipal principal, @PathVariable String tripId, @RequestBody JsonNode request) {
        return ResponseEntity.status(201).body(trips.createDraft(requirePrincipal(principal).userId(), tripId, TripRequests.draftCreate(request)));
    }

    @PostMapping("/{tripId}/drafts/{draftId}/duplicate")
    ResponseEntity<TripResponse> duplicateDraft(@AuthenticationPrincipal DetourUserPrincipal principal, @PathVariable String tripId, @PathVariable String draftId, @RequestBody JsonNode request) {
        return ResponseEntity.status(201).body(trips.duplicateDraft(requirePrincipal(principal).userId(), tripId, draftId, TripRequests.draftMutation(request)));
    }

    @GetMapping("/{tripId}/drafts/{draftId}/readiness")
    DraftReadinessResponse inspectDraftReadiness(@AuthenticationPrincipal DetourUserPrincipal principal,
            @PathVariable String tripId, @PathVariable String draftId) {
        return trips.inspectDraftReadiness(requirePrincipal(principal).userId(), tripId, draftId);
    }

    @PostMapping("/{tripId}/drafts/{draftId}/plan")
    ResponseEntity<TripResponse> promoteDraft(@AuthenticationPrincipal DetourUserPrincipal principal, @PathVariable String tripId,
            @PathVariable String draftId, @RequestBody JsonNode request) {
        return ResponseEntity.status(201).body(trips.promoteDraft(requirePrincipal(principal).userId(), tripId, draftId, TripRequests.promotion(request)));
    }

    @PostMapping("/{tripId}/alternatives/{alternativeId}/duplicate")
    ResponseEntity<TripResponse> duplicateAlternative(@AuthenticationPrincipal DetourUserPrincipal principal, @PathVariable String tripId,
            @PathVariable String alternativeId, @RequestBody JsonNode request) {
        return ResponseEntity.status(201).body(trips.duplicateAlternative(requirePrincipal(principal).userId(), tripId, alternativeId, TripRequests.alternativeDuplicate(request)));
    }

    @DeleteMapping("/{tripId}/alternatives/{alternativeId}")
    TripResponse deleteAlternative(@AuthenticationPrincipal DetourUserPrincipal principal, @PathVariable String tripId,
            @PathVariable String alternativeId, @RequestBody JsonNode request) {
        return trips.deleteAlternative(requirePrincipal(principal).userId(), tripId, alternativeId, TripRequests.alternativeDelete(request));
    }

    @GetMapping("/{tripId}/drafts/{draftId}/airfare")
    AirfareSearchResponse searchDraftAirfare(
            @AuthenticationPrincipal DetourUserPrincipal principal,
            @PathVariable String tripId,
            @PathVariable String draftId,
            @RequestParam(defaultValue = "false") boolean directOnly,
            @RequestParam(defaultValue = "DEFAULT") String sort) {
        return trips.searchAirfare(requirePrincipal(principal).userId(), tripId, draftId, directOnly, sort);
    }

    @GetMapping("/{tripId}/airfare")
    AirfareSearchResponse searchTripAirfare(
            @AuthenticationPrincipal DetourUserPrincipal principal,
            @PathVariable String tripId,
            @RequestParam(defaultValue = "false") boolean directOnly,
            @RequestParam(defaultValue = "DEFAULT") String sort) {
        return trips.searchAirfare(requirePrincipal(principal).userId(), tripId, null, directOnly, sort);
    }

    @PutMapping("/{tripId}/drafts/{draftId}/airfare")
    TripResponse selectDraftAirfare(
            @AuthenticationPrincipal DetourUserPrincipal principal,
            @PathVariable String tripId,
            @PathVariable String draftId,
            @RequestBody JsonNode request) {
        return trips.selectDraftAirfare(
                requirePrincipal(principal).userId(),
                tripId,
                draftId,
                TripRequests.airfareSelection(request)
        );
    }

    @DeleteMapping("/{tripId}/drafts/{draftId}/airfare")
    TripResponse removeDraftAirfare(
            @AuthenticationPrincipal DetourUserPrincipal principal,
            @PathVariable String tripId,
            @PathVariable String draftId,
            @RequestBody JsonNode request) {
        return trips.removeDraftAirfare(
                requirePrincipal(principal).userId(),
                tripId,
                draftId,
                TripRequests.draftMutation(request)
        );
    }

    @GetMapping("/{tripId}/drafts/{draftId}/stays")
    StaySearchResponse searchDraftStays(
            @AuthenticationPrincipal DetourUserPrincipal principal,
            @PathVariable String tripId,
            @PathVariable String draftId,
            @RequestParam(required = false) String type,
            @RequestParam(defaultValue = "DEFAULT") String sort) {
        return trips.searchStays(requirePrincipal(principal).userId(), tripId, draftId, type, sort);
    }

    @GetMapping("/{tripId}/stays")
    StaySearchResponse searchTripStays(
            @AuthenticationPrincipal DetourUserPrincipal principal,
            @PathVariable String tripId,
            @RequestParam(required = false) String type,
            @RequestParam(defaultValue = "DEFAULT") String sort) {
        return trips.searchStays(requirePrincipal(principal).userId(), tripId, null, type, sort);
    }

    @PutMapping("/{tripId}/drafts/{draftId}/stays")
    TripResponse selectDraftStay(
            @AuthenticationPrincipal DetourUserPrincipal principal,
            @PathVariable String tripId,
            @PathVariable String draftId,
            @RequestBody JsonNode request) {
        return trips.selectDraftStay(
                requirePrincipal(principal).userId(),
                tripId,
                draftId,
                TripRequests.staySelection(request)
        );
    }

    @DeleteMapping("/{tripId}/drafts/{draftId}/stays")
    TripResponse removeDraftStay(
            @AuthenticationPrincipal DetourUserPrincipal principal,
            @PathVariable String tripId,
            @PathVariable String draftId,
            @RequestBody JsonNode request) {
        return trips.removeDraftStay(
                requirePrincipal(principal).userId(),
                tripId,
                draftId,
                TripRequests.draftMutation(request)
        );
    }

    @GetMapping("/{tripId}/drafts/{draftId}/rentals")
    RentalSearchResponse searchDraftRentals(
            @AuthenticationPrincipal DetourUserPrincipal principal,
            @PathVariable String tripId,
            @PathVariable String draftId,
            @RequestParam(required = false) String pickupAt,
            @RequestParam(required = false) String returnAt,
            @RequestParam(defaultValue = "DEFAULT") String sort) {
        return trips.searchRentals(requirePrincipal(principal).userId(), tripId, draftId, pickupAt, returnAt, sort);
    }

    @GetMapping("/{tripId}/rentals")
    RentalSearchResponse searchTripRentals(
            @AuthenticationPrincipal DetourUserPrincipal principal,
            @PathVariable String tripId,
            @RequestParam(required = false) String pickupAt,
            @RequestParam(required = false) String returnAt,
            @RequestParam(defaultValue = "DEFAULT") String sort) {
        return trips.searchRentals(requirePrincipal(principal).userId(), tripId, null, pickupAt, returnAt, sort);
    }

    @PutMapping("/{tripId}/drafts/{draftId}/rentals")
    TripResponse selectDraftRental(
            @AuthenticationPrincipal DetourUserPrincipal principal,
            @PathVariable String tripId,
            @PathVariable String draftId,
            @RequestBody JsonNode request) {
        return trips.selectDraftRental(
                requirePrincipal(principal).userId(),
                tripId,
                draftId,
                TripRequests.rentalSelection(request)
        );
    }

    @DeleteMapping("/{tripId}/drafts/{draftId}/rentals")
    TripResponse removeDraftRental(
            @AuthenticationPrincipal DetourUserPrincipal principal,
            @PathVariable String tripId,
            @PathVariable String draftId,
            @RequestBody JsonNode request) {
        return trips.removeDraftRental(
                requirePrincipal(principal).userId(),
                tripId,
                draftId,
                TripRequests.draftMutation(request)
        );
    }

    @DeleteMapping("/{tripId}/drafts/{draftId}")
    TripResponse deleteDraft(@AuthenticationPrincipal DetourUserPrincipal principal, @PathVariable String tripId, @PathVariable String draftId, @RequestBody JsonNode request) {
        return trips.deleteDraft(requirePrincipal(principal).userId(), tripId, draftId, TripRequests.draftMutation(request));
    }

    @DeleteMapping("/{tripId}")
    ResponseEntity<Void> deleteTrip(@AuthenticationPrincipal DetourUserPrincipal principal, @PathVariable String tripId, @RequestBody JsonNode request) {
        trips.deleteTrip(requirePrincipal(principal).userId(), tripId, TripRequests.tripDelete(request));
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{tripId}/bookings")
    ResponseEntity<BookingResponse> createBooking(
            @AuthenticationPrincipal DetourUserPrincipal principal,
            @PathVariable String tripId,
            @RequestBody JsonNode request,
            @RequestHeader(value = "Idempotency-Key", required = false) String headerKey) {
        BookingService.BookingOutcome outcome = bookingService.book(
                requirePrincipal(principal).userId(),
                tripId,
                TripRequests.booking(request, headerKey)
        );
        return ResponseEntity.status(outcome.created() ? 201 : 200).body(outcome.response());
    }

    @GetMapping("/{tripId}/bookings/active")
    BookingResponse activeBooking(
            @AuthenticationPrincipal DetourUserPrincipal principal,
            @PathVariable String tripId) {
        return bookingService.getActiveBooking(requirePrincipal(principal).userId(), tripId);
    }

    @GetMapping("/{tripId}/bookings")
    List<BookingResponse> bookingHistory(
            @AuthenticationPrincipal DetourUserPrincipal principal,
            @PathVariable String tripId) {
        return bookingService.getBookingHistory(requirePrincipal(principal).userId(), tripId);
    }

    @PostMapping("/{tripId}/bookings/{bookingId}/cancel")
    TripResponse cancelBooking(
            @AuthenticationPrincipal DetourUserPrincipal principal,
            @PathVariable String tripId,
            @PathVariable String bookingId,
            @RequestBody JsonNode request) {
        return bookingService.cancelBooking(
                requirePrincipal(principal).userId(),
                tripId,
                bookingId,
                TripRequests.cancel(request)
        );
    }

    @PostMapping("/{tripId}/cancel")
    TripResponse cancelTrip(
            @AuthenticationPrincipal DetourUserPrincipal principal,
            @PathVariable String tripId,
            @RequestBody JsonNode request) {
        return bookingService.cancelTrip(
                requirePrincipal(principal).userId(),
                tripId,
                TripRequests.cancel(request)
        );
    }

    private static DetourUserPrincipal requirePrincipal(DetourUserPrincipal principal) {
        if (principal == null) throw new ApiException(401, "UNAUTHENTICATED", "Authentication is required.");
        return principal;
    }
}
