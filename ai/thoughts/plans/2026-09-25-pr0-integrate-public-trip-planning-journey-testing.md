# Public Trip Planning Journey Testing Plan

## Change Summary
Final integration of public Trip start, one Working plan, independently dated named options, booking and migrated data; removal of the remaining alternate Draft-promotion action.

## Impacted Areas and Risks
| Category | Risk | Planned evidence |
| --- | --- | --- |
| Guest/auth handoff | Premature create or duplicate on retry | React interaction test plus packaged unauthenticated/owned API checks |
| Option lifecycle | Generic/unintended option or stale in-place update | Named option and legacy-route Java tests; React actions |
| Revision | Source names lost or booked source changed | `TripApiIntegrationTest` revision test |
| Date-based booking | Trip dates used instead of selected option dates | `BookingApiIntegrationTest` and packaged verifier |
| Migration | Populated alternatives or booked references lost | V18-to-current `TripModelForwardMigrationIntegrationTest` |
| Navigation/accessibility | Old terminology or unreachable keyboard controls | Existing React interaction tests; optional browser observation |

## Existing Coverage and Environment Constraints
Vitest/React Testing Library tests live under `frontend/src/*test.tsx`; Java JUnit/Spring/H2 integration tests live under `src/test/java`. The repository has `npm test`, `npm run build`, Maven wrapper `clean verify`, and an isolated `scripts/verify-packaged-release.ps1` runner. Catalog fixtures cover supported March 2027 dates. The packaged runner launches its own loopback server and temporary H2 database. It is an API-level release check, not browser automation; frontend coverage supplies UI evidence. No external credentials or production service are needed.

## Failing Test First
- Name: `legacyPromotionCannotCreateAnUnnamedSavedOption`
- Type: Spring MVC/H2 integration.
- Location: `src/test/java/app/detour/trip/TripApiIntegrationTest.java`
- Arrange/Act/Assert: Create an owned Trip and select a component in Working, POST the old `/drafts/{id}/plan` route, assert a non-success response and zero Saved options, then POST `/options` with a name and assert exactly one Saved option.
- Expected pre-fix failure: current `promoteDraft` returns 201 and inserts an automatically named option.

## Tests to Add or Update
### 1. `legacyPromotionCannotCreateAnUnnamedSavedOption`
- Type: Spring MVC/H2 integration.
- Location: `src/test/java/app/detour/trip/TripApiIntegrationTest.java`
- Proves: old endpoint cannot bypass deliberate named save; new route still works.
- Inputs/fixture: owned Trip and seeded eligible airfare.
- Doubles or boundary isolation: in-memory test database.
- Edge cases: stale version and foreign owner remain protected.

### 2. `revisionCopiesNamedOptionsWithoutChangingSource`
- Type: Spring MVC/H2 integration.
- Location: `src/test/java/app/detour/trip/TripApiIntegrationTest.java`
- Proves: copied names, new option IDs, revision dates, one Working row, source snapshot and booking stability.
- Inputs/fixture: two named source options, one with booking history, revised supported dates.
- Doubles or boundary isolation: H2 fixture and existing HTTP client.
- Edge cases: invalid component removed with revision summary, stale version, foreign source ID.

### 3. `guestDraftSurvivesAuthenticationAndFailedSaveRetry`
- Type: React interaction.
- Location: `frontend/src/PublicTripFlow.test.tsx`
- Proves: Home/Trips navigation, all entered values retained across auth and 401/save failure, one explicit successful create and no duplicate while pending.
- Inputs/fixture: mocked profile/auth and Trip API responses.
- Doubles or boundary isolation: API mocks; no live server.
- Edge cases: account switch and keyboard submission.

### 4. `workingPlanOptionsCompareUpdateAndBook`
- Type: React interaction tests.
- Location: `frontend/src/ProgressiveTripBuilder.test.tsx` and `frontend/src/ItineraryComparisonAndBookingReview.test.tsx`
- Proves: truthful Working status, two named option dates, compare, in-place update versus save-new, correct option in booking review and no old Draft promotion control.
- Inputs/fixture: two supported date ranges and component-bearing Working plan.
- Doubles or boundary isolation: API mocks.
- Edge cases: stale update, expired option, focus after dialog close and narrow viewport DOM state.

### 5. `migratesPopulatedAlternativesAndBookedReferences`
- Type: Flyway/H2 integration.
- Location: `src/test/java/app/detour/trip/TripModelForwardMigrationIntegrationTest.java`
- Proves: V18 populated alternatives become named dated options; redundant empties disappear; booking foreign keys and snapshot values remain intact.
- Inputs/fixture: existing V18 migration fixture.
- Doubles or boundary isolation: in-memory test database.
- Edge cases: several populated Drafts and a booked Planned row.

### 6. `booksAndCancelsUsingSelectedOptionDates`
- Type: Spring MVC/H2 integration.
- Location: `src/test/java/app/detour/booking/BookingApiIntegrationTest.java`
- Proves: option-specific expiry/booking/cancellation with differing Trip and option dates, ownership, version and idempotency boundaries.
- Inputs/fixture: two named options with independent dates.
- Doubles or boundary isolation: fixed test clock and H2 data.
- Edge cases: an expired earlier option, valid later option, concurrent/replayed booking covered by existing booking suite.

### 7. `packagedPublicToSavedOptionJourney`
- Type: isolated packaged API smoke.
- Location: `scripts/verify-packaged-release.mjs`
- Proves: unauthenticated Trip write denied, registration, one owned Trip/Working, two named dates, selection, booking/cancellation, restart and login, persisted identity and ownership.
- Inputs/fixture: temporary H2 database and seeded catalog.
- Doubles or boundary isolation: loopback Java child process and disposable temp directory.
- Edge cases: old promotion is rejected; new session required after restart.

## Safe Verification Commands
- Focused frontend: `npm test -- PublicTripFlow.test.tsx ProgressiveTripBuilder.test.tsx ItineraryComparisonAndBookingReview.test.tsx` from `frontend`.
- Focused backend: `./mvnw.cmd -DskipFrontend=true -Dtest=TripApiIntegrationTest,TripModelForwardMigrationIntegrationTest,BookingApiIntegrationTest test` from repository root.
- Related suite: `npm test` from `frontend`; `./mvnw.cmd -DskipFrontend=true test` from repository root.
- Full safe suite: `npm run build` from `frontend`; `./mvnw.cmd clean verify`; `./scripts/verify-packaged-release.ps1` from repository root, in that order.

## Optional Developer Checks
- At mobile width and with keyboard only, inspect Home → Trips → auth → Working → option dialogs → booking and focus restoration. This is a visual/accessibility observation and must be reported separately from automated pass results.

## Exit Criteria
- [ ] The legacy-promotion test fails for its intended reason before implementation.
- [x] New and updated tests pass after implementation.
- [x] The broadest safe relevant repository suites pass.
- [x] Every PR0 acceptance criterion has executable evidence at the available UI/API/migration boundary.
- [x] Routine tests and the packaged verifier touch only isolated test data.
- [x] Migration, booking dates, ownership, concurrency and recovery paths above are covered.
- [x] Optional browser observations are reported as optional, never as completed automation.

The legacy-promotion regression was added after the service change, so its planned red run was not performed. It passes against the final implementation. The packaged verifier uses the isolated H2 file and loopback server; the manual mobile and keyboard observation remains optional and unrun.
