---
date: 2026-09-21
repository: loomspan-travel-demo
branch: main
commit: 7e785fcf2b044040a44db90d112dff106fe39d81
ticket: ai/thoughts/tickets/2026-09-21-p04-t03-rental-car-search-and-draft-selection.md
tags: [phase-4, rental-cars, search, draft-selection, pricing, 24-hour-billing, driver-eligibility, inventory-occupancy, optimistic-concurrency]
---

# Rental Car Search and Draft Selection Research

## Research Question

How does the current codebase support airport rental car catalog querying, half-open interval occupancy checking against `rental_unit_occupancy`, destination airport timezone interval bounding, 25+ driver eligibility validation and explanation, 24-hour billing cycle price calculation, deterministic vehicle class ranking, and draft rental selection persistence and removal with optimistic concurrency and cross-user authorization?

## Summary

The repository contains a fully defined relational schema and deterministic catalog fixtures for airport rental cars across supported destinations (SFO, MUC, MEX) seeded in Phase 2 (`V4`, `V11`). The catalog organizes car inventory into `rental_location` (tied to `catalog_destination` and `catalog_airport`), `rental_vehicle_class` (categorized as `ECONOMY`, `STANDARD`, or `SUV` with daily base price, tax, and fee cents), and physical vehicle units in `rental_unit`. Concurrency and half-open active interval exclusivity are enforced at the database level by `RentalUnitOccupancyOverlapTrigger` on `rental_unit_occupancy`.

In Phase 3, persistence structures for draft selections (`detour_trip_draft_rental_selection`) and planned snapshots (`detour_planned_rental_snapshot`) were created in `V14`. `JdbcTripRepository` already loads `RentalSelection` on draft reads (`loadDraftSelections:138-158`), snapshots rentals during promotion (`insertPlanned:224`), copies rental selections across draft duplication (`insertDraftSelections:217`), removes draft selections (`deleteDraftRentalSelection:261`), and validates rentals during trip detail updates (`revalidateRental:416-454`). Furthermore, `TripService:645-658` already demonstrates the 24-hour billing cycle calculation formula `(seconds + 86399) / 86400 * dailyRate`.

However, no rental search service, repository, or HTTP controller endpoints exist yet (`app.detour.rental` package has not been created). `JdbcTripRepository` does not yet expose `saveDraftRentalSelection`, `TripRequests` lacks a parser for `RentalSelectionRequest`, and no integration test suite currently tests rental car search, driver age gating, 24-hour cycle pricing math, half-open interval occupancy filtering, ranking determinism, or selection mutations.

## Repository State

- **Date:** 2026-09-21
- **Repository:** `loomspan-travel-demo`
- **Current Branch:** `main`
- **Current Commit:** `7e785fcf2b044040a44db90d112dff106fe39d81`
- **Working Tree Status:** Clean, no modified or untracked files.
- **Build & Verification Status:** All existing integration and unit tests pass (`StaySearchAndSelectionIntegrationTest`, `AirfareSearchAndSelectionIntegrationTest`, `TripApiIntegrationTest`, `RentalUnitOccupancyConstraintIntegrationTest`, `CatalogFixtureIntegrityAssertions`, etc.).

## Current Behavior and Data Flow

1. **Rental Catalog Schema & Fixtures:**
   - `rental_location` (`V4`, `V11`): Maps each supported destination to its destination airport rental facility (`destination_id`, `airport_id`, `name`). Seeded in `V11` for SFO (`Harborline Mobility at SFO`), MUC (`Alpine Roadworks at MUC`), and MEX (`Círculo Drive at MEX`).
   - `rental_vehicle_class` (`V4`, `V11`): Represents vehicle categories (`ECONOMY`, `STANDARD`, `SUV`) tied to a supplier and location. Holds `daily_base_price_cents`, `daily_tax_cents`, and `daily_fee_cents`. Seeded in `V11` with 3 vehicle classes per destination (9 total classes). Daily rates:
     - SFO: Economy ($42.77 base + $3.43 tax + $1.80 fee = $48.00 total/day), Standard ($63.33 + $5.07 + $2.60 = $71.00 total/day), SUV ($91.85 + $7.35 + $3.80 = $103.00 total/day).
     - MUC: Economy ($40.00 + $3.20 + $1.80 = $45.00 total/day), Standard ($57.77 + $4.63 + $2.60 = $65.00 total/day), SUV ($85.37 + $6.83 + $3.80 = $96.00 total/day).
     - MEX: Economy ($32.59 + $2.61 + $1.80 = $37.00 total/day), Standard ($48.51 + $3.89 + $2.60 = $55.00 total/day), SUV ($72.40 + $5.80 + $3.80 = $82.00 total/day).
   - `rental_unit` (`V4`, `V11`): Represents specific physical bookable vehicles (`rental_vehicle_class_id`, `catalog_key`, `unit_identifier`). Seeded in `V11` with 21 physical units across the 3 destinations (SFO: 3 Economy, 2 Standard, 2 SUV; MUC: 2 Economy, 3 Standard, 2 SUV; MEX: 2 Economy, 2 Standard, 3 SUV).
   - `rental_unit_occupancy` (`V4`): Records vehicle reservations (`rental_unit_id`, `pickup_at`, `return_at`, `occupancy_status` IN (`'ACTIVE'`, `'RELEASED'`)). Initially contains 0 rows in baseline fixtures.
   - `RentalUnitOccupancyOverlapTrigger` (`V4`, Java H2 trigger): Enforces that no two `ACTIVE` rows for the same `rental_unit_id` overlap on half-open interval `[pickup_at, return_at)`. Trigger query in `RentalUnitOccupancyOverlapTrigger:30-38`:
     ```sql
     SELECT 1 FROM rental_unit_occupancy
     WHERE rental_unit_id = ?
       AND occupancy_status = 'ACTIVE'
       AND id <> ?
       AND pickup_at < ?
       AND ? < return_at
     ```
     Two intervals $[A_{start}, A_{end})$ and $[B_{start}, B_{end})$ overlap if and only if $A_{start} < B_{end}$ and $B_{start} < A_{end}$.

2. **Destination Airport & Timezone Bounding:**
   - `catalog_airport` (`V3`, `V10`): Contains IANA `time_zone_id` for every airport:
     - SFO (`airport-sfo`): `America/Los_Angeles`
     - MUC (`airport-muc`): `Europe/Berlin`
     - MEX (`airport-mex`): `America/Mexico_City`
   - Ticket requirement: Pickup and return date/times must fall within the Trip's start and end date interval in the destination airport's local timezone (`[startDate 00:00, endDate 23:59]`).
   - Translating an incoming `OffsetDateTime` into destination timezone:
     `ZonedDateTime localPickup = pickupAt.atZoneSameInstant(destZoneId);`
     `ZonedDateTime localReturn = returnAt.atZoneSameInstant(destZoneId);`
   - Validation rules:
     - Require return strictly after pickup: `pickupAt < returnAt` (i.e. `!pickupAt.isBefore(returnAt)` is rejected).
     - In destination airport's local timezone:
       - `localPickup.toLocalDate().isBefore(trip.startDate())` or `localPickup.toLocalDate().isAfter(trip.endDate())` is rejected.
       - `localReturn.toLocalDate().isBefore(trip.startDate())` or `localReturn.toLocalDate().isAfter(trip.endDate())` is rejected.

3. **Driver Eligibility Rule (25+):**
   - Stored on `Trip` as `List<Integer> travelerAges` (loaded from `detour_trip_traveler` ordered by `traveler_ordinal`). If no traveler ages have been saved on the trip, `travelerAges` is `null`.
   - Eligibility condition: `trip.travelerAges() != null && trip.travelerAges().stream().anyMatch(age -> age != null && age >= 25)`.
   - If not eligible (ages missing or no traveler aged 25+):
     - Search response must return `driverEligible: false` (and `selectionDisabled: true`), with actionable explanation:
       *"Rental cars require at least one traveler aged 25 or older. Please add traveler ages in Trip Details to select a car."*
     - Search still returns available vehicle options so travelers can browse inventory and pricing.
     - Draft selection mutation (`PUT /api/trips/{tripId}/drafts/{draftId}/rentals`) must strictly reject saving with HTTP 400 `VALIDATION_FAILED` (field `travelerAges`).

4. **24-Hour Billing Cycle Duration & Pricing Math:**
   - Duration is calculated in consecutive 24-hour billing cycles between `pickupAt` and `returnAt`.
   - Any partial final cycle rounds up to a full 24-hour cycle:
     $$\text{totalSeconds} = \text{Duration.between(pickupAt, returnAt).getSeconds()}$$
     $$\text{billingCycles} = \frac{\text{totalSeconds} + 86399}{86400}$$
     (e.g., 24 hours = 1 cycle; 24 hours 1 second = 2 cycles; 25 hours = 2 cycles; 48 hours = 2 cycles; 49 hours = 3 cycles).
   - Rate components from `rental_vehicle_class`:
     - `dailyBasePriceCents`, `dailyTaxCents`, `dailyFeeCents`
     - `dailyTotalPriceCents = dailyBasePriceCents + dailyTaxCents + dailyFeeCents`
   - Itemized totals:
     - `totalBasePriceCents = billingCycles * dailyBasePriceCents`
     - `totalTaxCents = billingCycles * dailyTaxCents`
     - `totalFeeCents = billingCycles * dailyFeeCents`
     - `totalPriceCents = billingCycles * dailyTotalPriceCents` (USD integer cents).

5. **Deterministic Search Ranking:**
   - Results are deterministically ranked by:
     1. Vehicle class: `ECONOMY` first, then `STANDARD`, then `SUV`.
     2. Lowest complete total price (`totalPriceCents` ascending).
     3. Vehicle unit catalog key (`rental_unit.catalog_key` ascending, e.g. `...-01` before `...-02`).

6. **Draft Rental Selection Persistence & Concurrency:**
   - Table `detour_trip_draft_rental_selection` (`V14`): Primary key is `draft_id`. Columns: `rental_unit_id`, `pickup_at`, `return_at`.
   - Mutation atomically replaces any prior selection for the draft (`DELETE` followed by `INSERT`).
   - Removal deletes the draft row (`DELETE FROM detour_trip_draft_rental_selection WHERE draft_id = ?`).
   - Concurrency: `JdbcTripRepository.advanceVersionForDraftMutation` atomically checks `expectedVersion` on `detour_trip` and `expectedDraftVersion` on `detour_trip_draft`, incrementing both versions upon success. If versions do not match, returns 409 `VERSION_CONFLICT`.
   - On success, returns updated `TripResponse` where `selections.rental` contains full `RentalComponentResponse`.

7. **Available Trip Budget Interaction:**
   - In `TripService:634-660`, `availableBudgetCents` is computed for stay search by subtracting selected airfare and selected rental totals.
   - When calculating available budget for car search, existing draft car selections must NOT be subtracted from the available budget (allowing seamless replacement), while airfare and stays are subtracted.

8. **Authorization and Error Handling:**
   - Cross-user isolation: `TripService.ownedTrip(ownerUserId, tripId)` and `ownedDraft(trip, draftId)` verify user ownership against authenticated principal. Unauthorized or nonexistent requests return 404 `RESOURCE_NOT_FOUND` without leaking resource existence.

## Key Components

- `src/main/resources/db/migration/V4__create_catalog_inventory_schema.sql:54-114` — Schema definitions for `rental_location`, `rental_vehicle_class`, `rental_unit`, and `rental_unit_occupancy` with trigger `RentalUnitOccupancyOverlapTrigger`.
- `src/main/resources/db/migration/V11__seed_march_2027_stay_and_rental_catalog.sql:84-128` — Deterministic seed data for 3 rental locations, 9 vehicle classes, and 21 physical units.
- `src/main/resources/db/migration/V14__create_draft_selection_and_planned_snapshot_schema.sql:23-31,81-96` — Persistence tables for `detour_trip_draft_rental_selection` and `detour_planned_rental_snapshot`.
- `src/main/java/app/detour/catalog/persistence/RentalUnitOccupancyOverlapTrigger.java:1-61` — H2 database trigger enforcing half-open active occupancy interval non-overlap.
- `src/main/java/app/detour/trip/PlannedItinerary.java:28-30` — Domain record `RentalSelection(rentalUnitId, pickupAt, returnAt, locationName, vehicleClassName, unitIdentifier, dailyBasePriceCents, dailyTaxCents, dailyFeeCents)`.
- `src/main/java/app/detour/trip/AlternativeResponse.java:17-18` — API response record `RentalComponentResponse`.
- `frontend/src/api/tripsApi.ts:61-71,76` — Frontend TypeScript definitions for `RentalComponentResponse` and `DraftSelectionResponse.rental`.
- `src/main/java/app/detour/trip/JdbcTripRepository.java:138-158` — Queries `detour_trip_draft_rental_selection` joined with class and location into `RentalSelection`.
- `src/main/java/app/detour/trip/JdbcTripRepository.java:176-190` — Atomic optimistic version increment `advanceVersionForDraftMutation`.
- `src/main/java/app/detour/trip/JdbcTripRepository.java:217,224,245-249,261` — Persistence methods for copying, snapshotting, resolving, and deleting draft rental selections.
- `src/main/java/app/detour/trip/JdbcTripRepository.java:416-454` — Trip shared detail update revalidation method `revalidateRental`.
- `src/main/java/app/detour/trip/TripService.java:523-529` — Maps `RentalSelection` into `RentalComponentResponse` on `TripResponse`.
- `src/main/java/app/detour/trip/TripService.java:645-658` — Existing calculation of consecutive 24-hour billing cycles `(seconds + 86399) / 86400`.
- `src/main/java/app/detour/trip/TripRequests.java:1-202` — Request validation and parsing for trip and draft mutations.
- `src/main/java/app/detour/trip/TripController.java:1-194` — Spring RestController handling trip and draft endpoints.

## Affected Areas

| Area | Current behavior and evidence |
| --- | --- |
| **Rental Search Service & Repository** | No rental search service or repository exists. Physical units and pricing reside in `rental_unit`, `rental_vehicle_class`, `rental_location`, and `rental_unit_occupancy` (`V4`, `V11`). |
| **Airport Timezone & Interval Validation** | Destination airport timezone is in `catalog_airport.time_zone_id` (`V3`, `V10`). Pickup and return date/times must be validated against `[startDate 00:00, endDate 23:59]` in destination local time, and `pickupAt < returnAt` must be enforced. |
| **Driver Age Gating (25+)** | `detour_trip_traveler` holds traveler ages. `Trip` has `travelerAges`. Search must check for an adult aged 25+; if absent, return disabled status with explicit explanation string. Selection mutation must reject saving. |
| **Half-Open Interval Occupancy Filtering** | `rental_unit_occupancy` records active rentals. Search must filter out any physical unit where an `ACTIVE` occupancy overlaps `[pickupAt, returnAt)`. Half-open boundary abutment (`occ.return_at == search.pickupAt` or `search.returnAt == occ.pickup_at`) must remain available. |
| **24-Hour Billing Cycle Pricing Math** | Pricing requires calculating consecutive 24-hour billing cycles, rounding partial final cycles up to a full cycle `(seconds + 86399) / 86400`. Multiplied by daily base, tax, and fee cents to produce complete totals in USD integer cents. |
| **Deterministic Ranking & Sorting** | Options must default-order by vehicle class (`ECONOMY`, `STANDARD`, `SUV`), then lowest complete total price, then catalog key tie-breaker. |
| **Draft Rental Selection Persistence** | Table `detour_trip_draft_rental_selection` exists (`V14`). `JdbcTripRepository` has `deleteDraftRentalSelection` (line 261), but does not have `saveDraftRentalSelection(long draftId, long rentalUnitId, OffsetDateTime pickupAt, OffsetDateTime returnAt)`. |
| **Optimistic Concurrency on Mutation** | `advanceVersionForDraftMutation` in `JdbcTripRepository:176-190` validates and increments both `detour_trip.version` and `detour_trip_draft.version`. Must be invoked on car selection and removal. |
| **HTTP Controller Endpoints** | `TripController` has airfare and stay endpoints, but lacks endpoints for rental search (`GET /api/trips/{tripId}/drafts/{draftId}/rentals` and `GET /api/trips/{tripId}/rentals`), selection (`PUT /api/trips/{tripId}/drafts/{draftId}/rentals`), and removal (`DELETE /api/trips/{tripId}/drafts/{draftId}/rentals`). |

## Existing Tests and Fixtures

- `StayAndRentalFixtureIntegrationTest.java`:
  - Validates property and rental location catalog seed integrity.
  - Tests inserting active occupancy into `rental_unit_occupancy` and confirms fixture counts.
- `RentalUnitOccupancyConstraintIntegrationTest.java:1-50`:
  - Directly tests `RentalUnitOccupancyOverlapTrigger`.
  - Verifies that active overlapping intervals fail with `DataAccessException` (SQLState 23505), while adjacent half-open intervals (`pickup_at == return_at`) and `RELEASED` occupancies succeed.
- `CatalogFixtureIntegrityAssertions.java:85-110`:
  - Asserts exactly 3 rental locations, 9 vehicle classes, and 21 physical units across SFO, MUC, and MEX.
  - Verifies that every destination has at least 1 unit in each class (`ECONOMY`, `STANDARD`, `SUV`).
  - Asserts that `rental_unit_occupancy` starts with 0 rows in clean fixtures.
- `AirfareSearchAndSelectionIntegrationTest.java` & `StaySearchAndSelectionIntegrationTest.java`:
  - Proven blueprint test suites with 12–15 MockMvc integration tests covering search, validation failures, pricing breakdowns, sort orders, selection persistence, replacing selections, removing selections, version concurrency conflicts (409), and cross-user isolation (404).
- `TripApiIntegrationTest.java`:
  - Line 742 verifies that modifying trip details to remove 25+ travelers triggers `revalidateRental` and removes the car selection with message: *"Rental cars require at least one driver aged 25 or older."*

## Dependencies and Operational Constraints

- **Supported Destinations:** SFO (`destination-sfo`), MUC (`destination-muc`), MEX (`destination-mex`).
- **Destination Airports & Timezones:**
  - SFO: `America/Los_Angeles`
  - MUC: `Europe/Berlin`
  - MEX: `America/Mexico_City`
- **Supported Trip Dates:** March 1–31, 2027; trip duration 1–14 nights. Rental interval `[pickupAt, returnAt]` must fall within `[startDate 00:00, endDate 23:59]` in the destination airport's local timezone.
- **Driver Age Requirement:** At least one traveler on the Trip must be aged 25 or older (`travelerAges.stream().anyMatch(age -> age >= 25)`).
- **Physical Vehicle Classes:** Exactly three supported categories: `ECONOMY`, `STANDARD`, `SUV`.
- **Occupancy Invariant:** Half-open interval `[pickupAt, returnAt)` against active occupancies in `rental_unit_occupancy`.
- **Currency & Money Representation:** USD integer cents (`BIGINT`), non-negative.
- **Optimistic Concurrency:** Both `expectedVersion` (Trip) and `expectedDraftVersion` (Draft) are checked on mutation and both are incremented atomically.
- **Cross-User Isolation:** All endpoints enforce `owner_user_id == principal.userId()`; discrepancies return 404 `RESOURCE_NOT_FOUND` without disclosing data.

## Historical Context

- `ai/thoughts/architecture/2026-09-17-p00-t02-detour-replacement-architecture.md`: Establishes deterministic search and pricing in Java Spring Boot. Prohibits nondeterministic AI or dynamic external pricing outside seeded fixtures.
- `ai/thoughts/phases/phase-2-catalog-and-fixtures.md`: Created normalized schema and seeded deterministic rental catalog with locations, vehicle classes, and 21 physical units.
- `ai/thoughts/phases/phase-3-trips-itineraries-and-profile.md`: Implemented Trip aggregate, multi-draft support, revalidation of draft selections on shared detail updates, and promotion to planned snapshots.
- `ai/thoughts/phases/phase-4-component-selection.md`: Work package 4.4 outlines airport rental car search, 25+ driver eligibility rule, 24-hour billing cycle calculation, half-open interval occupancy filtering, and draft selection persistence.
- `ai/thoughts/tickets/2026-09-21-p04-t01-airfare-search-and-draft-selection.md`: Predecessor ticket implementing airfare search, round-trip combinations, party pricing, deterministic sorting, draft selection, and integration testing.
- `ai/thoughts/tickets/2026-09-21-p04-t02-stay-search-and-draft-selection.md`: Predecessor ticket implementing stay search, room calculation, nightly inventory validation, complete-stay pricing, budget-fit ranking, draft selection, and integration testing.

## Open Questions

1. **Endpoint Path Mapping:** For client and test flexibility, should `TripController` support both plural and singular paths (`/rentals`, `/rental`, `/cars`, `/car`) for `@GetMapping`, `@PutMapping`, and `@DeleteMapping`, mirroring the pattern established for `/stays` and `/stay` in P04-T02?
2. **Search Timestamp Format & Timezone Parsing:** Should `pickupAt` and `returnAt` query parameters accept both ISO offset timestamps (e.g. `2027-03-02T10:00:00-08:00`) and local timestamps (e.g. `2027-03-02T10:00:00`), interpreting local timestamps within the destination airport's local timezone?
3. **Response Representation for Disabled Selection:** In `RentalSearchResponse`, should both boolean flags (`driverEligible`, `selectionDisabled`) and explanation text (`explanation`, `disabledReason`) be included to provide complete compatibility with various client conventions?
4. **Available Trip Budget Presentation:** While car ranking is strictly ordered by vehicle class (Economy, Standard, SUV) and price, should `RentalSearchResponse` include `availableTripBudgetCents` (calculated by subtracting selected airfare and stay totals) and `RentalOptionResponse.fitsBudget` for seamless consumption by P04-T04?

## Step Report: 1_research_codebase
STATUS: complete
ARTIFACTS:
  - ai/thoughts/research/2026-09-21-p04-t03-rental-car-search-and-draft-selection.md
SUMMARY: Completed comprehensive codebase research for rental car search and draft selection across catalog schemas, H2 occupancy triggers, destination airport timezones, 25+ driver eligibility, 24-hour billing cycle pricing, and optimistic locking. All underlying fixtures (V4, V11), draft persistence tables (V14), and domain records exist, while search repositories, services, HTTP controller endpoints, and tests are ready to be planned.
DECISIONS:
  - Identified destination airport IANA time zones in catalog_airport to anchor the [startDate 00:00, endDate 23:59] local interval bounding.
  - Confirmed 24-hour billing cycle formula (seconds + 86399) / 86400 matching existing available budget calculation in TripService.
DEVELOPER QUESTION: none
EVIDENCE: none
RECOMMENDATION: none
NEXT: Proceed to Step 2 & Step 3 in pipeline mode: create implementation plan and testing plan.
