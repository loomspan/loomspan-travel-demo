# pr3 — Save one Working plan and create comparison options only on request Code Review — Cycle 1

## Scope and Repository State

Reviewed the PR3 ticket, research, implementation and testing plans, active design lens, and the uncommitted change from `3d995be` on `main`. Scope included all 18 modified production/test files and the three untracked PR3 planning artifacts. No active project guardrails are recorded. The change introduces option create/load/update routes, dated snapshot replacement, booking eligibility checks, and Working-plan UI actions. No prior review artifact was read.

## Findings

No actionable findings remain after the fixes below.

## Findings Resolved in This Context

### [P2] Keep a failed component mutation pending until retry or reload
- Location: `frontend/src/components/TripWorkspace.tsx:414`
- Scenario: A flight, stay, or car selection/removal request fails while local shared fields are unchanged. The prior change stopped treating the error state as unsaved, allowing Save as new option to capture the older server selection and allowing a background refresh to clear the failure message.
- Impact: The saved option can omit a component change the user just requested, with misleading save feedback.
- Evidence: Component handlers set `autosaveStatus` to `error`, while `hasUnsavedChanges` previously excluded that state and `ensureSavedWorking` relies on `hasUnsavedChanges`.
- Fix: Track failed Working component mutations until the component request succeeds or the user reloads. Added a UI test that rejects option saving after a failed removal.

### [P3] Use each option's departure date for its expiry badge
- Location: `frontend/src/components/TripWorkspace.tsx:1890`
- Scenario: The Working plan's departure is past, but a Saved option under the same Trip has a future departure.
- Impact: The future option was marked Expired even though booking and cancellation decisions use that option's date.
- Evidence: Every `AlternativeCard` previously received the Trip-level `isExpired` value.
- Fix: Pass expiry derived from the alternative's own start date, with the prior value only as fallback for older responses lacking dates. Added a dated-options UI test.

## Acceptance-Criteria and Plan Conformance

| Criterion/decision | Code evidence | Test evidence | Result |
| --- | --- | --- | --- |
| One Working row, save states, no-op behavior | `TripService.changeWorkingDates`, `replaceSharedDetails`, `TripWorkspace` autosave and failure tracking; V19 unique constraint | `TripApiIntegrationTest`, `ProgressiveTripBuilder.test.tsx` | implemented |
| Explicit named option; reject empty; null budget | `TripService.saveOption`, `validOptionSnapshot`, option snapshot insert | `TripApiIntegrationTest` named/budgetless/empty cases | implemented |
| Load, update, or branch without silent replacement | `TripService.loadOption`, `updateOption`, UI confirmation and save-current action | `TripApiIntegrationTest`, `ItineraryComparisonAndBookingReview.test.tsx` | implemented |
| Date revalidation and option isolation | Working mutation revalidation; dated `resolveSelectionsForOption`; unchanged option rows | `TripApiIntegrationTest` | implemented |
| Booked/history option immutability | booking-history guard in `updateOption` and repository; original option retained on load | `TripApiIntegrationTest`, `BookingApiIntegrationTest` | implemented |
| Option-specific booking, expiry, cancellation | `BookingTransactionExecutor`; option-specific UI expiry | `BookingApiIntegrationTest`, dated UI test | implemented |
| No multiple Working plans | reject extra-Draft routes; unique database constraint; load replaces one row | `TripApiIntegrationTest`, existing migration suite per testing plan | implemented |

## Active Project Guardrails

- None recorded in `ai/thoughts/design-lens.md`.

## Open Questions and Assumptions

- None affecting this review disposition.

## Verification Results

- PASS — `.\mvnw.cmd -q '-Dmaven.repo.local=C:\Users\rmelcher\.m2\repository' -DskipFrontend=true '-Dtest=TripApiIntegrationTest,BookingApiIntegrationTest' test` — focused backend integration tests passed.
- PASS — `npm test -- --run tripsApi.test.ts ProgressiveTripBuilder.test.tsx ItineraryComparisonAndBookingReview.test.tsx` — 53 frontend tests passed before review fixes.
- PASS — `npm test -- --run ProgressiveTripBuilder.test.tsx` — 23 tests passed after fixes.
- PASS — `npm test` — 14 files and 142 tests passed after fixes.
- PASS — `npm run build` — TypeScript and Vite build passed after fixes.
- PASS — `git diff --check` — no whitespace errors.
- NOT RUN — full backend suite in this review context; the focused changed backend paths passed, and Step 4 recorded a complete backend suite pass before these frontend-only review fixes.

## Residual Risks and Optional Developer Checks

- Browser inspection of option replacement wording and a two-tab stale edit remains useful but is not needed for the automated gate.

## Disposition

- `fixes-applied`
