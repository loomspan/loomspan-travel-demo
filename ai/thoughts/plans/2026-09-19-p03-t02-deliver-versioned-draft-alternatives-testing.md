# P03-T02 Versioned Draft Alternatives and Autosave Testing Plan

## Change Summary

P03-T02 evolves the owner-scoped Trip aggregate from exactly one immutable-in-practice component-empty Draft into a versioned collection of independently identified component-empty Drafts. It adds explicit authenticated APIs for complete shared-detail autosave, empty-Draft creation, source-preserving explicit duplication, and individual Draft deletion. Every successful shared or collection mutation advances the Trip version; Draft-targeted duplicate/delete requests also verify the observed Draft version. Stale writes receive safe machine-readable `409 VERSION_CONFLICT` responses with current-version information, while validation, ownership/non-disclosure, persistence rollback, and restart durability retain their established contracts.

## Impacted Areas and Risks

| Category | Risk | Planned evidence |
| --- | --- | --- |
| Flyway data model | V13 either fails to apply/advance or leaves the V12 one-Draft uniqueness restriction in place. | Clean/Flyway-forward tests assert V13 and persist multiple Drafts without altering prior identity/catalog records. |
| Shared-detail autosave | A partial/invalid payload corrupts dates, traveler rows, nullable budget, label, or versions. | Full PUT success/boundary matrix, reload/direct-SQL assertions, and unchanged-state assertions after rejection. |
| Alternative lifecycle | Create implicitly duplicates, duplicate mutates source, deletion removes too much, or zero alternatives blocks reuse. | API lifecycle tests assert new IDs/version zero, unchanged source, delete-one/delete-last, and create-after-empty. |
| Optimistic concurrency | A same-version write silently wins, a conflict reports success/incorrect current version, or a conditional update races incorrectly. | Sequential stale and barrier-coordinated two-session HTTP tests assert one success, one 409, and exact durable winner state. |
| Authorization/privacy | A user can mutate another user's Trip/Draft, or foreign IDs receive a distinguishable response. | Two-CSRF-session foreign-versus-unknown comparisons for each mutation category. |
| Transaction/persistence failure | A post-guard Draft failure commits the parent version, a partial row, or causes a retry duplicate. | Controlled H2 insert trigger, row/version assertions, trigger removal, and same-version retry test. |
| Durability | Collection changes or versioned shared updates disappear or drift after restart. | Real loopback temporary-file H2 restart test with fresh login and old-session rejection. |
| Scope regression | Components, lifecycle states, merge/UI/background behavior, or unsafe external calls leak into this ticket. | Request/response absence assertions, scoped source inspection, and local-only test commands. |

## Existing Coverage and Environment Constraints

The repository uses JUnit 5 Spring Boot integration tests with Flyway, H2, `JdbcTemplate`, MockMvc, and real Spring Security. `TripApiIntegrationTest` already registers independent clients through `GET /` CSRF bootstrap, retains a `MockHttpSession` per client, sends the XSRF cookie/header for unsafe requests, and directly checks H2 row counts. Its local H2 `FailingDraftTrigger` demonstrates a controlled persistence failure; extend that test-local mechanism instead of adding a production failpoint.

`TripApplicationRestartIntegrationTest` launches two loopback application contexts against a unique `@TempDir` file H2 URL, captures session/CSRF cookies with `HttpClient`, closes contexts in `finally`, and verifies a new login after restart. `DetourApplicationTest` checks complete Flyway version lineage and clean tables; `PhaseOneCatalogForwardMigrationIntegrationTest` advances a temporary V2 identity database to the current version and verifies historic identity/catalog preservation.

Maven can run frontend installation/build during normal packaging, so focused backend commands set `-DskipFrontend=true`. No test needs external services, production databases, browser automation, credentials, supplier inventory, or a model endpoint. Existing frontend commands are `npm.cmd run test --prefix frontend` and `npm.cmd run build --prefix frontend`; no frontend source changes are planned. The package script starts only an isolated temporary loopback H2 instance.

## Failing Test First

- Name: `createsAdditionalComponentEmptyDraftOnlyWhenExplicitlyRequested`
- Type: Spring Boot MockMvc API integration
- Location: `src/test/java/app/detour/trip/TripApiIntegrationTest.java`
- Arrange/Act/Assert: Register an owner, create a valid Trip, then `POST /api/trips/{tripId}/drafts` with its returned `expectedVersion`. Assert `201`, the same Trip ID/details, a Trip version increment, two distinct Draft UUIDs both with component-empty response shape and version zero, and durable two-row collection after `GET`.
- Expected pre-fix failure: V12 rejects a second Draft for the same Trip and no collection route exists, so the request cannot return the required `201` durable alternative.

## Tests to Add or Update

### 1. `createsAdditionalComponentEmptyDraftOnlyWhenExplicitlyRequested`

- Type: MockMvc API integration
- Location: `src/test/java/app/detour/trip/TripApiIntegrationTest.java`
- Proves: An authenticated owner uses only the additional-Draft route with an observed Trip version to create an independently identified component-empty Draft; the initial create route remains one initial Draft rather than an implicit duplicate.
- Inputs/fixture: A valid owner-created SFO Trip with complete ages and budget; `{"expectedVersion":0}` for the new route.
- Doubles or boundary isolation: Real security, Flyway V13, service, JDBC, and isolated random in-memory H2; direct SQL count checks only to corroborate persistence.
- Edge cases: Assert response omits owner/numeric IDs, components, state, price, and snapshot fields; assert a new Draft starts at version `0` while Trip version becomes `1`, and an unsupported body field returns `400` with no extra row/version change.

### 2. `duplicatesOnlyTheNamedDraftAndLeavesSourceUnchanged`

- Type: MockMvc API integration
- Location: `src/test/java/app/detour/trip/TripApiIntegrationTest.java`
- Proves: `POST /api/trips/{tripId}/drafts/{draftId}/duplicate` requires an explicit existing source and observed parent/source versions, creates one fresh UUID Draft at version zero, leaves the source UUID/version unchanged, and advances the Trip version once.
- Inputs/fixture: A Trip with at least two Drafts so identity/order are observable; source request `{"expectedVersion":1,"expectedDraftVersion":0}`.
- Doubles or boundary isolation: Real owner-qualified aggregate mapping; no fixture data beyond the seeded supported destination.
- Edge cases: Random/foreign source Draft IDs return the generic not-found contract; missing/negative/fractional/stale version fields are rejected/handled deterministically; no component content or Planned state appears because the source is component-empty.

### 3. `replacesSharedDetailsAtomicallyAndPreservesNullableBudgetSemantics`

- Type: MockMvc API integration
- Location: `src/test/java/app/detour/trip/TripApiIntegrationTest.java`
- Proves: Complete `PUT /api/trips/{tripId}` accepts supported destination, March dates, 1--8 traveler count, corresponding all-or-nothing ages, and nullable integer-cent budget; it replaces correlated shared/traveler data atomically, recomputes label, preserves owner/Draft identities, and advances only the Trip version.
- Inputs/fixture: Valid baseline updated across destination/date/traveler count/ages; one request omits/nulls budget and another supplies `budgetCents:0`.
- Doubles or boundary isolation: Real request parser/service/JDBC; reload through owner GET and, where useful, inspect traveler rows ordered by ordinal.
- Edge cases: March 1--2 and 17--31 valid ranges, all supported destination keys, unknown ages only when the whole array is omitted/null, zero/maximum budget, destination/date label change, and Draft versions remaining unchanged. Assert missing required fields, invalid dates/count/ages/budget, malformed JSON, partial/unsupported fields, client label/origin/owner/components, and mismatch count/ages all leave rows/versions exactly unchanged.

### 4. `returnsVersionConflictForSequentialAndConcurrentSameVersionSharedSaves`

- Type: MockMvc API integration with executor/barrier
- Location: `src/test/java/app/detour/trip/TripApiIntegrationTest.java`
- Proves: Two independently authenticated sessions for the same owner can start from the same observed Trip version, but exactly one complete shared-detail update commits; the stale request receives `409 VERSION_CONFLICT` with `fields.currentVersion` equal to the persisted version and must not overwrite the winner.
- Inputs/fixture: Owner registration followed by a separate login to obtain a second session/CSRF client; two different valid full PUT payloads using the same expected version.
- Doubles or boundary isolation: Real HTTP/Spring transaction/JDBC/H2; synchronize request submission with a barrier and await all futures, rather than asserting only a sequential stale request.
- Edge cases: First establish deterministic sequential stale behavior for concise contract assertions, then run the barrier race and assert one `200`/one `409`, single version increment, winner-only destination/dates/travelers/budget/label, and a reloadable body without protected data.

### 5. `serializesAlternativeCollectionMutationsAndChecksNamedDraftVersion`

- Type: MockMvc API integration with executor/barrier
- Location: `src/test/java/app/detour/trip/TripApiIntegrationTest.java`
- Proves: Same-observed-version concurrent create/duplicate/delete collection operations cannot silently combine under one Trip version; one guarded mutation succeeds and stale peers conflict. A named source/target with an obsolete Draft version returns the Draft-specific version conflict rather than copying/deleting an unexpected state.
- Inputs/fixture: A two-Draft owner Trip plus current parent and named Draft version values; operation-specific expected-version bodies. A controlled direct version adjustment may be used solely to create the otherwise unreachable component-empty Draft-version mismatch boundary.
- Doubles or boundary isolation: Real API/race path; direct JDBC only for the explicit internal Draft-version guard boundary, never as a substitute for HTTP owner/concurrency tests.
- Edge cases: Assert `currentVersion` for parent conflict, `currentDraftVersion` for an existing mismatched Draft, generic `404` rather than conflict for absent/foreign target, exactly preserved source/target rows on conflict, and no automatic retry/merge.

### 6. `deletesOnlySelectedOwnerDraftAndAllowsZeroAlternativeRecovery`

- Type: MockMvc API integration
- Location: `src/test/java/app/detour/trip/TripApiIntegrationTest.java`
- Proves: A guarded DELETE removes only its target, returns the current aggregate/version, retains the parent after final deletion, and permits the owner to create a fresh component-empty Draft from the resulting version.
- Inputs/fixture: A Trip with two alternatives, then a one-Draft and zero-Draft state; DELETE body includes both observed versions.
- Doubles or boundary isolation: Real owner-scoped repository/service and H2 row-count checks.
- Edge cases: Verify the non-target Draft persists untouched, `drafts: []` is returned/retrievable after final deletion, repeated delete/stale expected values do not claim success, and no Trip deletion, Planned state, or booking behavior is exposed.

### 7. `doesNotDiscloseForeignTripOrDraftMutationTargets`

- Type: MockMvc API integration
- Location: `src/test/java/app/detour/trip/TripApiIntegrationTest.java`
- Proves: A second user cannot PUT shared details, create a Draft, duplicate, or delete against the first user's Trip/Draft; foreign and randomly unknown parent/target identifiers yield the same `404 RESOURCE_NOT_FOUND` safe contract.
- Inputs/fixture: Two registered CSRF/session clients, owner A's Trip and Draft IDs, random UUID controls, and otherwise valid mutation bodies.
- Doubles or boundary isolation: Real authenticated principals and owner-qualified JDBC operations.
- Edge cases: Compare complete response body/status/code rather than only status; assert no destination, dates, label, Draft IDs, versions, or owner data leaks. Retain owner A success controls so the test distinguishes authorization from a broken route.

### 8. `rollsBackFailedAlternativeInsertAndRetriesWithoutDuplicate`

- Type: Spring integration/MockMvc API integration
- Location: `src/test/java/app/detour/trip/TripApiIntegrationTest.java`
- Proves: A controlled database failure after the optimistic parent guard produces safe `500 INTERNAL_ERROR`, restores parent version and existing Draft rows, and leaves the same expected-version request eligible to succeed exactly once after the failure is removed.
- Inputs/fixture: Owner Trip/current version; existing `FailingDraftTrigger` installed for `detour_trip_draft` insert; then trigger removed and the identical create-empty request repeated.
- Doubles or boundary isolation: Test-local H2 trigger only; all production service/repository transaction behavior remains real.
- Edge cases: Assert no SQL/UUID/version leakage in 500 response, no parent row without a Draft/traveler orphan, no version advancement after the failed operation, and exactly one added Draft/version increment after retry. Keep ordinary validation failures separately asserted as `400 VALIDATION_FAILED`, not `500` or conflict.

### 9. `persistsVersionedAlternativesAndSharedUpdatesAcrossApplicationRestart`

- Type: Loopback end-to-end restart integration
- Location: `src/test/java/app/detour/trip/TripApplicationRestartIntegrationTest.java`
- Proves: A real HTTP client creates a Trip, performs at least one shared-detail update and an alternative lifecycle operation, closes the application, logs in anew against the same temporary file H2 database, and reads the exact stored IDs/details/label/version/alternatives; its pre-restart session remains rejected.
- Inputs/fixture: `@TempDir`, unique file-backed H2, registration/login/CSRF cookies, valid March-trip payloads, and captured response IDs/versions.
- Doubles or boundary isolation: No doubles and no network beyond loopback; both Spring contexts closed in `finally`.
- Edge cases: Include zero or absent budget and a collection operation that proves more than initial creation persists; do not attempt UI autosave states, background behavior, or production database access.

### 10. `migratesCleanAndExistingIdentityDataThroughV13`

- Type: Flyway/Spring integration regression
- Location: `src/test/java/app/detour/DetourApplicationTest.java` and `src/test/java/app/detour/catalog/PhaseOneCatalogForwardMigrationIntegrationTest.java`
- Proves: Clean application context applies V1--V13 with unseeded Trip tables and expected catalog fixtures, and a database stopped at V2 preserves its identity row/data while advancing to V13.
- Inputs/fixture: Existing isolated H2 clean context and temporary V2 file database; a focused multiple-Draft insert/assertion to demonstrate the removed one-Draft restriction.
- Doubles or boundary isolation: Real Flyway/H2 migrations.
- Edge cases: Preserve the existing table/catalog counts and prior migration assertions; never edit historical migration SQL to make the test pass.

## Safe Verification Commands

- Focused: `.\mvnw.cmd -DskipFrontend=true -Dtest=TripApiIntegrationTest,TripApplicationRestartIntegrationTest test`
- Related suite: `.\mvnw.cmd -DskipFrontend=true -Dtest=DetourApplicationTest,PhaseOneCatalogForwardMigrationIntegrationTest,IdentityApiIntegrationTest test`
- Full safe suite: `.\mvnw.cmd test -DskipFrontend=true`; then `npm.cmd run test --prefix frontend`; `npm.cmd run build --prefix frontend`; `.\mvnw.cmd package`; and `powershell.exe -ExecutionPolicy Bypass -File .\scripts\verify-packaged-identity.ps1`.

## Optional Developer Checks

- In an isolated local loopback run with `DETOUR_SECURE_COOKIES=false`, open the same Trip in two independently authenticated browser sessions. After one session saves, confirm the other is able to show its eventual `saving` then `error/conflict` state and offer a deliberate reload rather than claiming success. This is nonblocking and belongs to P03-T06's UI work; the backend contract is automated here.

## Exit Criteria

- [ ] The planned red test fails for the intended missing route/schema-cardinality reason before implementation, when applicable.
- [x] New and updated tests pass after implementation.
- [x] The broadest safe relevant repository test suite passes.
- [x] Acceptance criteria map to executable evidence.
- [x] Routine automated tests do not perform unintended live or destructive operations.
- [x] Material lifecycle, concurrency, authorization, rollback/retry, migration, and restart edge cases identified above are covered.
- [x] Any optional check is reported as nonblocking and is not represented as already performed.
