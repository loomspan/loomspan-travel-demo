---
date: 2026-09-20
repository: loomspan-travel-demo
branch: main
commit: 17697c3dd201033715c3f05fa7b6deb0fe731456
ticket: ai/thoughts/tickets/2026-09-19-p03-t03-establish-planned-snapshot-lifecycle.md
tags: [phase-3, trips, drafts, planned-snapshots, persistence, concurrency, authorization]
---

# P03-T03 Planned Snapshot Lifecycle Research

## Research Question

What current Trip, Draft, catalog, API, persistence, concurrency, test, and roadmap behavior constrains the addition of immutable Planned snapshots; Draft/Planned duplication; and alternative deletion?

## Summary

The checked-out application has an owner-scoped Trip aggregate with a collection of component-empty, mutable `detour_trip_draft` rows. Its public API can create, duplicate, and delete Draft rows only; every collection mutation advances the Trip version and named Draft mutations also check the source Draft version. There is currently no selected-component persistence, Planned lifecycle/state, snapshot record, catalog-read service, Trip-facing frontend API, or UI.

The existing Phase 2 schema provides stable numeric catalog/inventory identifiers plus descriptive and pricing inputs for dated flight instances, accommodation properties/units/nightly inventory, and rental vehicle classes/units. The ticket's required copied resolved content cannot be observed in the current Trip responses because `TripDraft` contains only its identifiers and version; the persisted component-selection shape remains an unimplemented cross-ticket boundary with Phase 4.

## Repository State

- Recorded 2026-09-20T12:36:45-07:00 on repository `loomspan-travel-demo`, branch `main`, commit `17697c3dd201033715c3f05fa7b6deb0fe731456` (`clean up after P03-T02`).
- `git status --short` produced no entries before this research artifact was written. No pre-existing developer changes were present.
- Recent relevant history is `d2f236c` (P03-T01), `f0a7508` (P03-T02), and the clean-up commit above. The P03-T01/T02 ticket execution notes describe the current migration, ownership, and versioning choices; their checked-in code is the implementation evidence.

## Current Behavior and Data Flow

1. An authenticated request reaches `TripController`, which obtains the user ID exclusively from the authenticated principal and delegates to `TripService`; the controller only exposes Trip creation/detail, shared-detail replacement, blank-Draft creation, Draft duplication, and Draft deletion. There are no Planned routes (`src/main/java/app/detour/trip/TripController.java:25-61`).
2. `TripService.create` validates supported destination, supported dates, party size, optional exact ages, and optional nonnegative budget; it creates a UUID Trip and initial UUID Draft in one transaction (`src/main/java/app/detour/trip/TripService.java:29-46`). Age absence is represented as `null` values in the traveler table and budget absence as `NULL`, distinct from zero (`src/main/java/app/detour/trip/TripService.java:135-156`). The existing adult check is deliberately absent: the service comment assigns adult readiness to future Planned promotion (`src/main/java/app/detour/trip/TripService.java:18-19`).
3. Owner-scoped detail lookup joins `detour_trip` to its destination, loads ordered travelers and all Draft rows, and returns no result for a foreign Trip (`src/main/java/app/detour/trip/JdbcTripRepository.java:66-88`; `src/main/java/app/detour/trip/TripService.java:109-114`). The API maps a Trip to shared fields plus `List<DraftResponse>`; a Draft response has only `id` and `version` (`src/main/java/app/detour/trip/TripService.java:176-181`, `src/main/java/app/detour/trip/DraftResponse.java:5-6`).
4. Shared-detail updates use a conditional `UPDATE detour_trip ... version = ?` and then replace every traveler row in the same service transaction (`src/main/java/app/detour/trip/TripService.java:60-75`, `src/main/java/app/detour/trip/JdbcTripRepository.java:91-117`). A stale parent version produces a `409 VERSION_CONFLICT` with the current Trip version (`src/main/java/app/detour/trip/TripService.java:121-133`).
5. Blank-Draft creation advances the Trip version then inserts one row. Named Draft duplication currently advances the Trip only if the source Draft ID, owning Trip ID, and expected source Draft version all match; it then inserts a new blank Draft. Named Draft deletion uses the same guard and deletes only that row (`src/main/java/app/detour/trip/TripService.java:78-107`, `src/main/java/app/detour/trip/JdbcTripRepository.java:97-126`). The resulting concurrency behavior protects the collection and the selected existing Draft, but there is no content to copy today.
6. The `detour_trip` table persists owner, destination, dates, traveler count, nullable budget, label, and a nonnegative version. `detour_trip_traveler` holds an ordinal and nullable age. `detour_trip_draft` holds only a public ID, owning Trip foreign key, and version; V13 removed V12's one-Draft-per-Trip constraint (`src/main/resources/db/migration/V12__create_owned_trip_and_initial_draft_schema.sql:1-47`, `src/main/resources/db/migration/V13__allow_multiple_component_empty_drafts.sql:1-3`). No migration after V13 introduces an itinerary status or component-selection/snapshot table.
7. The current frontend makes only identity/profile requests. The authenticated profile renders an explicit empty state and has no Trip API client, alternatives view, promotion control, duplication control, or delete confirmation UI (`frontend/src/api/identityApi.ts:1-56`, `frontend/src/components/ProfileScreen.tsx:20-24`, `frontend/src/App.tsx:1-95`).

## Key Components

- `src/main/java/app/detour/trip/TripService.java:16-188` — transactional Trip and Draft lifecycle service; validates shared data, enforces owner-scoped optimistic guards, and translates stale mutations to deterministic API errors.
- `src/main/java/app/detour/trip/TripController.java:25-61` — authenticated HTTP surface, currently restricted to Draft alternatives.
- `src/main/java/app/detour/trip/TripRepository.java:9-28` and `src/main/java/app/detour/trip/JdbcTripRepository.java:17-129` — persistence interface and SQL implementation for aggregate loading, guarded version advancement, and Draft row insert/delete.
- `src/main/java/app/detour/trip/Trip.java:7-9`, `TripDraft.java:5-6`, `TripResponse.java:7-9`, and `DraftResponse.java:5-6` — in-memory and serialized contracts, which model only Draft alternatives.
- `src/main/resources/db/migration/V12__create_owned_trip_and_initial_draft_schema.sql:1-47` and `V13__allow_multiple_component_empty_drafts.sql:1-3` — current owned Trip, traveler, and multi-Draft persistence lineage.
- `src/main/resources/db/migration/V3__create_shared_catalog_and_flight_schema.sql:1-117` — normalized destinations, suppliers, flight schedules/segments, and dated flight-instance price/capacity/timing records with catalog keys.
- `src/main/resources/db/migration/V4__create_catalog_inventory_schema.sql:1-113` and `V5__add_accommodation_search_metadata.sql:1-30` — catalog structures for properties/units/nightly inventory, rental locations/classes/units/occupancy, and property display/search metadata.
- `src/test/java/app/detour/trip/TripApiIntegrationTest.java:45-315` — existing HTTP/integration patterns for ownership nondisclosure, validation, transactional rollback, Draft collection mutations, and stale-version concurrency.
- `src/test/java/app/detour/trip/TripApplicationRestartIntegrationTest.java:22-69` — loopback application-restart persistence pattern for Trip/Draft data.

## Affected Areas

| Area | Current behavior and evidence |
| --- | --- |
| Alternative lifecycle | Alternatives are modeled as a homogeneous Draft table, with no lifecycle discriminator or Planned table. Duplication creates an empty sibling instead of copying content, and deletion has no confirmation field (`TripService.java:78-107`; `V12__create_owned_trip_and_initial_draft_schema.sql:36-47`). |
| Owner authorization | All Trip retrieval begins with `(public_id, owner_user_id)` and target Draft lookup occurs within that owned aggregate. Foreign and random targets both resolve to the same 404 response, as exercised by `TripApiIntegrationTest.java:236-249`. |
| Concurrency and transactions | Service mutation methods are `@Transactional`; parent versions protect collection operations and source Draft versions protect named operations. The existing test has a two-client same-version shared-edit race and controlled-insert rollback coverage (`TripApiIntegrationTest.java:251-303`). |
| Readiness inputs | Trip persistence can represent exact ages and a supplied zero-or-positive budget, but permits absent ages/budget and a no-adult party. No readiness aggregation response or catalog-reservability check exists (`TripService.java:135-156`; `V12__create_owned_trip_and_initial_draft_schema.sql:15-31`). |
| Catalog references and copied data | Flight instances retain immutable catalog keys, dated pricing and availability, and segment times; stays retain property/unit descriptions and per-night price/inventory; rentals retain class descriptions/prices and individual units. These are catalog records only: no Trip tables reference any of them (`V3__create_shared_catalog_and_flight_schema.sql:85-117`; `V4__create_catalog_inventory_schema.sql:1-106`). |
| Inventory effects | The only reservation-like catalog structure is `rental_unit_occupancy`, constrained by an overlap trigger. Current Trip/Draft methods do not query or mutate it, flight availability, or stay availability (`V4__create_catalog_inventory_schema.sql:38-64,97-113`; `JdbcTripRepository.java:17-129`). |
| Client/API contract | Requests strictly reject unknown properties, and current named Draft operations require `expectedVersion` plus `expectedDraftVersion` (`TripRequests.java:31-72`). No Trip-facing frontend client currently calls these APIs. |

## Existing Tests and Fixtures

- `TripApiIntegrationTest` verifies initial aggregate persistence, server validation, owner isolation/nondisclosure, empty-Draft creation/duplication/deletion, stale parent/source versions, controlled database-insert rollback, and one two-client race for shared details. It does not cover component copies, Planned records, readiness issue aggregation, confirmation-gated Planned deletion, catalog mutation stability, source-type duplication, or promotion races.
- `TripApplicationRestartIntegrationTest` restarts an embedded loopback Spring application against file-backed H2 and verifies persisted Trip/Draft identifiers, alternative count, version, and nullable budget. It does not exercise any snapshot content.
- The catalog integration suite (`src/test/java/app/detour/catalog/`) contains schema, fixture, money/time, and rental-overlap checks. It tests catalog data as shared inventory and has no user-owned selection/snapshot behavior.
- The frontend tests currently cover identity/authentication components only; no Trip or alternative UI test exists (`frontend/src/api/identityApi.test.ts`, `frontend/src/App.test.tsx`, and `frontend/src/components/`).

## Dependencies and Operational Constraints

- The project is Java 21/Spring Boot with H2/Flyway and a React/Vite frontend. Trip migrations are forward-only after the fresh DeTour lineage; roadmap policy says development databases are disposable but startup must not silently delete them (`ai/thoughts/phases/README.md`, Development-stage clean-break policy).
- Phase 3 defines Drafts as mutable incomplete alternatives and Planned alternatives as immutable; multiple of both may coexist. It also defines a Canceled Booking as immutable booking history rather than an itinerary state (`ai/thoughts/phases/phase-3-trips-itineraries-and-profile.md`, sections 3.1-3.4).
- Phase 4 owns the progressive search/selection UI and the detailed source-specific rules for exactly one airfare, stay, and rental selection. It requires server-calculated persisted selection pricing but provides no implemented selection record/API in this checkout (`ai/thoughts/phases/phase-4-component-selection.md`, sections 4.1-4.4).
- Phase 5 owns canonical server-side component/grand-total calculation, stale/unavailable/sold-out checks, budget-overage acknowledgement, and comparison. It separately requires stable Planned snapshots with copied resolved details and inventory identifiers (`ai/thoughts/phases/phase-5-planning-budget-and-comparison.md`, sections 5.1-5.4).
- Phase 6 owns actual transactional inventory reservation/release plus Booked and Canceled Booking records. It requires destructive terminology to remain distinct and says Planned/Draft deletion has no inventory effect (`ai/thoughts/phases/phase-6-booking-and-cancellation.md`, sections 6.2-6.4).
- No research command contacted any external service or changed production code.

## Historical Context

- P03-T01 established nullable ages/budget, the stable owned aggregate, and documented that adult readiness belongs to future promotion. Its execution notes identify the maximum age as 120 and maximum budget as 100,000,000 cents (`ai/thoughts/tickets/2026-09-19-p03-t01-establish-trip-draft-foundation.md`, Requirements and Execution notes).
- P03-T02 intentionally limited its alternative work to component-empty Drafts. Its execution notes state that named duplicate/delete operations advance the parent only when the selected Draft version matches, and that V13 removed the one-Draft-per-Trip restriction (`ai/thoughts/tickets/2026-09-19-p03-t02-deliver-versioned-draft-alternatives.md`, Scope exclusions and Execution notes).
- The current ticket explicitly permits establishing snapshot storage but prohibits adding Phase 4 search UI/results APIs, Phase 5's competing pricing contract, or Phase 6 booking/cancellation records. Its stated reassessment trigger is inability to snapshot selections without settling a material Phase 4/5 contract (`ai/thoughts/tickets/2026-09-19-p03-t03-establish-planned-snapshot-lifecycle.md`, Context and Execution profile).

## Open Questions

- The checkout has no persisted selection representation from which to copy the required resolved descriptive/price data. Planning must establish whether the ticket can define the minimum durable selection-and-snapshot records directly from the settled Phase 2 catalog identifiers and fields without choosing Phase 4 search/result APIs or Phase 5 canonical-total behavior. This is the ticket's explicit reassessment boundary.
- Existing named mutations require the parent and source Draft version. A Planned source has no current version contract, and no request shape exists for confirmation-gated Planned deletion; the current API only accepts `expectedVersion`/`expectedDraftVersion`. The plan must specify the observable concurrency and confirmation boundary from requirements rather than assume an existing one.
- No booking-history tables or Trip protection flags exist. The ticket says Planned deletion is allowed when no booking-history rule protects it while also excluding booking records; planning must record the currently observable absence of such records and preserve the future enforcement boundary without adding fake Booked/Canceled itinerary states.

