# P04-T01 Deliver Airfare Search and Draft Selection Testing Plan

## Change Summary

Implement round-trip airfare search between fixed origin PDX and Trip destination for scheduled dates, available seat capacity filtering, `directOnly` filtering, March 31 arrival boundary exclusion, complete-party USD integer cent pricing, deterministic ranking across all supported sorts with immutable combination key tie-breaker, and Draft airfare selection persistence, replacement, and removal with optimistic concurrency (`expectedVersion` and `expectedDraftVersion`) and complete cross-user isolation.

## Impacted Areas and Risks

| Category | Risk | Planned evidence |
| --- | --- | --- |
| **Search Route & Pairing** | Outbound/return leg mismatch, wrong service dates, or incorrect origin/destination pairing. | Integration test verifying all combinations connect PDX and Trip destination on start and end dates. |
| **Capacity Filtering** | Flights with fewer available seats than party size are displayed or selected. | Test asserting combinations with insufficient capacity are omitted from search results and rejected during selection. |
| **`directOnly` Filtering** | One-stop flights returned when `directOnly=true` or direct flights omitted when `false`. | Tests verifying only 0-stop flights are returned when `directOnly=true` and both 0-stop and 1-stop when `false`. |
| **Deterministic Ranking** | Non-deterministic ordering across runs or missing tie-breaker. | Tests verifying exact sequence for `DEFAULT`, `LOWEST_PRICE`, `SHORTEST_DURATION`, `EARLIEST_DEPARTURE`, and `FEWEST_STOPS` sorting with `${outboundCatalogKey}__${returnCatalogKey}` tie-breaker. |
| **Party Pricing Breakdown** | Incorrect multiplication by traveler count or rounding errors. | Assertions checking base fare, tax, fee, and total equal `travelerCount * perTravelerAmount` in integer cents. |
| **Optimistic Concurrency** | Lost updates or state inconsistency if Trip or Draft version mismatches are ignored. | Tests asserting 409 `VERSION_CONFLICT` when either `expectedVersion` or `expectedDraftVersion` is stale, verifying no database mutation. |
| **Selection Mutation Lifecycle** | Orphaned rows or failure to increment Draft/Trip versions on save, replace, or delete. | Persistence tests verifying exact row counts in `detour_trip_draft_airfare_selection`, clean replacement, and version increments on both Draft and Trip. |
| **Data Isolation & Security** | Data disclosure or mutation across user boundaries (IDOR). | Tests asserting unowned Trip or Draft search/mutation requests return 404 `RESOURCE_NOT_FOUND` indistinguishable from non-existent resources. |

## Existing Coverage and Environment Constraints

- **`AirfareFixtureIntegrationTest`:** Verifies deterministic Flyway migrations (`V1` through `V15`), seeded flight catalog for March 2027 (24 schedules, 720 instances, 1,080 segments), capacities >= 8, and minimum 45-minute layovers.
- **`AirfareFixtureIntegrityAssertions`:** Verifies timezones, connections, durations, pricing variations, and March 31 cutoff boundaries.
- **`TripApiIntegrationTest`:** Covers Trip and Draft creation, duplicate/delete operations, and version conflict handling.
- **Environment:** In-memory H2 database, Spring Boot test slice with `MockMvc`. Fast, isolated, deterministic, no external services or credentials required.

## Failing Test First

- **Name:** `searchRoundTripAirfareReturnsAvailableCombinationsForTripDestinationAndDates`
- **Type:** HTTP Integration Test (`MockMvc`)
- **Location:** `src/test/java/app/detour/trip/AirfareSearchAndSelectionIntegrationTest.java`
- **Arrange:**
  - Register and authenticate a test client via `register("red-test@example.test")`.
  - Create a Trip to SFO for dates `2027-03-01` to `2027-03-05` with 2 travelers.
  - Extract `tripId` and `draftId` from the creation response.
- **Act:**
  - Perform `GET /api/trips/{tripId}/drafts/{draftId}/airfare` with session cookies and CSRF token.
- **Assert:**
  - Response status is 200 OK.
  - JSON payload contains `options` array with 16 combinations (4 outbound x 4 return).
  - Each option includes `combinationKey`, `outbound`, `returnFlight`, `totalDurationMinutes`, `direct`, and `pricing`.
  - `pricing.travelerCount` is 2 and `pricing.partyTotalPriceCents` equals `2 * (outbound.totalFareCents + returnFlight.totalFareCents)`.
- **Expected pre-fix failure:** HTTP 404 Not Found (`No static resource api/trips/.../airfare` or unmapped controller endpoint).

## Tests to Add or Update

### 1. `searchRoundTripAirfareReturnsAvailableCombinationsForTripDestinationAndDates`
- **Type:** HTTP Integration Test
- **Location:** `src/test/java/app/detour/trip/AirfareSearchAndSelectionIntegrationTest.java`
- **Proves:** Authenticated search on an owned Trip/Draft returns round-trip combinations between PDX and Trip destination for scheduled dates.
- **Inputs/fixture:** Trip destination SFO, dates 2027-03-01 to 2027-03-05, travelerCount 2.
- **Doubles or boundary isolation:** In-memory H2 database seeded with March 2027 catalog.
- **Edge cases:** Verifies flight carrier, flight numbers, airport codes, duration, available seats, and departure/arrival times with timezones.

### 2. `searchExcludesCombinationsWithInsufficientAvailableSeats`
- **Type:** HTTP Integration Test
- **Location:** `src/test/java/app/detour/trip/AirfareSearchAndSelectionIntegrationTest.java`
- **Proves:** Flight instances with `available_seats < travelerCount` are omitted from search results.
- **Inputs/fixture:** Create Trip with 6 travelers. In database, set `available_seats = 5` on one outbound instance.
- **Doubles or boundary isolation:** Direct H2 update on target instance.
- **Edge cases:** Verifies that options containing the restricted outbound flight are excluded while options with sufficient seats remain.

### 3. `directOnlyFilterRestrictsResultsToDirectFlights`
- **Type:** HTTP Integration Test
- **Location:** `src/test/java/app/detour/trip/AirfareSearchAndSelectionIntegrationTest.java`
- **Proves:** `directOnly=true` returns only combinations where both outbound and return are direct (0 stops). Omitting `directOnly` or setting `false` returns both direct and one-stop flights.
- **Inputs/fixture:** Query with `?directOnly=true` (expects 4 direct x direct combinations) vs `?directOnly=false` (expects 16 combinations).
- **Doubles or boundary isolation:** Real catalog fixtures.
- **Edge cases:** Verifies all returned options have `direct == true` and both legs have `stopCount == 0`.

### 4. `defaultRankingOrdersDirectFirstThenPriceThenDurationThenTieBreaker`
- **Type:** HTTP Integration Test
- **Location:** `src/test/java/app/detour/trip/AirfareSearchAndSelectionIntegrationTest.java`
- **Proves:** Default search ranking orders: (1) direct combinations first, (2) lowest complete-party price, (3) shortest total duration, (4) combination key ascending.
- **Inputs/fixture:** Search results without explicit sort parameter (defaults to `DEFAULT`).
- **Doubles or boundary isolation:** Real catalog fixtures.
- **Edge cases:** Direct combinations precede one-stop options even when one-stop options have lower price.

### 5. `sortOverridesApplyDeterministicallyWithCombinationKeyTieBreaker`
- **Type:** HTTP Integration Test
- **Location:** `src/test/java/app/detour/trip/AirfareSearchAndSelectionIntegrationTest.java`
- **Proves:** Sort options (`LOWEST_PRICE`, `SHORTEST_DURATION`, `EARLIEST_DEPARTURE`, `FEWEST_STOPS`) order results as specified, each with `${outboundCatalogKey}__${returnCatalogKey}` tie-breaker.
- **Inputs/fixture:** Queries with `?sort=LOWEST_PRICE`, `?sort=SHORTEST_DURATION`, `?sort=EARLIEST_DEPARTURE`, and `?sort=FEWEST_STOPS`.
- **Doubles or boundary isolation:** Real catalog fixtures.
- **Edge cases:** Rejects invalid sort parameter with 400 `VALIDATION_FAILED`.

### 6. `partyPricingCalculatesExactTotalsInCentsWithTransparentBreakdown`
- **Type:** HTTP Integration Test
- **Location:** `src/test/java/app/detour/trip/AirfareSearchAndSelectionIntegrationTest.java`
- **Proves:** Complete-party pricing calculates exact USD integer cents: `travelerCount * (baseFare + tax + fee)` for outbound plus return, with per-traveler and party breakdowns.
- **Inputs/fixture:** Trips with 1, 3, and 8 travelers.
- **Doubles or boundary isolation:** Real catalog fixtures.
- **Edge cases:** Large party sizes (8 travelers) verifying no 32-bit integer overflow.

### 7. `saveDraftAirfarePersistsSelectionAdvancesDraftAndTripVersionAndReturnsTripResponse`
- **Type:** HTTP Integration Test
- **Location:** `src/test/java/app/detour/trip/AirfareSearchAndSelectionIntegrationTest.java`
- **Proves:** `PUT /api/trips/{tripId}/drafts/{draftId}/airfare` persists outbound and return flight instance IDs in `detour_trip_draft_airfare_selection`, increments `detour_trip_draft.version`, advances `detour_trip.version`, and returns updated `TripResponse` with full `DraftSelectionResponse`.
- **Inputs/fixture:** Valid outbound and return flight instances matching destination and dates.
- **Doubles or boundary isolation:** Real catalog fixtures.
- **Edge cases:** Response `selections.airfare` contains accurate descriptions ("Flight CS101", "Flight CS102") and fares.

### 8. `replaceDraftAirfareReplacesSelectionCleanlyWithoutOrphans`
- **Type:** HTTP Integration Test
- **Location:** `src/test/java/app/detour/trip/AirfareSearchAndSelectionIntegrationTest.java`
- **Proves:** Saving a new airfare selection on a Draft with an existing selection replaces the selection without orphaned rows.
- **Inputs/fixture:** Save flight option A, then save flight option B on the same Draft.
- **Doubles or boundary isolation:** Database verification of single row in `detour_trip_draft_airfare_selection`.
- **Edge cases:** Verifies draft and trip versions increment on each mutation.

### 9. `removeDraftAirfareDeletesSelectionAdvancesDraftAndTripVersion`
- **Type:** HTTP Integration Test
- **Location:** `src/test/java/app/detour/trip/AirfareSearchAndSelectionIntegrationTest.java`
- **Proves:** `DELETE /api/trips/{tripId}/drafts/{draftId}/airfare` removes row from `detour_trip_draft_airfare_selection`, increments draft version, advances trip version, and returns `TripResponse` with `selections.airfare = null`.
- **Inputs/fixture:** Draft with saved airfare selection.
- **Doubles or boundary isolation:** Database check that row count in `detour_trip_draft_airfare_selection` for `draft_id` is 0.
- **Edge cases:** Deleting when no selection is set is idempotent, advances versions, and succeeds.

### 10. `selectionMutationsEnforceOptimisticConcurrencyOnTripAndDraft`
- **Type:** HTTP Integration Test
- **Location:** `src/test/java/app/detour/trip/AirfareSearchAndSelectionIntegrationTest.java`
- **Proves:** Stale `expectedVersion` or `expectedDraftVersion` returns 409 `VERSION_CONFLICT` without modifying persisted data.
- **Inputs/fixture:** Submit selection with stale `expectedVersion`, then stale `expectedDraftVersion`.
- **Doubles or boundary isolation:** Real H2 persistence.
- **Edge cases:** Checks error code `VERSION_CONFLICT` and `currentVersion` or `currentDraftVersion` field in response.

### 11. `unownedTripOrDraftRejectsSearchAndMutationWithNotFound`
- **Type:** HTTP Integration Test
- **Location:** `src/test/java/app/detour/trip/AirfareSearchAndSelectionIntegrationTest.java`
- **Proves:** A user cannot search or mutate airfare on a Trip or Draft owned by another user and receives 404 `RESOURCE_NOT_FOUND` with no data disclosure.
- **Inputs/fixture:** Client A owns Trip; Client B attempts `GET`, `PUT`, `DELETE` on airfare endpoints.
- **Doubles or boundary isolation:** Two distinct authenticated user principals.
- **Edge cases:** Response body matches response for non-existent UUIDs.

### 12. `invalidFlightSelectionRejectsWithValidationError`
- **Type:** HTTP Integration Test
- **Location:** `src/test/java/app/detour/trip/AirfareSearchAndSelectionIntegrationTest.java`
- **Proves:** Selecting flights with wrong dates, wrong destination, identical outbound/return IDs, or non-existent IDs returns 400 `VALIDATION_FAILED`.
- **Inputs/fixture:** Outbound and return instance IDs identical, or dates outside trip range.
- **Doubles or boundary isolation:** Real validation rules.
- **Edge cases:** Distinct check: `outbound_flight_instance_id <> return_flight_instance_id`.

## Safe Verification Commands

- **Focused:** `.\mvnw.cmd test -Dtest=AirfareSearchAndSelectionIntegrationTest`
- **Related suite:** `.\mvnw.cmd test -Dtest=TripApiIntegrationTest,AirfareFixtureIntegrationTest,AirfareSearchAndSelectionIntegrationTest`
- **Full safe suite:** `.\mvnw.cmd test`

## Optional Developer Checks

- None recorded or needed; all acceptance criteria are fully automated via `MockMvc` integration tests.

## Exit Criteria

- [x] The planned red test (`searchRoundTripAirfareReturnsAvailableCombinationsForTripDestinationAndDates`) fails with 404 before implementation.
- [x] New and updated tests in `AirfareSearchAndSelectionIntegrationTest` pass after implementation.
- [x] The broadest safe relevant repository test suite (`.\mvnw.cmd test`) passes with 0 failures.
- [x] Frontend tests (`npm.cmd test --prefix frontend`) continue passing with 0 failures.
- [x] Acceptance criteria map directly to executable test evidence.
- [x] Routine automated tests do not perform unintended live or destructive operations.
- [x] Material risks (seat capacity, deterministic ranking, complete-party pricing, optimistic locking, cross-user isolation) are covered by explicit assertions.
- [x] Any optional check is reported as nonblocking and is not represented as already performed.
