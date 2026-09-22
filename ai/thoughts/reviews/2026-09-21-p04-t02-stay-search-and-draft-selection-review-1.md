# P04-T02 — Deliver Stay Search and Draft Selection Code Review — Cycle 1

## Scope and Repository State

- **Branch / Base:** `main` (commit head)
- **Ticket:** `ai/thoughts/tickets/2026-09-21-p04-t02-stay-search-and-draft-selection.md`
- **Implementation Plan:** `ai/thoughts/plans/2026-09-21-p04-t02-stay-search-and-draft-selection.md`
- **Testing Plan:** `ai/thoughts/plans/2026-09-21-p04-t02-stay-search-and-draft-selection-testing.md`
- **Reviewed Implementation Files:**
  - `src/main/java/app/detour/stay/AccommodationType.java` (type enum and 400 validation parsing)
  - `src/main/java/app/detour/stay/StaySort.java` (sort enum, default handling, and 400 validation parsing)
  - `src/main/java/app/detour/stay/StayCandidate.java` (record models for properties, units, and nightly inventory)
  - `src/main/java/app/detour/stay/StaySearchResponses.java` (record DTOs for stay search response, pricing breakdowns, and options)
  - `src/main/java/app/detour/stay/StaySearchRepository.java` & `src/main/java/app/detour/stay/JdbcStaySearchRepository.java` (candidate lookup and rental rate retrieval)
  - `src/main/java/app/detour/stay/StaySearchService.java` (room calculations, multi-night inventory validation, integer cent pricing, budget fit evaluation, deterministic sorting)
  - `src/main/java/app/detour/trip/TripRequests.java` (added `StaySelectionRequest` and strict JSON parser)
  - `src/main/java/app/detour/trip/TripRepository.java` & `src/main/java/app/detour/trip/JdbcTripRepository.java` (added `saveDraftStaySelection` and enriched `loadDraftSelections` with property/unit details and itemized nights)
  - `src/main/java/app/detour/trip/TripService.java` (`searchStays`, `@Transactional selectDraftStay`, `@Transactional removeDraftStay`)
  - `src/main/java/app/detour/trip/TripController.java` (draft-scoped and trip-scoped search endpoints, PUT selection, DELETE removal)
  - `src/test/java/app/detour/trip/StaySearchAndSelectionIntegrationTest.java` (15 comprehensive integration tests)

## Findings

No actionable findings.

## Findings Resolved in This Context

None. The implementation and test suite were complete and passed all quality gates and tests without requiring modifications during this review context.

## Acceptance-Criteria and Plan Conformance

| Criterion/decision | Code evidence | Test evidence | Result: implemented/partial/missing/safe deviation |
| --- | --- | --- | --- |
| Authenticated search by mandatory accommodation type (`HOTEL`, `BED_AND_BREAKFAST`, `VACATION_RENTAL`) | `AccommodationType.from`, `TripController.searchDraftStays`, `TripController.searchTripStays`, `TripService.searchStays` | `StaySearchAndSelectionIntegrationTest.searchStaysRequiresMandatoryAccommodationType`, `searchStaysReturnsAvailableUnitsForDestinationAndDates` | implemented |
| Automatic room count calculation based on party size and unit capacity | `StaySearchService.search`, `TripService.selectDraftStay` | `StaySearchAndSelectionIntegrationTest.searchStaysCalculatesRoomCountForPartySize` | implemented |
| Vacation rentals excluded when guest capacity < traveler count | `StaySearchService.search`, `TripService.selectDraftStay` | `StaySearchAndSelectionIntegrationTest.searchStaysExcludesVacationRentalsWhenCapacityExceeded` | implemented |
| Stays with insufficient inventory on any night excluded | `StaySearchService.search`, `JdbcStaySearchRepository.findCandidates`, `loadNights` | `StaySearchAndSelectionIntegrationTest.searchStaysExcludesUnitsWithInsufficientInventoryOnAnyNight` | implemented |
| Complete-stay pricing sums nightly amounts times required rooms in USD integer cents | `StaySearchService.search`, `StayPricingResponse` | `StaySearchAndSelectionIntegrationTest.searchStaysProvidesCompletePricingAndNightlyBreakdown` | implemented |
| Available trip budget subtracts selected airfare and car, but does not subtract existing stay | `TripService.searchStays` | `StaySearchAndSelectionIntegrationTest.searchStaysCalculatesAvailableBudgetExcludingExistingStay` | implemented |
| Default ranking tiers within-budget before over-budget, ordered by rating, price, distance, and catalog key | `StaySearchService.search` default comparator (`availableTripBudgetCents != null`) | `StaySearchAndSelectionIntegrationTest.searchStaysDefaultRankingPlacesWithinBudgetBeforeOverBudget` | implemented |
| When no trip budget is set, tiering is omitted and results order directly by rating, price, distance | `StaySearchService.search` default comparator (`availableTripBudgetCents == null`) | `StaySearchAndSelectionIntegrationTest.searchStaysDefaultRankingWithoutTripBudgetOrdersDirectly` | implemented |
| Sort overrides (`LOWEST_PRICE`, `HIGHEST_RATING`, `NEAREST_CITY_CENTER`) order deterministically | `StaySort`, `StaySearchService.search` sort override comparators | `StaySearchAndSelectionIntegrationTest.searchStaysSortOverridesOrderResultsDeterministically` | implemented |
| Saving stay selection persists unit ID and count, advances versions, returns updated `TripResponse` | `TripRepository.saveDraftStaySelection`, `TripService.selectDraftStay`, `JdbcTripRepository.loadDraftSelections` | `StaySearchAndSelectionIntegrationTest.selectDraftStayPersistsUnitAndCalculatedRoomCount` | implemented |
| Replacing stay selection updates existing selection without duplicate rows | `JdbcTripRepository.saveDraftStaySelection` (delete + insert) | `StaySearchAndSelectionIntegrationTest.selectDraftStayReplacesExistingSelectionWithoutDuplicates` | implemented |
| Removing stay selection deletes draft stay record cleanly and advances versions | `TripService.removeDraftStay`, `JdbcTripRepository.deleteDraftStaySelection` | `StaySearchAndSelectionIntegrationTest.removeDraftStayDeletesSelectionAndAdvancesVersions` | implemented |
| Mismatched `expectedVersion` or `expectedDraftVersion` returns 409 `VERSION_CONFLICT` | `TripService.selectDraftStay`, `TripService.removeDraftStay`, `advanceVersionForDraftMutation` | `StaySearchAndSelectionIntegrationTest.selectionMutationsEnforceOptimisticConcurrencyOnTripAndDraft` | implemented |
| Unauthorized access to another user's Trip returns 404 with no data disclosure | `TripService.ownedTrip`, `TripService.ownedDraft` | `StaySearchAndSelectionIntegrationTest.unownedTripOrDraftRejectsSearchAndMutationWithNotFound` | implemented |
| Backend integration tests verify room calculation, inventory filtering, budget math, ranking determinism, selection persistence | `StaySearchAndSelectionIntegrationTest.java` | All 15 tests pass across full HTTP cycles with H2 | implemented |

## Active Project Guardrails

None recorded in `ai/thoughts/design-lens.md`.

## Open Questions and Assumptions

None. Requirements and edge cases are clearly defined and backed by comprehensive integration tests.

## Verification Results

- PASS — `.\mvnw.cmd test -Dtest=StaySearchAndSelectionIntegrationTest` — 15/15 tests pass (4.146s)
- PASS — `.\mvnw.cmd test "-Dtest=StaySearchAndSelectionIntegrationTest,AirfareSearchAndSelectionIntegrationTest,TripApiIntegrationTest"` — 63/63 tests pass (16.375s)
- PASS — `.\mvnw.cmd test` — 88/88 tests pass across the entire repository test suite (24.992s)

## Residual Risks and Optional Developer Checks

- None. All inventory date range calculations, room capacity ceil functions, budget math, sorting tie-breakers, draft selections, optimistic version concurrency, and cross-user authorization boundaries are covered by automated integration tests.

## Disposition

`clean`
