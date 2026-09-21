# P04-T01 Deliver Airfare Search and Draft Selection Implementation Plan

## Overview
- Ticket: `ai/thoughts/tickets/2026-09-21-p04-t01-airfare-search-and-draft-selection.md`
- Research: `ai/thoughts/research/2026-09-21-p04-t01-airfare-search-and-draft-selection.md`
- Outcome: Authenticated users can search round-trip flight combinations between PDX and their Trip destination for their scheduled dates, filter for direct flights, receive deterministically ranked results priced for their complete party, and persist or remove an airfare selection on an autosaved Draft alternative with optimistic concurrency and complete data isolation.

## Current State
- **Airfare Catalog Fixtures:** Seeded in `V10` with 24 flight schedules, 720 flight instances, and 1,080 flight instance segments covering March 2027 for destinations SFO, MUC, and MEX from PDX.
- **Relational Schema:** `detour_trip_draft_airfare_selection` (`draft_id`, `outbound_flight_instance_id`, `return_flight_instance_id`) exists in migration `V14`, but has no HTTP search or selection mutation endpoints.
- **Trip Aggregate & Drafts:** `detour_trip` and `detour_trip_draft` support multi-draft ownership (`JdbcTripRepository`). `TripService` manages Trip and Draft lifecycles.
- **Selection Gaps:**
  1. No search query exists to pair outbound and return flight instances into round-trip combinations.
  2. `loadDraftSelections` in `JdbcTripRepository` currently instantiates `AirfareSelection` with dummy null descriptions and zero fares (`JdbcTripRepository.java:88-89`).
  3. `advanceVersionForDraft` in `JdbcTripRepository` increments `detour_trip.version` but does not increment `detour_trip_draft.version`.
  4. No HTTP endpoints exist for searching airfare or selecting/removing draft airfare (`TripController.java`).

## Desired End State
- **Search Endpoint:** `GET /api/trips/{tripId}/airfare` and `GET /api/trips/{tripId}/drafts/{draftId}/airfare` return round-trip flight combinations between PDX and the Trip's destination on `startDate` and `endDate`:
  - Filters for traveler seat capacity (`available_seats >= travelerCount`).
  - Filters out instances with final arrivals after March 31, 2027.
  - Supports `directOnly` boolean query parameter (default `false`).
  - Supports deterministic sort query parameter `sort` with options: `DEFAULT` (direct first, then lowest price, then shortest duration), `LOWEST_PRICE`, `SHORTEST_DURATION`, `EARLIEST_DEPARTURE`, `FEWEST_STOPS`. Every sort ends with the immutable combination key `${outboundCatalogKey}__${returnCatalogKey}` as tie-breaker.
  - Calculates complete-party pricing in USD integer cents: `travelerCount * (baseFare + tax + fee)` for outbound plus return, with detailed base, tax, and fee breakdown.
  - Exposes flight details: carrier, flight numbers, stop count, layover airport and duration (for 1-stop flights), departure/arrival times with timezone info, total duration, available seats, and complete-party price.
- **Draft Selection Persistence:** `PUT /api/trips/{tripId}/drafts/{draftId}/airfare` persists or replaces the round-trip selection in `detour_trip_draft_airfare_selection`, atomically advances the Trip version and increments the Draft version, and returns the updated `TripResponse` containing the full `DraftSelectionResponse`.
- **Draft Selection Removal:** `DELETE /api/trips/{tripId}/drafts/{draftId}/airfare` removes the selection from `detour_trip_draft_airfare_selection`, atomically advances the Trip version and increments the Draft version, and returns the updated `TripResponse`.
- **Concurrency & Security:**
  - Optimistic locking validates both `expectedVersion` (Trip) and `expectedDraftVersion` (Draft). Stale mutations return 409 `VERSION_CONFLICT` without modifying persisted data.
  - All queries and mutations are scoped to `ownerUserId`. Accessing unowned trips/drafts returns 404 `RESOURCE_NOT_FOUND` with zero data disclosure.

## Scope

### In scope
- Data models and DTOs for airfare search requests, responses, flight legs, layovers, and complete-party pricing.
- Search service and query generating valid round-trip combinations from seeded flight instances and schedules between PDX and the Trip destination/dates.
- Filtering by available seat capacity and `directOnly` flag.
- Exclusion of any flight instance whose final arrival is after March 31, 2027.
- Complete-party USD integer cents pricing calculations and breakdowns.
- Deterministic sorting (`DEFAULT`, `LOWEST_PRICE`, `SHORTEST_DURATION`, `EARLIEST_DEPARTURE`, `FEWEST_STOPS`) with `${outboundCatalogKey}__${returnCatalogKey}` tie-breaker.
- Draft airfare selection persistence and replacement in `detour_trip_draft_airfare_selection`.
- Draft airfare selection deletion.
- Optimistic concurrency control advancing `detour_trip.version` and `detour_trip_draft.version` on selection mutations.
- Updated `loadDraftSelections` in `JdbcTripRepository` joining flight catalog tables to populate realistic descriptions ("Flight CS101") and authoritative fares.
- HTTP endpoints on `TripController`:
  - `GET /api/trips/{tripId}/airfare`
  - `GET /api/trips/{tripId}/drafts/{draftId}/airfare`
  - `PUT /api/trips/{tripId}/drafts/{draftId}/airfare`
  - `DELETE /api/trips/{tripId}/drafts/{draftId}/airfare`
- Comprehensive integration tests verifying search, filtering, deterministic sorting, concurrency conflict handling, and cross-user isolation.

### Out of scope
- Accommodation search and selection (Phase 4.3).
- Rental car search and selection (Phase 4.4).
- Progressive trip-builder frontend UI (Phase 4.1 / P04-T04).
- Whole-itinerary canonical pricing.
- Draft-to-Planned promotion rule changes.
- Booking, payment, cancellation, and exchange.
- Version 2 Events.

## Active Project Guardrails

None recorded in `ai/thoughts/design-lens.md`.

## Impact and Risk Analysis

- **Concurrency & Version Skew Risk:** Selection mutation modifies a Draft, which changes both the Draft state and the aggregate Trip version. If only one version is checked or advanced, concurrent edits could cause lost updates or stale draft views.
  - *Mitigation:* Explicitly require `expectedVersion` and `expectedDraftVersion` on `PUT` and `DELETE`. In a single database transaction, check and increment `detour_trip_draft.version` and check and increment `detour_trip.version`. Return 409 `VERSION_CONFLICT` indicating the specific changed entity if mismatched.
- **Ranking Determinism Risk:** In SQL queries or memory sorting, non-deterministic order can produce flaky tests and erratic UI re-renders.
  - *Mitigation:* Every sort comparator must end with an immutable tie-breaker: `${outboundCatalogKey}__${returnCatalogKey}` ascending.
- **Cross-User Data Disclosure Risk:** Malicious or mistaken users searching or mutating airfare on other users' trips or drafts could leak itinerary details.
  - *Mitigation:* Verify `ownerUserId` on every trip query before executing any search or mutation. Return 404 `RESOURCE_NOT_FOUND` indistinguishable from non-existent trips.
- **Timezone and Date Boundary Risk:** Final arrivals for long-haul or overnight flights (e.g. PDX -> MUC arriving next day) must not exceed March 31, 2027 in the arrival airport's timezone.
  - *Mitigation:* Filter outbound arrivals against destination timezone <= 2027-03-31 and return arrivals against PDX timezone (`America/Los_Angeles`) <= 2027-03-31.

## Implementation Approach

1. **Package Organization:**
   - Place airfare search models, ranking logic, and repository queries in `app.detour.airfare`.
   - Wire airfare search and draft selection mutations through `TripService` and `TripController` to keep relational ownership cleanly encapsulated in the existing `detour_trip` aggregate root.
2. **Deterministic Round-Trip Pairing:**
   - Query matching outbound instances (`origin = 'PDX'`, `destination = trip.destination`, `service_date = trip.startDate`, `available_seats >= travelerCount`).
   - Query matching return instances (`origin = trip.destination`, `destination = 'PDX'`, `service_date = trip.endDate`, `available_seats >= travelerCount`).
   - Combine into cartesian product (at most 4 x 4 = 16 options per date/destination in seeded catalog).
   - Filter and sort in memory using deterministic Java comparators ending with `${outboundCatalogKey}__${returnCatalogKey}`.
3. **Draft Airfare Persistence & Resolution:**
   - In `JdbcTripRepository`, update `loadDraftSelections` to join `flight_instance` and `flight_schedule`, resolving `"Flight " + flight_number` and fares.
   - Add `saveDraftAirfareSelection` to replace existing selections idempotently (`DELETE` + `INSERT`).
   - Add `advanceVersionForDraftMutation` to atomically update `detour_trip_draft.version = version + 1` and `detour_trip.version = version + 1`.

---

## Phase 1: Airfare Search DTOs, Enums, and Request Parsers

### Changes
- [x] `src/main/java/app/detour/airfare/AirfareSort.java`
  - Define enum: `DEFAULT`, `LOWEST_PRICE`, `SHORTEST_DURATION`, `EARLIEST_DEPARTURE`, `FEWEST_STOPS`.
  - Add static parser `from(String value)` returning `DEFAULT` if null or blank, matching case-insensitively, or throwing `ApiException(400, "VALIDATION_FAILED", "Choose a supported sort option.")`.
- [x] `src/main/java/app/detour/airfare/AirfareSearchResponses.java` (or separate records)
  - `LayoverResponse(String airportCode, String airportName, long durationMinutes)`
  - `FlightLegResponse(long flightInstanceId, String catalogKey, String carrier, String flightNumber, int stopCount, String originAirportCode, String originAirportName, String destinationAirportCode, String destinationAirportName, OffsetDateTime departureTime, OffsetDateTime arrivalTime, String departureTimeZone, String arrivalTimeZone, long durationMinutes, int availableSeats, long baseFareCents, long taxCents, long feeCents, long totalFareCents, LayoverResponse layover)`
  - `PartyPricingResponse(int travelerCount, long perTravelerBaseFareCents, long perTravelerTaxCents, long perTravelerFeeCents, long perTravelerTotalCents, long partyBaseFareCents, long partyTaxCents, long partyFeeCents, long partyTotalPriceCents)`
  - `FlightCombinationResponse(String combinationKey, FlightLegResponse outbound, FlightLegResponse returnFlight, long totalDurationMinutes, boolean direct, PartyPricingResponse pricing)`
  - `AirfareSearchResponse(UUID tripId, UUID draftId, String destinationKey, String originAirportCode, String destinationAirportCode, LocalDate startDate, LocalDate endDate, int travelerCount, boolean directOnly, AirfareSort sort, List<FlightCombinationResponse> options)`
- [x] `src/main/java/app/detour/trip/TripRequests.java`
  - Add `AirfareSelectionRequest(long expectedVersion, long expectedDraftVersion, long outboundFlightInstanceId, long returnFlightInstanceId)`.
  - Add static parser `airfareSelection(JsonNode body)` enforcing `expectedVersion`, `expectedDraftVersion`, positive long IDs, and rejecting unexpected fields.

### Automated verification
- [x] `.\mvnw.cmd test-compile` — compile successfully with new DTOs and request types.

---

## Phase 2: Airfare Search Repository and Service

### Changes
- [x] `src/main/java/app/detour/airfare/AirfareSearchRepository.java` and `JdbcAirfareSearchRepository.java`
  - Define repository interface and JDBC implementation.
  - Query outbound flight instances for `origin = 'PDX'`, `destination = destinationId`, `service_date = startDate`, `available_seats >= travelerCount`, joining `flight_schedule`, `catalog_supplier`, `catalog_airport`, `flight_instance_segment`, and `flight_schedule_segment`.
  - Query return flight instances for `origin = destinationId`, `destination = 'PDX'`, `service_date = endDate`, `available_seats >= travelerCount`.
  - Filter out any flight instance whose final segment arrival date in destination/PDX timezone is after `2027-03-31`.
  - Extract layover details for 1-stop flights (`stop_count = 1`): layover airport code, layover airport name, and duration between segment 1 arrival and segment 2 departure.
  - Method `Optional<ValidatedFlightLeg> findLegById(long flightInstanceId)` to look up a flight instance for mutation validation.
- [x] `src/main/java/app/detour/airfare/AirfareSearchService.java`
  - Takes destination ID/key, start date, end date, traveler count, `directOnly`, and `AirfareSort`.
  - Pairs outbound and return flights into combinations with key `${outboundCatalogKey}__${returnCatalogKey}`.
  - Filters by `directOnly` (both legs must have `stopCount == 0`).
  - Computes complete-party pricing in USD integer cents (`travelerCount * (baseFare + tax + fee)`).
  - Sorts deterministically:
    - `DEFAULT`: `direct` desc, `partyTotalPriceCents` asc, `totalDurationMinutes` asc, `combinationKey` asc.
    - `LOWEST_PRICE`: `partyTotalPriceCents` asc, `combinationKey` asc.
    - `SHORTEST_DURATION`: `totalDurationMinutes` asc, `combinationKey` asc.
    - `EARLIEST_DEPARTURE`: `outbound.departureTime` asc, `combinationKey` asc.
    - `FEWEST_STOPS`: `(outbound.stopCount + return.stopCount)` asc, `combinationKey` asc.

### Automated verification
- [x] `.\mvnw.cmd test-compile` — clean compilation of repository and service.

---

## Phase 3: Draft Selection Persistence, Version Concurrency, and Catalog Joining

### Changes
- [x] `src/main/java/app/detour/trip/TripRepository.java` and `JdbcTripRepository.java`
  - Add `void saveDraftAirfareSelection(long draftId, long outboundFlightInstanceId, long returnFlightInstanceId)`.
  - Add `boolean advanceVersionForDraftMutation(long tripId, long ownerUserId, long expectedVersion, long draftId, long expectedDraftVersion)`.
  - Update `loadDraftSelections(long draftId)` to join `flight_instance` and `flight_schedule`, resolving `"Flight " + flight_number` descriptions and real base/tax/fee cents.
- [x] `src/main/java/app/detour/trip/TripService.java`
  - Add `searchAirfare(long ownerUserId, String tripId, String draftId, boolean directOnly, String sort)`:
    - Verifies trip ownership (`ownedTrip`) and draft ownership (`ownedDraft` if `draftId != null`).
    - Delegates to `AirfareSearchService`.
  - Add `selectDraftAirfare(long ownerUserId, String tripId, String draftId, TripRequests.AirfareSelectionRequest request)`:
    - Verifies trip and draft ownership.
    - Validates optimistic versions: throws 409 conflict if `expectedDraftVersion` or `expectedVersion` mismatches.
    - Validates flight instance IDs: ensures outbound != return; validates flight route, dates, and seat capacity.
    - Calls `advanceVersionForDraftMutation` to advance both versions atomically.
    - Calls `saveDraftAirfareSelection`.
    - Returns updated `TripResponse`.
  - Add `removeDraftAirfare(long ownerUserId, String tripId, String draftId, TripRequests.DraftMutation request)`:
    - Verifies trip and draft ownership.
    - Validates optimistic versions.
    - Calls `advanceVersionForDraftMutation` to advance both versions atomically.
    - Calls `deleteDraftAirfareSelection`.
    - Returns updated `TripResponse`.

### Automated verification
- [x] `.\mvnw.cmd test -Dtest=TripApiIntegrationTest` — existing trip tests continue passing with zero regressions.

---

## Phase 4: HTTP Endpoints and Controller Mapping

### Changes
- [x] `src/main/java/app/detour/trip/TripController.java`
  - Add `GET /{tripId}/drafts/{draftId}/airfare` and `GET /{tripId}/airfare`:
    - Parameters: `@RequestParam(defaultValue = "false") boolean directOnly`, `@RequestParam(defaultValue = "DEFAULT") String sort`.
    - Returns `AirfareSearchResponse`.
  - Add `PUT /{tripId}/drafts/{draftId}/airfare`:
    - Parameter: `@RequestBody JsonNode request`.
    - Calls `TripRequests.airfareSelection(request)`.
    - Calls `trips.selectDraftAirfare(...)`.
    - Returns `TripResponse`.
  - Add `DELETE /{tripId}/drafts/{draftId}/airfare`:
    - Parameter: `@RequestBody JsonNode request`.
    - Calls `TripRequests.draftMutation(request)`.
    - Calls `trips.removeDraftAirfare(...)`.
    - Returns `TripResponse`.

### Automated verification
- [x] `.\mvnw.cmd test-compile` — clean compilation.

---

## Phase 5: Integration Testing & Verification

### Changes
- [x] `src/test/java/app/detour/trip/AirfareSearchAndSelectionIntegrationTest.java`
  - Design and implement comprehensive integration tests:
    1. `searchRoundTripAirfareReturnsAvailableCombinationsForTripDestinationAndDates`: proves search generates round-trip combinations between PDX and destination for party size.
    2. `searchExcludesCombinationsWithInsufficientAvailableSeats`: proves combinations lacking capacity for traveler count are omitted.
    3. `directOnlyFilterRestrictsResultsToDirectFlights`: proves `directOnly=true` returns only direct flights while `directOnly=false` returns direct and one-stop flights.
    4. `defaultRankingOrdersDirectFirstThenPriceThenDurationThenTieBreaker`: proves default sort hierarchy.
    5. `sortOverridesApplyDeterministicallyWithCombinationKeyTieBreaker`: proves `LOWEST_PRICE`, `SHORTEST_DURATION`, `EARLIEST_DEPARTURE`, and `FEWEST_STOPS` sort orders.
    6. `partyPricingCalculatesExactTotalsInCentsWithTransparentBreakdown`: verifies exact integer cent pricing math (`travelerCount * (baseFare + tax + fee)`).
    7. `saveDraftAirfarePersistsSelectionAdvancesDraftAndTripVersionAndReturnsTripResponse`: proves draft selection persistence and version increments.
    8. `replaceDraftAirfareReplacesSelectionCleanlyWithoutOrphans`: proves replacing selection leaves no duplicate or orphaned rows.
    9. `removeDraftAirfareDeletesSelectionAdvancesDraftAndTripVersion`: proves deletion removes selection and advances version.
    10. `selectionMutationsEnforceOptimisticConcurrencyOnTripAndDraft`: proves 409 `VERSION_CONFLICT` when either `expectedVersion` or `expectedDraftVersion` mismatches.
    11. `unownedTripOrDraftRejectsSearchAndMutationWithNotFound`: proves 404 with zero data disclosure when requesting another user's trip or draft.

### Automated verification
- [x] `.\mvnw.cmd test -Dtest=AirfareSearchAndSelectionIntegrationTest` — all new tests pass.
- [x] `.\mvnw.cmd test` — complete backend test suite passes cleanly.
- [x] `npm.cmd test --prefix frontend` — frontend tests continue passing.

---

## Test Strategy
- **Unit / Domain Level:** Enums and request parsers validated for boundary inputs and missing fields.
- **Service / Query Level:** SQL query correctness for multi-segment flight instances, connections, layovers, and March 31 arrival boundaries.
- **Integration Level (`MockMvc`):** End-to-end HTTP integration tests covering authentication, route mapping, search filtering, deterministic ranking, optimistic locking, relational ownership isolation, and draft selection persistence.

## Acceptance-Criteria Traceability

| Acceptance criterion | Planned code evidence | Planned test evidence |
| --- | --- | --- |
| Authenticated search returns valid round-trip combinations between PDX and Trip destination/dates | `AirfareSearchService`, `JdbcAirfareSearchRepository`, `TripController.searchAirfare` | `AirfareSearchAndSelectionIntegrationTest#searchRoundTripAirfareReturnsAvailableCombinationsForTripDestinationAndDates` |
| Combinations with insufficient seat capacity are omitted | `JdbcAirfareSearchRepository` (`available_seats >= travelerCount`) | `AirfareSearchAndSelectionIntegrationTest#searchExcludesCombinationsWithInsufficientAvailableSeats` |
| `directOnly` filter restricts results to direct flights | `AirfareSearchService.filterDirect` | `AirfareSearchAndSelectionIntegrationTest#directOnlyFilterRestrictsResultsToDirectFlights` |
| Default ranking orders direct first, price, duration, combination key | `AirfareSearchService.sortOptions` (`DEFAULT`) | `AirfareSearchAndSelectionIntegrationTest#defaultRankingOrdersDirectFirstThenPriceThenDurationThenTieBreaker` |
| Sort overrides sort deterministically with combination key tie-breaker | `AirfareSearchService.sortOptions` (`LOWEST_PRICE`, `SHORTEST_DURATION`, `EARLIEST_DEPARTURE`, `FEWEST_STOPS`) | `AirfareSearchAndSelectionIntegrationTest#sortOverridesApplyDeterministicallyWithCombinationKeyTieBreaker` |
| Pricing calculates exact complete-party total in USD cents with transparent breakdown | `AirfareSearchService.calculatePartyPricing` | `AirfareSearchAndSelectionIntegrationTest#partyPricingCalculatesExactTotalsInCentsWithTransparentBreakdown` |
| Saving airfare persists selection, advances draft and trip versions, returns `TripResponse` | `TripService.selectDraftAirfare`, `JdbcTripRepository.saveDraftAirfareSelection` | `AirfareSearchAndSelectionIntegrationTest#saveDraftAirfarePersistsSelectionAdvancesDraftAndTripVersionAndReturnsTripResponse` |
| Replacing airfare selection replaces cleanly without duplicate rows | `JdbcTripRepository.saveDraftAirfareSelection` (replaces by `draft_id`) | `AirfareSearchAndSelectionIntegrationTest#replaceDraftAirfareReplacesSelectionCleanlyWithoutOrphans` |
| Removing airfare selection deletes draft record and advances version | `TripService.removeDraftAirfare`, `JdbcTripRepository.deleteDraftAirfareSelection` | `AirfareSearchAndSelectionIntegrationTest#removeDraftAirfareDeletesSelectionAdvancesDraftAndTripVersion` |
| Version mismatch returns 409 `VERSION_CONFLICT` without modifying data | `TripService.selectDraftAirfare`, `TripService.removeDraftAirfare` | `AirfareSearchAndSelectionIntegrationTest#selectionMutationsEnforceOptimisticConcurrencyOnTripAndDraft` |
| Another user cannot search or mutate airfare and receives 404 | `TripService.ownedTrip`, `TripService.ownedDraft` | `AirfareSearchAndSelectionIntegrationTest#unownedTripOrDraftRejectsSearchAndMutationWithNotFound` |
| Backend integration tests verify all core capabilities | `AirfareSearchAndSelectionIntegrationTest` | Full suite execution via `.\mvnw.cmd test` |

## Risks and Rollback/Recovery
- All database operations utilize Spring `@Transactional`. Failed mutations automatically roll back.
- No schema migrations are introduced; existing `V3`, `V10`, `V14` schemas are reused. Rollback involves reverting Java source files without any database state corruption.

## References
- Ticket: `ai/thoughts/tickets/2026-09-21-p04-t01-airfare-search-and-draft-selection.md`
- Research: `ai/thoughts/research/2026-09-21-p04-t01-airfare-search-and-draft-selection.md`
- Architecture: `ai/thoughts/architecture/2026-09-17-p00-t02-detour-replacement-architecture.md`
- Fixture integrity: `src/test/java/app/detour/catalog/AirfareFixtureIntegrityAssertions.java`
