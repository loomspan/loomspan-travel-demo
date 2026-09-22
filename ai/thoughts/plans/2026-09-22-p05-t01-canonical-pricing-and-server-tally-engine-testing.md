# Canonical Pricing and Server Tally Engine Testing Plan

## Change Summary
Ticket P05-T01 introduces a centralized server-side pricing and tally calculation engine (`ItineraryTallyEngine`) that deterministically computes integer-cent totals for airfare, stay, rental car, and grand totals, remaining budget, overage, and available search budgets. It exposes an embedded `ItineraryTallyResponse` on `DraftResponse`, `PlannedResponse`, `AlternativeResponse`, and `TripResponse`, ensuring fresh catalog-backed price recalculations on retrieval and mutation.

## Impacted Areas and Risks

| Category | Risk | Planned evidence |
| --- | --- | --- |
| **Component Pricing Calculations** | Rounding errors or incorrect arithmetic in airfare party multiplication, stay nightly sums, or rental 24-hour cycle ceil rounding. | `ItineraryTallyEngineTest` unit tests asserting exact integer cents across individual components, multi-night stays, multi-traveler airfare, and rental duration boundaries (24h, 24h+1s, 25h, 48h, 49h). |
| **Budget Position Metrics** | Incorrect calculation of `remainingBudgetCents`, `budgetOverageCents`, or `isOverBudget` under boundary conditions (`grandTotal < budget`, `grandTotal == budget`, `grandTotal > budget`, `budgetCents == 0`, `budgetCents == null`). | Unit and HTTP tests asserting exact values for all 5 budget scenarios, especially zero-budget trips and null-budget trips. |
| **Component Combinations** | Inconsistencies when only a subset of components are selected (e.g. airfare only, stay+rental, etc.) or when all components are empty. | Tests verifying all 8 combinations (empty + 7 non-empty combinations) in both `ItineraryTallyEngineTest` and `TripPricingAndTallyIntegrationTest`. |
| **Search Available Budget** | Deducting the component being searched or replaced, artificially restricting user options. | Integration tests verifying that stay searches exclude existing stay costs and rental searches exclude existing rental costs. |
| **API Contract Compatibility** | Adding `tally` could disrupt existing client DTO parsing or test mocks. | Backward-compatible Java constructor overloads, optional TypeScript field definition `tally?: ItineraryTallyResponse` in `tripsApi.ts`, and full execution of the frontend Vitest suite (53 tests). |
| **Catalog Price Freshness vs Immutability** | Draft prices failing to reflect catalog updates or planned itineraries mutating after promotion. | Integration tests asserting dynamic recalculation on draft retrieval and mutation, and static preservation on planned itineraries. |
| **Multi-User Isolation** | Unauthorized users reading tally summaries or calculations belonging to other accounts. | MockMvc tests confirming 404 `NOT_FOUND` when attempting to access another user's trip tally. |

## Existing Coverage and Environment Constraints
- **Test frameworks:** JUnit 5, Spring Boot Test (`@SpringBootTest`, `@AutoConfigureMockMvc`), H2 in-memory database with Flyway migrations (`V1` through `V15`), Vitest (`v4.1.11`) for React/TypeScript.
- **Existing relevant tests:**
  - `TripApiIntegrationTest`: verifies budget validation (0 valid, -1 invalid, >100,000,000 invalid, null valid), multi-user isolation, and basic trip CRUD.
  - `StaySearchAndSelectionIntegrationTest`: verifies stay available budget deduction and budget tiering.
  - `RentalSearchAndSelectionIntegrationTest`: verifies 24-hour cycle math and rental selection.
  - `AirfareSearchAndSelectionIntegrationTest`: verifies airfare searching and selection.
- **Constraints:**
  - Windows environment requires `mvnw.cmd` for Maven commands and `npm.cmd` for npm commands (PowerShell execution policy blocks `npm.ps1`).
  - No database migration files needed; existing schema (`V12`, `V14`) already supports all required live and snapshot catalog queries.

## Failing Test First
- **Name:** `initialTripCreationExposesAuthoritativeZeroStateTally`
- **Type:** HTTP Integration Test (`@SpringBootTest` with MockMvc)
- **Location:** `src/test/java/app/detour/trip/TripPricingAndTallyIntegrationTest.java`
- **Arrange/Act/Assert:**
  - **Arrange:** Register a new user, prepare a `POST /api/trips` payload for destination "destination-sfo", dates "2027-03-01" to "2027-03-05", 2 travelers, budget `100_000L` cents ($1,000.00).
  - **Act:** Perform `POST /api/trips`.
  - **Assert:** Expect status 201 Created; assert `jsonPath("$.tally.grandTotalCents").value(0)`, `jsonPath("$.tally.remainingBudgetCents").value(100000)`, `jsonPath("$.tally.budgetOverageCents").value(0)`, `jsonPath("$.tally.isOverBudget").value(false)`, `jsonPath("$.drafts[0].tally.grandTotalCents").value(0)`.
- **Expected pre-fix failure:** `java.lang.AssertionError: No value at JSON path "$.tally"` because `tally` is not yet present on `TripResponse` or `DraftResponse`.

## Tests to Add or Update

### 1. `ItineraryTallyEngineTest.java` (Unit Test Suite)
- **Type:** Unit Test (JUnit 5)
- **Location:** `src/test/java/app/detour/trip/ItineraryTallyEngineTest.java`
- **Proves:** Pure mathematical correctness, boundary behavior, and search budget calculations without Spring context or database dependencies.
- **Cases:**
  1. `calculateAirfareTotal_multiTraveler_calculatesSumOfLegsMultipliedByTravelers`:
     - Inputs: outbound (base 20000, tax 2000, fee 1000 = 23000), return (base 18000, tax 2000, fee 1000 = 21000), 3 travelers.
     - Expected: `(23000 + 21000) * 3 = 132_000` cents.
  2. `calculateAirfareTotal_nullOrZeroTravelers_returnsZero`:
     - Inputs: null airfare or 0 travelers -> returns `0`.
  3. `calculateStayTotal_multiNightMultiUnit_sumsNightsMultipliedByUnitCount`:
     - Inputs: Night 1 (15000+1500+500 = 17000), Night 2 (18000+1800+500 = 20300), unitCount = 2.
     - Expected: `(17000 + 20300) * 2 = 74_600` cents.
  4. `calculateStayTotal_nullOrEmptyNights_returnsZero`:
     - Inputs: null stay or empty nights -> returns `0`.
  5. `calculateRentalTotal_exact24Hours_returnsSingleCycle`:
     - Inputs: pickup 10:00, return next day 10:00 (exact 24h), daily rate (4000+400+200 = 4600).
     - Expected: `1 * 4600 = 4600` cents.
  6. `calculateRentalTotal_partialCycleRoundUp_returnsCeilCycles`:
     - Inputs: pickup 10:00, return next day 10:00:01 (24h 1s), daily rate 4600 -> `2 * 4600 = 9200` cents.
     - Inputs: 25h -> 2 cycles; 48h -> 2 cycles; 49h -> 3 cycles.
  7. `calculateRentalTotal_nullRental_returnsZero`:
     - Inputs: null rental -> returns `0`.
  8. `calculateTally_withinBudget_calculatesPositiveRemainingAndZeroOverage`:
     - Inputs: grandTotal = 80000, budget = 100000.
     - Expected: `remaining = 20000`, `overage = 0`, `isOverBudget = false`.
  9. `calculateTally_exactBudget_returnsZeroRemainingAndZeroOverage`:
     - Inputs: grandTotal = 100000, budget = 100000.
     - Expected: `remaining = 0`, `overage = 0`, `isOverBudget = false`.
  10. `calculateTally_overBudget_returnsZeroRemainingAndPositiveOverageWithFlag`:
      - Inputs: grandTotal = 125000, budget = 100000.
      - Expected: `remaining = 0`, `overage = 25000`, `isOverBudget = true`.
  11. `calculateTally_zeroBudget_returnsZeroRemainingAndOverBudgetWhenNonZero`:
      - Inputs: budget = 0, grandTotal = 5000 -> `remaining = 0`, `overage = 5000`, `isOverBudget = true`.
      - Inputs: budget = 0, grandTotal = 0 -> `remaining = 0`, `overage = 0`, `isOverBudget = false`.
  12. `calculateTally_nullBudget_returnsNullRemainingAndNullOverage`:
      - Inputs: budget = null, grandTotal = 50000.
      - Expected: `remaining = null`, `overage = null`, `isOverBudget = false`.
  13. `calculateTally_allEightComponentCombinations`:
      - Tests 8 cases: None, Airfare only, Stay only, Rental only, Airfare+Stay, Stay+Car, Airfare+Car, Airfare+Stay+Car.
  14. `calculateAvailableSearchBudgets_deductsOtherComponentsAndExcludesSearchedComponent`:
      - Stay search with flight (30000) and car (15000) selected against budget (100000) -> returns `100000 - 30000 - 15000 = 55000`. Existing stay cost not deducted.
      - Rental search with flight (30000) and stay (40000) selected against budget (100000) -> returns `100000 - 30000 - 40000 = 30000`. Existing car cost not deducted.
      - When budget is null -> returns `null`.

### 2. `TripPricingAndTallyIntegrationTest.java` (HTTP Integration Suite)
- **Type:** Spring Boot MockMvc Integration Test
- **Location:** `src/test/java/app/detour/trip/TripPricingAndTallyIntegrationTest.java`
- **Proves:** End-to-end API correctness, DTO serialization, dynamic recalculation on mutation, catalog freshness, and authorization boundaries.
- **Cases:**
  1. `initialTripCreation_withBudget_returnsAuthoritativeZeroStateTally`:
     - Creates trip with budget 200,000 cents.
     - Verifies `TripResponse.tally` and `DraftResponse.tally` both have: `airfareTotalCents: 0, stayTotalCents: 0, rentalTotalCents: 0, grandTotalCents: 0, remainingBudgetCents: 200000, budgetOverageCents: 0, isOverBudget: false`.
  2. `initialTripCreation_withoutBudget_returnsNullBudgetMetrics`:
     - Creates trip with `budgetCents: null`.
     - Verifies `remainingBudgetCents: null`, `budgetOverageCents: null`, `isOverBudget: false`.
  3. `budgetValidationBoundaries_acceptsZeroAndRejectsNegativeOrOverMax`:
     - Budget `0`: accepted (201).
     - Budget `-1`: rejected (400 `VALIDATION_FAILED`).
     - Budget `100_000_001`: rejected (400 `VALIDATION_FAILED`).
     - Budget `100_000_000`: accepted (201).
  4. `airfareSelection_recalculatesAirfareTotalAndBudgetPosition`:
     - Selects round-trip airfare on draft.
     - Verifies `draft.tally.airfareTotalCents == (outbound + return) * travelers`.
     - Verifies `draft.tally.grandTotalCents == draft.tally.airfareTotalCents`.
     - Verifies `draft.tally.remainingBudgetCents == budget - airfareTotal`.
  5. `staySelection_recalculatesStayTotalAndBudgetPosition`:
     - Selects accommodation on draft.
     - Verifies `draft.tally.stayTotalCents == sum(nights) * unitCount`.
     - Verifies `draft.tally.grandTotalCents == airfareTotal + stayTotal`.
  6. `rentalSelection_recalculatesRentalTotalAndBudgetPosition`:
     - Selects rental on draft.
     - Verifies `draft.tally.rentalTotalCents == billingCycles * dailyRate`.
     - Verifies `draft.tally.grandTotalCents == airfareTotal + stayTotal + rentalTotal`.
  7. `allSevenComponentCombinations_verifyConsistentServerCalculatedTallies`:
     - Step-by-step adds and removes components to walk through all 7 non-empty combinations:
       - 1: Airfare only
       - 2: Airfare + Stay
       - 3: All three (Airfare + Stay + Rental)
       - 4: Stay + Rental (remove airfare)
       - 5: Rental only (remove stay)
       - 6: Airfare + Rental (add airfare)
       - 7: Stay only (remove airfare, add stay)
     - At every step, asserts exact arithmetic and consistency between `draft.tally` and `trip.tally`.
  8. `grandTotalExceedingBudget_setsIsOverBudgetAndOverageCents`:
     - Sets budget smaller than component total.
     - Asserts `remainingBudgetCents == 0`, `budgetOverageCents == grandTotal - budget`, and `isOverBudget == true`.
  9. `availableBudgetInComponentSearches_excludesSearchedComponent`:
     - Verifies stay search returns `availableTripBudgetCents == budget - flightTotal - rentalTotal`.
     - Verifies rental search returns `availableTripBudgetCents == budget - flightTotal - stayTotal`.
     - Verifies replacing stay preserves `availableTripBudgetCents`.
  10. `promoteDraftToPlanned_preservesAuthoritativeTallyOnPlannedResponse`:
      - Promotes draft to planned via `POST /api/trips/{tripId}/drafts/{draftId}/plan`.
      - Verifies `PlannedResponse.tally` matches the frozen snapshot price calculations.
      - Verifies `TripResponse.alternatives` contains tallies for both DRAFT and PLANNED items.
  11. `multiUserIsolation_userCannotAccessOtherUserTripOrTally`:
      - User A creates trip and adds components.
      - User B attempts `GET /api/trips/{tripA_id}` -> receives 404 `NOT_FOUND`.
      - Verifies User B cannot access User A's pricing tallies.

### 3. Frontend TypeScript Type Verification
- **Type:** TypeScript Compiler & Vitest Suite
- **Location:** `frontend/src/api/tripsApi.test.ts` & full test suite
- **Proves:** `ItineraryTallyResponse` interface compiles cleanly and existing frontend test fixtures continue to pass.

## Safe Verification Commands
- **Focused unit test:**
  `.\mvnw.cmd test -Dtest=ItineraryTallyEngineTest`
- **Focused integration test:**
  `.\mvnw.cmd test -Dtest=TripPricingAndTallyIntegrationTest`
- **Related suite:**
  `.\mvnw.cmd test -Dtest=ItineraryTallyEngineTest,TripPricingAndTallyIntegrationTest,TripApiIntegrationTest,StaySearchAndSelectionIntegrationTest,RentalSearchAndSelectionIntegrationTest,AirfareSearchAndSelectionIntegrationTest`
- **Full safe backend suite:**
  `.\mvnw.cmd test`
- **Full safe frontend suite:**
  `npm.cmd run test` (executed in `frontend/`)

## Optional Developer Checks
- Non-blocking manual observation: Launch dev server via `.\mvnw.cmd spring-boot:run` and inspect `GET /api/trips/{tripId}` via curl or browser developer tools to visually confirm the JSON structure of `tally`.

## Exit Criteria
- [x] The planned red test (`initialTripCreationExposesAuthoritativeZeroStateTally`) fails for the intended reason before implementation (absence of `$.tally`).
- [x] All new unit tests in `ItineraryTallyEngineTest` pass after implementation.
- [x] All new integration tests in `TripPricingAndTallyIntegrationTest` pass after implementation.
- [x] The full Maven test suite (`.\mvnw.cmd test`) passes with 0 failures, 0 errors, and 0 skipped.
- [x] The full Vitest test suite (`npm.cmd run test` in `frontend/`) passes with 53/53 tests passing.
- [x] All 7 acceptance criteria from ticket P05-T01 map to executable passing tests.
- [x] Routine automated tests do not perform unintended live or destructive operations (isolated H2 database used).
- [x] Material risks, edge cases (zero budget, null budget, overage, ceil cycles, replacement exclusion), and multi-user isolation are thoroughly covered.
- [x] Any optional check is reported as nonblocking and is not represented as already performed.
