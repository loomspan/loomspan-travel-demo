# P03-T04 Protected Shared-Detail Revisions and Selective Trip Duplication Testing Plan

## Change Summary
P03-T04 protects shared-detail updates and delivers active-Trip selective duplication from Planned snapshot sources:
- In-place shared-detail revisions (`PUT /api/trips/{tripId}`) are restricted to Trips with no Planned alternatives. Traveler changes reprice and revalidate all Draft selections for available seat capacity, stay room count and capacity, and rental driver age eligibility (25+). Destination or date changes remove incompatible selections. An immediate structured `revisionSummary` names all removals and adjustments with user-readable reasons.
- Trips containing Planned alternatives reject in-place travel detail mutations with `409 IMMUTABLE_TRIP`. In-place budget-only updates are permitted and update budget presentation without mutating selections or Planned snapshots.
- Active-Trip selective duplication (`POST /api/trips/{tripId}/duplicate` and `/revisions`) atomically creates a new Trip aggregate from an explicit list of Planned snapshot sources, converting each into a Draft, revalidating selections, and returning a post-duplication `revisionSummary` while preserving source Trips and snapshots intact.

## Impacted Areas and Risks
| Category | Risk | Planned evidence |
| --- | --- | --- |
| In-place Draft revalidation | Stale, incompatible, or over-capacity selections could remain attached to Drafts after destination/date/traveler changes. | Integration test verifying catalog-backed revalidation, room-count adjustment, and component pruning with structured explanations. |
| Immutability of Planned Trips | Trips with Planned alternatives could be silently corrupted by in-place shared-detail edits. | Integration test asserting `409 IMMUTABLE_TRIP` when attempting destination/date/traveler updates on a Trip with Planned snapshots. |
| Budget-only updates | Budget edits on Planned Trips could be improperly blocked or could inadvertently alter component selections. | Integration test proving budget update succeeds in place while Planned snapshots and Draft selections remain untouched. |
| Selective Duplication atomicity & source validation | Invalid, duplicate, or foreign source IDs could produce partial Trips or leak private alternative IDs. | Integration tests verifying atomic rollback, 400 for empty/duplicate sources, and 404 nondisclosure for foreign/cross-trip/Draft IDs. |
| Concurrency & Versioning | Concurrent updates or duplication from stale Trip versions could cause lost writes or race conditions. | WebMvc integration test verifying `409 VERSION_CONFLICT` when `expectedVersion` is stale. |
| Restart Persistence | Converted Drafts or revised shared details could fail to persist across application restarts. | `TripApplicationRestartIntegrationTest` restarting against an H2 file database and verifying persisted state. |

## Existing Coverage and Environment Constraints
- `src/test/java/app/detour/trip/TripApiIntegrationTest.java`: 23 integration tests covering aggregate creation, multi-Draft lifecycle, optimistic locking, Draft promotion, alternative duplication, and catalog fixture integrity.
- `src/test/java/app/detour/trip/TripApplicationRestartIntegrationTest.java`: Verifies full Trip aggregate and alternative persistence across JVM restart.
- Test commands use Maven wrapper (`.\mvnw.cmd`) under Windows Powershell.
- All tests execute against embedded H2 with Flyway migrations V1–V14 and authoritative March 2027 catalog fixtures.

## Failing Test First
- **Name**: `rejectsInPlaceTravelDetailEditsWhenPlannedAlternativesExist`
- **Type**: WebMvc Integration Test
- **Location**: `src/test/java/app/detour/trip/TripApiIntegrationTest.java`
- **Arrange/Act/Assert**:
  - *Arrange*: Register user, create Trip to SFO for March 10–14, 2027 with travelers `[25, 30]`, insert airfare selection, promote Draft to Planned itinerary.
  - *Act*: Send `PUT /api/trips/{tripId}` attempting to update destination to `destination-muc`.
  - *Assert*: Expect HTTP `409 Conflict` with `code: "IMMUTABLE_TRIP"`.
- **Expected pre-fix failure**: `TripService.replaceSharedDetails` currently executes the update unconditionally and returns HTTP `200 OK` with `destinationKey: "destination-muc"`, failing the assertion.

## Tests to Add or Update

### 1. `rejectsInPlaceTravelDetailEditsWhenPlannedAlternativesExist`
- **Type**: WebMvc Integration Test
- **Location**: `src/test/java/app/detour/trip/TripApiIntegrationTest.java`
- **Proves**: A Trip containing at least one Planned alternative rejects in-place destination, date, or traveler edits with `409 IMMUTABLE_TRIP`.
- **Inputs/fixture**: SFO Trip (`2027-03-10` to `2027-03-14`, party of 2, budget 50,000 cents), promoted to Planned; update body with `destinationKey: "destination-muc"`.
- **Doubles or boundary isolation**: Real H2 database and Spring Security context; no mocks.
- **Edge cases**: Verifies that changing destination, changing dates, or changing traveler count/ages are each individually rejected when Planned alternatives exist.

### 2. `budgetOnlyUpdatePreservesPlannedSnapshotsAndEnforcesConcurrency`
- **Type**: WebMvc Integration Test
- **Location**: `src/test/java/app/detour/trip/TripApiIntegrationTest.java`
- **Proves**: A budget-only change on a Trip with Planned alternatives is permitted in place, updates `budgetCents`, advances Trip version, preserves Planned snapshots and Draft selections unchanged, and enforces optimistic concurrency.
- **Inputs/fixture**: SFO Trip with Planned itinerary and Draft selection; update body with identical travel details and `budgetCents: 75000`.
- **Doubles or boundary isolation**: Real H2 database and Spring Security context.
- **Edge cases**: Distinct zero budget (`budgetCents: 0`) vs absent budget (`budgetCents: null`); stale `expectedVersion` returns `409 VERSION_CONFLICT`.

### 3. `inPlaceTravelerEditRevalidatesCapacityRoomCountAndEligibility`
- **Type**: WebMvc Integration Test
- **Location**: `src/test/java/app/detour/trip/TripApiIntegrationTest.java`
- **Proves**: For a Trip without Planned alternatives, changing traveler count from 2 to 3 updates room count on a hotel Draft selection from 1 to 2, recalculates total stay price, removes a rental car selection when updated ages omit adult >= 25, and returns structured `revisionSummary` detailing all adjustments and removals with reasons.
- **Inputs/fixture**: SFO Trip with Draft containing stay and rental selections; update body with `travelerCount: 3`, `travelerAges: [12, 14, 16]`.
- **Doubles or boundary isolation**: Real H2 database and authoritative catalog tables.
- **Edge cases**: Traveler count requiring additional room; adult age threshold for rental cars (removal when no adult >= 25 is present).

### 4. `inPlaceDestinationAndDateEditRemovesIncompatibleComponents`
- **Type**: WebMvc Integration Test
- **Location**: `src/test/java/app/detour/trip/TripApiIntegrationTest.java`
- **Proves**: For a Trip without Planned alternatives, changing destination from SFO to MUC or shifting dates removes incompatible airfare, stay, and rental selections, leaving Draft component-empty and returning structured reasons in `revisionSummary.removals`.
- **Inputs/fixture**: SFO Trip with airfare, stay, and rental selections; update body with `destinationKey: "destination-muc"`.
- **Doubles or boundary isolation**: Real H2 database.
- **Edge cases**: Draft selection tables have rows deleted; no dangling foreign keys or stale totals.

### 5. `selectivelyDuplicatesActiveTripFromPlannedSourcesIntoNewDrafts`
- **Type**: WebMvc Integration Test
- **Location**: `src/test/java/app/detour/trip/TripApiIntegrationTest.java`
- **Proves**: `POST /api/trips/{tripId}/duplicate` creates a new Trip aggregate with revised details, converts each selected Planned snapshot into exactly one Draft in the new Trip (version 0), revalidates selections, returns post-duplication `revisionSummary`, and preserves source Trip and snapshots unchanged.
- **Inputs/fixture**: Source Trip with 2 Planned snapshots; request body with `expectedVersion`, revised dates, and `sourcePlannedItineraryIds` selecting both Planned snapshots.
- **Doubles or boundary isolation**: Real H2 database.
- **Edge cases**: Source Trip version unchanged; source Planned snapshots unchanged; new Trip has version 0 and exactly 2 Drafts (no extra blank Draft).

### 6. `selectiveDuplicationPrunesIncompatibleComponentsWithStructuredSummary`
- **Type**: WebMvc Integration Test
- **Location**: `src/test/java/app/detour/trip/TripApiIntegrationTest.java`
- **Proves**: When selectively duplicating to different dates, incompatible components in copied Planned snapshots are removed with user-readable explanations in `revisionSummary`, while compatible components are retained.
- **Inputs/fixture**: SFO Planned snapshot containing airfare and stay; duplicate request with shifted dates where stay is incompatible.
- **Doubles or boundary isolation**: Real H2 database.
- **Edge cases**: Complete incompatibility pruning down to component-empty Draft.

### 7. `selectiveDuplicationRejectsInvalidOrUnauthorizedSourcesWithoutDisclosure`
- **Type**: WebMvc Integration Test
- **Location**: `src/test/java/app/detour/trip/TripApiIntegrationTest.java`
- **Proves**: Duplication fails atomically without creating partial aggregate when source list is empty (400), contains duplicates (400), contains a Draft ID (404), contains a cross-trip ID (404), or contains a foreign user's Planned ID (404).
- **Inputs/fixture**: Two users; attempts to duplicate using cross-user and invalid source lists.
- **Doubles or boundary isolation**: Real H2 database with multiple user registrations.
- **Edge cases**: Nondisclosure assertion ensuring error message for foreign ID matches error message for random unknown UUID.

### 8. `selectiveDuplicationEnforcesOptimisticConcurrency`
- **Type**: WebMvc Integration Test
- **Location**: `src/test/java/app/detour/trip/TripApiIntegrationTest.java`
- **Proves**: Duplication request with stale `expectedVersion` is rejected with `409 VERSION_CONFLICT` and `fields.currentVersion`.
- **Inputs/fixture**: Source Trip at version 2; duplication request with `expectedVersion: 1`.
- **Doubles or boundary isolation**: Real H2 database.
- **Edge cases**: Source Trip remains unchanged; no partial aggregate created.

### 9. `persistsSelectiveDuplicationAcrossRestart`
- **Type**: Application Restart Integration Test
- **Location**: `src/test/java/app/detour/trip/TripApplicationRestartIntegrationTest.java`
- **Proves**: A Trip created via selective duplication from Planned snapshots and its converted Drafts persist correctly across an application shutdown and restart against the same database file.
- **Inputs/fixture**: File-backed H2 database; user registration, Trip creation, Planned promotion, selective duplication, catalog mutation, application restart, login, and Trip verification.
- **Doubles or boundary isolation**: Full Spring Boot application lifecycle restart.
- **Edge cases**: Verifies that converted Draft selections and their resolved details persist across restart without reloading live catalog modifications.

## Safe Verification Commands
- Focused: `.\mvnw.cmd test -Dtest=TripApiIntegrationTest#rejectsInPlaceTravelDetailEditsWhenPlannedAlternativesExist`
- Related suite: `.\mvnw.cmd test -Dtest=TripApiIntegrationTest,TripApplicationRestartIntegrationTest`
- Full safe suite: `.\mvnw.cmd test`

## Optional Developer Checks
- None.

## Exit Criteria
- [x] The planned red test fails for the intended reason before implementation, when applicable.
- [x] New and updated tests pass after implementation.
- [x] The broadest safe relevant repository test suite passes (`.\mvnw.cmd test`).
- [x] Acceptance criteria map to executable evidence.
- [x] Routine automated tests do not perform unintended live or destructive operations.
- [x] Material risks and edge cases identified above are covered.
- [x] Any optional check is reported as nonblocking and is not represented as already performed.
