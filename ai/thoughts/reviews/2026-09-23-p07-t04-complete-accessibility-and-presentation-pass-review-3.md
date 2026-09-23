# P07-T04 Code Review — Cycle 3

## Scope and Repository State

Reviewed the ticket, research, implementation plan, testing plan, design lens, current frontend code and tests, and committed, staged, unstaged, and untracked inventory. The work is on `main` at `cf4f619`; the one committed deletion ahead of `origin/main` is unrelated to this ticket. Ticket work consists of the frontend presentation and test changes plus pipeline artifacts. There are no staged changes. Review covered navigation, search, schedules, comparison, booking, cancellation, dialogs, totals, history, CSS, and verification. The selected full profile remains appropriate for this cross-workflow change. No active project guardrails are recorded.

## Findings

### [P2] Focus the retained Trip subview on return navigation

- Location: `frontend/src/components/ProfileScreen.tsx:46`
- Scenario: Enter comparison from a Trip, choose Home, then use the persistent `Trip: …` navigation button to return. The same `TripWorkspace` remains mounted on its comparison view. Booking review and confirmation have the same retained-view behavior.
- Impact: The return-navigation focus effect attempted to focus `#workspace-heading`, which is absent from those subviews. Keyboard and screen-reader users remained on the navigation button without focus moving to the displayed Trip context.
- Evidence: `ProfileScreen` preserves the workspace while hidden, and `TripWorkspace` conditionally renders comparison, review, or confirmation instead of its default heading. The new integration test exercises the comparison route and asserts focus on the comparison heading after return.
- Fix: Focus the heading rendered by the current workspace subview, falling back to the default workspace heading. This was applied with a selector of the four view headings.

## Findings Resolved in This Context

- Updated `ProfileScreen` return-navigation focus target to include the retained comparison, booking review, and confirmation headings.
- Added `focuses a retained comparison view when returning from Home` in `ItineraryComparisonAndBookingReview.test.tsx`.
- Re-reviewed the complete ticket diff after the fix. No actionable findings remain in this context.

## Acceptance-Criteria and Plan Conformance

| Criterion/decision | Code evidence | Test evidence | Result |
| --- | --- | --- | --- |
| Keyboard registration, planning, booking, and cancellation paths | `App`, `ProfileScreen`, `TripWorkspace`, search sections, and modal focus handlers; retained subview return fixed | App, builder, promotion, comparison, cancellation, and pending-dialog tests | implemented for DOM behavior; rendered end-to-end observation remains optional |
| Errors, autosave, warnings, booking, cancellation announcements | Global error summary, search statuses, workspace notices, readiness banner, booking status and triage | App, builder, promotion, booking, cancellation tests | implemented |
| Dialog, About, and comparison semantics and focus | Modal Tab/Escape/pending guards, non-modal About, native comparison table and linked mobile tabs | Dialog, About, comparison and pending-action tests | implemented |
| Zoom, reduced motion, non-color cues | Responsive CSS, visible focus outlines, reduced-motion rule and jump behavior; textual badges | VisualSystem and workflow tests | implemented for source/DOM behavior; rendered 200% and contrast observation remains optional |
| Both local flight endpoints and date changes | `FlightSchedule` used in search, comparison, and review | FlightSchedule and comparison tests for SFO, MUC, MEX, missing fields and date changes | implemented |
| Authoritative USD and absent-component meaning | Draft tally, planned tally, comparison, review, confirmation, and history | Builder, comparison, confirmation and cancellation/history tests | implemented |

## Active Project Guardrails

None recorded in `ai/thoughts/design-lens.md`.

## Open Questions and Assumptions

None affecting the code decision. Rendered browser, screen-reader, contrast, and 200% zoom observations are not represented by jsdom tests.

## Verification Results

- PASS — `npm test` from `frontend` before the review fix — 13 files, 132 tests.
- PASS — `npm test -- --run src/ItineraryComparisonAndBookingReview.test.tsx` from `frontend` after the fix — 16 tests.
- PASS — `npm run build` from `frontend` after the fix — TypeScript and Vite build.
- PASS — `npm test` from `frontend` after the fix — 13 files, 133 tests.
- PASS — `git diff --check` from repository root — no whitespace errors.
- NOT RUN — rendered desktop/mobile keyboard, screen-reader, 200% zoom, and contrast checks — no local browser/AT observation was performed in this review; the residual is visual and assistive-technology behavior beyond jsdom.

## Residual Risks and Optional Developer Checks

In a local rendered browser, traverse registration through booking and cancellation by keyboard at desktop and narrow widths; inspect 200% zoom, contrast, reduced motion, flight schedule speech, and the cancellation-to-triage transition with a screen reader. These are optional observational checks and were not claimed as automated passes.

## Disposition

`fixes-applied`
