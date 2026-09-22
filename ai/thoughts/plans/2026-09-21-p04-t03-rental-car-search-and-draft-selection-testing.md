# P04-T03 Rental Car Search and Draft Selection Testing Plan

## Change Summary
Implements airport rental car search, 25+ driver eligibility validation and explanation, consecutive 24-hour billing cycle pricing, half-open interval occupancy checking against `rental_unit_occupancy`, deterministic vehicle class ranking, and draft selection persistence and removal with optimistic concurrency and cross-user authorization.

## Impacted Areas and Risks
| Category | Risk | Planned evidence |
| --- | --- | --- |
| **Driver Age Gating (25+)** | Missing traveler ages or travelers under 25 allowed to select a car, or missing the exact actionable explanation string | Test search response includes `driverEligible=false`, `selectionDisabled=true`, and exact explanation text; test selection mutation strictly rejects saving with HTTP 400 `VALIDATION_FAILED` |
| **Timezone Interval Validation** | Ambiguity across destination airport local timezones (`America/Los_Angeles`, `Europe/Berlin`, `America/Mexico_City`) and UTC offsets causing false positives/negatives at day boundaries | Test pickup and return timestamp validation against trip start and end dates in airport local time for SFO, MUC, and MEX |
| **Duration & Pricing Math** | Partial 24-hour billing cycles undercharged or overcharged (e.g. 24h 1s counted as 1 cycle or 25h counted as 3 cycles) | Test pricing across 24h, 24h 1s, 25h, 48h, and 49h intervals; verify exact itemized base, tax, fee, and total USD integer cents |
| **Half-Open Interval Occupancy** | Units with active reservations erroneously shown as available, or abutting reservations incorrectly blocked | Test active occupancy exclusion in half-open interval `[pickupAt, returnAt)`; test back-to-back abutting intervals (`returnAt == occ.pickup_at`) remain available; test `RELEASED` occupancies do not block |
| **Deterministic Ranking** | Inconsistent ordering between search runs or across vehicle classes | Test default ordering strictly follows Economy -> Standard -> SUV, then total price ascending, then catalog key ascending |
| **Selection Persistence & Concurrency** | Duplicate rows on replacement, version drift, or stale mutations overwriting concurrent updates | Test draft selection replacement produces exactly 1 row; test optimistic locking returns 409 `VERSION_CONFLICT` on stale versions |
| **Cross-User Authorization** | Foreign user accessing or modifying another user's car search or draft selection | Test unauthorized search, select, and delete return 404 `RESOURCE_NOT_FOUND` with no data disclosure |

## Existing Coverage and Environment Constraints
- `RentalUnitOccupancyConstraintIntegrationTest`: Verifies H2 database trigger `RentalUnitOccupancyOverlapTrigger` directly on `rental_unit_occupancy`.
- `CatalogFixtureIntegrityAssertions`: Confirms 3 rental locations, 9 vehicle classes, and 21 physical units across SFO, MUC, and MEX.
- `StayAndRentalFixtureIntegrationTest`: Confirms rental catalog fixture seed counts.
- `TripApiIntegrationTest`: Verifies rental revalidation on trip detail updates (`revalidateRental`).
- Environment: Standard Spring Boot H2 in-memory test setup with unique database URL per test class via `@DynamicPropertySource`. No external credentials, networks, or services required.

## Failing Test First
- **Name:** `searchRentalCarsReturnsAvailableInventoryDeterministicallyRanked`
- **Type:** Spring Boot MockMvc Integration Test
- **Location:** `src/test/java/app/detour/trip/RentalSearchAndSelectionIntegrationTest.java`
- **Arrange:** Register a test user, create a trip for SFO from 2027-03-02 to 2027-03-06 with 2 travelers (ages 30 and 28).
- **Act:** Perform `GET /api/trips/{tripId}/drafts/{draftId}/rentals?pickupAt=2027-03-02T10:00:00-08:00&returnAt=2027-03-06T10:00:00-08:00`.
- **Assert:** Expect HTTP 200 OK with `driverEligible: true`, `selectionDisabled: false`, 4 billing cycles, and options ordered by Economy, Standard, SUV, then price ascending, then catalog key.
- **Expected pre-fix failure:** Fails with compilation error (test class and endpoint do not exist) or HTTP 404 Not Found before implementation.

## Tests to Add or Update

### 1. `searchRentalCarsReturnsAvailableInventoryDeterministicallyRanked`
- **Type:** Spring Boot MockMvc Integration Test
- **Location:** `src/test/java/app/detour/trip/RentalSearchAndSelectionIntegrationTest.java`
- **Proves:** Destination airport car search returns available physical units ordered by vehicle class (Economy -> Standard -> SUV), then lowest total price, then catalog key tie-breaker.
- **Inputs/fixture:** Trip destination SFO (`destination-sfo`), 2027-03-02 to 2027-03-06, pickup `2027-03-02T10:00:00-08:00`, return `2027-03-06T10:00:00-08:00`.
- **Doubles or boundary isolation:** In-memory H2 database with V1–V15 migrations and deterministic catalog fixtures.
- **Edge cases:** Verifies draft-scoped and trip-scoped plural and singular routes (`/rentals`, `/rental`, `/cars`, `/car`).

### 2. `searchRentalCarsValidatesPickupAndReturnDatesWithinTripIntervalInAirportTimezone`
- **Type:** Spring Boot MockMvc Integration Test
- **Location:** `src/test/java/app/detour/trip/RentalSearchAndSelectionIntegrationTest.java`
- **Proves:** Pickup and return date/times outside `[startDate, endDate]` in destination airport local timezone are rejected with HTTP 400 `VALIDATION_FAILED`.
- **Inputs/fixture:** SFO trip (`2027-03-02` to `2027-03-06`). Pickup on `2027-03-01T23:59:59-08:00` (before start) or return on `2027-03-07T00:00:01-08:00` (after end).
- **Doubles or boundary isolation:** Standard MockMvc.
- **Edge cases:** Timestamp crossing UTC calendar day boundary while remaining within destination local calendar day (e.g. 19:00 PST = 03:00 UTC next day).

### 3. `searchRentalCarsRejectsReturnBeforeOrEqualToPickup`
- **Type:** Spring Boot MockMvc Integration Test
- **Location:** `src/test/java/app/detour/trip/RentalSearchAndSelectionIntegrationTest.java`
- **Proves:** `returnAt <= pickupAt` returns HTTP 400 `VALIDATION_FAILED` on field `returnAt`.
- **Inputs/fixture:** Pickup `2027-03-03T10:00:00-08:00`, return `2027-03-03T10:00:00-08:00` (equal) or `2027-03-03T09:00:00-08:00` (before).
- **Doubles or boundary isolation:** Standard MockMvc.
- **Edge cases:** Exact minute equality (`pickupAt == returnAt`).

### 4. `searchRentalCarsEnforces25PlusDriverEligibilityWithActionableExplanation`
- **Type:** Spring Boot MockMvc Integration Test
- **Location:** `src/test/java/app/detour/trip/RentalSearchAndSelectionIntegrationTest.java`
- **Proves:** When no traveler is aged 25 or older (e.g. ages [22, 24] or `null`), search reports `driverEligible: false`, `selectionDisabled: true`, and actionable explanation: *"Rental cars require at least one traveler aged 25 or older. Please add traveler ages in Trip Details to select a car."*, while still returning inventory for browsing.
- **Inputs/fixture:** Trip with travelers aged [20, 22] and trip with `travelerAges: null`.
- **Doubles or boundary isolation:** Standard MockMvc.
- **Edge cases:** Empty traveler ages, all under 25, exactly age 25 (eligible).

### 5. `searchRentalCarsFiltersOutActiveOverlappingOccupancyOnHalfOpenInterval`
- **Type:** Spring Boot MockMvc Integration Test
- **Location:** `src/test/java/app/detour/trip/RentalSearchAndSelectionIntegrationTest.java`
- **Proves:** Physical units with active overlapping reservations in `rental_unit_occupancy` are excluded from search results; adjacent half-open intervals and `RELEASED` occupancies remain available.
- **Inputs/fixture:** Insert active occupancy for `rental-unit-sfo-economy-01` from `2027-03-03T10:00:00-08:00` to `2027-03-04T10:00:00-08:00`.
- **Doubles or boundary isolation:** Direct JDBC insert into test H2 database.
- **Edge cases:**
  - Search overlapping `[2027-03-02T10:00:00-08:00, 2027-03-05T10:00:00-08:00)`: unit excluded.
  - Search abutting `[2027-03-02T10:00:00-08:00, 2027-03-03T10:00:00-08:00)`: unit included.
  - Occupancy with `occupancy_status = 'RELEASED'`: unit included.

### 6. `pricingCalculatesConsecutive24HourBillingCyclesAndRoundsUpPartialFinalCycle`
- **Type:** Spring Boot MockMvc Integration Test
- **Location:** `src/test/java/app/detour/trip/RentalSearchAndSelectionIntegrationTest.java`
- **Proves:** Consecutive 24-hour billing cycle math rounds any partial final cycle up to a full cycle and produces complete taxes-and-fees-inclusive totals in integer cents.
- **Inputs/fixture:** SFO Economy ($42.77 base + $3.43 tax + $1.80 fee = $48.00 / cycle):
  - 24h interval: 1 cycle -> $48.00 ($4,800 cents)
  - 24h 1s interval: 2 cycles -> $96.00 ($9,600 cents)
  - 25h interval: 2 cycles -> $96.00 ($9,600 cents)
  - 48h interval: 2 cycles -> $96.00 ($9,600 cents)
  - 49h interval: 3 cycles -> $144.00 ($14,400 cents)
- **Doubles or boundary isolation:** Standard MockMvc.
- **Edge cases:** Exact 24-hour second boundary vs 1-second boundary overflow.

### 7. `selectDraftRentalPersistsSelectionAndAdvancesVersions`
- **Type:** Spring Boot MockMvc Integration Test
- **Location:** `src/test/java/app/detour/trip/RentalSearchAndSelectionIntegrationTest.java`
- **Proves:** `PUT /api/trips/{tripId}/drafts/{draftId}/rentals` persists unit ID, pickup time, return time, advances trip and draft versions, and returns `TripResponse` containing `selections.rental`.
- **Inputs/fixture:** Trip version 0, draft version 0, SFO Economy unit ID, valid pickup and return times.
- **Doubles or boundary isolation:** Standard MockMvc.
- **Edge cases:** Verifies response fields (`rentalUnitId`, `pickupAt`, `returnAt`, `locationName`, `vehicleClassName`, `unitIdentifier`, `dailyBasePriceCents`, etc.).

### 8. `selectDraftRentalRejectsWhenDriverUnder25`
- **Type:** Spring Boot MockMvc Integration Test
- **Location:** `src/test/java/app/detour/trip/RentalSearchAndSelectionIntegrationTest.java`
- **Proves:** Attempting to select a rental car when no traveler is 25 or older is strictly rejected with HTTP 400 `VALIDATION_FAILED` (field `travelerAges`).
- **Inputs/fixture:** Trip created with travelers aged [24, 21].
- **Doubles or boundary isolation:** Standard MockMvc.
- **Edge cases:** Error message matches actionable explanation.

### 9. `selectDraftRentalRejectsWhenUnitOccupiedOrInvalidDates`
- **Type:** Spring Boot MockMvc Integration Test
- **Location:** `src/test/java/app/detour/trip/RentalSearchAndSelectionIntegrationTest.java`
- **Proves:** Selecting a car with invalid dates (return before pickup, outside trip dates) or selecting a unit with active overlapping occupancy is rejected with HTTP 400 `VALIDATION_FAILED`.
- **Inputs/fixture:** Mismatched destination, occupied unit, inverted pickup/return timestamps.
- **Doubles or boundary isolation:** Standard MockMvc.
- **Edge cases:** Unit belonging to MUC selected for SFO trip.

### 10. `replaceDraftRentalSelectionUpdatesDraftWithoutDuplicateRows`
- **Type:** Spring Boot MockMvc Integration Test
- **Location:** `src/test/java/app/detour/trip/RentalSearchAndSelectionIntegrationTest.java`
- **Proves:** Selecting a second rental car replaces the first rental selection on the draft without creating duplicate rows in `detour_trip_draft_rental_selection`.
- **Inputs/fixture:** Select SFO Economy, then select SFO SUV on same draft with updated expected versions.
- **Doubles or boundary isolation:** Direct JDBC query verifying row count is exactly 1.
- **Edge cases:** Replacing with different pickup and return times.

### 11. `removeDraftRentalSelectionDeletesSelectionAndAdvancesVersions`
- **Type:** Spring Boot MockMvc Integration Test
- **Location:** `src/test/java/app/detour/trip/RentalSearchAndSelectionIntegrationTest.java`
- **Proves:** `DELETE /api/trips/{tripId}/drafts/{draftId}/rentals` removes the rental selection, advances versions, and returns `selections.rental: null`.
- **Inputs/fixture:** Draft with existing rental selection.
- **Doubles or boundary isolation:** Standard MockMvc.
- **Edge cases:** Removing an already empty selection.

### 12. `draftRentalMutationsEnforceOptimisticConcurrency`
- **Type:** Spring Boot MockMvc Integration Test
- **Location:** `src/test/java/app/detour/trip/RentalSearchAndSelectionIntegrationTest.java`
- **Proves:** Stale `expectedVersion` or `expectedDraftVersion` on select or remove returns HTTP 409 `VERSION_CONFLICT`.
- **Inputs/fixture:** Pass `expectedVersion: 999` or `expectedDraftVersion: 999`.
- **Doubles or boundary isolation:** Standard MockMvc.
- **Edge cases:** Verify database state remains unchanged upon conflict.

### 13. `rentalEndpointsEnforceCrossUserIsolation`
- **Type:** Spring Boot MockMvc Integration Test
- **Location:** `src/test/java/app/detour/trip/RentalSearchAndSelectionIntegrationTest.java`
- **Proves:** User B attempting search, select, or delete on User A's trip or draft receives HTTP 404 `RESOURCE_NOT_FOUND` with zero data disclosure.
- **Inputs/fixture:** Register User A and User B. User A creates trip and draft; User B attempts all rental endpoints.
- **Doubles or boundary isolation:** Standard MockMvc.
- **Edge cases:** Random non-existent UUIDs return identical 404 response shape.

## Safe Verification Commands
- Focused: `.\mvnw.cmd test -Dtest=RentalSearchAndSelectionIntegrationTest`
- Related suite: `.\mvnw.cmd test -Dtest=RentalSearchAndSelectionIntegrationTest,StaySearchAndSelectionIntegrationTest,AirfareSearchAndSelectionIntegrationTest,RentalUnitOccupancyConstraintIntegrationTest`
- Full safe suite: `.\mvnw.cmd test`

## Optional Developer Checks
- None.

## Exit Criteria
- [x] The planned red test fails for the intended reason before implementation, when applicable.
- [x] New and updated tests pass after implementation.
- [x] The broadest safe relevant repository test suite passes.
- [x] Acceptance criteria map to executable evidence.
- [x] Routine automated tests do not perform unintended live or destructive operations.
- [x] Material risks and edge cases identified above are covered.
- [x] Any optional check is reported as nonblocking and is not represented as already performed.
