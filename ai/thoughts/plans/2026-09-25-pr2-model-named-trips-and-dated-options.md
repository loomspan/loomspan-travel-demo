# Named Trips and Dated Options Implementation Plan

## Overview
- Ticket: `ai/thoughts/tickets/2026-09-25-pr2-model-named-trips-and-dated-options.md`
- Research: `ai/thoughts/research/2026-09-25-pr2-model-named-trips-and-dated-options.md`
- Outcome: A Trip has a user name, shared destination and traveler group, exactly one dated Working plan, and independently named and dated Saved options. Existing bookings and meaningful alternatives survive an additive forward migration.

## Current State
`TripService.create` derives a label and creates one Draft, while `validateAges` accepts omitted ages. `TripRequests`, `TripResponse`, and `TripProfileSummary` expose dates and label at Trip level. `JdbcTripRepository` reads any number of Drafts and Planned rows; Draft selections refer to catalog rows and use the Trip dates, whereas Planned selections carry copied facts. `V13__allow_multiple_component_empty_drafts.sql` removed the unique Draft-per-Trip constraint. `BookingTransactionExecutor` resolves Planned by public ID but uses Trip dates for expiry and cancellation. The PR1 `TripStartForm` collects a name without sending it and permits blank ages. PR1's uncommitted source and tests are the baseline and must be preserved.

## Desired End State
1. Creation requires a trimmed nonblank name and exactly one valid age per traveler, persists one empty Working plan with supplied dates, and creates no Saved option. Merely viewing Home, Trips, or start form causes no writes.
2. The Trip owns `name`, destination, count, ordinal ages, budget/status/version; the Working plan owns dates and mutable selections; each Saved option owns name, dates, copied component/price facts, and an option version. Two options may have different dates without changing shared travelers.
3. Rename is an owner-scoped, expected-version Trip mutation. Duplicate names are legal; Trip public ID and Booking references are unchanged.
4. V19 migrates every old Trip and Planned row without changing their IDs. It chooses one Working Draft deterministically, converts other populated Drafts into Saved options with copied facts, removes redundant empty Drafts, and creates a Working Draft where absent. Legacy null ages remain null until a user supplies them; only new Trip creation requires complete ages.
5. Booking and cancellation use the relevant Saved option's dates; immutable booked Planned rows and Booking snapshots remain intact. Both old and new database histories project the same response shape.

## Scope
### In scope
- Forward-only H2 Flyway migration, Java domain/repository/service/controller contracts, booking date checks, TypeScript API types and PR1 create form handoff, targeted regression tests.
- A minimal read/write contract for rename, Working dates, and Saved option identity/version/dates to support PR3/PR5. Keep existing component selection and booking operations functional through that contract.

### Out of scope
- PR3's user workflow for save/update/copy options and PR4/PR5 visual redesign.
- Traveler identities or birthdates, destination changes between options, anonymous records, historical itinerary edit log, catalog expansion.

## Active Project Guardrails
None recorded in `ai/thoughts/design-lens.md`.

## Impact and Risk Analysis
- **Migration/data loss:** Old data can contain multiple populated Drafts, no Drafts, nullable ages, and booked Planned rows. Preserve every populated Draft selection as a Saved option, choosing the lowest-ID populated Draft as Working; if none populated, choose lowest-ID empty Draft, or create one. Copy extra populated Draft selections to Planned snapshot rows using current catalog values and the old Trip dates. Delete only redundant empty Drafts after conversion. Retain old Planned IDs and all Booking FKs. Assert pre/post row and component counts in migration tests. If a selected catalog fact cannot be resolved, fail migration transactionally rather than discard it.
- **Compatibility:** Existing `display_label`, Trip-level date columns, `drafts`/`planned`/`alternatives`, and API routes have many current callers. Keep these as compatibility projections for this series, with `name`, `workingPlan`, and `savedOptions` as authoritative new fields. Trip-level dates project Working dates only; no code may use them for option expiry or booking. Keep `label` aligned to `name` for old client rendering, not a derived date pair. This is a temporary bridge, not a promise that dates are shared.
- **Booking integrity:** Booked Planned rows must never be rewritten by an unbooked option edit. PR2 adds version-guarded option persistence primitives; PR3 may update only unbooked options, and any booking-linked option is immutable. Active booking FK and Booking snapshot rows stay stable; cancellation uses booked option dates (or a frozen booking date if later deletion becomes possible). Current `ON DELETE SET NULL` allows canceled booking history, so retain an immutable booked-date source for cancellation and history where needed.
- **Concurrency/ownership:** Parent `detour_trip.version` advances on all mutations; Working Draft and Saved option also have row versions. Every mutation resolves the parent by owner and uses conditional updates inside a transaction; stale parent or child versions return 409. A database unique constraint on `detour_trip_draft.trip_id` enforces one Working row even under races.
- **Availability:** Saved facts are a snapshot from save time, not a perpetual inventory guarantee. Booking still checks and locks current inventory. Date-specific searches/readiness and booking validation use the selected Working/option dates.

## Implementation Approach
Keep `detour_trip_draft` as the single Working row and `detour_planned_itinerary` as the Saved-option snapshot row. This reuses existing component snapshot and booking FK tables. Add names, dates, and option version where they belong; `detour_trip` retains its historical date columns as compatibility fields synchronized to Working dates. A separate new option table would require moving every Booking FK and snapshot relationship, with more migration risk and no needed benefit. For old extra populated Drafts, a Flyway Java migration (version 19, in `src/main/java/db/migration/`) can reuse application snapshot resolution only if its dependency/lifecycle is safe; otherwise implement deterministic SQL copy with explicit catalog joins in `V19__...sql`. Do not call the ordinary promotion service during Flyway startup. Make migration fail on unresolvable rows and verify atomic rollback in tests.

## Phase 1: Schema and faithful migration
### Changes
- [x] `src/main/resources/db/migration/V19__model_named_trips_and_dated_options.sql` (or a same-version Java Flyway migration at `src/main/java/db/migration/V19__ModelNamedTripsAndDatedOptions.java`) — add Trip `name`, Draft `start_date`/`end_date`, Planned `name`/`start_date`/`end_date`/`version`; backfill from old Trip label/dates; preserve every existing Planned PK/public ID and Booking FK; choose Working Draft by populated-first/lowest-ID; copy other populated Drafts to Planned snapshot records; remove redundant empty Drafts; create missing Working rows; add nonnull/check/unique constraints after normalization. Use stable readable generated option names such as `Option 1`, collision-safe within each Trip. Maintain a mapping during migration to assert each populated Draft was copied before deleting it.
- [x] `src/test/java/app/detour/trip/TripModelForwardMigrationIntegrationTest.java` — seed a V18 database with multiple Draft shapes, Planned rows, active/canceled booking refs, null ages and duplicate labels; migrate to latest and compare IDs, names, dates, selections, Booking refs, and exactly one Working row. Test fresh DB schema too.
### Automated verification
- [x] `mvn -DskipFrontend=true -Dtest=TripModelForwardMigrationIntegrationTest test` — migration works on representative V18 and fresh databases without lost populated selections or broken Booking FKs.
### Optional developer checks
- [ ] On a backed-up local database copy, inspect migrated Trip names/options before using the live database. This is not an automated gate.
**Success:** Exactly one Draft per Trip; all old Planned/Booking IDs and references remain; each old extra populated Draft has one Saved representation; empty Draft clutter is absent.

## Phase 2: Domain, persistence, and owner-scoped contracts
### Changes
- [x] `src/main/java/app/detour/trip/Trip.java`, `TripDraft.java`, `PlannedItinerary.java`, `TripAlternative.java` — expose Trip name, Working dates, and each Saved option's name/dates/version; preserve public identities.
- [x] `src/main/java/app/detour/trip/TripRepository.java`, `JdbcTripRepository.java` — create one Working row with dates, load date/name/version fields, use Working dates for Draft pricing, conditionally rename and change Working dates, and add owner/parent/option-version guarded update primitives without changing booked option snapshots. Keep old Trip date columns synchronized with Working dates for old projections.
- [x] `src/main/java/app/detour/trip/TripRequests.java`, `TripController.java`, `TripService.java` — require `name` and complete ages at create, validate/trim names and date ranges; expose a versioned rename route and Working-date mutation contract; reject creating/duplicating/deleting a second Working Draft while keeping a Working row when deleting/copying; make existing promotion create a named, dated Saved snapshot without replacing Working. Distinguish incomplete legacy ages from new creation, and require complete ages before operations that need age-dependent validation.
- [x] `src/main/java/app/detour/trip/TripResponse.java`, `DraftResponse.java`, `AlternativeResponse.java`, `TripProfileSummary.java`, `AlternativeProfileSummary.java` — include `name`, `workingPlan`, `savedOptions`, per-option dates/names/versions; keep old projection fields consistent and do not present every option as sharing Trip dates. Calculate per-option expiry and Trip list categorization from respective dates, with documented deterministic Trip grouping based on Working dates.
- [x] `src/test/java/app/detour/trip/TripApiIntegrationTest.java` — create validation, no duplicate Working rows, independent rename and option dates, stale/cross-owner failures, and response consistency.
### Automated verification
- [x] `mvn -DskipFrontend=true -Dtest=TripApiIntegrationTest test` — new contract and legacy path regressions pass.
### Optional developer checks
- [ ] None.
**Success:** Create returns one Working and zero Saved; same-name Trips stay separate; every write is version guarded and owner scoped; per-option dates survive reread.

## Phase 3: Booking semantics and frontend contract
### Changes
- [x] `src/main/java/app/detour/booking/BookingTransactionExecutor.java`, `BookingService.java`, `BookingResponse.java`, `JdbcBookingRepository.java` — resolve selected Saved option before expiry check, use its dates for booking eligibility and cancellation, preserve immutable booked option and Booking facts. Avoid relying on Trip-level dates for a booked option with different dates.
- [x] `src/main/java/app/detour/trip/TripService.java`, `JdbcTripRepository.java` — update readiness, search, promotion, and option expiry to use Working/option dates; pass correct dates through snapshot resolution and tally paths. Revalidate date-sensitive selections before changing Working dates and report removals/adjustments.
- [x] `frontend/src/api/tripsApi.ts`, `frontend/src/components/TripStartForm.tsx` — send name and complete ages from PR1 form; type new response fields; retain old projections while PR3–PR5 update UI. Update `frontend/src/components/TripWorkspace.tsx` only where existing client behavior would violate the new one-Working invariant or omit required name/ages.
- [x] `src/test/java/app/detour/booking/BookingApiIntegrationTest.java`, `BookingConcurrencyIntegrationTest.java`, `BookingApplicationRestartIntegrationTest.java`, `src/test/java/app/detour/trip/TripApplicationRestartIntegrationTest.java`, `frontend/src/PublicTripFlow.test.tsx` — cover differently dated option booking, conflict/ownership, restart, and PR1 create payload.
### Automated verification
- [x] `mvn -DskipFrontend=true test` — all backend suites pass on isolated test databases.
- [x] `npm test -- --run` from `frontend/` — frontend regressions pass.
- [x] `npm run build` from `frontend/` — TypeScript and Vite build pass.
### Optional developer checks
- [ ] In a local configured environment, inspect two date-distinct options and a migrated booked Trip. Report as optional observation, not a gate.
**Success:** A dated option's booking/expiry follows that option, Booking refs survive restart, and PR1 form creates a named Trip with all ages.

## Test Strategy
Begin with API and migration red tests for missing name/per-option dates, then implement migration and contracts. Use isolated H2/test-clock integration tests already established in this repository. Cover fresh and V18-upgraded schema, populated/empty/no Draft cases, nullable legacy ages, duplicate Trip names, stale writes, cross-owner access, option-specific booking expiry, and restart. No test should touch the persistent default runtime DB.

## Acceptance-Criteria Traceability
| Acceptance criterion | Planned code evidence | Planned test evidence |
| --- | --- | --- |
| New named Trip, complete ages, one Working plan | Create parser/service/repository and PR1 form payload | `TripApiIntegrationTest`, `PublicTripFlow.test.tsx` |
| Two named Saved options with different dates | Planned metadata, repository reads, response mapping | `TripApiIntegrationTest` date-isolation case |
| Rename keeps identity/options/bookings; duplicate names | Versioned rename mutation | `TripApiIntegrationTest` rename and duplicate-name cases |
| Upgrade preserves bookings/populated alternatives, removes empty Drafts | V19 migration and one-Draft constraint | `TripModelForwardMigrationIntegrationTest` |
| Stale/cross-owner protection and Booking restart | Conditional updates, Booking date handling | `TripApiIntegrationTest`, Booking tests and restart tests |
| Fresh/upgraded response parity | Response mapping over normalized schema | Migration and API integration assertions |

## Risks and Rollback/Recovery
Flyway migration is forward-only; take a backup of any persistent DB before rollout. A failed migration should leave V18 data intact and surface its error, not silently omit selections. Restore the DB backup for rollback; do not edit applied V1–V18 files. The main correctness hazard is dereferencing Trip dates after option-specific dates exist; search all `trip.startDate()`/`trip.endDate()` consumers and cover booking and expiry paths with distinct-date tests.

## References
- `ai/thoughts/tickets/2026-09-25-pr2-model-named-trips-and-dated-options.md`
- `ai/thoughts/research/2026-09-25-pr2-model-named-trips-and-dated-options.md`
- `src/main/java/app/detour/trip/TripService.java`, `JdbcTripRepository.java`, `TripRequests.java`
- `src/main/java/app/detour/booking/BookingTransactionExecutor.java`
- `src/main/resources/db/migration/V12__create_owned_trip_and_initial_draft_schema.sql`, `V13__allow_multiple_component_empty_drafts.sql`, `V14__create_draft_selection_and_planned_snapshot_schema.sql`, `V18__add_trip_status_and_cancellation_constraints.sql`
