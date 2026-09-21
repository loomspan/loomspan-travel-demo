# P03-T03 Immutable Planned Snapshot Lifecycle Testing Plan

## Change Summary

The change replaces the Draft-only alternative contract with typed Draft and immutable Planned alternatives.  It adds normalized Draft-selection and Planned-snapshot persistence, owner-scoped promotion/duplicate/delete HTTP operations, limited deterministic readiness validation, and optimistic/transactional graph writes.  It deliberately does not add a component-selection UI/search API, Phase 5 total or availability logic, or Phase 6 booking/inventory mutations.

## Impacted Areas and Risks

| Category | Risk | Planned evidence |
| --- | --- | --- |
| Flyway/persistence | A snapshot could lack copied facts, reload live catalog fields, or leave orphaned rows after failure. | H2 integration tests inspect graph rows, mutate live catalog data, force an insert failure, and restart a file-backed application. |
| Lifecycle/API | Promotion might consume/mutate the Draft, expose a Planned source as editable, or accept an unconfirmed Planned deletion. | MockMvc tests assert response lifecycle/type/IDs, source equality, immutable error, strict request validation, and sibling preservation. |
| Readiness | The service could stop on the first condition, accept a component-empty Draft, or accidentally take on Phase 5 availability/total rules. | A single request with all missing conditions asserts the complete issue map; separate valid catalog-shaped selections prove each allowed component kind. |
| Authorization | A caller could act on a different owner’s Trip/alternative or infer a target’s lifecycle. | Two-user tests compare foreign and random target 404 bodies for promotion, duplicate, detail, and deletion. |
| Concurrency/atomicity | Repeated/racing promotion or duplication could create multiple snapshots or leave partially copied children. | Same-session/two-client barrier tests assert one success, one 409, one complete graph; injected DB-write failure asserts rollback. |
| Inventory boundary | Lifecycle writes could reserve, decrement, release, or otherwise alter catalog inventory. | Before/after SQL counts and quantities for flight seats, nightly availability, and rental occupancy around promotion/duplicate/delete. |
| Deferred boundaries | A convenience implementation could introduce a JSON blob, calculated grand total, stale/sold-out policy, booking records, or Canceled itinerary status. | Contract assertions validate copied typed summaries only; code/test review and negative inventory assertions maintain the Phase 4/5/6 boundary. |

## Existing Coverage and Environment Constraints

`src/test/java/app/detour/trip/TripApiIntegrationTest.java` is the established `@SpringBootTest` + MockMvc boundary.  It has anonymous CSRF setup, two registered-user helpers, strict JSON assertions, owner-nondisclosure checks, H2 trigger-based rollback tests, and a `CyclicBarrier` concurrency pattern.  Extend it rather than introducing a separate HTTP harness.

`src/test/java/app/detour/trip/TripApplicationRestartIntegrationTest.java` starts a real loopback Spring application twice against a temporary file-backed H2 database.  Extend it to prove snapshot and duplicate graph persistence.  `src/test/java/app/detour/catalog/CatalogSchemaIntegrationTest.java` and the catalog fixture tests establish the representative Phase 2 catalog values; lifecycle tests should choose stable fixture keys and use direct `JdbcTemplate` setup only for the new internal Draft-selection records until Phase 4 supplies a public selection-write API.

The Maven build invokes npm unless `-DskipFrontend=true` is set.  Focused backend commands should set that property.  The complete package command intentionally verifies the frontend build as well.  Tests use H2, temporary directories, loopback HTTP, and in-memory/file databases only; they require no credentials, external network, real supplier, or production database.

## Failing Test First

- Name: `promotesReadyDraftIntoSeparateImmutablePlannedSnapshot`
- Type: Spring MockMvc/H2 integration test
- Location: `src/test/java/app/detour/trip/TripApiIntegrationTest.java`
- Arrange/Act/Assert: Create an owner Trip with ages, an adult, and zero budget; insert one structurally valid flight selection for its initial Draft using the narrow test selection helper; call the planned `POST /api/trips/{tripId}/drafts/{draftId}/plan` route with the current Trip and Draft versions; assert `201`, the original Draft remains with its original ID/version, exactly one distinct Planned alternative appears, and the Planned response contains copied flight description, schedule, source identifiers, and money breakdown rather than a live-only lookup.
- Expected pre-fix failure: The route/service/table/response contract does not exist and the current aggregate only returns component-empty Draft rows, so the test fails before any lifecycle implementation is present.

## Tests to Add or Update

### 1. `promotesReadyDraftIntoSeparateImmutablePlannedSnapshot`

- Type: MockMvc/H2 integration
- Location: `src/test/java/app/detour/trip/TripApiIntegrationTest.java`
- Proves: A valid Draft promotes to a second immutable Planned source while retaining the Draft and unrelated alternatives; promotion returns server-derived typed snapshot content and advances the Trip collection version once.
- Inputs/fixture: Standard `destination-sfo` Trip dates with exact ages `[18, 12]`, `budgetCents: 0`, and stable outbound/return `flight_instance` fixture keys matching PDX/destination/start/end dates.
- Doubles or boundary isolation: Use a package-visible test helper or repository fixture to create the Draft selection; do not invent Phase 4 client routes or provide request-side copied pricing.
- Edge cases: Draft ID/new Planned ID are distinct UUIDs; no original Draft is deleted; an extra Draft is unchanged.

### 2. `returnsEveryPromotionReadinessIssueTogether`

- Type: MockMvc/H2 integration
- Location: `src/test/java/app/detour/trip/TripApiIntegrationTest.java`
- Proves: Promotion returns one stable validation response containing all missing prerequisites, not first-error-only behavior.
- Inputs/fixture: Component-empty Trip with `travelerAges: null`, `budgetCents: null`; variants with known under-18 ages and an inserted malformed/mismatched selection.
- Doubles or boundary isolation: Direct selection fixtures create a structurally invalid reference combination only where required; catalog availability quantities are not changed.
- Edge cases: Missing ages, no adult, no budget, and no valid component are all present in the same response; valid zero budget and age 18 satisfy their respective gates; unknown properties/non-integral versions remain strict 400 parsing failures.

### 3. `promotesEachStructurallyValidSelectionKindWithoutCanonicalPricing`

- Type: MockMvc/H2 integration
- Location: `src/test/java/app/detour/trip/TripApiIntegrationTest.java`
- Proves: Airfare, stay, and rental selections can independently make a ready Draft promotable when their stable catalog references and trip-specific shape are valid, and snapshot rows preserve their individual breakdown/detail values.
- Inputs/fixture: Separate Trips with fixture-backed flight pair; stay unit/nightly rows and valid room count; rental unit plus valid pickup/return instants within the Trip interval.
- Doubles or boundary isolation: Fixture-selection inserts only.  Do not add a grand total expectation, overage acknowledgement, sold-out rejection, or occupancy reservation.
- Edge cases: Wrong destination/date/direction, missing stay-night row, invalid unit count, rental interval outside the Trip, return-before-pickup, and an invalid component alongside no valid component produce component readiness issues; a valid selection remains enough even if another selection is invalid only when the service's documented structural policy permits it.

### 4. `plannedSnapshotReadsCopiedContentAfterDraftAndCatalogMutation`

- Type: MockMvc/H2 plus direct catalog mutation integration
- Location: `src/test/java/app/detour/trip/TripApiIntegrationTest.java`
- Proves: Planned response/reload reads copied rows, not live catalog joins; duplicate source content is stable.
- Inputs/fixture: Promote a Draft containing representative flight, stay, and rental selections; afterward mutate the source Draft selection/content and update catalog supplier/property/unit/class display strings and all applicable catalog price columns.
- Doubles or boundary isolation: Direct `JdbcTemplate` mutations occur after promotion only and are confined to the test H2 database; restore nothing because each test database is isolated.
- Edge cases: Mutating another Draft does not change the snapshot; catalog price/display changes do not change any Planned text/amount; snapshot retains stable catalog/inventory identifiers even when copied display differs from current catalog.

### 5. `duplicatesDraftAndPlannedSourcesIntoIndependentDraftCopies`

- Type: MockMvc/H2 integration
- Location: `src/test/java/app/detour/trip/TripApiIntegrationTest.java`
- Proves: The source-agnostic duplicate route copies either lifecycle source into a new mutable Draft, preserves the source byte-for-byte/content-for-content, resets only the new Draft's mutable version, and gives it a new stable ID.
- Inputs/fixture: One selected Draft and one Planned snapshot from it; current expected Trip version and, for Draft source, current expected Draft version.
- Doubles or boundary isolation: No catalog changes during duplication; read response and SQL rows to compare typed selection/snapshot values.
- Edge cases: Duplicate from both source types, duplicate a Draft after catalog changes to show it copies current Draft selection state rather than editing source, optional/missing component types, stale parent/source version conflict, malformed/unknown source ID, and no intended source version for Planned.

### 6. `rejectsPlannedAsDraftMutationAndGatesItsDeletion`

- Type: MockMvc/H2 integration
- Location: `src/test/java/app/detour/trip/TripApiIntegrationTest.java`
- Proves: Planned sources never follow Draft-update semantics, and only an explicit confirmed Planned delete can remove a snapshot.
- Inputs/fixture: Trip with at least two Planned snapshots and at least one Draft; requests use a Planned ID in a Draft-only mutation, then delete with absent/false confirmation, then confirmed deletion.
- Doubles or boundary isolation: No booking history fixture because Phase 6 records are intentionally absent.
- Edge cases: Immutable-owned error is deterministic; unconfirmed response makes no version or row changes; confirmed deletion removes exactly the selected snapshot and its owned detail rows, leaves siblings/Drafts unchanged, advances the Trip version once, and a Draft delete remains direct under its version guard.

### 7. `alternativeLifecycleMutationsDoNotRevealForeignTargets`

- Type: MockMvc/H2 integration
- Location: `src/test/java/app/detour/trip/TripApiIntegrationTest.java`
- Proves: Authorization is enforced by owner-scoped aggregate retrieval, not merely UI filtering.
- Inputs/fixture: Owner creates a Trip with selected Draft and Planned alternatives; a second registered user calls promotion, duplication, detail, and deletion paths with the owner's IDs and equivalent random UUID paths.
- Doubles or boundary isolation: Existing two-user `Client` helper and real Spring Security session/CSRF handling.
- Edge cases: Compare complete foreign and unknown response bodies/statuses; an owner can still read/mutate both lifecycle types; malformed UUID remains the same 404 class where current conventions require it.

### 8. `rollsBackPromotionAndDuplicateGraphWritesOnFailure`

- Type: Transactional H2 integration
- Location: `src/test/java/app/detour/trip/TripApiIntegrationTest.java`
- Proves: A failure inserting a Planned detail row or copied Draft selection rolls back the parent version plus every parent/child graph row.
- Inputs/fixture: Ready selected Draft plus an H2 `BEFORE INSERT` trigger on the selected new snapshot/detail table and, separately, the copied selection table.
- Doubles or boundary isolation: Reuse the existing `FailingDraftTrigger` pattern or a generalized failure trigger; drop the trigger in `finally`.
- Edge cases: No orphan planned header/detail rows, no duplicate Draft, no Trip-version increment after 500, source survives exactly unchanged, and a retry after trigger removal succeeds once.

### 9. `sameVersionPromotionAndDuplicateRacesHaveOneCompleteWinner`

- Type: Concurrent MockMvc/H2 integration
- Location: `src/test/java/app/detour/trip/TripApiIntegrationTest.java`
- Proves: Optimistic collection/source guards make repeated/racing promotion and duplication safe.
- Inputs/fixture: Two authenticated clients for one user, an exact `CyclicBarrier`, a ready selected Draft, and matching expected versions.  Run a promotion race and a separate duplicate race.
- Doubles or boundary isolation: Use the existing executor/barrier convention; query database rows after both futures complete.
- Edge cases: Exactly one `201` and one `409`; one Planned (or one new Draft) graph is complete; no partial child rows; Trip version increments once; retry with the current returned version is permitted according to the new request contract.

### 10. `persistsPlannedSnapshotsAndDraftCopiesAcrossApplicationRestart`

- Type: Loopback application-restart integration
- Location: `src/test/java/app/detour/trip/TripApplicationRestartIntegrationTest.java`
- Proves: Public IDs and copied typed content survive a process restart and a new login, while the old server session remains invalid.
- Inputs/fixture: Temporary file H2; create a ready selected Draft through the established setup seam, promote, duplicate the Planned source, close context, start a second context, log in, and read detail.
- Doubles or boundary isolation: Existing `HttpClient`/temporary-directory application harness; no external service.
- Edge cases: Planned ID, copied descriptions/prices/reference IDs, duplicated Draft selection content, count/order, and Trip version are identical after restart; current catalog changes made before restart do not rewrite snapshot output.

### 11. `plannedLifecycleLeavesCatalogInventoryUntouched`

- Type: H2 integration with SQL before/after assertions
- Location: `src/test/java/app/detour/trip/PlannedSnapshotPersistenceIntegrationTest.java` (new focused class) or `TripApiIntegrationTest.java` if helpers remain compact
- Proves: Promotion, source duplication, and either deletion mode do not reserve/release or alter catalog inventory.
- Inputs/fixture: Ready Draft containing all three component types; capture flight `available_seats`, stay `available_inventory`, and `rental_unit_occupancy` count/status before operations.
- Doubles or boundary isolation: Query real fixture tables; do not simulate a booking.
- Edge cases: Values/counts remain exactly equal after success, after a forced rollback, and after confirmed Planned deletion; no `Booked`/`Canceled` lifecycle response or booking table is introduced.

## Safe Verification Commands

- Focused: `./mvnw.cmd test -DskipFrontend=true -Dtest=TripApiIntegrationTest`
- Related suite: `./mvnw.cmd test -DskipFrontend=true -Dtest=TripApiIntegrationTest,TripApplicationRestartIntegrationTest,CatalogSchemaIntegrationTest`
- Full safe suite: `./mvnw.cmd test -DskipFrontend=true`
- Packaging regression: `./mvnw.cmd package`

## Optional Developer Checks

- With an explicitly reset disposable local H2 database, use two browser sessions to create a Trip, promote a ready selected Draft once Phase 4 can create selections, duplicate both source types, attempt an unconfirmed Planned delete, and confirm one Planned delete.  Observe distinct snapshots and unchanged catalog inventory only; do not treat this as a substitute for the automated assertions.

## Exit Criteria

- [ ] The planned red test fails for the intended missing-promotion reason before implementation, when applicable.
- [ ] New and updated lifecycle, snapshot-immutability, authorization, transaction, race, restart, and inventory-boundary tests pass after implementation.
- [ ] `./mvnw.cmd test -DskipFrontend=true` passes as the broadest safe backend suite.
- [ ] `./mvnw.cmd package` passes after the serialized API contract change.
- [ ] Every ticket acceptance criterion maps to executable evidence in the implementation plan and these tests.
- [ ] Routine automated tests use isolated H2/temporary loopback environments and do not perform live, inventory-reserving, or destructive operations.
- [ ] Readiness aggregation, immutable source behavior, planned-delete confirmation, ownership nondisclosure, complete graph rollback, and same-version races are covered.
- [ ] Phase 4 selection UI/search, Phase 5 canonical pricing/availability/comparison, and Phase 6 booking/cancellation are asserted absent from this ticket's lifecycle behavior.
- [ ] Any optional check is reported as nonblocking and is not represented as already performed.
