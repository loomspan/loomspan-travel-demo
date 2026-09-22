# Canonical Pricing and Server Tally Engine Implementation Plan

## Overview
- Ticket: `ai/thoughts/tickets/2026-09-22-p05-t01-canonical-pricing-and-server-tally-engine.md`
- Research: `ai/thoughts/research/2026-09-22-p05-t01-canonical-pricing-and-server-tally-engine.md`
- Outcome: Deterministically calculate canonical airfare, stay, rental car, and grand totals, remaining budget, overage, and available component search budgets across all Draft, Planned, and Alternative representations on the server using integer cents (USD), exposing authoritative price summaries in API responses (`DraftResponse`, `PlannedResponse`, `AlternativeResponse`, and `TripResponse`) via an embedded `ItineraryTallyResponse`.

## Current State
- Component pricing math is fragmented: airfare pricing formulas reside in `AirfareSearchService.java` (lines 49-69), stay pricing formulas in `StaySearchService.java` (lines 78-117), rental car billing cycle formulas in `RentalSearchService.java` (lines 141-150), and inline ad-hoc budget deduction in `TripService.java` (lines 646-673 for stays, 788-809 for rentals).
- Frontend `ItinerarySummaryTally.tsx` (lines 15-58) currently performs client-side arithmetic for airfare, stay, rental, grand total, and budget overage/remaining calculations based solely on component selection facts.
- API response DTOs (`DraftResponse`, `PlannedResponse`, `AlternativeResponse`, and `TripResponse` in `DraftResponse.java`, `AlternativeResponse.java`, and `TripResponse.java`) return component selection facts (`DraftSelectionResponse`) but omit authoritative tally metrics.
- Budget bounds (`0` to `100_000_000` cents, or `null`) are validated in `TripService.validateBudget` and enforced by database check constraint `ck_detour_trip_budget` in `detour_trip`.
- Draft selections store only foreign keys to catalog inventory (`detour_trip_draft_*_selection`), dynamically joining live catalog records on query in `JdbcTripRepository.loadDraftSelections`, ensuring price freshness. Planned itineraries persist frozen price snapshots in `detour_planned_*_snapshot` loaded by `JdbcTripRepository.loadPlannedSelections`.

## Desired End State
- A centralized, pure calculation engine `ItineraryTallyEngine` in `app.detour.trip` provides deterministic methods to calculate individual component totals (airfare, stay, rental), grand totals, budget position (remaining, overage, isOverBudget), and available search budgets.
- All pricing and monetary metrics are represented strictly in integer cents (USD `long`/`Long`) with zero floating-point math.
- An immutable DTO `ItineraryTallyResponse(long airfareTotalCents, long stayTotalCents, long rentalTotalCents, long grandTotalCents, Long remainingBudgetCents, Long budgetOverageCents, boolean isOverBudget)` is embedded across all API responses:
  - `DraftResponse`: exposes `tally` for the draft.
  - `PlannedResponse`: exposes `tally` for the planned itinerary.
  - `AlternativeResponse`: exposes `tally` for each alternative (draft or planned).
  - `TripResponse`: exposes `tally` for the active itinerary (primary draft if present, otherwise planned, otherwise empty).
- `TripService.searchStays` and `TripService.searchRentals` delegate available search budget calculations to `ItineraryTallyEngine`, eliminating duplicated inline math.
- Airfare, stay, rental, and grand totals are refreshed against live catalog data upon retrieval and mutation for drafts, while planned itineraries evaluate against frozen snapshots.
- Frontend API contracts in `tripsApi.ts` define `ItineraryTallyResponse` and update response types with backward compatibility for existing tests.

## Scope

### In scope
- Creation of `ItineraryTallyResponse` record in `app.detour.trip`.
- Creation of `ItineraryTallyEngine` Spring component in `app.detour.trip` encapsulating:
  - Round-trip airfare party total calculation: `(outboundPerTraveler + returnPerTraveler) * travelerCount`.
  - Stay party total calculation: `sum(nightly_base + tax + fee) * unitCount`.
  - Rental car total calculation: `billingCycles * (daily_base + tax + fee)` where `billingCycles = Math.max(1, (totalSeconds + 86399) / 86400)`.
  - Grand total calculation: `airfareTotal + stayTotal + rentalTotal`.
  - Budget position calculation: `remainingBudgetCents`, `budgetOverageCents`, and `isOverBudget` for defined budgets (`0..100_000_000`) and null budgets.
  - Available search budget calculation for stays and rentals deducting other selected components while excluding the searched component.
- Updating `DraftResponse`, `PlannedResponse`, `AlternativeResponse`, and `TripResponse` to expose `tally: ItineraryTallyResponse`.
- Updating `TripService.response` to assemble authoritative tallies across drafts, planned itineraries, alternatives, and active trip summary.
- Replacing inline search budget math in `TripService.searchStays` and `TripService.searchRentals` with `ItineraryTallyEngine`.
- Updating `frontend/src/api/tripsApi.ts` with `ItineraryTallyResponse` type and optional fields on response types.
- Comprehensive unit and integration test suites covering all component combinations, budget boundaries, and multi-user isolation.

### Out of scope
- Modifying UI components (e.g. `ItinerarySummaryTally.tsx`, `TripWorkspace.tsx`, or budget dialogs); downstream tickets P05-T03 and P05-T04 display tallies in builder and comparison views.
- Promotion readiness revalidation and overage acknowledgment (deferred to P05-T02).
- Snapshot schema changes or database migration alterations (existing `V12` and `V14` schemas already support live and snapshot pricing).
- Booking reservation mutations or checkout transactions (Phase 6).
- Version 2 Events or WebSocket updates.

## Active Project Guardrails

None recorded in `ai/thoughts/design-lens.md`.

## Impact and Risk Analysis

- **Arithmetic Precision and Zero-Budget Boundaries:** All totals and budget metrics must use integer cents (`long`/`Long`). A budget of `0` cents is a valid target; when selections exist against a budget of 0, `remainingBudgetCents` must be `0`, `budgetOverageCents` must equal `grandTotalCents`, and `isOverBudget` must be `true`. When selections are empty against a budget of 0, `remainingBudgetCents` is `0`, `budgetOverageCents` is `0`, and `isOverBudget` is `false`.
- **Search Available Budget Deductions:** In stay searches, existing stay selections must not be deducted (allowing replacement). In rental searches, existing rental selections must not be deducted. If no overall budget is set (`budgetCents == null`), available search budget must be `null`, suppressing budget tiering.
- **Multi-Alternative Consistency:** Trips may contain multiple drafts and planned itineraries. Each alternative must calculate its tally from its own component selections, while `TripResponse.tally` represents the active draft (`drafts.get(0)`).
- **Multi-User Isolation:** All tally data exposed via `TripResponse` and alternative endpoints must be strictly scoped to the authenticated trip owner.
- **Frontend Contract Compatibility:** Adding `tally` as an embedded object on `DraftResponse`, `PlannedResponse`, `AlternativeResponse`, and `TripResponse` must not break existing TypeScript mocks in `frontend/src/App.test.tsx` or `ProgressiveTripBuilder.test.tsx`.

## Implementation Approach

1. **Encapsulate Pure Calculation in `ItineraryTallyEngine`:**
   A dedicated `@Component` `ItineraryTallyEngine` in `app.detour.trip` provides deterministic calculation methods for component totals, grand total, budget position, and available search budgets. Separating calculation logic from database queries and HTTP handling enables comprehensive unit test coverage and clean reusability in downstream tickets (e.g. readiness validation in P05-T02).

2. **Embedded `ItineraryTallyResponse` DTO:**
   Introduce `ItineraryTallyResponse` containing `airfareTotalCents`, `stayTotalCents`, `rentalTotalCents`, `grandTotalCents`, `remainingBudgetCents`, `budgetOverageCents`, and `isOverBudget`. Embed `tally` into `DraftResponse`, `PlannedResponse`, `AlternativeResponse`, and `TripResponse`. Overloaded constructors in Java records preserve source compatibility.

3. **Centralized Response Assembly in `TripService.response`:**
   In `TripService.java`, compute tallies for all drafts, planned itineraries, and alternatives during response generation. All mutation endpoints (`selectDraftAirfare`, `selectDraftStay`, `selectDraftRental`, `removeDraft*`, `planTrip`) call `response(trip)`, ensuring fresh catalog-backed pricing recalculation on every mutation.

4. **Streamline Available Budget in Component Searches:**
   Replace the manual inline calculations in `TripService.searchStays` and `TripService.searchRentals` with calls to `ItineraryTallyEngine.calculateAvailableStaySearchBudget` and `ItineraryTallyEngine.calculateAvailableRentalSearchBudget`.

## Phase 1: Canonical Tally Engine Component & DTOs

### Changes
- [x] `src/main/java/app/detour/trip/ItineraryTallyResponse.java` — Create record with fields: `long airfareTotalCents`, `long stayTotalCents`, `long rentalTotalCents`, `long grandTotalCents`, `Long remainingBudgetCents`, `Long budgetOverageCents`, `boolean isOverBudget`.
- [x] `src/main/java/app/detour/trip/ItineraryTallyEngine.java` — Implement Spring `@Component` with methods:
  - `long calculateAirfareTotal(AirfareSelection airfare, int travelerCount)`: `(outboundPerTraveler + returnPerTraveler) * travelerCount`. Returns 0 if airfare is null or travelerCount <= 0.
  - `long calculateStayTotal(StaySelection stay)`: `sum(nights) * unitCount`. Returns 0 if stay is null or nights is null/empty.
  - `long calculateRentalTotal(RentalSelection rental)`: `billingCycles * dailyRate`, where `billingCycles = Math.max(1, (Duration.between(pickupAt, returnAt).getSeconds() + 86399) / 86400)` and `dailyRate = dailyBasePriceCents + dailyTaxCents + dailyFeeCents`. Returns 0 if rental is null.
  - `long calculateGrandTotal(long airfareTotal, long stayTotal, long rentalTotal)`: sum of components.
  - `ItineraryTallyResponse calculateTally(DraftSelections selections, int travelerCount, Long budgetCents)`: calculates component totals, grand total, and budget metrics according to budget presence:
    - If `budgetCents != null`: `remaining = max(0, budget - grandTotal)`, `overage = max(0, grandTotal - budget)`, `isOverBudget = grandTotal > budget`.
    - If `budgetCents == null`: `remaining = null`, `overage = null`, `isOverBudget = false`.
  - `Long calculateAvailableStaySearchBudget(Long budgetCents, DraftSelections selections, int travelerCount)`: if `budgetCents == null` return null; otherwise `budgetCents - airfareTotal - rentalTotal`.
  - `Long calculateAvailableRentalSearchBudget(Long budgetCents, DraftSelections selections, int travelerCount)`: if `budgetCents == null` return null; otherwise `budgetCents - airfareTotal - stayTotal`.
- [x] `src/test/java/app/detour/trip/ItineraryTallyEngineTest.java` — Unit tests covering:
  - Airfare party totals (single traveler, multiple travelers, zero fare, null airfare).
  - Stay totals (single night, multi-night, multi-unit, zero price, null stay).
  - Rental car totals (exact 24h, 24h 1s round-up, multi-day, missing timestamps fallback, null rental).
  - Grand total summation across all 8 component combinations (empty, airfare-only, stay-only, rental-only, airfare+stay, stay+car, airfare+car, all three).
  - Budget calculations: within budget (`grandTotal < budget`), exact budget (`grandTotal == budget`), over budget (`grandTotal > budget`), zero budget (`budgetCents == 0`), null budget (`budgetCents == null`).
  - Available search budget calculations for stay and rental searches (deducting other components, excluding searched component, null budget handling).

### Automated verification
- [x] `.\mvnw.cmd test -Dtest=ItineraryTallyEngineTest` — All unit tests pass with 100% assertions on calculation formulas.

### Optional developer checks
- [ ] None.

## Phase 2: Integrate Tally Engine into API Response Models and TripService

### Changes
- [x] `src/main/java/app/detour/trip/DraftResponse.java` — Update record to `(UUID id, long version, DraftSelectionResponse selections, ItineraryTallyResponse tally)` with constructor overloads `DraftResponse(UUID id, long version)` and `DraftResponse(UUID id, long version, DraftSelectionResponse selections)`.
- [x] `src/main/java/app/detour/trip/AlternativeResponse.java` —
  - Update `PlannedResponse` record to `(UUID id, DraftSelectionResponse selections, ItineraryTallyResponse tally)` with overload `PlannedResponse(UUID id, DraftSelectionResponse selections)`.
  - Update `AlternativeResponse` record to `(UUID id, String lifecycle, Long version, DraftSelectionResponse selections, ItineraryTallyResponse tally)` with overload `AlternativeResponse(UUID id, String lifecycle, Long version, DraftSelectionResponse selections)`.
- [x] `src/main/java/app/detour/trip/TripResponse.java` — Update record to include `ItineraryTallyResponse tally` with constructor overloads preserving backward compatibility.
- [x] `src/main/java/app/detour/trip/TripService.java` —
  - Inject `ItineraryTallyEngine tallyEngine` in constructor.
  - In `response(Trip trip, RevisionSummaryResponse revisionSummary)`:
    - Compute `ItineraryTallyResponse` for each `DraftResponse` using `tallyEngine.calculateTally(draft.selections(), trip.travelerCount(), trip.budgetCents())`.
    - Compute `ItineraryTallyResponse` for each `PlannedResponse` using `tallyEngine.calculateTally(item.selections(), trip.travelerCount(), trip.budgetCents())`.
    - Compute `ItineraryTallyResponse` for each `AlternativeResponse` matching its lifecycle item.
    - Compute active trip tally `tripTally` (from primary draft `drafts.get(0)` if present, else primary planned `planned.get(0)`, else empty tally).
    - Pass `tripTally` into `TripResponse`.
  - In `searchStays`: replace inline airfare/rental deduction with `tallyEngine.calculateAvailableStaySearchBudget(trip.budgetCents(), draft != null ? draft.selections() : null, trip.travelerCount())`.
  - In `searchRentals`: replace inline airfare/stay deduction with `tallyEngine.calculateAvailableRentalSearchBudget(trip.budgetCents(), draft != null ? draft.selections() : null, trip.travelerCount())`.
- [x] `frontend/src/api/tripsApi.ts` —
  - Export `type ItineraryTallyResponse = { airfareTotalCents: number; stayTotalCents: number; rentalTotalCents: number; grandTotalCents: number; remainingBudgetCents: number | null; budgetOverageCents: number | null; isOverBudget: boolean; };`.
  - Add optional `tally?: ItineraryTallyResponse;` to `DraftResponse`, `PlannedResponse`, `AlternativeResponse`, and `TripResponse`.

### Automated verification
- [x] `.\mvnw.cmd test-compile` — Java backend compiles without errors.
- [x] `npm.cmd run test` in `frontend/` — All 53 frontend tests pass cleanly with updated API types.

### Optional developer checks
- [ ] None.

## Phase 3: Comprehensive Integration Testing & Verification

### Changes
- [x] `src/test/java/app/detour/trip/TripPricingAndTallyIntegrationTest.java` — New comprehensive integration test suite verifying:
  - Initial trip creation with budget: returns `tally` on `TripResponse` and `DraftResponse` with grand total 0, full remaining budget, overage 0, and `isOverBudget = false`.
  - Initial trip creation without budget: returns `tally` with grand total 0, null remaining budget, null overage, and `isOverBudget = false`.
  - Trip creation with budget = 0: returns `remainingBudgetCents = 0`, `budgetOverageCents = 0`, `isOverBudget = false`.
  - Budget validation rejection: negative budget `-1` and values `> 100_000_000` rejected with 400 `VALIDATION_FAILED`.
  - Airfare selection: adds airfare, recalculates `airfareTotalCents` (`partyTotal = perPerson * travelerCount`), updates `grandTotalCents`, updates `remainingBudgetCents`.
  - Stay selection: adds stay, recalculates `stayTotalCents` (`sum(nights) * unitCount`), updates `grandTotalCents`.
  - Rental selection: adds rental, recalculates `rentalTotalCents` (`billingCycles * dailyRate`), updates `grandTotalCents`.
  - All 7 component combinations (airfare only, stay only, rental only, airfare+stay, stay+car, airfare+car, all three): asserts exact grand totals and component breakdown.
  - Budget overage: when components exceed budget, asserts `remainingBudgetCents = 0`, `budgetOverageCents = grandTotal - budget`, and `isOverBudget = true`.
  - Removal of components: removing stay/rental/airfare decrements totals and restores remaining budget accurately.
  - Component replacement: stay search available budget does not deduct existing stay; rental search available budget does not deduct existing rental.
  - Draft promotion to Planned: `POST /api/trips/{tripId}/drafts/{draftId}/plan` produces `PlannedResponse` with identical snapshot tally.
  - Alternative listing: `TripResponse.alternatives` contains accurate tallies for both DRAFT and PLANNED alternatives.
  - Multi-user isolation: User B attempting to view User A's trip or tallies receives 404 `NOT_FOUND`.
- [x] Update existing tests if needed (`TripApiIntegrationTest.java`, `StaySearchAndSelectionIntegrationTest.java`, `RentalSearchAndSelectionIntegrationTest.java`) to ensure all existing assertions continue to pass seamlessly.

### Automated verification
- [x] `.\mvnw.cmd test` — Full backend test suite passes (including existing 19 test classes + new test classes).
- [x] `npm.cmd run test` in `frontend/` — Full frontend test suite passes (53 tests).

### Optional developer checks
- [ ] Inspect JSON response payload from `GET /api/trips/{tripId}` to confirm shape of `tally` matches specification.

## Test Strategy

- **Unit level (`ItineraryTallyEngineTest.java`):** Fast, isolated tests of pure mathematical functions: airfare party pricing, multi-night stay totals, rental cycle rounding, grand totals, budget remaining/overage boundary conditions, zero-budget edge cases, and search budget deduction logic.
- **Integration level (`TripPricingAndTallyIntegrationTest.java`):** MockMvc tests against H2 database verifying end-to-end HTTP behavior: trip creation, draft mutations (airfare, stay, rental), removal mutations, promotion to planned, multi-alternative tallies, catalog freshness recalculation, budget validation, and multi-user isolation.
- **Regression coverage:** Run the full existing test suites (`TripApiIntegrationTest`, `StaySearchAndSelectionIntegrationTest`, `RentalSearchAndSelectionIntegrationTest`, `AirfareSearchAndSelectionIntegrationTest`) to ensure existing search, selection, and concurrency behaviors are undisturbed.

## Acceptance-Criteria Traceability

| Acceptance criterion | Planned code evidence | Planned test evidence |
| --- | --- | --- |
| 1. Airfare, stay, rental car, and grand totals are calculated on the server and returned in integer cents for all Draft, Planned, and Alternative responses. | `ItineraryTallyEngine.java`, `TripService.response`, `DraftResponse.java`, `PlannedResponse.java`, `AlternativeResponse.java`, `TripResponse.java` | `ItineraryTallyEngineTest`, `TripPricingAndTallyIntegrationTest` |
| 2. Trips with a defined budget return accurate `remainingBudgetCents`, `budgetOverageCents`, and `isOverBudget` indicators matching the server-calculated totals. | `ItineraryTallyEngine.calculateTally`, `TripService.response` | `ItineraryTallyEngineTest.testBudgetPositions`, `TripPricingAndTallyIntegrationTest.testTripBudgetCalculations` |
| 3. Trips without a defined budget return `null` for remaining budget and overage, and omit budget-fit ranking inputs in component searches. | `ItineraryTallyEngine.calculateTally`, `TripService.searchStays`, `TripService.searchRentals` | `ItineraryTallyEngineTest.testNullBudget`, `TripPricingAndTallyIntegrationTest.testNullBudget`, `StaySearchAndSelectionIntegrationTest` |
| 4. Overall budgets of `0` cents are accepted, while negative values and values exceeding `100_000_000` cents are rejected with 400 `VALIDATION_FAILED`. | `TripService.validateBudget`, `V12__create_owned_trip_and_initial_draft_schema.sql` (existing check constraint) | `TripApiIntegrationTest.java`, `TripPricingAndTallyIntegrationTest.testBudgetValidationBoundaries` |
| 5. Available budget in stay and rental car searches accurately deducts other selected components while excluding the component being searched or replaced. | `ItineraryTallyEngine.calculateAvailableStaySearchBudget`, `ItineraryTallyEngine.calculateAvailableRentalSearchBudget`, `TripService.searchStays`, `TripService.searchRentals` | `ItineraryTallyEngineTest.testSearchBudgets`, `StaySearchAndSelectionIntegrationTest`, `TripPricingAndTallyIntegrationTest` |
| 6. Unit and HTTP integration tests prove consistent tally calculations across all combinations of components (airfare only, stay only, rental only, airfare+stay, stay+car, airfare+car, and all three). | `ItineraryTallyEngine.java`, `TripService.java` | `ItineraryTallyEngineTest.testAllComponentCombinations`, `TripPricingAndTallyIntegrationTest.testAllSevenComponentCombinations` |
| 7. Multi-user isolation tests verify that pricing calculations and tally responses are strictly scoped to the authenticated trip owner. | `TripService.ownedTrip`, `TripController.java` | `TripPricingAndTallyIntegrationTest.testMultiUserIsolation`, `TripApiIntegrationTest` |

## Risks and Rollback/Recovery

- **Risk:** Response shape changes could break existing frontend test mocks that do not expect or define `tally`.
  - **Mitigation:** In `tripsApi.ts`, define `tally?: ItineraryTallyResponse;` as optional so mock fixtures omitting `tally` remain type-valid. In Java records, provide overloaded constructors that default `tally` when omitted in tests.
- **Risk:** Discrepancy between rental billing cycle math in `RentalSearchService` vs `ItineraryTallyEngine`.
  - **Mitigation:** Directly reuse the canonical formula `Math.max(1, (totalSeconds + 86399) / 86400)` verified in `RentalSearchAndSelectionIntegrationTest`.
- **Rollback:** All changes are non-destructive and code-only (no database migrations or DDL changes). A git checkout/revert to `main` completely restores previous behavior.

## References
- Ticket: `ai/thoughts/tickets/2026-09-22-p05-t01-canonical-pricing-and-server-tally-engine.md`
- Research: `ai/thoughts/research/2026-09-22-p05-t01-canonical-pricing-and-server-tally-engine.md`
- Source files:
  - `src/main/java/app/detour/trip/TripService.java`
  - `src/main/java/app/detour/trip/DraftResponse.java`
  - `src/main/java/app/detour/trip/AlternativeResponse.java`
  - `src/main/java/app/detour/trip/TripResponse.java`
  - `src/main/java/app/detour/trip/PlannedItinerary.java`
  - `src/main/java/app/detour/trip/JdbcTripRepository.java`
  - `frontend/src/api/tripsApi.ts`
