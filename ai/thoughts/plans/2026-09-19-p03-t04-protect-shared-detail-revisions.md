# P03-T04 Protected Shared-Detail Revisions and Selective Trip Duplication Implementation Plan

## Overview
- Ticket: `ai/thoughts/tickets/2026-09-19-p03-t04-protect-shared-detail-revisions.md`
- Research: `ai/thoughts/research/2026-09-19-p03-t04-protect-shared-detail-revisions.md`
- Outcome: Changing destination, dates, or travelers never silently corrupts an alternative: mutable Draft selections are revalidated with an explicit structured change summary, while Trips containing Planned snapshots are protected from in-place shared-detail mutation and instead revised through a new Trip built strictly from user-selected Planned snapshot sources.

## Current State
The repository supports owner-scoped Trip aggregates (`detour_trip`, `detour_trip_traveler`), mutable Draft alternatives (`detour_trip_draft`) with foreign key component references (`detour_trip_draft_airfare_selection`, `detour_trip_draft_stay_selection`, `detour_trip_draft_rental_selection`), and immutable Planned snapshots (`detour_planned_itinerary`, `detour_planned_*_snapshot`).

Currently:
- `TripService.replaceSharedDetails` (`TripService.java:60-75`) allows in-place updates to destination, dates, travelers, and budget on any Trip regardless of whether Planned alternatives exist.
- Existing Draft selections are completely ignored during shared-detail updates: invalid catalog references (e.g. flights or stays belonging to a different destination or date range, or exceeding available capacity) remain persisted in Draft selection tables.
- No revalidation occurs for seat capacity, accommodation room counts, or driver age eligibility, and no revision summary is returned.
- No Trip-level revision or selective duplication endpoint exists to produce a revised Trip from Planned snapshot sources.

## Desired End State
1. **Pre-Planned In-Place Shared-Detail Revisions (`PUT /api/trips/{tripId}`)**:
   - Permitted only while `trip.planned().isEmpty()`.
   - When traveler count or ages change: reprice each component from authoritative Phase 2 catalog tables; revalidate Draft selections for available seat capacity, stay room count and capacity, and driver age eligibility (requiring at least one traveler aged 25+ for rental cars). Retain valid selections (updating stay unit counts when party size requires more rooms); remove invalid selections.
   - When destination or dates change: remove all incompatible Draft selections (out-of-bounds dates, wrong destination airport, missing inventory). Never retain a catalog reference merely because its ID exists.
   - Return an immediate structured `revisionSummary` containing `removals` and `adjustments` with user-readable explanations.
2. **Protection of Planned Trips**:
   - If `!trip.planned().isEmpty()`, in-place destination, date, or traveler updates are rejected with `409 IMMUTABLE_TRIP`.
   - Budget-only updates (destination, dates, and travelers unchanged) are permitted in place, updating `budget_cents` without mutating selections or Planned snapshots.
3. **Active-Trip Selective Duplication (`POST /api/trips/{tripId}/duplicate`)**:
   - Requires a nonempty, distinct list of `sourcePlannedItineraryIds`.
   - Validates that every source ID belongs to the user's source Trip as a Planned alternative. Foreign, cross-trip, unknown, or Draft IDs fail atomically with `404 RESOURCE_NOT_FOUND` without leaking protected data.
   - Enforces optimistic concurrency on the source Trip (`expectedVersion`).
   - Creates a new Trip aggregate (version 0) with the revised shared details.
   - Converts each selected Planned snapshot into exactly one Draft in the new Trip (never copying Drafts).
   - Revalidates each copied component against the new shared details: retains compatible content (updating room counts and prices), removes incompatible content, and returns `201 Created` with the new `TripResponse` and post-duplication `revisionSummary`.
   - The source Trip and all its Planned snapshots remain completely unchanged.

## Scope
### In scope
- Response and request contracts for structured revision summaries and Trip selective duplication.
- Authoritative Phase 2 catalog validation and repricing queries for airfare, stays, and rentals.
- In-place shared-detail revalidation and component pruning for Trips without Planned alternatives.
- Rejection of in-place shared-detail mutations on Trips with Planned alternatives (`409 IMMUTABLE_TRIP`).
- Support for budget-only in-place updates on Trips with Planned alternatives.
- Transactional active-Trip selective duplication from Planned snapshot sources to Draft alternatives in a new Trip aggregate.
- Atomicity, optimistic concurrency, and two-user authorization isolation.

### Out of scope
- Canceled-Trip duplication fallback and canceled status (owned by Phase 6).
- Booking history and Booked/Canceled Booking records (owned by Phase 6).
- Component search and ranking UI (owned by Phase 4).
- Canonical itinerary and grand-total budget rollups (owned by Phase 5).
- Modifying immutable Planned snapshots.

## Active Project Guardrails
None recorded in `ai/thoughts/design-lens.md`.

## Impact and Risk Analysis
- **Alternative Protection Risk**: In-place edits could corrupt immutable Planned itineraries. *Mitigation*: Strictly check `trip.planned().isEmpty()` before allowing destination/date/traveler mutations; reject with `409 IMMUTABLE_TRIP`.
- **Dangling Draft References**: Changing destination or dates could leave Draft selections referencing catalog rows from another city or date range. *Mitigation*: Execute authoritative catalog joins; delete rows from `detour_trip_draft_*_selection` when compatibility fails.
- **Capacity and Pricing Staleness**: Changing traveler counts could leave accommodation room counts insufficient or airfare selections with insufficient seats. *Mitigation*: Recompute required rooms `ceil(travelers / guest_capacity)` for hotel/B&B rooms and verify `available_inventory`; check `available_seats >= travelerCount` for flight instances; reprice totals.
- **Authorization & Disclosure**: Invalid or foreign source Planned IDs could leak alternative existence. *Mitigation*: Return uniform `404 RESOURCE_NOT_FOUND` for any ID not found on the authenticated user's source Trip.
- **Zero vs Absent Budget**: Preserving distinct `null` vs `0` budget cents across updates and duplication. *Mitigation*: Re-use strict `JsonNode budgetCents` validation from P03-T01/P03-T02.

## Implementation Approach
The implementation is organized into four focused components:

1. **API Contracts (`TripRequests.java`, `TripResponse.java`)**:
   - Add `TripRequests.TripRevision` record to parse duplication requests: `expectedVersion`, `destinationKey`, `startDate`, `endDate`, `travelerCount`, `travelerAges`, `budgetCents`, `sourcePlannedItineraryIds`.
   - Add `RevisionSummaryResponse(List<ComponentRemovalResponse> removals, List<ComponentAdjustmentResponse> adjustments)` to `app.detour.trip`.
   - Add `ComponentRemovalResponse(UUID draftId, String component, String reason)` and `ComponentAdjustmentResponse(UUID draftId, String component, String changeType, Integer previousUnitCount, Integer newUnitCount, Long previousPriceCents, Long newPriceCents, String reason)`.
   - Add `revisionSummary` field to `TripResponse`, preserving the existing 14-parameter constructor for backward compatibility. When `revisionSummary` is null (e.g. GET `/api/trips/{id}`), it is omitted from JSON.

2. **Catalog Revalidation Engine (`JdbcTripRepository.java`, `TripRepository.java`)**:
   - Implement `revalidateAirfare`: checks route airports (PDX <-> destination), service dates, and `available_seats >= travelerCount`. Computes party price change.
   - Implement `revalidateStay`: checks destination, verifies that nightly inventory covers all nights `startDate` to `endDate`, calculates required room count for rooms `ceil(travelerCount / guest_capacity)` or verifies `travelerCount <= guest_capacity` for whole properties, verifies `available_inventory >= unit_count` across all nights, and computes price change.
   - Implement `revalidateRental`: checks destination airport location, verifies `pickupAt` and `returnAt` fall within `startDate` and `endDate`, verifies age eligibility (`travelerAges != null && travelerAges.anyMatch(age >= 25)`), and computes duration billing cycle price.
   - Implement selection update and deletion methods: `deleteDraftAirfareSelection`, `deleteDraftStaySelection`, `deleteDraftRentalSelection`, and `updateDraftStayUnitCount`.

3. **In-Place Shared-Detail Revalidation & Protection (`TripService.java`)**:
   - In `replaceSharedDetails`:
     - If `!trip.planned().isEmpty()`:
       - Compare destination, dates, traveler count, and ages against existing trip facts.
       - If any travel detail changed: throw `ApiException(409, "IMMUTABLE_TRIP", "Trips with Planned alternatives cannot change destination, dates, or travelers in place. Create a revised trip instead.")`.
       - If only `budgetCents` changed (budget-only update): advance version, update budget in `detour_trip`, return `TripResponse` without revision summary.
     - If `trip.planned().isEmpty()`:
       - Advance version, update `detour_trip` and `detour_trip_traveler`.
       - For each Draft in the trip: revalidate airfare, stay, rental selections against new shared details.
       - Apply deletions and stay unit count updates in DB.
       - Build `RevisionSummaryResponse` with all removals and adjustments.
       - Return `TripResponse` with `revisionSummary`.

4. **Active-Trip Selective Duplication (`TripController.java`, `TripService.java`, `JdbcTripRepository.java`)**:
   - Map `@PostMapping({ "/{tripId}/duplicate", "/{tripId}/revisions" })` in `TripController`.
   - In `TripService.duplicateTrip`:
     - Load and verify source Trip ownership.
     - Check `expectedVersion == trip.version()`; throw 409 `VERSION_CONFLICT` if mismatched.
     - Validate `sourcePlannedItineraryIds`: non-null, non-empty, distinct UUIDs.
     - Validate that each source ID exists in `trip.planned()`. If any ID is missing or not a Planned alternative on this Trip, throw `404 RESOURCE_NOT_FOUND`.
     - Validate destination, dates, traveler count, ages, and budget.
     - Create new Trip aggregate (version 0).
     - For each selected Planned source:
       - Create a new Draft in the new Trip (`version = 0`).
       - Revalidate its snapshot selections against the new shared details.
       - Insert retained selections into the draft selection tables (with updated stay unit counts).
       - Aggregate all removals and adjustments into `RevisionSummaryResponse`.
     - Return `201 Created` with the new `TripResponse` containing `revisionSummary`.

---

## Phase 1: Request & Response Contracts

### Changes
- [x] `src/main/java/app/detour/trip/AlternativeResponse.java` — Add `RevisionSummaryResponse`, `ComponentRemovalResponse`, `ComponentAdjustmentResponse` records.
- [x] `src/main/java/app/detour/trip/TripResponse.java` — Add `RevisionSummaryResponse revisionSummary` field and preserve overloaded constructor.
- [x] `src/main/java/app/detour/trip/TripRequests.java` — Add `TripRevision` record and `revision(JsonNode body)` parser with strict validation for `expectedVersion`, `destinationKey`, `startDate`, `endDate`, `travelerCount`, `travelerAges`, `budgetCents`, `sourcePlannedItineraryIds`.
- [x] `src/test/java/app/detour/trip/TripApiIntegrationTest.java` — Ensure all existing tests compile and pass with the updated records.

### Automated verification
- [x] `.\mvnw.cmd test-compile` — Clean compilation of all modified response and request records.

### Optional developer checks
- [ ] None.

---

## Phase 2: Authoritative Catalog Revalidation Engine

### Changes
- [x] `src/main/java/app/detour/trip/TripRepository.java` — Declare revalidation methods, selection update/deletion methods, and batch draft creation helper.
- [x] `src/main/java/app/detour/trip/JdbcTripRepository.java` — Implement catalog queries for:
  - `revalidateAirfare(long destinationId, LocalDate startDate, LocalDate endDate, int travelerCount, AirfareSelection selection)`
  - `revalidateStay(long destinationId, LocalDate startDate, LocalDate endDate, int travelerCount, StaySelection selection)`
  - `revalidateRental(long destinationId, LocalDate startDate, LocalDate endDate, List<Integer> ages, RentalSelection selection)`
  - Database mutations for removing invalid selections and updating stay unit counts on drafts.
  - `createAggregateWithDrafts`: transactional creation of new Trip aggregate with multiple Drafts and initial selections.
- [x] `src/test/java/app/detour/trip/TripApiIntegrationTest.java` — Unit/component assertions on revalidation helper methods.

### Automated verification
- [x] `.\mvnw.cmd test-compile` — Clean compilation of repository queries and revalidation methods.

### Optional developer checks
- [ ] None.

---

## Phase 3: In-Place Shared-Detail Revalidation & Protection

### Changes
- [x] `src/main/java/app/detour/trip/TripService.java` — Update `replaceSharedDetails`:
  - Enforce `409 IMMUTABLE_TRIP` when `!trip.planned().isEmpty()` and travel details (destination, dates, travelers) change.
  - Support in-place budget-only updates when `!trip.planned().isEmpty()`.
  - Revalidate all Draft selections when `trip.planned().isEmpty()`, removing invalid components and adjusting room counts.
  - Return `TripResponse` with structured `RevisionSummaryResponse`.
- [x] `src/test/java/app/detour/trip/TripApiIntegrationTest.java` — Add tests verifying:
  - In-place traveler increase updates room count and prices in Drafts.
  - In-place traveler increase exceeding flight seat capacity removes airfare selection with explanation.
  - In-place destination or date change removes incompatible components with explanation.
  - In-place travel detail edit on Trip with Planned alternatives returns 409 `IMMUTABLE_TRIP`.
  - Budget-only update on Trip with Planned alternatives succeeds in place.

### Automated verification
- [x] `.\mvnw.cmd test -Dtest=TripApiIntegrationTest` — Passes with new in-place revalidation and protection assertions.

### Optional developer checks
- [ ] None.

---

## Phase 4: Active-Trip Selective Duplication

### Changes
- [x] `src/main/java/app/detour/trip/TripController.java` — Add `@PostMapping({ "/{tripId}/duplicate", "/{tripId}/revisions" })`.
- [x] `src/main/java/app/detour/trip/TripService.java` — Implement `duplicateTrip`:
  - Validate source Trip ownership, version concurrency, and source Planned IDs.
  - Reject empty, duplicate, or foreign/Draft source lists.
  - Create new Trip aggregate and convert selected Planned sources to Drafts.
  - Revalidate copied components, remove incompatible items, and generate post-duplication summary.
  - Preserve source Trip and all source Planned snapshots unchanged.
- [x] `src/test/java/app/detour/trip/TripApiIntegrationTest.java` — Add tests verifying:
  - Selective duplication creates new Trip with one Draft per selected Planned snapshot.
  - Incompatible components in copied snapshots are pruned with clear explanations.
  - Source Trip and Planned snapshots remain unchanged.
  - Validation failures (empty source list, duplicate source list) return 400.
  - Unauthorized or unknown source IDs return 404.
  - Stale `expectedVersion` returns 409 `VERSION_CONFLICT`.
  - Zero budget vs absent budget are distinctly preserved.
- [x] `src/test/java/app/detour/trip/TripApplicationRestartIntegrationTest.java` — Verify duplicated Trip and revised Draft persistence across application restart.

### Automated verification
- [x] `.\mvnw.cmd test` — Full repository test suite passes cleanly.

### Optional developer checks
- [ ] None.

---

## Test Strategy
- **Unit/Integration Boundary**: WebMvc integration tests against real H2 schema and authoritative Phase 2 catalog fixtures (`TripApiIntegrationTest`).
- **Restart Persistence**: End-to-end restart verification in `TripApplicationRestartIntegrationTest` to ensure that duplicated Trips, converted Drafts, and revalidated selections persist across server restart without reloading live catalog data.
- **Negative & Security Cases**: Two-user isolation (verifying foreign source IDs return 404), optimistic concurrency races (concurrent mutations return 409), input boundary checks (empty source lists, duplicates, invalid ages, date ranges).

## Acceptance-Criteria Traceability
| Acceptance criterion | Planned code evidence | Planned test evidence |
| --- | --- | --- |
| Pre-Planned traveler edits retain still-valid Draft selections, remove invalid ones, and report every resulting price, room-count, eligibility, capacity, and selection change with specific reasons. | `TripService.replaceSharedDetails`, `revalidateSelections`, `JdbcTripRepository.revalidate*` | `TripApiIntegrationTest.inPlaceTravelerEditRevalidatesCapacityRoomCountAndEligibility` |
| Pre-Planned destination/date edits remove incompatible components and report each removal; no incompatible reference or stale displayed total remains. | `TripService.replaceSharedDetails`, `revalidateSelections` removing date/destination mismatches | `TripApiIntegrationTest.inPlaceDestinationAndDateEditRemovesIncompatibleComponents` |
| Once any Planned snapshot exists, an in-place destination/date/traveler edit is rejected and the revision workflow creates a separate owned Trip from exactly the selected Planned sources. | `TripService.replaceSharedDetails` checking `trip.planned().isEmpty()` and throwing `IMMUTABLE_TRIP`; `TripService.duplicateTrip` | `TripApiIntegrationTest.rejectsInPlaceTravelDetailEditsWhenPlannedAlternativesExist` |
| Active-Trip selective duplication requires at least one source and produces one Draft per selected Planned snapshot while preserving all source Trips and snapshots unchanged. | `TripService.duplicateTrip`, `TripRequests.revision`, `JdbcTripRepository.createAggregateWithDrafts` | `TripApiIntegrationTest.selectivelyDuplicatesActiveTripFromPlannedSourcesIntoNewDrafts` |
| Invalid or unauthorized source lists fail atomically without revealing protected alternatives or leaving a partial new Trip. | `TripService.duplicateTrip` verifying sources against `trip.planned()` and throwing uniform 404; transactional boundary | `TripApiIntegrationTest.selectiveDuplicationRejectsInvalidOrUnauthorizedSourcesWithoutDisclosure` |
| Budget-only updates change Draft budget presentation without mutating Planned selections, and optimistic conflicts cannot silently overwrite either source or new aggregate state. | `TripService.replaceSharedDetails` allowing budget-only updates; `advanceVersion` concurrency check | `TripApiIntegrationTest.budgetOnlyUpdatePreservesPlannedSnapshotsAndEnforcesConcurrency` |
| Deterministic tests cover supported destination/date/traveler revisions, partial component compatibility, zero versus absent budget, concurrent requests, restart persistence, and two-user isolation. | Multiple targeted integration test methods in `TripApiIntegrationTest` and `TripApplicationRestartIntegrationTest` | Full suite run via `.\mvnw.cmd test` |
| Draft, Booked, and Canceled Booking records are not copied by the Trip-level operation, and Phase 6 history/cancellation behavior is not implemented here. | `TripService.duplicateTrip` restricted strictly to `trip.planned()` sources | `TripApiIntegrationTest.tripDuplicationDoesNotCopyDraftAlternatives` |

## Risks and Rollback/Recovery
- Schema changes: None required. Schema V12, V13, and V14 already support owned Trips, Draft selections, and Planned snapshots. All revalidation, pruning, and selective duplication operate on existing tables.
- Rollback: Reverting `TripService.java`, `TripController.java`, `TripRequests.java`, and `JdbcTripRepository.java` restores previous behavior cleanly without migration rollback.

## References
- Ticket: `ai/thoughts/tickets/2026-09-19-p03-t04-protect-shared-detail-revisions.md`
- Research: `ai/thoughts/research/2026-09-19-p03-t04-protect-shared-detail-revisions.md`
- Roadmap: `ai/thoughts/phases/phase-3-trips-itineraries-and-profile.md`
- Source files: `TripService.java`, `TripController.java`, `TripRequests.java`, `TripResponse.java`, `JdbcTripRepository.java`
