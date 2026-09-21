# P03-T06 Deliver the Trips and Alternatives Profile Experience Code Review — Cycle 2

## Scope and Repository State

- **Target Ticket:** `P03-T06` — `ai/thoughts/tickets/2026-09-19-p03-t06-deliver-trips-profile-experience.md`
- **Reviewed Sources and Tests:**
  - `frontend/src/api/tripsApi.ts` & `frontend/src/api/tripsApi.test.ts`
  - `frontend/src/api/identityApi.ts`
  - `frontend/src/components/ProfileScreen.tsx`
  - `frontend/src/components/EmptyProfileState.tsx`
  - `frontend/src/components/TripListSection.tsx`
  - `frontend/src/components/TripCreateModal.tsx`
  - `frontend/src/components/TripWorkspace.tsx`
  - `frontend/src/components/AlternativeCard.tsx`
  - `frontend/src/components/RevisionSummaryBanner.tsx`
  - `frontend/src/components/TripRevisionModal.tsx`
  - `frontend/src/components/ConfirmDeleteModal.tsx`
  - `frontend/src/style.css`
  - `frontend/src/App.tsx` & `frontend/src/App.test.tsx`
- **Scope Summary:** Independent review of the complete frontend Trips and alternatives profile workspace implemented across React 19 components, REST client calls, responsive styling, optimistic concurrency handling, scoped deletion confirmations, and accessibility landmarks.

---

## Findings

### [P1] Clear trigger element reference upon focus restoration to prevent focus theft on subsequent state updates
- **Location:** `frontend/src/components/TripRevisionModal.tsx:44-59`, `frontend/src/components/ConfirmDeleteModal.tsx:44-60`, `frontend/src/components/TripCreateModal.tsx:41-54`
- **Scenario:** When a user opened and dismissed a modal (e.g. `TripRevisionModal`), `previousActiveElement.current` recorded the trigger button (e.g. "Revise Trip") and restored focus upon close, but did not reset `previousActiveElement.current = null`. In `TripRevisionModal`, the effect additionally included `trip` in its dependency array. Whenever a subsequent background autosave or mutation updated `trip` while the modal was closed, the effect ran again, found `previousActiveElement.current` truthy, and refocused the trigger button, stealing focus away from whatever input the user was actively typing into.
- **Impact:** Violates WCAG focus management principles and disrupts keyboard and screen reader input during debounced autosaves or background updates.
- **Evidence:** `TripRevisionModal.tsx` dependency array `[isOpen, trip]` without `previousActiveElement.current = null` caused spurious focus calls on any `trip` state transition.
- **Fix:** In `TripRevisionModal.tsx`, decouple the focus restoration effect from `trip` prop changes (`[isOpen]`), ensure `previousActiveElement.current` is attached to `document.body`, and reset `previousActiveElement.current = null` immediately after restoring focus across all modal dialogs.

### [P2] Support string buffering, clearing, typing, and inline validation for traveler count in Trip Workspace
- **Location:** `frontend/src/components/TripWorkspace.tsx:597-630`
- **Scenario:** The `workspace-traveler-count` number input previously bound its `value` directly to numeric `travelerCount` and guarded `onChange` with `if (!isNaN(val) && val >= 1 && val <= 8)`. If a user attempted to clear the input with Backspace (`e.target.value === ''`) or type a replacement digit (e.g. typing `4` with `1` present yielded `14`), the change handler ignored the event, snapping the input back to its prior value. Users could neither delete nor type multi-digit/replacement values freely. Furthermore, `validateInputs()` did not check traveler count, and no field error message was rendered.
- **Impact:** Users could not edit traveler count using normal keyboard backspacing or typing without mouse-selecting the single digit or relying on browser spinner buttons; invalid values provided no inline visual feedback.
- **Evidence:** Clearing or typing into `#workspace-traveler-count` was rejected by the numeric guard without updating component state.
- **Fix:** Buffer traveler count input as a string (`travelerCountInput`), allow free typing and clearing in `onChange`, parse and update traveler count and traveler ages when valid (1–8), validate integer range in `validateInputs()`, and render `{fieldErrors.travelerCount && <p className="field-error">}` with `aria-describedby`.

### [P2] Provide primary `<h1>` heading landmark and accessible logout action in Trip Workspace
- **Location:** `frontend/src/components/TripWorkspace.tsx:438-468`, `frontend/src/components/ProfileScreen.tsx:164-186`
- **Scenario:** When navigating from profile overview into workspace mode, `ProfileScreen` rendered `<TripWorkspace />` which defined `<h2 id="workspace-heading">{trip.label}</h2>` and omitted any `<h1>` element on the page. Consequently, the workspace page had no primary `<h1>` landmark, and `App.tsx`'s focus recovery (`document.querySelector('h1')?.focus()`) found nothing. Furthermore, `onLogout` and `logoutPending` were omitted from `TripWorkspaceProps`, leaving authenticated users unable to log out without first returning to "all trips", contradicting Requirement 1 and the implementation plan.
- **Impact:** Degraded screen reader navigation and heading structure (WCAG 1.3.1 / 2.4.6) and trapped users in the workspace without a direct logout action.
- **Evidence:** Inspection of rendered DOM in workspace mode showed no `<h1>` element, and no logout control in `TripWorkspace`.
- **Fix:** Render the trip label in `TripWorkspace` as `<h1 id="workspace-heading" tabIndex={-1}>{trip.label}</h1>`, pass `onLogout` and `logoutPending` to `TripWorkspace`, and render an accessible "Log out" button in `workspace-nav`.

---

## Findings Resolved in This Context

1. **[P1] Reset trigger reference upon focus restoration in modals:** Updated `TripRevisionModal.tsx`, `ConfirmDeleteModal.tsx`, and `TripCreateModal.tsx` to clear `previousActiveElement.current = null` after restoring focus and decoupled `TripRevisionModal`'s focus effect from `trip` prop changes.
2. **[P2] Buffer and validate traveler count input in Trip Workspace:** Updated `TripWorkspace.tsx` to maintain `travelerCountInput: string`, allow clearing and typing, validate the 1–8 range in `validateInputs()`, adjust `travelerAges` dynamically, and display `fieldErrors.travelerCount` with `aria-describedby`.
3. **[P2] Provide primary `<h1>` heading and accessible logout action in Trip Workspace:** Updated `TripWorkspace.tsx` to render `<h1 id="workspace-heading" tabIndex={-1}>{trip.label}</h1>`, added `onLogout` and `logoutPending` props to `TripWorkspace`, rendered an accessible "Log out" button in `workspace-nav`, and wired the handlers in `ProfileScreen.tsx`.
4. **Added automated tests:** Added integration tests in `frontend/src/App.test.tsx` verifying:
   - Clearing and typing traveler count in `TripWorkspace` with inline error validation.
   - Logging out directly from `TripWorkspace` and presence of the primary `<h1>` heading landmark.

---

## Acceptance-Criteria and Plan Conformance

| Criterion/decision | Code evidence | Test evidence | Result: implemented/partial/missing/safe deviation |
| --- | --- | --- | --- |
| 1. Empty profile onboarding and initial trip creation | `EmptyProfileState.tsx`, `TripCreateModal.tsx`, `tripsApi.createTrip` | `App.test.tsx`: `displays empty profile onboarding with Plan Trip action and creates an initial trip` | Implemented |
| 2. Multiple drafts, duplication, autosave debouncing, conflict handling, input retention | `TripWorkspace.tsx`, `tripsApi.replaceSharedDetails`, `StatusRegion.tsx` | `App.test.tsx`: `creates component-empty draft...`, `autosaves mutable traveler ages...`, `handles optimistic concurrency conflict (409 VERSION_CONFLICT)...`, `prevents redundant autosave calls...` | Implemented |
| 3. Upcoming and Past trips in backend order with nested alternatives, badges, no fabricated booking | `TripListSection.tsx`, `AlternativeCard.tsx`, `ProfileScreen.tsx` | `App.test.tsx`: `renders Upcoming and Past trips in backend order with clear nested hierarchy and status badges` | Implemented |
| 4. Planned alternatives read-only, revision workflow, change summaries | `AlternativeCard.tsx`, `TripRevisionModal.tsx`, `RevisionSummaryBanner.tsx` | `App.test.tsx`: `displays Planned alternative as visibly read-only...`, `displays structured revision summary...`, `opens TripRevisionModal...` | Implemented |
| 5. Scoped deletion confirmations for Draft, Planned, and Trip with server counts & booking history guard | `ConfirmDeleteModal.tsx`, `tripsApi.delete*`, `ProfileScreen.tsx`, `TripWorkspace.tsx` | `App.test.tsx`: `confirms and executes scoped draft deletion...`, `blocks trip deletion when server reports booking history`, `handles stale trip deletion confirmation...`, `disables trip deletion inside workspace when trip has booking history` | Implemented |
| 6. Keyboard navigation, visible focus, dialog focus trap, focus restoration, 320px responsive styling | `style.css`, focus management in `ConfirmDeleteModal.tsx`, `TripCreateModal.tsx`, `TripRevisionModal.tsx` | `App.test.tsx`: `enforces accessible keyboard navigation...`, `restores focus to section heading when deleted trip card button is removed from DOM` | Implemented |
| 7. Full frontend and backend test verification | `tripsApi.test.ts`, `App.test.tsx`, `TripApiIntegrationTest.java` | `npm.cmd --prefix frontend test -- --run` (36 tests pass), `npm.cmd --prefix frontend run build` (clean build), `.\mvnw.cmd test-compile` (clean compile) | Implemented |
| 8. Existing identity flows (login, register, logout, password change, CSRF, About demo) intact | `ProfileScreen.tsx`, `AboutDemoTab.tsx`, `identityApi.ts` | All 9 pre-existing identity tests in `App.test.tsx` pass without regression | Implemented |
| 9. Zero Phase 4–6 experiences introduced | Scope boundaries preserved across all components (no catalog search, comparison totals, or booking/payment controls) | Code inspection confirms zero Phase 4–6 capabilities | Implemented |

---

## Active Project Guardrails

- None recorded in `ai/thoughts/design-lens.md`.

---

## Open Questions and Assumptions

- None.

---

## Verification Results

- PASS — `npm.cmd --prefix frontend test -- --run` — 36 tests pass across 4 test suites (`identityApi.test.ts`, `tripsApi.test.ts`, `PasswordField.test.tsx`, `App.test.tsx`).
- PASS — `npm.cmd --prefix frontend run build` — TypeScript type-checking and Vite production asset bundling succeed without errors.
- PASS — `.\mvnw.cmd test-compile` — Maven frontend-install, frontend-build, resource copying, Java production compile, and test compile succeed cleanly.

---

## Residual Risks and Optional Developer Checks

- Nonblocking optional check: Run `npm.cmd --prefix frontend run dev` and test interactive browser experience at 320px mobile viewport width in Chrome DevTools to visually inspect card padding and responsive wrapping.

---

## Disposition

- `fixes-applied`
