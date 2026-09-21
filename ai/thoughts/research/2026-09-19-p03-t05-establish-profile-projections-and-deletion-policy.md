---
date: 2026-09-21
repository: loomspan/loomspan-travel-demo
branch: main
commit: ff821cf059f187952fb6addc92f4ace8a8034d7a
ticket: ai/thoughts/tickets/2026-09-19-p03-t05-establish-profile-projections-and-deletion-policy.md
tags: [trip, profile, projections, temporal, expiration, clock, deletion, lifecycle]
---

# Date-Derived Profile Views and Safe Deletion Policy Research

## Research Question

What is the current architecture, data model, lifecycle enforcement, and endpoint behavior for:
1. Querying owner-scoped Trips and projecting profile views (upcoming vs past, alternative counts and statuses);
2. Temporal evaluation of Trips (Upcoming vs Past) and unbooked alternatives (Draft and Planned expiration at the start of the departure date in `America/Los_Angeles`);
3. Application clock control and injection in runtime and deterministic tests (including daylight-saving offset boundaries);
4. Deletion operations for Drafts, Planned alternatives, and whole Trips, including concurrency guards, confirmation validation, cascade behavior, catalog isolation, and forward-compatibility with Phase 6 booking history?

## Summary

- **Trip Aggregate and Alternatives State**:
  - `detour_trip` stores the aggregate root with `owner_user_id`, `catalog_destination_id`, `start_date`, `end_date`, `traveler_count`, `budget_cents`, `display_label`, and `version`.
  - `detour_trip_traveler` stores traveler ordinals (1..8) and optional ages (0..120).
  - `detour_trip_draft` stores versioned mutable alternatives (`id`, `public_id`, `trip_id`, `version`) referencing catalog items via `detour_trip_draft_airfare_selection`, `detour_trip_draft_stay_selection`, and `detour_trip_draft_rental_selection`.
  - `detour_planned_itinerary` stores immutable snapshots (`id`, `public_id`, `trip_id`) referencing snapshot tables `detour_planned_airfare_snapshot`, `detour_planned_stay_snapshot`, `detour_planned_stay_night_snapshot`, and `detour_planned_rental_snapshot`.
  - There is currently no `detour_booking` table or booking entity in the schema.
- **Profile and Trip Retrieval**:
  - `IdentityController.java:65-68` maps `GET /api/profile`, returning `ProfileResponse(email)`. It does not retrieve or include Trip data.
  - `SpaRouteController.java:9-11` serves the single-page application HTML for `/profile`.
  - `TripController.java:18-85` maps `/api/trips`, providing `POST /api/trips` (create), `GET /api/trips/{tripId}` (detail), `PUT /api/trips/{tripId}` (replaceSharedDetails), and `/duplicate`, `/revisions`, `/drafts`, `/alternatives` routes.
  - There is currently no `GET /api/trips` collection endpoint, nor any method in `JdbcTripRepository` to retrieve all Trips for a given user.
- **Temporal Evaluation and Clock**:
  - No `Clock` bean or clock abstraction currently exists in `app.detour`.
  - All date checks in `TripService` compare requested dates against static boundaries (`FIRST_SUPPORTED_DATE = 2027-03-01`, `LAST_SUPPORTED_DATE = 2027-03-31`).
  - No concept of Upcoming vs Past or alternative Expiration currently exists in code or database.
  - `TripService.promoteDraft` (lines 168–180) checks promotion readiness (`readinessIssues` in lines 412–419 for ages, adult presence, budget, components) but does not inspect current time or check whether the draft is expired.
- **Deletion Policy and Relational Cascades**:
  - Draft deletion is implemented via `DELETE /api/trips/{tripId}/drafts/{draftId}` and `DELETE /api/trips/{tripId}/alternatives/{alternativeId}`. Both enforce optimistic concurrency (`expectedVersion`, `expectedDraftVersion`) and delete from `detour_trip_draft`. Selection tables have `ON DELETE CASCADE`.
  - Planned itinerary deletion is implemented via `DELETE /api/trips/{tripId}/alternatives/{alternativeId}`. It enforces `expectedVersion`, forbids `expectedDraftVersion`, requires `confirmed == true`, and deletes from `detour_planned_itinerary`. Snapshot tables have `ON DELETE CASCADE`.
  - Trip deletion (`DELETE /api/trips/{tripId}`) is not implemented anywhere in the controller, service, or repository.
  - Foreign key constraints on `detour_trip`:
    - `fk_planned_itinerary_trip` on `detour_planned_itinerary(trip_id)` has `ON DELETE CASCADE`.
    - `fk_detour_trip_traveler_trip` on `detour_trip_traveler(trip_id)` does **not** have `ON DELETE CASCADE`.
    - `fk_detour_trip_draft_trip` on `detour_trip_draft(trip_id)` does **not** have `ON DELETE CASCADE`.
    - Direct deletion of a `detour_trip` row currently fails with an H2 foreign key constraint violation unless child rows are deleted first or foreign keys are migrated to cascade.
  - Catalog isolation: Catalog tables (`flight_instance`, `accommodation_nightly_inventory`, `rental_unit_occupancy`) contain inventory data. Drafts and Planned snapshots only reference catalog IDs or copy facts without holding reservations. Deleting alternatives does not mutate catalog rows.
  - Phase 6 forward compatibility: No booking tables exist in Phase 3. The deletion contract must specify that permanent deletion is permitted only when no booking has ever existed, leaving Phase 6 to enforce this guard once bookings are introduced.

## Repository State

- **Commit**: `ff821cf059f187952fb6addc92f4ace8a8034d7a`
- **Branch**: `main`
- **Working Tree**: clean
- **Local Time**: 2026-09-21T11:36:48-07:00
- **Platform**: Java 21, Spring Boot 4.1.0, Maven 3.9, H2 Database 2.4.240, Flyway 14 migrations (`V1`–`V14`).
- **Test Status**: All 29 integration tests in `app.detour.trip.TripApiIntegrationTest` and restart tests in `app.detour.trip.TripApplicationRestartIntegrationTest` pass cleanly.

## Current Behavior and Data Flow

### 1. Identity Profile vs Trip Retrieval
- Currently, when a client calls `GET /api/profile`:
  - `IdentityController.profile(@AuthenticationPrincipal DetourUserPrincipal principal)` extracts `principal.userId()`.
  - `IdentityService.profile(long userId)` queries `detour_user` by ID and returns `new ProfileResponse(user.canonicalEmail())`.
  - In frontend `frontend/src/api/identityApi.ts:52-59`, `getProfile` requests `/api/profile` and expects an object with an `email` string property.
- When a client calls `GET /api/trips/{tripId}`:
  - `TripController.detail(principal, tripId)` validates the UUID string, calls `TripService.detail(ownerUserId, tripId)`.
  - `TripService.detail` delegates to `JdbcTripRepository.findByPublicIdAndOwnerUserId(UUID publicId, long ownerUserId)`.
  - `JdbcTripRepository` queries `detour_trip` joined with `catalog_destination`, then executes separate queries to load travelers (`detour_trip_traveler`), drafts (`detour_trip_draft`) and their selections, and planned itineraries (`detour_planned_itinerary`) and their snapshots.
  - If the trip belongs to another user or does not exist, `findByPublicIdAndOwnerUserId` returns `Optional.empty()`, resulting in `404 RESOURCE_NOT_FOUND`.
  - The endpoint returns `TripResponse` containing trip metadata, drafts, planned items, and unified alternatives.
- There is currently no endpoint or repository query to list all Trips for an authenticated user.

### 2. Temporal Classification (Upcoming vs Past)
- `detour_trip` contains `start_date DATE` and `end_date DATE`.
- No read-time calculation or database column classifies Trips as Upcoming or Past.
- There is no background job, scheduler, or cron updating trip status.

### 3. Alternative Expiration and Promotion Readiness
- `TripAlternative` sealed interface in `PlannedItinerary.java:9-13` is implemented by `TripDraft` (`lifecycle() == "DRAFT"`) and `PlannedItinerary` (`lifecycle() == "PLANNED"`).
- `TripService.promoteDraft` (lines 168–180):
  1. Finds the owned Trip and Draft.
  2. Resolves draft selections against current catalog constraints via `trips.resolveSelectionsForPromotion(trip, draft)`.
  3. Checks readiness issues via `readinessIssues(trip, resolved)`:
     - All traveler ages must be present (`trip.travelerAges() != null`).
     - At least one traveler must be an adult (`age >= 18`).
     - Budget must be specified (`trip.budgetCents() != null`).
     - At least one component must be selected.
  4. If readiness fails, throws `400 PLANNING_NOT_READY`.
  5. Atomically advances trip version and draft version via `advanceVersionForDraft`.
  6. Inserts a new `detour_planned_itinerary` and copies component snapshots.
  - **Absence of expiration check**: `promoteDraft` does not inspect current time or compare the departure date to a clock. An unbooked Draft whose departure date has already passed can currently be promoted to Planned without error.

### 4. Alternative Deletion Data Flow
- `DELETE /api/trips/{tripId}/drafts/{draftId}`:
  - Payload: `{"expectedVersion": V, "expectedDraftVersion": DV}`.
  - `TripService.deleteDraft` advances both parent and draft versions using `advanceVersionForDraft(tripId, ownerUserId, expectedVersion, draftId, expectedDraftVersion)`. If either version doesn't match, throws `409 VERSION_CONFLICT`.
  - Executes `DELETE FROM detour_trip_draft WHERE trip_id = ? AND id = ?`. Child rows in selection tables cascade-delete.
  - Returns updated `TripResponse`.
- `DELETE /api/trips/{tripId}/alternatives/{alternativeId}`:
  - Payload: `{"expectedVersion": V, "expectedDraftVersion": DV, "confirmed": true/false}`.
  - If target is a Draft: requires `expectedDraftVersion != null`, validates versions, deletes draft.
  - If target is a Planned itinerary: requires `confirmed == Boolean.TRUE`, forbids `expectedDraftVersion`, advances parent version via `advanceVersion`, deletes from `detour_planned_itinerary`. Child snapshot rows cascade-delete.
  - Returns updated `TripResponse`.

### 5. Whole-Trip Deletion
- Not implemented. No controller route, service method, or repository method exists for deleting a Trip aggregate.
- No confirmation schema exists for listing expected Draft and Planned counts to be removed.

## Key Components

- `src/main/java/app/detour/identity/IdentityController.java:65-68` — maps `GET /api/profile`, currently returning only `{email: ...}`.
- `src/main/java/app/detour/identity/IdentityService.java:44-48` — constructs `ProfileResponse(user.canonicalEmail())`.
- `src/main/java/app/detour/identity/ProfileResponse.java:3-4` — single-field record for user email.
- `src/main/java/app/detour/trip/TripController.java:18-85` — REST endpoints for `/api/trips`; lacks collection `GET /api/trips` and `DELETE /api/trips/{tripId}`.
- `src/main/java/app/detour/trip/TripService.java:49-57` — `detail(ownerUserId, tripId)` retrieves a single Trip aggregate.
- `src/main/java/app/detour/trip/TripService.java:156-165` — `deleteDraft` handles draft removal with parent/draft version checks.
- `src/main/java/app/detour/trip/TripService.java:168-180` — `promoteDraft` handles Draft -> Planned promotion without temporal expiration check.
- `src/main/java/app/detour/trip/TripService.java:202-217` — `deleteAlternative` handles unified alternative deletion, gating Planned deletion on `confirmed == true`.
- `src/main/java/app/detour/trip/TripService.java:412-419` — `readinessIssues` validates ages, adult presence, budget, components; lacks expiration evaluation.
- `src/main/java/app/detour/trip/TripRequests.java:31-33, 70-79` — request records and JSON parsing for alternative duplicate and delete; lacks `TripDelete` request model.
- `src/main/java/app/detour/trip/TripResponse.java:7-19` — top-level Trip response record.
- `src/main/java/app/detour/trip/AlternativeResponse.java:9` — `AlternativeResponse(id, lifecycle, version, selections)`.
- `src/main/java/app/detour/trip/TripRepository.java:8-64` — repository interface; lacks user-level trip query and trip deletion.
- `src/main/java/app/detour/trip/JdbcTripRepository.java:54-76` — `findByPublicIdAndOwnerUserId` and `loadTrip`.
- `src/main/java/app/detour/trip/JdbcTripRepository.java:108-109` — `deleteDraft` and `deletePlanned`.
- `src/main/resources/db/migration/V12__create_owned_trip_and_initial_draft_schema.sql:1-48` — schema for `detour_trip`, `detour_trip_traveler`, `detour_trip_draft`.
- `src/main/resources/db/migration/V13__allow_multiple_component_empty_drafts.sql:1-4` — drops unique draft constraint on `trip_id`.
- `src/main/resources/db/migration/V14__create_draft_selection_and_planned_snapshot_schema.sql:1-97` — draft selections and planned snapshots with cascade delete on alternative keys.
- `src/main/java/app/detour/security/SecurityConfiguration.java:35-57` — security filter chain requiring authentication for `/api/trips/**` and `/api/profile`.
- `frontend/src/api/identityApi.ts:52-59` — frontend API client reading `/api/profile`.

## Affected Areas

| Area | Current behavior and evidence |
| --- | --- |
| Profile Projection Endpoint | No Trip profile projection exists. `GET /api/profile` (`IdentityController.java:65`) returns `{email: ...}`. No `GET /api/trips` endpoint exists on `TripController`. `JdbcTripRepository` has no method to query all trips for a user. |
| Deterministic Ordering & Tie-Breaker | `detour_trip` has columns `id` (auto-increment primary key), `public_id` (UUID), `start_date`, and `end_date`. No ordering logic exists for owner-scoped Trip lists. Multiple trips with the same `start_date` have no established tie-breaker ordering in code. |
| Temporal Classification (Upcoming vs Past) | Trips have `start_date` and `end_date` in March 2027. There is no code evaluating whether a Trip is Upcoming or Past at read time. No scheduled database mutation exists. |
| Expiration Boundary & Alternative Status | `TripDraft` reports lifecycle `"DRAFT"`, and `PlannedItinerary` reports lifecycle `"PLANNED"`. Neither has an Expired status or expiration evaluation. Departure date boundary (`startDate` midnight in `America/Los_Angeles`) is not computed. Expired drafts are not blocked from promotion in `TripService.promoteDraft` (`TripService.java:168-180`). |
| Application Clock Injection | No `Clock` bean or abstraction exists in the codebase. All date validations use literals or static boundaries. Tests have no mechanism to control application time. |
| Draft Deletion | `DELETE /api/trips/{tripId}/drafts/{draftId}` and `DELETE /api/trips/{tripId}/alternatives/{alternativeId}` exist and enforce `expectedVersion` and `expectedDraftVersion`. Deleting a draft cascades to its selections in `detour_trip_draft_*_selection`. |
| Planned Itinerary Deletion | `DELETE /api/trips/{tripId}/alternatives/{alternativeId}` handles Planned deletion when `confirmed == true`. Deleting a planned itinerary cascades to snapshot tables. |
| Permanent Trip Deletion | `DELETE /api/trips/{tripId}` does not exist. No service or repository method exists to delete a Trip aggregate. No confirmation payload exists to receive or check expected draft/planned counts. |
| Database Relational Integrity & Cascades | `detour_planned_itinerary` has `ON DELETE CASCADE` referencing `detour_trip`. However, `detour_trip_traveler` and `detour_trip_draft` foreign keys to `detour_trip` do not have `ON DELETE CASCADE`. Direct deletion on `detour_trip` fails without deleting children first or altering schema. |
| Catalog Inventory Isolation | Catalog inventory tables (`flight_instance`, `accommodation_nightly_inventory`, `rental_unit_occupancy`) store seats, room inventory, and vehicle occupancy. Alternative operations do not alter these rows. Trip deletion must also preserve catalog inventory. |
| Phase 6 Booking History Protection | Phase 3 has no bookings. The deletion contract requires blocking permanent deletion if any booking has ever existed. In Phase 3, this condition trivially holds, but the contract and interface boundary must explicitly accommodate Phase 6. |

## Existing Tests and Fixtures

- `src/test/java/app/detour/trip/TripApiIntegrationTest.java`:
  - `updatesDuplicatesDeletesAndConflictsWithoutLostWrites` (lines 209–233): tests Draft deletion via `DELETE /api/trips/{tripId}/drafts/{draftId}` with version progression and conflict handling.
  - `promotesAReadyDraftAsAnIndependentImmutablePlannedSnapshot` (lines 302–334): tests Planned deletion via `DELETE /api/trips/{tripId}/alternatives/{alternativeId}`, verifying that `confirmed: true` is required and unconfirmed requests return 400.
  - `alternativeLifecycleMutationsDoNotRevealForeignTargets` (lines 475–510): verifies that foreign alternative deletion requests return 404 without disclosure.
  - `plannedLifecycleLeavesCatalogInventoryUntouched` (lines 594–631): verifies that promotion, duplicate, and alternative deletion leave catalog inventory unchanged.
  - `sameVersionPromotionAndDuplicateRacesHaveOneCompleteWinner` (lines 539–591): verifies concurrent mutation safety with optimistic locking.
  - `selectiveDuplicationRejectsInvalidOrUnauthorizedSourcesWithoutDisclosure` (lines 884–964): verifies foreign ID nondisclosure and validation rules.
- `src/test/java/app/detour/trip/TripApplicationRestartIntegrationTest.java`:
  - Lines 21–102: tests starting the application against a file-based H2 database, mutating data, stopping, and restarting, verifying persistence across restart.
- `src/test/java/app/detour/identity/IdentityApiIntegrationTest.java`:
  - Lines 40–55: verifies `GET /api/profile` returns `{email: "..."}` and no password hash or internal ID.
- `src/test/java/app/detour/catalog/CatalogTemporalMoneyRoundTripIntegrationTest.java`:
  - Lines 36–39, 57–60: retrieves airport timezones from `catalog_airport.time_zone_id` (`PDX` is `America/Los_Angeles`).

### Test Coverage Gaps for Ticket Scope
- No integration test verifies profile projection of Trips (grouping into Upcoming and Past, alternative counts, deterministic sorting by start date and tie-breaker).
- No test verifies date-derived transition from Upcoming to Past across the trip end date boundary.
- No test verifies alternative transition to Expired at midnight on the departure date in `America/Los_Angeles`.
- No test verifies clock injection immediately before versus at the expiration boundary (e.g. 23:59:59.999 vs 00:00:00).
- No test verifies clock behavior around Daylight Saving Time changes (e.g. March 14, 2027 in `America/Los_Angeles`).
- No test verifies that an Expired Draft is rejected when attempting promotion.
- No test exists for `DELETE /api/trips/{tripId}`, stale confirmation counts, stale trip version, or cascade deletion across all child tables.

## Dependencies and Operational Constraints

- **Platform**: Spring Boot 4.1.0, Java 21, H2 2.4.240, Flyway.
- **Fixed Origin and Timezone**: DeTour departure origin is fixed to `PDX` (`America/Los_Angeles`). All expiration calculations for departure dates evaluate at the start of day (midnight 00:00:00) in `America/Los_Angeles`.
- **Daylight Saving Time (DST)**: In 2027, US Daylight Saving Time begins on Sunday, March 14 at 02:00:00 (clocks jump from UTC-8 to UTC-7). Calculations using `ZoneId.of("America/Los_Angeles")` must handle this offset shift accurately.
- **Transactional Atomicity**: Trip deletion and alternative deletion must run under `@Transactional`. Failure in any sub-operation must roll back completely.
- **Optimistic Concurrency**:
  - `detour_trip` versioning: `version` must match `expectedVersion`.
  - Trip deletion confirmation must match both the Trip version and the exact alternative counts (Draft and Planned) expected by the client. Stale confirmation must fail without deleting anything.
- **Nondisclosure & Isolation**: Cross-user requests to view, query, or delete Trips must return 404 (or omit other users' trips from profile queries), never revealing whether a foreign ID exists.
- **Forward-Compatibility for Phase 6**:
  - In Phase 3, no bookings exist. Permanent deletion of a Trip is permitted if confirmation matches.
  - In Phase 6, if any booking has ever existed (active or canceled), permanent deletion must be rejected, and the Trip must instead be retained under Canceled Trips. The Phase 3 deletion contract must be designed so this guard can be introduced without altering existing Draft, Planned, Expired, Upcoming, or Past semantics.

## Historical Context

- **P00-T02 ADR** (`ai/thoughts/architecture/2026-09-17-p00-t02-detour-replacement-architecture.md`): Established deterministic Java services and the enforceable relational ownership path `User -> Trip -> Itinerary/Booking`.
- **P01-T02 & P01-T03** (`ai/thoughts/tickets/2026-09-17-p01-t02-establish-secure-user-identity.md`): Delivered authenticated user identity, password change, and the baseline `GET /api/profile` returning only email. The tickets explicitly excluded Trip grouping/status cards, noting they belong to Phase 3.
- **P03-T01** (`d2f236c`): Established `detour_trip`, `detour_trip_traveler`, and initial component-empty Draft.
- **P03-T02** (`f0a7508`): Added multiple Draft alternatives per Trip, optimistic locking on parent and draft, and Draft deletion.
- **P03-T03** (`47db74e`): Added immutable Planned snapshots, Draft selection tables, and Planned alternative deletion gated on `confirmed == true`.
- **P03-T04** (`e3c995c`): Added in-place shared-detail revision revalidation and selective Trip duplication (`/duplicate` and `/revisions`).
- **Phase 3 Specification** (`ai/thoughts/phases/phase-3-trips-itineraries-and-profile.md:39-49`): Work package 3.4 outlines:
  - Upcoming trips ordered by start date with nested alternative counts/statuses.
  - Past trips based on end date rather than a scheduled status mutation.
  - Expired unbooked alternatives at the start of departure date in `America/Los_Angeles`.
  - Delete Draft, confirmation-gated Delete Planned, and Delete Trip guarded by confirmation of draft and planned counts that will be removed.
- **Downstream P03-T06** (`ai/thoughts/tickets/2026-09-19-p03-t06-deliver-trips-profile-experience.md`): Implements the responsive frontend profile UI, consuming the projection and deletion contracts delivered by P03-T05.

## Open Questions

1. **Profile Projection Endpoint URI and Structure**:
   - Should the profile projection be served from `GET /api/profile` (extending the current response to include `upcoming` and `past` trips alongside `email`), or from a dedicated collection endpoint `GET /api/trips` (or `GET /api/profile/trips`)?
   - If served from `GET /api/profile`, `frontend/src/api/identityApi.ts:52-59` already consumes `/api/profile` and reads only `email`, meaning extending this JSON object would be backward-compatible with existing frontend tests.
   - Alternatively, if `GET /api/trips` is introduced, it cleanly separates trip collection queries under `TripController` from user identity under `IdentityController`.
2. **Deterministic Tie-Breaker for Trip Sorting**:
   - When multiple upcoming trips have the same `start_date`, what secondary tie-breaker should be used? `trip.id ASC` (database sequence ID), `trip.public_id ASC` (UUID lexicographical), or `trip.display_label ASC`?
   - How should past trips be sorted? By `start_date DESC` or `end_date DESC`, and with what tie-breaker?
3. **Trip Deletion Confirmation Contract and Error Codes**:
   - What exact fields should the `DELETE /api/trips/{tripId}` request payload require? E.g., `{"expectedVersion": long, "expectedDraftCount": int, "expectedPlannedCount": int, "confirmed": boolean}`?
   - When a confirmation is stale (e.g. `expectedDraftCount` or `expectedPlannedCount` does not match the actual counts currently in the Trip), what HTTP status code and error code should be returned? (e.g., `409 VERSION_CONFLICT` with current counts, or `409 STALE_CONFIRMATION`, or `400 VALIDATION_FAILED`)?
4. **Alternative Representation in Profile Projection**:
   - Should the profile projection return summary counts only (e.g. `draftCount`, `plannedCount`, `expiredAlternativeCount`, `bookedCount`), or should it also include nested lists of alternative items (e.g. `id`, `type`, `version`, `status` / `expired`)?
   - Providing alternative lists with IDs allows downstream P03-T06 to offer direct actions (duplicate, delete alternative) directly from the profile card hierarchy without requiring extra API requests for each Trip.
5. **Database Foreign Key Cascade vs Repository Deletion**:
   - `detour_trip_traveler` and `detour_trip_draft` do not have `ON DELETE CASCADE` foreign keys to `detour_trip`.
   - Should a Flyway migration `V15` add `ON DELETE CASCADE` to these constraints, or should repository logic explicitly delete traveler and draft child rows before deleting the `detour_trip` row, or both?
6. **Clock Injection Architecture**:
   - How should the controllable `Clock` be structured in Spring? Should a `@Bean Clock` bean defaulting to `Clock.system(ZoneId.of("America/Los_Angeles"))` be injected into `TripService`, with test configuration or a mutable test clock wrapper providing deterministic time control in tests?
