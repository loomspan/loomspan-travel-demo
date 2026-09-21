# Date-Derived Profile Views and Safe Deletion Policy Implementation Plan

## Overview
- Ticket: `ai/thoughts/tickets/2026-09-19-p03-t05-establish-profile-projections-and-deletion-policy.md`
- Research: `ai/thoughts/research/2026-09-19-p03-t05-establish-profile-projections-and-deletion-policy.md`
- Outcome: Deliver an owner-scoped profile projection partitioning Trips into Upcoming and Past by date, calculate unbooked alternative expiration at departure midnight in `America/Los_Angeles` via an injected clock, block promotion of expired drafts, and provide guarded permanent Trip deletion gated on version and exact Draft/Planned alternative counts with cascade cleanup, catalog isolation, and Phase 6 booking-history protection.

## Current State
- `IdentityController.java:65-68` exposes `GET /api/profile` returning only `ProfileResponse(email)` from `IdentityService.profile(userId)`.
- `TripController.java:18-85` provides `/api/trips` endpoints for create, detail, shared details replacement, duplicate, revisions, drafts, and alternative mutations/deletion, but lacks a collection endpoint (`GET /api/trips`) and Trip deletion (`DELETE /api/trips/{tripId}`).
- `JdbcTripRepository.java:54-76` queries single trips via `findByPublicIdAndOwnerUserId(publicId, ownerUserId)`. There is no repository query to fetch all trips for a user or delete a trip aggregate.
- No `Clock` bean exists in the Spring application context. Dates are validated against static boundaries (`FIRST_SUPPORTED_DATE = 2027-03-01`, `LAST_SUPPORTED_DATE = 2027-03-31`). No temporal evaluation (Upcoming vs Past or alternative Expiration) exists.
- In `TripService.java:168-180`, `promoteDraft` validates readiness issues (traveler ages, adult presence, budget, components) but does not inspect current time or verify whether the draft's departure date has passed.
- Foreign keys on `detour_trip_traveler` and `detour_trip_draft` in `V12__create_owned_trip_and_initial_draft_schema.sql` lack `ON DELETE CASCADE`, while `detour_planned_itinerary` in `V14__create_draft_selection_and_planned_snapshot_schema.sql` has `ON DELETE CASCADE`. Direct deletion of a `detour_trip` row currently fails with a foreign key constraint violation.

## Desired End State
- `GET /api/profile` returns the authenticated user's email, alongside `upcoming` and `past` Trip profile projections, maintaining backward compatibility with `frontend/src/api/identityApi.ts`.
- `GET /api/trips` exposes the same structured `TripsProfileResponse` (`upcoming` and `past`) directly under the Trip resource hierarchy.
- Upcoming Trips are ordered by `start_date ASC, id ASC` (soonest first with database ID deterministic tie-breaker).
- Past Trips are ordered by `start_date DESC, id DESC` (most recent past departure first with deterministic tie-breaker).
- Upcoming vs Past is computed purely at read time based on the product's date semantics: a Trip is Past when the current date in `America/Los_Angeles` is after `trip.endDate()`. No scheduled database jobs or status mutations are used.
- Alternative expiration is computed at read time: an unbooked Draft or Planned alternative is Expired when current time is at or after departure midnight (00:00:00) on `trip.startDate()` in `America/Los_Angeles`.
- Application clock is controllable in deterministic tests via a configurable `Clock` bean, verifying behavior at exact millisecond boundaries (e.g. 23:59:59.999 vs 00:00:00) and across Daylight Saving Time transitions (e.g. March 14, 2027).
- Promotion of an expired Draft (`POST /api/trips/{tripId}/drafts/{draftId}/plan`) is rejected with `400 ALTERNATIVE_EXPIRED`.
- Permanent deletion of an entire Trip is supported via `DELETE /api/trips/{tripId}` guarded by confirmation containing `expectedVersion`, `expectedDraftCount`, `expectedPlannedCount`, and `confirmed: true`.
- Stale confirmations fail without partial deletion:
  - If `confirmed != true`: returns `400 VALIDATION_FAILED`.
  - If `expectedVersion != trip.version()`: returns `409 VERSION_CONFLICT`.
  - If `expectedDraftCount != actualDrafts || expectedPlannedCount != actualPlanned`: returns `409 STALE_CONFIRMATION`.
  - If foreign trip or non-existent trip: returns `404 RESOURCE_NOT_FOUND` without disclosure.
- Deletion cascades cleanly to `detour_trip_traveler`, `detour_trip_draft` (and selections), and `detour_planned_itinerary` (and snapshots), leaving catalog inventory untouched.
- An explicit downstream contract guard (`hasBookingHistory`) is established, blocking permanent deletion if a Booking ever existed (`409 CANNOT_DELETE_BOOKED_TRIP`), ready for Phase 6.

## Scope
### In scope
- Flyway migration `V15` adding `ON DELETE CASCADE` to `detour_trip_traveler` and `detour_trip_draft`.
- `ClockConfiguration` providing a Spring `@Bean Clock` defaulting to `America/Los_Angeles` (`PDX_ZONE`).
- Repository methods `findAllByOwnerUserId(long ownerUserId)`, `deleteTrip(long tripId, long ownerUserId)`, and `hasBookingHistory(long tripId)`.
- Service logic for date-derived Upcoming vs Past and Expired alternative evaluation.
- Promotion rejection for expired Drafts.
- `DELETE /api/trips/{tripId}` endpoint with optimistic concurrency, count matching, and cascade deletion.
- Profile projection endpoint `GET /api/profile` (extended with `upcoming` and `past`) and collection endpoint `GET /api/trips`.
- Unit and integration tests covering clock control, exact millisecond boundary transitions, DST offset shifts, deterministic ordering, stale confirmation handling, cross-user isolation, and catalog non-mutation.

### Out of scope
- Final responsive UI profile card presentation (owned by P03-T06).
- Creating Booking records or Canceled Booking history (owned by Phase 6).
- Inventory booking/cancellation transactions (owned by Phase 6).
- Custom origin airports or non-PDX timezones (origin is fixed to PDX).
- Version 2 Events, collaborative trips, or voting.

## Active Project Guardrails
None recorded in `ai/thoughts/design-lens.md`.

## Impact and Risk Analysis
- **Temporal/DST Accuracy**: Departure origin is fixed to PDX (`America/Los_Angeles`). In 2027, US DST begins on March 14 (clocks skip 02:00 to 03:00, moving from UTC-8 to UTC-7). Evaluating `startDate.atStartOfDay(ZoneId.of("America/Los_Angeles")).toInstant()` natively handles DST offset transitions without manual math.
- **Clock Injection in Tests**: Without a controllable `Clock`, integration tests cannot deterministically verify the exact millisecond transition before vs at midnight or simulate future dates. Providing a mutable test clock wrapper or test configuration allows tests to advance time deterministically in a single Spring context.
- **Relational Integrity on Deletion**: In Phase 3, deleting a Trip must cascade through travelers, drafts, selections, planned itineraries, and snapshots. If foreign keys lack cascade, deletion fails with DB constraint violations. Migration `V15` adds `ON DELETE CASCADE` to remaining child foreign keys, guaranteeing atomic DB-level cleanup.
- **Stale Confirmation & Race Conditions**: A user deleting a Trip might have confirmed deletion based on 1 Draft and 0 Planned itineraries, while another tab created a Planned itinerary. Checking `expectedDraftCount` and `expectedPlannedCount` alongside `expectedVersion` prevents accidental deletion of unexpected itineraries.
- **Downstream Phase 6 Guard**: Phase 6 introduces immutable booking records and requires retaining canceled trips rather than deleting them. The `TripRepository` and `TripService` deletion contract must include an explicit `hasBookingHistory` check so Phase 6 can plug in `detour_booking` existence without altering the Phase 3 deletion signature or error handling.
- **Cross-User Security & Isolation**: Profile projections and deletion must be strictly owner-scoped. Queries filter by `owner_user_id`. Requesting or deleting a foreign trip must return `404 RESOURCE_NOT_FOUND` without leaking counts, versions, or existence.

## Implementation Approach
1. **Clock Bean and Temporal Logic**:
   - Create `app.detour.common.ClockConfiguration` defining `@Bean @ConditionalOnMissingBean Clock clock() { return Clock.system(ZoneId.of("America/Los_Angeles")); }`.
   - In `TripService`, inject `Clock`. Define helper methods `isPast(LocalDate endDate)` and `isExpired(LocalDate startDate)`.
   - Update `promoteDraft` to check `isExpired(trip.startDate())` and throw `400 ALTERNATIVE_EXPIRED`.
2. **Schema & Repository**:
   - Create `V15__cascade_trip_traveler_and_draft_deletion.sql` to alter `fk_detour_trip_traveler_trip` and `fk_detour_trip_draft_trip` with `ON DELETE CASCADE`.
   - In `TripRepository` and `JdbcTripRepository`, add:
     - `List<Trip> findAllByOwnerUserId(long ownerUserId)`: queries trips for owner ordered by `start_date ASC, id ASC`, batch-loading travelers, drafts, and planned items.
     - `void deleteTrip(long tripId, long ownerUserId)`: executes `DELETE FROM detour_trip WHERE id = ? AND owner_user_id = ?`.
     - `boolean hasBookingHistory(long tripId)`: returns `false` in Phase 3.
3. **Profile Projection Models & Endpoints**:
   - Create records:
     - `TripProfileSummary(UUID id, String destinationKey, String destinationName, LocalDate startDate, LocalDate endDate, String label, long version, String temporalStatus, int draftCount, int plannedCount, int expiredAlternativeCount, int bookedCount, boolean hasBookingHistory, List<AlternativeProfileSummary> alternatives)`
     - `AlternativeProfileSummary(UUID id, String lifecycle, Long version, String status, boolean expired)`
     - `TripsProfileResponse(List<TripProfileSummary> upcoming, List<TripProfileSummary> past)`
   - In `TripService`, implement `TripsProfileResponse tripsProfile(long ownerUserId)`:
     - Fetches owner's trips via `trips.findAllByOwnerUserId(ownerUserId)`.
     - Partitions into `upcoming` and `past` using `isPast(trip.endDate())`.
     - Sorts `upcoming` by `start_date ASC, id ASC`.
     - Sorts `past` by `start_date DESC, id DESC`.
     - Computes alternative statuses and counts:
       - For drafts: `expired = isExpired(trip.startDate())`, `status = expired ? "EXPIRED" : "DRAFT"`.
       - For planned: `expired = isExpired(trip.startDate())`, `status = expired ? "EXPIRED" : "PLANNED"`.
       - `expiredAlternativeCount = expiredDrafts + expiredPlanned`.
   - In `TripController`: add `@GetMapping TripsProfileResponse list(@AuthenticationPrincipal DetourUserPrincipal principal)`.
   - In `IdentityController`: update `@GetMapping("/profile")` to return `ProfileResponse` populated with `upcoming` and `past` from `TripService.tripsProfile(userId)`.
   - In `ProfileResponse`: extend constructor to accept `(String email, List<TripProfileSummary> upcoming, List<TripProfileSummary> past)` while keeping `(String email)` default constructor for registration.
4. **Trip Deletion Endpoint**:
   - In `TripRequests`: add record `TripDelete(long expectedVersion, int expectedDraftCount, int expectedPlannedCount, Boolean confirmed)` and JSON parser `tripDelete(JsonNode body)`.
   - In `TripService`: implement `deleteTrip(long ownerUserId, String tripId, TripRequests.TripDelete request)`:
     - Resolves owned trip (or throws 404).
     - Validates `Boolean.TRUE.equals(request.confirmed())` (or throws 400).
     - Checks `trips.hasBookingHistory(trip.id())` (throws 409 `CANNOT_DELETE_BOOKED_TRIP` if true).
     - Checks `trip.version() == request.expectedVersion()` (throws 409 `VERSION_CONFLICT` if false).
     - Checks `trip.drafts().size() == request.expectedDraftCount() && trip.planned().size() == request.expectedPlannedCount()` (throws 409 `STALE_CONFIRMATION` if false).
     - Executes `trips.deleteTrip(trip.id(), ownerUserId)`.
   - In `TripController`: add `@DeleteMapping("/{tripId}")` returning `204 No Content`.

---

## Phase 1: Database Schema & Repository Extensions

### Changes
- [x] `src/main/resources/db/migration/V15__cascade_trip_traveler_and_draft_deletion.sql` — Drop and recreate `fk_detour_trip_traveler_trip` and `fk_detour_trip_draft_trip` with `ON DELETE CASCADE`.
- [x] `src/main/java/app/detour/trip/TripRepository.java` — Add `List<Trip> findAllByOwnerUserId(long ownerUserId)`, `void deleteTrip(long tripId, long ownerUserId)`, and `boolean hasBookingHistory(long tripId)`.
- [x] `src/main/java/app/detour/trip/JdbcTripRepository.java` — Implement `findAllByOwnerUserId` (batch loading travelers, drafts, and planned itineraries ordered by `start_date ASC, id ASC`), `deleteTrip`, and `hasBookingHistory` (stub returning `false`).
- [x] `src/test/java/app/detour/trip/TripApiIntegrationTest.java` — Verify migration applies and repository methods function under clean transaction boundaries.

### Automated verification
- [x] `./mvnw.cmd test -Dtest=TripApiIntegrationTest` — Migrations up to V15 execute successfully, existing tests pass.

### Optional developer checks
- [ ] Inspect H2 schema table metadata to verify foreign keys specify cascade delete.

---

## Phase 2: Controllable Clock Configuration & Temporal Evaluation

### Changes
- [x] `src/main/java/app/detour/common/ClockConfiguration.java` — Create configuration class providing `@Bean @ConditionalOnMissingBean Clock clock()`, defaulted to `ZoneId.of("America/Los_Angeles")`.
- [x] `src/main/java/app/detour/trip/TripService.java` — Inject `Clock`. Add `isExpired(LocalDate startDate)` and `isPast(LocalDate endDate)` using `America/Los_Angeles`.
- [x] `src/main/java/app/detour/trip/TripService.java` — In `promoteDraft`, check `isExpired(trip.startDate())` and throw `ApiException(400, "ALTERNATIVE_EXPIRED", "Expired alternatives cannot be promoted.")`.
- [x] `src/test/java/app/detour/trip/TestClockConfiguration.java` — Provide a mutable `TestClock` bean for test execution allowing instant setting and clock manipulation.
- [x] `src/test/java/app/detour/trip/TripApiIntegrationTest.java` — Add test proving:
  - Draft is promotable immediately before departure midnight (e.g. `2027-03-09T23:59:59.999-08:00`).
  - Draft promotion is rejected at departure midnight (`2027-03-10T00:00:00-08:00`).
  - Transition behavior across US DST change on March 14, 2027.

### Automated verification
- [x] `./mvnw.cmd test -Dtest=TripApiIntegrationTest` — Clock injection and expiration enforcement pass.

### Optional developer checks
- [ ] None.

---

## Phase 3: Profile Projection & Trip Collection Endpoints

### Changes
- [x] `src/main/java/app/detour/trip/TripProfileSummary.java` (or inside `TripResponse.java`) — Define `TripProfileSummary`, `AlternativeProfileSummary`, and `TripsProfileResponse`.
- [x] `src/main/java/app/detour/trip/TripService.java` — Implement `TripsProfileResponse tripsProfile(long ownerUserId)`:
  - Fetches owned trips.
  - Partitions into Upcoming (`!isPast(trip.endDate())`) and Past (`isPast(trip.endDate())`).
  - Orders Upcoming by `start_date ASC, id ASC`.
  - Orders Past by `start_date DESC, id DESC`.
  - Maps alternatives with derived `expired` boolean, `status` ("DRAFT", "PLANNED", or "EXPIRED"), and counts (`draftCount`, `plannedCount`, `expiredAlternativeCount`, `bookedCount = 0`, `hasBookingHistory = false`).
- [x] `src/main/java/app/detour/trip/TripController.java` — Add `@GetMapping` returning `TripsProfileResponse`.
- [x] `src/main/java/app/detour/identity/ProfileResponse.java` — Update record to `(String email, List<TripProfileSummary> upcoming, List<TripProfileSummary> past)` with single-argument constructor for registration.
- [x] `src/main/java/app/detour/identity/IdentityController.java` — Inject `TripService`; in `GET /api/profile`, construct `ProfileResponse` with email and trips projection.
- [x] `src/test/java/app/detour/trip/TripApiIntegrationTest.java` — Add tests for `GET /api/trips` and `GET /api/profile`:
  - Signed-in user receives only their own Trips.
  - Correct partition into Upcoming vs Past based on injected clock.
  - Deterministic tie-breaker for identical start dates.
  - Accurate Draft, Planned, and Expired counts.
  - Cross-user isolation: user B sees empty profile and cannot access user A's trips.
- [x] `src/test/java/app/detour/identity/IdentityApiIntegrationTest.java` — Verify `GET /api/profile` continues to pass for registration, login, session isolation, and non-disclosure.
- [x] `src/test/java/app/detour/trip/TripApplicationRestartIntegrationTest.java` — Verify date-derived Upcoming vs Past projection produces correct results across application restart without database status mutation.

### Automated verification
- [x] `./mvnw.cmd test -Dtest=TripApiIntegrationTest,IdentityApiIntegrationTest,TripApplicationRestartIntegrationTest` — All profile projection tests pass cleanly.

### Optional developer checks
- [ ] None.

---

## Phase 4: Guarded Permanent Trip Deletion

### Changes
- [x] `src/main/java/app/detour/trip/TripRequests.java` — Add record `TripDelete(long expectedVersion, int expectedDraftCount, int expectedPlannedCount, Boolean confirmed)` and parser `tripDelete(JsonNode body)`.
- [x] `src/main/java/app/detour/trip/TripService.java` — Implement `deleteTrip(long ownerUserId, String tripId, TripRequests.TripDelete request)`:
  - Enforce ownership and existence (`404 RESOURCE_NOT_FOUND` if foreign or missing).
  - Enforce `confirmed == true` (`400 VALIDATION_FAILED` if not true).
  - Enforce Phase 6 booking history guard (`409 CANNOT_DELETE_BOOKED_TRIP` if booked).
  - Enforce version match (`409 VERSION_CONFLICT` if mismatched).
  - Enforce exact count match (`409 STALE_CONFIRMATION` if mismatched).
  - Execute atomic cascade deletion.
- [x] `src/main/java/app/detour/trip/TripController.java` — Add `@DeleteMapping("/{tripId}")` returning `204 No Content`.
- [x] `src/test/java/app/detour/trip/TripApiIntegrationTest.java` — Add comprehensive deletion tests:
  - Successful deletion of Trip with multiple Drafts and Planned itineraries; verify `detour_trip`, `detour_trip_traveler`, `detour_trip_draft`, and `detour_planned_itinerary` rows are removed.
  - Verification that catalog inventory rows (`flight_instance`, `accommodation_nightly_inventory`, `rental_unit_occupancy`) remain untouched.
  - Stale confirmation failure on count mismatch (`expectedDraftCount` or `expectedPlannedCount` incorrect) returning `409 STALE_CONFIRMATION` without partial deletion.
  - Version conflict failure on `expectedVersion` mismatch returning `409 VERSION_CONFLICT`.
  - Missing or false confirmation returning `400 VALIDATION_FAILED`.
  - Cross-user deletion attempt returning `404 RESOURCE_NOT_FOUND` without disclosure.
  - Verification that Phase 6 booking history guard prevents deletion when history exists.

### Automated verification
- [x] `./mvnw.cmd test -Dtest=TripApiIntegrationTest` — Full integration suite passes with all new deletion scenarios.

### Optional developer checks
- [ ] None.

---

## Test Strategy
- **Unit / Domain Level**:
  - Temporal evaluation: verify `isExpired` and `isPast` logic across edge cases: 23:59:59.999 before departure date vs 00:00:00 on departure date, DST leap on March 14, 2027.
- **Integration API Level (`TripApiIntegrationTest`)**:
  - `GET /api/profile` and `GET /api/trips` projection: Upcoming vs Past grouping, ordering with tie-breaker, accurate alternative counts and statuses.
  - Promotion rejection on expired drafts (`400 ALTERNATIVE_EXPIRED`).
  - Atomic Trip deletion with cascade verification across all child tables.
  - Catalog inventory isolation: count before vs count after deletion.
  - Stale confirmation and concurrency rejection (`409 STALE_CONFIRMATION`, `409 VERSION_CONFLICT`).
  - Authorization isolation: cross-user requests return 404 without disclosure.
- **Persistence Across Restart (`TripApplicationRestartIntegrationTest`)**:
  - Confirm date-derived status computation works across file-based H2 restart without scheduled mutations.

---

## Acceptance-Criteria Traceability

| Acceptance criterion | Planned code evidence | Planned test evidence |
| --- | --- | --- |
| The profile endpoint returns only the signed-in user's Trips, orders upcoming Trips by start date with a deterministic tie-breaker, and reports accurate Draft and Planned counts/statuses. | `TripService.tripsProfile`, `IdentityController.profile`, `TripController.list` | `TripApiIntegrationTest.profileReturnsOwnerTripsWithDeterministicSortAndAccurateCounts` |
| A controllable clock proves the precise transition to Expired at midnight on the departure date in `America/Los_Angeles`, and expired unbooked alternatives cannot be promoted or booked. | `ClockConfiguration`, `TripService.isExpired`, `TripService.promoteDraft` | `TripApiIntegrationTest.clockControlsExpirationAtMidnightAndBlocksPromotion` |
| Upcoming versus Past changes through date-derived reads without a scheduled database mutation, and records remain correct across restart. | `TripService.isPast` evaluated at read time | `TripApplicationRestartIntegrationTest.upcomingAndPastDeriveCorrectlyAcrossRestart` |
| Delete Draft, Delete Planned itinerary, and Delete Trip have distinct guarded behavior; stale confirmations and cross-user requests fail without partial deletion or protected-data disclosure. | `TripService.deleteDraft`, `TripService.deleteAlternative`, `TripService.deleteTrip` | `TripApiIntegrationTest.distinctDeletionGuardsAndNondisclosure` |
| An eligible never-booked Trip is deleted atomically only after confirmation that lists every Draft and Planned alternative count that will disappear. | `TripRequests.tripDelete`, `TripService.deleteTrip`, `V15__cascade_trip_traveler_and_draft_deletion.sql` | `TripApiIntegrationTest.deletesNeverBookedTripAtomicallyWithCountConfirmation` |
| No Phase 3 deletion operation models, accepts, or removes Booked/Canceled Booking history, and the downstream requirement for Phase 6 to block permanent deletion after any Booking is explicit in the deletion contract. | `TripRepository.hasBookingHistory`, `TripService.deleteTrip` check | `TripApiIntegrationTest.deletionContractBlocksWhenBookingHistoryPresent` |
| Profile queries avoid leaking another user's counts or identifiers and remain deterministic when multiple Trips share a date. | `JdbcTripRepository.findAllByOwnerUserId` with `owner_user_id = ?` and `ORDER BY start_date ASC, id ASC` | `TripApiIntegrationTest.profileQueriesIsolateUsersAndSortDeterministically` |
| Phase 6 booking creation, inventory cancellation, confirmation references, and Canceled Trips UI are not introduced. | Explicit scope boundaries maintained; no booking schema or endpoints added | Inspection of codebase diff and API schema |

---

## Risks and Rollback/Recovery
- **Flyway Migration `V15` Rollback**: The migration alters foreign keys to add `ON DELETE CASCADE`. If rolled back, foreign keys can be restored to non-cascading without data loss because child tables already enforce referential integrity.
- **Frontend Compatibility**: `frontend/src/api/identityApi.ts` reads `email` from `/api/profile`. Extending the JSON envelope with `upcoming` and `past` preserves `email`, avoiding breaks in existing frontend callers.
- **Catalog Integrity**: Deletion operates exclusively on `detour_trip` and its dependent tables (`detour_trip_traveler`, `detour_trip_draft`, `detour_planned_itinerary`). No SQL statement issues `DELETE` against catalog tables, ensuring catalog inventory isolation.

---

## References
- Ticket: `ai/thoughts/tickets/2026-09-19-p03-t05-establish-profile-projections-and-deletion-policy.md`
- Research: `ai/thoughts/research/2026-09-19-p03-t05-establish-profile-projections-and-deletion-policy.md`
- Downstream Ticket: `ai/thoughts/tickets/2026-09-19-p03-t06-deliver-trips-profile-experience.md`
- Phase 3 Specification: `ai/thoughts/phases/phase-3-trips-itineraries-and-profile.md`
- Schema Migrations: `src/main/resources/db/migration/V12__create_owned_trip_and_initial_draft_schema.sql`, `V14__create_draft_selection_and_planned_snapshot_schema.sql`
- Source Controllers & Services: `src/main/java/app/detour/trip/TripController.java`, `TripService.java`, `JdbcTripRepository.java`, `app/detour/identity/IdentityController.java`
