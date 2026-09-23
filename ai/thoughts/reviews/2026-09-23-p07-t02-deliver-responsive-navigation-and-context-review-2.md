# P07-T02 Code Review — Cycle 2

## Scope and Repository State

Reviewed the ticket, research, implementation and testing plans, current unstaged frontend diff, untracked process artifacts, connected Trip API usage, and existing comparison behavior. There are no staged changes. The ticket change is frontend-only; the existing backend owner-scoped Trip contract is unchanged. This review did not use an earlier review document.

## Findings

### [P2] Ignore obsolete refresh failures after editing begins
- Location: `frontend/src/components/TripWorkspace.tsx:407`
- Scenario: Returning to an open Trip starts a `getTrip` refresh. The traveler edits a field while that request is pending, then the refresh rejects after the edit or its save.
- Impact: The stale read failure replaces the current save status with a refresh error, potentially claiming a saved edit is unsaved and obscuring the relevant Retry save state.
- Evidence: The success path already checks `latestRefreshState` before applying data, while the failure path set `refreshFailed` and `autosaveStatus` unconditionally. `ProfileScreen.tsx:97` invokes this refresh when returning to the active Trip.
- Fix: Capture the requested version and ignore a failed refresh when local changes are pending or a newer Trip version has arrived.

## Findings Resolved in This Context

- Added the failure guard in `TripWorkspace.tsx:398-408` and a deferred-request regression in `ProgressiveTripBuilder.test.tsx:160-175`. Re-reviewed the resulting diff; no further actionable findings remained.

## Acceptance-Criteria and Plan Conformance

| Criterion/decision | Code evidence | Test evidence | Result |
| --- | --- | --- | --- |
| Named Home, Profile, active Trip, logout | Shared navigation wraps every authenticated view in `ProfileScreen.tsx:239-249` | `App.test.tsx` and `ProgressiveTripBuilder.test.tsx`; full suite passed | implemented |
| Preserve Draft and authoritative saved tally | Keyed mounted workspace, guarded refresh, and existing Trip API responses in `ProfileScreen.tsx:252-276` and `TripWorkspace.tsx:383-410` | Navigation, deferred refresh, selection and tally tests in `ProgressiveTripBuilder.test.tsx` | implemented |
| First-time entry, loading, failure and retry | Home actions and opening state in `ProfileScreen.tsx`; save/search/history retry in `TripWorkspace.tsx` and search/history sections | `App.test.tsx`, `ProgressiveTripBuilder.test.tsx`, `FeeFreeCancellationAndTriage.test.tsx` | implemented |
| Narrow profile, Trip and action layout | Wrapping and mobile grid rules in `style.css:634-661` | Semantic action tests pass; browser geometry remains optional | implemented with visual observation pending |
| Planned comparison cap, desktop columns, mobile stacked selector, absence distinct from zero | Existing Planned filter and cap in `TripWorkspace.tsx`, comparison view, sticky selector in `style.css:648-649` | `ItineraryComparisonAndBookingReview.test.tsx` in full suite | implemented with visual observation pending |
| Preserve server ownership, validation and snapshot rules | No backend or API contract edit; existing owner-scoped operations and versioned mutations remain in use | Frontend mocked suite passes; backend suite not rerun for frontend-only change | implemented |

## Active Project Guardrails

- None recorded in `ai/thoughts/design-lens.md`.

## Open Questions and Assumptions

- None affecting correctness. Actual 320px and 375px geometry and sticky scrolling need a configured browser observation.

## Verification Results

- PASS — `npm test` from `frontend` — 10 files, 115 tests passed after the fix.
- PASS — `npm run build` from `frontend` — TypeScript and Vite build passed after the fix.
- PASS — `git diff --check` — no whitespace errors; Git emitted line-ending conversion notices.
- FAIL, then resolved — `npm test -- src/ProgressiveTripBuilder.test.tsx` — the first new-test run failed because the fixture already had a $2,000 budget and the test appended digits; corrected the test to clear the field first, then the full suite passed.
- NOT RUN — live browser geometry/sticky scroll check — requires a configured local UI session; no live external service was used.

## Residual Risks and Optional Developer Checks

- Optional: inspect Home, Profile, Trip, search, booking history/review, and comparison at 320px and 375px; scroll a long mobile comparison to confirm the selector stays reachable and no required content clips.

## Disposition

- `fixes-applied`. A new independent review context must certify the updated implementation.
