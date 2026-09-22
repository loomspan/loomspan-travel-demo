# P04-T03 Code Review — Cycle 1

## Scope and Repository State

- **Target Ticket:** `ai/thoughts/tickets/2026-09-21-p04-t03-rental-car-search-and-draft-selection.md`
- **Execution Profile:** Full 5-Step Pipeline (`full`)
- **Review Mode:** Pipeline mode (Cycle 1)
- **Base Commit:** `7e785fc clean up after P04-T02`
- **Changed Artifacts Evaluated:**
  - `ai/thoughts/tickets/2026-09-21-p04-t03-rental-car-search-and-draft-selection.md` (acceptance criteria checkboxes verified)
  - `ai/thoughts/plans/2026-09-21-p04-t03-rental-car-search-and-draft-selection.md` (implementation plan conformance)
  - `ai/thoughts/plans/2026-09-21-p04-t03-rental-car-search-and-draft-selection-testing.md` (testing plan conformance)
  - `src/main/java/app/detour/rental/RentalSort.java` (sort enum and parsing)
  - `src/main/java/app/detour/rental/RentalCandidate.java` (catalog query projection)
  - `src/main/java/app/detour/rental/RentalSearchResponses.java` (immutable search and pricing response records)
  - `src/main/java/app/detour/rental/RentalSearchRepository.java` (repository contract)
  - `src/main/java/app/detour/rental/JdbcRentalSearchRepository.java` (JDBC query implementation with half-open occupancy check)
  - `src/main/java/app/detour/rental/RentalSearchService.java` (interval validation in airport timezone, 25+ driver gating, 24-hour billing math, deterministic ranking)
  - `src/main/java/app/detour/trip/TripRepository.java` (added `saveDraftRentalSelection`)
  - `src/main/java/app/detour/trip/JdbcTripRepository.java` (implemented `saveDraftRentalSelection`)
  - `src/main/java/app/detour/trip/TripRequests.java` (added `RentalSelectionRequest` and parser)
  - `src/main/java/app/detour/trip/TripController.java` (added GET, PUT, DELETE endpoints for draft and trip rental operations with plural and singular aliases)
  - `src/main/java/app/detour/trip/TripService.java` (implemented `searchRentals`, `selectDraftRental`, `removeDraftRental`)
  - `src/test/java/app/detour/trip/RentalSearchAndSelectionIntegrationTest.java` (13 end-to-end integration tests)

## Findings

No actionable findings.

## Findings Resolved in This Context

None (no implementation changes required).

## Acceptance-Criteria and Plan Conformance

| Criterion/decision | Code evidence | Test evidence | Result: implemented/partial/missing/safe deviation |
| --- | --- | --- | --- |
| Authenticated user can search rental cars specifying pickup and return date/times within trip interval at destination airport | `TripController.java:179-198`, `TripService.java:750-822`, `RentalSearchService.java:29-104`, `JdbcRentalSearchRepository.java:20-54` | `RentalSearchAndSelectionIntegrationTest.java:53-120` (`searchRentalCarsReturnsAvailableInventoryDeterministicallyRanked`) | implemented |
| Requests with return before or equal to pickup, or times outside trip date window, rejected with clear validation messages | `RentalSearchService.java:106-135` (`validateInterval`) | `RentalSearchAndSelectionIntegrationTest.java:122-162` (`...ValidatesPickupAndReturnDates...`, `...RejectsReturnBeforeOrEqualToPickup`) | implemented |
| When no traveler is 25 or older, search indicates selection disabled with required explanation; selecting car rejected | `RentalSearchService.java:47-50,137-139`, `TripService.java:829-831` | `RentalSearchAndSelectionIntegrationTest.java:164-203,343-368` (`...Enforces25PlusDriverEligibility...`, `selectDraftRentalRejectsWhenDriverUnder25`) | implemented |
| Rental units with overlapping reservations in `rental_unit_occupancy` over `[pickupAt, returnAt)` excluded | `JdbcRentalSearchRepository.java:43-50,85-96` | `RentalSearchAndSelectionIntegrationTest.java:205-264,423-441` (`...FiltersOutActiveOverlappingOccupancy...`, `selectDraftRentalRejectsWhenUnitOccupiedOrInvalidDates`) | implemented |
| Pricing charges consecutive 24-hour cycles and rounds partial final cycle up to full cycle in USD integer cents | `RentalSearchService.java:141-168` (`calculateBillingCycles`, `computePricing`) | `RentalSearchAndSelectionIntegrationTest.java:266-306` (`pricingCalculatesConsecutive24HourBillingCyclesAndRoundsUpPartialFinalCycle`) | implemented |
| Results default-order by Economy, Standard, SUV; then lowest total price; then vehicle unit catalog key | `RentalSearchService.java:77-87,170-175` | `RentalSearchAndSelectionIntegrationTest.java:77-94` (ordering assertions across 7 SFO units) | implemented |
| Saving rental selection persists unit ID, pickup time, return time in `detour_trip_draft_rental_selection`, advances draft version, and returns updated `TripResponse` | `TripService.java:824-856`, `JdbcTripRepository.java:261-265` | `RentalSearchAndSelectionIntegrationTest.java:308-341` (`selectDraftRentalPersistsSelectionAndAdvancesVersions`) | implemented |
| Replacing rental selection updates draft without duplicate rows | `JdbcTripRepository.java:261-265` (`DELETE` prior draft row + `INSERT`) | `RentalSearchAndSelectionIntegrationTest.java:443-489` (`replaceDraftRentalSelectionUpdatesDraftWithoutDuplicateRows`) | implemented |
| Removing rental selection deletes draft rental record and advances draft version | `TripService.java:858-870`, `JdbcTripRepository.java:266-268` | `RentalSearchAndSelectionIntegrationTest.java:491-529` (`removeDraftRentalSelectionDeletesSelectionAndAdvancesVersions`) | implemented |
| Concurrency conflicts return 409 `VERSION_CONFLICT`; unauthorized access returns 404 with no data disclosure | `TripService.java:850-852,864-866,878-883`, `TripRequests.java:169-173` | `RentalSearchAndSelectionIntegrationTest.java:531-637` (`draftRentalMutationsEnforceOptimisticConcurrency`, `rentalEndpointsEnforceCrossUserIsolation`) | implemented |
| Backend integration tests verify all capabilities | `RentalSearchAndSelectionIntegrationTest.java` (13 tests) | All 13 test methods passing cleanly | implemented |

## Active Project Guardrails

- `ai/thoughts/design-lens.md`: None recorded.

## Open Questions and Assumptions

- None.

## Verification Results

- PASS — `.\mvnw.cmd test -Dtest=RentalSearchAndSelectionIntegrationTest` — 13 tests run, 0 failures, 0 errors, 0 skipped (time: 4.278 s).
- PASS — `.\mvnw.cmd test` — 101 tests run across full repository suite, 0 failures, 0 errors, 0 skipped (time: 29.491 s).
- PASS — `.\mvnw.cmd test-compile` — Clean compilation of production and test code.

## Residual Risks and Optional Developer Checks

- None.

## Disposition

- `clean`
