# P04-T03 Rental Car Search and Draft Selection Implementation Plan

## Overview
- Ticket: `ai/thoughts/tickets/2026-09-21-p04-t03-rental-car-search-and-draft-selection.md`
- Research: `ai/thoughts/research/2026-09-21-p04-t03-rental-car-search-and-draft-selection.md`
- Outcome: Authenticated users can search airport rental car inventory within their Trip dates, enforce the 25+ driver eligibility rule with an actionable explanation, calculate duration and complete pricing in consecutive 24-hour billing cycles, rank options deterministically across economy, standard, and SUV classes, and persist or remove a rental car selection on an autosaved Draft alternative with optimistic concurrency and cross-user isolation.

## Current State
- Rental catalog tables (`rental_location`, `rental_vehicle_class`, `rental_unit`, `rental_unit_occupancy`) and trigger `RentalUnitOccupancyOverlapTrigger` were created in `V4` and seeded in `V11` with deterministic locations, vehicle classes, and 21 physical units across SFO, MUC, and MEX.
- `detour_trip_draft_rental_selection` and `detour_planned_rental_snapshot` were created in `V14`.
- `JdbcTripRepository` already reads `RentalSelection` on draft load (`JdbcTripRepository:138-158`), snapshots rental selections during promotion (`JdbcTripRepository:224`), copies rental selections across draft duplication (`JdbcTripRepository:217`), deletes draft rental selections (`JdbcTripRepository:261`), and revalidates rentals on trip detail updates (`JdbcTripRepository:416-454`).
- `TripService:645-658` already demonstrates the 24-hour billing cycle calculation `(seconds + 86399) / 86400 * dailyRate`.
- Missing capabilities:
  - No `app.detour.rental` package exists; no rental search repository, service, or response models exist.
  - `TripRepository` and `JdbcTripRepository` do not expose `saveDraftRentalSelection`.
  - `TripRequests` lacks parsing and validation for `RentalSelectionRequest`.
  - `TripController` lacks search, select, and remove endpoints for rentals.
  - No integration test suite verifies car search, driver age gating, 24-hour billing math, occupancy check, ranking determinism, or selection mutations.

## Desired End State
- Authenticated users can search rental cars for a trip or draft by querying `GET /api/trips/{tripId}/drafts/{draftId}/rentals` or `GET /api/trips/{tripId}/rentals` (with singular and plural aliases `/rental`, `/cars`, `/car`).
- Interval and location validation ensures:
  - Pickup and return occur at the destination airport rental location.
  - In destination airport's local timezone (`catalog_airport.time_zone_id`), pickup and return fall within `[startDate 00:00, endDate 23:59]`.
  - Return is strictly after pickup (`pickupAt < returnAt`).
- Driver eligibility rule (25+):
  - When trip travelers are not supplied or no traveler is aged 25+, search reports `driverEligible: false`, `selectionDisabled: true`, and actionable explanation: *"Rental cars require at least one traveler aged 25 or older. Please add traveler ages in Trip Details to select a car."*
  - Vehicle options remain browsable in search.
  - Selection mutation (`PUT /api/trips/{tripId}/drafts/{draftId}/rentals`) strictly rejects saving with HTTP 400 `VALIDATION_FAILED` (field `travelerAges`).
- Half-open interval occupancy check:
  - Only physical units with no active overlapping occupancy in `rental_unit_occupancy` during `[pickupAt, returnAt)` are included in search results.
  - Abutting intervals (`return_at == pickupAt` or `pickup_at == returnAt`) are permitted.
- Duration and complete pricing:
  - Duration is calculated in consecutive 24-hour billing cycles `(seconds + 86399) / 86400`.
  - Partial final cycles round up to a full cycle.
  - Complete pricing equals `billingCycles * (daily_base_price_cents + daily_tax_cents + daily_fee_cents)` in USD integer cents.
- Deterministic search ranking:
  - Default order by vehicle class: Economy, then Standard, then SUV.
  - Within each vehicle class, order by lowest complete total price (`totalPriceCents`), then vehicle unit catalog key (`unitCatalogKey`).
- Selection persistence and concurrency:
  - `PUT /api/trips/{tripId}/drafts/{draftId}/rentals` saves the selection into `detour_trip_draft_rental_selection`, replacing prior selections.
  - `DELETE /api/trips/{tripId}/drafts/{draftId}/rentals` removes the selection.
  - Both mutations validate and increment `expectedVersion` on `detour_trip` and `expectedDraftVersion` on `detour_trip_draft`, returning 409 `VERSION_CONFLICT` on mismatch.
  - Returns updated `TripResponse` with full `RentalComponentResponse`.
- Cross-user isolation:
  - Unauthorized access to another user's trip or draft returns 404 `RESOURCE_NOT_FOUND`.

## Scope
### In scope
- Creation of `app.detour.rental` package containing:
  - `RentalSearchResponses` (records for search response, options, pricing).
  - `RentalSort` (enum supporting `DEFAULT` and `LOWEST_PRICE`).
  - `RentalSearchCandidate` (database query mapping record).
  - `RentalSearchRepository` and `JdbcRentalSearchRepository`.
  - `RentalSearchService`.
- Trip repository enhancements:
  - Add `saveDraftRentalSelection(long draftId, long rentalUnitId, OffsetDateTime pickupAt, OffsetDateTime returnAt)` to `TripRepository` and `JdbcTripRepository`.
- Trip request parsing:
  - Add `RentalSelectionRequest` to `TripRequests` with parsing and validation for `expectedVersion`, `expectedDraftVersion`, `rentalUnitId`, `pickupAt`, and `returnAt`.
- Trip service and controller integration:
  - Implement `searchRentals`, `selectDraftRental`, and `removeDraftRental` in `TripService`.
  - Add HTTP endpoints in `TripController` supporting `GET`, `PUT`, and `DELETE` on `/rentals`, `/rental`, `/cars`, `/car`.
- End-to-end integration test suite:
  - `RentalSearchAndSelectionIntegrationTest` verifying all acceptance criteria.

### Out of scope
- Airfare search, accommodation search, whole-itinerary canonical pricing, comparison UI, booking/cancellation, and Version 2 Events.
- Frontend trip-builder UI components (consumed downstream in P04-T04).

## Active Project Guardrails
None recorded in `ai/thoughts/design-lens.md`.

## Impact and Risk Analysis
- **Timezone Boundary Edge Cases:** Airport timezones (`America/Los_Angeles`, `Europe/Berlin`, `America/Mexico_City`) differ in UTC offsets. When evaluating pickup and return timestamps, the local date must be computed in the destination airport's local timezone before comparing against `[trip.startDate(), trip.endDate()]`.
- **Half-Open Interval Abutment:** Rental bookings can abut back-to-back at the exact same minute. The query condition must strictly replicate `RentalUnitOccupancyOverlapTrigger`: `occ.pickup_at < searchReturn AND searchPickup < occ.return_at`.
- **24-Hour Billing Math Rounding:** Rounding partial cycles up must ensure that 24 hours 0 seconds is 1 cycle, while 24 hours 1 second is 2 cycles. Integer arithmetic `(seconds + 86399) / 86400` handles this deterministically.
- **Cross-User Authorization:** Enforce `ownedTrip` and `ownedDraft` checks to ensure zero data leakage across users, returning 404 `RESOURCE_NOT_FOUND`.

## Implementation Approach
The implementation mirrors the proven patterns established in `AirfareSearchService` / `JdbcAirfareSearchRepository` and `StaySearchService` / `JdbcStaySearchRepository`.

1. **Domain and Data Layer (`app.detour.rental`):**
   - Query candidate physical units for a destination airport, joining `rental_unit`, `rental_vehicle_class`, `rental_location`, and `catalog_airport`.
   - Exclude units with active overlapping records in `rental_unit_occupancy`.
2. **Pricing and Evaluation Logic (`RentalSearchService`):**
   - Calculate consecutive 24-hour cycles: `billingCycles = Math.max(1, (Duration.between(pickupAt, returnAt).getSeconds() + 86399) / 86400)`.
   - Multiply by class daily base, tax, and fee cents to produce complete USD integer cents.
   - Evaluate driver eligibility: `driverEligible = travelerAges != null && travelerAges.stream().anyMatch(age -> age >= 25)`.
   - Compute `availableTripBudgetCents` (trip budget minus selected airfare and stay totals) and mark `fitsBudget`.
   - Deterministically sort by vehicle class rank (Economy=1, Standard=2, SUV=3), then total price ascending, then catalog key ascending.
3. **Draft Selection Persistence (`TripService` & `JdbcTripRepository`):**
   - In `selectDraftRental`, validate destination match, driver 25+ eligibility, interval bounds, and active unit occupancy.
   - Increment optimistic versions via `advanceVersionForDraftMutation`.
   - Persist into `detour_trip_draft_rental_selection`.
   - Return updated `TripResponse`.

---

## Phase 1: Core Domain, Model, and Repository Foundation

### Changes
- [x] `src/main/java/app/detour/rental/RentalSort.java` — Enum defining sort options: `DEFAULT`, `LOWEST_PRICE`.
- [x] `src/main/java/app/detour/rental/RentalSearchResponses.java` — Immutable response records:
  - `RentalPricingResponse(int billingCycles, long dailyBasePriceCents, long dailyTaxCents, long dailyFeeCents, long dailyTotalPriceCents, long totalBasePriceCents, long totalTaxCents, long totalFeeCents, long totalPriceCents)`
  - `RentalOptionResponse(long rentalUnitId, String unitCatalogKey, String unitIdentifier, long vehicleClassId, String vehicleClassCatalogKey, String vehicleClassName, String vehicleCategory, long locationId, String locationCatalogKey, String locationName, String airportIataCode, RentalPricingResponse pricing, Boolean fitsBudget)`
  - `RentalSearchResponse(UUID tripId, UUID draftId, String destinationKey, OffsetDateTime pickupAt, OffsetDateTime returnAt, int billingCycles, boolean driverEligible, boolean selectionDisabled, String disabledReason, String explanation, Long availableTripBudgetCents, RentalSort sort, List<RentalOptionResponse> options)`
- [x] `src/main/java/app/detour/rental/RentalCandidate.java` — Internal projection record mapping rental unit and vehicle class data.
- [x] `src/main/java/app/detour/rental/RentalSearchRepository.java` — Interface defining:
  - `List<RentalCandidate> findAvailableUnits(long destinationId, OffsetDateTime pickupAt, OffsetDateTime returnAt)`
  - `Optional<RentalCandidate> findCandidateById(long rentalUnitId, OffsetDateTime pickupAt, OffsetDateTime returnAt)`
  - `Optional<String> findDestinationAirportTimeZone(long destinationId)`
- [x] `src/main/java/app/detour/rental/JdbcRentalSearchRepository.java` — Spring `@Repository` implementing `RentalSearchRepository` using `JdbcTemplate` with SQL excluding active overlapping occupancies.
- [x] `src/main/java/app/detour/trip/TripRepository.java` — Add `void saveDraftRentalSelection(long draftId, long rentalUnitId, OffsetDateTime pickupAt, OffsetDateTime returnAt);`.
- [x] `src/main/java/app/detour/trip/JdbcTripRepository.java` — Implement `saveDraftRentalSelection`: deletes existing draft row and inserts new selection.

### Automated verification
- [x] `.\mvnw.cmd test-compile` — Verifies compilation of new repository and models.

### Optional developer checks
- None.

---

## Phase 2: Rental Search and Selection Business Logic

### Changes
- [x] `src/main/java/app/detour/rental/RentalSearchService.java` — Spring `@Service` implementing:
  - Timezone-aware interval validation against trip start and end dates.
  - Duration and 24-hour billing cycle calculation `(seconds + 86399) / 86400`.
  - 25+ driver eligibility detection and explanation text.
  - Complete price breakdown computation in integer cents.
  - Deterministic multi-attribute sorting: vehicle category order (Economy -> Standard -> SUV), lowest complete price, unit catalog key.
- [x] `src/main/java/app/detour/trip/TripRequests.java` —
  - Add record `RentalSelectionRequest(long expectedVersion, long expectedDraftVersion, long rentalUnitId, OffsetDateTime pickupAt, OffsetDateTime returnAt)`.
  - Add parser `static RentalSelectionRequest rentalSelection(JsonNode body)` with strict property validation and timestamp parsing.
- [x] `src/main/java/app/detour/trip/TripService.java` —
  - Inject `RentalSearchService` and `RentalSearchRepository`.
  - Implement `searchRentals(long ownerUserId, String tripId, String draftId, String pickupAtStr, String returnAtStr, String sortStr)`.
  - Calculate `availableTripBudgetCents` (trip budget minus selected airfare and stays).
  - Implement `selectDraftRental(long ownerUserId, String tripId, String draftId, RentalSelectionRequest request)`: validates driver 25+ age, destination match, interval, and unit availability; advances versions; saves selection; returns `TripResponse`.
  - Implement `removeDraftRental(long ownerUserId, String tripId, String draftId, DraftMutation request)`: advances versions; removes selection; returns `TripResponse`.

### Automated verification
- [x] `.\mvnw.cmd test-compile` — Verifies service wiring and parsing compilation.

### Optional developer checks
- None.

---

## Phase 3: Controller Endpoints and API Integration

### Changes
- [x] `src/main/java/app/detour/trip/TripController.java` —
  - Add `@GetMapping({"/{tripId}/drafts/{draftId}/rentals", "/{tripId}/drafts/{draftId}/rental", "/{tripId}/drafts/{draftId}/cars", "/{tripId}/drafts/{draftId}/car"})` mapping to `trips.searchRentals(...)`.
  - Add `@GetMapping({"/{tripId}/rentals", "/{tripId}/rental", "/{tripId}/cars", "/{tripId}/car"})` mapping to `trips.searchRentals(...)`.
  - Add `@PutMapping({"/{tripId}/drafts/{draftId}/rentals", "/{tripId}/drafts/{draftId}/rental", "/{tripId}/drafts/{draftId}/cars", "/{tripId}/drafts/{draftId}/car"})` mapping to `trips.selectDraftRental(...)`.
  - Add `@DeleteMapping({"/{tripId}/drafts/{draftId}/rentals", "/{tripId}/drafts/{draftId}/rental", "/{tripId}/drafts/{draftId}/cars", "/{tripId}/drafts/{draftId}/car"})` mapping to `trips.removeDraftRental(...)`.

### Automated verification
- [x] `.\mvnw.cmd test-compile` — Confirms controller routing and Spring web mapping compile cleanly.

### Optional developer checks
- None.

---

## Phase 4: Integration Verification and Edge-Case Coverage

### Changes
- [x] `src/test/java/app/detour/trip/RentalSearchAndSelectionIntegrationTest.java` — Full MockMvc integration test suite covering:
  - Search returns available cars ranked deterministically (Economy -> Standard -> SUV -> price -> catalog key).
  - Airport timezone date window validation (`[startDate, endDate]`) in local destination timezone (`America/Los_Angeles`, `Europe/Berlin`, `America/Mexico_City`).
  - Validation rejections for return <= pickup and dates outside trip interval.
  - Driver eligibility (25+): search returns `selectionDisabled=true` with required explanation string when travelers under 25 or missing; inventory remains browsable.
  - Strict rejection of car selection mutation when no traveler is 25+.
  - Half-open interval occupancy filtering: units with overlapping active occupancy in `rental_unit_occupancy` are excluded; non-overlapping/abutting units remain available.
  - 24-hour billing cycle duration and price calculation across 24h, 24h 1s, 25h, 48h, and 49h intervals.
  - Draft rental selection persistence, version advancement, and `RentalComponentResponse` shape.
  - Replacing an existing draft rental selection without duplicate rows.
  - Removing a rental selection and advancing versions.
  - Optimistic concurrency conflict (409) on stale trip or draft versions.
  - Cross-user isolation (404) for foreign trips and drafts.

### Automated verification
- [x] `.\mvnw.cmd test -Dtest=RentalSearchAndSelectionIntegrationTest` — Runs all rental search and selection integration tests.
- [x] `.\mvnw.cmd test` — Broad repository test suite verifying no regressions across airfare, stay, trip, and catalog tests.

### Optional developer checks
- None.

---

## Test Strategy
- **Test Level:** Spring Boot MockMvc integration tests with dedicated in-memory H2 database per test class (`@AutoConfigureMockMvc`, `@SpringBootTest`, dynamic datasource URL).
- **Regression Coverage:** Run existing test suites (`AirfareSearchAndSelectionIntegrationTest`, `StaySearchAndSelectionIntegrationTest`, `TripApiIntegrationTest`, `RentalUnitOccupancyConstraintIntegrationTest`, `CatalogFixtureIntegrityAssertions`).
- **Edge Cases:**
  - Leap year/timezone boundaries across March 2027 in Pacific, Central European, and Central Standard timezones.
  - Boundary timestamp abutment (`pickup_at == existing.return_at` and `return_at == existing.pickup_at`).
  - Fractional billing cycle rounding up (24 hours 1 second = 2 cycles).
  - Missing traveler ages array vs empty array vs all under 25.

---

## Acceptance-Criteria Traceability

| Acceptance criterion | Planned code evidence | Planned test evidence |
| --- | --- | --- |
| Search rental cars for trip by specifying pickup and return date/times within trip interval at destination airport | `TripController.searchDraftRentals`, `RentalSearchService.search`, `JdbcRentalSearchRepository.findAvailableUnits` | `RentalSearchAndSelectionIntegrationTest.searchRentalCarsReturnsAvailableInventoryDeterministicallyRanked` |
| Requests with return before or equal to pickup, or times outside trip date window, rejected with clear validation messages | `RentalSearchService.validateInterval`, `TripRequests.rentalSelection` | `RentalSearchAndSelectionIntegrationTest.searchRentalCarsValidatesPickupAndReturnDatesWithinTripIntervalInAirportTimezone`, `...RejectsReturnBeforeOrEqualToPickup` |
| When no traveler is 25 or older, search indicates selection disabled with required explanation; selection rejected | `RentalSearchService.search` sets `driverEligible=false`, `selectionDisabled=true`, `disabledReason`; `TripService.selectDraftRental` rejects | `RentalSearchAndSelectionIntegrationTest.searchRentalCarsEnforces25PlusDriverEligibilityWithActionableExplanation`, `...selectDraftRentalRejectsWhenDriverUnder25` |
| Units with overlapping active reservations in `rental_unit_occupancy` over `[pickupAt, returnAt)` excluded | `JdbcRentalSearchRepository` half-open interval overlap `NOT EXISTS` query | `RentalSearchAndSelectionIntegrationTest.searchRentalCarsFiltersOutActiveOverlappingOccupancyOnHalfOpenInterval` |
| Pricing charges consecutive 24-hour cycles, rounds partial final cycle up to full cycle in USD integer cents | `RentalSearchService.computePricing`: `(seconds + 86399) / 86400 * dailyRate` | `RentalSearchAndSelectionIntegrationTest.pricingCalculatesConsecutive24HourBillingCyclesAndRoundsUpPartialFinalCycle` |
| Results default-order by Economy, Standard, SUV; then lowest total price; then vehicle unit catalog key | `RentalSearchService` multi-attribute comparator | `RentalSearchAndSelectionIntegrationTest.searchRentalCarsReturnsAvailableInventoryDeterministicallyRanked` |
| Saving rental selection persists unit ID, pickup time, return time, advances versions, returns `TripResponse` | `TripService.selectDraftRental`, `JdbcTripRepository.saveDraftRentalSelection`, `advanceVersionForDraftMutation` | `RentalSearchAndSelectionIntegrationTest.selectDraftRentalPersistsSelectionAndAdvancesVersions` |
| Replacing rental selection updates draft without duplicate rows | `JdbcTripRepository.saveDraftRentalSelection` deletes prior draft row and inserts new selection | `RentalSearchAndSelectionIntegrationTest.replaceDraftRentalSelectionUpdatesDraftWithoutDuplicateRows` |
| Removing rental selection deletes draft rental record and advances draft version | `TripService.removeDraftRental`, `JdbcTripRepository.deleteDraftRentalSelection`, `advanceVersionForDraftMutation` | `RentalSearchAndSelectionIntegrationTest.removeDraftRentalSelectionDeletesSelectionAndAdvancesVersions` |
| Concurrency conflicts return 409 `VERSION_CONFLICT`; unauthorized access returns 404 with no data disclosure | `TripService.mutationConflict`, `TripService.ownedTrip`, `TripService.ownedDraft` | `RentalSearchAndSelectionIntegrationTest.draftRentalMutationsEnforceOptimisticConcurrency`, `...rentalEndpointsEnforceCrossUserIsolation` |
| Backend integration tests verify all capabilities | `RentalSearchAndSelectionIntegrationTest` | Executable suite passing with zero failures |

---

## Risks and Rollback/Recovery
- **Low Regression Risk:** Rental search and draft selection introduces new endpoints and service classes without modifying existing airfare or stay search business logic.
- **Rollback:** In the event of an unexpected issue, changes are self-contained in new files (`app.detour.rental.*`) and additions to `TripController`, `TripService`, and `JdbcTripRepository`. Git revert can safely roll back without database migrations.

---

## References
- Ticket: `ai/thoughts/tickets/2026-09-21-p04-t03-rental-car-search-and-draft-selection.md`
- Research: `ai/thoughts/research/2026-09-21-p04-t03-rental-car-search-and-draft-selection.md`
- Schema & Fixtures: `src/main/resources/db/migration/V4__create_catalog_inventory_schema.sql`, `V11__seed_march_2027_stay_and_rental_catalog.sql`, `V14__create_draft_selection_and_planned_snapshot_schema.sql`
- Overlap Trigger: `src/main/java/app/detour/catalog/persistence/RentalUnitOccupancyOverlapTrigger.java`
- Reference Implementations: `app/detour/stay/StaySearchService.java`, `app/detour/stay/JdbcStaySearchRepository.java`, `src/test/java/app/detour/trip/StaySearchAndSelectionIntegrationTest.java`
