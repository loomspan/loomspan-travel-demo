# P03-T03 Code Review — Cycle 1

## Scope and Repository State

- **Ticket:** `ai/thoughts/tickets/2026-09-19-p03-t03-establish-planned-snapshot-lifecycle.md`
- **Reviewed commits/diff:** Working tree changes against `origin/main` commit `17697c3dd201033715c3f05fa7b6deb0fe731456`.
- **Files reviewed:**
  - Production code:
    - `src/main/resources/db/migration/V14__create_draft_selection_and_planned_snapshot_schema.sql`
    - `src/main/java/app/detour/trip/AlternativeResponse.java`
    - `src/main/java/app/detour/trip/DraftResponse.java`
    - `src/main/java/app/detour/trip/JdbcTripRepository.java`
    - `src/main/java/app/detour/trip/PlannedItinerary.java`
    - `src/main/java/app/detour/trip/Trip.java`
    - `src/main/java/app/detour/trip/TripController.java`
    - `src/main/java/app/detour/trip/TripDraft.java`
    - `src/main/java/app/detour/trip/TripRepository.java`
    - `src/main/java/app/detour/trip/TripRequests.java`
    - `src/main/java/app/detour/trip/TripResponse.java`
    - `src/main/java/app/detour/trip/TripService.java`
  - Tests:
    - `src/test/java/app/detour/DetourApplicationTest.java`
    - `src/test/java/app/detour/catalog/PhaseOneCatalogForwardMigrationIntegrationTest.java`
    - `src/test/java/app/detour/trip/TripApiIntegrationTest.java`
    - `src/test/java/app/detour/trip/TripApplicationRestartIntegrationTest.java`
- **Scope summary:** Introduction of typed Draft-selection and immutable Planned snapshot relational schema (V14); aggregate model supporting plural Draft and Planned alternatives (`TripAlternative`); transactional lifecycle operations for draft promotion (`POST /api/trips/{tripId}/drafts/{draftId}/plan`), source duplication (`POST /api/trips/{tripId}/alternatives/{alternativeId}/duplicate`), and gated deletion (`DELETE /api/trips/{tripId}/alternatives/{alternativeId}`); optimistic aggregate locking; full readiness issue aggregation; and catalog inventory neutrality.

## Findings

No actionable findings.

## Findings Resolved in This Context

### [P1] Rental resolution in `JdbcTripRepository.resolveRental` incorrectly rejected valid rental returns on the trip's end date
- **Location:** `src/main/java/app/detour/trip/JdbcTripRepository.java:141`
- **Scenario:** A user promotes a Draft containing a valid rental selection for a trip with dates `2027-03-10` to `2027-03-14`, where `pickupAt` is e.g. `2027-03-10T10:00:00Z` and `returnAt` is `2027-03-14T15:00:00Z`.
- **Impact:** In H2, `CAST(trip.endDate() AS TIMESTAMP WITH TIME ZONE)` casts `LocalDate` `2027-03-14` to midnight `2027-03-14 00:00:00+00`. Evaluating `returnAt <= 2027-03-14 00:00:00+00` resulted in `false` for any daytime return on the final day of the trip. This caused `resolveRental` to return `null`, erroneously treating valid rentals as structurally invalid and either dropping the rental from the snapshot or blocking promotion.
- **Evidence:** Query condition `AND ? >= CAST(? AS TIMESTAMP WITH TIME ZONE) AND ? <= CAST(? AS TIMESTAMP WITH TIME ZONE)`.
- **Fix:** Changed the comparison to compare the calendar dates: `AND CAST(? AS DATE) >= ? AND CAST(? AS DATE) <= ?`, matching `selected.pickupAt()`, `trip.startDate()`, `selected.returnAt()`, and `trip.endDate()`.

### [P2] Missing restart persistence verification for Planned snapshots and duplicated Drafts
- **Location:** `src/test/java/app/detour/trip/TripApplicationRestartIntegrationTest.java:22-70`
- **Scenario:** Application restart against file-backed H2 database.
- **Impact:** Acceptance criterion 3 and testing plan test 10 explicitly require verification that Planned snapshots and duplicated Drafts survive a process restart with stable public IDs and copied component content intact, and that subsequent catalog edits do not rewrite snapshot facts upon reloading. Step 4 had left `TripApplicationRestartIntegrationTest.java` untouched from P03-T02.
- **Evidence:** `TripApplicationRestartIntegrationTest.java` was unchanged and tested only component-empty Drafts.
- **Fix:** Extended `TripApplicationRestartIntegrationTest` to arrange a ready Draft with selections, promote to a Planned snapshot, duplicate the Planned snapshot into a new Draft, mutate live catalog prices, close the context, restart against the file database, authenticate a new session, and assert that the Planned snapshot (with unchanged copied prices) and duplicated Drafts persist across restart.

### [P3] Incomplete integration test coverage for core acceptance criteria and testing plan
- **Location:** `src/test/java/app/detour/trip/TripApiIntegrationTest.java:337-550`
- **Scenario:** Verification of individual component kinds (stay, rental), catalog and source draft mutation stability, authorization nondisclosure across new routes, transactional rollback on graph insert failure, same-version concurrency races, and catalog inventory neutrality.
- **Impact:** Step 4 added only two basic tests, omitting coverage for:
  - Promoting stay-only and rental-only selections (testing plan test 3).
  - Snapshot stability after catalog display and price mutations and source Draft mutations (testing plan test 4).
  - Duplicating Draft and Planned sources into new mutable Drafts with reset versions (testing plan test 5).
  - Cross-user nondisclosure on `/plan`, `/alternatives/{id}/duplicate`, and `/alternatives/{id}` DELETE (testing plan test 7).
  - Rollback on snapshot/copy insert failure (testing plan test 8).
  - Racing promotion and duplication under `CyclicBarrier` (acceptance criterion 6 / testing plan test 9).
  - Negative SQL assertions verifying catalog inventory (`available_seats`, `available_inventory`, `rental_unit_occupancy`) is unchanged across promotion, duplication, and deletion (acceptance criteria 4 and 7 / testing plan test 11).
- **Evidence:** Omission of tests 3, 4, 5, 7, 8, 9, 11 from `TripApiIntegrationTest.java`.
- **Fix:** Added comprehensive test methods to `TripApiIntegrationTest.java` covering all aforementioned scenarios and assertions.

## Acceptance-Criteria and Plan Conformance

| Criterion/decision | Code evidence | Test evidence | Result: implemented/partial/missing/safe deviation |
| --- | --- | --- | --- |
| Ready promotion and complete readiness issue aggregation | `TripService.promoteDraft`, `TripService.readinessIssues`, `JdbcTripRepository.resolveSelectionsForPromotion` | `TripApiIntegrationTest.reportsAllKnownPromotionReadinessIssuesTogether`, `promotesAReadyDraftAsAnIndependentImmutablePlannedSnapshot` | implemented |
| Planned snapshot retains copied descriptions/prices after Draft/catalog changes | `V14` snapshot tables (`detour_planned_*`), `JdbcTripRepository.loadPlannedSelections` | `TripApiIntegrationTest.plannedSnapshotReadsCopiedContentAfterDraftAndCatalogMutation`, `promotesAReadyDraftAsAnIndependentImmutablePlannedSnapshot` | implemented |
| Draft and Planned sources can be duplicated into new mutable Drafts; restart verifiable | `TripService.duplicateAlternative`, `JdbcTripRepository.insertDraftCopy` | `TripApiIntegrationTest.duplicatesDraftAndPlannedSourcesIntoIndependentDraftCopies`, `TripApplicationRestartIntegrationTest.persistsTripAndDraftAcrossApplicationRestart` | implemented |
| Planned update attempts rejected; confirmation-gated deletion leaves siblings untouched and no inventory effects | `TripService.ownedDraft` (409 IMMUTABLE_ALTERNATIVE), `TripService.deleteAlternative`, `JdbcTripRepository.deletePlanned` | `TripApiIntegrationTest.rejectsPlannedAsDraftMutationAndGatesItsDeletion`, `plannedLifecycleLeavesCatalogInventoryUntouched` | implemented |
| Multiple Planned alternatives coexist; owner-scoped tests prevent cross-user leakage | `JdbcTripRepository.findByPublicIdAndOwnerUserId`, `TripService.ownedTrip`, `TripService.ownedAlternative` | `TripApiIntegrationTest.alternativeLifecycleMutationsDoNotRevealForeignTargets` | implemented |
| Transactional and concurrency coverage proves race safety and rollback | `TripService` `@Transactional`, `JdbcTripRepository.advanceVersion`, `advanceVersionForDraft` | `TripApiIntegrationTest.sameVersionPromotionAndDuplicateRacesHaveOneCompleteWinner`, `rollsBackPromotionAndDuplicateGraphWritesOnFailure` | implemented |
| Phase 5 pricing/readiness and Phase 6 booking/cancellation behavior not prematurely introduced; no Canceled status | `AlternativeResponse` (no total, no overage), `TripAlternative` sealed interface (DRAFT, PLANNED only) | `AlternativeResponse.java:8-19`, `TripApiIntegrationTest.promotesEachStructurallyValidSelectionKindWithoutCanonicalPricing`, `plannedLifecycleLeavesCatalogInventoryUntouched` | implemented |

## Active Project Guardrails

- `ai/thoughts/design-lens.md`: None recorded.

## Open Questions and Assumptions

- None.

## Verification Results

- PASS — `./mvnw.cmd test -DskipFrontend=true` — 43 tests passed across all catalog, identity, and trip suites.
- PASS — `./mvnw.cmd test -DskipFrontend=true "-Dtest=TripApiIntegrationTest,TripApplicationRestartIntegrationTest"` — 21 tests passed verifying lifecycle, immutability, authorization, concurrency races, rollback, inventory neutrality, and restart persistence.
- PASS — `./mvnw.cmd package` — Full Maven build packaging backend JAR and building frontend assets succeeded with 0 errors.

## Residual Risks and Optional Developer Checks

- Local development databases with pre-V14 schema will need explicit reset using `./scripts/reset-detour.ps1 -ConfirmReset` if running standalone against local `data/detour.mv.db`.
- Optional developer check: start the packaged application on an isolated disposable H2 URL, create a trip with selections, promote, duplicate, and delete alternatives, and inspect that catalog inventory tables remain unchanged.

## Disposition

- `fixes-applied`
