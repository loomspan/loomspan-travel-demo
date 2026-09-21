---
date: 2026-09-21
repository: loomspan-travel-demo
branch: main
commit: d8fb425ebba1913ebc49da4a0ba138fa1f36c3fb
ticket: ai/thoughts/tickets/2026-09-21-p04-t01-airfare-search-and-draft-selection.md
tags: [airfare, search, draft-selection, component-selection, deterministic-sorting, party-pricing, concurrency]
---

# Airfare Search and Draft Selection Research

## Research Question

How does the current DeTour platform support round-trip flight search between fixed origin PDX and Trip destinations, party pricing, deterministic sorting and filtering, and draft airfare selection persistence with optimistic concurrency and cross-user isolation?

## Summary

The repository has completed Phase 0 through Phase 3: the DeTour platform reset, user authentication, Flyway migrations (V1 through V15), the seeded airfare and stay/car catalog fixtures for March 2027, and the user-owned Trip and Draft alternative foundation. Flight catalog fixtures contain 24 flight schedules, 720 flight instances, and 1,080 flight instance segments covering all valid 1–14-night trips between PDX and SFO, MUC, and MEX in March 2027. The relational persistence schema for draft airfare selection (`detour_trip_draft_airfare_selection`) already exists in Flyway migration `V14`, but there are currently no HTTP endpoints or application service methods for searching flights or mutating draft airfare selections.

Draft selections currently store only raw foreign key identifiers (`outbound_flight_instance_id`, `return_flight_instance_id`), and `loadDraftSelections` in `JdbcTripRepository` currently initializes `AirfareSelection` with dummy null descriptions and zero fares. Existing promotion and revalidation code in `JdbcTripRepository` demonstrates joining `flight_instance`, `flight_schedule`, and `catalog_airport` to resolve flight numbers, routes, and per-seat pricing.

## Repository State

- **Date:** 2026-09-21
- **Repository:** `loomspan-travel-demo`
- **Branch:** `main`
- **Commit:** `d8fb425ebba1913ebc49da4a0ba138fa1f36c3fb`
- **Working tree:** Clean (no uncommitted or dirty files).
- **Backend build and test suite:** 61 tests passing via `./mvnw.cmd test`.
- **Frontend test suite:** 36 tests passing via `npm.cmd test --prefix frontend`.

## Current Behavior and Data Flow

### 1. Catalog and Schedule Schema

- **Airports (`catalog_airport`):**
  - Origin airport: PDX (`iata_code = 'PDX'`, `time_zone_id = 'America/Los_Angeles'`, `destination_id IS NULL`).
  - Destination airports: SFO (`America/Los_Angeles`), MUC (`Europe/Berlin`), MEX (`America/Mexico_City`), all linked to their respective `catalog_destination` records.
  - Intermediate layover airports: SEA (`America/Los_Angeles`), SLC (`America/Denver`), ORD (`America/Chicago`), LAX (`America/Los_Angeles`), DFW (`America/Chicago`).
- **Suppliers (`catalog_supplier`):**
  - Two airline suppliers: Cascade Skies (`supplier-cascade-skies`) and Meridian Air (`supplier-meridian-air`).
- **Schedules (`flight_schedule` & `flight_schedule_segment`):**
  - Each destination has 4 outbound schedules (PDX -> destination: 2 direct, 2 one-stop) and 4 return schedules (destination -> PDX: 2 direct, 2 one-stop), totaling 24 schedules.
  - Direct schedules have `stop_count = 0`, `segment_count = 1`.
  - One-stop schedules have `stop_count = 1`, `segment_count = 2`. Ordinal 1 connects PDX to the layover airport; Ordinal 2 connects the layover airport to the destination (or vice versa on return).
  - Layovers are fixed by destination and schedule: SFO via SEA or SLC; MUC via SEA or ORD; MEX via LAX or DFW. Layover durations are at least 45 minutes.
- **Instances (`flight_instance` & `flight_instance_segment`):**
  - Outbound instances span service dates from 2027-03-01 to 2027-03-30.
  - Return instances span service dates from 2027-03-02 to 2027-03-31.
  - Each instance record contains `base_fare_cents`, `tax_cents`, `fee_cents`, `seat_capacity`, and `available_seats`.
  - All instances have initial seat capacity between 32 and 60, with available seats equal to seat capacity (at least 8, accommodating parties of 1–8).
  - Segment times are stored as `TIMESTAMP WITH TIME ZONE` (`departure_at`, `arrival_at`). No arrival occurs after March 31, 2027.

### 2. Trip and Draft Persistence

- **Trip Entity (`detour_trip`):**
  - Columns: `id`, `public_id`, `owner_user_id`, `catalog_destination_id`, `start_date`, `end_date`, `traveler_count`, `budget_cents`, `display_label`, `version`.
  - Trips enforce date intervals within March 1–31, 2027, 1–14 nights length, and 1–8 travelers.
- **Draft Entity (`detour_trip_draft`):**
  - Columns: `id`, `public_id`, `trip_id`, `version`.
  - Multiple drafts per trip are supported (`V13`).
- **Draft Airfare Selection (`detour_trip_draft_airfare_selection`):**
  - Columns: `draft_id BIGINT PRIMARY KEY`, `outbound_flight_instance_id BIGINT NOT NULL`, `return_flight_instance_id BIGINT NOT NULL`.
  - Foreign keys: `draft_id` references `detour_trip_draft(id) ON DELETE CASCADE`; flight instance IDs reference `flight_instance(id)`.
  - Constraint: `ck_draft_airfare_distinct` ensures outbound and return flight instances are distinct.
  - Holds only mutable catalog references, not cached snapshot data (`V14`).

### 3. Existing Draft Loading and Promotion Behavior

- **`JdbcTripRepository.loadDraftSelections(long draftId)` (`JdbcTripRepository.java:87-95`):**
  - Currently queries `detour_trip_draft_airfare_selection` for `outbound_flight_instance_id` and `return_flight_instance_id`.
  - Maps to `AirfareSelection` with dummy values: `new AirfareSelection(r.getLong(1), r.getLong(2), null, null, 0, 0, 0, 0, 0, 0)`.
- **`JdbcTripRepository.resolveAirfare(Trip trip, AirfareSelection selected)` (`JdbcTripRepository.java:147-152`):**
  - Joins `flight_instance` for outbound and return with `flight_schedule` and `catalog_airport`.
  - Verifies that outbound originates at PDX and arrives at the Trip destination on `trip.startDate()`.
  - Verifies that return originates at the Trip destination and arrives at PDX on `trip.endDate()`.
  - Populates flight numbers ("Flight " + `flight_number`) and authoritative base fare, tax, and fee cents.
- **`JdbcTripRepository.revalidateAirfare(...)` (`JdbcTripRepository.java:177-233`):**
  - Revalidates retained draft airfare when trip shared details change (e.g. travelers or dates).
  - Checks if available seats on outbound and return are `>= newTravelerCount`.
  - Calculates repriced complete-party airfare (`(newTravelerCount - oldTravelerCount) * (outboundTotal + returnTotal)`).
  - Removes airfare selection if dates or destination no longer match or seats are insufficient.

### 4. Concurrency and Versioning

- **Trip versioning:** `detour_trip.version` starts at 0 and increments on mutation.
- **Draft versioning:** `detour_trip_draft.version` starts at 0.
- **`TripRepository.advanceVersionForDraft` (`JdbcTripRepository.java:109`):**
  - Updates `detour_trip SET version = version + 1 WHERE id = ? AND owner_user_id = ? AND version = ? AND EXISTS (SELECT 1 FROM detour_trip_draft WHERE id = ? AND trip_id = ? AND version = ?)`.
  - Note: This currently advances `detour_trip.version` and checks `detour_trip_draft.version`, but does not increment `detour_trip_draft.version`.
- **Conflict Handling (`TripService.java:436-447`):**
  - Returns HTTP 409 with error code `VERSION_CONFLICT`.
  - If draft version mismatched: `The Draft has changed. Reload before saving.` (`currentDraftVersion`).
  - If trip version mismatched: `The Trip has changed. Reload before saving.` (`currentVersion`).

### 5. Authentication and Authorization

- Authenticated user principal: `DetourUserPrincipal` provides `userId()`.
- Endpoints enforce authentication via `SecurityConfiguration` and `requirePrincipal(principal)`.
- Ownership scoping: All queries and mutations look up the trip with `findByPublicIdAndOwnerUserId(publicId, ownerUserId)`.
- Non-owned trips throw `ApiException(404, "RESOURCE_NOT_FOUND", ...)` to prevent data disclosure.

## Key Components

- `src/main/resources/db/migration/V3__create_shared_catalog_and_flight_schema.sql:1-118` — Flight catalog DDL (`catalog_destination`, `catalog_airport`, `catalog_supplier`, `flight_schedule`, `flight_schedule_segment`, `flight_instance`, `flight_instance_segment`).
- `src/main/resources/db/migration/V10__seed_march_2027_airfare_catalog.sql:1-107` — Deterministic seeding of 24 flight schedules, 720 flight instances, and 1,080 segments for March 2027.
- `src/main/resources/db/migration/V14__create_draft_selection_and_planned_snapshot_schema.sql:4-12` — DDL for `detour_trip_draft_airfare_selection` storing `draft_id`, `outbound_flight_instance_id`, `return_flight_instance_id`.
- `src/main/java/app/detour/trip/TripController.java:1-97` — Controller for `/api/trips/**` endpoints.
- `src/main/java/app/detour/trip/TripService.java:1-530` — Core domain service managing Trip/Draft lifecycle, version checks, and response mapping.
- `src/main/java/app/detour/trip/TripRepository.java:1-71` — Repository interface for Trip aggregate and draft components.
- `src/main/java/app/detour/trip/JdbcTripRepository.java:1-369` — JDBC queries for trips, drafts, revalidations, and selections.
- `src/main/java/app/detour/trip/AlternativeResponse.java:8-18` — DTO definitions for `DraftSelectionResponse`, `AirfareComponentResponse`, and `AlternativeResponse`.
- `src/main/java/app/detour/trip/TripRequests.java:1-171` — Request parsers, validation, and version extraction.
- `src/test/java/app/detour/catalog/AirfareFixtureIntegrityAssertions.java:1-199` — Complete fixture integrity verification including airport timezones, connections, layovers, durations, and March 31 arrival boundaries.
- `frontend/src/api/tripsApi.ts:33-44,73-82` — Frontend TypeScript types for `AirfareComponentResponse`, `DraftSelectionResponse`, and `DraftResponse`.

## Affected Areas

| Area | Current behavior and evidence |
| --- | --- |
| **Flight Search Query** | No flight search query exists currently. Flight instance data resides in `flight_instance`, `flight_instance_segment`, `flight_schedule`, `flight_schedule_segment`, `catalog_airport`, and `catalog_supplier`. |
| **Round-Trip Combination Generation** | No combination generator exists. Outbound flights are PDX -> Trip destination on `startDate`; return flights are Trip destination -> PDX on `endDate`. Combination key format is `${outboundCatalogKey}__${returnCatalogKey}`. |
| **Capacity Filtering** | `revalidateAirfare` in `JdbcTripRepository:219` checks `available_seats >= travelerCount`, but search filtering for capacity is not yet implemented. |
| **Arrival Cutoff Enforcement** | `AirfareFixtureIntegrityAssertions:151-154` checks that segment arrivals do not exceed 2027-03-31, but search query filtering must explicitly exclude instances with final arrival after March 31, 2027. |
| **`directOnly` Filtering** | `flight_schedule.stop_count` indicates 0 (direct) or 1 (one-stop). Filtering on `stop_count = 0` for both legs is required when `directOnly = true`. |
| **Party Pricing Calculation** | Complete-party pricing is `travelerCount * (outboundPerSeat + returnPerSeat)`. `AirfareFixtureIntegrationTest:23-44` tests this math. Breakdown requires total base fare, taxes, and fees in USD integer cents. |
| **Deterministic Ranking & Sorting** | `AirfareFixtureIntegrityAssertions:156-189` demonstrates sorting by price, duration, departure, and stop count. Tie-breaker must use the immutable combination key `${outboundCatalogKey}__${returnCatalogKey}`. |
| **Draft Selection Mutation** | `detour_trip_draft_airfare_selection` table exists. `deleteDraftAirfareSelection` exists in `JdbcTripRepository:163`, but there is no save/replace method, nor any HTTP mutation endpoint. |
| **Draft Version Incrementing** | `advanceVersionForDraft` currently advances `detour_trip.version` but leaves `detour_trip_draft.version` unchanged. Selecting or removing airfare requires incrementing draft version and advancing trip version. |
| **Draft Selection Response** | `loadDraftSelections` in `JdbcTripRepository:88-89` returns zero fares and null descriptions. To return full `DraftSelectionResponse` on mutation or trip detail, draft airfare must resolve flight numbers and prices from catalog. |
| **API Endpoints** | `TripController` has no endpoints for flight search or draft airfare selection/removal. |

## Existing Tests and Fixtures

- `AirfareFixtureIntegrationTest.java`:
  - `cleanMigrationProducesCompleteDeterministicCatalog`: Confirms Flyway creates expected catalog rows.
  - `fixtureUsesOnePerTravelerFareAndMeaningfulComparisonVariation`: Confirms every traveler pays identical airfare regardless of age (parties of 1 to 8).
  - `independentCleanBuildsProduceIdenticalOrderedAirfareSnapshots`: Confirms deterministic catalog snapshots.
  - `fixtureIntegrityRejectsRepresentativeCorruptions`: Tests rejection of missing instances, zero prices, sub-8 capacities, invalid layovers, and post-March 31 arrivals.
- `AirfareFixtureIntegrityAssertions.java`:
  - Checks 24 schedules, 720 instances, 1,080 segments.
  - Verifies airport timezones (`America/Los_Angeles`, `Europe/Berlin`, `America/Mexico_City`, `America/Denver`, `America/Chicago`).
  - Verifies 2 direct and 2 one-stop options for every direction/date.
  - Verifies minimum 45-minute layovers for one-stop flights.
  - Verifies final arrival dates in destination timezone <= 2027-03-31.
  - Verifies pricing and duration variations across options.
- `TripApiIntegrationTest.java`:
  - Covers trip creation, draft creation, draft deletion, alternative duplication, promotion, version conflicts (409), and cross-user isolation (404).
  - Does not yet test airfare search or draft airfare selection.
- `TripApplicationRestartIntegrationTest.java`:
  - Verifies persistence of `detour_trip_draft_airfare_selection` across H2 application restart, promotion to planned snapshot, and selective duplication.

## Dependencies and Operational Constraints

- **Fixed Origin:** PDX (`airport-pdx`).
- **Supported Destinations:** SFO (`destination-sfo` / `airport-sfo`), MUC (`destination-muc` / `airport-muc`), MEX (`destination-mex` / `airport-mex`).
- **Supported Dates:** March 1–31, 2027; duration 1–14 nights (`detour_trip.ck_detour_trip_dates`).
- **Party Size:** 1–8 travelers (`detour_trip.ck_detour_trip_travelers`).
- **Arrival Cutoff:** Final arrival of any flight instance must not occur after 2027-03-31 in destination local time / timezone.
- **Money Representation:** Integer cents (USD), non-negative (`BIGINT`).
- **Optimistic Concurrency:** Both `expectedVersion` (Trip) and `expectedDraftVersion` (Draft) must be validated. Both Trip version and Draft version must advance on selection mutation.
- **Relational Ownership:** All operations must verify `owner_user_id` matches the authenticated user; non-matching trips or drafts must return 404 `RESOURCE_NOT_FOUND`.

## Historical Context

- `ai/thoughts/architecture/2026-09-17-p00-t02-detour-replacement-architecture.md`: Establishes deterministic Java services for planning, search, ranking, and validation. Loomspan and model-driven interpretations are removed. Relational ownership path is `User -> Trip -> Draft/Planned`.
- `ai/thoughts/phases/phase-2-catalog-and-fixtures.md`: Created normalized catalog schema and seeded airfare catalog for March 2027 with direct and one-stop flights, positive pricing, and >= 8 seat capacity.
- `ai/thoughts/phases/phase-3-trips-itineraries-and-profile.md`: Implemented Trip aggregate, multi-draft support, draft revalidation on shared-detail changes, and planned snapshots.
- `ai/thoughts/phases/phase-4-component-selection.md`: Specifies component selection requirements, deterministic search ranking, party pricing, and draft selection persistence.
- `ai/thoughts/tickets/2026-09-21-p04-t01-airfare-search-and-draft-selection.md`: Focuses exclusively on airfare search, complete-party pricing, deterministic ranking, draft selection persistence, and concurrency/authorization verification.

## Open Questions

1. **Search Endpoint Path:** Should flight search be exposed under `GET /api/trips/{tripId}/airfare` (using Trip destination, dates, and traveler count) or `GET /api/trips/{tripId}/drafts/{draftId}/airfare` (associating search directly with the draft context)?
2. **Draft Airfare Selection Mutation HTTP Method:** Should setting an airfare selection on a draft use `PUT /api/trips/{tripId}/drafts/{draftId}/airfare` or `POST /api/trips/{tripId}/drafts/{draftId}/airfare`?
3. **Draft Selection Resolution on Read:** In `JdbcTripRepository`, `loadDraftSelections` currently leaves descriptions null and fares 0. When loading trips or returning draft responses, should `loadDraftSelections` perform a join with the catalog tables (similar to `resolveAirfare`) so `DraftResponse.selections().airfare()` always contains accurate descriptions and fares?
