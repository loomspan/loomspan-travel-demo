---
date: 2026-09-21
repository: loomspan-travel-demo
branch: main
commit: 3d6f3a1748a1d15f76e36b7231c32876f8024c52
ticket: ai/thoughts/tickets/2026-09-19-p03-t04-protect-shared-detail-revisions.md
tags: [phase-3, trips, shared-details, revisions, selective-duplication, planned-snapshots, draft-selections, concurrency, revalidation]
---

# P03-T04 Protected Shared-Detail Revisions and Selective Trip Duplication Research

## Research Question

What existing Trip, Draft, Planned alternative, catalog inventory, concurrency, and persistence mechanisms constrain how mutable Draft selections are revalidated when shared details change, how Trips with Planned alternatives are protected from in-place mutation, and how active Trips are selectively duplicated from Planned snapshot sources?

## Summary

The repository currently supports owner-scoped Trip aggregates containing mutable `detour_trip_draft` alternatives and immutable `detour_planned_itinerary` snapshots. P03-T03 established durable Draft selection foreign keys (`detour_trip_draft_airfare_selection`, `detour_trip_draft_stay_selection`, `detour_trip_draft_rental_selection`) and copied snapshot records (`detour_planned_airfare_snapshot`, `detour_planned_stay_snapshot`, `detour_planned_stay_night_snapshot`, `detour_planned_rental_snapshot`).

Currently, `TripService.replaceSharedDetails` updates destination, dates, travelers, and budget directly in place on `detour_trip` and replaces `detour_trip_traveler` rows. It does not inspect whether Planned alternatives exist, does not revalidate Draft selections against revised details, does not check capacity, room count, or age eligibility, does not remove incompatible components, and does not return any change summary. Furthermore, there is no endpoint or service method for Trip-level selective duplication from Planned sources into a new Trip aggregate.

Authoritative catalog tables and fixtures for airfare, stays, and rentals (established in Phase 2 via migrations V3–V11) define exact validation and pricing rules: round-trip flight instances with dated schedules and available seat counts; accommodation units with room versus whole-property unit kinds, guest capacities, and nightly pricing/inventory; and destination airport rental units requiring local date intervals and at least one traveler aged 25 or older.

## Repository State

- Recorded 2026-09-21T11:00:00-07:00 on repository `loomspan-travel-demo`, branch `main`, commit `3d6f3a1748a1d15f76e36b7231c32876f8024c52` (`clean up after P03-T03`).
- `git status --short` confirmed a clean working tree prior to writing this artifact.
- Recent commit history shows completion of Phase 3 foundational milestones:
  - `d2f236c`: P03-T01 — Establish Owned Trips and Initial Drafts
  - `f0a7508`: P03-T02 — Deliver Versioned Draft Alternatives and Autosave
  - `47db74e`: P03-T03 — Establish Planned Snapshot Lifecycle
- The test suite (`mvn test`) runs cleanly with 43 tests passing across unit, catalog integrity, identity security, and Trip integration suites.

## Current Behavior and Data Flow

1. **Shared-Detail Updates (`PUT /api/trips/{tripId}`)**:
   - `TripController.replace` (`TripController.java:38-41`) extracts the authenticated user ID and delegates to `TripService.replaceSharedDetails`.
   - `TripService.replaceSharedDetails` (`TripService.java:60-75`) parses `TripRequests.SharedDetailsUpdate`, validates destination key, date bounds (March 1–31, 2027, 1–14 nights), traveler count (1–8), traveler ages (0–120), and budget (0–100,000,000 cents).
   - It performs an optimistic concurrency check via `trips.advanceVersion(trip.id(), ownerUserId, request.expectedVersion())` (`JdbcTripRepository.java:90`), updating the Trip version by +1.
   - It executes `trips.replaceSharedDetails` (`JdbcTripRepository.java:93-97`), updating `catalog_destination_id`, `start_date`, `end_date`, `traveler_count`, `budget_cents`, and `display_label` in `detour_trip`, then deletes and re-inserts `detour_trip_traveler` rows.
   - **Current limitation**: This method completely ignores existing Planned alternatives on the Trip, allowing in-place edits even when immutable snapshots exist. It leaves all existing Draft selections in `detour_trip_draft_*_selection` untouched, even if they point to flights, stays, or rentals that contradict the new destination, dates, traveler count, or ages. It returns `TripResponse` with no revision summary.

2. **Draft Selections and Planned Snapshots**:
   - `detour_trip_draft_airfare_selection` stores `outbound_flight_instance_id` and `return_flight_instance_id` (`V14:4-12`).
   - `detour_trip_draft_stay_selection` stores `accommodation_unit_id` and `unit_count` (`V14:14-21`).
   - `detour_trip_draft_rental_selection` stores `rental_unit_id`, `pickup_at`, and `return_at` (`V14:23-31`).
   - `detour_planned_itinerary` stores `public_id` and `trip_id` (`V14:33-40`).
   - Snapshot tables (`detour_planned_airfare_snapshot`, `detour_planned_stay_snapshot`, `detour_planned_stay_night_snapshot`, `detour_planned_rental_snapshot`) copy resolved prices, descriptions, and catalog keys (`V14:42-96`).
   - In `JdbcTripRepository.loadDraftSelections` (`JdbcTripRepository.java:69-77`), Draft selections are loaded with foreign keys only (descriptions are null and prices are 0). In `loadPlannedSelections` (`JdbcTripRepository.java:79-88`), snapshot details are fully populated from the snapshot tables.

3. **Existing Promotion and Alternative Duplication**:
   - `TripService.promoteDraft` (`TripService.java:111-123`) resolves Draft selections via `trips.resolveSelectionsForPromotion(trip, draft)` (`JdbcTripRepository.java:120-142`). It validates readiness (`readinessIssues`, lines 260-267) requiring non-null ages, at least one adult (age >= 18), non-null budget, and at least one structurally valid component.
   - `TripService.duplicateAlternative` (`TripService.java:126-142`) duplicates an individual Draft or Planned source into a new Draft within the *same* Trip aggregate. For Planned sources, it calls `trips.insertDraftCopy` (`JdbcTripRepository.java:102-111`), creating a new Draft row and copying foreign keys into the Draft selection tables.
   - **Current limitation**: There is no Trip-level revision or duplication workflow. No endpoint exists to duplicate an entire Trip aggregate from selected Planned snapshots into a new Trip.

4. **Component Catalog Validation and Pricing Inputs**:
   - **Airfare** (`V3:85-117`): Flight instances have `flight_schedule_id`, `service_date`, `base_fare_cents`, `tax_cents`, `fee_cents`, `seat_capacity`, and `available_seats`. Outbound and return schedules connect PDX and destination airports. Price is per traveler.
   - **Stays** (`V4:1-53`, `V5:1-30`): Accommodation units have `guest_capacity`, `inventory_capacity`, and `unit_kind` (`ROOM` for hotels/B&Bs, `WHOLE_PROPERTY` for vacation rentals). Nightly inventory specifies `available_inventory`, `base_price_cents`, `tax_cents`, and `fee_cents`. For rooms, required unit count is `ceil(travelerCount / guest_capacity)`. For whole properties, required unit count is 1 and `travelerCount <= guest_capacity`.
   - **Rentals** (`V4:54-114`): Rental units link to vehicle classes with `daily_base_price_cents`, `daily_tax_cents`, and `daily_fee_cents`. Pickup and return occur at destination airport locations. Consecutive 24-hour cycles round up. Requires at least one traveler aged 25 or older.

## Key Components

- `src/main/java/app/detour/trip/TripService.java:60-75` — `replaceSharedDetails`: in-place shared-detail mutation, currently lacking Planned-trip protection and Draft revalidation.
- `src/main/java/app/detour/trip/TripService.java:111-123` — `promoteDraft`: uses `resolveSelectionsForPromotion` and `readinessIssues` to validate Draft readiness.
- `src/main/java/app/detour/trip/TripService.java:126-142` — `duplicateAlternative`: duplicates single alternatives within an existing Trip.
- `src/main/java/app/detour/trip/TripController.java:38-41` — HTTP PUT handler for `/api/trips/{tripId}`.
- `src/main/java/app/detour/trip/TripRequests.java:20-21, 37-42` — `SharedDetailsUpdate` record and JSON parser with strict field validation.
- `src/main/java/app/detour/trip/TripRepository.java:22-37` — persistence interface for Trip aggregate creation, version advancement, shared-detail replacement, and component resolution.
- `src/main/java/app/detour/trip/JdbcTripRepository.java:93-97` — SQL update for `detour_trip` and `detour_trip_traveler`.
- `src/main/java/app/detour/trip/JdbcTripRepository.java:102-111` — `insertDraftCopy` and `insertDraftSelections`: inserts Draft selection foreign keys.
- `src/main/java/app/detour/trip/JdbcTripRepository.java:120-142` — `resolveSelectionsForPromotion`: SQL queries resolving catalog data for flight instances, stays, and rentals.
- `src/main/resources/db/migration/V14__create_draft_selection_and_planned_snapshot_schema.sql:4-31, 42-96` — schema for mutable Draft selection foreign keys and immutable Planned snapshot details.

## Affected Areas

| Area | Current behavior and evidence |
| --- | --- |
| In-place shared-detail update | `replaceSharedDetails` (`TripService.java:60-75`) executes unconditionally without checking `trip.planned().isEmpty()`. It does not revalidate Draft selections, does not remove incompatible references, and returns `TripResponse` without any revision summary. |
| Protection of Planned Trips | Trips containing Planned alternatives can currently be mutated in place via `PUT /api/trips/{tripId}` (`TripService.java:62-74`), violating the immutability guarantee for Planned travel alternatives. |
| Budget-only updates | Budget changes are bundled into `SharedDetailsUpdate` (`TripRequests.java:20-21`). No logic distinguishes a budget-only update from a destination/date/traveler update when Planned alternatives exist. |
| Draft component revalidation | There is no logic to reprice or revalidate Draft selections against updated traveler counts or ages (`detour_trip_draft_*_selection` rows remain unchanged regardless of capacity, room count, or age eligibility). |
| Component compatibility removal | Changing destination or dates does not delete incompatible rows in `detour_trip_draft_airfare_selection`, `detour_trip_draft_stay_selection`, or `detour_trip_draft_rental_selection`. References persist even if the catalog items belong to another destination or date range. |
| Trip selective duplication | No endpoint or service method exists to duplicate an active Trip from an explicit list of Planned snapshot sources into a new Trip aggregate. |
| Revision and change summary | No data structure or response field exists to summarize removed components, price changes, room-count changes, or eligibility failures with user-readable explanations. |
| Authorization and isolation | Current alternative operations check ownership (`TripService.java:162-185`) and return 404 for foreign or invalid IDs without leaking details. A Trip-level duplication endpoint must enforce the same atomic owner-isolation rules on the source Trip and source Planned snapshot list. |

## Existing Tests and Fixtures

- `src/test/java/app/detour/trip/TripApiIntegrationTest.java`:
  - `createsOwnedTripAndInitialComponentEmptyDraft` (lines 45-74): verifies aggregate creation and version 0.
  - `validatesEnvelopeAndNeverPersistsPartialAggregate` (lines 77-116): tests strict JSON validation and date/party boundaries.
  - `preservesUnknownAgesAndAbsentBudgetSeparatelyFromZero` (lines 119-133): verifies absent vs zero budget handling.
  - `updatesDuplicatesDeletesAndConflictsWithoutLostWrites` (lines 209-230): tests `PUT /api/trips/{tripId}` with shared details, version conflicts (409), and Draft duplicate/delete.
  - `promotesAReadyDraftAsAnIndependentImmutablePlannedSnapshot` (lines 302-334): tests promotion with populated airfare selection and verifies snapshot immutability against catalog mutation.
  - `promotesEachStructurallyValidSelectionKindWithoutCanonicalPricing` (lines 356-397): tests stay-only, rental-only, and all-three component combinations.
  - `plannedSnapshotReadsCopiedContentAfterDraftAndCatalogMutation` (lines 400-435): verifies snapshot stability after Draft selection mutations.
  - `duplicatesDraftAndPlannedSourcesIntoIndependentDraftCopies` (lines 438-472): tests duplicating individual Draft and Planned sources within the same Trip.
  - `alternativeLifecycleMutationsDoNotRevealForeignTargets` (lines 475-510): tests 404 nondisclosure for foreign alternative IDs.
  - `sameVersionPromotionAndDuplicateRacesHaveOneCompleteWinner` (lines 539-591): tests concurrent race conditions on parent and alternative versions.
  - `plannedLifecycleLeavesCatalogInventoryUntouched` (lines 594-631): verifies that promotion, duplicate, and delete do not mutate catalog seats, nightly inventory, or occupancy.
- `src/test/java/app/detour/trip/TripApplicationRestartIntegrationTest.java:21-102`:
  - Starts Spring Boot, registers user, creates Trip with Draft selections, promotes to Planned, duplicates Planned to Draft, mutates catalog, stops app, restarts against same H2 file, and verifies persisted data across restart.
- `src/test/java/app/detour/catalog/AirfareFixtureIntegrityAssertions.java` & `CatalogFixtureIntegrityAssertions.java`:
  - Validates all Phase 2 fixtures across SFO, MUC, MEX: flight schedules, instances, seat capacities, stay properties, units, room vs whole-property kinds, nightly inventories, rental classes, and locations.

## Dependencies and Operational Constraints

- **Java 21 / Spring Boot 4.1.0 / H2 / Flyway**: All persistence modifications must use Flyway migrations if schema changes are needed. However, existing schema in V12, V13, and V14 already supports multi-draft owned Trips, Draft selection foreign keys, and Planned snapshot tables.
- **Transactional Atomicity**: All mutations must run under `@Transactional`. Failure or invalid input (such as an invalid source Planned ID) must fail atomically without leaving partial Trips or orphan rows.
- **Optimistic Concurrency**: Stale `expectedVersion` values must produce deterministic `409 VERSION_CONFLICT` responses with the current version.
- **Authoritative Catalog Inputs**: Repricing and capacity checks must query the authoritative Phase 2 catalog tables rather than trusting client-supplied values or stale snapshot data.
- **Immutability of Planned Snapshots**: Source Trips and their Planned snapshots must never be modified or deleted during Trip revision or duplication.
- **Forward Compatibility for Phase 6**: Selective Planned-source copying and component revalidation rules must be reusable by Phase 6 when it introduces canceled-Trip duplication. Phase 6 owns canceled status, booking history, and the component-empty fallback when no Planned source exists; P03-T04 must not invent canceled records or states.

## Historical Context

- **P03-T01** (`d2f236c`) established the base owned Trip aggregate (`detour_trip`, `detour_trip_traveler`), initial blank Draft, and strict date/budget bounds (March 1–31, 2027; budget up to 100,000,000 cents).
- **P03-T02** (`f0a7508`) delivered multi-Draft alternatives under a single Trip, optimistic versioning on parent and Draft rows, and noted that component compatibility summaries for populated Drafts belong to P03-T04.
- **P03-T03** (`47db74e`) established immutable Planned snapshots and Draft selection storage (`detour_trip_draft_*_selection` and `detour_planned_*_snapshot`). It established that Drafts hold only mutable catalog foreign keys, while Planned snapshots copy resolved descriptive and price details.
- **Roadmap Continuation & Phase 3 Specification** (`ai/thoughts/phases/phase-3-trips-itineraries-and-profile.md:28-37`):
  - Work package 3.3 requires that when no Planned alternative exists, in-place changes reprice and revalidate Draft selections, removing invalid ones and summarizing changes.
  - When Planned alternatives exist, in-place changes to destination, dates, or travelers must be rejected, requiring a new Trip built from explicitly selected Planned snapshots.
  - Incompatible components in copied Drafts must be removed, compatible components retained, and an explanatory post-duplication summary returned.
  - Budget-only updates must recalculate budget presentation without mutating selections, and distinct zero versus absent budgets must be preserved.

## Open Questions

1. **Revision Summary Contract**: What exact JSON structure should represent the structured change summary? Should it be embedded directly in `TripResponse` as a `revisionSummary` object containing `removals` and `adjustments`/`changes`, ensuring full backward compatibility with existing tests expecting top-level Trip fields on `PUT /api/trips/{tripId}`?
2. **Selective Duplication Endpoint**: Should active-Trip selective duplication be exposed via `POST /api/trips/{tripId}/duplicate` (consistent with `/api/trips/{tripId}/alternatives/{alternativeId}/duplicate`) with a request body containing `expectedVersion`, revised shared details, and `sourcePlannedItineraryIds`?
3. **Draft Age Revalidation when Traveler Ages are Omitted**: If traveler ages were originally provided and then updated to null/omitted during a shared-detail update, does an existing rental car selection remain valid or does it require age verification? (Rental selection requires at least one traveler aged 25+; if ages are absent, is rental removed or retained as a Draft selection until promotion readiness?).
4. **Airfare Repricing Semantics**: Since airfare prices in Phase 2 are stored per-seat in `flight_instance` (`base_fare_cents`, `tax_cents`, `fee_cents`), does a change in party size (e.g. from 2 to 4 travelers) constitute a price change in the revision summary, or does the summary report total complete-party fare changes vs per-seat changes?
