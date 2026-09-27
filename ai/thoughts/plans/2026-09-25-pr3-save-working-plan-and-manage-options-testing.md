# Save the Working Plan and Manage Options Testing Plan

## Change Summary

PR3 changes the Working save path, explicit Saved option lifecycle, per-option date validation, snapshot replacement, and the route by which a saved option becomes bookable. The plan must prove one Working row, explicit option writes, accurate status/conflict feedback, date isolation, and booking-history immutability.

## Impacted Areas and Risks

| Category | Risk | Planned evidence |
| --- | --- | --- |
| Working persistence | Local no-op or a stale tab creates records or overwrites changes | API row counts/version tests and frontend request-count/conflict tests |
| Option validation | Empty or budgetless Working is handled incorrectly | structural rejection and null-budget option tests |
| Snapshot graph | Update appends an option, leaves stale child rows, or partially commits | ID/count/child-graph assertions plus forced SQL failure rollback |
| Booking history | An active or canceled booking's option is changed | guarded update and unchanged booking/inventory assertions |
| Dates and pricing | Working dates affect other options, or prices do not match option dates | date edit/revalidation summary and snapshot isolation tests |
| Booking eligibility | Removing promotion gate permits ineligible or unavailable booking | Booking API eligibility, inventory, deadline, payment, and idempotency tests |
| UI decisions | Loading an option silently replaces different Working content | cancel/keep/replace UI tests and server-side confirmation rejection |

## Existing Coverage and Environment Constraints

The repository uses Spring Boot/JUnit integration tests with isolated H2 databases, MockMvc, and a controlled clock. `TripApiIntegrationTest` covers PR2 cardinality, versions, dated option snapshots, and old promotion behavior; `DraftReadinessAndPlannedSnapshotIntegrationTest` covers readiness and catalog failures; `BookingApiIntegrationTest` and `BookingCancellationIntegrationTest` cover atomic inventory, idempotency, owner isolation, and booked dates. `TripModelForwardMigrationIntegrationTest` covers V18-to-V19 normalization. Frontend uses Vitest and Testing Library in `frontend/src/ProgressiveTripBuilder.test.tsx`, `frontend/src/ItineraryComparisonAndBookingReview.test.tsx`, `frontend/src/PublicTripFlow.test.tsx`, and API mocks in `frontend/src/api/tripsApi.test.ts`. `mvn -q -DskipFrontend=true test` avoids npm installation during backend testing; `npm test` and `npm run build` run from `frontend`. Tests must not use the persistent default `./data/detour` database or live catalog/payment services.

## Failing Test First

- Name: `savesOneNamedOptionFromBudgetlessWorkingPlanAndRejectsEmptyWorkingPlan`
- Type: API integration test.
- Location: `src/test/java/app/detour/trip/TripApiIntegrationTest.java`.
- Arrange/Act/Assert: Create an owned Trip with null budget and one valid stay selection; call the explicit save-as-new-option route with a name and expected versions; assert one named option with Working dates, fresh priced snapshot, null budget fit, unchanged Working ID, and exactly one option row. In a separate new Trip with no components, call the same route and assert field validation, no option row, and unchanged versions.
- Expected pre-fix failure: the explicit route does not exist; the old promotion requires budget and cannot take a user name.

## Tests to Add or Update

### 1. `workingEditsPersistInOneRowAndNoOpActionsDoNotWrite`
- Type: API integration plus frontend component test.
- Location: `src/test/java/app/detour/trip/TripApiIntegrationTest.java`; `frontend/src/ProgressiveTripBuilder.test.tsx`.
- Proves: changed details and components survive reload; opening/searching/no-op local values do not POST options or create Drafts; save status reflects actual completion.
- Inputs/fixture: owned Trip with one initial Working row, API mocks for delayed, successful, and failed saves.
- Doubles or boundary isolation: isolated H2 and mocked frontend fetch.
- Edge cases: rapid edits during in-flight save, retry after network failure, stale second-tab version, pre-creation form typing.

### 2. `workingDateChangeRevalidatesWithoutMutatingSavedOptions`
- Type: API integration test.
- Location: `src/test/java/app/detour/trip/TripApiIntegrationTest.java`.
- Proves: changing Working dates after a Saved option exists revalidates flight/stay/rental, reports removed or adjusted selections and prices/capacity/eligibility, and leaves the Saved option's dates and snapshot bytes unchanged.
- Inputs/fixture: catalog-backed components on two supported date windows, one saved option, Working with selected components.
- Doubles or boundary isolation: H2 fixture catalog and controlled clock.
- Edge cases: one component remains valid while another is removed; absent budget; stale Draft version.

### 3. `saveAsNewOptionValidatesAndCapturesCurrentFacts`
- Type: API integration test.
- Location: `src/test/java/app/detour/trip/TripApiIntegrationTest.java`; `src/test/java/app/detour/trip/DraftReadinessAndPlannedSnapshotIntegrationTest.java`.
- Proves: explicit request inserts exactly one named, dated option with refreshed catalog prices; blank name or no structurally valid component writes nothing; budget absence is distinct from zero; saving does not reserve inventory.
- Inputs/fixture: stay, flight, and rental selections with controlled catalog price changes.
- Doubles or boundary isolation: H2 fixture catalog.
- Edge cases: stale parent/Draft version, malformed field, unavailable live inventory that is a booking concern rather than an option-save prerequisite.

### 4. `loadOptionRequiresExplicitWorkingReplacementAndKeepsSourceFixed`
- Type: API integration and frontend component tests.
- Location: `src/test/java/app/detour/trip/TripApiIntegrationTest.java`; `frontend/src/ItineraryComparisonAndBookingReview.test.tsx`; `frontend/src/ProgressiveTripBuilder.test.tsx`.
- Proves: differing Working contents require confirmation; cancel issues no request; keep-current creates a separately named option before load; confirmed load replaces only the single Working row and leaves source option unchanged.
- Inputs/fixture: Working and source option with different dates/selections.
- Doubles or boundary isolation: H2 database and mocked frontend requests.
- Edge cases: already-matching Working, stale source/Working versions, source whose selection is no longer structurally usable, pending autosave when the user opens an option.

### 5. `updateOptionReplacesOnlyUnbookedSnapshotAndBranchesOnRequest`
- Type: API integration test.
- Location: `src/test/java/app/detour/trip/TripApiIntegrationTest.java`.
- Proves: Update this option keeps option ID and row count, increments version, replaces every snapshot child and dated price; Save as new leaves source untouched and adds exactly one row; no user-facing revision record is added.
- Inputs/fixture: source option, changed Working dates and components, controlled catalog price change.
- Doubles or boundary isolation: H2 catalog and transaction.
- Edge cases: changed component type, empty Working, stale option version, forced child-insert failure and complete rollback, two same-version concurrent updates with one winner.

### 6. `bookedHistoryOptionCannotBeUpdatedAndBookingRulesStillHold`
- Type: booking API integration test.
- Location: `src/test/java/app/detour/booking/BookingApiIntegrationTest.java`; `src/test/java/app/detour/booking/BookingCancellationIntegrationTest.java`.
- Proves: active and canceled booking references prohibit in-place option mutation without altering snapshots or inventory; explicit new-option branch remains available. Option-specific deadline, adult/driver eligibility, inventory locking, payment simulation, owner isolation, and idempotent replay remain enforced after option save no longer has booking readiness as a prerequisite.
- Inputs/fixture: saved options with different dates, adult/driver and inventory boundary cases, controlled Pacific-time clock.
- Doubles or boundary isolation: isolated H2 and existing simulated payment path.
- Edge cases: booking/update race, cancellation with Working dates different from booked option, retry of same idempotency key.

### 7. `oneWorkingPlanSurvivesAllLifecyclePaths`
- Type: API and migration integration plus frontend flow test.
- Location: `src/test/java/app/detour/trip/TripApiIntegrationTest.java`; `src/test/java/app/detour/trip/TripModelForwardMigrationIntegrationTest.java`; `frontend/src/PublicTripFlow.test.tsx`.
- Proves: create, forbidden extra-Draft calls, Trip duplication, option load, canceled-booking continuation, retry, and concurrent calls leave one Working row per Trip.
- Inputs/fixture: fresh and migrated Trips with options and canceled booking history.
- Doubles or boundary isolation: H2 databases and mocked UI API.
- Edge cases: repeated request and concurrent creates, legacy multiple-Draft migration.

### 8. `optionClientUsesStrictPayloadsAndConflictResponses`
- Type: frontend API unit test.
- Location: `frontend/src/api/tripsApi.test.ts`.
- Proves: create/load/update routes carry expected parent, Working, and option versions, name and confirmation fields; GET searches do not mutate; CSRF headers and error codes reach the UI.
- Inputs/fixture: mocked fetch requests and responses.
- Doubles or boundary isolation: browser network mock.
- Edge cases: omitted optional fields, 409 conflict, server-side validation errors.

## Safe Verification Commands

- Focused: `mvn -q -DskipFrontend=true -Dtest=TripApiIntegrationTest test`; `npm test -- --run tripsApi.test.ts ProgressiveTripBuilder.test.tsx ItineraryComparisonAndBookingReview.test.tsx` from `frontend`.
- Related suite: `mvn -q -DskipFrontend=true -Dtest=TripApiIntegrationTest,DraftReadinessAndPlannedSnapshotIntegrationTest,BookingApiIntegrationTest,BookingCancellationIntegrationTest,TripModelForwardMigrationIntegrationTest test`; `npm test` from `frontend`.
- Full safe suite: `mvn -q -DskipFrontend=true test`; `npm run build` from `frontend`.

## Optional Developer Checks

- In a configured non-production browser, inspect the replacement confirmation and revalidation messages, and try two tabs editing one Working plan. Record these observations separately from automated gates.

## Exit Criteria

- [x] The named budgetless-option test fails for the missing route/old budget requirement before implementation.
- [x] New and updated tests pass after implementation.
- [x] The full safe backend and frontend suites and frontend build pass.
- [x] Every acceptance criterion has executable evidence in the tests above.
- [x] Automated tests use isolated databases and mocked network calls, with no live booking or payment side effects.
- [x] Rollback, stale-version, booking-history, option-date, and one-Working edge cases pass.
- [x] Optional browser checks are reported as nonblocking and are not represented as performed by automated tests.

## Implementation verification

The pre-implementation named budgetless-option test returned 404 for the absent route. After implementation, the complete backend suite passed (25 Surefire reports, 200 tests, zero failures/errors), the frontend suite passed (14 files, 140 tests), and the production frontend build passed. A focused option-lifecycle test also passed after adding a retry case for a successful keep-current save followed by a failed load.

The existing booking cancellation and migration integration tests supply the broad date and one-Working regression coverage. The new Trip API and booking tests cover named budgetless saving, load and in-place update, canceled-history immutability, version conflict, rollback, catalog repricing, and eligibility at booking. Browser inspection remains an optional developer check.
