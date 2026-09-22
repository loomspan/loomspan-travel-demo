# Draft Promotion, Actionable Readiness, and Budget Overage Acknowledgment Implementation Plan

## Overview
- Ticket: `ai/thoughts/tickets/2026-09-22-p05-t03-deliver-draft-promotion-and-readiness-experience.md`
- Research: `ai/thoughts/research/2026-09-22-p05-t03-deliver-draft-promotion-and-readiness-experience.md`
- Outcome: Enable authenticated users to promote ready Draft itineraries into immutable Planned snapshots directly from the Progressive Trip Builder and Draft Alternative cards, receive actionable visual readiness feedback directing focus to blocking issues, review and acknowledge budget overages via an accessible modal dialog, and observe real-time state synchronization with snapshot locking and concurrency conflict resolution.

## Current State
- The backend readiness validation engine (`TripService.java:605-727`), snapshot persistence schema (Flyway V16 tables `detour_planned_itinerary`, `detour_planned_airfare_snapshot`, `detour_planned_stay_snapshot`, `detour_planned_rental_snapshot`), and REST endpoints (`GET /api/trips/{tripId}/drafts/{draftId}/readiness`, `POST /api/trips/{tripId}/drafts/{draftId}/plan`) are complete and verified by MockMvc integration tests (`DraftReadinessAndPlannedSnapshotIntegrationTest.java`).
- The frontend API client (`frontend/src/api/tripsApi.ts:100-106, 663-668`) defines `DraftReadinessResponse`, `PromotionRequest`, `tripsApi.getDraftReadiness`, and `tripsApi.promoteDraft`.
- In `frontend/src/components/TripWorkspace.tsx`, the workspace renders the Progressive Trip Builder (`builder-section`, lines 778–846), Trip Details form (`shared-details-heading`, lines 848–1004), and Alternatives list (`alternatives-heading`, lines 1006–1044).
- Neither `TripWorkspace.tsx` nor `AlternativeCard.tsx` currently exposes a promotion action button.
- There is no readiness feedback banner to present blocking issues or guide users to invalid fields or component slots.
- There is no budget-overage modal dialog to require explicit acknowledgment before saving an over-budget itinerary.
- When an over-budget draft is promoted without acknowledgment, the server rejects it with HTTP 400 `BUDGET_OVERAGE_UNACKNOWLEDGED`, but the client does not capture this error to present an overage dialog or track acknowledgment invalidation on subsequent form edits.

## Desired End State
- **Promotion Actions**:
  - The Progressive Trip Builder workspace renders a prominent "Save as Planned Itinerary" button in its section header.
  - Each Draft `AlternativeCard` renders a "Promote to Planned" button in its actions container.
  - Both actions are disabled when the trip is expired (`isExpired === true`).
- **Actionable Readiness Feedback**:
  - When promotion is attempted on an unready draft (or readiness check is requested), the server's blocking issues map (`PLANNING_NOT_READY` or readiness response) is rendered in an accessible `DraftReadinessBanner` above the builder.
  - Each issue displays a descriptive message and an actionable "Fix issue" button that immediately scrolls to and focuses the relevant DOM element:
    - Missing traveler ages -> first empty age input (`#traveler-age-${i}`).
    - Missing adult (18+) -> first traveler age input (`#traveler-age-0`) with adult requirement visual highlight.
    - Missing budget -> `#workspace-budget`.
    - Missing components -> `#builder-heading` / `.component-slots-grid`.
    - Stale or sold-out component -> `#airfare-slot-heading`, `#stay-slot-heading`, or `#rental-slot-heading` with slot highlighted and replace/remove options.
    - Invalid destination/dates/travelers -> `#workspace-destination`, `#workspace-start-date`, or `#workspace-traveler-count`.
- **Budget Overage Interception and Acknowledgment**:
  - When an over-budget draft is promoted, the attempt is intercepted by an accessible `BudgetOverageModal` dialog displaying:
    - Overall trip budget.
    - Itinerary grand total.
    - Exact budget overage amount clearly highlighted with `role="alert"`.
  - The "Confirm and Save as Planned" button remains disabled until the user checks the explicit acknowledgment checkbox: *"I understand this itinerary exceeds my overall trip budget"*.
  - Submitting sends `budgetOverageAcknowledged: true` in the promotion request.
  - Editing any trip detail (destination, dates, travelers, traveler ages, budget) or component selection (airfare, stay, rental) resets the acknowledgment state, forcing fresh acknowledgment on subsequent promotion attempts.
- **Lifecycle Transitions and State Synchronization**:
  - On successful promotion (HTTP 201), the workspace updates immediately:
    - The new Planned alternative appears in the Alternatives list with a `Planned itinerary (read-only)` badge (`badge-planned`) and actions for "Duplicate to draft" and "Delete planned itinerary".
    - The persistent summary tally reflects the authoritative server numbers.
    - `hasPlanned` becomes true, locking trip-level destination, dates, and traveler count fields against in-place edits and showing the revision notice.
    - An `aria-live="polite"` status message announces successful promotion.
  - Version conflicts (HTTP 409 `VERSION_CONFLICT`) preserve user input and present a "Reload from server" recovery action.
- **Accessibility and Responsive Design**:
  - Full keyboard accessibility, modal focus trapping, Escape key closing, and focus restoration to the triggering button.
  - Responsive stacking and touch-friendly targets on mobile viewports (`max-width: 520px`).

## Scope

### In scope
1. Enhancing `IdentityApiError` in `frontend/src/api/identityApi.ts` to capture `apiMessage` from `envelope.message`.
2. Creating `BudgetOverageModal.tsx` with accessible dialog semantics, focus trapping, Escape handling, focus restoration, overage breakdown, and mandatory acknowledgment checkbox.
3. Creating `DraftReadinessBanner.tsx` with accessible alert/region semantics, issue list rendering, dismiss action, and direct jump/focus handlers.
4. Updating `AlternativeCard.tsx` to accept `onPromoteDraft` and render the "Promote to Planned" button on Draft cards with expired trip disablement.
5. Updating `AirfareSlot.tsx`, `StaySlot.tsx`, and `RentalSlot.tsx` to ensure headings have `tabIndex={-1}` for programmatic focus and support visual highlighting.
6. Updating `TripWorkspace.tsx` to orchestrate promotion requests, capture `PLANNING_NOT_READY` and `BUDGET_OVERAGE_UNACKNOWLEDGED` errors, manage overage modal and readiness banner states, implement jump-to-field logic, invalidate acknowledgments on form/component edits, handle 409 concurrency conflicts, and synchronize workspace state upon snapshot creation.
7. Adding styling in `frontend/src/style.css` for the readiness banner, budget overage modal, overage callout, highlighted slots/fields, and mobile responsiveness.
8. Writing comprehensive automated Vitest tests covering all 8 acceptance criteria.

### Out of scope
- Multi-alternative side-by-side comparison matrix (deferred to P05-T04).
- Simulated booking checkout or payment flows (deferred to Phase 6).
- Version 2 Events or WebSocket push updates.
- Changes to backend readiness evaluation logic or database schema (already delivered and passing in P05-T02).

## Active Project Guardrails
- `None recorded` in `ai/thoughts/design-lens.md`.

## Impact and Risk Analysis
- **Accessibility & Focus Management**:
  - *Risk*: Opening/closing modals or clicking jump actions could lose user focus or cause focus disorientations for screen-reader users.
  - *Mitigation*: Adhere strictly to the focus management pattern established in `ConfirmDeleteModal.tsx` and `ConfirmRemoveModal.tsx`: store `previousActiveElement.current`, autofocus modal control, trap Tab/Shift+Tab, dismiss on Escape, and restore focus upon dismissal.
- **Form Invalidation State Synchronization**:
  - *Risk*: A user acknowledges an overage, dismisses the modal, increases the price or changes dates/budget, and a subsequent promotion silently reuses the stale acknowledgment.
  - *Mitigation*: Reset `budgetOverageAcknowledged` to `false` in every state setter for trip shared details (destination, dates, travelers, ages, budget) and component selection/removal handlers.
- **Optimistic Concurrency (409 VERSION_CONFLICT)**:
  - *Risk*: A background autosave or concurrent tab mutation modifies the trip version, causing promotion to fail with HTTP 409.
  - *Mitigation*: Catch 409 responses, preserve all local draft and form state, display the conflict alert with "Reload from server", and announce via `aria-live`.
- **Planned Snapshot Immutability**:
  - *Risk*: Promoting a draft could leave the form inputs editable, leading to desynchronization between planned snapshots and shared trip parameters.
  - *Mitigation*: `TripWorkspace.tsx` already uses `hasPlanned = trip.planned && trip.planned.length > 0` to disable `#workspace-destination`, `#workspace-start-date`, `#workspace-end-date`, and `#workspace-traveler-count`. When `promoteDraft` succeeds, `trip.planned` is populated, immediately enforcing read-only trip constraints.

## Implementation Approach
1. **API Client & Error Propagation**:
   - In `frontend/src/api/identityApi.ts`, update `IdentityApiError` to preserve `apiMessage` extracted from `envelope.message`. This allows downstream error handling in `TripWorkspace` to inspect both error codes (`PLANNING_NOT_READY`, `BUDGET_OVERAGE_UNACKNOWLEDGED`, `VERSION_CONFLICT`, `ALTERNATIVE_EXPIRED`) and server error messages.
2. **Accessible Modal & Banner Components**:
   - `BudgetOverageModal.tsx`: Pure accessible modal component modeled after `ConfirmDeleteModal.tsx`. Takes `isOpen`, `budgetCents`, `grandTotalCents`, `budgetOverageCents`, `pending`, `errorMessage`, `onClose`, and `onConfirm`. Manages internal `acknowledged` checkbox state, resetting to `false` on open.
   - `DraftReadinessBanner.tsx`: Accessible banner rendering server blocking issues. Takes `issues: Record<string, string>`, `onJumpTo: (key: string) => void`, and `onDismiss: () => void`. Renders a list where each item provides a "Fix issue" button.
3. **Slot Enhancements**:
   - In `AirfareSlot.tsx`, `StaySlot.tsx`, and `RentalSlot.tsx`, add `tabIndex={-1}` to heading elements (`#airfare-slot-heading`, `#stay-slot-heading`, `#rental-slot-heading`) and support a `highlighted` prop to visually accent the slot when directed from a readiness issue.
4. **Workspace Promotion Orchestration (`TripWorkspace.tsx`)**:
   - Expose "Save as Planned Itinerary" in the progressive builder header and "Promote to Planned" on `AlternativeCard`.
   - Maintain promotion state: `promotionPending`, `readinessIssues`, `isReadinessBannerOpen`, `isOverageModalOpen`, `overageDetails`, `budgetOverageAcknowledged`, `highlightedSlot`.
   - Implement `handlePromoteDraft(draftId, draftVersion, forceAcknowledged)`:
     - Calls `tripsApi.promoteDraft`.
     - Catches `PLANNING_NOT_READY` -> renders `DraftReadinessBanner`.
     - Catches `BUDGET_OVERAGE_UNACKNOWLEDGED` -> opens `BudgetOverageModal`.
     - Catches `VERSION_CONFLICT` -> sets conflict status with reload action.
     - On 201 -> applies updated trip state, closes modals, announces success via `aria-live`.
   - Implement `handleJumpToIssue(key)`:
     - Resolves the target DOM element based on `key` (`travelerAges`, `adult`, `budgetCents`, `components`, `airfare`, `stay`, `rental`, `destination`, `dates`, `travelerCount`).
     - Smoothly scrolls into view and programmatically focuses the element.
     - If `key === 'travelerAges'`, locates the first input with empty/invalid value.
     - If `key === 'adult'`, focuses traveler 1 age and sets adult highlight.
5. **Styling & Responsive Layout (`style.css`)**:
   - Add styles for the readiness banner, overage modal, overage callout, highlighted slots, and mobile stacking rules.

---

## Phase 1: API Error Preservation and Component Foundation

### Changes
- [x] `frontend/src/api/identityApi.ts` — Update `IdentityApiError` to accept `apiMessage?: string` and populate it from `envelope.message` in `request<T>()`.
- [x] `frontend/src/components/BudgetOverageModal.tsx` — Create accessible modal component with `role="dialog"`, `aria-modal="true"`, focus trap, Escape dismissal, overage amount breakdown, acknowledgment checkbox, and confirm button.
- [x] `frontend/src/components/DraftReadinessBanner.tsx` — Create accessible readiness banner with `role="region"`, dismiss button, list of blocking issues, and "Fix issue" jump buttons.
- [x] `frontend/src/components/AlternativeCard.tsx` — Add `onPromoteDraft?: (draftId: string, version: number) => void` prop and render "Promote to Planned" button on Draft cards with `disabled={tripExpired}`.
- [x] `frontend/src/components/AirfareSlot.tsx` — Add `tabIndex={-1}` to `#airfare-slot-heading` and support `highlighted?: boolean` styling.
- [x] `frontend/src/components/StaySlot.tsx` — Add `tabIndex={-1}` to `#stay-slot-heading` and support `highlighted?: boolean` styling.
- [x] `frontend/src/components/RentalSlot.tsx` — Add `tabIndex={-1}` to `#rental-slot-heading` and support `highlighted?: boolean` styling.

### Automated verification
- [x] `npm.cmd test -- --run` — Existing 55 tests continue to pass with no regressions.

### Optional developer checks
- [ ] None.

---

## Phase 2: Workspace Promotion State, Jump Navigation, and Acknowledgment Invalidation

### Changes
- [x] `frontend/src/components/TripWorkspace.tsx` — Add promotion states (`readinessIssues`, `isReadinessBannerOpen`, `isOverageModalOpen`, `overageDetails`, `budgetOverageAcknowledged`, `promotionPending`, `highlightedSlot`).
- [x] `frontend/src/components/TripWorkspace.tsx` — Implement `handlePromoteDraft(draftId, draftVersion, forceAcknowledged)` to call `tripsApi.promoteDraft`, handle 201 success (state update, `aria-live` announcement), catch 400 `PLANNING_NOT_READY` (populate `readinessIssues`), catch 400 `BUDGET_OVERAGE_UNACKNOWLEDGED` (open overage modal), and catch 409 `VERSION_CONFLICT` (preserve inputs and trigger conflict status).
- [x] `frontend/src/components/TripWorkspace.tsx` — Implement `handleJumpToIssue(key)` to map server issue keys (`travelerAges`, `adult`, `budgetCents`, `components`, `airfare`, `stay`, `rental`, `destination`, `dates`, `travelerCount`) to exact DOM elements, scroll them into view, and set focus.
- [x] `frontend/src/components/TripWorkspace.tsx` — Add "Save as Planned Itinerary" button in the builder section header with `disabled={isExpired || promotionPending}` and wire `onPromoteDraft` on `AlternativeCard`.
- [x] `frontend/src/components/TripWorkspace.tsx` — Render `DraftReadinessBanner` and `BudgetOverageModal`.
- [x] `frontend/src/components/TripWorkspace.tsx` — Invalidate `budgetOverageAcknowledged = false` on every shared details change (budget, dates, traveler count, traveler ages, destination) and component selection/removal.

### Automated verification
- [x] `npm.cmd test -- --run` — Existing tests pass.

### Optional developer checks
- [ ] None.

---

## Phase 3: CSS Styles and Responsive Layout

### Changes
- [x] `frontend/src/style.css` — Add styles for `.promote-draft-btn`, `.readiness-banner`, `.readiness-issues-list`, `.readiness-issue-item`, `.readiness-jump-button`, `.budget-overage-modal`, `.budget-overage-breakdown`, `.breakdown-row`, `.breakdown-overage`, `.overage-highlight`, `.acknowledgment-checkbox-container`, and `.slot-highlighted`.
- [x] `frontend/src/style.css` — In `@media (max-width: 520px)`, add responsive rules for full-width action buttons, modal stacking, and banner wrapping.

### Automated verification
- [x] `npm.cmd test -- --run` — Tests pass.

### Optional developer checks
- [ ] Visual verification of modal and banner responsiveness in browser if applicable.

---

## Phase 4: Comprehensive Test Suite for Draft Promotion and Readiness Experience

### Changes
- [x] `frontend/src/DraftPromotion.test.tsx` — Create test suite verifying:
  1. Promotion buttons rendered in Progressive Builder workspace and Draft AlternativeCard, disabled when trip is expired.
  2. Incomplete draft displays actionable readiness banner with blocking issues returned by the server (`travelerAges`, `adult`, `budgetCents`, `components`, `airfare`, `stay`, `rental`), and clicking an issue moves DOM focus directly to the target element.
  3. Over-budget draft promotion intercepts with budget overage modal, showing budget, grand total, and overage, with "Confirm and Save as Planned" disabled until explicit acknowledgment checkbox is checked.
  4. Confirming overage sends `budgetOverageAcknowledged: true`, and successful promotion updates workspace state: newly created Planned snapshot appears under Alternatives with `Planned itinerary (read-only)` badge, and tally reflects authoritative server numbers.
  5. Planned snapshots prevent in-place modification (destination, dates, travelers disabled; card shows read-only notice), and "Duplicate to draft" produces a new mutable Draft.
  6. Closing modal and editing form fields (budget, ages, dates, or component selections) invalidates previous acknowledgment and requires fresh acknowledgment on subsequent promotion attempt.
  7. Accessibility: `aria-modal="true"`, focus trapping within overage modal, Escape key dismissal restoring focus to triggering button, and `aria-live="polite"` status announcements.
  8. Concurrency conflict handling (409 `VERSION_CONFLICT`) preserves user input and presents "Reload from server".

### Automated verification
- [x] `npm.cmd test -- --run` — All tests pass, including the new `DraftPromotion.test.tsx` test suite.

### Optional developer checks
- [ ] None.

---

## Test Strategy
- **Unit and Component Testing**:
  - Test `BudgetOverageModal` isolation: modal accessibility attributes (`role="dialog"`, `aria-modal="true"`, `aria-labelledby`), checkbox enable/disable behavior for the confirmation button, Escape key dismissal, and Tab focus cycling.
  - Test `DraftReadinessBanner` isolation: rendering server-supplied blocking issue messages and executing `onJumpTo` callbacks.
  - Test `AlternativeCard`: promotion button rendering on Draft cards, omission on Planned cards, and disablement when `tripExpired={true}`.
- **Integration Testing in `TripWorkspace` / `App`**:
  - Mock fetch requests to simulate server responses:
    - 400 `PLANNING_NOT_READY` with various `fields` maps.
    - 400 `BUDGET_OVERAGE_UNACKNOWLEDGED` with overage amounts.
    - 201 Created with updated `TripResponse` containing the new `PlannedResponse` and incremented versions.
    - 409 `VERSION_CONFLICT` with conflict reload workflow.
    - 400 `ALTERNATIVE_EXPIRED` with expiration notification.
  - Verify DOM focus transitions:
    - Clicking "Fix issue" for `travelerAges` focuses the first empty `#traveler-age-${i}` input.
    - Clicking "Fix issue" for `adult` focuses `#traveler-age-0`.
    - Clicking "Fix issue" for `budgetCents` focuses `#workspace-budget`.
    - Clicking "Fix issue" for `components` focuses `#builder-heading`.
    - Clicking "Fix issue" for `airfare` focuses `#airfare-slot-heading`.
    - Clicking "Fix issue" for `stay` focuses `#stay-slot-heading`.
    - Clicking "Fix issue" for `rental` focuses `#rental-slot-heading`.
  - Verify acknowledgment invalidation:
    - User opens overage modal, dismisses modal, changes budget dollars or traveler count, and attempts promotion again -> modal reappears and requires fresh acknowledgment.

---

## Acceptance-Criteria Traceability

| Acceptance criterion | Planned code evidence | Planned test evidence |
| --- | --- | --- |
| "Promote to Planned" action is accessible in the progressive trip builder and Draft alternative cards. | `TripWorkspace.tsx` `.promote-draft-btn` in builder header; `AlternativeCard.tsx` `.promote-draft-btn` in `.alternative-card-actions`. | `DraftPromotion.test.tsx`: "renders promotion buttons in progressive builder and draft alternative cards, disabled when trip is expired". |
| Incomplete Drafts display actionable blocking issues, and clicking an issue moves focus directly to the missing field or component slot. | `DraftReadinessBanner.tsx`, `TripWorkspace.tsx:handleJumpToIssue`, focusing target inputs and component slot headings with `tabIndex={-1}`. | `DraftPromotion.test.tsx`: "displays actionable blocking issues on unready draft and moves focus directly to missing field or slot when clicked". |
| Over-budget Draft promotion displays a warning modal showing budget, grand total, and overage, requiring explicit checkbox acknowledgment before enabling confirmation. | `BudgetOverageModal.tsx` showing budget, grand total, and overage, with submit disabled until checkbox is checked. | `DraftPromotion.test.tsx`: "intercepts over-budget promotion with modal requiring explicit checkbox acknowledgment". |
| Successfully promoted Drafts appear immediately as Planned snapshots with read-only badges and duplicate/delete actions. | `TripWorkspace.tsx:handlePromoteDraft` calls `applyTripState`, updating `trip.alternatives` with `badge-planned` and duplicate/delete actions. | `DraftPromotion.test.tsx`: "updates workspace state with Planned snapshot, read-only badge, and duplicate/delete actions upon successful promotion". |
| In-place modifications to Planned snapshots are prevented, and duplicating a Planned snapshot produces a new mutable Draft. | `TripWorkspace.tsx` `disabled={hasPlanned}` on shared inputs; `AlternativeCard.tsx` displays read-only notice; `handleDuplicatePlanned` creates mutable Draft. | `DraftPromotion.test.tsx`: "prevents in-place modifications to planned snapshots and creates new mutable draft when duplicated". |
| Form edits following an overage warning invalidate previous client acknowledgment and require fresh acknowledgment on subsequent promotion attempts. | `TripWorkspace.tsx` resets `budgetOverageAcknowledged = false` on budget, date, traveler, age, or component mutations. | `DraftPromotion.test.tsx`: "invalidates previous overage acknowledgment when user edits form fields, requiring fresh acknowledgment". |
| Keyboard navigation, focus trapping, Escape key dismissal, and `aria-live` status announcements are verified in Vitest tests. | `BudgetOverageModal.tsx` Tab trap and Escape handler; `StatusRegion` / `autosave-status` `aria-live="polite"`. | `DraftPromotion.test.tsx`: "verifies keyboard navigation, focus trapping, Escape dismissal, and aria-live status announcements". |
| Responsive tests confirm accessible layout and functionality on both desktop and mobile viewports. | `style.css` `@media (max-width: 520px)` rules for modal, banner, and button stacking. | `DraftPromotion.test.tsx`: "maintains accessible layout and interactive controls under mobile viewport constraints". |

---

## Risks and Rollback/Recovery
- **Risk**: Programmatic focus (`el.focus()`) fails if target element is hidden, disabled, or not focusable.
  - *Recovery*: Ensure target headings have `tabIndex={-1}`, and fallback to container or section heading if a specific input is not in the DOM.
- **Risk**: Uncaught API exceptions crash the promotion flow.
  - *Recovery*: Wrap `tripsApi.promoteDraft` in robust try/catch block handling all `IdentityApiError` codes and network errors, displaying safe user-facing feedback.
- **Rollback**: Changes are strictly additive to frontend components and styles; reverting changes to `TripWorkspace.tsx`, `AlternativeCard.tsx`, and reverting new modal/banner files completely restores the prior state without affecting backend or database data.

## References
- Ticket: `ai/thoughts/tickets/2026-09-22-p05-t03-deliver-draft-promotion-and-readiness-experience.md`
- Research: `ai/thoughts/research/2026-09-22-p05-t03-deliver-draft-promotion-and-readiness-experience.md`
- Backend Service: `src/main/java/app/detour/trip/TripService.java`
- Backend Controller: `src/main/java/app/detour/trip/TripController.java`
- API Client: `frontend/src/api/tripsApi.ts`
- Workspace Component: `frontend/src/components/TripWorkspace.tsx`
- Modal Reference: `frontend/src/components/ConfirmDeleteModal.tsx`
