---
date: 2026-09-21
repository: loomspan-travel-demo
branch: main
commit: 69d6f8301b6d594aca5679f802b6275aea2da4e1
ticket: ai/thoughts/tickets/2026-09-21-p04-t02-stay-search-and-draft-selection.md
tags: [phase-4, stays, search, draft-selection, pricing, inventory, optimistic-concurrency]
---

# Stay Search and Draft Selection Research

## Research Question

How does the current codebase support accommodation searching, automatic room calculation, multi-night inventory validation, complete stay pricing with nightly and per-room breakdowns, available budget calculation, deterministic ranking and sort overrides, and draft stay selection persistence and removal with optimistic concurrency and authorization?

## Summary

The repository provides a complete relational schema and deterministic catalog fixtures for accommodations across destinations (SFO, MUC, MEX) for March 2027. Accommodation metadata (property category, coordinates, location description, guest rating, and distance to city center) is stored in `accommodation_property`, bookable room types and whole properties in `accommodation_unit`, and nightly prices and availability in `accommodation_nightly_inventory`.

While Phase 3 implemented revalidation for stays during trip detail revisions (`JdbcTripRepository.revalidateStay`) and snapshotting during promotion to Planned status, no stay search service, repository, or HTTP controller endpoints exist yet. Furthermore, draft stay selection loading in `JdbcTripRepository.loadDraftSelections` only populates unit ID and unit count with null property and unit names and empty nights. Draft stay selection mutations (`saveDraftStaySelection`) and controller endpoints (`GET/PUT/DELETE /api/trips/{tripId}/drafts/{draftId}/stay` and `GET /api/trips/{tripId}/stays`) need to be introduced following the pattern established for airfare in P04-T01.

## Repository State

- **Date:** 2026-09-21
- **Repository:** `loomspan-travel-demo`
- **Current Branch:** `main`
- **Current Commit:** `69d6f8301b6d594aca5679f802b6275aea2da4e1`
- **Working Tree Status:** Clean, no modified or untracked files.
- **Build & Verification Status:** All existing integration and unit tests pass (`AirfareSearchAndSelectionIntegrationTest`, `TripApiIntegrationTest`, `CatalogFixtureIntegrityAssertions`, etc.).

## Current Behavior and Data Flow

1. **Accommodation Catalog & Inventory Schema:**
   - `accommodation_property` (`V4`, `V5`, `V11`): Stores lodging properties classified as `HOTEL`, `BED_AND_BREAKFAST`, or `VACATION_RENTAL`. Search metadata includes `guest_rating` (DECIMAL 2,1 between 0 and 5), `distance_to_city_center_meters` (INTEGER >= 0), `location_description` (VARCHAR 500), `latitude`, and `longitude`.
   - `accommodation_unit` (`V4`, `V11`): Stores room types (`unit_kind = 'ROOM'`) for hotels and B&Bs, and whole properties (`unit_kind = 'WHOLE_PROPERTY'`) for vacation rentals. Holds `guest_capacity` and `inventory_capacity`.
   - `accommodation_nightly_inventory` (`V4`, `V11`): Stores day-by-day availability and money breakdown (`base_price_cents`, `tax_cents`, `fee_cents`, `available_inventory`, `inventory_capacity`) for 30 nights (2027-03-01 through 2027-03-30) per unit.
   - Catalog data in `V11`: Exactly 2 properties per category per destination (6 properties per destination; 18 total properties and 18 total units across SFO, MUC, and MEX; 540 total nightly inventory rows).

2. **Room Count & Capacity Calculations:**
   - In `JdbcTripRepository.revalidateStay:318-335`, room calculation logic already exists:
     - For whole property (`VACATION_RENTAL`): `requiredRooms = 1`. If `travelerCount > unit.guestCapacity()`, the stay is ineligible.
     - For rooms (`HOTEL`, `BED_AND_BREAKFAST`): `requiredRooms = (int) Math.ceil((double) travelerCount / unit.guestCapacity())`.
   - Nightly inventory validation checks that for all nights in the half-open date range `[startDate, endDate)`, `available_inventory >= requiredRooms`. Any unit with fewer available nights or insufficient inventory on any night is excluded.

3. **Complete Stay Pricing:**
   - Complete stay price is calculated in USD integer cents as:
     $$\text{totalPriceCents} = \text{requiredRooms} \times \sum_{night \in [startDate, endDate)} (base\_price\_cents + tax\_cents + fee\_cents)$$
   - Transparent breakdown requires per-room sums and per-night itemized amounts.

4. **Available Trip Budget Calculation:**
   - `trip.budgetCents` represents the overall trip budget in USD integer cents (optional, nullable).
   - If `trip.budgetCents` is null, available budget is undefined (`null`).
   - If defined, available trip budget is:
     $$\text{availableBudget} = \text{trip.budgetCents} - \text{selectedAirfareTotal} - \text{selectedCarTotal}$$
   - When replacing an existing stay selection on a draft, the existing stay cost is NOT subtracted from the budget.
   - `selectedAirfareTotal`: Calculated from `draft.selections().airfare()` as `travelerCount * (outboundBase + outboundTax + outboundFee + returnBase + returnTax + returnFee)`.
   - `selectedCarTotal`: Calculated from `draft.selections().rental()` if present.

5. **Search Ranking and Sorting:**
   - **Default Ranking:**
     - If budget is defined: Tier 1 (options where `totalPrice <= availableBudget`) appears before Tier 2 (options where `totalPrice > availableBudget`).
     - Within each tier (or across all options if budget is null):
       1. Highest guest rating (`guest_rating` descending);
       2. Lowest complete-stay price (`totalPrice` ascending);
       3. Nearest city center (`distance_to_city_center_meters` ascending);
       4. Property catalog key tie-breaker (`catalog_key` ascending).
   - **Sort Overrides:**
     - `LOWEST_PRICE`: `totalPrice` ascending, then `catalog_key` ascending.
     - `HIGHEST_RATING`: `guest_rating` descending, then `catalog_key` ascending.
     - `NEAREST_CITY_CENTER`: `distance_to_city_center_meters` ascending, then `catalog_key` ascending.

6. **Draft Stay Selection Persistence & Mutation:**
   - Table `detour_trip_draft_stay_selection` (`V14`): Primary key is `draft_id`. Stores `accommodation_unit_id` and `unit_count`.
   - `JdbcTripRepository.advanceVersionForDraftMutation:133-147`: Atomically increments both `detour_trip.version` and `detour_trip_draft.version` checking `expectedVersion` and `expectedDraftVersion`.
   - `JdbcTripRepository.deleteDraftStaySelection:210-212`: Deletes existing selection for the draft.
   - Currently, `JdbcTripRepository` does not have `saveDraftStaySelection(long draftId, long unitId, int unitCount)`, nor does `loadDraftSelections` populate property name, unit name, or nightly price objects.

7. **Authorization and Error Handling:**
   - `TripService.ownedTrip(ownerUserId, tripId)` and `ownedDraft(trip, draftId)` verify user ownership and return 404 `RESOURCE_NOT_FOUND` if the trip or draft does not exist or belongs to another user.
   - Concurrency conflicts return 409 `VERSION_CONFLICT`.
   - Missing mandatory accommodation type or invalid fields return 400 `VALIDATION_FAILED`.

## Key Components

- `src/main/java/app/detour/trip/TripService.java:525-609` — Service orchestration for airfare search, draft selection mutation, and draft removal; template for stay methods.
- `src/main/java/app/detour/trip/TripController.java:83-128` — Controller endpoints for draft airfare search (`GET /api/trips/{tripId}/drafts/{draftId}/airfare`), trip airfare search (`GET /api/trips/{tripId}/airfare`), draft airfare selection (`PUT`), and removal (`DELETE`).
- `src/main/java/app/detour/trip/TripRepository.java:23-75` — Aggregate repository contract; includes `deleteDraftStaySelection`, `updateDraftStayUnitCount`, `revalidateStay`, and `resolveSelectionsForPromotion`.
- `src/main/java/app/detour/trip/JdbcTripRepository.java:112-113,133-147,210-218,280-365` — JDBC implementations for draft loading, version advancement, draft stay deletion, unit count updating, and stay revalidation.
- `src/main/java/app/detour/trip/TripRequests.java:68-75` — Request parsing and validation helper; contains `DraftMutation` and `AirfareSelectionRequest`.
- `src/main/java/app/detour/trip/AlternativeResponse.java:15-16` — DTOs for `StayComponentResponse` (`accommodationUnitId`, `unitCount`, `propertyName`, `unitName`, `nights`) and `StayNightResponse` (`date`, `basePriceCents`, `taxCents`, `feeCents`).
- `src/main/java/app/detour/trip/PlannedItinerary.java:23-26` — Domain records `StaySelection` and `StayNight`.
- `src/main/java/app/detour/airfare/AirfareSearchResponses.java:1-80` — Pattern for search response DTOs and pricing breakdowns.
- `src/main/java/app/detour/airfare/AirfareSort.java:1-26` — Pattern for sort enum parsing and 400 validation error responses.
- `src/main/java/app/detour/airfare/AirfareSearchService.java:1-122` — Pattern for search service, party pricing, sorting comparators, and result assembly.
- `src/main/java/app/detour/airfare/JdbcAirfareSearchRepository.java:1-167` — Pattern for repository queries and result mapping.
- `src/test/java/app/detour/catalog/CatalogFixtureIntegrityAssertions.java:29-83` — Integrity verification for accommodation properties, units, nightly inventory, and party capacities.
- `src/test/java/app/detour/trip/AirfareSearchAndSelectionIntegrationTest.java:1-644` — Full MockMvc integration test suite demonstrating search, pricing, sorting, draft mutations, version conflicts, and cross-user isolation.
- `frontend/src/api/tripsApi.ts:46-60,73-82` — Frontend TypeScript definitions for `StayNightResponse`, `StayComponentResponse`, and `DraftSelectionResponse`.

## Affected Areas

| Area | Current behavior and evidence |
| --- | --- |
| **Stay Search Query & Repository** | No stay search repository or query exists. Properties and units reside in `accommodation_property`, `accommodation_unit`, and `accommodation_nightly_inventory` (`V4`, `V5`, `V11`). |
| **Accommodation Type Validation** | Accommodation type is mandatory (`HOTEL`, `BED_AND_BREAKFAST`, `VACATION_RENTAL`). No enum or request validator currently checks this for search. |
| **Automatic Room Count Calculation** | `revalidateStay` in `JdbcTripRepository:318-335` computes `ceil(travelerCount / guestCapacity)` for rooms and 1 for vacation rentals. Search service must apply this to determine `requiredRooms`. |
| **Vacation Rental Capacity Filtering** | Vacation rentals must be excluded when `guest_capacity < travelerCount`. Demonstrated in `revalidateStay:320-323`. |
| **Multi-Night Inventory Filtering** | Units must be excluded when any night in `[startDate, endDate)` has `available_inventory < requiredRooms` or missing inventory. Demonstrated in `revalidateStay:312-334`. |
| **Complete Stay Pricing Breakdown** | Search results must compute complete-stay pricing: nightly sums multiplied by `requiredRooms` in USD integer cents, providing both per-room and per-night itemized breakdowns. |
| **Available Trip Budget Math** | Trip budget is `trip.budgetCents`. Available budget subtracts selected airfare and rental car totals. If a stay is already selected on the draft, it must not be subtracted when searching for a replacement stay. |
| **Deterministic Ranking & Sorting** | Default ranking tiers within-budget stays before over-budget stays, then orders by rating (desc), price (asc), distance (asc), and catalog key (asc). Sort overrides (`LOWEST_PRICE`, `HIGHEST_RATING`, `NEAREST_CITY_CENTER`) order directly with catalog key tie-breaker. |
| **Draft Stay Selection Persistence** | `detour_trip_draft_stay_selection` table exists (`V14`). `JdbcTripRepository` has `deleteDraftStaySelection` and `updateDraftStayUnitCount`, but no `saveDraftStaySelection`. |
| **Draft Stay Selection Resolution** | `loadDraftSelections` in `JdbcTripRepository:112-113` leaves `propertyName` and `unitName` null and `nights` empty. Needs to join property/unit and load nightly records so `TripResponse` returns complete selection data. |
| **Optimistic Concurrency on Mutation** | `advanceVersionForDraftMutation` in `JdbcTripRepository:133-147` validates and increments both `detour_trip.version` and `detour_trip_draft.version`. Must be used on stay selection and removal. |
| **HTTP Controller Endpoints** | `TripController` has airfare search and mutation endpoints, but no endpoints for stay search (`GET /api/trips/{tripId}/drafts/{draftId}/stays`, `GET /api/trips/{tripId}/stays`), selection (`PUT /api/trips/{tripId}/drafts/{draftId}/stay`), or removal (`DELETE /api/trips/{tripId}/drafts/{draftId}/stay`). |

## Existing Tests and Fixtures

- `StayAndRentalFixtureIntegrationTest.java`:
  - `cleanMigrationProducesCompleteStayAndRentalCatalog`: Verifies property counts and migrations.
  - `preV11LineageFailsTheCompleteFixtureFacadeForTheExpectedZeroInventoryReason`: Validates behavior before V11.
- `CatalogFixtureIntegrityAssertions.java:29-83`:
  - Validates 18 properties, 18 units, and 540 nightly inventory rows.
  - Verifies 2 properties per category (`HOTEL`, `BED_AND_BREAKFAST`, `VACATION_RENTAL`) for each destination (`sfo`, `muc`, `mex`).
  - Verifies guest ratings (between 0 and 5), positive city center distances, 30 nights (March 1–30, 2027), positive nightly totals.
  - Verifies that for every party size 1 to 8, at least one property fits capacity on March 10.
- `TripApiIntegrationTest.java`:
  - Tests trip aggregate lifecycle, draft creation, draft duplication, draft deletion, and promotion.
  - Verifies stay revalidation on trip date or traveler count change (`test` at lines 740-750, 1010-1030).
  - Verifies cross-user isolation (404) and version conflicts (409).
- `AirfareSearchAndSelectionIntegrationTest.java`:
  - Benchmark test suite with 12 comprehensive integration tests covering search, capacity exclusions, pricing breakdowns, sort orders, selection persistence, replacing selections, removing selections, version concurrency conflicts, and cross-user isolation.

## Dependencies and Operational Constraints

- **Supported Destinations:** SFO (`destination-sfo`), MUC (`destination-muc`), MEX (`destination-mex`).
- **Supported Stay Dates:** March 1–31, 2027; stay nights fall in `[startDate, endDate)` where `1 <= nights <= 14`. All nights fall within the seeded March 1–30 inventory range.
- **Party Size:** 1–8 travelers (`detour_trip.traveler_count`).
- **Accommodation Types:** Mandatory preference among `HOTEL`, `BED_AND_BREAKFAST`, `VACATION_RENTAL`.
- **Currency & Money Representation:** USD integer cents (`BIGINT`), non-negative.
- **Optimistic Concurrency:** Both `expectedVersion` (Trip) and `expectedDraftVersion` (Draft) are checked on mutation and both are incremented atomically.
- **Cross-User Isolation:** All operations enforce `owner_user_id == principal.userId()`; any discrepancy returns 404 `RESOURCE_NOT_FOUND` without disclosing data.

## Historical Context

- `ai/thoughts/architecture/2026-09-17-p00-t02-detour-replacement-architecture.md`: Defines deterministic search, ranking, and selection architecture in Java Spring Boot. Prohibits nondeterministic AI models or dynamic pricing outside seeded fixtures.
- `ai/thoughts/phases/phase-2-catalog-and-fixtures.md`: Created normalized schema and seeded deterministic stay catalog with properties, units, and 30-night inventory for March 2027.
- `ai/thoughts/phases/phase-3-trips-itineraries-and-profile.md`: Implemented Trip aggregate with multi-draft support, revalidation of draft selections on shared detail updates, and promotion to planned snapshots.
- `ai/thoughts/phases/phase-4-component-selection.md`: Work package 4.3 outlines stay search, room calculation, nightly availability filtering, complete-stay pricing, budget-fit ranking, and draft selection persistence.
- `ai/thoughts/tickets/2026-09-21-p04-t01-airfare-search-and-draft-selection.md`: Predecessor ticket implementing airfare search, round-trip combinations, party pricing, deterministic sorting, draft selection, and integration testing.

## Open Questions

1. **Search Endpoint Path Pluralization:** `Airfare` uses singular `/airfare` (e.g. `GET /api/trips/{tripId}/drafts/{draftId}/airfare`). For stays, should both singular `/stay` and plural `/stays` be mapped in `TripController` on `@GetMapping`, `@PutMapping`, and `@DeleteMapping` to ensure complete client and test flexibility?
2. **Draft Selection Mutation Request Payload:** Should the stay selection request body allow both `{"expectedVersion":0, "expectedDraftVersion":0, "accommodationUnitId":123}` and optionally `unitCount`, while computing and enforcing the authoritative `unitCount` server-side?
3. **Draft Selection Stay Resolution on Read:** When `loadDraftSelections` is invoked in `JdbcTripRepository`, should it query `accommodation_property`, `accommodation_unit`, and `accommodation_nightly_inventory` using the trip's `start_date` and `end_date` so that `DraftResponse.selections.stay` is fully populated with `propertyName`, `unitName`, and `nights`?
