# P03-T03 Code Review — Cycle 2

## Scope and Repository State

- Ticket: `ai/thoughts/tickets/2026-09-19-p03-t03-establish-planned-snapshot-lifecycle.md`
- Profile: `full` (Full 5-Step Pipeline)
- Review cycle: 2
- Working copy review against commit `17697c3dd201033715c3f05fa7b6deb0fe731456` (`clean up after P03-T02`):
  - Modified production files:
    - `src/main/java/app/detour/trip/Trip.java`
    - `src/main/java/app/detour/trip/TripDraft.java`
    - `src/main/java/app/detour/trip/TripResponse.java`
    - `src/main/java/app/detour/trip/DraftResponse.java`
    - `src/main/java/app/detour/trip/TripRequests.java`
    - `src/main/java/app/detour/trip/TripRepository.java`
    - `src/main/java/app/detour/trip/JdbcTripRepository.java`
    - `src/main/java/app/detour/trip/TripService.java`
    - `src/main/java/app/detour/trip/TripController.java`
  - Added production files:
    - `src/main/java/app/detour/trip/AlternativeResponse.java`
    - `src/main/java/app/detour/trip/PlannedItinerary.java`
    - `src/main/resources/db/migration/V14__create_draft_selection_and_planned_snapshot_schema.sql`
  - Modified test files:
    - `src/test/java/app/detour/DetourApplicationTest.java`
    - `src/test/java/app/detour/catalog/PhaseOneCatalogForwardMigrationIntegrationTest.java`
    - `src/test/java/app/detour/trip/TripApiIntegrationTest.java`
    - `src/test/java/app/detour/trip/TripApplicationRestartIntegrationTest.java`

## Findings

No actionable findings remaining.

## Findings Resolved in This Context

### [P2] Assert adult readiness rejection for minor-only traveler party
- Location: `src/test/java/app/detour/trip/TripApiIntegrationTest.java:336-345`
- Scenario: When promoting a Draft under a Trip where traveler ages are specified but all travelers are minors (e.g. `[17, 12]`) and budget/components are missing, promotion must reject with `400 PLANNING_NOT_READY` containing `fields.adult`, `fields.budgetCents`, and `fields.components`.
- Impact: Closed verification gap for Acceptance Criterion 1, ensuring the adult requirement gate is proven with automated test assertions.
- Evidence: Grep search confirmed `adult` was not previously asserted anywhere in tests; `reportsAllKnownPromotionReadinessIssuesTogether` only tested `travelerAges: null`.
- Fix: Extended `reportsAllKnownPromotionReadinessIssuesTogether` in `TripApiIntegrationTest.java` with a minor-only party case, asserting `fields.adult` is present alongside `fields.budgetCents` and `fields.components` while `fields.travelerAges` is absent.

### [P2] Assert coexistence and sibling preservation of multiple Planned alternatives under one Trip
- Location: `src/test/java/app/detour/trip/TripApiIntegrationTest.java:319-335`
- Scenario: Acceptance Criterion 5 requires that multiple Planned alternatives can coexist under one Trip, and deletion of one Planned alternative must leave sibling Planned alternatives intact.
- Impact: Closed verification gap for Acceptance Criterion 5 and testing plan item 6.
- Evidence: Prior test flows in `TripApiIntegrationTest.java` only created a single Planned alternative on any Trip and tested deletion from 1 to 0 planned alternatives.
- Fix: Extended `promotesAReadyDraftAsAnIndependentImmutablePlannedSnapshot` in `TripApiIntegrationTest.java` to duplicate the planned alternative to a second draft, promote the second draft to establish multiple concurrent Planned snapshots (`$.planned.length().value(2)`), and then delete the first planned alternative to verify that `$.planned.length().value(1)` preserves the sibling planned snapshot and existing drafts intact.

## Acceptance-Criteria and Plan Conformance

| Criterion/decision | Code evidence | Test evidence | Result: implemented/partial/missing/safe deviation |
| --- | --- | --- | --- |
| Ready promotion and complete readiness issues | `TripService.promoteDraft`, `readinessIssues`, `resolveSelectionsForPromotion`, `TripRequests.promotion` | `TripApiIntegrationTest.promotesAReadyDraftAsAnIndependentImmutablePlannedSnapshot`, `reportsAllKnownPromotionReadinessIssuesTogether` | implemented |
| Planned snapshot retains copied component descriptions and prices after source Draft, other Draft, or referenced catalog display/price data changes | `V14__create_draft_selection_and_planned_snapshot_schema.sql`, `JdbcTripRepository.insertPlanned`, `loadPlannedSelections` | `TripApiIntegrationTest.plannedSnapshotReadsCopiedContentAfterDraftAndCatalogMutation`, `TripApplicationRestartIntegrationTest` | implemented |
| Draft and Planned sources can each be explicitly duplicated into a new mutable Draft without changing source; restart persistent | `TripService.duplicateAlternative`, `JdbcTripRepository.insertDraftCopy`, `TripRequests.alternativeDuplicate` | `TripApiIntegrationTest.duplicatesDraftAndPlannedSourcesIntoIndependentDraftCopies`, `TripApplicationRestartIntegrationTest.persistsTripAndDraftAcrossApplicationRestart` | implemented |
| Planned update attempts rejected; deleting one Draft or confirmation-gated Planned alternative leaves every other alternative unchanged and has no inventory effect | `TripService.ownedDraft` (409 `IMMUTABLE_ALTERNATIVE`), `TripService.deleteAlternative`, `JdbcTripRepository.deletePlanned`, `deleteDraft` | `TripApiIntegrationTest.promotesAReadyDraftAsAnIndependentImmutablePlannedSnapshot`, `plannedLifecycleLeavesCatalogInventoryUntouched`, `alternativeLifecycleMutationsDoNotRevealForeignTargets` | implemented |
| Multiple Planned alternatives can coexist under one Trip; owner-scoped tests prevent cross-user promotion, duplication, reads, deletion without disclosing protected data | `detour_planned_itinerary` schema, `JdbcTripRepository.loadTrip`, `TripService.ownedTrip`, `ownedDraft`, `ownedAlternative` | `TripApiIntegrationTest.promotesAReadyDraftAsAnIndependentImmutablePlannedSnapshot`, `alternativeLifecycleMutationsDoNotRevealForeignTargets` | implemented |
| Transactional and concurrency coverage proves repeated/racing promotion or duplication does not create unintended duplicate snapshots or partially copied content | `TripService.promoteDraft`, `duplicateAlternative`, `@Transactional`, optimistic version checking `advanceVersion` / `advanceVersionForDraft` | `TripApiIntegrationTest.sameVersionPromotionAndDuplicateRacesHaveOneCompleteWinner`, `rollsBackPromotionAndDuplicateGraphWritesOnFailure` | implemented |
| Phase 5 pricing/readiness and Phase 6 booking/cancellation behavior not prematurely introduced; no Canceled Booking modeled as itinerary status | No total calculation, no availability decrement/reservation, no booking tables, `TripAlternative` sealed interface separating lifecycle kinds without status column | `TripApiIntegrationTest.plannedLifecycleLeavesCatalogInventoryUntouched`, schema inspection, response contracts | implemented |

## Active Project Guardrails

- `ai/thoughts/design-lens.md`: None recorded.

## Open Questions and Assumptions

- None.

## Verification Results

- PASS — `./mvnw.cmd test -DskipFrontend=true -Dtest=TripApiIntegrationTest` — 20/20 tests passed, including adult readiness rejection and multiple Planned coexistence assertions.
- PASS — `./mvnw.cmd test -DskipFrontend=true` — 43/43 tests passed across all catalog, identity, and trip integration suites.
- PASS — `./mvnw.cmd package` — Full build succeeded (all 43 backend tests passed, frontend build packaging verified, runnable JAR produced).

## Residual Risks and Optional Developer Checks

- None blocking. Local development databases using earlier schemas should be reset with `./scripts/reset-detour.ps1 -ConfirmReset` before running the local development server against new schema V14.

## Disposition

- `fixes-applied`
