# Stay Search and Draft Selection Testing Plan

## Change Summary
Introduce stay search, automatic room calculations, multi-night inventory validation, complete stay pricing with transparent nightly and per-room breakdowns, available budget determination, deterministic ranking and sort overrides, and draft stay selection persistence, replacement, and removal with optimistic locking and cross-user isolation.

## Impacted Areas and Risks
| Category | Risk | Planned evidence |
| --- | --- | --- |
| Accommodation Type Validation | Search request with missing, blank, or unsupported accommodation type could return unconstrained results or fail with unhandled 500 error | `searchStaysRequiresMandatoryAccommodationType` tests missing parameter, empty string, and invalid enum values returning 400 `VALIDATION_FAILED` with `type` issue |
| Room Count Calculation | Integer division truncation (`travelerCount / capacity`) would undercount required rooms for large parties (e.g. party of 3 in 2-person room) | `searchStaysCalculatesRoomCountForPartySize` asserts party of 1 gets 1 room, party of 3 in capacity-2 room gets 2 rooms, party of 5 gets 3 rooms |
| Vacation Rental Capacity Exclusion | Vacation rental with capacity less than party size might be included despite whole-property rule | `searchStaysExcludesVacationRentalsWhenCapacityExceeded` verifies vacation rentals with capacity < travelerCount are omitted from results |
| Multi-Night Inventory Validation | Unit available on most nights but sold out on a single night in `[startDate, endDate)` could be booked, causing overbooking | `searchStaysExcludesUnitsWithInsufficientInventoryOnAnyNight` artificially zeroes inventory on one night and asserts unit is excluded from search results |
| Complete Stay Pricing | Calculation errors in summing base, tax, fee across nights or multiplying by required rooms | `searchStaysProvidesCompletePricingAndNightlyBreakdown` verifies exact arithmetic sums for nightly items, per-room totals, and complete stay totals in USD integer cents |
| Available Budget Calculation | Replacing an existing stay selection on a draft could erroneously subtract the existing stay from its own search budget, artificially constraining options | `searchStaysCalculatesAvailableBudgetExcludingExistingStay` verifies available budget subtracts airfare and car totals but leaves stay un-subtracted |
| Deterministic Ranking (Default with Budget) | Over-budget options could rank above within-budget options, or equal-cost options could shuffle arbitrarily across queries | `searchStaysDefaultRankingPlacesWithinBudgetBeforeOverBudget` verifies Tier 1 (within budget) precedes Tier 2 (over budget), sorted by rating desc, price asc, distance asc, catalog key asc |
| Default Ranking (No Budget) | Search on trip without budget could crash on null pointer or improperly apply budget tiering | `searchStaysDefaultRankingWithoutTripBudgetOrdersDirectly` verifies trips with `budgetCents == null` order directly by rating desc, price asc, distance asc, catalog key asc |
| Sort Overrides | Overrides could fail to apply, ignore tie-breakers, or accept invalid values | `searchStaysSortOverridesOrderResultsDeterministically` validates `LOWEST_PRICE`, `HIGHEST_RATING`, `NEAREST_CITY_CENTER`, and 400 error on invalid sort |
| Draft Selection Persistence | Selected unit or calculated room count not persisted in `detour_trip_draft_stay_selection`, or `TripResponse` omits property/unit names and nightly breakdown | `selectDraftStayPersistsUnitAndCalculatedRoomCount` asserts DB row exists, version advances, and `TripResponse` contains full `StayComponentResponse` details |
| Selection Replacement | Replacing stay creates duplicate rows in draft selection table | `selectDraftStayReplacesExistingSelectionWithoutDuplicates` saves a selection then replaces it, asserting exactly 1 DB row remains |
| Selection Removal | DELETE endpoint fails to remove DB row or fails to advance trip and draft versions | `removeDraftStayDeletesSelectionAndAdvancesVersions` validates row deletion and version increments |
| Optimistic Concurrency | Stale `expectedVersion` or `expectedDraftVersion` could overwrite concurrent edits | `selectionMutationsEnforceOptimisticConcurrencyOnTripAndDraft` validates 409 `VERSION_CONFLICT` on stale trip version and draft version for both PUT and DELETE |
| Cross-User Isolation | User A could view or mutate User B's trip or draft | `unownedTripOrDraftRejectsSearchAndMutationWithNotFound` tests foreign trips return 404 `RESOURCE_NOT_FOUND` identical to nonexistent IDs |

## Existing Coverage and Environment Constraints
- **Existing Tests:**
  - `CatalogFixtureIntegrityAssertions.java:29-83`: validates 18 accommodation properties, 18 units, and 540 nightly inventory rows seeded by `V11`.
  - `AirfareSearchAndSelectionIntegrationTest.java`: established pattern for component search and draft selection integration testing with Spring Boot Test, MockMvc, and H2 database.
  - `TripApiIntegrationTest.java`: validates trip lifecycle, draft mutations, and revalidation logic.
- **Environment Constraints:**
  - Standard Spring Boot Test running against an in-memory H2 database (`jdbc:h2:mem:...`) with Flyway migrations applied up to `V15`.
  - Authentication requires session cookies (`JSESSIONID`) and CSRF headers (`X-CSRF-TOKEN`) via MockMvc requests.
  - Stay dates must fall within the catalog inventory window: March 1–30, 2027.

## Failing Test First
- Name: `searchDraftStaysReturnsAvailableUnitsForDestinationAndDates`
- Type: Integration Test (`@SpringBootTest`, `@AutoConfigureMockMvc`)
- Location: `src/test/java/app/detour/trip/StaySearchAndSelectionIntegrationTest.java`
- Arrange/Act/Assert:
  - **Arrange:** Register a test user, create a trip to `destination-sfo` for `2027-03-01` to `2027-03-05` (4 nights) with 2 travelers and budget of 100,000 cents ($1,000). Extract `tripId` and `draftId`.
  - **Act:** Perform HTTP `GET /api/trips/{tripId}/drafts/{draftId}/stays?type=HOTEL`.
  - **Assert:** Expect HTTP 200 OK, JSON response with `options` array containing 2 hotel options for SFO, each with positive total price, transparent pricing breakdown, and valid property details.
- Expected pre-fix failure:
  - HTTP 404 (or 405 Method Not Allowed) because neither `/api/trips/{tripId}/drafts/{draftId}/stays` nor `/api/trips/{tripId}/drafts/{draftId}/stay` is registered in `TripController`.

## Tests to Add or Update

### 1. `searchStaysRequiresMandatoryAccommodationType`
- Type: Integration Test (`MockMvc`)
- Location: `src/test/java/app/detour/trip/StaySearchAndSelectionIntegrationTest.java`
- Proves: The `type` query parameter is mandatory; omitting it, supplying an empty string, or supplying an unsupported value returns HTTP 400 with field-level issue `{"type": "Choose a supported accommodation type."}`.
- Inputs/fixture: SFO trip, queries with `type=` omitted, `type=""`, and `type=RESORT`.
- Doubles or boundary isolation: In-memory H2 database, real controller and service.
- Edge cases: Case-insensitivity (`hotel` vs `HOTEL`), whitespace trimming.

### 2. `searchStaysReturnsAvailableUnitsForDestinationAndDates`
- Type: Integration Test (`MockMvc`)
- Location: `src/test/java/app/detour/trip/StaySearchAndSelectionIntegrationTest.java`
- Proves: Stays can be queried under both draft-scoped (`/api/trips/{tripId}/drafts/{draftId}/stays`) and trip-scoped (`/api/trips/{tripId}/stays`) routes, returning properties for the trip destination.
- Inputs/fixture: Trips in SFO, MUC, MEX across March 2027 dates.
- Doubles or boundary isolation: In-memory H2 database.
- Edge cases: Singular route alias (`/stay`) works identically to plural (`/stays`).

### 3. `searchStaysCalculatesRoomCountForPartySize`
- Type: Integration Test (`MockMvc`)
- Location: `src/test/java/app/detour/trip/StaySearchAndSelectionIntegrationTest.java`
- Proves: Required room count is calculated via `ceil(travelerCount / guestCapacity)` for rooms.
- Inputs/fixture: Hotel unit with capacity 2:
  - Party of 1: `requiredRooms == 1`.
  - Party of 2: `requiredRooms == 1`.
  - Party of 3: `requiredRooms == 2`.
  - Party of 5: `requiredRooms == 3`.
- Doubles or boundary isolation: In-memory H2 database.
- Edge cases: Ceiling boundary when `travelerCount % guestCapacity != 0`.

### 4. `searchStaysExcludesVacationRentalsWhenCapacityExceeded`
- Type: Integration Test (`MockMvc`)
- Location: `src/test/java/app/detour/trip/StaySearchAndSelectionIntegrationTest.java`
- Proves: Vacation rentals are treated as whole properties (`requiredRooms = 1`) and excluded if unit capacity is strictly less than party size.
- Inputs/fixture: Vacation rental with capacity 4 queried with party of 5: excluded from options. Queried with party of 3: included with `requiredRooms == 1`.
- Doubles or boundary isolation: In-memory H2 database.
- Edge cases: Exact capacity match (`party == capacity`) is included.

### 5. `searchStaysExcludesUnitsWithInsufficientInventoryOnAnyNight`
- Type: Integration Test (`MockMvc`)
- Location: `src/test/java/app/detour/trip/StaySearchAndSelectionIntegrationTest.java`
- Proves: Any accommodation unit with `available_inventory < requiredRooms` on ANY night in `[startDate, endDate)` is filtered out of search results.
- Inputs/fixture: 4-night stay; temporarily update inventory of one unit to `available_inventory = 0` on night 3. Assert this unit is omitted while the other unit is returned. Restore inventory after test.
- Doubles or boundary isolation: In-memory H2 database.
- Edge cases: First night zero inventory, last night zero inventory, middle night zero inventory.

### 6. `searchStaysProvidesCompletePricingAndNightlyBreakdown`
- Type: Integration Test (`MockMvc`)
- Location: `src/test/java/app/detour/trip/StaySearchAndSelectionIntegrationTest.java`
- Proves: Pricing computes exact sums of nightly base, tax, and fee amounts multiplied by `requiredRooms`, returning per-room sums and per-night itemized records in USD integer cents.
- Inputs/fixture: 3-night stay with 2 rooms required; compare returned pricing breakdown values with raw database inventory records.
- Doubles or boundary isolation: In-memory H2 database.
- Edge cases: Multi-room party pricing scaling.

### 7. `searchStaysCalculatesAvailableBudgetExcludingExistingStay`
- Type: Integration Test (`MockMvc`)
- Location: `src/test/java/app/detour/trip/StaySearchAndSelectionIntegrationTest.java`
- Proves: Available trip budget subtracts selected airfare and car totals, but does NOT subtract an existing stay selection when searching for a replacement stay.
- Inputs/fixture: Trip with $2,000 budget. Draft has $500 airfare selection and $300 stay selection. When searching stays, `availableTripBudgetCents` must be $1,500 ($2,000 - $500 airfare), NOT $1,200.
- Doubles or boundary isolation: In-memory H2 database.
- Edge cases: Trip with no other selections: `availableBudget == trip.budgetCents`.

### 8. `searchStaysDefaultRankingPlacesWithinBudgetBeforeOverBudget`
- Type: Integration Test (`MockMvc`)
- Location: `src/test/java/app/detour/trip/StaySearchAndSelectionIntegrationTest.java`
- Proves: In default ranking, options within available budget (Tier 1) are sorted before options exceeding budget (Tier 2). Within each tier, options are sorted by highest rating desc, lowest price asc, nearest distance asc, catalog key asc.
- Inputs/fixture: Set trip budget such that one stay fits budget and another exceeds budget. Assert within-budget option is ranked first regardless of price or rating differences.
- Doubles or boundary isolation: In-memory H2 database.
- Edge cases: Both within budget, both over budget.

### 9. `searchStaysDefaultRankingWithoutTripBudgetOrdersDirectly`
- Type: Integration Test (`MockMvc`)
- Location: `src/test/java/app/detour/trip/StaySearchAndSelectionIntegrationTest.java`
- Proves: When `trip.budgetCents == null`, budget-fit tiering is omitted and options order directly by rating desc, price asc, distance asc, catalog key asc.
- Inputs/fixture: Trip created with `budgetCents: null`.
- Doubles or boundary isolation: In-memory H2 database.
- Edge cases: Null available budget in response.

### 10. `searchStaysSortOverridesOrderResultsDeterministically`
- Type: Integration Test (`MockMvc`)
- Location: `src/test/java/app/detour/trip/StaySearchAndSelectionIntegrationTest.java`
- Proves: Sort overrides (`LOWEST_PRICE`, `HIGHEST_RATING`, `NEAREST_CITY_CENTER`) order results deterministically with property catalog key as tie-breaker.
- Inputs/fixture: Search with `sort=LOWEST_PRICE`, `sort=HIGHEST_RATING`, `sort=NEAREST_CITY_CENTER`. Assert ordering criteria.
- Doubles or boundary isolation: In-memory H2 database.
- Edge cases: Invalid sort string returns 400 `VALIDATION_FAILED`.

### 11. `selectDraftStayPersistsUnitAndCalculatedRoomCount`
- Type: Integration Test (`MockMvc`)
- Location: `src/test/java/app/detour/trip/StaySearchAndSelectionIntegrationTest.java`
- Proves: `PUT /api/trips/{tripId}/drafts/{draftId}/stay` persists unit ID and calculated room count in `detour_trip_draft_stay_selection`, increments trip and draft versions, and returns updated `TripResponse` containing full stay selection data.
- Inputs/fixture: Draft with version 0, payload `{"expectedVersion":0, "expectedDraftVersion":0, "accommodationUnitId":123}`.
- Doubles or boundary isolation: In-memory H2 database.
- Edge cases: Response includes `propertyName`, `unitName`, and itemized `nights`.

### 12. `selectDraftStayReplacesExistingSelectionWithoutDuplicates`
- Type: Integration Test (`MockMvc`)
- Location: `src/test/java/app/detour/trip/StaySearchAndSelectionIntegrationTest.java`
- Proves: Selecting a different stay updates the existing selection on the draft without creating duplicate rows.
- Inputs/fixture: Select unit A, then select unit B with updated versions.
- Doubles or boundary isolation: In-memory H2 database.
- Edge cases: Exactly 1 row in `detour_trip_draft_stay_selection` for that `draft_id`.

### 13. `removeDraftStayDeletesSelectionAndAdvancesVersions`
- Type: Integration Test (`MockMvc`)
- Location: `src/test/java/app/detour/trip/StaySearchAndSelectionIntegrationTest.java`
- Proves: `DELETE /api/trips/{tripId}/drafts/{draftId}/stay` deletes the row in `detour_trip_draft_stay_selection`, increments trip and draft versions, and returns `TripResponse` with `stay` set to null.
- Inputs/fixture: Draft with saved stay, DELETE with `{"expectedVersion":1, "expectedDraftVersion":1}`.
- Doubles or boundary isolation: In-memory H2 database.
- Edge cases: Deleting when already empty or after replacement.

### 14. `selectionMutationsEnforceOptimisticConcurrencyOnTripAndDraft`
- Type: Integration Test (`MockMvc`)
- Location: `src/test/java/app/detour/trip/StaySearchAndSelectionIntegrationTest.java`
- Proves: Providing a stale `expectedVersion` or `expectedDraftVersion` on PUT or DELETE returns 409 `VERSION_CONFLICT` with current versions, leaving database untouched.
- Inputs/fixture: Send `expectedVersion: 99` and `expectedDraftVersion: 99` on both PUT and DELETE.
- Doubles or boundary isolation: In-memory H2 database.
- Edge cases: Stale trip version vs stale draft version.

### 15. `unownedTripOrDraftRejectsSearchAndMutationWithNotFound`
- Type: Integration Test (`MockMvc`)
- Location: `src/test/java/app/detour/trip/StaySearchAndSelectionIntegrationTest.java`
- Proves: Accessing another user's trip or draft for search, selection, or removal returns 404 `RESOURCE_NOT_FOUND` with identical response body to a nonexistent random UUID.
- Inputs/fixture: Two registered users (owner and intruder). Intruder calls search, PUT, DELETE on owner's trip and draft.
- Doubles or boundary isolation: In-memory H2 database.
- Edge cases: Nonexistent trip vs foreign owned trip.

## Safe Verification Commands
- Focused: `.\mvnw.cmd test -Dtest=StaySearchAndSelectionIntegrationTest`
- Related suite: `.\mvnw.cmd test -Dtest=StaySearchAndSelectionIntegrationTest,AirfareSearchAndSelectionIntegrationTest,TripApiIntegrationTest`
- Full safe suite: `.\mvnw.cmd test`

## Optional Developer Checks
- None planned. All acceptance criteria are fully automated by executable integration tests.

## Exit Criteria
- [x] The planned red test fails for the intended reason before implementation, when applicable.
- [x] New and updated tests pass after implementation.
- [x] The broadest safe relevant repository test suite passes.
- [x] Acceptance criteria map to executable evidence.
- [x] Routine automated tests do not perform unintended live or destructive operations.
- [x] Material risks and edge cases identified above are covered.
- [x] Any optional check is reported as nonblocking and is not represented as already performed.
