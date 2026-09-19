# P03-T01 Owned Trips and Initial Drafts Testing Plan

## Change Summary

P03-T01 adds a persisted, owner-scoped Trip aggregate with required shared travel data and exactly one component-empty Draft created in the same transaction. The HTTP boundary adds authenticated create/detail routes only; it must strictly validate March-2027/PDX travel constraints, optional all-or-nothing ages, optional integer-cent budget, derived labels, versions, stable opaque identifiers, owner non-disclosure, rollback, and restart persistence without introducing mutable alternatives, promotion, components, UI, or booking behavior.

## Impacted Areas and Risks

| Category | Risk | Planned evidence |
| --- | --- | --- |
| Flyway schema | An applied migration breaks a clean DB, prior V2 identity data, reference integrity, or later aggregate expansion. | Clean-context schema assertions and V2-to-latest forward migration test. |
| API validation | Dates, destination, count, ages, or money are accepted outside the product envelope, or absent and zero collapse. | MockMvc boundary matrix with persisted-row assertions after every rejection. |
| Transactionality | A parent insert commits even though a child Draft/traveler insert fails. | Controlled persistence-failure integration test plus direct row-count queries. |
| Authorization/privacy | A caller can fetch another user's Trip, or receives a distinguishable foreign-ID response. | Two-session comparison of foreign and unknown detail responses and body fields. |
| Durability | Aggregate records or public IDs disappear/change across restart. | Temporary file-H2, two-context loopback restart test. |
| Compatibility | V12 breaks existing identity/catalog behavior, frontend build, or the packaged startup. | Full Maven backend suite, frontend test/build, package, and packaged loopback script. |

## Existing Coverage and Environment Constraints

The repository uses JUnit 5/Spring Boot integration tests, MockMvc, isolated H2 URLs via `@DynamicPropertySource`, Flyway directly for forward-migration verification, and a loopback `HttpClient` application-restart test. `IdentityApiIntegrationTest` has the reusable shape: first `GET /` captures `XSRF-TOKEN`; unsafe requests send that cookie/header and retain the individual `MockHttpSession`. Existing JSON errors use `ApiExceptionHandler`: malformed JSON returns `400 MALFORMED_REQUEST`, while expected domain validation uses `400 VALIDATION_FAILED` with safe field messages.

`ApplicationRestartIntegrationTest` starts the real server twice against a temporary file H2 database. It needs no credentials/network service, but may retain test logs/process resources if not closed in `finally`; follow its cleanup pattern. Maven performs `npm ci` and frontend build during normal package. The supported frontend commands are `npm.cmd run test --prefix frontend` and `npm.cmd run build --prefix frontend`; no frontend source changes are planned.

## Failing Test First

- Name: `createsOwnedTripAndInitialComponentEmptyDraft`
- Type: Spring Boot MockMvc integration test
- Location: `src/test/java/app/detour/trip/TripApiIntegrationTest.java`
- Arrange/Act/Assert: Register an owner via a CSRF-bearing client; `POST /api/trips` with `destination-sfo`, `2027-03-10` to `2027-03-14`, two travelers, ages `[17, 17]`, and `budgetCents: 0`; assert `201`, UUID-shaped distinct Trip/Draft IDs, version `0`, normalized PDX/details, derived label, `budgetCents: 0`, and exactly one Draft with no component/lifecycle fields. `GET` the returned Trip as the same client and assert equivalent durable details.
- Expected pre-fix failure: `404` because `/api/trips` and the Trip aggregate do not exist.

## Tests to Add or Update

### 1. `createsOwnedTripAndInitialComponentEmptyDraft`
- Type: MockMvc API integration
- Location: `src/test/java/app/detour/trip/TripApiIntegrationTest.java`
- Proves: Authenticated creation atomically returns normalized shared details, server-derived label, version, and one stable component-empty Draft; owner detail retrieval returns the same data.
- Inputs/fixture: Seeded `destination-sfo`, March 10--14, `travelerCount: 2`, valid ages, and zero budget.
- Doubles or boundary isolation: Isolated random in-memory H2; real Flyway/Spring Security/JDBC; no external services.
- Edge cases: Assert response lacks numeric IDs, owner identifiers, custom name, components, price/totals, Planned/Booked/Canceled state, and mutable update metadata.

### 2. `validatesRequiredAndSupportedSharedTravelEnvelope`
- Type: MockMvc API integration, parameterized or table-driven cases
- Location: `src/test/java/app/detour/trip/TripApiIntegrationTest.java`
- Proves: Missing/null destination, dates, or traveler count; unapproved destination key; start/end outside March 1--31 2027; same-day/zero-night; reverse/negative duration; 15 nights; count 0 or 9; and malformed ISO dates each yield safe `400` errors and no aggregate rows.
- Inputs/fixture: Minimal valid request mutated one field at a time, including all three valid destination keys at least once.
- Doubles or boundary isolation: Real route and database; use direct count queries only for persistence assertion.
- Edge cases: Check March 1 to March 2 and March 17 to March 31 (14 nights) succeed; ensure PDX is returned rather than accepted from request JSON.

### 3. `validatesAgesAndPreservesUnknownAges`
- Type: MockMvc API integration
- Location: `src/test/java/app/detour/trip/TripApiIntegrationTest.java`
- Proves: Omitted age list creates unknown/null ordered ages; supplied list must have exactly traveler-count integral entries in `0..120`; `[0, 120]` is valid; negative, 121, null member, fractional, string, and mismatched cardinality fail.
- Inputs/fixture: Two- and three-traveler valid baseline requests.
- Doubles or boundary isolation: Real JSON deserialization/validation and H2 mapping.
- Edge cases: Complete valid ages with no adult (for example `[0, 17]`) returns `201`, documenting that adult readiness belongs only to P03-T03 promotion.

### 4. `strictlyValidatesBudgetAndDistinguishesZeroFromAbsent`
- Type: MockMvc API integration
- Location: `src/test/java/app/detour/trip/TripApiIntegrationTest.java`
- Proves: Omitted/null budget returns/persists absence; `0` returns/persists `0`; maximum `100000000` cents succeeds; `-1`, `100000001`, decimal numeric values, strings, exponential/non-integral JSON forms, and values beyond `long` fail with no rows.
- Inputs/fixture: Otherwise valid one-traveler request.
- Doubles or boundary isolation: Real request parser and service; no global mapper alteration.
- Edge cases: Read the omitted and zero Trips back after creation and compare their null versus zero JSON/database state.

### 5. `doesNotDiscloseForeignTripAndBindsCreateToPrincipal`
- Type: MockMvc API integration
- Location: `src/test/java/app/detour/trip/TripApiIntegrationTest.java`
- Proves: Owner A creates a Trip; Owner B receives the same `404 RESOURCE_NOT_FOUND` status/code/message/empty-safe fields for A's UUID and a random UUID, with neither response containing label, destination, dates, Draft ID, or owner data. A's own GET succeeds.
- Inputs/fixture: Two registered MockMvc clients with separate sessions/CSRF cookies.
- Doubles or boundary isolation: Real Spring Security principal plus owner-scoped JDBC query.
- Edge cases: Include an attempted `ownerUserId` field in B's create JSON and verify it is ignored/rejected by the request shape while B can create only a Trip owned by B; no update endpoint is asserted because P03-T01 deliberately exposes none.

### 6. `rollsBackEveryAggregateRowWhenCreationFails`
- Type: Spring integration/repository-boundary test
- Location: `src/test/java/app/detour/trip/TripApiIntegrationTest.java` or `TripPersistenceIntegrationTest.java`
- Proves: Validation failures, malformed request failure, and a controlled failure after the parent insert cannot leave `detour_trip` without matching traveler rows and exactly one Draft.
- Inputs/fixture: Isolated H2 plus a test-local, controlled database failure (for example temporarily make the Draft insert fail while retaining an inspectable Trip table) invoked through the normal transactional service/HTTP boundary.
- Doubles or boundary isolation: Prefer a test configuration/spied repository fault at the Draft write rather than production failpoints; never use an external database.
- Edge cases: Assert generic `500 INTERNAL_ERROR` reveals no SQL/Trip data for the controlled infrastructure failure, and direct SQL confirms zero parent/traveler/Draft rows for the rejected operation.

### 7. `concurrentOrDuplicateFailedCreatesDoNotLeaveOrphans`
- Type: Spring integration concurrency test
- Location: `src/test/java/app/detour/trip/TripPersistenceIntegrationTest.java`
- Proves: Simultaneous identical invalid/malformed creation attempts all fail and create no childless parent or unattached Draft; a concurrent valid request, if included, produces only complete aggregates.
- Inputs/fixture: `ExecutorService`/barrier against isolated H2 with distinct authenticated owner IDs created in setup.
- Doubles or boundary isolation: Direct service calls may avoid MockMvc thread/session complexity, provided the test still passes explicit authenticated owner IDs and separately retains HTTP ownership coverage.
- Edge cases: Query `LEFT JOIN` counts for parents lacking a Draft and child rows lacking a parent after all tasks complete. Do not claim idempotency for valid duplicate creates because the ticket does not define an idempotency key.

### 8. `persistsTripAndDraftAcrossApplicationRestart`
- Type: Loopback end-to-end restart integration
- Location: `src/test/java/app/detour/trip/TripApplicationRestartIntegrationTest.java`
- Proves: A real first application context registers/logs in, creates a Trip, stops, and a second context on the same temporary file H2 accepts a new login and retrieves the same Trip/Draft IDs, version, details, optional values, and label; the first session stays invalid.
- Inputs/fixture: `@TempDir`, unique file H2 URL, real HTTP cookies/CSRF.
- Doubles or boundary isolation: No doubles, no network beyond loopback; close both contexts in `finally`.
- Edge cases: Use zero budget or omitted ages to prove optional persistence state, not only required dates.

### 9. `migratesCleanAndExistingIdentityDataToV12`
- Type: Flyway/Spring integration regression
- Location: `src/test/java/app/detour/DetourApplicationTest.java` and `src/test/java/app/detour/catalog/PhaseOneCatalogForwardMigrationIntegrationTest.java`
- Proves: A clean runtime context reaches V12 with Phase 1/2 records unchanged and new Trip tables empty; a database stopped at V2 keeps its user after advancing through V12.
- Inputs/fixture: Existing random H2 clean database and temp file Flyway test fixture.
- Doubles or boundary isolation: Real Flyway migrations.
- Edge cases: Confirm the new schema did not seed users/trips and catalog fixture row counts remain their existing values.

## Safe Verification Commands

- Focused: `.\mvnw.cmd -DskipFrontend=true -Dtest=TripApiIntegrationTest,TripPersistenceIntegrationTest,TripApplicationRestartIntegrationTest test`
- Related suite: `.\mvnw.cmd -DskipFrontend=true -Dtest=DetourApplicationTest,PhaseOneCatalogForwardMigrationIntegrationTest,IdentityApiIntegrationTest test`
- Full safe suite: `.\mvnw.cmd test -DskipFrontend=true`; then `npm.cmd run test --prefix frontend`; `npm.cmd run build --prefix frontend`; `.\mvnw.cmd package`; and `.\scripts\verify-packaged-identity.ps1` (or its safely extended Trip-aware successor).

## Optional Developer Checks

- With a local isolated DB and `DETOUR_SECURE_COOKIES=false` only for loopback, use two independent browser sessions or HTTP clients to confirm the second account receives the generic missing-resource response for the first account's Trip URL. This is nonblocking because automated two-user coverage is required.

## Exit Criteria

- [ ] The planned red test fails for the intended missing-route/missing-aggregate reason before implementation.
- [x] New and updated tests pass after implementation.
- [x] The broadest safe relevant repository test suite passes.
- [x] Acceptance criteria map to executable evidence.
- [x] Routine automated tests do not perform unintended live or destructive operations.
- [x] Material risks and edge cases identified above are covered.
- [x] Any optional check is reported as nonblocking and is not represented as already performed.
