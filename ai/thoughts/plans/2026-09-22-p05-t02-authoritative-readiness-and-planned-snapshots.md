# Authoritative Readiness Validation, Overage Acknowledgment, and Auditable Planned Snapshots Implementation Plan

## Overview
- Ticket: `ai/thoughts/tickets/2026-09-22-p05-t02-authoritative-readiness-and-planned-snapshots.md`
- Research: `ai/thoughts/research/2026-09-22-p05-t02-authoritative-readiness-and-planned-snapshots.md`
- Outcome: The backend validates comprehensive promotion readiness against real-time catalog availability, exposes the `GET /api/trips/{tripId}/drafts/{draftId}/readiness` inspection endpoint, requires explicit acknowledgment of budget-overage warnings (`BUDGET_OVERAGE_UNACKNOWLEDGED`) before Planned promotion, invalidates acknowledgments on subsequent changes, and creates auditable, immutable Planned snapshot records in Flyway `V16` tables that freeze complete descriptive and pricing facts without relying on live catalog joins.

## Current State
- `TripService.promoteDraft` (lines 280-295) and `TripService.readinessIssues` (lines 570-577) implement only basic readiness checks: `trip.travelerAges()` non-null, at least one age >= 18, `trip.budgetCents()` non-null, and at least one resolved component.
- `JdbcTripRepository.resolveSelectionsForPromotion` (lines 227-249) queries catalog tables using loose joins without checking flight seat availability (`available_seats >= travelerCount`), nightly accommodation inventory (`available_inventory >= unitCount`), stay guest capacity, rental driver age (25+), or active rental occupancy overlaps. When a component fails a join, it is silently dropped rather than reporting a structured error.
- There is no readiness inspection endpoint (`GET /api/trips/{tripId}/drafts/{draftId}/readiness`).
- `TripRequests.Promotion` accepts only `expectedVersion` and `expectedDraftVersion`, with no support for `budgetOverageAcknowledged`. Over-budget drafts are promoted unconditionally without overage warnings.
- The planned snapshot tables (`detour_planned_*` established in `V14`) omit key descriptive attributes: airfare snapshots lack carrier names, flight numbers, stops count, layovers, timestamps, timezones, and duration; stay snapshots lack property category, location description, distance to city center, and guest capacity.
- `DetourApplicationTest.java:37` and `PhaseOneCatalogForwardMigrationIntegrationTest.java:33` assert Flyway migration version `15`.
- `frontend/src/api/tripsApi.ts` lacks definitions for `DraftReadinessResponse`, `PromotionRequest`, `tripsApi.getDraftReadiness`, and `tripsApi.promoteDraft`.

## Desired End State
- **Comprehensive Readiness Validation:**
  - Validates destination, trip date boundaries (March 1–31, 2027, 1–14 nights), traveler count (1–8), exact non-null traveler ages matching traveler count, at least one adult (18+), defined non-negative budget, and at least one selected component.
  - Returns all blocking readiness issues together in a structured map (`Map<String, String>`) under 400 `PLANNING_NOT_READY`.
- **Real-Time Catalog Availability & Staleness Validation:**
  - **Airfare:** Outbound and return flights must exist, match trip dates and route (PDX <-> destination), and satisfy `available_seats >= trip.travelerCount()`.
  - **Stay:** Accommodation unit must match destination, satisfy guest capacity (`guestCapacity * unitCount >= travelerCount`), and have `available_inventory >= unitCount` for every night in `[startDate, endDate)`.
  - **Rental:** Rental unit must exist, match destination airport, satisfy driver age requirement (at least one traveler 25+), have pickup/return within trip dates with `pickupAt < returnAt`, and have no active occupancy overlap in `rental_unit_occupancy`.
  - If any component is unavailable, sold out, or structurally invalid, it is reported under the component's key (`"airfare"`, `"stay"`, `"rental"`) in `blockingIssues` rather than silently dropped.
- **Budget-Overage Warning & Acknowledgment:**
  - Evaluates the canonical grand total against `trip.budgetCents()`.
  - If `grandTotal > trip.budgetCents()`, promotion requires `budgetOverageAcknowledged: true` in `TripRequests.Promotion`.
  - If over budget and `budgetOverageAcknowledged` is null or false, promotion is rejected with 400 `BUDGET_OVERAGE_UNACKNOWLEDGED` detailing `grandTotalCents`, `budgetCents`, and `budgetOverageCents`.
  - Any subsequent edit to draft selections or trip budget naturally increments the version and invalidates acknowledgment.
- **Readiness Inspection Endpoint:**
  - `GET /api/trips/{tripId}/drafts/{draftId}/readiness` returns:
    - `ready`: boolean indicating if the draft can be promoted immediately (`blockingIssues.isEmpty() && !requiresOverageAcknowledgment`).
    - `blockingIssues`: map of field/component keys to descriptive messages.
    - `isOverBudget`: boolean indicating whether current grand total exceeds trip budget.
    - `budgetOverageCents`: integer cents overage amount (or 0 when within budget).
    - `requiresOverageAcknowledgment`: boolean indicating if overage acknowledgment is required (`isOverBudget`).
- **Auditable Planned Snapshots (Flyway V16):**
  - Migration `V16__enhance_planned_snapshot_schema.sql` enriches `detour_planned_airfare_snapshot`, `detour_planned_stay_snapshot`, and `detour_planned_rental_snapshot` with all descriptive attributes.
  - `insertPlanned` writes all descriptive facts into snapshot tables; `loadPlannedSelections` reads them directly without joining live catalog tables.
  - Foreign key references to catalog inventory IDs (`outbound_flight_instance_id`, `return_flight_instance_id`, `accommodation_unit_id`, `rental_unit_id`) are preserved for Phase 6 booking revalidation.
- **Lifecycle Invariants & Multi-User Isolation:**
  - In-place modifications to Planned snapshots remain blocked with 409 `IMMUTABLE_ALTERNATIVE`.
  - Deleting Planned snapshot requires `confirmed = true`.
  - Duplicating Planned snapshot produces a fresh mutable Draft.
  - Expired trip promotion rejected with 400 `ALTERNATIVE_EXPIRED`.
  - All operations enforce multi-user isolation (404 `RESOURCE_NOT_FOUND` for non-owners).

## Scope
### In scope
- Flyway migration `V16__enhance_planned_snapshot_schema.sql` altering `detour_planned_airfare_snapshot`, `detour_planned_stay_snapshot`, and `detour_planned_rental_snapshot`.
- Domain model updates in `PlannedItinerary.java` (`AirfareSelection`, `StaySelection`, `RentalSelection`) preserving backwards-compatible constructors.
- DTO updates in `AlternativeResponse.java` (`AirfareComponentResponse`, `StayComponentResponse`, `RentalComponentResponse`, and new `DraftReadinessResponse`).
- Request parsing in `TripRequests.java` for `budgetOverageAcknowledged` in `Promotion`.
- Readiness evaluation pipeline in `TripService.java` checking trip constraints, component availability, and budget overage.
- New REST endpoint `GET /api/trips/{tripId}/drafts/{draftId}/readiness` in `TripController.java`.
- Updated promotion workflow in `TripService.promoteDraft` enforcing readiness and overage acknowledgment before snapshot insertion.
- Persistence updates in `JdbcTripRepository.java` (`insertPlanned`, `loadPlannedSelections`, `resolveSelectionsForPromotion`).
- TypeScript API client updates in `frontend/src/api/tripsApi.ts`.
- Updating migration version assertions in `DetourApplicationTest.java` and `PhaseOneCatalogForwardMigrationIntegrationTest.java`.
- Comprehensive integration, concurrency, and multi-user isolation tests in `src/test/java/app/detour/trip/`.

### Out of scope
- Frontend comparison view and comparison matrix UI (Phase 5 Work Package 5.4 / Ticket P05-T04).
- Frontend promotion modal and readiness banner UI components (Ticket P05-T03).
- Booking checkout, reservation transactions, and payment processing (Phase 6).
- Version 2 Events.

## Active Project Guardrails
None recorded in `ai/thoughts/design-lens.md`.

## Impact and Risk Analysis
- **Database Schema Evolution:** Adding columns to `detour_planned_*` tables via `ALTER TABLE` in H2. All added columns must be nullable or have sensible defaults so existing tests and fixtures run cleanly without requiring seed data migration.
- **Backwards Compatibility in Domain Records:** `AirfareSelection`, `StaySelection`, and `RentalSelection` are constructed in existing unit tests (`ItineraryTallyEngineTest`). We must provide overloaded constructors with existing signatures so that existing tests continue to compile and pass unchanged.
- **Existing Phase 3 Test Updates:** In `TripApiIntegrationTest.java`, some Phase 3 tests created trips with `budgetCents: 0` or low budget and promoted them before overage acknowledgment existed. These tests must be updated to pass `budgetOverageAcknowledged: true` or set realistic budgets, directly verifying Phase 5 behavior.
- **Transactional & Concurrency Integrity:** Snapshot creation occurs inside `@Transactional public TripResponse promoteDraft`. Racing promotion attempts on the same draft version must result in exactly one winner and one `VERSION_CONFLICT` (409), leaving the database with a single, uncorrupted Planned snapshot.

## Implementation Approach
1. **Flyway Migration `V16`:** Alter `detour_planned_airfare_snapshot`, `detour_planned_stay_snapshot`, and `detour_planned_rental_snapshot` to store complete supplier, flight number, stop count, layover, schedule, property category, location description, distance, capacity, and vehicle category facts.
2. **Domain & DTO Enrichment:**
   - Enrich `AirfareSelection`, `StaySelection`, `RentalSelection` in `PlannedItinerary.java`.
   - Enrich `AirfareComponentResponse`, `StayComponentResponse`, `RentalComponentResponse` in `AlternativeResponse.java` and add `DraftReadinessResponse`.
   - Update `selectionResponse` mapping in `TripService.java`.
3. **Readiness Evaluation Engine:**
   - Implement `evaluateDraftReadiness(Trip trip, TripDraft draft)` in `TripService`:
     - Inspect trip aggregate fields (dates within March 1–31, 2027; 1–14 nights; 1–8 travelers; exact ages present; at least one traveler >= 18; budget non-null and >= 0).
     - Inspect component presence (at least one of airfare, stay, rental selected).
     - Inspect catalog availability for selected components (seats >= travelerCount, inventory >= unitCount for every night, party fits guest capacity, rental driver age >= 25, rental dates within trip, rental unit available without active occupancy overlap).
     - Collect all issues into `Map<String, String> blockingIssues`.
     - Calculate tally and budget overage using `ItineraryTallyEngine`.
     - Determine `isOverBudget`, `budgetOverageCents`, `requiresOverageAcknowledgment`, and `ready`.
4. **Endpoints & Workflows:**
   - Add `GET /api/trips/{tripId}/drafts/{draftId}/readiness` in `TripController.java`.
   - Update `POST /api/trips/{tripId}/drafts/{draftId}/plan` in `TripController.java` and `TripService.java`:
     - If trip is expired, throw 400 `ALTERNATIVE_EXPIRED`.
     - Evaluate readiness: if blocking issues exist, throw 400 `PLANNING_NOT_READY` with `fields = blockingIssues`.
     - If over budget and `!Boolean.TRUE.equals(request.budgetOverageAcknowledged())`, throw 400 `BUDGET_OVERAGE_UNACKNOWLEDGED` with `fields = {"grandTotalCents": ..., "budgetCents": ..., "budgetOverageCents": ...}`.
     - Atomically advance version and insert Planned snapshot.
5. **Persistence Layer:**
   - In `JdbcTripRepository`:
     - Update `insertPlanned` to persist all enriched snapshot columns.
     - Update `loadPlannedSelections` to load all enriched snapshot columns directly from snapshot tables.
     - Provide component resolver helpers that extract complete descriptive details from catalog tables.
6. **Frontend API Client:**
   - Update `frontend/src/api/tripsApi.ts` with `DraftReadinessResponse`, `PromotionRequest`, `tripsApi.getDraftReadiness`, and `tripsApi.promoteDraft`.

---

## Phase 1: Database Migration and Model Enrichment

### Changes
- [x] `src/main/resources/db/migration/V16__enhance_planned_snapshot_schema.sql` — Add Flyway migration altering `detour_planned_airfare_snapshot`, `detour_planned_stay_snapshot`, and `detour_planned_rental_snapshot` with descriptive attributes.
- [x] `src/main/java/app/detour/trip/PlannedItinerary.java` — Extend `AirfareSelection`, `StaySelection`, and `RentalSelection` records with enriched fields while preserving existing constructors.
- [x] `src/main/java/app/detour/trip/AlternativeResponse.java` — Extend component response records and add `DraftReadinessResponse(boolean ready, Map<String, String> blockingIssues, boolean isOverBudget, long budgetOverageCents, boolean requiresOverageAcknowledgment)`.
- [x] `src/test/java/app/detour/DetourApplicationTest.java` — Update applied migrations assertion to include `"16"`.
- [x] `src/test/java/app/detour/catalog/PhaseOneCatalogForwardMigrationIntegrationTest.java` — Update target version assertion to `"16"`.

### Automated verification
- [x] `.\mvnw.cmd test -Dtest=DetourApplicationTest,PhaseOneCatalogForwardMigrationIntegrationTest,ItineraryTallyEngineTest` — verifies schema migration applies cleanly and existing domain tally tests pass.

### Optional developer checks
- [x] None.

---

## Phase 2: Persistence Layer and Catalog Snapshot Resolution

### Changes
- [x] `src/main/java/app/detour/trip/TripRepository.java` — Update repository contract if necessary for enriched snapshot resolution.
- [x] `src/main/java/app/detour/trip/JdbcTripRepository.java`:
  - Update `insertPlanned` to write all enriched fields to `detour_planned_airfare_snapshot`, `detour_planned_stay_snapshot`, and `detour_planned_rental_snapshot`.
  - Update `loadPlannedSelections` to read all enriched fields without live catalog joins.
  - Update `resolveSelectionsForPromotion` / component resolver queries to join full catalog details (carrier, flight numbers, stops, layovers, timestamps, timezones, property category, location description, distance to center, guest capacity, vehicle category).

### Automated verification
- [x] `.\mvnw.cmd test -Dtest=TripApiIntegrationTest` — verifies snapshot insertion and loading with V16 schema.

### Optional developer checks
- [x] Inspect H2 schema directly to verify column types and foreign key definitions.

---

## Phase 3: Authoritative Readiness Evaluation, Overage Acknowledgment, and Promotion API

### Changes
- [x] `src/main/java/app/detour/trip/TripRequests.java`:
  - Update `Promotion` record to accept optional `Boolean budgetOverageAcknowledged`.
  - Update `TripRequests.promotion` parser to allow `budgetOverageAcknowledged`.
- [x] `src/main/java/app/detour/trip/TripService.java`:
  - Implement `evaluateDraftReadiness(Trip trip, TripDraft draft)` returning readiness result with all blocking issues, tally, and overage status.
  - Expose `inspectDraftReadiness(long ownerUserId, String tripId, String draftId)`.
  - Update `promoteDraft`: validate readiness (`PLANNING_NOT_READY`), validate overage acknowledgment (`BUDGET_OVERAGE_UNACKNOWLEDGED`), enforce expiration (`ALTERNATIVE_EXPIRED`), and insert snapshot.
  - Update `selectionResponse` to map enriched component fields into `DraftSelectionResponse`.
- [x] `src/main/java/app/detour/trip/TripController.java`:
  - Add `GET /api/trips/{tripId}/drafts/{draftId}/readiness` endpoint.
- [x] `frontend/src/api/tripsApi.ts`:
  - Add `DraftReadinessResponse`, `PromotionRequest`, `tripsApi.getDraftReadiness`, and `tripsApi.promoteDraft`.

### Automated verification
- [x] `.\mvnw.cmd test -Dtest=TripApiIntegrationTest,TripPricingAndTallyIntegrationTest` — verifies promotion, readiness, and tally behavior.
- [x] `npm.cmd --prefix frontend test -- --run` — verifies TypeScript compilation and frontend tests.

### Optional developer checks
- [x] None.

---

## Phase 4: Focused Verification, Concurrency, and Edge Case Coverage

### Changes
- [x] `src/test/java/app/detour/trip/TripApiIntegrationTest.java`:
  - Update Phase 3 tests where `budgetCents: 0` was used to include `budgetOverageAcknowledged: true`.
- [x] `src/test/java/app/detour/trip/DraftReadinessAndPlannedSnapshotIntegrationTest.java` (new test suite):
  - Readiness endpoint returns `ready: true` for valid within-budget draft.
  - Readiness endpoint returns structured `blockingIssues` for missing destination, invalid dates, missing traveler ages, missing adult, missing budget, and missing components.
  - Component availability validation: flight seat exhaustion (`available_seats < travelerCount`), stay nightly inventory exhaustion (`available_inventory < unitCount`), stay capacity exceeded (`unit.guest_capacity * unitCount < travelerCount`), rental driver age requirement (< 25), rental pickup/return date violations, rental active occupancy overlap.
  - Budget overage rejection: 400 `BUDGET_OVERAGE_UNACKNOWLEDGED` with `grandTotalCents`, `budgetCents`, and `budgetOverageCents` when unacknowledged.
  - Successful promotion of over-budget draft when `budgetOverageAcknowledged: true`.
  - Invalidation of overage acknowledgment upon subsequent draft or budget modification.
  - Snapshot data integrity: verify that Planned snapshot contains complete frozen descriptive facts from V16 tables.
  - Immutability: verify mutating live catalog prices/descriptions does not alter saved Planned snapshot.
  - Immutability: verify deleting the source draft does not alter saved Planned snapshot.
  - Immutability: verify in-place draft mutation endpoints reject Planned snapshot with 409 `IMMUTABLE_ALTERNATIVE`.
  - Multi-user isolation: User B receives 404 `RESOURCE_NOT_FOUND` when attempting to inspect readiness, promote, duplicate, or delete User A's draft or alternative.
  - Concurrency: racing promotion requests on same draft version result in exactly one 201 Created and one 409 `VERSION_CONFLICT`, with exactly one Planned snapshot created.

### Automated verification
- [x] `.\mvnw.cmd test -Dtest=DraftReadinessAndPlannedSnapshotIntegrationTest` — all new scenarios pass.
- [x] `.\mvnw.cmd test` — full backend test suite passes (0 failures).
- [x] `npm.cmd --prefix frontend test -- --run` — full frontend test suite passes (0 failures).

### Optional developer checks
- [x] None.

---

## Test Strategy
- **Failing Red Test First:** Create an integration test verifying `GET /api/trips/{tripId}/drafts/{draftId}/readiness` and overage rejection 400 `BUDGET_OVERAGE_UNACKNOWLEDGED` before implementing the service changes, confirming failure for the intended reason.
- **Integration Tests:** Use `MockMvc` with real Spring context, Flyway H2 database, and seeded catalog data to test end-to-end HTTP request and response contracts.
- **Concurrency Tests:** Use `ExecutorService` and `CyclicBarrier` to execute parallel racing promotion requests against the same draft, proving atomic version advancement and isolation.
- **Catalog Tampering Tests:** Update live catalog records after promotion and assert that `GET /api/trips/{tripId}` continues returning the original frozen values from the Planned snapshot.

## Acceptance-Criteria Traceability
| Acceptance criterion | Planned code evidence | Planned test evidence |
| --- | --- | --- |
| 1. Draft promotion fails with 400 `PLANNING_NOT_READY` when any required field is missing or incomplete, returning all issues together. | `TripService.evaluateDraftReadiness`, `TripService.promoteDraft` | `DraftReadinessAndPlannedSnapshotIntegrationTest.reportsAllMissingFieldReadinessIssuesTogether` |
| 2. Draft promotion fails with 400 `PLANNING_NOT_READY` when any selected flight seats, stay nightly inventory, or rental unit are sold out or unavailable. | `TripService.evaluateDraftReadiness`, `JdbcTripRepository` | `DraftReadinessAndPlannedSnapshotIntegrationTest.rejectsPromotionWhenCatalogComponentsAreSoldOutOrUnavailable` |
| 3. Over-budget draft promotion is rejected with 400 `BUDGET_OVERAGE_UNACKNOWLEDGED` unless `budgetOverageAcknowledged: true` is explicitly supplied in the promotion request. | `TripRequests.promotion`, `TripService.promoteDraft` | `DraftReadinessAndPlannedSnapshotIntegrationTest.enforcesBudgetOverageAcknowledgmentOnPromotion` |
| 4. The readiness inspection endpoint (`GET /api/trips/{tripId}/drafts/{draftId}/readiness`) returns accurate blocking issues, overage status, and promotion eligibility. | `TripController.inspectDraftReadiness`, `TripService.inspectDraftReadiness` | `DraftReadinessAndPlannedSnapshotIntegrationTest.readinessInspectionEndpointReturnsCompleteStatus` |
| 5. Complete descriptive and schedule facts are persisted into Planned snapshot records in Flyway V16 tables. | `V16__enhance_planned_snapshot_schema.sql`, `JdbcTripRepository.insertPlanned` | `DraftReadinessAndPlannedSnapshotIntegrationTest.persistsCompleteDescriptiveFactsIntoPlannedSnapshot` |
| 6. Modifying catalog display data or deleting a source Draft does not alter previously saved Planned snapshots. | `JdbcTripRepository.loadPlannedSelections` (no live catalog joins) | `DraftReadinessAndPlannedSnapshotIntegrationTest.plannedSnapshotIsUnaffectedByCatalogEditsOrDraftDeletion` |
| 7. Concurrency and transactional tests verify that racing promotion requests succeed once and do not produce partial or corrupted snapshots. | `@Transactional TripService.promoteDraft`, `trips.advanceVersionForDraft` | `DraftReadinessAndPlannedSnapshotIntegrationTest.racingPromotionRequestsHaveSingleWinnerAndNoCorruption` |
| 8. Multi-user isolation prevents unauthorized inspection, promotion, or deletion across different accounts. | `TripService.ownedTrip`, `TripService.ownedDraft`, `TripService.ownedAlternative` | `DraftReadinessAndPlannedSnapshotIntegrationTest.enforcesMultiUserIsolationAcrossAllEndpoints` |

## Risks and Rollback/Recovery
- **Risk:** Schema migration alters existing snapshot tables.
  - **Mitigation:** Adding columns using nullable types ensures backward compatibility with existing tests and fixtures. If migration issues occur, rollback entails removing `V16` before deployment.
- **Risk:** Existing tests breaking due to newly enforced budget overage acknowledgment.
  - **Mitigation:** Pre-flight test scan identified specific Phase 3 tests that used `budgetCents: 0`. Updating those tests to explicitly include `budgetOverageAcknowledged: true` preserves their test intent while validating Phase 5 behavior.
- **Risk:** Incomplete catalog availability checks allowing oversold promotion.
  - **Mitigation:** Direct queries against `flight_instance.available_seats`, `accommodation_nightly_inventory.available_inventory`, and `rental_unit_occupancy` with active status checks guarantee real-time verification at promotion instant.

## References
- Ticket: `ai/thoughts/tickets/2026-09-22-p05-t02-authoritative-readiness-and-planned-snapshots.md`
- Research: `ai/thoughts/research/2026-09-22-p05-t02-authoritative-readiness-and-planned-snapshots.md`
- Related ticket P05-T01: `ai/thoughts/tickets/2026-09-22-p05-t01-canonical-pricing-and-server-tally-engine.md`
- Downstream ticket P05-T03: `ai/thoughts/tickets/2026-09-22-p05-t03-deliver-draft-promotion-and-readiness-experience.md`
- Downstream ticket P05-T04: `ai/thoughts/tickets/2026-09-22-p05-t04-deliver-itinerary-comparison-and-booking-selection.md`
