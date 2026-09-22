---
date: 2026-09-22
repository: loomspan/loomspan-travel-demo
branch: main
commit: 39dc429d0c6a42d802b618f9acc267737f74c89b
ticket: ai/thoughts/tickets/2026-09-22-p05-t02-authoritative-readiness-and-planned-snapshots.md
tags: [readiness, planned-snapshots, promotion, overage-acknowledgment, flyway-v16, catalog-availability, immutability, phase-5]
---

# Authoritative Readiness Validation, Overage Acknowledgment, and Auditable Planned Snapshots Research

## Research Question

How does the Detour backend currently validate draft readiness, handle promotion to Planned itineraries, persist planned snapshots, enforce immutability, and track budget overages? What models, schemas, validations, endpoints, and tests must be created or updated to satisfy all P05-T02 requirements?

## Summary

In the current codebase, draft promotion (`POST /api/trips/{tripId}/drafts/{draftId}/plan`) performs a rudimentary readiness check in `TripService.readinessIssues` and `TripService.promoteDraft`. It verifies that traveler ages are not null, at least one traveler is age 18+, a budget is non-null, and at least one component (airfare, stay, or rental) is present. However, it does not validate trip destination, date bounds, or traveler count against system constraints. Crucially, `resolveSelectionsForPromotion` in `JdbcTripRepository` does not check real-time catalog availability: it ignores flight seat availability (`available_seats >= travelerCount`), nightly accommodation inventory (`available_inventory >= unitCount`), stay guest capacity rules, rental unit availability (via `rental_unit_occupancy`), rental destination airport matching, and driver age (25+). When components fail simple foreign-key joins, they are silently dropped rather than reporting actionable, structured errors identifying the offending component.

Furthermore, there is currently no readiness inspection endpoint (`GET /api/trips/{tripId}/drafts/{draftId}/readiness`), no support for budget-overage acknowledgment (`budgetOverageAcknowledged` in `TripRequests.Promotion`), and no rejection with 400 `BUDGET_OVERAGE_UNACKNOWLEDGED`. The database schema for planned snapshots (`detour_planned_*` established in `V14`) lacks critical descriptive attributes: airfare snapshots lack carrier names, flight numbers, stop counts, layover details, timestamps, timezones, and durations; stay snapshots lack property category, location description, distance to city center, and guest capacity.

To satisfy ticket P05-T02, Flyway migration `V16` must enhance the planned snapshot tables to freeze complete descriptive attributes without reliance on live catalog joins. The backend must introduce a dedicated readiness evaluation pipeline that returns all blocking issues together (`PLANNING_NOT_READY`), calculates canonical grand totals against `trip.budgetCents`, enforces explicit acknowledgment of budget overages (`BUDGET_OVERAGE_UNACKNOWLEDGED`), exposes the `GET /api/trips/{tripId}/drafts/{draftId}/readiness` endpoint, preserves concurrency and multi-user isolation, and updates migration version assertions in existing integration tests.

## Repository State

- **Date:** 2026-09-22T12:05:00-07:00
- **Repository:** loomspan/loomspan-travel-demo
- **Current Branch:** `main`
- **Current Commit:** `39dc429d0c6a42d802b618f9acc267737f74c89b` ("clean up after p05-t01")
- **Working Tree:** Clean (no uncommitted or untracked changes)
- **Baseline Test Suite Status:**
  - Backend: `.\mvnw.cmd test` passes 100% (128 tests across 20 test classes, 0 failures, 0 errors, 0 skipped).
  - Frontend: `npm.cmd --prefix frontend test` passes 100% (53 tests across 5 test files, 0 failures).

## Current Behavior and Data Flow

### 1. Draft Creation and Component Selection

- Trips are created via `POST /api/trips`, creating a `detour_trip` aggregate, `detour_trip_traveler` records, and an initial empty `detour_trip_draft` (`version = 0`).
- Component selections are saved via `PUT` endpoints:
  - Airfare: `PUT /api/trips/{tripId}/drafts/{draftId}/airfare` stores `outbound_flight_instance_id` and `return_flight_instance_id` into `detour_trip_draft_airfare_selection`.
  - Stay: `PUT /api/trips/{tripId}/drafts/{draftId}/stays` stores `accommodation_unit_id` and `unit_count` into `detour_trip_draft_stay_selection`.
  - Rental: `PUT /api/trips/{tripId}/drafts/{draftId}/rentals` stores `rental_unit_id`, `pickup_at`, and `return_at` into `detour_trip_draft_rental_selection`.
- Drafts store *only foreign keys* to catalog records. When drafts are fetched via `GET /api/trips/{tripId}`, `JdbcTripRepository.loadDraftSelections` joins live catalog tables (`flight_instance`, `flight_schedule`, `accommodation_unit`, `accommodation_property`, `rental_unit`, `rental_vehicle_class`, `rental_location`) to display fresh catalog names, descriptions, and prices.

### 2. Current Draft Promotion (`POST /api/trips/{tripId}/drafts/{draftId}/plan`)

1. **Authentication & Ownership:** `TripController.promoteDraft` (lines 67-71) extracts `principal.userId()`, delegating to `TripService.promoteDraft` (lines 280-295).
2. **Trip & Draft Lookup:** Retrieves the trip via `ownedTrip(ownerUserId, tripId)` and draft via `ownedDraft(trip, draftId)`.
3. **Expiration Check:** Calls `isExpired(trip.startDate())` (line 284). If current time is on or after trip start date midnight (PDX zone), throws 400 `ALTERNATIVE_EXPIRED`.
4. **Resolution via Catalog:** Calls `trips.resolveSelectionsForPromotion(trip, draft)`:
   - `resolveAirfare`: queries flights matching route (`PDX` <-> destination), service dates, and flight instance IDs. Does *not* check `available_seats >= travelerCount`.
   - `resolveStay`: queries accommodation unit matching destination and checks `unit.guest_capacity * unitCount >= travelerCount` and that night count equals date diff. Does *not* check `available_inventory >= unitCount` per night.
   - `resolveRental`: queries rental unit matching destination and dates. Does *not* check rental unit availability (occupancy overlap trigger/records), driver age (25+), or airport location.
   - If any component query fails, the component is silently omitted (`null`).
5. **Readiness Check:** Calls `readinessIssues(trip, resolved)` (lines 570-577):
   - Checks `trip.travelerAges() == null` -> `"travelerAges"`.
   - Checks `trip.travelerAges().stream().noneMatch(age -> age >= 18)` -> `"adult"`.
   - Checks `trip.budgetCents() == null` -> `"budgetCents"`.
   - Checks if all three resolved components are null -> `"components"`.
   - If issues map is not empty, throws `ApiException(400, "PLANNING_NOT_READY", "The Draft is not ready to be planned.", issues)`.
6. **Optimistic Concurrency:** Calls `trips.advanceVersionForDraft(trip.id(), ownerUserId, request.expectedVersion(), draft.id(), request.expectedDraftVersion())`. If version mismatch, throws 409 `VERSION_CONFLICT`.
7. **Snapshot Insertion:** Calls `trips.insertPlanned(trip.id(), UUID.randomUUID(), resolved)`:
   - Generates a new `detour_planned_itinerary` row.
   - Inserts into `detour_planned_airfare_snapshot`, `detour_planned_stay_snapshot`, `detour_planned_stay_night_snapshot`, and `detour_planned_rental_snapshot`.
8. **Response:** Re-queries and returns `TripResponse` containing the new Planned itinerary and server-calculated tallies.

### 3. Alternative Immutability and Lifecycle

- Planned itineraries are loaded via `loadPlannedSelections` from `detour_planned_*` tables, preserving frozen prices and names.
- Any attempt to modify a Planned alternative via draft mutation endpoints is rejected with 409 `IMMUTABLE_ALTERNATIVE` (`TripService.ownedDraft` lines 454-456).
- Duplicating a Planned alternative (`POST /api/trips/{tripId}/alternatives/{alternativeId}/duplicate`) copies snapshot selections into a new Draft (`TripService.duplicateAlternative` lines 298-314).
- Deleting a Planned alternative (`DELETE /api/trips/{tripId}/alternatives/{alternativeId}`) requires `confirmed: true` in the request body (`TripService.deleteAlternative` lines 317-332).

### 4. Gaps in Current Behavior

1. **No System Constraint Validation on Promotion:** Does not check whether trip destination is supported, dates fall within March 1–31, 2027 (1–14 nights), or traveler count is 1–8.
2. **Incomplete Traveler Age Validation:** Does not check if `travelerAges` contains nulls or if `travelerAges.size() != travelerCount`.
3. **No Real-Time Inventory & Availability Revalidation:**
   - Flight seat exhaustion (`available_seats < travelerCount`) is not checked.
   - Stay nightly inventory exhaustion (`available_inventory < unitCount`) is not checked.
   - Rental car active occupancy overlap is not checked.
   - Rental car driver age requirement (at least one traveler 25+) is not checked.
   - When a component is sold out or unavailable, it is silently dropped instead of returning 400 `PLANNING_NOT_READY` naming the offending component (`airfare`, `stay`, `rental`) and the specific reason.
4. **No Budget Overage Warning or Acknowledgment:**
   - Canonical grand total is not checked against `trip.budgetCents`.
   - `TripRequests.Promotion` does not accept `budgetOverageAcknowledged`.
   - Over-budget drafts are promoted without requiring acknowledgment.
   - 400 `BUDGET_OVERAGE_UNACKNOWLEDGED` error is not implemented.
5. **No Draft Readiness Inspection Endpoint:**
   - `GET /api/trips/{tripId}/drafts/{draftId}/readiness` does not exist.
6. **Incomplete Planned Snapshot Schema (V14 limitations):**
   - Airfare snapshot lacks carrier name, flight numbers, stop count, layover details, timestamps, timezones, and duration.
   - Stay snapshot lacks property category, location description, distance to city center, and guest capacity.

## Key Components

- `src/main/java/app/detour/trip/TripController.java:67-71` — Handles `POST /api/trips/{tripId}/drafts/{draftId}/plan`. Needs `GET /api/trips/{tripId}/drafts/{draftId}/readiness`.
- `src/main/java/app/detour/trip/TripRequests.java:37, 101-104` — `TripRequests.Promotion` record. Currently only contains `expectedVersion` and `expectedDraftVersion`. Must parse `budgetOverageAcknowledged`.
- `src/main/java/app/detour/trip/TripService.java:280-295` — `promoteDraft` workflow. Must evaluate readiness, real-time catalog availability, and budget overage acknowledgment before snapshot insertion.
- `src/main/java/app/detour/trip/TripService.java:570-577` — `readinessIssues` method. Must be expanded to check system constraints, exact ages, adult traveler, defined budget, selected components, and catalog availability.
- `src/main/java/app/detour/trip/TripRepository.java:43, 74` — Repository interfaces `insertPlanned` and `resolveSelectionsForPromotion`.
- `src/main/java/app/detour/trip/JdbcTripRepository.java:162-171` — `loadPlannedSelections`. Must load enriched snapshot attributes from V16 tables.
- `src/main/java/app/detour/trip/JdbcTripRepository.java:219-225` — `insertPlanned`. Must persist enriched snapshot attributes into V16 tables.
- `src/main/java/app/detour/trip/JdbcTripRepository.java:227-249` — `resolveSelectionsForPromotion`. Must query full descriptive facts and verify availability.
- `src/main/java/app/detour/trip/ItineraryTallyEngine.java:45-81` — Computes canonical totals, `remainingBudgetCents`, `budgetOverageCents`, and `isOverBudget`.
- `src/main/java/app/detour/trip/PlannedItinerary.java:15-34` — Domain records `DraftSelections`, `AirfareSelection`, `StaySelection`, `RentalSelection`, `PlannedItinerary`.
- `src/main/java/app/detour/trip/AlternativeResponse.java:9-27` — DTOs for `AlternativeResponse`, `PlannedResponse`, `DraftSelectionResponse`, `AirfareComponentResponse`, `StayComponentResponse`, `RentalComponentResponse`. Needs `DraftReadinessResponse`.
- `src/main/resources/db/migration/V14__create_draft_selection_and_planned_snapshot_schema.sql:33-96` — Baseline planned snapshot tables.
- `src/main/java/app/detour/airfare/AirfareSearchRepository.java` & `JdbcAirfareSearchRepository.java:20-51, 112-165` — `findLegById` and `findAirportIataCodeForDestination`.
- `src/main/java/app/detour/stay/StaySearchRepository.java` & `JdbcStaySearchRepository.java:49-73` — `findCandidateById` with candidate nights and nightly inventory.
- `src/main/java/app/detour/rental/RentalSearchRepository.java` & `JdbcRentalSearchRepository.java:57-96` — `findUnitById` and `isUnitAvailable`.
- `src/test/java/app/detour/DetourApplicationTest.java:37` — Asserts applied migration version list up to `"15"`.
- `src/test/java/app/detour/catalog/PhaseOneCatalogForwardMigrationIntegrationTest.java:33` — Asserts applied migration version equals `"15"`.

## Affected Areas

| Area | Current behavior and evidence |
| --- | --- |
| **Comprehensive Readiness Validation** | `readinessIssues` in `TripService.java:570-577` only checks for non-null `travelerAges`, any traveler >= 18, non-null `budgetCents`, and at least one component. It lacks checks for system constraints (destination, dates within March 1–31 2027, 1–14 nights, traveler count 1–8), null values within `travelerAges`, traveler count matching `travelerAges.size()`, and non-negative budget. |
| **Real-Time Catalog Availability Check** | `JdbcTripRepository.resolveSelectionsForPromotion` (lines 227-249) queries catalog tables with basic joins, omitting seat capacity checks, nightly inventory checks, rental driver age checks, and rental occupancy overlap checks. Sold-out components are silently dropped rather than triggering 400 `PLANNING_NOT_READY` with structured component keys. |
| **Budget-Overage Warning & Acknowledgment** | `TripRequests.Promotion` (lines 37, 101-104) does not support `budgetOverageAcknowledged`. Over-budget drafts are promoted without verification. 400 `BUDGET_OVERAGE_UNACKNOWLEDGED` detailing overage amount and grand total is not implemented. |
| **Draft Readiness Endpoint** | `GET /api/trips/{tripId}/drafts/{draftId}/readiness` does not exist in `TripController.java`. Must return `ready`, `blockingIssues`, `isOverBudget`, `budgetOverageCents`, and `requiresOverageAcknowledgment`. |
| **Flyway Migration V16** | Flyway migrations currently end at `V15`. Planned snapshot tables (`detour_planned_airfare_snapshot`, `detour_planned_stay_snapshot`) lack descriptive facts (carrier, flight numbers, stops, layover, departure/arrival timestamps, timezones, duration, property category, location description, distance to city center, guest capacity). |
| **Planned Snapshot Persistence & Immutability** | `insertPlanned` (lines 219-225) writes only V14 fields. `loadPlannedSelections` (lines 162-171) reads only V14 fields. Planned snapshots must freeze all descriptive facts so historical Planned itineraries never rely on live catalog joins. |
| **DTOs & Domain Models** | `AirfareSelection`, `StaySelection`, `RentalSelection` in `PlannedItinerary.java`, and `AirfareComponentResponse`, `StayComponentResponse`, `RentalComponentResponse` in `AlternativeResponse.java` need fields to represent the complete snapshot facts. |
| **Migration Version Test Assertions** | `DetourApplicationTest.java:37` asserts migrations `"1"` through `"15"`. `PhaseOneCatalogForwardMigrationIntegrationTest.java:33` asserts current version `"15"`. Both will fail once `V16` is added unless updated. |
| **Frontend API Client** | `frontend/src/api/tripsApi.ts` does not define `DraftReadinessResponse`, `PromotionRequest`, `tripsApi.getDraftReadiness`, or `tripsApi.promoteDraft`. |

## Existing Tests and Fixtures

- `src/test/java/app/detour/trip/TripApiIntegrationTest.java`:
  - Lines 315-348: `promotesAReadyDraftAsAnIndependentImmutablePlannedSnapshot` verifies basic promotion, frozen price retention after flight price update, alternative duplication, and confirmation-gated deletion.
  - Lines 350-367: `reportsAllKnownPromotionReadinessIssuesTogether` asserts 400 `PLANNING_NOT_READY` with `fields.travelerAges`, `fields.budgetCents`, `fields.components`, and `fields.adult`.
  - Lines 369-411: `promotesEachStructurallyValidSelectionKindWithoutCanonicalPricing` tests promoting stay-only, rental-only, and all-three drafts.
  - Lines 413-463: `plannedSnapshotReadsCopiedContentAfterDraftAndCatalogMutation` tests snapshot stability when live catalog prices are updated.
  - Lines 530-575: Tests concurrent promotion using multiple threads and asserts 409 `VERSION_CONFLICT`.
  - Lines 495-504: Asserts multi-user isolation on draft promotion (intruder receives 404 `RESOURCE_NOT_FOUND`).
- `src/test/java/app/detour/trip/TripPricingAndTallyIntegrationTest.java`:
  - Lines 455-502: Tests promoting a draft with server-calculated tally, asserting `grandTotalCents`, `remainingBudgetCents`, `budgetOverageCents`, and `isOverBudget` on `planned[0].tally`.
  - Lines 505-525: Asserts multi-user isolation on trip details and component searches.
- `src/test/java/app/detour/DetourApplicationTest.java`:
  - Lines 35-41: Tests Flyway migration lineage up to `V15`.
- `src/test/java/app/detour/catalog/PhaseOneCatalogForwardMigrationIntegrationTest.java`:
  - Lines 18-63: Tests forward migration of identity and catalog schemas up to `V15`.
- **Gaps in existing test coverage:**
  - No tests for the readiness endpoint `GET /api/trips/{tripId}/drafts/{draftId}/readiness`.
  - No tests asserting 400 `PLANNING_NOT_READY` when flight seats are sold out (`available_seats < travelerCount`), stay nightly inventory is sold out (`available_inventory < unitCount`), or rental unit has active occupancy overlap.
  - No tests asserting 400 `PLANNING_NOT_READY` when rental driver age constraint (25+) is violated.
  - No tests asserting 400 `BUDGET_OVERAGE_UNACKNOWLEDGED` when grand total exceeds budget and `budgetOverageAcknowledged` is missing or false.
  - No tests verifying that `budgetOverageAcknowledged: true` succeeds and creates a Planned snapshot for an over-budget draft.
  - No tests verifying that modifying draft selections or trip budget invalidates client acknowledgment.
  - No tests verifying rich snapshot fields (carrier, flight numbers, stops, layover, local timestamps, timezones, property category, location description, distance to city center, guest capacity) in V16 tables.

## Dependencies and Operational Constraints

1. **Integer Cents Representation:** All prices, tallies, and overage amounts must use non-negative 64-bit integers (`long`/`Long`), with zero floating-point arithmetic.
2. **Snapshot Immutability Contract:**
   - Planned snapshots must be permanently frozen at promotion time.
   - `loadPlannedSelections` must read descriptive facts and prices directly from `detour_planned_*` tables without joining live catalog tables.
   - Foreign key references to catalog inventory identifiers (`outbound_flight_instance_id`, `return_flight_instance_id`, `accommodation_unit_id`, `rental_unit_id`) must be retained for Phase 6 booking revalidation.
3. **Structured Error Contract:**
   - Unready drafts: HTTP 400 with code `PLANNING_NOT_READY` and a `fields` map containing all blocking issue keys (`travelerAges`, `adult`, `budgetCents`, `components`, `airfare`, `stay`, `rental`, `destination`, `dates`, `travelerCount`).
   - Unacknowledged overage: HTTP 400 with code `BUDGET_OVERAGE_UNACKNOWLEDGED`, detailing overage and grand total.
   - Expired trip or draft: HTTP 400 with code `ALTERNATIVE_EXPIRED`.
   - In-place modification of Planned snapshot: HTTP 409 with code `IMMUTABLE_ALTERNATIVE`.
   - Version conflict: HTTP 409 with code `VERSION_CONFLICT`.
4. **Clean-Break Policy:** No legacy fallbacks, compatibility wrappers, or deprecated columns.
5. **Multi-User Isolation:** All operations (inspection, promotion, deletion, duplication) must be scoped strictly to the authenticated trip owner.
6. **Flyway Migration Sequence:** Migration must be named `V16__enhance_planned_snapshot_schema.sql` to follow `V15`.

## Historical Context

- **Phase 3 (Ticket P03-T03):** Established the immutable Planned snapshot concept and lifecycle invariants (`TripAlternative`, `detour_planned_itinerary`, duplication, confirmation-gated deletion). Explicitly deferred real-time catalog availability, canonical pricing, and budget-overage acknowledgment to Phase 5.
- **Phase 4 (Tickets P04-T01 to P04-T04):** Established search and draft selection for Airfare, Stay, and Rental Car, storing catalog foreign keys in `detour_trip_draft_*` tables.
- **Phase 5 (Ticket P05-T01):** Implemented `ItineraryTallyEngine` on the server, returning canonical `airfareTotalCents`, `stayTotalCents`, `rentalTotalCents`, `grandTotalCents`, `remainingBudgetCents`, `budgetOverageCents`, and `isOverBudget` across Draft, Planned, Alternative, and Trip responses.
- **Downstream Ticket P05-T03:** Implements the frontend promotion modal, readiness banner, and overage acknowledgment flow, consuming the `GET .../readiness` endpoint and `budgetOverageAcknowledged: true` contract created in this ticket.
- **Downstream Ticket P05-T04:** Implements the multi-alternative comparison matrix, reading the rich Planned snapshot attributes (carrier, flight numbers, stops, layover, times, timezones, property category, distance to city center, etc.) persisted in V16.

## Open Questions

Matters for planning to investigate and settle:

1. **Semantics of `ready` in `DraftReadinessResponse`:**
   - Does `ready` indicate that the draft is eligible for promotion (i.e. `blockingIssues.isEmpty()`), or that it can be promoted immediately without any further prompt (i.e. `blockingIssues.isEmpty() && !requiresOverageAcknowledgment`)?
   - *Consideration:* In P05-T03, over-budget drafts with complete selections can still be promoted after acknowledging the overage modal. If `ready` were false solely due to budget overage, client UI might treat the draft as having blocking errors rather than an overage warning. Settling whether `ready = blockingIssues.isEmpty()` or `ready = blockingIssues.isEmpty() && !requiresOverageAcknowledgment` will clarify the contract.
2. **Payload Structure for `BUDGET_OVERAGE_UNACKNOWLEDGED`:**
   - In Detour, `ApiError` contains `(code, message, fields)`. For `BUDGET_OVERAGE_UNACKNOWLEDGED`, what keys should appear in `fields`?
   - *Recommendation:* `fields: {"grandTotalCents": String.valueOf(grandTotal), "budgetCents": String.valueOf(budget), "budgetOverageCents": String.valueOf(overage)}`.
3. **Flyway Migration Strategy for V16:**
   - Should `V16__enhance_planned_snapshot_schema.sql` alter existing `detour_planned_*` tables using `ALTER TABLE ... ADD COLUMN`, or drop and re-create them?
   - *Consideration:* There are no production deployments or seeded planned snapshots in any migrations. However, `ALTER TABLE` is standard Flyway practice. In H2, adding columns with `DEFAULT` or `NULL` allows clean migration without breaking existing tests.
4. **DTO Field Expansion vs Separate Snapshot DTOs:**
   - Should `AirfareComponentResponse` and `StayComponentResponse` be enriched with nullable descriptive fields (populated for Planned snapshots, null or populated for Drafts), or should distinct DTOs be used?
   - *Consideration:* `AlternativeResponse` and `TripResponse` use `DraftSelectionResponse(AirfareComponentResponse airfare, StayComponentResponse stay, RentalComponentResponse rental)` for both Draft and Planned items. Enriching `AirfareComponentResponse` and `StayComponentResponse` keeps the API contract unified and directly supports P05-T04 comparison.
