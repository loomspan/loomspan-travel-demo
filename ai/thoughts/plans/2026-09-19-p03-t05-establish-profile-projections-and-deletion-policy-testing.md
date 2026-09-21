# Date-Derived Profile Views and Safe Deletion Policy Testing Plan

## Change Summary
Establish test coverage for:
1. Owner-scoped profile projection (`GET /api/profile` and `GET /api/trips`) partitioning Trips into Upcoming and Past by date, ordering upcoming Trips by start date with deterministic tie-breaker, and projecting accurate Draft, Planned, and Expired alternative counts and statuses.
2. Controllable application clock proving the exact millisecond transition to Expired at departure midnight in `America/Los_Angeles` (and across US DST offset changes), and rejecting promotion of expired Drafts (`400 ALTERNATIVE_EXPIRED`).
3. Permanent Trip deletion (`DELETE /api/trips/{tripId}`) guarded by `expectedVersion`, `expectedDraftCount`, `expectedPlannedCount`, and `confirmed: true`, failing on stale confirmations or version conflicts without partial deletion.
4. Atomic relational cascade of deleted Trips, catalog inventory preservation, cross-user non-disclosure, and Phase 6 booking history protection.
5. Date-derived status correctness across application restart without scheduled database mutations.

## Impacted Areas and Risks
| Category | Risk | Planned evidence |
| --- | --- | --- |
| Temporal Evaluation & Clock | Off-by-one or timezone offset errors around midnight or DST transitions could cause premature or missed alternative expiration. | Exact millisecond boundary tests (`23:59:59.999` vs `00:00:00`) in `America/Los_Angeles` and DST transition checks on March 14, 2027. |
| Promotion Lifecycle | Expired Drafts could be promoted after departure, creating invalid planned itineraries. | `promoteDraft` returns `400 ALTERNATIVE_EXPIRED` when clock is at or past departure date midnight. |
| Profile Projection & Tie-Breaker | Non-deterministic ordering when trips share `start_date`, or cross-user data leakage. | Create trips with identical `start_date` and assert strict sort by `id ASC`; verify another user sees empty profile. |
| Stale Confirmation on Trip Deletion | Concurrently created or deleted alternatives could be silently wiped out if counts are not verified. | Verify `DELETE /api/trips/{tripId}` fails with `409 STALE_CONFIRMATION` on draft or planned count mismatch. |
| Relational Cascade & Orphan Rows | Foreign key violations or orphaned child rows when deleting a Trip. | Verify `detour_trip`, `detour_trip_traveler`, `detour_trip_draft`, and `detour_planned_itinerary` (and selections/snapshots) are completely wiped. |
| Catalog Integrity | Deleting a Trip might accidentally cascade or delete catalog inventory rows. | Assert row counts of `flight_instance`, `accommodation_nightly_inventory`, and `rental_unit_occupancy` are identical before and after Trip deletion. |
| Downstream Booking History Guard | Future Phase 6 booking records could be deleted if Phase 3 allows unconditional deletion. | Explicit contract test ensuring `hasBookingHistory` blocks deletion with `409 CANNOT_DELETE_BOOKED_TRIP`. |
| Persistence Across Restart | System might rely on volatile or in-memory mutations rather than date derivation. | `TripApplicationRestartIntegrationTest` asserts status transitions correctly across application stop and start without DB mutations. |

## Existing Coverage and Environment Constraints
- `TripApiIntegrationTest.java`: 29 integration tests testing trip creation, detail, update, duplication, revisions, draft mutations, and planned promotions against in-memory H2.
- `TripApplicationRestartIntegrationTest.java`: Tests persistence and mutation across application stops and restarts against file-based H2.
- `IdentityApiIntegrationTest.java`: Tests registration, login, logout, password change, CSRF, and current baseline `GET /api/profile`.
- Environment constraints:
  - Timezone is fixed to `America/Los_Angeles` (PDX departure origin).
  - Tests run in Spring Boot test slice with `MockMvc`.
  - Clock control must be deterministic and must not disrupt existing tests.

## Failing Test First
- Name: `profileProjectionReturnsOwnerTripsPartitionedByDateWithAccurateCounts`
- Type: Integration Test (`@SpringBootTest` with `MockMvc`)
- Location: `src/test/java/app/detour/trip/TripApiIntegrationTest.java`
- Arrange/Act/Assert:
  - Arrange: Register a new user, create a Trip for March 10–15, 2027.
  - Act: Call `GET /api/trips` and `GET /api/profile`.
  - Assert:
    - Status is `200 OK`.
    - Response contains `upcoming` list with 1 Trip (correct ID, destination, dates, label, `draftCount: 1`, `plannedCount: 0`, `expiredAlternativeCount: 0`).
    - Response contains `past` list (empty).
- Expected pre-fix failure:
  - `GET /api/trips` returns `404 Not Found` (endpoint does not exist).
  - `GET /api/profile` does not contain `upcoming` or `past` fields (returns only `{"email": "..."}`).

## Tests to Add or Update

### 1. `profileProjectionPartitionsUpcomingAndPastWithDeterministicSort`
- Type: Integration Test (`@SpringBootTest`)
- Location: `src/test/java/app/detour/trip/TripApiIntegrationTest.java`
- Proves:
  - Trips are correctly partitioned into Upcoming vs Past based on end date.
  - Multiple upcoming trips sharing the same `start_date` are deterministically ordered by `start_date ASC, id ASC`.
  - Past trips are deterministically ordered by `start_date DESC, id DESC`.
- Inputs/fixture:
  - Set clock to `2027-03-08T12:00:00-08:00`.
  - Create Trip 1: March 10–14, 2027 (Upcoming).
  - Create Trip 2: March 10–16, 2027 (Upcoming, same start date as Trip 1).
  - Create Trip 3: March 2–5, 2027 (Past, end date March 5 has passed).
- Doubles or boundary isolation:
  - `TestClock` set to `2027-03-08T12:00:00-08:00`.
- Edge cases:
  - Exactly on the end date (e.g. `2027-03-05T23:59:59.999-08:00`): Trip 3 is still Upcoming.
  - Immediately after the end date (e.g. `2027-03-06T00:00:00.000-08:00`): Trip 3 transitions to Past.

### 2. `clockControlsExpirationAtDepartureMidnightAndBlocksPromotion`
- Type: Integration Test (`@SpringBootTest`)
- Location: `src/test/java/app/detour/trip/TripApiIntegrationTest.java`
- Proves:
  - Unbooked alternatives transition to Expired precisely at departure midnight (00:00:00) in `America/Los_Angeles`.
  - Expired Drafts cannot be promoted to Planned (`400 ALTERNATIVE_EXPIRED`).
  - Active Drafts immediately before departure midnight can be promoted normally.
- Inputs/fixture:
  - Create Trip with `startDate = 2027-03-10` and `endDate = 2027-03-15`. Complete traveler ages and budget. Select valid airfare and stay components.
  - Set clock to `2027-03-09T23:59:59.999-08:00`.
  - Assert `/api/trips` projection reports `expired: false`, `status: "DRAFT"`, `expiredAlternativeCount: 0`.
  - Duplicate draft so there are two drafts. Promote first draft: succeeds, becomes Planned.
  - Set clock to `2027-03-10T00:00:00.000-08:00`.
  - Assert `/api/trips` projection reports `expired: true` on both the remaining Draft and the Planned alternative; `expiredAlternativeCount: 2`.
  - Attempt to promote remaining expired draft: rejected with `400 ALTERNATIVE_EXPIRED`.
- Doubles or boundary isolation:
  - Injected `TestClock` updated between calls.
- Edge cases:
  - DST boundary: test departure date `2027-03-15` around the March 14, 2027 DST change (transitioning from UTC-8 to UTC-7). Expiration at midnight on March 15 corresponds to `2027-03-15T00:00:00-07:00` (`2027-03-15T07:00:00Z`).

### 3. `deletesNeverBookedTripAtomicallyWithCascadeAndPreservesCatalog`
- Type: Integration Test (`@SpringBootTest`)
- Location: `src/test/java/app/detour/trip/TripApiIntegrationTest.java`
- Proves:
  - Whole-trip deletion cascades across all child tables and leaves catalog inventory untouched.
- Inputs/fixture:
  - Create Trip with 2 travelers, 2 Drafts, and 1 Planned itinerary (with selections and snapshots).
  - Record counts of rows in `flight_instance`, `accommodation_nightly_inventory`, and `rental_unit_occupancy`.
  - Call `DELETE /api/trips/{tripId}` with:
    `{"expectedVersion": 3, "expectedDraftCount": 2, "expectedPlannedCount": 1, "confirmed": true}`.
- Assert:
  - Returns `204 No Content`.
  - Calling `GET /api/trips/{tripId}` returns `404 RESOURCE_NOT_FOUND`.
  - Direct JDBC queries assert 0 rows with `trip_id` in `detour_trip`, `detour_trip_traveler`, `detour_trip_draft`, `detour_trip_draft_airfare_selection`, `detour_trip_draft_stay_selection`, `detour_trip_draft_rental_selection`, `detour_planned_itinerary`, `detour_planned_airfare_snapshot`, `detour_planned_stay_snapshot`, `detour_planned_rental_snapshot`.
  - Catalog inventory row counts remain strictly unchanged.

### 4. `tripDeletionRejectsStaleConfirmationAndVersionConflicts`
- Type: Integration Test (`@SpringBootTest`)
- Location: `src/test/java/app/detour/trip/TripApiIntegrationTest.java`
- Proves:
  - Mismatched counts return `409 STALE_CONFIRMATION`.
  - Mismatched version returns `409 VERSION_CONFLICT`.
  - Missing or false confirmation returns `400 VALIDATION_FAILED`.
  - All rejections preserve the Trip and all its alternatives without partial deletion.
- Inputs/fixture:
  - Create Trip with 1 Draft and 0 Planned (`version: 0`).
  - Attempt delete with `expectedDraftCount: 2, expectedPlannedCount: 0, expectedVersion: 0, confirmed: true`: fails with 409 `STALE_CONFIRMATION`.
  - Attempt delete with `expectedDraftCount: 1, expectedPlannedCount: 1, expectedVersion: 0, confirmed: true`: fails with 409 `STALE_CONFIRMATION`.
  - Attempt delete with `expectedDraftCount: 1, expectedPlannedCount: 0, expectedVersion: 99, confirmed: true`: fails with 409 `VERSION_CONFLICT`.
  - Attempt delete with `confirmed: false`: fails with 400 `VALIDATION_FAILED`.
  - Verify `GET /api/trips/{tripId}` shows Trip still exists with version 0 and 1 Draft.

### 5. `tripOperationsEnforceOwnershipAndNondisclosure`
- Type: Integration Test (`@SpringBootTest`)
- Location: `src/test/java/app/detour/trip/TripApiIntegrationTest.java`
- Proves:
  - User B cannot see User A's trips in `/api/profile` or `/api/trips`.
  - User B calling `DELETE /api/trips/{tripAId}` receives `404 RESOURCE_NOT_FOUND` without leaking existence or metadata.
- Inputs/fixture:
  - User A creates Trip A.
  - User B calls `GET /api/trips` -> `upcoming` and `past` are empty.
  - User B calls `DELETE /api/trips/{tripAId}` with valid payload -> 404.
  - Verify Trip A still exists under User A.

### 6. `tripDeletionBlocksWhenBookingHistoryPresent`
- Type: Integration Test (`@SpringBootTest`)
- Location: `src/test/java/app/detour/trip/TripApiIntegrationTest.java`
- Proves:
  - Downstream Phase 6 guard: If `hasBookingHistory(tripId)` is true, deletion is blocked with `409 CANNOT_DELETE_BOOKED_TRIP`.
- Inputs/fixture:
  - Spy/stub `TripRepository.hasBookingHistory` returning `true` for a test trip.
  - Attempt `DELETE /api/trips/{tripId}` with matching confirmation.
  - Assert response is `409 CANNOT_DELETE_BOOKED_TRIP`.
  - Verify trip is not deleted.

### 7. `upcomingAndPastDeriveCorrectlyAcrossRestartWithoutMutation`
- Type: Restart Integration Test (`@SpringBootTest` with restart)
- Location: `src/test/java/app/detour/trip/TripApplicationRestartIntegrationTest.java`
- Proves:
  - Date-derived Upcoming vs Past status is evaluated at read time without any database status update, remaining correct across application shutdown and restart.
- Inputs/fixture:
  - File-based H2 database.
  - Create Trip ending March 5, 2027 while clock is on March 3, 2027 -> verified as Upcoming.
  - Restart application context with clock advanced to March 7, 2027.
  - Query profile -> Trip is now in Past.
  - Check database: no background job or status column was updated.

## Safe Verification Commands
- Focused: `./mvnw.cmd test -Dtest=TripApiIntegrationTest`
- Related suite: `./mvnw.cmd test -Dtest=TripApiIntegrationTest,IdentityApiIntegrationTest,TripApplicationRestartIntegrationTest`
- Full safe suite: `./mvnw.cmd test`

## Optional Developer Checks
- None.

## Exit Criteria
- [x] The planned red test fails for the intended reason before implementation, when applicable.
- [x] New and updated tests pass after implementation.
- [x] The broadest safe relevant repository test suite passes.
- [x] Acceptance criteria map to executable evidence.
- [x] Routine automated tests do not perform unintended live or destructive operations.
- [x] Material risks and edge cases identified above are covered.
- [x] Any optional check is reported as nonblocking and is not represented as already performed.
