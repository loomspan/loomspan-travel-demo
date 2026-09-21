# P03-T03 Immutable Planned Snapshot Lifecycle Implementation Plan

## Overview

- Ticket: `ai/thoughts/tickets/2026-09-19-p03-t03-establish-planned-snapshot-lifecycle.md`
- Research: `ai/thoughts/research/2026-09-19-p03-t03-establish-planned-snapshot-lifecycle.md`
- Outcome: Persist typed Draft selections and immutable, owner-scoped Planned alternatives; promote, duplicate, and delete them atomically while keeping catalog inventory untouched.

## Current State

`TripService` and `JdbcTripRepository` currently own a Trip with a homogeneous list of component-empty `TripDraft` rows.  `TripController` exposes only Draft creation, duplication, and deletion, and `TripResponse` serializes only draft IDs and versions.  Parent Trip optimistic versioning protects collection changes; named Draft operations additionally guard the source Draft version.

The Phase 2 catalog already has stable relational identifiers and all deterministic descriptive/price source data needed for a snapshot: flight instances and segments, accommodation property/unit/nightly inventory, and rental location/class/unit.  No table currently stores a selected component, a Planned snapshot, or copied catalog content.  There is no Trip-facing UI, so this ticket must establish the backend lifecycle/API contract but not introduce a Phase 4 builder or search surface.

## Desired End State

A Trip owns multiple mutable Draft alternatives and multiple immutable Planned alternatives.  A Draft with exact traveler ages, an adult, a non-null nonnegative budget, and at least one structurally valid typed selection can be promoted without consuming the Draft.  Promotion writes a distinct Planned snapshot whose component descriptions, schedules, and price breakdown remain unchanged if the Draft or catalog later changes, while retaining catalog/inventory IDs for later Phase 5/6 revalidation.

Each owned Draft or Planned alternative can explicitly be copied into a new Draft.  Draft deletion remains direct; Planned deletion requires an explicit confirmation.  All alternative mutations advance the Trip version and are guarded so a stale/repeated/racing request cannot add duplicate snapshots or leave a partially copied graph.  Foreign trip and alternative targets remain indistinguishable from unknown targets.  No total calculation, availability/staleness decision, inventory reservation, booking-history record, `Canceled` itinerary state, search/result API, or frontend builder is added.

## Scope

### In scope

- Forward-only Flyway schema for typed Draft selections and immutable Planned snapshots, including stable catalog/inventory references and copied resolved fields.
- Trip aggregate, repository, HTTP request/response, and service lifecycle support for promotion, source-agnostic duplication, and lifecycle-appropriate deletion.
- Deterministic readiness aggregation limited to shared Trip fields and structural component integrity available from the current catalog.
- Owner-scoped persistence, optimistic concurrency, transaction rollback, restart persistence, and catalog/inventory non-mutation verification.

### Out of scope

- Phase 4 component search, ranking, UI, autosave UI state, or public component-builder/result APIs.
- Phase 5 canonical/grand-total calculation, sold-out/stale/availability validation, budget-overage acknowledgement, comparison, or booking-choice workflow.
- Phase 6 reservation/release, idempotency keys, booking/cancellation/history rows, read-only canceled Trip behavior, and active-booking protections.
- A `Canceled` itinerary lifecycle value, generalized opaque snapshot payload, compatibility routes, or migration of disposable development databases.

## Active Project Guardrails

None recorded in `ai/thoughts/design-lens.md`.

The roadmap's clean-break policy applies: replace the current Draft-only alternative HTTP/response contract coherently rather than retaining aliases or a dual representation.  Backend authorization must be owner-scoped, all client-supplied catalog IDs/pricing assumptions must be server validated, and all money remains integer cents.

## Impact and Risk Analysis

- **Persisted contract:** Planned data cannot be derived from live catalog joins.  The migration must store snapshot values in typed relational tables, not JSON/opaque payloads, and referential links must be durable without cascading catalog edits into snapshot content.
- **Selection boundary:** Phase 4 owns searches and ordinary selection editing, but this ticket needs a narrow persisted selection shape to exercise promotion.  Use one typed selection record per component kind (airfare, stay, rental) with the stable source IDs and trip-specific selection parameters.  Expose no new public selection-write/search route; Phase 4 will own writes against this schema/API boundary.  Integration tests may arrange these records through repository/test fixtures.
- **Readiness boundary:** Promotion verifies only exact ages, an adult, supplied budget, and at least one internally consistent selection matching the owned Trip's destination/dates/party constraints and catalog referential shape.  It intentionally does not calculate canonical totals, test current inventory/availability, decide staleness, or require overage acknowledgement; those remain Phase 5.
- **Authorization and disclosure:** Find the Trip by `(public_id, owner_user_id)` before resolving its alternative.  A foreign or unknown Trip/alternative must return the same 404 response; do not reveal whether a foreign alternative is Draft or Planned.
- **Concurrency/atomicity:** A promotion/duplicate/delete must conditionally advance the Trip version together with source checks before graph writes.  The transaction must roll back the version if any snapshot or copy insert fails.  The parent version means simultaneous operations with the same request version produce one winner and one deterministic conflict.
- **Lifecycle compatibility:** Planned records are immutable and have no edit path.  Model source type separately from itinerary mutability so a later Phase 6 immutable Booked/Canceled Booking snapshot can participate in the same duplicate-to-Draft source abstraction without treating `Canceled` as a mutable itinerary status.
- **Recovery/deployment:** This is an additive forward migration.  Development databases are disposable and need an explicit reset if already at the previous Flyway version; application startup must not delete data.  Rollback is code/migration reversal plus recreation of disposable development data, not a compatibility schema.

## Implementation Approach

Introduce a discriminated alternative aggregate: `TripDraft` remains the mutable record, while a new `PlannedItinerary` record represents a separately stored immutable source.  Add a sealed/internal source abstraction (for example `TripAlternative`) so duplicate and delete dispatch on lifecycle without conflating Planned with Draft.  Change `Trip` and `TripResponse` to carry an ordered list of typed alternatives (and typed component summaries) instead of a Draft-only list; retain IDs/versions for Drafts and assign a public ID to every Planned snapshot.

Define the minimum normalized selection/snapshot storage contract directly from current Phase 2 catalog, avoiding a premature Phase 4 API design.  Draft tables hold one optional selection each for airfare (outbound/return flight-instance IDs), stay (unit ID and required-unit count), and rental (unit ID plus pickup/return instants).  Planned tables copy each selected component's immutable display and pricing facts, its stable source/catalog IDs, and the applicable detail rows: flight/segment details, nightly-stay price lines, and rental duration/price details.  The copied fields are read from catalog data inside promotion; no client-provided price/description is accepted.  Foreign keys retain later revalidation keys but never cause a catalog display/price update to rewrite snapshot columns.

Use a single source-agnostic duplication endpoint and delete endpoint under `alternatives`, with a promotion endpoint explicitly under `drafts`.  The duplicate request always carries `expectedVersion`; it additionally carries `expectedDraftVersion` only when duplicating a mutable Draft.  The request parser/service must reject an absent or mismatched Draft version for Draft sources and must not invent a version for Planned data.  Planned delete carries `confirmed: true` and the expected Trip version; Draft delete does not accept a confirmation requirement.  Before Phase 6 there is no booking-history guard to enforce, but the deletion service/repository boundary must make room for a later protected-snapshot predicate without adding booking tables now.  A caller that presents an owned Planned ID to a Draft-only mutation resolves the lifecycle and returns a deterministic immutable-alternative conflict; foreign/unknown still return 404.

The selected approach is preferable to a generic `status` column plus JSON snapshot because it preserves immutable typed content, keeps source-specific catalog references explicit for Phase 5/6, and supports the future immutable-source kinds.  It is also preferable to simply joining live catalog records at read time, which violates the snapshot requirement.  It intentionally stops short of Phase 4's public selection/autosave API: internal typed persistence is the smallest contract that makes promotion real without taking ownership of search or builder behavior.

## Phase 1: Establish Typed Alternative and Snapshot Persistence

### Changes

- [ ] `src/main/resources/db/migration/V14__create_draft_selection_and_planned_snapshot_schema.sql` — add Draft airfare/stay/rental selection tables with one row per Draft/type and foreign keys to the existing catalog rows; store only source identifiers and selection-specific values required to validate/copy (including stay unit count and rental interval).  Add `detour_planned_itinerary` with a public UUID and Trip foreign key, plus explicit typed snapshot/detail tables for flight legs/segments, stay property/unit/night rows, and rental location/class/unit/price/duration.  Constrain positive counts, non-negative money, valid rental intervals, unique component ownership, and unique public IDs; index Trip/alternative lookups.  Do not add a status column, JSON payload, booking table, inventory mutation trigger, or destructive catalog cascade.
- [ ] `src/main/java/app/detour/trip/Trip.java`, `TripDraft.java`, and new package-private domain records such as `PlannedItinerary`, `TripAlternative`, `DraftSelections`, and typed component/snapshot value records — represent Draft and Planned lifecycle sources distinctly, preserve stable public/internal IDs, and expose immutable lists/value objects for loaded content.
- [ ] `src/main/java/app/detour/trip/TripRepository.java` and `JdbcTripRepository.java` — load all owned alternatives and their typed Draft/snapshot content; add catalog-backed read projections that validate/copy selection source data; add graph insert/copy/delete methods.  Ensure all repository writes are scoped by owned Trip IDs and that snapshot reads use copied columns rather than live catalog descriptions/prices.

### Automated verification

- [ ] `./mvnw.cmd test -DskipFrontend=true -Dtest=TripApiIntegrationTest,TripApplicationRestartIntegrationTest` — proves the new Flyway schema boots and aggregate loading preserves typed Draft/Planned graphs.
- [ ] `./mvnw.cmd test -DskipFrontend=true -Dtest=CatalogSchemaIntegrationTest,TripApiIntegrationTest` — proves the new references coexist with catalog constraints and tests do not need inventory writes.

### Optional developer checks

- [ ] Reset only the disposable local `data/detour.mv.db` with `./scripts/reset-detour.ps1 -ConfirmReset` if a developer needs to run the new Flyway lineage against a pre-V14 local database; this is not part of automated verification.

### Success criteria

The database can represent multiple typed Draft and Planned alternatives under one Trip; every Planned row has copied relational snapshot data plus stable catalog/inventory IDs, and a Trip reload never derives Planned descriptions or prices from mutable catalog fields.

## Phase 2: Implement Owner-Scoped Lifecycle Operations and Contracts

### Changes

- [ ] `src/main/java/app/detour/trip/TripRequests.java` — replace Draft-only mutation parsing with strict, lifecycle-aware request records for promotion, duplicate, and delete.  Continue rejecting unknown JSON properties; require a non-negative expected Trip version everywhere, require a Draft source version only for Draft source operations, and require `confirmed: true` for Planned deletion.
- [ ] `src/main/java/app/detour/trip/TripService.java` — add transactional `promoteDraft`, `duplicateAlternative`, and `deleteAlternative` operations.  Aggregate all readiness problems (unknown ages, no adult, missing budget, and no valid selection/selection structural failures) into one deterministic validation response.  On successful promotion, conditionally advance the Trip/source Draft versions, resolve server-authoritative catalog fields, insert a new immutable Planned graph, and leave the Draft and other alternatives untouched.  Duplicate each allowed source into a new Draft by copying its typed content/references, reset the new Draft's mutable version, and preserve source bytes/content.  Delete only the selected owned alternative; reject unconfirmed Planned deletion and Planned IDs used by a Draft-only write with stable errors.  Keep inventory tables read-only and leave the Phase 6 booking-history protection as an explicit extension point.
- [ ] `src/main/java/app/detour/trip/TripController.java` — replace the Draft-only duplicate/delete routes with `POST /api/trips/{tripId}/drafts/{draftId}/plan`, `POST /api/trips/{tripId}/alternatives/{alternativeId}/duplicate`, and `DELETE /api/trips/{tripId}/alternatives/{alternativeId}`.  Take the authenticated principal only from Spring Security, preserve 201 for creation operations, and expose no component search/builder route.
- [ ] `src/main/java/app/detour/trip/TripResponse.java`, `DraftResponse.java`, and new public response records (for example `AlternativeResponse`, `PlannedResponse`, and typed component summaries) — return stable IDs, Draft versions, lifecycle kind, and copied snapshot content sufficient to observe immutability and restart persistence without leaking live catalog joins or introducing Phase 5 totals.

### Automated verification

- [ ] `./mvnw.cmd test -DskipFrontend=true -Dtest=TripApiIntegrationTest` — proves HTTP validation, lifecycle behavior, owner isolation, optimistic conflicts, and transactional rollback.

### Optional developer checks

- [ ] None.

### Success criteria

An authenticated owner can promote a ready Draft, see a separate immutable Planned alternative, duplicate either source into a new Draft, and delete only the intended alternative under the required confirmation/version rules.  A foreign caller cannot distinguish a protected target from an unknown one, and every mutation remains inventory-free.

## Phase 3: Prove Persistence, Isolation, and Race Safety

### Changes

- [ ] `src/test/java/app/detour/trip/TripApiIntegrationTest.java` — extend the existing two-user MockMvc suite with catalog-backed selection setup and assertions for complete readiness-error aggregation; promotion with each supported selection type; immutable copied content after changing a source Draft/another Draft/catalog description or price; Draft/Planned duplication; confirmation-gated deletion; deterministic immutable-update rejection; multiple Planned coexistence; cross-user nondisclosure; controlled graph-insert rollback; and same-version promotion/duplication races.
- [ ] `src/test/java/app/detour/trip/TripApplicationRestartIntegrationTest.java` — create a selected Draft, promote and duplicate it through the real loopback application, restart against file-backed H2, and assert stable alternative IDs plus copied Planned and Draft component content.
- [ ] `src/test/java/app/detour/catalog/CatalogSchemaIntegrationTest.java` or a focused new `src/test/java/app/detour/trip/PlannedSnapshotPersistenceIntegrationTest.java` — assert snapshot promotion/deletion/duplication never changes `flight_instance.available_seats`, `accommodation_nightly_inventory.available_inventory`, or `rental_unit_occupancy`; use database mutations only to demonstrate catalog divergence from copied snapshots.

### Automated verification

- [ ] `./mvnw.cmd test -DskipFrontend=true -Dtest=TripApiIntegrationTest,TripApplicationRestartIntegrationTest` — focused lifecycle, concurrency, transactional rollback, and restart proof.
- [ ] `./mvnw.cmd test -DskipFrontend=true` — broad backend/Flyway regression suite.
- [ ] `./mvnw.cmd package` — full backend-plus-frontend build after the API/serialized contract change.

### Optional developer checks

- [ ] Start the packaged application with an isolated disposable H2 URL, create/promote/duplicate/delete alternatives as two accounts, and confirm no catalog inventory count changes; report this as a configured observation only.

### Success criteria

Focused and full suites demonstrate exactly one winner for racing same-version promotion/duplication, no duplicate/partial snapshot graphs after retries or injected failures, restart-stable identifiers/content, owner nondisclosure, and no Phase 5/6 behavior.

## Test Strategy

Step 3 will specify HTTP integration tests as the primary boundary because the existing suite already verifies security, JSON contracts, H2/Flyway persistence, and transactional behavior.  It will add a restart test for the persisted graph and targeted SQL assertions for immutability/inventory non-effects.  Tests will arrange selected Draft records through the new repository/test-only fixture seam until Phase 4 owns public selection writes; they must never use client-supplied copied prices or call live services.

## Acceptance-Criteria Traceability

| Acceptance criterion | Planned code evidence | Planned test evidence |
| --- | --- | --- |
| Ready promotion and complete readiness issues | `TripService.promoteDraft`, catalog-backed structural validation, strict promotion request | `TripApiIntegrationTest` covers ready source and every simultaneous missing age/adult/budget/component issue. |
| Planned content survives Draft/catalog changes | V14 typed snapshot/detail tables; repository snapshot reads from copied values | Mutate Draft and catalog description/price after promotion; returned/reloaded Planned fields remain equal. |
| Draft/Planned duplication survives restart | `duplicateAlternative`, typed graph copy, response records | HTTP duplicate tests plus `TripApplicationRestartIntegrationTest` assert source stability, new IDs, copied content. |
| Immutable updates and scoped deletion/no inventory effects | lifecycle dispatch, confirmed Planned delete, Draft-only immutable error, inventory-free repository methods | Delete/update tests assert sibling equality and SQL inventory counts/occupancy unchanged. |
| Multiple Planned and owner isolation | plural alternative loader and owner-scoped lookup | Two-user mutation/read tests compare foreign and random 404 responses. |
| Transactional/concurrent safety | conditional Trip/source-version advance and `@Transactional` graph writes | barrier race tests and injected insert failure assert one winner and no orphan/partial rows. |
| No premature Phase 5/6 behavior | no total/availability/booking logic, source abstraction reserved for later immutable types | tests assert no inventory mutation and only limited readiness checks; review source/route scope. |

## Risks and Rollback/Recovery

The primary risk is choosing a snapshot shape that later search or booking cannot revalidate.  The typed source IDs, per-component selection values, and copied source facts intentionally cover Phase 2's concrete catalog entities while deferring search presentation and canonical totals.  A second risk is exposing a Planned lifecycle through a Draft-specific endpoint; the service must resolve lifecycle after owner-scoped aggregate lookup and distinguish immutable-owned from unknown/foreign safely.

If implementation is rolled back before release, revert the application/migration changes and explicitly recreate the disposable development H2 database.  Do not attempt a reverse data migration or preserve a legacy response/schema path.  If Phase 4 later needs additional selection attributes, extend the typed Draft-selection contract and corresponding snapshot copy explicitly rather than writing opaque payload data.

## References

- `ai/thoughts/tickets/2026-09-19-p03-t03-establish-planned-snapshot-lifecycle.md`
- `ai/thoughts/research/2026-09-19-p03-t03-establish-planned-snapshot-lifecycle.md`
- `ai/thoughts/phases/README.md`
- `ai/thoughts/phases/phase-3-trips-itineraries-and-profile.md`
- `ai/thoughts/phases/phase-4-component-selection.md`
- `ai/thoughts/phases/phase-5-planning-budget-and-comparison.md`
- `ai/thoughts/phases/phase-6-booking-and-cancellation.md`
- `src/main/java/app/detour/trip/TripService.java`
- `src/main/java/app/detour/trip/JdbcTripRepository.java`
- `src/test/java/app/detour/trip/TripApiIntegrationTest.java`
