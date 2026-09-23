# P07-T02 Code Review — Cycle 4

## Scope and Repository State

Reviewed the ticket, research, implementation plan, testing plan, active design lens, the complete ticket-scoped working-tree diff, connected application and workspace code, and relevant tests. The checkout is `main` with staged changes absent; the ticket changes are unstaged, and process artifacts are untracked. No repository `AGENTS.md` was found. Prior review documents were not consulted. The selected profile remains `full`.

## Findings

### [P1] Preserve the authenticated workspace when a profile refresh fails
- Location: `frontend/src/App.tsx:42`
- Scenario: A traveler edits a Trip, navigates to Profile, and the `onRefreshProfile` request fails transiently. `loadProfile` changed the screen to public even though the session was not rejected.
- Impact: The mounted Trip workspace was destroyed, losing local unsaved edits and the retry path.
- Evidence: `ProfileScreen.navigateTo('profile')` invokes `onRefreshProfile` (`frontend/src/components/ProfileScreen.tsx:123`); `App` previously set `{kind: 'public'}` for every profile request failure. The regression test now covers a failed autosave followed by a failed profile refresh.
- Fix: Preserve the authenticated screen for non-authentication failures on refresh, surface the existing error notice, and still transition to public on `UNAUTHENTICATED`.

## Findings Resolved in This Context

- Applied the App refresh guard and added `frontend/src/App.test.tsx:471`, which verifies the Trip and unsaved budget remain available with Retry save after the failed refresh.
- Re-reviewed the resulting code and diff. No further actionable findings were confirmed.

## Acceptance-Criteria and Plan Conformance

| Criterion/decision | Code evidence | Test evidence | Result |
| --- | --- | --- | --- |
| Named Home, Profile, open Trip, and Log out from primary views | Shared navigation in `ProfileScreen`; workspace remains mounted | `App.test.tsx`, `ProgressiveTripBuilder.test.tsx` | implemented |
| Preserve active Draft and authoritative saved selections/tally across component views and return | Keyed `TripWorkspace`, guarded `refreshIfClean`, server Trip response | `ProgressiveTripBuilder.test.tsx`; existing component selection tests | implemented |
| Home entry modes, loading/empty states, failed save retry | `EmptyProfileState`, open-trip status/retry, workspace Retry save, search/history retry | `App.test.tsx`, `ProgressiveTripBuilder.test.tsx`, `FeeFreeCancellationAndTriage.test.tsx` | implemented |
| Narrow Profile/Trip actions and content | Responsive wrapping/grid rules in `style.css` | DOM interaction tests; geometry remains optional browser check | implemented with visual check outstanding |
| Three Planned comparison choices; desktop columns, mobile stack/selector; absent versus zero | Existing Planned filter/cap and comparison markup; mobile sticky CSS | `ItineraryComparisonAndBookingReview.test.tsx` | implemented with visual check outstanding |
| Retain owner isolation and snapshot/lifecycle rules | No backend or API contract changes; existing owner-scoped endpoints | Existing frontend suite; backend tests were not required for frontend-only change | implemented |

## Active Project Guardrails

- None recorded in `ai/thoughts/design-lens.md`.

## Open Questions and Assumptions

- None affecting the code review disposition. Browser geometry is not established by jsdom.

## Verification Results

- PASS — `npm test -- src/App.test.tsx` from `frontend` — 32 tests passed.
- PASS — `npm test` from `frontend` — 10 files, 117 tests passed.
- PASS — `npm run build` from `frontend` — TypeScript and Vite build passed.
- PASS — `git diff --check` — no whitespace errors.
- NOT RUN — configured browser visual inspection — no live local browser environment was used; CSS geometry and sticky scroll behavior need observation.

## Residual Risks and Optional Developer Checks

- Inspect Home, Profile, Trip, search, booking, history, and comparison at 320px and 375px. Scroll a long mobile comparison to confirm the itinerary selector stays reachable and no required action clips. This is an optional visual check; automated DOM tests cannot establish layout geometry.

## Disposition

- `fixes-applied`
