---
date: 2026-09-17
ticket: ai/thoughts/tickets/2026-09-17-p00-t01-capture-replacement-baseline.md
purpose: pre-DeTour replacement baseline
---

# P00-T01 Capture Replacement Baseline

## Capture metadata

- Captured before baseline verification: `2026-09-17T13:32:53.6702721-07:00`
- Branch: `main`
- Commit: `5fb7d2b45209c9034bf40ce40e6c580618abc5c8`
- Capture commands: `git branch --show-current`; `git rev-parse HEAD`; `git status --porcelain=v1 --untracked-files=all`; `git diff --name-only`; `git diff --cached --name-only`.

## Starting worktree snapshot

The snapshot was taken before the preflight, build, or test commands below. There were no staged changes and no unstaged tracked changes: both name-only diff commands returned no paths.

| Status category | Captured entry | Provenance and disposition |
| --- | --- | --- |
| Untracked | `ai/thoughts/research/2026-09-17-p00-t01-capture-replacement-baseline.md` | Workflow-created research artifact; preserve as pipeline provenance. |
| Untracked | `ai/thoughts/plans/2026-09-17-p00-t01-capture-replacement-baseline.md` | Workflow-created implementation-plan artifact; preserve as pipeline provenance. |
| Untracked | `ai/thoughts/plans/2026-09-17-p00-t01-capture-replacement-baseline-testing.md` | Workflow-created testing-plan artifact; preserve as pipeline provenance. |
| Staged | None | `git diff --cached --name-only` was empty. |
| Unstaged tracked | None | `git diff --name-only` was empty. |

`src/main/java/demo/wayfarer/IntakeService.java` and `src/main/java/demo/wayfarer/TripStore.java` were not modified at capture time, so there was no current user work on either path to preserve. They were not changed by this ticket. The historical user-owned-work note refers to an earlier checkout state; the files are present in `HEAD` in this checkout.

Ignored paths such as `data/`, `frontend/dist/`, `frontend/node_modules/`, `frontend/tsconfig.tsbuildinfo`, and `target/` are outside porcelain output and are neither baseline worktree entries nor ticket output.

## Verification results

No command enabled `WAYFARER_LIVE_TEST`, started the application, contacted a provider, or targeted the persistent `data/` database.

| Category | Exact command / inspection | Result | Relevant outcome |
| --- | --- | --- | --- |
| Record preflight | `Test-Path ai/thoughts/baselines/2026-09-17-p00-t01-capture-replacement-baseline.md` | PASS | Returned `False` before this record was created, as expected for the planned documentation-contract preflight. |
| Backend tests | `./mvnw.cmd test -DskipFrontend=true` | FAIL | Exit 1 before test execution. The Maven wrapper reported `Cannot start maven from wrapper`; Java is not usable on `PATH` in this environment. No test result was produced. |
| Frontend automated tests | `Get-Content -Raw frontend/package.json` plus tracked-file inspection | NOT RUN | `package.json` defines only `dev` and `build`; no tracked frontend test/spec files were found. No checkout-supported frontend test command exists. |
| Frontend production build (documented invocation) | `npm run build --prefix frontend` | FAIL | Did not reach TypeScript/Vite: PowerShell blocked `C:\Program Files\nodejs\npm.ps1` because script execution is disabled. The blocked script set PowerShell success to `False` and did not start an `npm` process, so no `npm` exit code was available. |
| Frontend production build (Windows executable) | `npm.cmd run build --prefix frontend` | PASS | Exit 0. `tsc -b && vite build` completed and Vite built 18 modules into `frontend/dist`. This invokes the same package `build` script without changing PowerShell policy or repository configuration. |
| Packaged-application build | `./mvnw.cmd package` | FAIL | Exit 1 before package execution. The Maven wrapper reported `Cannot start maven from wrapper`; Java is not usable on `PATH` in this environment. |

These failures are baseline observations. This ticket did not install a runtime, change `PATH` or PowerShell policy, add a build workaround, or modify repository code/configuration to make a check green.

## Automated-test disposition inventory

The five tracked JUnit classes contain 41 `@Test` methods (`rg -n "@Test" src/test/java/demo/wayfarer` returned 41). Each current method is listed exactly once below. "Reusable" identifies behavioral properties to carry into DeTour with new contracts/fixtures; it does not retain Wayfarer endpoint or data details.

### Reusable behavior worth preserving - 6

| Test(s) | Reason |
| --- | --- |
| `TripApplicationTest.bookingReservesBothLegsAndNightsAndRetriesAreIdempotent`; `TripApplicationTest.acceptedBookingConsumesFourFiniteRows`; `TripApplicationTest.racingBookingsNeverPartiallyReserve`; `TripApplicationTest.changedPricesRejectBookingWithoutStockWrites`; `TripApplicationTest.historicalAndForeignProposalsCannotBeBooked` | Preserve the general transactional properties: inventory reservation, atomicity under concurrency, idempotency, stale quote/state rejection, and cross-trip isolation. Replace the Wayfarer fixtures and contracts. |
| `HttpFlowTest.malformedJsonAndUnknownIdsReturnUsefulHttpStatuses` | Preserve generic malformed-input and missing-resource HTTP error behavior; replace the Wayfarer routes and response details. |

### Wayfarer-scenario behavior to replace - 19

| Test(s) | Reason |
| --- | --- |
| `TripApplicationTest.allFixtureCombinationsHaveCorrectPartyPricingAndTiming`; `TripApplicationTest.budgetFailureUsesCheapestOtherwiseFeasibleTrip`; `TripApplicationTest.exactBudgetAndArrivalBoundariesAreInclusive`; `TripApplicationTest.railOnlyIsSelectiveAndCoverageRejectsOmissionsDuplicatesAndForeignIds`; `TripApplicationTest.missingOneHotelNightExcludesTheHotelAndSnapshotsAreImmutable`; `TripApplicationTest.unsupportedRequestsFailBeforePersistence` | Fixed Boston-New York itinerary, pricing, timing, rail/air/hotel fixture, and request rules are obsolete Wayfarer scenario behavior. |
| `TripApplicationTest.changePlanningCreditsOnlyOwnedInventoryAndPreservesHotelWithoutReleasingStock`; `TripApplicationTest.exchangeAtomicallyReplacesInventoryAndPersistsHistoryWithIdempotentReplay`; `TripApplicationTest.failedExchangeRollsBackReleasedInventoryAndRetainsOriginalBooking`; `TripApplicationTest.staleAndForeignExchangeProposalsCannotReplaceBooking`; `TripApplicationTest.concurrentExchangeAcceptancesHaveOneWinner`; `TripApplicationTest.exchangeAndNewBookingCompetingForLastSeatsHaveOneWinner`; `TripApplicationTest.infeasibleChangeAndMissingHeldNightNeverReleaseCurrentBooking`; `TripApplicationTest.cancellationIsIdempotentAndRecoveryRetainsUnaffectedReservations`; `TripApplicationTest.cancellationAffectsAllBookingsAndFencesEarlierProposals`; `TripApplicationTest.railOnlyRecoverySuggestsExplicitModeAndBudgetChangesWithoutApplyingThem`; `TripApplicationTest.failedRecoveryDoesNotReleaseHotelOrOutboundAndNoInventoryCannotBeFixedByPreferences`; `TripApplicationTest.cancellationDuringExchangeCannotLeaveAHealthyBookingOnCanceledService` | Booking exchange, supplier-style return cancellation, disruption, and recovery are Wayfarer-specific flows. DeTour has different itinerary/cancellation behavior and defers these flows. |
| `HttpFlowTest.httpRequestAssessmentBookingAndReload` | Fixed assessment/booking/exchange/supplier-return HTTP workflow is a Wayfarer scenario to replace. |

### Loomspan/model-coupled behavior to remove - 16

| Test(s) | Reason |
| --- | --- |
| `TripApplicationTest.invalidModelResultsCannotBecomeProposals`; `TripApplicationTest.interruptedAssessmentsBecomeRetryableFailuresAndLateResultsAreFenced`; `TripApplicationTest.allAllowedSearchesAndActualEvaluationAreRequired` | Assert model-result shapes, model execution lifecycle, or Loomspan skill receipts. |
| `IntakeServiceTest.reviewedDeltaPreservesOtherFieldsAndConfirmationIsIdempotent`; `IntakeServiceTest.clarificationRetainsConversationAndRequiresConcreteProposal`; `IntakeServiceTest.staleRequestAndCatalogRejectConfirmation`; `IntakeServiceTest.bookingCreatedAfterInterpretationInvalidatesDraft`; `IntakeServiceTest.invalidPatchesUnsupportedRequestsAndProviderFailuresDoNotChangeTrip`; `IntakeServiceTest.foreignAndStaleClarificationParentsAreRejected`; `IntakeServiceTest.simultaneousConfirmationsCreateExactlyOneRevision` | All seven drive `SkillTemplate.invoke("interpretTripChange", ...)` or persist/confirm a model-produced intake draft. |
| `LivePlanningTest.nestedPlanningProducesBookableQuietTripAndBudgetAlternative`; `LivePlanningTest.railOnlyUnderBudgetRequestReportsInfeasibilityWithoutFlightSearch`; `LivePlanningTest.canceledRailReturnNeedsModeConsentBeforeLiveRecovery`; `LiveIntakeTest.liveInterpretationClarifiesTimeComputesBudgetAndDeclinesUnsupportedChange` | Provider-backed live Loomspan tests, gated by `WAYFARER_LIVE_TEST=true`, assert model interpretations, nested skill execution, traces, or model-derived consent. |
| `HttpFlowTest.providerFailureIsNotInfeasibility`; `HttpFlowTest.conversationalDraftRequiresExplicitHttpConfirmation` | Respectively assert `SkillTemplate` provider-failure handling and the model-backed conversational-draft endpoint. |

**Totals:** reusable 6 + replace 19 + remove 16 = **41**.

## Final worktree comparison

Captured at `2026-09-17T13:34:22.4136583-07:00` after the record was first written, then repeated at `2026-09-17T13:36:15.1794177-07:00` after all baseline commands, using `git status --porcelain=v1 --untracked-files=all`, `git diff --name-only`, `git diff --cached --name-only`, and `git diff --check`. Both captures had the same tracked-diff results and porcelain path list.

| Final check | Observed result |
| --- | --- |
| Porcelain status | The three starting workflow artifacts remain untracked, and `ai/thoughts/baselines/2026-09-17-p00-t01-capture-replacement-baseline.md` is the only additional entry. |
| Unstaged tracked names | None (`git diff --name-only` was empty). |
| Staged names | None (`git diff --cached --name-only` was empty). |
| Whitespace check | PASS (`git diff --check` exited 0 with no output). |

Therefore the baseline record is the sole Step-4 implementation artifact. The research and two plan files predate the verification snapshot and remain workflow provenance, not user work attributed to this ticket. No tracked application, test, dependency, configuration, generated-asset, or database path was altered; the named `IntakeService.java` and `TripStore.java` paths remain absent from both tracked-diff lists. This comparison preserves all entries in place and performs no clean, reset, checkout, staging, or revert operation.
