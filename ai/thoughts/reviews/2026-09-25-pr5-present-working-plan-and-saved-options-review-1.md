# PR5 Code Review — Cycle 1

## Scope and Repository State

Reviewed the PR5 ticket, research, implementation and testing plans, design lens, and all staged, unstaged, and untracked files against HEAD `f9ebcc2` (PR4). The checkout contained PR5 production, test, and plan changes; no unrelated dirty files were identified. Traced the Trips list, Working/Saved option actions, comparison, booking and cancellation UI, rename API, persistence and authorization paths, and test coverage. No active project guardrails are recorded.

## Findings

No actionable findings remain after fixes in this context.

## Findings Resolved in This Context

### [P1] Preserve unsaved Working edits during option rename
- Location: `frontend/src/components/TripWorkspace.tsx:1058`
- Scenario: Edit a shared Working field, then rename a Saved option before the debounced save completes.
- Impact: A successful rename called `applyTripState`, replacing local shared fields with the server response and losing the unsaved edit.
- Evidence: Rename had no `ensureSavedWorking()` guard; other option mutations used that guard. `applyTripState` resets budget, dates, travelers, and ages.
- Fix: Require a saved Working plan before rename and keep the dialog and local edits available when the guard fails. Added a regression test.

### [P2] Keep keyboard focus within the new option dialogs
- Location: `frontend/src/components/TripWorkspace.tsx:2032`
- Scenario: Open Update or Rename and use Tab or Escape with a keyboard or screen reader.
- Impact: Focus remained on a control behind an `aria-modal` dialog; Tab could continue into background controls, and closing did not restore focus.
- Evidence: The new inline dialogs had no focus effect or keyboard handler, unlike existing modal components.
- Fix: Focus the first dialog control on open, contain Tab movement, close with Escape when no action is pending, and return focus to the opener or workspace heading. Added a rename dialog focus test.

## Acceptance-Criteria and Plan Conformance

| Criterion/decision | Code evidence | Test evidence | Result |
| --- | --- | --- | --- |
| One Working plan and separate named, dated Saved options | `TripWorkspace`, `AlternativeCard`, `TripListSection` | `App.test.tsx`, `DraftPromotion.test.tsx` | implemented |
| No empty Draft creation; incomplete Working editable | Workspace actions and save guard | `DraftPromotion.test.tsx` | implemented |
| Save, update, copy, rename effects and booked immutability | Workspace confirmations and rename API | `DraftPromotion.test.tsx`, `TripApiIntegrationTest` | implemented |
| Per-option dates, components, price and status | Card, comparison, profile summary and booked response flag | `ItineraryComparisonAndBookingReview.test.tsx`, API integration tests | implemented |
| Date summaries and honest save/auth failure state | `RevisionSummaryBanner`, Workspace status and guards | `DraftPromotion.test.tsx` | implemented |
| Keyboard, assistive, and narrow layouts | Labels, focus handler, comparison tabs and CSS | Frontend interaction tests and build | implemented; manual visual and screen-reader check remains optional |

## Active Project Guardrails

None recorded in `ai/thoughts/design-lens.md`.

## Open Questions and Assumptions

None.

## Verification Results

- PASS — `npm test` from `frontend` — 154 tests in 14 files.
- PASS — `npm run build` from `frontend` — TypeScript and Vite build.
- PASS — `$env:MAVEN_OPTS='-Duser.home=C:\Users\rmelcher -Dmaven.repo.local=C:\Users\rmelcher\.m2\repository'; .\mvnw.cmd -o -Dtest=TripApiIntegrationTest test` — 46 integration tests.
- PASS — `git diff --check` — no whitespace errors.
- FAIL, then corrected test assertion — `npm test -- --run DraftPromotion.test.tsx` — first run expected string `500` from a number input; the assertion now expects numeric `500` and passes in the full suite.
- FAIL, environment only — initial Maven attempts could not write cache metadata under the sandbox or resolved a `C:\.m2` path; the approved offline cache invocation above passed.

## Residual Risks and Optional Developer Checks

Inspect long names and modal/comparison layout at narrow phone width and perform a screen-reader smoke check of dialog announcements. Automated semantic and keyboard tests pass, but visual and assistive output were not manually observed.

## Disposition

`fixes-applied` — a fresh independent Step 5 context is required.
