# P03-T01 Owned Trips and Initial Drafts Implementation Plan

## Overview
- Ticket: `ai/thoughts/tickets/2026-09-19-p03-t01-establish-trip-draft-foundation.md`
- Research: `ai/thoughts/research/2026-09-19-p03-t01-establish-trip-draft-foundation.md`
- Outcome: An authenticated owner can create and later retrieve one durable, validated Trip aggregate containing its first component-empty Draft, without exposing another user's aggregate.

## Current State

There is no Trip, traveler, Draft, or Trip API implementation. `DetourUserPrincipal.userId()` is the persistent authenticated owner identity and `IdentityController` passes it to the service layer; this is the established pattern for an owner-scoped route (`src/main/java/app/detour/identity/IdentityController.java`). Spring Security requires authentication for all new `/api/**` routes and protects unsafe requests with the XSRF cookie/header pair (`src/main/java/app/detour/security/SecurityConfiguration.java`).

Flyway currently ends at `V11`; `detour_user` uses a `BIGINT` identity key and the shared `catalog_destination` table exposes generated database IDs plus immutable `catalog_key` values. The seeded supported keys are exactly `destination-sfo`, `destination-muc`, and `destination-mex` (`src/main/resources/db/migration/V2__create_detour_user_identity.sql`, `V3__create_shared_catalog_and_flight_schema.sql`, and `V10__seed_march_2027_airfare_catalog.sql`). Existing persistence uses `JdbcTemplate` and generated-key holders, API failures use `ApiException`/`ApiExceptionHandler`, and HTTP integration tests establish session and CSRF state using MockMvc.

## Desired End State

- `POST /api/trips`, bound only to the authenticated principal, creates a Trip and its first Draft in one transaction and returns `201` with stable opaque identifiers, version `0`, normalized shared details, the derived label, and a one-element Draft collection.
- `GET /api/trips/{tripId}` returns the same complete owner-scoped representation for its owner. A syntactically valid but foreign identifier and an unknown identifier both return the existing generic `404 RESOURCE_NOT_FOUND` body; no response contains owner internals.
- Creation accepts only the fixed PDX/March-2027 travel envelope, the three catalog destination keys, one to eight travelers, optional all-or-nothing ages, and optional USD integer-cent budget. Omitted ages and budget remain distinct from supplied values; `0` cents remains a supplied budget.
- The initial Draft has only identity, parent relation, and its initial version. It has no status transition, component, price, selection, snapshot, booking, profile, or custom-name state.
- A new clean H2 database applies the forward-only `V12` migration, and all successful aggregate data survives restart. Invalid, foreign, malformed, or persistence-failed requests leave no parent without a Draft.

| Acceptance criterion | Planned behavior |
| --- | --- |
| Create durable aggregate | Owner-scoped `POST` validates, inserts Trip/travelers/Draft atomically, and returns IDs, version, label, and details. |
| Reject invalid shared details without partial persistence | Service validation precedes writes; database checks and the transaction protect persistence failures. |
| Allow an adult-less complete Draft | Age validation allows `0..120`; adult readiness is deliberately not evaluated in this ticket. |
| Preserve absent versus zero optionals | Nullable age/budget fields retain absence; `0` is valid and returned as `0`. |
| Isolate two users | Repository detail lookup includes both Trip public ID and owner user ID, returning the generic not-found contract on a miss. |
| Prevent orphans and survive restart | Transaction creates all rows; integration coverage exercises rollback, concurrent invalid requests, and a file-backed restart. |
| Preserve platform verification | Existing Maven/frontend/packaged-JAR verification continues with no external services. |
| Preserve scope boundary | No mutable Trip endpoint, alternatives beyond the first Draft, component storage, readiness/promotion, UI, or booking behavior is added. |

## Scope

### In scope

- A new forward-only Trip/Draft/traveler migration and migration test updates.
- Java domain records, strict request validation, JDBC aggregate persistence, transactional service, and authenticated create/detail HTTP endpoints.
- Stable opaque Trip and Draft identifiers, response records, owner-scoped lookup, generic foreign/not-found response, and API integration coverage.
- Clean-database, rollback, two-user, concurrent-failed-create, restart, frontend-build, package, and packaged-startup verification.

### Out of scope

- React Trip or profile experience and any client API module (P03-T06).
- Extra/duplicated/deleted Drafts, Draft autosave, mutable shared-detail updates, and conflict handling (P03-T02).
- Planned snapshots, adult/budget/component readiness, selection storage, pricing, comparison, booking/cancellation, sharing, custom names, and Version 2 Events.
- Catalog schema/API changes, external suppliers, model services, compatibility routes, aliases, or a legacy-data migration.

## Active Project Guardrails

None recorded in `ai/thoughts/design-lens.md`.

## Impact and Risk Analysis

The migration changes the durable schema used by later Phase 3 tickets. It must be numbered after V11 and must not edit an applied migration. Use database foreign keys `detour_user -> detour_trip -> detour_trip_draft` (and travelers under the Trip) to make the intended owner path enforceable; never trust an owner identifier in JSON or fetch a Trip by public ID and authorize it later.

The destination is shared reference data, not user data. Store `catalog_destination.id` as the immutable relational reference while accepting and returning its seeded `catalog_key`; lookup must restrict the input to the three approved keys so a future catalog record does not silently become creatable. The fixed PDX origin is an application rule, not a user-controlled column.

Use UUID-formatted public identifiers for Trip and Draft response/route identity while retaining numeric surrogate primary keys for joins. This avoids exposing sequence values and gives stable resources without changing the catalog's existing identifier contract. A malformed or unknown Trip identifier must use the same not-found response as a foreign one.

Validation needs to distinguish JSON absence from `0`. Set `MAX_TRAVELER_AGE = 120` (inclusive) as the documented realistic bound. Set `MAX_BUDGET_CENTS = 100_000_000L` ($1,000,000.00): it is comfortably below `BIGINT` overflow, bounded for a 1--8-person, 14-night consumer trip, and is enforced in both application validation and the migration check constraint. Strictly accept a budget only when JSON supplies an integral, `long`-representable number; reject strings, fractions, exponential/non-integral forms, overflow, negatives, and values above the maximum. This prevents mapper coercion from silently turning malformed money into cents.

`@Transactional` must span every parent, traveler, and Draft insert. Application validation handles useful field errors; migration constraints remain defense-in-depth for values that cannot be invalidated by a partial/infrastructure failure. Do not add a mutation endpoint merely to demonstrate foreign mutation: this ticket's only write is creation, which obtains its owner exclusively from the principal. P03-T02 will introduce owner-scoped mutation endpoints; this ticket's two-user proof covers foreign detail reads and that creation cannot target another owner.

## Implementation Approach

Add a focused `app.detour.trip` package rather than extending identity or catalog ownership. The controller owns HTTP/session-principal adaptation, `TripService` owns validation, label construction, ownership semantics, and transaction demarcation, and `JdbcTripRepository` owns SQL and aggregate mapping. This matches the existing identity split and keeps future alternatives/version work close to the Trip aggregate.

Use three normalized tables rather than a serialized traveler JSON column: `detour_trip` stores shared details/version and a `catalog_destination_id` foreign key; `detour_trip_traveler` stores one ordinal row for each declared traveler with nullable `age`; and `detour_trip_draft` stores the initial Draft identity/version and its Trip foreign key. Creating one row per traveler, even when its age is null, preserves the required traveler count, stable traveler ordering, and later P03-T02 age completion without inventing traveler profiles. The service requires supplied ages to contain exactly that many non-null integer values; an omitted/null age list creates all-null ages. The database retains cardinality/ordinal integrity, while the service enforces the all-or-nothing API rule.

The selected response contract is intentionally small and forward-compatible: `TripResponse` exposes `id`, `destinationKey`, `destinationName`, `originAirportCode` (`PDX`), `startDate`, `endDate`, `travelerCount`, `travelerAges` (nullable only when unknown), `budgetCents` (nullable only when absent), `label`, `version`, and `drafts`; each `DraftResponse` exposes `id` and `version`. `drafts` contains exactly one entry in this ticket, so P03-T02 can add alternatives without replacing a singular creation field. No response accepts or exposes `ownerUserId`, numeric database IDs, custom labels, components, or lifecycle states. The label is server-derived using the catalog destination name and inclusive dates in the fixed format `&lt;Destination&gt; — Mar d–d, 2027`; all accepted dates are in March 2027, making that deterministic format unambiguous.

The alternative of storing a catalog key string on Trip was rejected because a foreign key to `catalog_destination.id` preserves reference integrity and lets detail reads return the authoritative immutable key/name. The alternative of a generic itinerary/status table was rejected because it would prematurely model Planned/Booked/Canceled lifecycle concepts reserved for P03-T03/P06.

## Phase 1: Add Durable Aggregate Schema

### Changes

- [x] `src/main/resources/db/migration/V12__create_owned_trip_and_initial_draft_schema.sql` — create `detour_trip` with numeric primary key, unique UUID public ID, non-null `owner_user_id` foreign key to `detour_user`, non-null `catalog_destination_id` foreign key to `catalog_destination`, March 2027 date/nights checks, traveler-count `1..8` check, nullable `budget_cents` constrained to `0..100000000`, non-null derived `display_label`, and non-negative `version` defaulting to `0`.
- [x] `src/main/resources/db/migration/V12__create_owned_trip_and_initial_draft_schema.sql` — create `detour_trip_traveler` with Trip foreign key, stable ordinal `1..8`, nullable age constrained to `0..120`, and `(trip_id, traveler_ordinal)` uniqueness; create `detour_trip_draft` with numeric primary key, unique UUID public ID, Trip foreign key, and non-negative version defaulting to `0`. Add indexes supporting `(owner_user_id, public_id)` detail lookup and child lookup by Trip.
- [x] `src/test/java/app/detour/DetourApplicationTest.java` — include migration `12` in the clean-lineage expectation and assert the new Trip/Draft/traveler tables exist but are unseeded, while the Phase 1/2 fixtures remain intact.
- [x] `src/test/java/app/detour/catalog/PhaseOneCatalogForwardMigrationIntegrationTest.java` — advance the expected complete lineage to `12` and prove an existing V2 identity row survives forward migration alongside the new tables.

### Automated verification

- [x] `.\mvnw.cmd -DskipFrontend=true -Dtest=DetourApplicationTest,PhaseOneCatalogForwardMigrationIntegrationTest test` — a fresh and an older identity-only H2 database both migrate through V12 without modifying prior data.

### Optional developer checks

- [ ] none.

### Success criteria

Migration V12 applies to a clean database and forward from V2, establishes enforceable owner/reference/child relationships, accepts no seeded Trip records, and does not change the existing catalog or identity schema/data.

## Phase 2: Implement the Owner-Scoped Trip Boundary

### Changes

- [x] `src/main/java/app/detour/trip/TripRequests.java` — add `Create` request shape for destination key, ISO local dates, traveler count, optional age array, and a JSON-node/strict integral budget input so null/absence, zero, fractions, strings, and overflow are differentiated without global ObjectMapper coercion changes.
- [x] `src/main/java/app/detour/trip/TripResponse.java`, `DraftResponse.java`, `Trip.java`, and `TripDraft.java` — add explicit response/domain records using UUID public IDs, `LocalDate`, nullable optionals, version fields, catalog key/name, and an initial `drafts` list; do not add owner IDs, component fields, or lifecycle statuses.
- [x] `src/main/java/app/detour/trip/TripRepository.java` and `JdbcTripRepository.java` — resolve an approved catalog key, insert/map the normalized parent and traveler rows plus Draft, and load the full aggregate only with `WHERE trip.public_id = ? AND trip.owner_user_id = ?`. Never expose an unscoped `findByPublicId` method.
- [x] `src/main/java/app/detour/trip/TripService.java` — define and document the date/traveler/age/budget limits; validate required fields, approved destination, dates in March 1--31 2027, 1--14 nights, count `1..8`, all-or-nothing age cardinality and `0..120`, and strict `0..100000000` cents. Derive the label, generate UUIDs, call aggregate creation inside `@Transactional`, and convert any absent owner-scoped detail result to the existing generic `RESOURCE_NOT_FOUND` exception.
- [x] `src/main/java/app/detour/trip/TripController.java` — add authenticated `POST /api/trips` returning `201` and `GET /api/trips/{tripId}` returning `200`; obtain the owner only from `@AuthenticationPrincipal DetourUserPrincipal` and retain the established safe principal/error handling convention.

### Automated verification

- [x] `.\mvnw.cmd -DskipFrontend=true -Dtest=TripApiIntegrationTest test` — create/detail, validation, ownership, rollback, and stable response behavior pass against isolated H2.

### Optional developer checks

- [ ] none.

### Success criteria

An authenticated caller can create and fetch only its own complete aggregate; every accepted representation is normalized and contains exactly one empty Draft, every rejected creation persists no partial aggregate, and there is no Trip mutation/UI/component/snapshot behavior.

## Phase 3: Prove Persistence, Isolation, and Restart Behavior

### Changes

- [x] `src/test/java/app/detour/trip/TripApiIntegrationTest.java` — add MockMvc integration coverage using the existing CSRF/session-client pattern for success, normalization/label/version/IDs, all validation boundaries, malformed money/JSON, absent-versus-zero optionals, adult-less valid ages, generic foreign/not-found detail responses, and no owner/data leakage.
- [x] `src/test/java/app/detour/trip/TripApiIntegrationTest.java` — assert failed validation and a controlled database-write failure roll back parent/traveler/Draft rows; issue concurrent/duplicate invalid creates and assert no orphan rows result. Assert create is principal-bound rather than accepting a target owner field.
- [x] `src/test/java/app/detour/trip/TripApplicationRestartIntegrationTest.java` — adapt the existing loopback temporary-file H2/restart pattern to register, create a Trip, close the first context, log in after restart, and retrieve the exact persisted aggregate/Draft while the stale session remains rejected.

### Automated verification

- [x] `.\mvnw.cmd -DskipFrontend=true -Dtest=TripApiIntegrationTest,TripApplicationRestartIntegrationTest test` — HTTP authorization, rollback, and durable restart behavior pass without supplier/model dependencies.

### Optional developer checks

- [ ] none.

### Success criteria

Two independent user sessions cannot obtain protected Trip data, all failed creation paths leave no orphan parent or Draft, and a successful aggregate remains retrievable after an application restart with a new authenticated session.

## Phase 4: Run Regression and Package Verification

### Changes

- [x] `README.md` — preserve existing packaged-verification wording because the established safe shell check was not extended to make authenticated Trip requests.
- [x] `scripts/verify-packaged-identity.ps1` — preserve the existing loopback-only temporary-resource shell verification; authenticated Trip behavior is covered by the restart integration test.

### Automated verification

- [x] `.\mvnw.cmd test -DskipFrontend=true` — full backend/migration/integration regression suite passes.
- [x] `npm.cmd run test --prefix frontend` — existing frontend tests remain green even though this ticket changes no frontend source.
- [x] `npm.cmd run build --prefix frontend` — TypeScript/Vite production build remains green.
- [x] `.\mvnw.cmd package` — Maven builds the frontend and produces the packaged application.
- [x] `powershell.exe -ExecutionPolicy Bypass -File .\scripts\verify-packaged-identity.ps1` — packaged application starts on loopback with an isolated H2 database and serves the existing shell.

### Optional developer checks

- [ ] With `DETOUR_SECURE_COOKIES=false` only for local loopback use, use two browser sessions or HTTP clients to confirm a created Trip detail is visible only to its creator; this is supplementary to automated two-user coverage.

### Success criteria

All safe repository regressions, frontend build, package, and packaged loopback startup pass with no external supplier/model configuration, and no compatibility or out-of-scope feature is introduced.

## Test Strategy

Step 3 should implement mostly HTTP integration tests because validation, authentication, CSRF, JSON strictness, response privacy, transactions, Flyway, and JDBC mapping meet at this boundary. Keep a focused `TripApiIntegrationTest` using its own named in-memory H2 URL and existing authenticated-client helper style; add direct JDBC assertions only for no-orphan/rollback proof. Use a temporary file-backed application restart test for persistence instead of assuming an in-memory context proves restart behavior. Retain the existing clean-lineage and frontend/package commands as regression gates; no frontend interaction test should be added because no frontend behavior is in scope.

## Acceptance-Criteria Traceability

| Acceptance criterion | Planned code evidence | Planned test evidence |
| --- | --- | --- |
| Authenticated atomic create with label/version/initial Draft | `TripController`, `TripService.create`, `JdbcTripRepository.createAggregate`, V12 constraints | `TripApiIntegrationTest.createsOwnedTripAndInitialEmptyDraft` |
| Invalid/malformed input leaves no partial aggregate | strict `TripRequests.Create` handling, service validation, V12 checks, transaction | boundary matrix plus database row-count/controlled-failure rollback tests |
| Adult-less complete ages remain Draft-valid | age validator intentionally has no adult rule | creation test with complete child ages and no age `>=18` returns `201` |
| Absent optionals differ from zero | nullable traveler ages/budget response mapping and strict cents parser | separate omitted/zero request-response/persistence assertions |
| Two-user non-disclosure | owner-constrained repository query and generic not-found service result | second session gets identical `404` contract for foreign and unknown IDs, with no Trip fields |
| No orphan under failed/concurrent creation; restart durable | transactional aggregate write and FK constraints | concurrent invalid create/rollback JDBC checks plus `TripApplicationRestartIntegrationTest` |
| Migration/regression/package health | V12 and retained build scripts | clean/forward migration tests, Maven suite, frontend test/build, package, packaged script |
| No out-of-scope functionality | narrow DTO/schema/controller surface | API response assertions reject/omit owner, custom-label, component, pricing, status, and mutation fields |

## Risks and Rollback/Recovery

The principal risk is a migration or aggregate-write defect leaving a partial parent row. The transaction, child foreign keys, and controlled-failure test address this before release. A foreign-ID lookup must never first load an unscoped record, otherwise observably different timing/errors could reveal ownership; owner and public ID must be query predicates together. Strict budget parsing must be local to the new request so it does not alter existing identity JSON behavior.

Because this is a development-stage forward migration, rollback is to revert the application and V12 migration together before deployment, then recreate disposable development databases using the documented reset workflow. Do not edit V1--V11 or add a downgrade/compatibility migration. For a failed deployed schema change, restore the prior application only if it has not been pointed at a database migrated through V12; otherwise fix forward with a new migration after preserving the database. No external side effect, supplier, credential, or inventory recovery is involved.

## References

- `ai/thoughts/tickets/2026-09-19-p03-t01-establish-trip-draft-foundation.md`
- `ai/thoughts/research/2026-09-19-p03-t01-establish-trip-draft-foundation.md`
- `ai/thoughts/phases/phase-3-trips-itineraries-and-profile.md`
- `ai/thoughts/architecture/2026-09-17-p00-t02-detour-replacement-architecture.md`
- `src/main/java/app/detour/identity/IdentityController.java`
- `src/main/java/app/detour/security/SecurityConfiguration.java`
- `src/main/resources/db/migration/V3__create_shared_catalog_and_flight_schema.sql`
- `src/main/resources/db/migration/V10__seed_march_2027_airfare_catalog.sql`
