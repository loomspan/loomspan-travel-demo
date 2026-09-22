# P05-T01 Code Review — Cycle 1

## Scope and Repository State

- **Ticket:** `ai/thoughts/tickets/2026-09-22-p05-t01-canonical-pricing-and-server-tally-engine.md`
- **Execution Mode:** Pipeline mode (Step 5 of `0_run_pipeline.md`, Profile: `full`)
- **Review Cycle:** 1
- **Reviewed Changes:**
  - Production code:
    - `src/main/java/app/detour/trip/ItineraryTallyResponse.java` (new immutable DTO record with integer cents)
    - `src/main/java/app/detour/trip/ItineraryTallyEngine.java` (new Spring `@Component` pure calculation engine)
    - `src/main/java/app/detour/trip/DraftResponse.java` (embedded `tally` with backward-compatible constructors)
    - `src/main/java/app/detour/trip/AlternativeResponse.java` (embedded `tally` on `PlannedResponse` and `AlternativeResponse` with backward-compatible constructors)
    - `src/main/java/app/detour/trip/TripResponse.java` (embedded `tally` for active trip aggregate with backward-compatible constructors)
    - `src/main/java/app/detour/trip/TripService.java` (injected `ItineraryTallyEngine`, computed tallies in `response()`, streamlined available budget in `searchStays` and `searchRentals`)
    - `frontend/src/api/tripsApi.ts` (added `ItineraryTallyResponse` type and optional `tally` on response models)
  - Test suites:
    - `src/test/java/app/detour/trip/ItineraryTallyEngineTest.java` (14 unit tests covering component formulas, boundary cases, and search budget calculations)
    - `src/test/java/app/detour/trip/TripPricingAndTallyIntegrationTest.java` (11 MockMvc integration tests covering API lifecycle, component combinations, budget boundaries, catalog freshness, and multi-user isolation)
  - Documentation / Ticket:
    - `ai/thoughts/tickets/2026-09-22-p05-t01-canonical-pricing-and-server-tally-engine.md` (acceptance criteria checkboxes updated)

## Findings

No actionable findings.

## Findings Resolved in This Context

None.

## Acceptance-Criteria and Plan Conformance

| Criterion/decision | Code evidence | Test evidence | Result: implemented/partial/missing/safe deviation |
| --- | --- | --- | --- |
| 1. Airfare, stay, rental car, and grand totals are calculated on the server and returned in integer cents for all Draft, Planned, and Alternative responses. | `ItineraryTallyEngine.java` (lines 9-49), `TripService.response` (lines 527-560), `DraftResponse.java`, `AlternativeResponse.java`, `TripResponse.java` | `ItineraryTallyEngineTest` (methods 1-7, 13), `TripPricingAndTallyIntegrationTest` (tests 1, 4, 5, 6, 7, 10) | implemented |
| 2. Trips with a defined budget return accurate `remainingBudgetCents`, `budgetOverageCents`, and `isOverBudget` indicators matching the server-calculated totals. | `ItineraryTallyEngine.calculateTally` (lines 55-70) | `ItineraryTallyEngineTest.calculateTally_withinBudget_calculatesPositiveRemainingAndZeroOverage`, `calculateTally_exactBudget_returnsZeroRemainingAndZeroOverage`, `calculateTally_overBudget_returnsZeroRemainingAndPositiveOverageWithFlag`, `TripPricingAndTallyIntegrationTest.grandTotalExceedingBudget_setsIsOverBudgetAndOverageCents` | implemented |
| 3. Trips without a defined budget return `null` for remaining budget and overage, and omit budget-fit ranking inputs in component searches. | `ItineraryTallyEngine.calculateTally` (lines 66-70), `TripService.searchStays` (lines 673-677), `TripService.searchRentals` (lines 793-797) | `ItineraryTallyEngineTest.calculateTally_nullBudget_returnsNullRemainingAndNullOverage`, `calculateAvailableSearchBudgets_deductsOtherComponentsAndExcludesSearchedComponent`, `TripPricingAndTallyIntegrationTest.initialTripCreation_withoutBudget_returnsNullBudgetMetrics`, `StaySearchAndSelectionIntegrationTest` | implemented |
| 4. Overall budgets of `0` cents are accepted, while negative values and values exceeding `100_000_000` cents are rejected with 400 `VALIDATION_FAILED`. | `TripService.validateBudget` (lines 500-508), `V12__create_owned_trip_and_initial_draft_schema.sql` (`ck_detour_trip_budget`) | `TripPricingAndTallyIntegrationTest.budgetValidationBoundaries_acceptsZeroAndRejectsNegativeOrOverMax`, `TripApiIntegrationTest.createTrip_enforcesBudgetBounds` | implemented |
| 5. Available budget in stay and rental car searches accurately deducts other selected components while excluding the component being searched or replaced. | `ItineraryTallyEngine.calculateAvailableStaySearchBudget` (lines 83-90), `ItineraryTallyEngine.calculateAvailableRentalSearchBudget` (lines 92-99), `TripService.searchStays`, `TripService.searchRentals` | `ItineraryTallyEngineTest.calculateAvailableSearchBudgets_deductsOtherComponentsAndExcludesSearchedComponent`, `TripPricingAndTallyIntegrationTest.availableBudgetInComponentSearches_excludesSearchedComponent` | implemented |
| 6. Unit and HTTP integration tests prove consistent tally calculations across all combinations of components (airfare only, stay only, rental only, airfare+stay, stay+car, airfare+car, and all three). | `ItineraryTallyEngine.java`, `TripService.java` | `ItineraryTallyEngineTest.calculateTally_allEightComponentCombinations`, `TripPricingAndTallyIntegrationTest.allSevenComponentCombinations_verifyConsistentServerCalculatedTallies` | implemented |
| 7. Multi-user isolation tests verify that pricing calculations and tally responses are strictly scoped to the authenticated trip owner. | `TripService.ownedTrip` (lines 444-447), `TripService.detail`, `TripController.java` | `TripPricingAndTallyIntegrationTest.multiUserIsolation_userCannotAccessOtherUserTripOrTally`, `TripApiIntegrationTest` | implemented |

## Active Project Guardrails

- `ai/thoughts/design-lens.md`: None recorded.

## Open Questions and Assumptions

- None.

## Verification Results

- PASS — `.\mvnw.cmd test` — Full Maven test suite executed (128 tests across 20 test classes, 0 failures, 0 errors, 0 skipped).
- PASS — `npm.cmd run test` in `frontend/` — Full Vitest test suite executed (53 tests across 5 test files, 0 failures).

## Residual Risks and Optional Developer Checks

- **Residual Risks:** None. All calculations utilize 64-bit integer cents (`long`/`Long`) with zero floating-point arithmetic. Boundary conditions (0 budget, null budget, 24h ceiling billing cycles, over-budget transitions) and multi-user isolation are thoroughly asserted.
- **Optional Developer Checks:** Non-blocking manual observation: launch the local backend via `.\mvnw.cmd spring-boot:run` and inspect `GET /api/trips/{tripId}` to confirm runtime JSON appearance of `tally`.

## Disposition

- `clean`
