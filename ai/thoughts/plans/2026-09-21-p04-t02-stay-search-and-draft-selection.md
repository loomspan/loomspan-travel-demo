# Stay Search and Draft Selection Implementation Plan

## Overview
- Ticket: `ai/thoughts/tickets/2026-09-21-p04-t02-stay-search-and-draft-selection.md`
- Research: `ai/thoughts/research/2026-09-21-p04-t02-stay-search-and-draft-selection.md`
- Outcome: Authenticated users can search accommodations for their Trip destination and dates filtered by mandatory accommodation type preference (`HOTEL`, `BED_AND_BREAKFAST`, or `VACATION_RENTAL`), receive automatic room calculations and complete stay pricing in USD integer cents with transparent nightly and per-room breakdowns, view results deterministically ranked by available-budget fit and guest rating, and persist, replace, or remove a stay selection on an autosaved Draft with optimistic concurrency and cross-user isolation.

## Current State
- The relational database schema (`V4`, `V5`, `V11`, `V14`) defines `accommodation_property` (with location description, guest rating, distance to city center, coordinates), `accommodation_unit` (with guest and inventory capacity, room vs whole property kind), and `accommodation_nightly_inventory` (with nightly base, tax, fee cents and availability for March 1–30, 2027).
- `detour_trip_draft_stay_selection` (`V14`) defines draft stay selections keyed by `draft_id` with `accommodation_unit_id` and `unit_count`.
- `JdbcTripRepository` currently provides `deleteDraftStaySelection` and `updateDraftStayUnitCount`, but lacks `saveDraftStaySelection`. Furthermore, `loadDraftSelections` queries only `accommodation_unit_id` and `unit_count` while leaving `propertyName` and `unitName` null and `nights` empty.
- While `TripService` provides airfare search and draft selection mutations, no stay search service, repository, or controller endpoints exist yet.

## Desired End State
- Authenticated users can invoke `GET /api/trips/{tripId}/drafts/{draftId}/stays` (or `GET /api/trips/{tripId}/stays`) with a mandatory `type` query parameter (`HOTEL`, `BED_AND_BREAKFAST`, `VACATION_RENTAL`) and optional `sort` parameter (`DEFAULT`, `LOWEST_PRICE`, `HIGHEST_RATING`, `NEAREST_CITY_CENTER`).
- Room count is automatically calculated: `ceil(travelerCount / guestCapacity)` for rooms, and `1` for vacation rentals. Units with insufficient inventory on any night in `[startDate, endDate)` or vacation rentals with capacity smaller than `travelerCount` are excluded.
- Complete stay pricing is computed in USD integer cents across all required rooms and nights, with transparent per-room and nightly breakdowns.
- Available trip budget is computed as `trip.budgetCents - selectedAirfareTotal - selectedCarTotal`, excluding any existing draft stay selection. Stays within budget are placed in Tier 1 ahead of over-budget Tier 2 stays in default ranking; if budget is null, tiering is omitted.
- Authenticated users can persist or replace a stay selection on their Draft via `PUT /api/trips/{tripId}/drafts/{draftId}/stay` and remove it via `DELETE /api/trips/{tripId}/drafts/{draftId}/stay`. Concurrency is protected by `expectedVersion` and `expectedDraftVersion`, and cross-user access is isolated with 404 responses.
- `TripResponse` returns fully populated stay selection data (`accommodationUnitId`, `unitCount`, `propertyName`, `unitName`, itemized `nights`).

## Scope
### In scope
- Public API stay search endpoint (`GET /api/trips/{tripId}/drafts/{draftId}/stays` and `GET /api/trips/{tripId}/stays`, supporting both `/stays` and `/stay`).
- Mandatory accommodation type validation (`HOTEL`, `BED_AND_BREAKFAST`, `VACATION_RENTAL`).
- Dynamic party room count calculation and vacation rental capacity filtering.
- Multi-night inventory validation across half-open range `[startDate, endDate)`.
- Complete-stay pricing calculation in USD integer cents with itemized nightly and per-room breakdowns.
- Available trip budget calculation with existing stay replacement exclusion.
- Deterministic search ranking (budget-fit tiering and rating/price/distance/catalog key ordering; sort overrides).
- Draft stay selection mutation (`PUT`) and removal (`DELETE`) endpoints.
- Optimistic locking on Trip (`expectedVersion`) and Draft (`expectedDraftVersion`).
- Full resolution of stay selection data on Draft and Trip responses.
- Comprehensive integration tests validating search, pricing, ranking, selection persistence, concurrency conflicts, and authorization.

### Out of scope
- Airfare search and selection (already delivered in P04-T01).
- Rental car search and selection (deferred to P04-T03).
- Whole-itinerary canonical pricing, planning promotion changes, comparison UI, and booking.
- Version 2 Events.

## Active Project Guardrails
None recorded (from `ai/thoughts/design-lens.md`).

## Impact and Risk Analysis
- **Off-by-One Night in Date Range:** Trip dates represent check-in on `startDate` and check-out on `endDate`. Inventory queries and nightly pricing must cover the half-open interval `[startDate, endDate)`. Exactly `ChronoUnit.DAYS.between(startDate, endDate)` nights must have sufficient inventory.
- **Room Count Division Integrity:** For hotels and B&Bs, integer division `travelerCount / guestCapacity` would truncate without floating-point ceiling math. Calculation must use `(int) Math.ceil((double) travelerCount / guestCapacity)`.
- **Budget fit with Existing Selections:** When searching stays to replace an existing stay on a draft, the existing stay cost must NOT be subtracted from the available budget, while existing airfare and car rental selections must be subtracted.
- **Draft Selection Representation:** When returning `TripResponse` after mutation or trip fetch, `StaySelection` must include property name, unit name, and nightly breakdown matching the trip dates.
- **Optimistic Concurrency & Cross-User Security:** Mismatched versions must fail atomically with 409 `VERSION_CONFLICT` without modifying the database. Unauthorized requests must return 404 `RESOURCE_NOT_FOUND` without leaking whether the foreign trip exists.

## Implementation Approach
- Introduce package `app.detour.stay` containing:
  - `AccommodationType`: enum for `HOTEL`, `BED_AND_BREAKFAST`, `VACATION_RENTAL` with 400 validation error mapping.
  - `StaySort`: enum for `DEFAULT`, `LOWEST_PRICE`, `HIGHEST_RATING`, `NEAREST_CITY_CENTER` with 400 validation error mapping.
  - `StaySearchResponses`: record DTOs for stay search results, pricing breakdowns, and option details.
  - `StaySearchRepository` and `JdbcStaySearchRepository`: queries `accommodation_property`, `accommodation_unit`, and `accommodation_nightly_inventory`.
  - `StaySearchService`: handles room count calculations, multi-night inventory validation, complete pricing calculations, budget computation, and deterministic sorting comparators.
- Extend `app.detour.trip`:
  - In `TripRequests`: add `StaySelectionRequest` and static parsing helper `staySelection(JsonNode)`.
  - In `TripRepository` and `JdbcTripRepository`: add `saveDraftStaySelection(long draftId, long accommodationUnitId, int unitCount)`, and update `loadDraftSelections` to join property and unit details and load nightly inventory for `[startDate, endDate)`.
  - In `TripService`: add `searchStays`, `selectDraftStay`, and `removeDraftStay`.
  - In `TripController`: add `@GetMapping`, `@PutMapping`, and `@DeleteMapping` endpoints for stay search, draft selection, and draft removal.

---

## Phase 1: Stay Domain Model, DTOs, Enums, and Repository Query

### Changes
- [x] `src/main/java/app/detour/stay/AccommodationType.java`
  - Enum with constants: `HOTEL`, `BED_AND_BREAKFAST`, `VACATION_RENTAL`.
  - Static factory `from(String value)`: throws `ApiException(400, "VALIDATION_FAILED", "One or more fields are invalid.", Map.of("type", "Choose a supported accommodation type."))` if value is null, blank, or unrecognized.
- [x] `src/main/java/app/detour/stay/StaySort.java`
  - Enum with constants: `DEFAULT`, `LOWEST_PRICE`, `HIGHEST_RATING`, `NEAREST_CITY_CENTER`.
  - Static factory `from(String value)`: defaults to `DEFAULT` if null or blank; throws `ApiException(400, "VALIDATION_FAILED", "One or more fields are invalid.", Map.of("sort", "Choose a supported sort option."))` if unrecognized.
- [x] `src/main/java/app/detour/stay/StaySearchResponses.java`
  - `StayNightPricingResponse`: records `date`, `basePriceCents`, `taxCents`, `feeCents`, `totalCents`, `availableInventory`.
  - `StayPricingResponse`: records `requiredRooms`, `nightCount`, `perRoomBasePriceCents`, `perRoomTaxCents`, `perRoomFeeCents`, `perRoomTotalPriceCents`, `totalBasePriceCents`, `totalTaxCents`, `totalFeeCents`, `totalPriceCents`, `List<StayNightPricingResponse> nights`.
  - `StayOptionResponse`: records `accommodationUnitId`, `propertyId`, `propertyCatalogKey`, `unitCatalogKey`, `propertyName`, `unitName`, `propertyCategory`, `unitKind`, `locationDescription`, `guestRating`, `distanceToCityCenterMeters`, `latitude`, `longitude`, `guestCapacity`, `inventoryCapacity`, `pricing`, `fitsBudget`.
  - `StaySearchResponse`: records `tripId`, `draftId`, `destinationKey`, `accommodationType`, `startDate`, `endDate`, `travelerCount`, `availableTripBudgetCents`, `sort`, `List<StayOptionResponse> options`.
- [x] `src/main/java/app/detour/stay/StaySearchRepository.java` & `src/main/java/app/detour/stay/JdbcStaySearchRepository.java`
  - Interface and Spring `@Repository` implementation querying `accommodation_property`, `accommodation_unit`, and `accommodation_nightly_inventory`.
  - Method `findCandidates(long destinationId, AccommodationType type, LocalDate startDate, LocalDate endDate)`: returns candidate properties and units with their nightly inventory in `[startDate, endDate)`.
  - Method `findCandidateById(long accommodationUnitId, LocalDate startDate, LocalDate endDate)`: returns single candidate for validation during draft stay selection.
  - Method `findRentalDailyTotal(long rentalUnitId)`: returns daily base + tax + fee cents for draft car rental when calculating available budget.

### Automated verification
- [x] `.\mvnw.cmd test-compile` — compile successfully with all new symbols and queries.

---

## Phase 2: Stay Pricing, Room Calculation, and Deterministic Ranking Service

### Changes
- [x] `src/main/java/app/detour/stay/StaySearchService.java`
  - Spring `@Service` bean injecting `StaySearchRepository`.
  - Method `search(...)`:
    1. Room count calculation:
       - If `type == VACATION_RENTAL`: `requiredRooms = 1`. If `candidate.guestCapacity() < travelerCount`, filter out candidate.
       - If `type == HOTEL` or `BED_AND_BREAKFAST`: `requiredRooms = (int) Math.ceil((double) travelerCount / candidate.guestCapacity())`.
    2. Multi-night inventory check:
       - Verify candidate has exactly `ChronoUnit.DAYS.between(startDate, endDate)` nights in `[startDate, endDate)`.
       - Filter out any candidate where `available_inventory < requiredRooms` for any night.
    3. Complete stay pricing calculation:
       - Sum nightly base, tax, fee cents per room across `[startDate, endDate)`.
       - Multiply by `requiredRooms` to compute complete-stay totals.
    4. Available budget evaluation:
       - Calculate `availableBudget = budgetCents - selectedAirfareTotal - selectedCarTotal` (when `budgetCents != null`).
       - Compute `fitsBudget = (availableBudget != null) ? (totalPriceCents <= availableBudget) : null`.
    5. Deterministic sorting:
       - `DEFAULT`:
         - If `availableBudget != null`: Tier 1 (`fitsBudget == true`) before Tier 2 (`fitsBudget == false`).
         - Within tier (or if `availableBudget == null`): highest `guestRating` (descending), lowest `totalPriceCents` (ascending), nearest `distanceToCityCenterMeters` (ascending), and `propertyCatalogKey` (ascending).
       - `LOWEST_PRICE`: `totalPriceCents` ascending, `propertyCatalogKey` ascending.
       - `HIGHEST_RATING`: `guestRating` descending, `propertyCatalogKey` ascending.
       - `NEAREST_CITY_CENTER`: `distanceToCityCenterMeters` ascending, `propertyCatalogKey` ascending.
    6. Assemble and return `StaySearchResponse`.

### Automated verification
- [x] `.\mvnw.cmd test-compile` — verifies compilation of service logic and sorting comparators.

---

## Phase 3: Draft Selection Persistence, Resolution, and Version Concurrency

### Changes
- [x] `src/main/java/app/detour/trip/TripRequests.java`
  - Add record `StaySelectionRequest(long expectedVersion, long expectedDraftVersion, long accommodationUnitId, Integer unitCount)`.
  - Add parser `staySelection(JsonNode body)`: requires `expectedVersion`, `expectedDraftVersion`, `accommodationUnitId`; allows optional `unitCount`.
- [x] `src/main/java/app/detour/trip/TripRepository.java` & `src/main/java/app/detour/trip/JdbcTripRepository.java`
  - Add method `saveDraftStaySelection(long draftId, long accommodationUnitId, int unitCount)`.
  - Implement `saveDraftStaySelection`: executes `DELETE FROM detour_trip_draft_stay_selection WHERE draft_id = ?`, followed by `INSERT INTO detour_trip_draft_stay_selection (draft_id, accommodation_unit_id, unit_count) VALUES (?, ?, ?)`.
  - Update `loadDraftSelections(long draftId, LocalDate startDate, LocalDate endDate)`:
    - Query `detour_trip_draft_stay_selection` joining `accommodation_unit` and `accommodation_property` to populate `propertyName` and `unitName`.
    - Query `accommodation_nightly_inventory` between `startDate` and `endDate` to populate `StayNight` list with itemized `basePriceCents`, `taxCents`, and `feeCents`.
    - Join `rental_vehicle_class` and `rental_location` for rental draft selections so prices are populated.
- [x] `src/main/java/app/detour/trip/TripService.java`
  - Inject `StaySearchService` and `StaySearchRepository`.
  - Add method `searchStays(long ownerUserId, String tripId, String draftId, String typeStr, String sortStr)`:
    - Validate ownership with `ownedTrip` and optional `ownedDraft`.
    - Validate `type` via `AccommodationType.from(typeStr)`.
    - Parse `sort` via `StaySort.from(sortStr)`.
    - Compute `selectedAirfareTotal` from `draft.selections().airfare()` (if present).
    - Compute `selectedCarTotal` from `draft.selections().rental()` (if present).
    - Delegate to `staySearchService.search(...)`.
  - Add `@Transactional public TripResponse selectDraftStay(long ownerUserId, String tripId, String draftId, TripRequests.StaySelectionRequest request)`:
    - Validate request body not null.
    - Check ownership with `ownedTrip` and `ownedDraft`.
    - Validate accommodation unit exists and belongs to the trip destination.
    - Validate capacity and compute required rooms (`ceil(travelers / capacity)` for rooms, `1` for whole property). Exclude if vacation rental capacity < traveler count.
    - Validate client-provided `unitCount` (if present, must match `requiredRooms`).
    - Validate sufficient available inventory on all nights in `[startDate, endDate)`.
    - Perform optimistic concurrency check with `trips.advanceVersionForDraftMutation(...)`.
    - Persist selection via `trips.saveDraftStaySelection(...)`.
    - Return updated `TripResponse`.
  - Add `@Transactional public TripResponse removeDraftStay(long ownerUserId, String tripId, String draftId, TripRequests.DraftMutation request)`:
    - Validate request body not null.
    - Check ownership with `ownedTrip` and `ownedDraft`.
    - Perform optimistic concurrency check with `trips.advanceVersionForDraftMutation(...)`.
    - Delete selection via `trips.deleteDraftStaySelection(draft.id())`.
    - Return updated `TripResponse`.

### Automated verification
- [x] `.\mvnw.cmd test-compile` — compile repository and service changes cleanly.

---

## Phase 4: HTTP Controller Endpoints and Routing

### Changes
- [x] `src/main/java/app/detour/trip/TripController.java`
  - Add search endpoints supporting both draft-scoped and trip-scoped URLs:
    - `@GetMapping({"/{tripId}/drafts/{draftId}/stays", "/{tripId}/drafts/{draftId}/stay"})`
    - `@GetMapping({"/{tripId}/stays", "/{tripId}/stay"})`
  - Add selection endpoint:
    - `@PutMapping({"/{tripId}/drafts/{draftId}/stays", "/{tripId}/drafts/{draftId}/stay"})`
  - Add removal endpoint:
    - `@DeleteMapping({"/{tripId}/drafts/{draftId}/stays", "/{tripId}/drafts/{draftId}/stay"})`

### Automated verification
- [x] `.\mvnw.cmd test-compile` — ensure all controller endpoints compile without warnings.

---

## Phase 5: Integration Testing & Verification

### Changes
- [x] `src/test/java/app/detour/trip/StaySearchAndSelectionIntegrationTest.java`
  - Add comprehensive integration test suite mirroring `AirfareSearchAndSelectionIntegrationTest`:
    1. `searchStaysRequiresMandatoryAccommodationType`: validates missing/blank/unsupported `type` parameter returns 400 `VALIDATION_FAILED`.
    2. `searchStaysReturnsAvailableUnitsForDestinationAndDates`: verifies search results under draft and trip routes.
    3. `searchStaysCalculatesRoomCountForPartySize`: tests single traveler vs party of 3+ travelers yielding multiple rooms.
    4. `searchStaysExcludesVacationRentalsWhenCapacityExceeded`: verifies vacation rentals are excluded when party > capacity.
    5. `searchStaysExcludesUnitsWithInsufficientInventoryOnAnyNight`: validates exclusion when one night lacks inventory.
    6. `searchStaysProvidesCompletePricingAndNightlyBreakdown`: verifies integer cent pricing and nightly breakdown math.
    7. `searchStaysCalculatesAvailableBudgetExcludingExistingStay`: validates airfare/car subtraction without subtracting existing stay.
    8. `searchStaysDefaultRankingPlacesWithinBudgetBeforeOverBudget`: verifies Tier 1 vs Tier 2 ordering with tie-breakers.
    9. `searchStaysDefaultRankingWithoutTripBudgetOrdersDirectly`: verifies direct rating/price/distance ordering when budget is null.
    10. `searchStaysSortOverridesOrderResultsDeterministically`: verifies `LOWEST_PRICE`, `HIGHEST_RATING`, `NEAREST_CITY_CENTER`.
    11. `selectDraftStayPersistsUnitAndCalculatedRoomCount`: tests PUT persists selection, advances versions, returns full `TripResponse`.
    12. `selectDraftStayReplacesExistingSelectionWithoutDuplicates`: tests replacing stay updates cleanly in-place.
    13. `removeDraftStayDeletesSelectionAndAdvancesVersions`: tests DELETE removes stay selection and increments versions.
    14. `selectionMutationsEnforceOptimisticConcurrencyOnTripAndDraft`: validates 409 `VERSION_CONFLICT` on stale trip or draft versions.
    15. `unownedTripOrDraftRejectsSearchAndMutationWithNotFound`: validates 404 cross-user isolation with identical response shapes.

### Automated verification
- [x] `.\mvnw.cmd test -Dtest=StaySearchAndSelectionIntegrationTest` — execute new comprehensive test suite.
- [x] `.\mvnw.cmd test -Dtest=StaySearchAndSelectionIntegrationTest,AirfareSearchAndSelectionIntegrationTest,TripApiIntegrationTest` — verify related trip and airfare suites pass without regressions.
- [x] `.\mvnw.cmd test` — full repository test suite passes.

---

## Test Strategy
- Integration tests will run against Spring Boot with an in-memory H2 database seeded via Flyway with the deterministic March 2027 catalog fixtures (`V11`).
- Tests will exercise full HTTP request cycles via `MockMvc`, including session cookies, CSRF protection, request body validation, and JSON serialization.
- Assertions will verify exact room counts, multi-night price sums, available budget calculations, sort orders, database row state, and optimistic locking exceptions.

## Acceptance-Criteria Traceability
| Acceptance criterion | Planned code evidence | Planned test evidence |
| --- | --- | --- |
| Authenticated search by mandatory accommodation type (`HOTEL`, `BED_AND_BREAKFAST`, `VACATION_RENTAL`) | `AccommodationType.from`, `TripController.searchDraftStays`, `TripController.searchTripStays` | `StaySearchAndSelectionIntegrationTest.searchStaysRequiresMandatoryAccommodationType`, `searchStaysReturnsAvailableUnitsForDestinationAndDates` |
| Automatic room count calculation based on party size and unit capacity | `StaySearchService.search`, `TripService.selectDraftStay` | `StaySearchAndSelectionIntegrationTest.searchStaysCalculatesRoomCountForPartySize` |
| Vacation rentals excluded when guest capacity < traveler count | `StaySearchService.search`, `TripService.selectDraftStay` | `StaySearchAndSelectionIntegrationTest.searchStaysExcludesVacationRentalsWhenCapacityExceeded` |
| Stays with insufficient inventory on any night excluded | `StaySearchService.search`, `JdbcStaySearchRepository.findCandidates` | `StaySearchAndSelectionIntegrationTest.searchStaysExcludesUnitsWithInsufficientInventoryOnAnyNight` |
| Complete-stay pricing sums nightly amounts times required rooms in USD integer cents | `StaySearchService.search`, `StayPricingResponse` | `StaySearchAndSelectionIntegrationTest.searchStaysProvidesCompletePricingAndNightlyBreakdown` |
| Available trip budget subtracts selected airfare and car, but does not subtract existing stay | `TripService.searchStays`, `StaySearchService.search` | `StaySearchAndSelectionIntegrationTest.searchStaysCalculatesAvailableBudgetExcludingExistingStay` |
| Default ranking tiers within-budget before over-budget, ordered by rating, price, distance, and catalog key | `StaySearchService.search` default comparator | `StaySearchAndSelectionIntegrationTest.searchStaysDefaultRankingPlacesWithinBudgetBeforeOverBudget` |
| When no trip budget is set, tiering is omitted and results order directly by rating, price, distance | `StaySearchService.search` default comparator when `availableBudget == null` | `StaySearchAndSelectionIntegrationTest.searchStaysDefaultRankingWithoutTripBudgetOrdersDirectly` |
| Sort overrides (`LOWEST_PRICE`, `HIGHEST_RATING`, `NEAREST_CITY_CENTER`) order deterministically | `StaySort`, `StaySearchService.search` sort override comparators | `StaySearchAndSelectionIntegrationTest.searchStaysSortOverridesOrderResultsDeterministically` |
| Saving stay selection persists unit ID and count, advances versions, returns updated `TripResponse` | `TripRepository.saveDraftStaySelection`, `TripService.selectDraftStay`, `JdbcTripRepository.loadDraftSelections` | `StaySearchAndSelectionIntegrationTest.selectDraftStayPersistsUnitAndCalculatedRoomCount` |
| Replacing stay selection updates existing selection without duplicate rows | `TripRepository.saveDraftStaySelection` (delete + insert) | `StaySearchAndSelectionIntegrationTest.selectDraftStayReplacesExistingSelectionWithoutDuplicates` |
| Removing stay selection deletes draft stay record cleanly and advances versions | `TripService.removeDraftStay`, `JdbcTripRepository.deleteDraftStaySelection` | `StaySearchAndSelectionIntegrationTest.removeDraftStayDeletesSelectionAndAdvancesVersions` |
| Mismatched `expectedVersion` or `expectedDraftVersion` returns 409 `VERSION_CONFLICT` | `TripService.selectDraftStay`, `TripService.removeDraftStay`, `advanceVersionForDraftMutation` | `StaySearchAndSelectionIntegrationTest.selectionMutationsEnforceOptimisticConcurrencyOnTripAndDraft` |
| Unauthorized access to another user's Trip returns 404 with no data disclosure | `TripService.ownedTrip`, `TripService.ownedDraft` | `StaySearchAndSelectionIntegrationTest.unownedTripOrDraftRejectsSearchAndMutationWithNotFound` |
| Backend integration tests verify room calculation, inventory filtering, budget math, ranking determinism, selection persistence | `src/test/java/app/detour/trip/StaySearchAndSelectionIntegrationTest.java` | Full test execution passing 15 integration tests |

## Risks and Rollback/Recovery
- **Concurrency & Partial Mutation:** All mutation operations (`selectDraftStay`, `removeDraftStay`) are marked `@Transactional`. If `advanceVersionForDraftMutation` fails or an unexpected exception occurs, the transaction rolls back cleanly, leaving database state unaffected.
- **Data Integrity:** Table `detour_trip_draft_stay_selection` has a primary key on `draft_id`. `saveDraftStaySelection` executes a delete followed by an insert, guaranteeing that a draft never has duplicate stay selections.
- **Rollback:** In the event of an issue, code changes are purely additive (new stay package, new controller endpoints, and non-breaking repository additions). Reverting Git commits restores prior behavior without database schema changes.

## References
- Ticket: `ai/thoughts/tickets/2026-09-21-p04-t02-stay-search-and-draft-selection.md`
- Research: `ai/thoughts/research/2026-09-21-p04-t02-stay-search-and-draft-selection.md`
- Airfare Benchmark: `src/main/java/app/detour/airfare/AirfareSearchService.java`, `src/test/java/app/detour/trip/AirfareSearchAndSelectionIntegrationTest.java`
- Catalog Assertions: `src/test/java/app/detour/catalog/CatalogFixtureIntegrityAssertions.java`
- Migrations: `V4__create_catalog_inventory_schema.sql`, `V5__add_accommodation_search_metadata.sql`, `V11__seed_march_2027_stay_and_rental_catalog.sql`, `V14__create_draft_selection_and_planned_snapshot_schema.sql`
