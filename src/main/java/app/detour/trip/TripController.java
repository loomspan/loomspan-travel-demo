package app.detour.trip;

import app.detour.api.ApiException;
import app.detour.identity.DetourUserPrincipal;
import java.util.Map;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import tools.jackson.databind.JsonNode;

@RestController
@RequestMapping("/api/trips")
public class TripController {
    private final TripService trips;

    TripController(TripService trips) {
        this.trips = trips;
    }

    @PostMapping
    ResponseEntity<TripResponse> create(@AuthenticationPrincipal DetourUserPrincipal principal,
            @RequestBody JsonNode request) {
        return ResponseEntity.status(201).body(trips.create(requirePrincipal(principal).userId(), TripRequests.from(request)));
    }

    @GetMapping("/{tripId}")
    TripResponse detail(@AuthenticationPrincipal DetourUserPrincipal principal, @PathVariable String tripId) {
        return trips.detail(requirePrincipal(principal).userId(), tripId);
    }

    @PutMapping("/{tripId}")
    TripResponse replace(@AuthenticationPrincipal DetourUserPrincipal principal, @PathVariable String tripId, @RequestBody JsonNode request) {
        return trips.replaceSharedDetails(requirePrincipal(principal).userId(), tripId, TripRequests.update(request));
    }

    @PostMapping("/{tripId}/drafts")
    ResponseEntity<TripResponse> createDraft(@AuthenticationPrincipal DetourUserPrincipal principal, @PathVariable String tripId, @RequestBody JsonNode request) {
        return ResponseEntity.status(201).body(trips.createDraft(requirePrincipal(principal).userId(), tripId, TripRequests.draftCreate(request)));
    }

    @PostMapping("/{tripId}/drafts/{draftId}/duplicate")
    ResponseEntity<TripResponse> duplicateDraft(@AuthenticationPrincipal DetourUserPrincipal principal, @PathVariable String tripId, @PathVariable String draftId, @RequestBody JsonNode request) {
        return ResponseEntity.status(201).body(trips.duplicateDraft(requirePrincipal(principal).userId(), tripId, draftId, TripRequests.draftMutation(request)));
    }

    @DeleteMapping("/{tripId}/drafts/{draftId}")
    TripResponse deleteDraft(@AuthenticationPrincipal DetourUserPrincipal principal, @PathVariable String tripId, @PathVariable String draftId, @RequestBody JsonNode request) {
        return trips.deleteDraft(requirePrincipal(principal).userId(), tripId, draftId, TripRequests.draftMutation(request));
    }

    private static DetourUserPrincipal requirePrincipal(DetourUserPrincipal principal) {
        if (principal == null) throw new ApiException(401, "UNAUTHENTICATED", "Authentication is required.");
        return principal;
    }
}
