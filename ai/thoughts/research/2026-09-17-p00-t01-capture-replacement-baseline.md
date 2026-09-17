---
date: 2026-09-17
repository: loomspan-travel-demo
branch: main
commit: 5fb7d2b45209c9034bf40ce40e6c580618abc5c8
ticket: ai/thoughts/tickets/2026-09-17-p00-t01-capture-replacement-baseline.md
tags: [phase-0, baseline, wayfarer, tests, build]
---

# P00-T01 Capture Replacement Baseline Research

## Research Question

What current repository state, supported verification commands, and automated-test dispositions must a durable pre-DeTour replacement baseline record capture?

## Summary

The checkout is `main` at `5fb7d2b45209c9034bf40ce40e6c580618abc5c8` and was clean at 2026-09-17T13:27:18-07:00: no staged, unstaged, or untracked files were reported. The historically noted `IntakeService.java` and `TripStore.java` work is present in the HEAD commit rather than as a current worktree modification; `git show --stat HEAD` records both files in that commit, while current `git status --porcelain=v1 --untracked-files=all` and both diff-name checks are empty.

Wayfarer is a Spring Boot/React/H2 application whose assessment and conversational-intake paths call Loomspan `SkillTemplate`; backend inventory, transaction, idempotency, and stale-state protections are separate Java/H2 behavior. The 41 current JUnit tests divide into six reusable transactional/error-contract tests, 19 fixed Wayfarer-scenario tests to replace, and 16 Loomspan/model-coupled tests to remove; there are no frontend test files or frontend test script.

## Repository State

- Repository root: `C:\code\loomspan-travel-demo`
- Captured at: `2026-09-17T13:27:18.3256587-07:00`
- Branch: `main` (`main...origin/main`)
- Commit: `5fb7d2b45209c9034bf40ce40e6c580618abc5c8` (`planning for future roadmap complete; phase 0 created`)
- Starting tracked modifications: none (`git diff --name-only` and `git diff --cached --name-only` were empty).
- Starting untracked files: none (`git status --porcelain=v1 --untracked-files=all` was empty).
- Known-file refresh: `src/main/java/demo/wayfarer/IntakeService.java` and `src/main/java/demo/wayfarer/TripStore.java` had no current modifications. `ai/thoughts/phases/CONTINUATION.md:45-48` describes them as user-owned changes in an earlier check; the current commit stat includes both files, which explains why that historical statement does not match the clean current worktree.
- Ignored local/generated paths observed: `data/`, `frontend/dist/`, `frontend/node_modules/`, `frontend/tsconfig.tsbuildinfo`, and `target/`. They are not starting tracked or untracked worktree changes.

## Current Behavior and Data Flow

`WayfarerApplication` starts the Spring application. `ApiController` exposes `/api` endpoints for fixed-scenario trip creation/revision, asynchronous assessments, booking, booking exchange, supplier-style return cancellation, and conversational intake (`src/main/java/demo/wayfarer/ApiController.java:10-41`).

An assessment enters `AssessmentService.start`, which persists an assessment and invokes Loomspan's `planTrip` asynchronously; failures are persisted through `TripStore` (`src/main/java/demo/wayfarer/AssessmentService.java:17-25`). The YAML manifests define `planTrip`, `planTransport`, `assessStay`, `assessTripLogistics`, and `interpretTripChange` on the configured Loomspan planner (`src/main/resources/skills/planTrip.yml:2-21`, `src/main/resources/skills/interpretTripChange.yml:2-169`). Java `TravelSkills` reads eligible rail/flight/hotel data and evaluates a snapshot, recording receipts in `TripStore` (`src/main/java/demo/wayfarer/TravelSkills.java:10-31`, `src/main/java/demo/wayfarer/TripStore.java:144-162`). `TripCalculator` owns validation, eligibility, deterministic candidate evaluation, result validation, and recovery suggestions (`src/main/java/demo/wayfarer/TripCalculator.java:11-141`).

`TripStore` persists fixed Wayfarer trips, assessments, bookings, exchanges, catalog receipts, and service cancellations. Its transactional booking and exchange methods validate idempotency, current revision/catalog state, quote equality, and inventory adjustments (`src/main/java/demo/wayfarer/TripStore.java:58-303`). `IntakeService` invokes the `interpretTripChange` model skill, persists conversational drafts, and confirms a proposed revision transactionally (`src/main/java/demo/wayfarer/IntakeService.java:14-190`).

The React client calls `/api`; Vite proxies that path to the local Spring server (`frontend/src/main.tsx:10-82`, `frontend/src/Conversation.tsx:6`, `frontend/vite.config.ts:1-3`). Maven runs `npm ci` and `npm run build` during `generate-resources`, then copies `frontend/dist` into the packaged application (`pom.xml:71-128`). The active configuration binds locally, stores H2 data in `data/wayfarer`, and configures Loomspan/OpenAI-compatible model access (`src/main/resources/application.yml:1-46`).

## Key Components

- `pom.xml:14-18` — Java 21, Loomspan/Spring AI versions, and `skipFrontend` build property.
- `pom.xml:31-62` — Loomspan, Spring Web MVC/JDBC/Flyway/H2, and Spring test dependencies.
- `pom.xml:71-128` — frontend install/build and static-resource packaging executions.
- `frontend/package.json:1` — only `dev` and `build` scripts; no frontend test script is configured.
- `src/main/resources/application.yml:7-8` — default persistent H2 database location; `:17-46` is the Loomspan/model configuration boundary.
- `src/main/java/demo/wayfarer/ApiController.java:15-27` — current HTTP contract and Wayfarer-only operational endpoints.
- `src/main/java/demo/wayfarer/TripStore.java:194-303` — assessment lifecycle plus transactional booking/exchange persistence.
- `src/main/java/demo/wayfarer/TripCalculator.java:37-141` — catalog eligibility, whole-trip calculation, model-result validation, and fixed recovery suggestion logic.
- `src/main/java/demo/wayfarer/IntakeService.java:60-190` — model-backed natural-language change draft and confirmation flow.
- `src/test/java/demo/wayfarer/TripApplicationTest.java:16-305` — ordinary in-memory H2 unit/integration coverage of calculator, inventory, booking, exchanges, and disruption recovery.
- `src/test/java/demo/wayfarer/HttpFlowTest.java:21-100` — in-process HTTP integration tests with mocked `SkillTemplate`.
- `src/test/java/demo/wayfarer/LivePlanningTest.java:16-99` and `LiveIntakeTest.java:10-39` — provider-backed tests gated by `WAYFARER_LIVE_TEST=true`.

## Affected Areas

| Area | Current behavior and evidence |
| --- | --- |
| Starting-worktree preservation | The starting state is clean. The required named files are not current local changes; their historical user-work status is documented in `ai/thoughts/phases/CONTINUATION.md:45-48`, and both are included in `HEAD` according to `git show --stat HEAD`. |
| Backend test command | README documents `./mvnw.cmd test -DskipFrontend=true` for ordinary tests (`README.md:168-181`). Maven's configured test dependency is Spring Boot Test (`pom.xml:59-62`). |
| Frontend tests | No `test` script is present in `frontend/package.json:1`, and no tracked frontend test/spec file was found. The baseline must record this check as unavailable/not run rather than infer an alternate runner. |
| Frontend production build | `npm run build --prefix frontend` is documented (`README.md:168-172`) and maps to `tsc -b && vite build` (`frontend/package.json:1`). |
| Packaged application build | `./mvnw.cmd package` is documented as the packaging command (`README.md:15-29`). It runs frontend `npm ci` and `npm run build` unless `skipFrontend` is set, then copies the built assets (`pom.xml:71-128`). |
| Verification environment | Node/npm is installed and `frontend/node_modules` exists. `java` was not discoverable on PATH; `./mvnw.cmd -v` therefore failed with `Cannot start maven from wrapper`. This is an environment observation, not a repository test result. |
| Live provider tests | `LivePlanningTest` and `LiveIntakeTest` are disabled unless `WAYFARER_LIVE_TEST=true` (`src/test/java/demo/wayfarer/LivePlanningTest.java:16`, `src/test/java/demo/wayfarer/LiveIntakeTest.java:10`). README says they require a configured provider and may transmit fictional trip data (`README.md:173-181`); research did not enable them. |

## Existing Tests and Fixtures

All five tracked test files use Spring Boot and isolated in-memory H2 URLs. `TripApplicationTest` and `IntakeServiceTest` explicitly delete/reseed the Wayfarer tables in `@BeforeEach` (`TripApplicationTest.java:24-27`, `IntakeServiceTest.java:28-31`); `HttpFlowTest` uses a random local port (`HttpFlowTest.java:21-32`). There are 41 `@Test` methods in total. The following disposition inventory covers every method by named cohesive group.

| Disposition | Tests | Reason |
| --- | --- | --- |
| Reusable behavior worth preserving | `TripApplicationTest`: `bookingReservesBothLegsAndNightsAndRetriesAreIdempotent`, `acceptedBookingConsumesFourFiniteRows`, `racingBookingsNeverPartiallyReserve`, `changedPricesRejectBookingWithoutStockWrites`, `historicalAndForeignProposalsCannotBeBooked` (`:85-121`) | These protect general inventory reservation, atomicity, idempotency, stale quote/state rejection, and cross-trip isolation. Their fixture and contract details are Wayfarer-specific, but their behavioral properties align with the roadmap's application-owned, transactional inventory requirements. |
| Reusable behavior worth preserving | `HttpFlowTest.malformedJsonAndUnknownIdsReturnUsefulHttpStatuses` (`:78-80`) | It protects generic malformed-input and missing-resource HTTP error behavior. Current endpoints and response shapes remain Wayfarer-specific. |
| Wayfarer-scenario behavior to replace | `TripApplicationTest`: `allFixtureCombinationsHaveCorrectPartyPricingAndTiming`, `budgetFailureUsesCheapestOtherwiseFeasibleTrip`, `exactBudgetAndArrivalBoundariesAreInclusive`, `railOnlyIsSelectiveAndCoverageRejectsOmissionsDuplicatesAndForeignIds`, `missingOneHotelNightExcludesTheHotelAndSnapshotsAreImmutable`, `unsupportedRequestsFailBeforePersistence` (`:40-74`, `:129-131`) | These assert the Boston–New York, two-adult, rail/air/hotel fixture, exact prices, dates, and rules that the roadmap declares non-migratable. |
| Wayfarer-scenario behavior to replace | `TripApplicationTest`: `changePlanningCreditsOnlyOwnedInventoryAndPreservesHotelWithoutReleasingStock`, `exchangeAtomicallyReplacesInventoryAndPersistsHistoryWithIdempotentReplay`, `failedExchangeRollsBackReleasedInventoryAndRetainsOriginalBooking`, `staleAndForeignExchangeProposalsCannotReplaceBooking`, `concurrentExchangeAcceptancesHaveOneWinner`, `exchangeAndNewBookingCompetingForLastSeatsHaveOneWinner`, `infeasibleChangeAndMissingHeldNightNeverReleaseCurrentBooking`, `cancellationIsIdempotentAndRecoveryRetainsUnaffectedReservations`, `cancellationAffectsAllBookingsAndFencesEarlierProposals`, `railOnlyRecoverySuggestsExplicitModeAndBudgetChangesWithoutApplyingThem`, `failedRecoveryDoesNotReleaseHotelOrOutboundAndNoInventoryCannotBeFixedByPreferences`, `cancellationDuringExchangeCannotLeaveAHealthyBookingOnCanceledService` (`:155-305`) | These cover the old booking-change, supplier disruption, and recovery scenario. The DeTour roadmap uses a different trip/itinerary and cancellation model and explicitly defers exchanges/disruptions/recovery. |
| Wayfarer-scenario behavior to replace | `HttpFlowTest.httpRequestAssessmentBookingAndReload` (`:34-70`) | It exercises the fixed Wayfarer HTTP workflow, including assessment, exchange, and simulated supplier return cancellation. |
| Loomspan/model-coupled behavior to remove | `TripApplicationTest`: `invalidModelResultsCannotBecomeProposals`, `interruptedAssessmentsBecomeRetryableFailuresAndLateResultsAreFenced`, `allAllowedSearchesAndActualEvaluationAreRequired` (`:75-83`, `:123-127`, `:133-152`) | These depend on model result shapes, model execution lifecycle, and model-mediated Java skill receipts. |
| Loomspan/model-coupled behavior to remove | All seven `IntakeServiceTest` methods (`:39-101`) | Every test drives mocked `SkillTemplate.invoke("interpretTripChange", ...)`, persists conversational turns, or confirms a model-produced patch. |
| Loomspan/model-coupled behavior to remove | All three `LivePlanningTest` methods (`:31-98`) and `LiveIntakeTest.liveInterpretationClarifiesTimeComputesBudgetAndDeclinesUnsupportedChange` (`:16-38`) | These require a real configured provider and assert nested skill execution, model interpretations, execution traces, or consent generated from that flow. |
| Loomspan/model-coupled behavior to remove | `HttpFlowTest.providerFailureIsNotInfeasibility` and `HttpFlowTest.conversationalDraftRequiresExplicitHttpConfirmation` (`:72-93`) | They respectively assert `SkillTemplate` provider failure handling and the model-backed conversational-draft endpoint. |

The inventory counts are: reusable 6, replace 19, remove 16 (total 41). No frontend automated tests or test fixtures are tracked.

## Dependencies and Operational Constraints

- Java 21 is declared by the build (`pom.xml:15`); a usable Java executable was not available in this execution environment, so Maven could not be started.
- Maven packaging invokes `npm ci`, which can need package-registry access even when a local `frontend/node_modules` directory exists (`pom.xml:79-106`).
- The normal backend suite uses in-memory H2 and mocks Loomspan in the non-live tests; it should not require a live model provider by default. Live suites are opt-in through `WAYFARER_LIVE_TEST=true`.
- The default application database is a persistent ignored file under `data/wayfarer` (`src/main/resources/application.yml:7-8`); baseline work must not reset or mutate it.
- The application is configured for loopback binding, but research did not start it or contact a provider (`src/main/resources/application.yml:1-3`, `:17-46`).

## Historical Context

`5fb7d2b` added the DeTour roadmap, Phase 0, pipeline commands, and this ticket. Its stat also includes `IntakeService.java` and `TripStore.java`; the continuation guide's earlier user-owned-worktree note concerns the state before this current clean checkout. Earlier history introduced the Wayfarer fixed scenario, booking change/exchange, disruption recovery, and conversational changes (`git log --oneline` shows `4f21034`, `2ed3945`, and `f64ffa1`).

The authoritative DeTour roadmap says the current Wayfarer Boston–New York data are not migrated, Loomspan is removed, and DeTour retains application-owned validation, authorization, and transactional/concurrency-safe inventory behavior (`ai/thoughts/phases/README.md`, `ai/thoughts/phases/CONTINUATION.md`, and `ai/thoughts/phases/phase-0-baseline-and-boundaries.md`). P00-T02 explicitly consumes this ticket's baseline and disposition inventory (`ai/thoughts/tickets/2026-09-17-p00-t02-define-replacement-architecture.md:20,35-43`).

## Open Questions

None for research. The absent Java runtime is a recorded verification-environment condition; the later baseline execution can record the resulting command outcome without changing repository code or configuration.
