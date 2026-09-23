# P07-T02 Code Review — Cycle 1

## Scope and Repository State

Reviewed the ticket, research, implementation and testing plans, design lens, all ticket-scoped unstaged frontend changes, untracked process artifacts, connected workspace and comparison code, and existing frontend tests. Branch `main` at `2d3712a`; no staged changes. No `AGENTS.md` or active project-specific design guardrail was found. The change is frontend-only; server ownership and mutation contracts were inspected for fit and were not edited.

## Findings

### [P2] Clear entry mode when opening another Trip
- Location: `frontend/src/components/ProfileScreen.tsx:107`
- Scenario: Create a Trip through Airfare or Stay, then open a different Trip that has no corresponding selection.
- Impact: The other Trip starts in the previous Trip's search mode without an explicit action, breaking component context.
- Evidence: `entryContext` was set after creation but only cleared after deletion; a newly keyed `TripWorkspace` uses `initialEntryMode` to start a search.
- Fix: Clear `entryContext` when a different Trip opens. Extended the Airfare creation test to open a second Trip and verify its airfare slot is empty.

### [P2] Keep a later Home/Profile choice during a slow Trip open
- Location: `frontend/src/components/ProfileScreen.tsx:123`
- Scenario: Start opening a Trip, then choose Home before its detail request resolves.
- Impact: The late detail response previously forced the workspace back into view.
- Evidence: The request sequence changed only for another Trip open or creation, not for Home/Profile navigation.
- Fix: Invalidate the pending open on Home/Profile navigation and clear its loading/error state. Added a deferred-response regression test.

### [P2] Make the selector, rather than the entire mobile comparison, sticky
- Location: `frontend/src/style.css:649`
- Scenario: Scroll a long stacked mobile comparison.
- Impact: Sticky positioning on the wrapper containing both the selector and full comparison panel cannot keep its top selector available throughout a long panel.
- Evidence: `ItineraryComparisonView` puts `.mobile-tablist` and `.mobile-stacked-panel` inside `.mobile-comparison-switcher`; the original new rule applied `position: sticky` to that outer wrapper.
- Fix: Apply sticky positioning to `.mobile-tablist` and leave the panel wrapper in normal flow.

## Findings Resolved in This Context

All three findings above were fixed. A complete diff review after the fixes found no further actionable finding. This context changed implementation and test artifacts, so a fresh Step 5 review is required.

## Acceptance-Criteria and Plan Conformance

| Criterion/decision | Code evidence | Test evidence | Result |
| --- | --- | --- | --- |
| Named Home, Profile, active Trip, logout | Shared navigation in `ProfileScreen`; workspace remains mounted through subviews | Navigation and slow-open tests in `App.test.tsx` and `ProgressiveTripBuilder.test.tsx` | implemented |
| Draft context and authoritative saved tally | Keyed retained `TripWorkspace`, guarded `refreshIfClean`, existing owner-scoped `getTrip` | Draft continuity, stale refresh, save retry, and component tests | implemented |
| First Trip starts, empty/loading/failure/retry | Home entry actions, opening status/retry, autosave and search/history retry controls | Entry, save retry, flight search, history tests | implemented |
| Narrow profile/Trip usability | Responsive wrapping/grid rules in `style.css` | DOM interaction suite; browser geometry remains optional | implemented, geometry unverified |
| Up to three Planned comparison; desktop columns/mobile stacked selector; absent distinct from zero | Existing Planned-only selection cap, comparison views, corrected sticky selector | `ItineraryComparisonAndBookingReview.test.tsx` | implemented, sticky scroll unverified in browser |
| No new route or server contract | Local view state and unchanged backend | Frontend suite/build; backend tests not rerun because backend is unchanged | implemented |

## Active Project Guardrails

- None recorded in `ai/thoughts/design-lens.md`.

## Open Questions and Assumptions

- None affecting this review's disposition. A one-item comparison remains unavailable, consistent with the Phase 5 multiple-alternative rule and the approved plan.

## Verification Results

- PASS — `npm test -- src/ProgressiveTripBuilder.test.tsx` from `frontend` — 18 tests passed after the initial fixes.
- PASS — `npm test` from `frontend` — 114 tests passed after all fixes.
- PASS — `npm run build` from `frontend` — TypeScript and Vite build completed after all fixes.
- NOT RUN — local browser geometry and sticky-scroll inspection — no configured local application/browser session was used; jsdom does not establish actual 320px fit or sticky behavior.
- NOT RUN — backend integration suite — no backend code or API contract changed; frontend API calls are mocked in the run suite.

## Residual Risks and Optional Developer Checks

- Inspect Home, Profile, Trip, search results, booking review/history, and comparison at 320px and 375px in a configured local browser. Scroll a long mobile comparison to confirm the tab selector stays visible and no required action is clipped. This is a visual observation, not an automated pass.

## Disposition

- `fixes-applied`
