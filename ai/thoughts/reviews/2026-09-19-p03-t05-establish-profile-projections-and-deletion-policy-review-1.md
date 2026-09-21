# P03-T05 — Establish Date-Derived Profile Views and Safe Deletion Policy Code Review — Cycle 1

## Scope and Repository State

- **Branch / Base**: `main` (commit `ff821cf059f187952fb6addc92f4ace8a8034d7a`)
- **Review Target**: Ticket `P03-T05` (`ai/thoughts/tickets/2026-09-19-p03-t05-establish-profile-projections-and-deletion-policy.md`)
- **Governing Plans**:
  - `ai/thoughts/plans/2026-09-19-p03-t05-establish-profile-projections-and-deletion-policy.md`
  - `ai/thoughts/plans/2026-09-19-p03-t05-establish-profile-projections-and-deletion-policy-testing.md`
- **Changed Artifacts Evaluated**:
  - Schema: `src/main/resources/db/migration/V15__cascade_trip_traveler_and_draft_deletion.sql`
  - Domain / Configuration: `src/main/java/app/detour/common/ClockConfiguration.java`
  - Projection DTOs: `src/main/java/app/detour/trip/AlternativeProfileSummary.java`, `TripProfileSummary.java`, `TripsProfileResponse.java`, `src/main/java/app/detour/identity/ProfileResponse.java`
  - Requests: `src/main/java/app/detour/trip/TripRequests.java` (added `TripDelete` record and `tripDelete` JSON parser)
  - Persistence: `src/main/java/app/detour/trip/TripRepository.java`, `JdbcTripRepository.java` (`findAllByOwnerUserId`, `deleteTrip`, `hasBookingHistory`)
  - Application Service: `src/main/java/app/detour/trip/TripService.java` (clock injection, `isPast`, `isExpired`, `tripsProfile`, `promoteDraft` expiration check, `deleteTrip`)
  - Web Controllers: `src/main/java/app/detour/trip/TripController.java` (`GET /api/trips`, `DELETE /api/trips/{tripId}`), `src/main/java/app/detour/identity/IdentityController.java` (`GET /api/profile`)
  - Tests: `src/test/java/app/detour/trip/TestClockConfiguration.java`, `TripApiIntegrationTest.java`, `TripApplicationRestartIntegrationTest.java`, `DetourApplicationTest.java`, `PhaseOneCatalogForwardMigrationIntegrationTest.java`
  - Ticket: `ai/thoughts/tickets/2026-09-19-p03-t05-establish-profile-projections-and-deletion-policy.md`

## Findings

No actionable findings.

## Findings Resolved in This Context

None.

## Acceptance-Criteria and Plan Conformance

| Criterion/decision | Code evidence | Test evidence | Result: implemented/partial/missing/safe deviation |
| --- | --- | --- | --- |
| The profile endpoint returns only the signed-in user's Trips, orders upcoming Trips by start date with a deterministic tie-breaker, and reports accurate Draft and Planned counts/statuses. | `TripService.tripsProfile`, `IdentityController.profile`, `TripController.list`, `JdbcTripRepository.findAllByOwnerUserId` | `TripApiIntegrationTest.profileProjectionReturnsOwnerTripsPartitionedByDateWithAccurateCounts`, `TripApiIntegrationTest.profileProjectionPartitionsUpcomingAndPastWithDeterministicSort` | implemented |
| A controllable clock proves the precise transition to Expired at midnight on the departure date in `America/Los_Angeles`, and expired unbooked alternatives cannot be promoted or booked. | `ClockConfiguration`, `TripService.isExpired`, `TripService.promoteDraft` | `TripApiIntegrationTest.clockControlsExpirationAtDepartureMidnightAndBlocksPromotion` | implemented |
| Upcoming versus Past changes through date-derived reads without a scheduled database mutation, and records remain correct across restart. | `TripService.isPast(LocalDate endDate)` | `TripApplicationRestartIntegrationTest.upcomingAndPastDeriveCorrectlyAcrossRestartWithoutMutation` | implemented |
| Delete Draft, Delete Planned itinerary, and Delete Trip have distinct guarded behavior; stale confirmations and cross-user requests fail without partial deletion or protected-data disclosure. | `TripService.deleteDraft`, `deleteAlternative`, `deleteTrip` | `TripApiIntegrationTest.tripDeletionRejectsStaleConfirmationAndVersionConflicts`, `tripOperationsEnforceOwnershipAndNondisclosure` | implemented |
| An eligible never-booked Trip is deleted atomically only after confirmation that lists every Draft and Planned alternative count that will disappear. | `TripRequests.tripDelete`, `TripService.deleteTrip`, `JdbcTripRepository.deleteTrip`, `V15__cascade_trip_traveler_and_draft_deletion.sql` | `TripApiIntegrationTest.deletesNeverBookedTripAtomicallyWithCascadeAndPreservesCatalog` | implemented |
| No Phase 3 deletion operation models, accepts, or removes Booked/Canceled Booking history, and the downstream requirement for Phase 6 to block permanent deletion after any Booking is explicit in the deletion contract. | `TripRepository.hasBookingHistory`, `TripService.deleteTrip` guard returning 409 `CANNOT_DELETE_BOOKED_TRIP` | `TripApiIntegrationTest.tripDeletionBlocksWhenBookingHistoryPresent` | implemented |
| Profile queries avoid leaking another user's counts or identifiers and remain deterministic when multiple Trips share a date. | `JdbcTripRepository.findAllByOwnerUserId` with `WHERE owner_user_id = ? ORDER BY start_date ASC, id ASC` | `TripApiIntegrationTest.profileProjectionPartitionsUpcomingAndPastWithDeterministicSort`, `tripOperationsEnforceOwnershipAndNondisclosure` | implemented |
| Phase 6 booking creation, inventory cancellation, confirmation references, and Canceled Trips UI are not introduced. | Diff inspection confirms absence of booking tables, booking controllers, or cancellation UI | Full codebase and test suite inspection | implemented |

## Active Project Guardrails

- `ai/thoughts/design-lens.md`: None recorded.

## Open Questions and Assumptions

- None.

## Verification Results

- PASS — `./mvnw.cmd test` — Full repository test suite (61 tests across 12 test classes pass with 0 failures, 0 errors, 0 skipped).
- PASS — `./mvnw.cmd test -Dtest=TripApiIntegrationTest` — 36 integration tests passing in 5.6s, verifying all profile projection, expiration, DST boundary, cascade deletion, inventory isolation, and conflict guards.
- PASS — `./mvnw.cmd test -Dtest=TripApplicationRestartIntegrationTest` — 3 restart tests passing in 5.5s, confirming date-derived Upcoming vs Past across context stops and restarts without scheduled database mutations.
- PASS — `./mvnw.cmd test -Dtest=IdentityApiIntegrationTest` — 6 tests passing in 3.6s, confirming backward-compatible `GET /api/profile` response structure.
- PASS — `npm.cmd test` (in `frontend/`) — 13 tests passing across 3 test files, confirming frontend API client compatibility with extended profile envelope.

## Residual Risks and Optional Developer Checks

- **Residual Risks**: None. All database cascade deletions are backed by verified foreign keys (`V15`) and checked in integration tests; catalog inventory rows are explicitly counted and verified unaffected before and after deletion.
- **Optional Developer Checks**: None.

## Disposition

- `clean`
