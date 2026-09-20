# P03-T02 Versioned Draft Alternatives and Autosave Implementation Plan

## Overview

- Ticket: `ai/thoughts/tickets/2026-09-19-p03-t02-deliver-versioned-draft-alternatives.md`
- Research: `ai/thoughts/research/2026-09-19-p03-t02-deliver-versioned-draft-alternatives.md`
- Outcome: An authenticated Trip owner can save shared Draft-era details and create, explicitly duplicate, or delete durable component-empty Draft alternatives without silent concurrent overwrites.

## Current State

P03-T01 provides the owner-scoped `Trip` aggregate, a transactional create path, and authenticated `POST /api/trips` plus `GET /api/trips/{tripId}`. A Trip has a non-negative `version`, and each `TripDraft` has its own non-negative `version`, both initially zero. `TripResponse` already exposes a `drafts` collection, but `DraftResponse` contains only an opaque UUID and version. There are no mutable endpoints, conditional writes, or conflict responses (`src/main/java/app/detour/trip/TripController.java`, `TripService.java`, and `JdbcTripRepository.java`).

`V12__create_owned_trip_and_initial_draft_schema.sql` constrains the Draft relation to one row per Trip through `uq_detour_trip_draft_trip_id`; removing that constraint is necessary before an owner can create another alternative. Shared destination, dates, traveler rows, nullable budget, and derived label reside on `detour_trip`/`detour_trip_traveler`. The service already owns the supported-destination, date, traveler, age, nullable-budget, and label rules.

`ApiException` renders stable `{code,message,fields}` JSON and can carry a `409` code and current-version string fields. Owner-qualified aggregate lookup already makes foreign and unknown Trip IDs indistinguishable. MockMvc tests provide two authenticated CSRF clients, direct H2 assertions, an H2 failure trigger, and a file-backed real-server restart pattern.

## Desired End State

- `PUT /api/trips/{tripId}` replaces the complete shared-detail representation for a component-empty-Draft Trip when the request's `expectedVersion` equals the current Trip version. It preserves existing validation, nullable-versus-zero budget semantics, normalized traveler ordering, owner, and server-derived label; success increments only the Trip version.
- `POST /api/trips/{tripId}/drafts` creates a new component-empty Draft, while `POST /api/trips/{tripId}/drafts/{draftId}/duplicate` explicitly copies the named component-empty Draft into a distinct new Draft. Neither operation infers duplication, mutates the source, or creates component/lifecycle data. Each collection mutation requires `expectedVersion` for the Trip; duplicate and deletion also require `expectedDraftVersion` for their specific source/target Draft.
- `DELETE /api/trips/{tripId}/drafts/{draftId}` removes only the selected owner-visible Draft. It may leave `drafts: []`; the owner can use the resulting Trip version to create a fresh component-empty Draft or later use the separate Trip-deletion workflow.
- A successful shared update or alternative collection mutation advances the Trip version exactly once. New Drafts start at version zero; duplicate/delete do not mutate the source Draft. The current ticket deliberately has no Draft-content autosave endpoint because component-empty Drafts have no mutable component data.
- A stale expected Trip or referenced Draft version receives `409 VERSION_CONFLICT` with a stable `fields.currentVersion` or `fields.currentDraftVersion` value, respectively. Validation remains `400 VALIDATION_FAILED`, owner/unknown resources remain identical `404 RESOURCE_NOT_FOUND`, and infrastructure failures remain safe `500 INTERNAL_ERROR`; no failed request reports success or partially persists.
- The V13 forward migration, aggregate changes, and integration tests preserve successful alternatives/updates through refresh and restart, with no component search/selection, merge, polling, Planned promotion, booking/cancellation, sharing, UI, or Version 2 Event work.

| Acceptance criterion | Planned behavior |
| --- | --- |
| Multiple durable independent Drafts and explicit duplication | V13 permits many Drafts; distinct create and duplicate routes insert a fresh UUID Draft and return the reloaded aggregate. |
| Mutable shared details before Planned alternatives | Owner-scoped full shared-detail replacement reuses P03-T01 validation/label logic and conditionally increments the Trip version. |
| No lost concurrent updates | Conditional Trip/Draft predicates make one request succeed and stale peers receive a `409` with the relevant current version. |
| Distinguishable failures and retry safety | Stable error codes plus transactional write units roll back a persistence failure so retrying the same expected version inserts only once. |
| Isolated Draft deletion and zero alternatives | Owner-qualified parent/Draft lookup deletes precisely one Draft, keeps foreign and unknown responses indistinguishable, and permits `drafts: []`. |
| API-level durability proof | MockMvc, direct H2 integrity checks, Flyway tests, and file-H2 restart coverage exercise the lifecycle, races, rollback, and persistence. |

## Scope

### In scope

- A forward-only V13 schema migration which removes the one-Draft-per-Trip uniqueness restriction while preserving UUID, parent FK, and version constraints.
- Owner-scoped APIs, strict request parsing, transactional JDBC conditional writes, lifecycle operations, response mapping, conflict/error contract, and validation reuse for shared Trip edits and component-empty Draft alternatives.
- Migration, two-user isolation, lifecycle, optimistic-concurrency, rollback/retry, concurrent-request, and restart-persistence integration coverage.

### Out of scope

- React Trip client, autosave indicator UI, browser conflict affordances, polling, real-time collaboration, automatic merging, or retry automation (P03-T06 owns UI status).
- Component search, selection, repricing, compatibility/removal summaries, Draft-content mutation, snapshot storage, and populated-Draft shared-detail behavior (P03-T04 and Phase 4).
- Planned/Booked/Canceled alternatives, promotion, booking/cancellation, sharing, profile organization, Trip deletion endpoint, custom naming, or Version 2 Events.
- Compatibility aliases, replacement schemas, legacy-data migration, or modifications to applied migrations V1--V12.

## Active Project Guardrails

None recorded in `ai/thoughts/design-lens.md`.

## Impact and Risk Analysis

The lifecycle changes are persisted, owner-authorized, and concurrent. V13 must only remove `uq_detour_trip_draft_trip_id`; it must not model future itinerary statuses or components. Any alternative operation must use a parent lookup constrained by both public Trip ID and principal owner before resolving a Draft, so foreign Trip/Draft combinations disclose neither existence nor version.

The selected optimistic boundary is deliberate. The Trip version represents shared Trip details and the alternative collection, so every shared replacement/create/duplicate/delete conditionally advances it. This serializes collection changes and ensures a stale tab cannot add or remove alternatives after another tab has changed the aggregate. An individual Draft version represents the named alternative; duplicate/delete include `expectedDraftVersion` so a future Draft-content change cannot cause an old view to copy or delete an unexpectedly changed Draft. This ticket has no component fields, so it does not invent a no-op Draft update solely to increment that version.

Use a `409 VERSION_CONFLICT` error with stable safe fields: a parent conflict returns `fields.currentVersion`; a still-visible named-Draft conflict returns `fields.currentDraftVersion`. The client can GET the Trip to deliberately reload the aggregate. If the parent or target is absent for this owner, return the existing generic not-found body instead of an existence-revealing conflict. Validation occurs before a conditional update; all database writes for a successful mutation occur in one service transaction. A database error after the parent version changes must roll the version change back as well, allowing one retry with the original expected version and no duplicate Draft.

The `PUT` body is a complete shared-detail representation plus `expectedVersion`, rather than a partial patch. Autosave callers can send their last observed full model; this keeps traveler count and ordered ages atomic and avoids ambiguous field omission, especially for nullable budget. It follows the creation envelope and does not accept client label, owner, origin, version result, or component fields. Current storage contains only Drafts, so the “while no Planned alternative exists” condition is satisfied without introducing a speculative lifecycle table/check; P03-T03/P03-T04 will extend the rule when Planned storage exists.

## Implementation Approach

Keep HTTP adaptation in `TripController`, request-shape validation in `TripRequests`, product validation and transaction demarcation in `TripService`, and all owner-qualified/conditional SQL in `TripRepository` plus `JdbcTripRepository`. Extend the current aggregate records only as required to address Drafts by parent/internal ID during a transaction; responses remain opaque public IDs.

Add these explicit JSON contracts, retaining strict allowed-field checks:

- `PUT /api/trips/{tripId}`: `{expectedVersion, destinationKey, startDate, endDate, travelerCount, travelerAges?, budgetCents?}`. A `null`/omitted budget clears/retains absence just as creation does; the complete payload prevents an accidental partial replacement.
- `POST /api/trips/{tripId}/drafts`: `{expectedVersion}`. This is always component-empty creation, never duplication.
- `POST /api/trips/{tripId}/drafts/{draftId}/duplicate`: `{expectedVersion, expectedDraftVersion}`. It requires that exact source Draft but creates a new UUID Draft at version zero and leaves source rows unchanged.
- `DELETE /api/trips/{tripId}/drafts/{draftId}`: `{expectedVersion, expectedDraftVersion}`. Returning the reloaded `TripResponse` with `200` exposes the new aggregate version and remaining alternatives for intentional next actions.

For each route, return a full `TripResponse` after success (`200` for replace/delete and `201` for create/duplicate). SQL must make optimistic updates atomic: conditionally update `detour_trip.version = version + 1` based on owner-visible parent/version and, for Draft-specific operations, the named Draft/version; distinguish zero affected rows by owner-scoped current-state probes into not-found, parent conflict, or named-Draft conflict. Perform the Draft insert/delete or traveler replacement only after the guard succeeds and within the same transaction. Use a new Draft UUID per lifecycle insert and reload with existing ordered aggregate mapping.

The alternative of only checking a Draft version was rejected because it would allow concurrent tabs to change the shared alternatives collection under an unchanged Trip version. The alternative of an aggregate-wide version only was rejected because a future Draft-content update could make an observed duplicate/delete source stale without conveying which resource changed. The alternative of a partial PATCH was rejected because null/omitted shared values and correlated count/age fields need a deterministic complete autosave representation.

## Phase 1: Evolve the Durable Alternative Schema

### Changes

- [ ] `src/main/resources/db/migration/V13__allow_multiple_component_empty_drafts.sql` — drop only `uq_detour_trip_draft_trip_id`, preserving `detour_trip_draft` public-ID uniqueness, parent foreign key, non-negative version check, and child lookup index so multiple Draft rows can persist for one Trip.
- [ ] `src/test/java/app/detour/DetourApplicationTest.java` — advance the clean Flyway expectation through V13; assert the Trip tables remain unseeded and the clean schema accepts the intended Draft cardinality without changing catalog/identity fixtures.
- [ ] `src/test/java/app/detour/catalog/PhaseOneCatalogForwardMigrationIntegrationTest.java` — advance the V2-to-latest expectation through V13 and retain its existing identity/catalog/empty-Trip persistence proof.

### Automated verification

- [ ] `.\mvnw.cmd -DskipFrontend=true -Dtest=DetourApplicationTest,PhaseOneCatalogForwardMigrationIntegrationTest test` — clean and forward-migrated H2 databases reach V13 while retaining prior data and permit multiple Drafts per Trip.

### Optional developer checks

- [ ] none.

### Success criteria

V13 applies after V12 without altering prior migration files or prior catalog/identity data, and its only lifecycle-model effect is removing the one-Draft-per-Trip restriction.

## Phase 2: Add Versioned Owner-Scoped Mutation Contracts

### Changes

- [ ] `src/main/java/app/detour/trip/TripRequests.java` — add strict `SharedDetailsUpdate`, `DraftCreate`, and `DraftMutation` request records/parsers. Require integral non-negative expected versions; use the existing typed date/count/age/budget parsing and reject unsupported, client-owned, component, label, lifecycle, or idempotency fields.
- [ ] `src/main/java/app/detour/trip/TripController.java` — add authenticated `PUT /api/trips/{tripId}`, `POST /api/trips/{tripId}/drafts`, `POST /api/trips/{tripId}/drafts/{draftId}/duplicate`, and body-bearing `DELETE /api/trips/{tripId}/drafts/{draftId}` mappings. Bind owner exclusively from `DetourUserPrincipal`; return `200` full responses for update/delete and `201` full responses for new alternatives.
- [ ] `src/main/java/app/detour/trip/TripService.java` — add transactional shared replacement/create-empty/duplicate/delete methods. Reuse P03-T01's supported destination, March date, traveler, all-or-nothing age, nullable integer-cent budget, and label logic; generate a fresh Draft UUID on insert; preserve draft source data; and map missing resources, parent conflicts, and Draft conflicts to the stable contracts.
- [ ] `src/main/java/app/detour/trip/TripRepository.java`, `Trip.java`, and `TripDraft.java` — expose only the owner-scoped aggregate and operations/metadata needed to conditionally mutate a Trip and resolve a named Draft within it; do not expose a global Draft lookup or model component/status fields.
- [ ] `src/main/java/app/detour/trip/JdbcTripRepository.java` — reload all Drafts in stable internal-ID order; atomically compare/update the Trip version, replace traveler rows only after that comparison, insert duplicate/empty Draft rows at version zero, and delete exactly the resolved target row. Classify a failed conditional write through owner-scoped current-version reads, never a cross-owner query.
- [ ] `src/main/java/app/detour/trip/TripResponse.java` and `DraftResponse.java` — retain the existing public aggregate shape/version fields unless a small response-only addition is necessary for the explicit operation contract; do not expose database IDs, owner IDs, component selections, prices, state, or snapshots.
- [ ] `src/main/java/app/detour/api/ApiException.java`, `ApiError.java`, and/or `ApiExceptionHandler.java` — preserve the established error envelope while adding no broad exception behavior: service-created `409 VERSION_CONFLICT` errors carry the stable relevant current-version field and safe reload guidance.

### Automated verification

- [ ] `.\mvnw.cmd -DskipFrontend=true -Dtest=TripApiIntegrationTest test` — authenticated lifecycle, complete shared-detail replacement, strict failure contracts, owner isolation, and optimistic concurrency pass against real Flyway/JDBC/Security/H2 boundaries.

### Optional developer checks

- [ ] none.

### Success criteria

Each owner-visible mutation is explicit and transactionally guarded by its observed version(s); successes return their persisted current aggregate, and no mutation adds a component, Planned state, background behavior, or cross-user disclosure.

## Phase 3: Prove Lifecycle, Concurrency, Failure, and Durability

### Changes

- [ ] `src/test/java/app/detour/trip/TripApiIntegrationTest.java` — extend the authenticated CSRF/session integration suite with owner create-empty, explicit duplicate, selected-Draft deletion, and zero-alternative recreation scenarios. Assert new Draft UUID/version identity, unchanged duplicate source, exact remaining collection, aggregate-version increments, and no component/lifecycle fields.
- [ ] `src/test/java/app/detour/trip/TripApiIntegrationTest.java` — cover complete shared-detail replacement across supported destination/date/traveler/age/budget boundaries, server-derived label recomputation, nullable budget versus zero, strict request fields/types, and unchanged persisted data/version after a validation failure.
- [ ] `src/test/java/app/detour/trip/TripApiIntegrationTest.java` — use two independently authenticated sessions and a barrier/executor for same-observed-version shared updates and collection mutations. Assert one success, one `409 VERSION_CONFLICT` with the current version, and stored details/drafts matching only the success; compare foreign and unknown Trip/Draft mutation responses for non-disclosure.
- [ ] `src/test/java/app/detour/trip/TripApiIntegrationTest.java` — retain/extend the H2 trigger approach to fail an alternative insert after the parent version guard, assert safe `500 INTERNAL_ERROR` and full transaction rollback, remove the trigger, retry with the same expected version, and assert exactly one new Draft rather than a duplicate.
- [ ] `src/test/java/app/detour/trip/TripApplicationRestartIntegrationTest.java` — create/update/lifecycle-mutate a Trip through real loopback HTTP on temporary file H2, restart, authenticate again, and assert the exact Trip/Draft IDs, surviving alternatives, shared details, label, and final versions persist while the old session remains invalid.

### Automated verification

- [ ] `.\mvnw.cmd -DskipFrontend=true -Dtest=TripApiIntegrationTest,TripApplicationRestartIntegrationTest test` — HTTP API behavior, two-user isolation, races, rollback/retry, and restart persistence pass without external services.

### Optional developer checks

- [ ] With `DETOUR_SECURE_COOKIES=false` only on a local loopback instance, use two separately authenticated browser sessions to save from the same Trip view and verify that the losing session presents a reloadable conflict state. This is supplementary; P03-T06 owns the UI.

### Success criteria

The executable tests demonstrate every ticket lifecycle/concurrency/persistence acceptance criterion, including no duplicate after a failed create and a durable usable zero-alternative aggregate.

## Phase 4: Run Regression and Package Verification

### Changes

- [ ] `README.md` and `scripts/verify-packaged-identity.ps1` — leave unchanged unless implementation evidence shows an existing documented command requires a narrow, Trip-aware correction. The restart integration test is the authoritative durable authenticated Trip proof for this ticket.

### Automated verification

- [ ] `.\mvnw.cmd test -DskipFrontend=true` — all backend, migration, security, concurrency, and restart regression tests pass.
- [ ] `npm.cmd run test --prefix frontend` — unchanged frontend tests remain green.
- [ ] `npm.cmd run build --prefix frontend` — unchanged frontend production build succeeds.
- [ ] `.\mvnw.cmd package` — the full Maven frontend/package path produces the executable JAR.
- [ ] `powershell.exe -ExecutionPolicy Bypass -File .\scripts\verify-packaged-identity.ps1` — packaged loopback identity shell still starts with an isolated temporary H2 database.

### Optional developer checks

- [ ] none.

### Success criteria

All safe repository regression and packaging gates pass without live suppliers, model credentials, production data, or an introduced compatibility path.

## Test Strategy

Step 3 should first add the smallest API integration test for an additional owner Draft from a valid observed Trip version; it fails before implementation because the route/schema cardinality are absent. Keep most coverage at the MockMvc integration boundary because request strictness, CSRF/authentication, owner-qualified SQL, transactions, error JSON, and version outcomes interact there. Use direct JDBC only to prove precise durable state and rollback. Use a controlled executor/barrier for true concurrent API calls rather than relying only on sequential stale requests. Preserve the temporary file-H2 loopback restart test for application-lifecycle persistence, and retain Flyway clean/forward tests for schema evolution. No frontend test is added: UI autosave states are deliberately deferred.

## Acceptance-Criteria Traceability

| Acceptance criterion | Planned code evidence | Planned test evidence |
| --- | --- | --- |
| Multiple independent Drafts; explicit duplication; source unchanged; restart durable | V13; controller lifecycle routes; service/repository fresh UUID insert and source lookup | create/duplicate/list identity assertions and file-H2 restart scenario |
| Supported shared edits, versions, ownership, label, absent/zero budget | complete PUT parser; service validation/label; conditional Trip update and traveler replacement | success/boundary matrix with reloaded database/API assertions |
| Same-version clients cannot overwrite silently | Trip conditional update and `VERSION_CONFLICT` fields | two-session sequential and barrier-concurrent success/conflict tests |
| Validation/persistence errors differ; retry avoids duplicate | existing validation/internal error envelopes; transactional conditional insert | unsupported/body validation plus H2-trigger rollback/remove/retry count test |
| Selected Draft delete, isolation, zero-alternative usability | owner-qualified target resolution; guarded single-row delete; retained Trip | two-user foreign/unknown equality, delete-one, delete-last/create-new tests |
| Integration proof including isolation, concurrency, rollback, restart | `TripApiIntegrationTest`, restart test, migration tests | focused backend suite and full safe backend regression |
| No later-phase behavior | intentionally narrow schema/DTO/controller/service surface | response/request absence assertions and scoped review/search |

## Risks and Rollback/Recovery

The primary deployment risk is an incorrect V13 constraint change or conditional-write sequence. Review the migration against the exact V12 constraint name, run clean and forward migration tests, and keep alternative inserts/deletes and parent-version increments in one transaction. If a conditional write affects no rows, do not treat it as success or retry it server-side: classify it from owner-scoped state and return the client-visible conflict/not-found result.

The primary authorization risk is resolving a Draft globally before checking its parent owner. Keep public Trip ID plus principal owner in every parent query and scope every Draft predicate to that resolved Trip. The primary recovery risk is a persistence failure after a Trip version bump; rollback must restore that version so a deliberate client retry does not falsely conflict or create an extra Draft.

This is a forward-only development migration. Before deployment, rollback is reverting the ticket implementation and V13 together, then recreating disposable development data via the documented reset workflow. Do not edit V1--V12 or add downgrade/compatibility paths. After an environment has applied V13, correct defects with a new forward migration rather than pointing it at a pre-V13 application. No external inventory, supplier, credential, or booking recovery is involved.

## References

- `ai/thoughts/tickets/2026-09-19-p03-t02-deliver-versioned-draft-alternatives.md`
- `ai/thoughts/research/2026-09-19-p03-t02-deliver-versioned-draft-alternatives.md`
- `ai/thoughts/phases/README.md`
- `ai/thoughts/phases/phase-3-trips-itineraries-and-profile.md`
- `src/main/resources/db/migration/V12__create_owned_trip_and_initial_draft_schema.sql`
- `src/main/java/app/detour/trip/TripController.java`
- `src/main/java/app/detour/trip/TripService.java`
- `src/main/java/app/detour/trip/JdbcTripRepository.java`
- `src/test/java/app/detour/trip/TripApiIntegrationTest.java`
- `src/test/java/app/detour/trip/TripApplicationRestartIntegrationTest.java`
