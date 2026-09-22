# Authoritative Readiness Validation, Overage Acknowledgment, and Auditable Planned Snapshots Testing Plan

## Change Summary
Ticket P05-T02 enhances the Detour backend to enforce authoritative promotion readiness, real-time catalog availability verification, budget-overage warnings and acknowledgments, an inspection endpoint (`GET /api/trips/{tripId}/drafts/{draftId}/readiness`), and immutable Planned snapshots frozen in Flyway `V16` tables with complete descriptive attributes.

## Impacted Areas and Risks
| Category | Risk | Planned evidence |
| --- | --- | --- |
| **Readiness Inspection Endpoint** | Endpoint unmapped (404), unauthenticated, or returning incorrect schema (`ready`, `blockingIssues`, `isOverBudget`, `budgetOverageCents`, `requiresOverageAcknowledgment`). | `DraftReadinessAndPlannedSnapshotIntegrationTest.readinessEndpointReturnsCompleteStatusForReadyDraftWithinBudget` |
| **Comprehensive Readiness Validation** | Missing trip constraints (destination, dates, traveler count, traveler ages, adult requirement, budget, or components) are not caught, or evaluation halts prematurely at the first failure. | `DraftReadinessAndPlannedSnapshotIntegrationTest.reportsAllMissingFieldReadinessIssuesTogether` |
| **Catalog Availability Revalidation** | Sold-out flight seats (`available_seats < travelerCount`), nightly accommodation inventory (`available_inventory < unitCount`), accommodation capacity violations, rental driver age violations (< 25), or active rental occupancy overlaps are promoted instead of blocked. | `DraftReadinessAndPlannedSnapshotIntegrationTest.rejectsPromotionWhenCatalogComponentsAreSoldOutOrUnavailable` |
| **Budget-Overage Warning & Acknowledgment** | Over-budget drafts are promoted without required client acknowledgment, or acknowledgment check bypasses canonical tally logic. | `DraftReadinessAndPlannedSnapshotIntegrationTest.enforcesBudgetOverageAcknowledgmentOnPromotion` |
| **Acknowledgment Invalidation** | Prior acknowledgment persists across subsequent draft mutations or budget edits, allowing silent promotion of altered itineraries. | `DraftReadinessAndPlannedSnapshotIntegrationTest.invalidatesOverageAcknowledgmentOnSubsequentEdits` |
| **Snapshot Schema & Data Integrity (Flyway V16)** | Migration fails, or descriptive snapshot facts (carrier, flight numbers, stops, layovers, timestamps, timezones, duration, property category, location, distance, capacity, car class) are omitted or rely on live catalog joins. | `DraftReadinessAndPlannedSnapshotIntegrationTest.persistsCompleteDescriptiveFactsIntoPlannedSnapshotV16` |
| **Immutability Invariants** | Live catalog price/schedule changes or draft deletions mutate historical Planned snapshot records; in-place mutations on Planned alternatives are permitted. | `DraftReadinessAndPlannedSnapshotIntegrationTest.plannedSnapshotIsUnaffectedByCatalogEditsOrDraftDeletion`, `rejectsInPlaceMutationOnPlannedSnapshotWithImmutableAlternative` |
| **Concurrency & Atomic Promotion** | Concurrent promotion requests on the same draft version produce duplicate Planned itineraries or leave corrupted partial records. | `DraftReadinessAndPlannedSnapshotIntegrationTest.racingPromotionRequestsHaveSingleWinnerAndNoCorruption` |
| **Multi-User Security & Isolation** | Unauthorized users can inspect readiness, promote, duplicate, or delete other users' drafts and alternatives. | `DraftReadinessAndPlannedSnapshotIntegrationTest.enforcesMultiUserIsolationAcrossAllEndpoints` |
| **Flyway Lineage Regressions** | Existing migration tests fail due to unrecorded migration `16`. | `DetourApplicationTest.startsWithFreshDetourCatalogLineageAndSeededPhaseTwoData`, `PhaseOneCatalogForwardMigrationIntegrationTest.migratesExistingDatabaseToLatestCatalogSchema` |

## Existing Coverage and Environment Constraints
- `TripApiIntegrationTest.java`: 27 integration tests covering trip creation, versioning, draft mutations, optimistic locking, and basic promotion.
- `TripPricingAndTallyIntegrationTest.java`: 11 integration tests covering canonical price tallying, draft/planned pricing, and multi-user tally isolation.
- `DetourApplicationTest.java`: Asserts applied Flyway migrations up to version `15`.
- `PhaseOneCatalogForwardMigrationIntegrationTest.java`: Asserts Flyway migration to version `15`.
- **Environment Constraints:** All tests run within the standard Maven build against an in-memory H2 database seeded by Flyway migrations `V1`–`V16`. No external network calls, mock servers, or cloud credentials are required.

## Failing Test First
- **Name:** `readinessEndpointAndOverageEnforcement_failBeforeImplementation`
- **Type:** Spring Boot Integration Test (`@SpringBootTest`, `@AutoConfigureMockMvc`)
- **Location:** `src/test/java/app/detour/trip/DraftReadinessAndPlannedSnapshotIntegrationTest.java`
- **Arrange/Act/Assert:**
  1. Register a test user and create a trip with destination `destination-sfo`, dates `2027-03-10` to `2027-03-14`, travelers `[25, 25]`, and budget `100000` ($1,000.00).
  2. Select airfare, stay, and rental car on the initial draft (canonical grand total: ~154,500 cents > 100,000 cents budget).
  3. **Act 1 (Readiness Inspection):** Send `GET /api/trips/{tripId}/drafts/{draftId}/readiness`.
     - **Assert:** Status 200 OK with `ready: false`, `isOverBudget: true`, `budgetOverageCents: 54500`, `requiresOverageAcknowledgment: true`, `blockingIssues: {}`.
  4. **Act 2 (Promotion without Acknowledgment):** Send `POST /api/trips/{tripId}/drafts/{draftId}/plan` with payload `{"expectedVersion": 3, "expectedDraftVersion": 0}` (omitting `budgetOverageAcknowledged`).
     - **Assert:** Status 400 Bad Request with code `BUDGET_OVERAGE_UNACKNOWLEDGED` and `fields` detailing `grandTotalCents`, `budgetCents`, and `budgetOverageCents`.
- **Expected pre-fix failure:**
  - Act 1 fails with HTTP 404 NOT FOUND (or 405 Method Not Allowed) because the readiness endpoint is not implemented.
  - Act 2 fails because `TripService.promoteDraft` currently does not check budget overage, returning 201 Created instead of 400 `BUDGET_OVERAGE_UNACKNOWLEDGED`.

## Tests to Add or Update

### 1. `readinessEndpointReturnsCompleteStatusForReadyDraftWithinBudget`
- **Type:** Integration Test (`MockMvc`)
- **Location:** `src/test/java/app/detour/trip/DraftReadinessAndPlannedSnapshotIntegrationTest.java`
- **Proves:** When a draft has valid selections and is within budget, `GET .../readiness` returns `ready: true`, empty `blockingIssues`, `isOverBudget: false`, `budgetOverageCents: 0`, and `requiresOverageAcknowledgment: false`.
- **Inputs/fixture:** Trip with budget $5,000 (500,000 cents), 2 travelers age 25, SFO airfare and stay selected (~$1,200).
- **Doubles or boundary isolation:** Live Spring Boot test with in-memory H2 database.
- **Edge cases:** Exact equality (`grandTotal == budgetCents`) evaluates as not over budget (`isOverBudget: false`).

### 2. `reportsAllMissingFieldReadinessIssuesTogether`
- **Type:** Integration Test (`MockMvc`)
- **Location:** `src/test/java/app/detour/trip/DraftReadinessAndPlannedSnapshotIntegrationTest.java`
- **Proves:** Promotion and readiness inspection return all structural blocking issues simultaneously under 400 `PLANNING_NOT_READY` rather than halting at the first failure.
- **Inputs/fixture:** Trip created with null ages, null budget, and no selected components.
- **Doubles or boundary isolation:** Live Spring context.
- **Edge cases:** Traveler ages contains minors only (`travelerAges: [16, 14]`), verifying `fields.adult` is reported while `fields.travelerAges` is absent; traveler count mismatch (`travelerCount: 2, travelerAges: [30]`), verifying `fields.travelerAges` is reported.

### 3. `rejectsPromotionWhenCatalogComponentsAreSoldOutOrUnavailable`
- **Type:** Integration Test (`MockMvc`)
- **Location:** `src/test/java/app/detour/trip/DraftReadinessAndPlannedSnapshotIntegrationTest.java`
- **Proves:** When catalog inventory is depleted or constraints are violated, promotion is rejected with 400 `PLANNING_NOT_READY` naming the specific component key (`airfare`, `stay`, `rental`) and descriptive reason:
  - **Airfare:** `available_seats < travelerCount` reports issue on `"airfare"`.
  - **Stay:** `available_inventory < unitCount` on any night in `[startDate, endDate)` reports issue on `"stay"`.
  - **Stay Capacity:** Party size exceeding unit capacity (`unit.guest_capacity * unitCount < travelerCount`) reports issue on `"stay"`.
  - **Rental Driver Age:** Party with no driver age 25+ (`travelerAges: [24, 22]`) reports issue on `"rental"`.
  - **Rental Dates:** Pickup or return date outside trip dates reports issue on `"rental"`.
  - **Rental Availability:** Active occupancy overlap in `rental_unit_occupancy` reports issue on `"rental"`.
- **Inputs/fixture:** Database updates setting `flight_instance.available_seats = 0`, `accommodation_nightly_inventory.available_inventory = 0`, or inserting active `rental_unit_occupancy` records.
- **Doubles or boundary isolation:** Real H2 catalog tables.
- **Edge cases:** Multiple components simultaneously sold out (e.g. both flight and hotel sold out) returns both `"airfare"` and `"stay"` issues together.

### 4. `enforcesBudgetOverageAcknowledgmentOnPromotion`
- **Type:** Integration Test (`MockMvc`)
- **Location:** `src/test/java/app/detour/trip/DraftReadinessAndPlannedSnapshotIntegrationTest.java`
- **Proves:** When itinerary grand total exceeds trip budget:
  - Promotion request with `budgetOverageAcknowledged: null` or omitted is rejected with 400 `BUDGET_OVERAGE_UNACKNOWLEDGED`.
  - Promotion request with `budgetOverageAcknowledged: false` is rejected with 400 `BUDGET_OVERAGE_UNACKNOWLEDGED`.
  - Error response body contains `fields` with `grandTotalCents`, `budgetCents`, and `budgetOverageCents`.
- **Inputs/fixture:** Trip with budget $500 (50,000 cents), selections totaling $1,200 (120,000 cents).
- **Doubles or boundary isolation:** Live Spring context.
- **Edge cases:** Zero budget (`budgetCents: 0`) with non-zero selection total requires overage acknowledgment.

### 5. `allowsPromotionWhenBudgetOverageIsExplicitlyAcknowledged`
- **Type:** Integration Test (`MockMvc`)
- **Location:** `src/test/java/app/detour/trip/DraftReadinessAndPlannedSnapshotIntegrationTest.java`
- **Proves:** Promotion succeeds with 201 Created and advances version when `budgetOverageAcknowledged: true` is explicitly provided in the request body for an over-budget draft.
- **Inputs/fixture:** Over-budget draft, promotion payload `{"expectedVersion": X, "expectedDraftVersion": Y, "budgetOverageAcknowledged": true}`.
- **Doubles or boundary isolation:** Live Spring context.
- **Edge cases:** Resulting `PlannedResponse` includes authoritative tally with `isOverBudget: true` and positive `budgetOverageCents`.

### 6. `invalidatesOverageAcknowledgmentOnSubsequentEdits`
- **Type:** Integration Test (`MockMvc`)
- **Location:** `src/test/java/app/detour/trip/DraftReadinessAndPlannedSnapshotIntegrationTest.java`
- **Proves:** Client acknowledging overage cannot reuse acknowledgment if draft selections or trip budget are edited before promotion is finalized.
- **Inputs/fixture:** Over-budget draft; user modifies stay unit count or replaces airfare; subsequent promotion with stale version is rejected with 409 `VERSION_CONFLICT`; reloaded promotion without acknowledgment is rejected with 400 `BUDGET_OVERAGE_UNACKNOWLEDGED`.
- **Doubles or boundary isolation:** Live Spring context.
- **Edge cases:** Trip budget updated via `replaceSharedDetails` increments trip version, invalidating pending promotion version expectations.

### 7. `persistsCompleteDescriptiveFactsIntoPlannedSnapshotV16`
- **Type:** Integration Test (`MockMvc` & `JdbcTemplate`)
- **Location:** `src/test/java/app/detour/trip/DraftReadinessAndPlannedSnapshotIntegrationTest.java`
- **Proves:** Promotion writes all descriptive facts into `detour_planned_airfare_snapshot`, `detour_planned_stay_snapshot`, and `detour_planned_rental_snapshot`:
  - **Airfare:** carrier name, flight numbers, stops count, layover airport/minutes, departure/arrival timestamps, timezones, duration minutes.
  - **Stay:** property category (`HOTEL`), location description, distance to city center, guest capacity, unit count, nightly prices.
  - **Rental:** vehicle class name, vehicle category, location name, pickup/return timestamps, unit identifier, daily pricing.
- **Inputs/fixture:** Draft with SFO Airfare (1 stop with layover), SFO Hotel, and SFO Rental.
- **Doubles or boundary isolation:** Query database tables directly via `JdbcTemplate` to verify raw stored values in `V16` columns.
- **Edge cases:** Direct non-stop flight has `stop_count = 0` and null layover columns.

### 8. `plannedSnapshotIsUnaffectedByCatalogEditsOrDraftDeletion`
- **Type:** Integration Test (`MockMvc` & `JdbcTemplate`)
- **Location:** `src/test/java/app/detour/trip/DraftReadinessAndPlannedSnapshotIntegrationTest.java`
- **Proves:** Planned snapshots are completely isolated from live catalog changes and draft lifecycles:
  - Modifying flight schedule flight numbers, base fares, carrier name, or accommodation property names in catalog tables does NOT alter the Planned snapshot response on `GET /api/trips/{tripId}`.
  - Deleting the source draft (`DELETE /api/trips/{tripId}/drafts/{draftId}`) does NOT delete or modify the Planned snapshot.
- **Inputs/fixture:** Promoted Planned itinerary; execute raw SQL updates against `flight_instance`, `flight_schedule`, `accommodation_property`; delete source draft; re-fetch trip.
- **Doubles or boundary isolation:** Live Spring context.
- **Edge cases:** Deleting trip owner cascade deletes both draft and planned itineraries cleanly (`ON DELETE CASCADE`).

### 9. `rejectsInPlaceMutationOnPlannedSnapshotWithImmutableAlternative`
- **Type:** Integration Test (`MockMvc`)
- **Location:** `src/test/java/app/detour/trip/DraftReadinessAndPlannedSnapshotIntegrationTest.java`
- **Proves:** Draft mutation endpoints (`PUT .../airfare`, `PUT .../stays`, `PUT .../rentals`, `DELETE .../airfare`) reject Planned itinerary identifiers with 409 `IMMUTABLE_ALTERNATIVE`.
- **Inputs/fixture:** Valid Planned alternative public ID supplied as `draftId` to mutation endpoints.
- **Doubles or boundary isolation:** Live Spring context.
- **Edge cases:** Duplicating a Planned alternative via `POST .../alternatives/{alternativeId}/duplicate` successfully creates a new mutable Draft.

### 10. `disallowsPromotionOfExpiredTrip`
- **Type:** Integration Test (`MockMvc`)
- **Location:** `src/test/java/app/detour/trip/DraftReadinessAndPlannedSnapshotIntegrationTest.java`
- **Proves:** Promoting a draft whose trip departure date has passed is rejected with 400 `ALTERNATIVE_EXPIRED`.
- **Inputs/fixture:** Fixed `Clock` bean set to a date after trip departure date.
- **Doubles or boundary isolation:** Spring test configuration with simulated clock.
- **Edge cases:** Readiness inspection endpoint returns `ready: false` with descriptive issue for expired trip.

### 11. `racingPromotionRequestsHaveSingleWinnerAndNoCorruption`
- **Type:** Concurrency Integration Test (`ExecutorService`, `CyclicBarrier`)
- **Location:** `src/test/java/app/detour/trip/DraftReadinessAndPlannedSnapshotIntegrationTest.java`
- **Proves:** Two concurrent promotion requests against the same draft version resolve with exactly one 201 Created and one 409 `VERSION_CONFLICT`. Exactly one Planned snapshot row is inserted into `detour_planned_itinerary`.
- **Inputs/fixture:** 2 parallel worker threads synchronized via `CyclicBarrier(2)` posting to `/api/trips/{tripId}/drafts/{draftId}/plan`.
- **Doubles or boundary isolation:** Real Spring transaction manager and H2 database.
- **Edge cases:** Trigger failures or unexpected exceptions trigger full transaction rollback, leaving trip version unchanged.

### 12. `enforcesMultiUserIsolationAcrossAllEndpoints`
- **Type:** Security & Multi-User Integration Test (`MockMvc`)
- **Location:** `src/test/java/app/detour/trip/DraftReadinessAndPlannedSnapshotIntegrationTest.java`
- **Proves:** An authenticated user cannot inspect readiness, promote, duplicate, or delete another user's draft or Planned alternative (returns 404 `RESOURCE_NOT_FOUND`).
- **Inputs/fixture:** User A creates trip and draft; User B sends `GET .../readiness`, `POST .../plan`, `POST .../alternatives/{altId}/duplicate`, and `DELETE .../alternatives/{altId}`.
- **Doubles or boundary isolation:** Two distinct authenticated `Client` sessions.
- **Edge cases:** User B cannot infer existence of User A's private trip IDs (strict 404 rather than 403).

### 13. `updatesExistingPhase3TestsForOverageAcknowledgment`
- **Type:** Regression Test Update
- **Location:** `src/test/java/app/detour/trip/TripApiIntegrationTest.java`
- **Proves:** Existing tests in `TripApiIntegrationTest` that created trips with `budgetCents: 0` or low budget explicitly provide `budgetOverageAcknowledged: true` or adequate budgets to ensure 100% test pass rate under Phase 5 rules.

### 14. `updatesMigrationVersionAssertionsInApplicationAndCatalogTests`
- **Type:** Migration Test Update
- **Location:** `src/test/java/app/detour/DetourApplicationTest.java`, `src/test/java/app/detour/catalog/PhaseOneCatalogForwardMigrationIntegrationTest.java`
- **Proves:** Flyway migration lineage correctly includes `"16"`.

## Safe Verification Commands
- **Focused:**
  `.\mvnw.cmd test -Dtest=DraftReadinessAndPlannedSnapshotIntegrationTest`
- **Related suite:**
  `.\mvnw.cmd test -Dtest=TripApiIntegrationTest,TripPricingAndTallyIntegrationTest,DetourApplicationTest,PhaseOneCatalogForwardMigrationIntegrationTest,DraftReadinessAndPlannedSnapshotIntegrationTest`
- **Full safe suite:**
  `.\mvnw.cmd test`
  `npm.cmd --prefix frontend test -- --run`

## Optional Developer Checks
- None.

## Exit Criteria
- [x] The planned red test fails for the intended reason before implementation, when applicable.
- [x] New and updated tests pass after implementation.
- [x] The broadest safe relevant repository test suite passes (`.\mvnw.cmd test` passes 100%).
- [x] Acceptance criteria map to executable evidence.
- [x] Routine automated tests do not perform unintended live or destructive operations.
- [x] Material risks and edge cases identified above are covered.
- [x] Any optional check is reported as nonblocking and is not represented as already performed.
