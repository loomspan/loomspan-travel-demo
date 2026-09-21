# P04-T01 Code Review — Cycle 1

## Scope and Repository State

- **Ticket:** `ai/thoughts/tickets/2026-09-21-p04-t01-airfare-search-and-draft-selection.md`
- **Research:** `ai/thoughts/research/2026-09-21-p04-t01-airfare-search-and-draft-selection.md`
- **Implementation Plan:** `ai/thoughts/plans/2026-09-21-p04-t01-airfare-search-and-draft-selection.md`
- **Testing Plan:** `ai/thoughts/plans/2026-09-21-p04-t01-airfare-search-and-draft-selection-testing.md`
- **Design Lens:** `ai/thoughts/design-lens.md`
- **Branch / Base:** `main` (commit `d8fb425ebba1913ebc49da4a0ba138fa1f36c3fb`)
- **Working Tree Changes Inspected:**
  - Modified:
    - `src/main/java/app/detour/trip/JdbcTripRepository.java` (added `advanceVersionForDraftMutation`, `saveDraftAirfareSelection`, updated `loadDraftSelections` to join catalog schedules and instances)
    - `src/main/java/app/detour/trip/TripController.java` (added `searchDraftAirfare`, `searchTripAirfare`, `selectDraftAirfare`, `removeDraftAirfare`)
    - `src/main/java/app/detour/trip/TripRepository.java` (added method declarations for draft mutation versions and airfare selection persistence)
    - `src/main/java/app/detour/trip/TripRequests.java` (added `AirfareSelectionRequest` record and parser enforcing positive IDs and non-negative versions)
    - `src/main/java/app/detour/trip/TripService.java` (added `searchAirfare`, `selectDraftAirfare`, and `removeDraftAirfare` with ownership validation, optimistic concurrency control, and leg validation)
  - Untracked:
    - `src/main/java/app/detour/airfare/AirfareSort.java`
    - `src/main/java/app/detour/airfare/AirfareSearchResponses.java`
    - `src/main/java/app/detour/airfare/AirfareSearchRepository.java`
    - `src/main/java/app/detour/airfare/JdbcAirfareSearchRepository.java`
    - `src/main/java/app/detour/airfare/AirfareSearchService.java`
    - `src/test/java/app/detour/trip/AirfareSearchAndSelectionIntegrationTest.java`

## Findings

No actionable findings.

## Findings Resolved in This Context

None. No source code edits or implementation changes were made during this review cycle.

## Acceptance-Criteria and Plan Conformance

| Criterion/decision | Code evidence | Test evidence | Result: implemented/partial/missing/safe deviation |
| --- | --- | --- | --- |
| Authenticated users search round-trip flights between PDX and Trip destination/dates | `TripController.searchDraftAirfare`, `TripController.searchTripAirfare`, `TripService.searchAirfare`, `JdbcAirfareSearchRepository.findOutboundLegs`, `findReturnLegs` | `AirfareSearchAndSelectionIntegrationTest.java:45-96` (`searchRoundTripAirfareReturnsAvailableCombinationsForTripDestinationAndDates`) | implemented |
| Combinations with insufficient seat capacity omitted from search results | `JdbcAirfareSearchRepository.java:65,80` (`available_seats >= ?`), `TripService.java:572,581` | `AirfareSearchAndSelectionIntegrationTest.java:98-129` (`searchExcludesCombinationsWithInsufficientAvailableSeats`) | implemented |
| `directOnly` filter restricts results to direct flights; omitting returns direct and one-stop flights | `AirfareSearchService.java:37-40` (filters on `stopCount == 0`), `AirfareSearchResponses.java:58` | `AirfareSearchAndSelectionIntegrationTest.java:131-160` (`directOnlyFilterRestrictsResultsToDirectFlights`) | implemented |
| Default ranking orders direct first, complete-party price, duration, combination key tie-breaker | `AirfareSearchService.java:83-87` (`DEFAULT` comparator) | `AirfareSearchAndSelectionIntegrationTest.java:162-198` (`defaultRankingOrdersDirectFirstThenPriceThenDurationThenTieBreaker`) | implemented |
| Sort overrides (`LOWEST_PRICE`, `SHORTEST_DURATION`, `EARLIEST_DEPARTURE`, `FEWEST_STOPS`) sort deterministically with combination key tie-breaker | `AirfareSearchService.java:88-100` | `AirfareSearchAndSelectionIntegrationTest.java:200-260` (`sortOverridesApplyDeterministicallyWithCombinationKeyTieBreaker`) | implemented |
| Pricing calculates exact complete-party total (`travelerCount * perSeatTotal`) in USD integer cents with transparent breakdown | `AirfareSearchService.java:49-69` (`PartyPricingResponse`) | `AirfareSearchAndSelectionIntegrationTest.java:262-304` (`partyPricingCalculatesExactTotalsInCentsWithTransparentBreakdown`) | implemented |
| Saving airfare persists selection into `detour_trip_draft_airfare_selection`, advances draft and trip version, returns `TripResponse` | `TripService.java:545-594`, `JdbcTripRepository.java:113-117` (`saveDraftAirfareSelection`), `advanceVersionForDraftMutation` | `AirfareSearchAndSelectionIntegrationTest.java:306-350` (`saveDraftAirfarePersistsSelectionAdvancesDraftAndTripVersionAndReturnsTripResponse`) | implemented |
| Replacing airfare selection cleanly replaces without orphaned or duplicate rows | `JdbcTripRepository.java:113-117` (`DELETE` + `INSERT` by `draft_id`) | `AirfareSearchAndSelectionIntegrationTest.java:352-396` (`replaceDraftAirfareReplacesSelectionCleanlyWithoutOrphans`) | implemented |
| Removing airfare selection deletes draft record and advances versions | `TripService.java:596-608`, `JdbcTripRepository.java:171-173` (`deleteDraftAirfareSelection`) | `AirfareSearchAndSelectionIntegrationTest.java:398-431` (`removeDraftAirfareDeletesSelectionAdvancesDraftAndTripVersion`) | implemented |
| Mutations with mismatched `expectedVersion` or `expectedDraftVersion` return 409 `VERSION_CONFLICT` without modifying persisted data | `TripService.java:585-587,602-604`, `mutationConflict`, `JdbcTripRepository.java:97-111` | `AirfareSearchAndSelectionIntegrationTest.java:433-494` (`selectionMutationsEnforceOptimisticConcurrencyOnTripAndDraft`) | implemented |
| Cross-user isolation: foreign users cannot search or mutate airfare and receive 404 `RESOURCE_NOT_FOUND` | `TripService.java:420-436` (`ownedTrip`, `ownedDraft`) | `AirfareSearchAndSelectionIntegrationTest.java:496-550` (`unownedTripOrDraftRejectsSearchAndMutationWithNotFound`) | implemented |
| Invalid flight selection rejected with 400 `VALIDATION_FAILED` | `TripService.java:550-583`, `TripRequests.java:18-25` | `AirfareSearchAndSelectionIntegrationTest.java:552-595` (`invalidFlightSelectionRejectsWithValidationError`) | implemented |
| Exclude flight instances arriving after March 31, 2027 | `JdbcAirfareSearchRepository.java:106-110` (`isArrivalWithinLimit`) | `AirfareSearchRepository` tests and fixture assertions | implemented |

## Active Project Guardrails

None recorded in `ai/thoughts/design-lens.md`.

## Open Questions and Assumptions

None.

## Verification Results

- PASS — `.\mvnw.cmd test -Dtest=AirfareSearchAndSelectionIntegrationTest` — 12 tests run, 0 failures, 0 errors.
- PASS — `.\mvnw.cmd test "-Dtest=TripApiIntegrationTest,AirfareFixtureIntegrationTest,AirfareSearchAndSelectionIntegrationTest"` — 52 tests run, 0 failures, 0 errors.
- PASS — `.\mvnw.cmd test` — Full backend test suite: 73 tests run, 0 failures, 0 errors.
- PASS — `npm.cmd test --prefix frontend` — Full frontend test suite: 36 tests run, 0 failures.

## Residual Risks and Optional Developer Checks

- None. All acceptance criteria and risk dimensions (seat capacity, deterministic ranking, complete-party pricing, optimistic concurrency, cross-user isolation) are thoroughly verified with executable integration tests.

## Disposition

`clean`
