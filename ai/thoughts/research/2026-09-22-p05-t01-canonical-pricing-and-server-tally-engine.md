---
date: 2026-09-22
repository: loomspan-travel-demo
branch: main
commit: de1c5255920a8d2131af1c280fcb267de070ff83
ticket: ai/thoughts/tickets/2026-09-22-p05-t01-canonical-pricing-and-server-tally-engine.md
tags: [phase-5, pricing, tally-engine, budget, airfare, stays, rentals]
---

# Canonical Pricing and Server Tally Engine Research

## Research Question

How does the current DeTour server represent, validate, and calculate pricing and budget metrics across Trips, Drafts, Planned itineraries, and component searches? What components, schemas, calculations, tests, and API contracts currently exist, and how are prices derived and exposed across draft and planned lifecycle states?

## Summary

In the current codebase, financial values are uniformly represented in integer cents (USD), and budget limits are validated between `0` and `100_000_000` cents ($1,000,000.00). However, component pricing and tally calculations are fragmented across individual search services (`AirfareSearchService`, `StaySearchService`, `RentalSearchService`), ad-hoc inline math in `TripService` for stay and rental car searches, and a client-side calculator in `ItinerarySummaryTally.tsx`. There is no centralized server-side tally engine or canonical pricing service.

Furthermore, current API responses (`DraftResponse`, `PlannedResponse`, `AlternativeResponse`, and `TripResponse`) return component selection facts but omit authoritative tally summaries (`airfareTotalCents`, `stayTotalCents`, `rentalTotalCents`, `grandTotalCents`, `remainingBudgetCents`, `budgetOverageCents`, and `isOverBudget`).

Draft selections store only foreign keys to catalog items (e.g. flight instances, accommodation units, rental units) and temporal/quantity parameters. Prices for drafts are dynamically reconstructed from live catalog fixtures upon query. Planned itineraries store price snapshots captured at creation time in dedicated snapshot tables.

## Repository State

- **Date:** 2026-09-22
- **Repository:** `loomspan-travel-demo`
- **Branch:** `main`
- **Commit:** `de1c5255920a8d2131af1c280fcb267de070ff83`
- **Working Tree:** Clean (no uncommitted modifications).
- **Test Suite Status:**
  - Backend: 101 tests across 19 test classes passing (0 failures, 0 errors, 0 skipped).
  - Frontend: 53 tests across 5 Vitest suites passing (0 failures).

## Current Behavior and Data Flow

### 1. Trip Creation and Budget Validation
- Trips are created via `POST /api/trips` and updated via `PUT /api/trips/{tripId}` or `POST /api/trips/{tripId}/duplicate` (`TripController.java:31-55`).
- The payload is parsed in `TripRequests.java:42-61` and validated in `TripService.java:70-87,169-197`.
- Budget validation occurs in `TripService.validateBudget`:
  - `null` or missing budget is permitted (represented as `null`).
  - Non-integral values are rejected with 400 `VALIDATION_FAILED`.
  - Values `< 0` or `> 100_000_000` cents are rejected with 400 `VALIDATION_FAILED`.
  - Exactly `0` cents is valid.
- The underlying database table `detour_trip` enforces this with `ck_detour_trip_budget CHECK (budget_cents IS NULL OR budget_cents BETWEEN 0 AND 100000000)` (`V12__create_owned_trip_and_initial_draft_schema.sql:17`).

### 2. Component Selection and Pricing Storage in Drafts
Draft selections are stored in three separate relational tables (`V14__create_draft_selection_and_planned_snapshot_schema.sql:4-31`):
- `detour_trip_draft_airfare_selection`: stores `draft_id`, `outbound_flight_instance_id`, `return_flight_instance_id`. No price columns exist in this table.
- `detour_trip_draft_stay_selection`: stores `draft_id`, `accommodation_unit_id`, `unit_count`. No price columns exist in this table.
- `detour_trip_draft_rental_selection`: stores `draft_id`, `rental_unit_id`, `pickup_at`, `return_at`. No price columns exist in this table.

When drafts are loaded via `JdbcTripRepository.loadDraftSelections` (`JdbcTripRepository.java:89-160`):
- **Airfare:** Joins `flight_instance` for outbound and return legs to read live `base_fare_cents`, `tax_cents`, and `fee_cents`.
- **Stay:** Queries `accommodation_nightly_inventory` for the selected `accommodation_unit_id` across `startDate <= night_date < endDate` to read live `base_price_cents`, `tax_cents`, and `fee_cents` for each night.
- **Rental:** Joins `rental_vehicle_class` via `rental_unit` to read live `daily_base_price_cents`, `daily_tax_cents`, and `daily_fee_cents`.

Because prices are not persisted in draft selection tables, retrieving or mutating a draft always queries live catalog tables, naturally preserving price freshness against catalog updates.

### 3. Pricing Storage in Planned Itineraries
When a draft is promoted via `POST /api/trips/{tripId}/drafts/{draftId}/plan` (`TripService.java:277-292`), `JdbcTripRepository.insertPlanned` captures frozen prices into snapshot tables:
- `detour_planned_airfare_snapshot`: stores `outbound_base_fare_cents`, `outbound_tax_cents`, `outbound_fee_cents`, `return_base_fare_cents`, `return_tax_cents`, and `return_fee_cents`.
- `detour_planned_stay_snapshot` and `detour_planned_stay_night_snapshot`: store `unit_count` and nightly `base_price_cents`, `tax_cents`, `fee_cents` per night.
- `detour_planned_rental_snapshot`: stores `daily_base_price_cents`, `daily_tax_cents`, and `daily_fee_cents`.

When planned itineraries are loaded via `JdbcTripRepository.loadPlannedSelections` (`JdbcTripRepository.java:162-171`), prices are read directly from these snapshot tables without live catalog joins.

### 4. Component Search Available Budget Flow
When users search for components within a draft or trip:
- **Airfare Search (`TripService.searchAirfare`):** Invokes `AirfareSearchService.search`. No available budget parameter is computed or passed. Airfare sorting does not include budget-fit ranking.
- **Stay Search (`TripService.searchStays`, lines 646-673):**
  - If `trip.budgetCents() == null`, passes `availableBudgetCents = null`.
  - If `trip.budgetCents() != null`, calculates:
    - `selectedAirfareTotal`: per-person round-trip fare `* trip.travelerCount()`.
    - `selectedCarTotal`: billing cycles `* dailyRate`.
    - `availableBudgetCents = trip.budgetCents() - selectedAirfareTotal - selectedCarTotal`.
    - Note: It does not subtract existing stay cost if a stay is already selected in the draft.
  - Passes `availableBudgetCents` to `StaySearchService.search`, which flags each candidate with `fitsBudget` and uses it as Tier 1 in `DEFAULT` sort.
- **Rental Search (`TripService.searchRentals`, lines 788-809):**
  - If `trip.budgetCents() == null`, passes `availableBudgetCents = null`.
  - If `trip.budgetCents() != null`, calculates:
    - `selectedAirfareTotal`: per-person round-trip fare `* trip.travelerCount()`.
    - `selectedStayTotal`: sum of nightly room rates `* unitCount`.
    - `availableBudgetCents = trip.budgetCents() - selectedAirfareTotal - selectedStayTotal`.
    - Note: It does not subtract existing rental cost if a rental is already selected in the draft.
  - Passes `availableBudgetCents` to `RentalSearchService.search`, which flags each candidate with `fitsBudget`.

### 5. API Responses
In `TripService.response` (`TripService.java:520-533`):
- `DraftResponse` contains `id`, `version`, and `selections`.
- `PlannedResponse` contains `id` and `selections`.
- `AlternativeResponse` contains `id`, `lifecycle`, `version`, and `selections`.
- `TripResponse` contains trip metadata, `drafts`, `planned`, `alternatives`, and optional `revisionSummary`.
- None of these DTOs currently expose calculated component totals, grand total, remaining budget, overage, or `isOverBudget`.

## Key Components

- `src/main/java/app/detour/trip/TripService.java:38,497-505` — `MAX_BUDGET_CENTS` constant (`100_000_000L`) and `validateBudget` enforcing bounds `0..100_000_000`.
- `src/main/java/app/detour/trip/TripService.java:520-533` — `response` assembling `TripResponse`, `DraftResponse`, `PlannedResponse`, and `AlternativeResponse` without tally summaries.
- `src/main/java/app/detour/trip/TripService.java:646-673` — `searchStays` inline calculation of available search budget deducting airfare and rental, excluding stay.
- `src/main/java/app/detour/trip/TripService.java:788-809` — `searchRentals` inline calculation of available search budget deducting airfare and stay, excluding rental.
- `src/main/java/app/detour/trip/JdbcTripRepository.java:89-160` — `loadDraftSelections` joining catalog tables (`flight_instance`, `accommodation_nightly_inventory`, `rental_vehicle_class`) to populate pricing fields.
- `src/main/java/app/detour/trip/JdbcTripRepository.java:162-171` — `loadPlannedSelections` reading from static snapshot tables.
- `src/main/java/app/detour/trip/DraftResponse.java:5-7` — Record definition: `DraftResponse(UUID id, long version, DraftSelectionResponse selections)`.
- `src/main/java/app/detour/trip/AlternativeResponse.java:9-19` — Record definitions: `AlternativeResponse`, `PlannedResponse`, `DraftSelectionResponse`, and individual component response DTOs.
- `src/main/java/app/detour/trip/TripResponse.java:7-19` — Record definition: `TripResponse` with trip metadata and alternative lists.
- `src/main/java/app/detour/trip/PlannedItinerary.java:15-34` — Domain records: `DraftSelections`, `AirfareSelection`, `StaySelection`, `StayNight`, `RentalSelection`, and `PlannedItinerary`.
- `src/main/java/app/detour/airfare/AirfareSearchService.java:49-69` — Airfare pricing formulas: outbound + return per traveler, multiplied by party count.
- `src/main/java/app/detour/stay/StaySearchService.java:78-117,120-123` — Stay pricing formulas: nightly base/tax/fee sum multiplied by room count, and budget-fit calculation.
- `src/main/java/app/detour/rental/RentalSearchService.java:141-150` — Rental car pricing formulas: consecutive 24-hour cycle math `Math.max(1, (totalSeconds + 86399) / 86400)` and daily rate breakdown.
- `src/main/resources/db/migration/V12__create_owned_trip_and_initial_draft_schema.sql:17` — Database check constraint `ck_detour_trip_budget` enforcing `budget_cents IS NULL OR budget_cents BETWEEN 0 AND 100000000`.
- `src/main/resources/db/migration/V14__create_draft_selection_and_planned_snapshot_schema.sql:1-97` — Tables for draft selection references and planned snapshots.
- `frontend/src/components/ItinerarySummaryTally.tsx:15-58` — Client-side calculation functions: `computeAirfareTotalCents`, `computeStayTotalCents`, `computeRentalTotalCents`, `grandTotal`, `remainingCents`, and `overageCents`.
- `frontend/src/api/tripsApi.ts:297-359` — Frontend TypeScript interfaces matching current backend DTO structures.

## Affected Areas

| Area | Current behavior and evidence |
| --- | --- |
| **Pricing & Tally Engine** | Currently nonexistent on the server. Math exists as isolated fragments across `AirfareSearchService` (lines 49-69), `StaySearchService` (lines 78-117), `RentalSearchService` (lines 141-150), inline in `TripService` (lines 646-673, 788-809), and in frontend `ItinerarySummaryTally.tsx` (lines 15-58). |
| **Draft, Planned, and Alternative Responses** | `DraftResponse`, `PlannedResponse`, and `AlternativeResponse` expose `selections` only. Tally summary metrics (`airfareTotalCents`, `stayTotalCents`, `rentalTotalCents`, `grandTotalCents`, `remainingBudgetCents`, `budgetOverageCents`, `isOverBudget`) are absent from these responses. |
| **Trip Response** | `TripResponse` exposes `drafts`, `planned`, and `alternatives` lists, but does not provide an authoritative tally summary for the primary/active itinerary. |
| **Component Search Available Budget** | `TripService.searchStays` and `TripService.searchRentals` compute available budget via inline calculations. Airfare search does not receive or compute available budget. When budget is `null`, `availableTripBudgetCents` is `null`. When a component is already selected, its cost is excluded from the search budget. |
| **Budget Validation Boundaries** | Handled in `TripService.validateBudget` and enforced in SQL `detour_trip`. Rejects negative budgets and values exceeding `100_000_000` cents with 400 `VALIDATION_FAILED`. Allows `0` and `null`. |
| **Catalog Price Freshness vs Immutability** | Drafts hold foreign keys only (`V14`). Queries join live catalog tables (`JdbcTripRepository.loadDraftSelections`), ensuring price freshness. Planned itineraries read from frozen snapshot tables (`JdbcTripRepository.loadPlannedSelections`), ensuring immutability. |
| **Frontend API Contracts** | `frontend/src/api/tripsApi.ts` types currently define `DraftResponse`, `PlannedResponse`, `AlternativeResponse`, and `TripResponse` without `tally` fields. |

## Existing Tests and Fixtures

- `src/test/java/app/detour/trip/TripApiIntegrationTest.java`:
  - Lines 64, 73: Verifies budget of `0` cents is accepted and returned.
  - Lines 115-119: Verifies budget validation rejects `-1`, `100000001`, decimals (`1.5`), scientific notation (`1e3`), and strings (`"100"`).
  - Lines 139, 143: Verifies maximum budget `100000000` is accepted.
  - Lines 225, 231: Verifies clearing/setting budget to `null`.
  - Multi-user isolation tests verify that trips and drafts are isolated to the authenticated user.
- `src/test/java/app/detour/trip/StaySearchAndSelectionIntegrationTest.java`:
  - Lines 304-342: Verifies stay search available budget deduction: initial budget (200k), deduction after airfare selection (200k - flight total), and replacing an existing stay does not deduct the existing stay.
  - Lines 345-369: Verifies budget tiering (within budget ranked before over budget in DEFAULT sort).
  - Lines 372-390: Verifies absent budget (`budgetCents = null`) sets `availableTripBudgetCents = null` and omits budget tiering.
- `src/test/java/app/detour/trip/RentalSearchAndSelectionIntegrationTest.java`:
  - Lines 267-306: Verifies 24-hour billing cycle calculation and partial cycle round-up: 24h -> 1 cycle; 24h 1s -> 2 cycles; 25h -> 2 cycles; 48h -> 2 cycles; 49h -> 3 cycles.
- `src/test/java/app/detour/trip/AirfareSearchAndSelectionIntegrationTest.java`:
  - Verifies round-trip flight search, seat checks, and selection persistence.
- **Gaps in existing tests:**
  - No existing backend tests assert server-calculated `airfareTotalCents`, `stayTotalCents`, `rentalTotalCents`, `grandTotalCents`, `remainingBudgetCents`, `budgetOverageCents`, or `isOverBudget` on `DraftResponse`, `PlannedResponse`, `AlternativeResponse`, or `TripResponse`.
  - No tests currently verify tally calculation across all 7 non-empty component combinations (airfare only, stay only, rental only, airfare+stay, stay+car, airfare+car, and all three) or component-empty drafts.

## Dependencies and Operational Constraints

1. **Integer Cents Representation:** All monetary totals and rates must remain non-negative integer cents (`long`/`Long`), with no floating-point arithmetic.
2. **Deterministic Formulas:**
   - Airfare total: `(outbound per-traveler fare + return per-traveler fare) * travelerCount`, where each leg fare is `base_fare_cents + tax_cents + fee_cents`.
   - Stay total: sum of nightly rates (`base_price_cents + tax_cents + fee_cents`) for all nights across the trip interval multiplied by `unitCount`.
   - Rental car total: `billingCycles * (daily_base_price_cents + daily_tax_cents + daily_fee_cents)`, where billing cycles is `Math.max(1, (totalSeconds + 86399) / 86400)`.
   - Grand total: sum of selected component totals.
3. **Budget Position Rules:**
   - If `trip.budgetCents != null`:
     - If `grandTotal <= budgetCents`: `remainingBudgetCents = budgetCents - grandTotal`, `budgetOverageCents = 0`, `isOverBudget = false`.
     - If `grandTotal > budgetCents`: `remainingBudgetCents = 0`, `budgetOverageCents = grandTotal - budgetCents`, `isOverBudget = true`.
   - If `trip.budgetCents == null`:
     - `remainingBudgetCents = null`, `budgetOverageCents = null`, `isOverBudget = false`.
4. **Data Freshness vs Snapshot Immutability:**
   - Draft selections must recalculate component prices from live catalog tables upon retrieval and mutation.
   - Planned itineraries must preserve frozen snapshot prices.
5. **Multi-User Scoping:** All pricing lookups and tally responses must remain strictly scoped to the authenticated trip owner.
6. **Clean-Break Policy:** No compatibility aliases, legacy fallbacks, or transitional wrappers.

## Historical Context

- **Roadmap Context:** `ai/thoughts/phases/README.md` lines 104-112 established the budget behavior: overall budget is a planning target, 0 is valid, negative is invalid, upper bound must be safely representable in integer cents, screens show tally, actual selected total, and remaining budget/overage, and component search excludes the component being searched or replaced.
- **Phase 5 Work Packages:** `ai/thoughts/phases/phase-5-planning-budget-and-comparison.md` defines Work Package 5.1 (Implement canonical price calculation) as the prerequisite foundation for Work Package 5.2 (Validate readiness) and Work Package 5.3 (Save stable Planned snapshots).
- **Ticket P04-T04:** Delivered the progressive trip builder where frontend `ItinerarySummaryTally.tsx` temporarily performed client-side tally calculations. Ticket P05-T01 moves this responsibility to the server so that downstream tickets P05-T02 (readiness revalidation/overage acknowledgment), P05-T03 (promotion UI), and P05-T04 (comparison matrix) consume authoritative server-calculated tallies.

## Open Questions

Matters for planning to address:
1. **Response Contract Format:** Whether `airfareTotalCents`, `stayTotalCents`, `rentalTotalCents`, `grandTotalCents`, `remainingBudgetCents`, `budgetOverageCents`, and `isOverBudget` should be represented via an embedded `tally: ItineraryTallyResponse` object across `DraftResponse`, `PlannedResponse`, `AlternativeResponse`, and `TripResponse`, or as flat top-level fields, or both (e.g. embedded object with top-level convenience accessors).
2. **Engine Architecture and Separation of Concerns:** Whether the canonical calculation engine should be encapsulated in a dedicated service/component (e.g. `ItineraryTallyService` or `PricingEngine`) that can be cleanly injected into `TripService` and directly consumed by future readiness validation (`P05-T02`).
3. **TripResponse Tally Semantics:** In trips containing multiple draft or planned alternatives, what `TripResponse.tally` should represent (e.g., the primary/active draft `drafts.get(0)`).
