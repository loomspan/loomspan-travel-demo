# Draft Promotion, Actionable Readiness, and Budget Overage Acknowledgment Testing Plan

## Change Summary
Deliver end-to-end regression and verification coverage for the Draft Promotion, Actionable Readiness Feedback with DOM jump navigation, and Budget Overage Warning and Acknowledgment modal dialog experience across the Progressive Trip Builder workspace and Draft Alternative cards.

## Impacted Areas and Risks
| Category | Risk | Planned evidence |
| --- | --- | --- |
| **Promotion Triggers** | Buttons missing in builder or card, or incorrectly enabled on expired trips. | `DraftPromotion.test.tsx`: asserts builder "Save as Planned Itinerary" and card "Promote to Planned" buttons render and are disabled when `isExpired === true`. |
| **Actionable Readiness** | Incomplete drafts fail to display server blocking issues or "Fix issue" buttons fail to scroll and focus target fields/slots. | `DraftPromotion.test.tsx`: simulates 400 `PLANNING_NOT_READY` with issue map; verifies `DraftReadinessBanner` renders all issues and clicking "Fix issue" moves DOM focus to empty age inputs, budget, component slots, and headings. |
| **Budget Overage Interception** | Over-budget draft promotion bypasses warning modal or confirmation button enables without explicit checkbox acknowledgment. | `DraftPromotion.test.tsx`: simulates 400 `BUDGET_OVERAGE_UNACKNOWLEDGED`; verifies `BudgetOverageModal` opens with formatted breakdown, and "Confirm and Save as Planned" button remains disabled until checkbox is checked. |
| **Acknowledgment Invalidation** | User edits form or components after viewing overage warning, but previous acknowledgment persists and silently bypasses subsequent warning. | `DraftPromotion.test.tsx`: closes overage modal, mutates budget or traveler count, re-promotes, and verifies fresh acknowledgment is required. |
| **Snapshot Lifecycle & Immutability** | Successful promotion fails to reflect Planned alternative immediately or leaves trip shared parameters editable in place. | `DraftPromotion.test.tsx`: verifies 201 response updates alternatives list with `Planned itinerary (read-only)` badge, disables shared inputs, and enables "Duplicate to draft". |
| **Concurrency Conflict (409)** | Version conflict crashes workspace or loses user inputs. | `DraftPromotion.test.tsx`: simulates 409 `VERSION_CONFLICT`; verifies local inputs are preserved and "Reload from server" recovery button is rendered. |
| **Keyboard Accessibility & Focus Management** | Modal fails to trap Tab focus, Escape key does not dismiss, or focus is not restored to trigger button on dismissal. | `DraftPromotion.test.tsx`: simulates Tab/Shift+Tab wrapping, Escape key dismissal, and trigger focus restoration. |
| **Status Announcements** | Screen readers receive no feedback during saving, error, or promotion success. | `DraftPromotion.test.tsx`: verifies `aria-live="polite"` status announcements for saving, success, and error states. |

## Existing Coverage and Environment Constraints
- Existing frontend tests (`npm.cmd test -- --run`) cover 55 tests in 5 test files (`identityApi.test.ts`, `tripsApi.test.ts`, `PasswordField.test.tsx`, `ProgressiveTripBuilder.test.tsx`, `App.test.tsx`).
- Tests run in `jsdom` with Vitest, `@testing-library/react`, and `@testing-library/user-event`.
- HTTP endpoints are mocked with `fetchMock = vi.fn()` returning mocked JSON responses.
- Backend MockMvc tests (`DraftReadinessAndPlannedSnapshotIntegrationTest.java`) already verify all backend readiness rules, inventory availability, overage acknowledgment enforcement, and snapshot persistence.
- No live network connection, credentials, or running database services are required for frontend tests.

## Failing Test First
- **Name**: `renders promotion button in progressive builder and attempts promotion on unready draft displaying readiness banner with actionable blocking issues`
- **Type**: Vitest component/integration test
- **Location**: `frontend/src/DraftPromotion.test.tsx`
- **Arrange/Act/Assert**:
  - *Arrange*: Render `TripWorkspace` with an incomplete mock trip (e.g. draft with no components or empty traveler ages) and stub `fetch` returning 400 `PLANNING_NOT_READY` with blocking issues `{ travelerAges: "Provide exact ages for every traveler before planning." }`.
  - *Act*: Query for button with accessible name `/save as planned itinerary/i` and click it.
  - *Assert*:
    - Pre-implementation failure: `getByRole('button', { name: /save as planned itinerary/i })` throws `TestingLibraryElementError: Unable to find an accessible element with the role "button" and name "/save as planned itinerary/i"`.
    - Post-implementation success: Button exists, click triggers promotion call, `DraftReadinessBanner` appears with heading "Draft Not Ready to Save as Planned", and clicking "Fix issue" focuses `#traveler-age-0`.
- **Expected pre-fix failure**: Element with role `button` and name `/save as planned itinerary/i` not found in `TripWorkspace`.

## Tests to Add or Update

### 1. `renders promotion button in progressive builder and draft alternative card, disabled when trip is expired`
- **Type**: Component test (React Testing Library)
- **Location**: `frontend/src/DraftPromotion.test.tsx`
- **Proves**: AC 1 — "Promote to Planned" / "Save as Planned Itinerary" action is accessible in the builder and Draft cards, and disabled when trip is expired.
- **Inputs/fixture**: `createMockTrip()` with 1 Draft alternative, tested with `isExpired = false` and `isExpired = true`.
- **Doubles or boundary isolation**: Fetch mocked; no network calls required.
- **Edge cases**: Planned alternative cards must NOT render promotion buttons.

### 2. `displays actionable blocking issues on unready draft and moves focus directly to missing field or slot when clicked`
- **Type**: Integration test
- **Location**: `frontend/src/DraftPromotion.test.tsx`
- **Proves**: AC 2 — Incomplete drafts display actionable blocking issues, and clicking an issue moves focus directly to the missing field or component slot.
- **Inputs/fixture**: Mock trip with unready draft; mock `POST .../plan` returning 400 `PLANNING_NOT_READY` with issues for `travelerAges`, `adult`, `budgetCents`, `components`, `airfare`, `stay`, `rental`.
- **Doubles or boundary isolation**: Fetch mock returning `PLANNING_NOT_READY`.
- **Edge cases**:
  - Missing traveler ages: focuses first empty age input (`#traveler-age-${i}`).
  - Missing adult: focuses `#traveler-age-0` with adult highlight.
  - Missing budget: focuses `#workspace-budget`.
  - Missing components: focuses `#builder-heading` / `.component-slots-grid`.
  - Stale/sold-out airfare/stay/rental: focuses `#airfare-slot-heading`, `#stay-slot-heading`, or `#rental-slot-heading`.

### 3. `intercepts over-budget draft promotion with modal requiring explicit checkbox acknowledgment`
- **Type**: Integration test
- **Location**: `frontend/src/DraftPromotion.test.tsx`
- **Proves**: AC 3 — Over-budget draft promotion displays warning modal showing budget, grand total, and overage, requiring explicit checkbox acknowledgment before enabling confirmation.
- **Inputs/fixture**: Draft with grand total $2,500 and budget $2,000; server returns 400 `BUDGET_OVERAGE_UNACKNOWLEDGED` with `budgetCents: '200000'`, `grandTotalCents: '250000'`, `budgetOverageCents: '50000'`.
- **Doubles or boundary isolation**: Fetch mock.
- **Edge cases**: Confirm button must remain disabled until checkbox is checked; unchecking disables button again.

### 4. `confirming overage sends acknowledgment and updates workspace to planned snapshot with read-only badges`
- **Type**: Integration test
- **Location**: `frontend/src/DraftPromotion.test.tsx`
- **Proves**: AC 4 — Successfully promoted drafts appear immediately as Planned snapshots with read-only badges and duplicate/delete actions.
- **Inputs/fixture**: User checks acknowledgment and confirms; mock returns 201 with updated trip containing Planned alternative.
- **Doubles or boundary isolation**: Fetch mock capturing request body (`budgetOverageAcknowledged: true`).
- **Edge cases**: Modal closes, `aria-live` announces success, Planned card displays "Planned itinerary (read-only)" badge, "Delete planned itinerary" and "Duplicate to draft" buttons appear.

### 5. `planned snapshots prevent in-place modification and duplicating produces a new mutable draft`
- **Type**: Integration test
- **Location**: `frontend/src/DraftPromotion.test.tsx`
- **Proves**: AC 5 — In-place modifications to Planned snapshots are prevented, and duplicating a Planned snapshot produces a new mutable Draft.
- **Inputs/fixture**: Trip with Planned alternative (`hasPlanned = true`).
- **Doubles or boundary isolation**: Fetch mock for `duplicateAlternative`.
- **Edge cases**: Inputs `#workspace-destination`, `#workspace-start-date`, `#workspace-end-date`, and `#workspace-traveler-count` have `disabled={true}`; read-only notice is displayed; clicking "Duplicate to draft" sends duplicate request and updates alternatives list.

### 6. `form edits following an overage warning invalidate previous client acknowledgment and require fresh acknowledgment`
- **Type**: Integration test
- **Location**: `frontend/src/DraftPromotion.test.tsx`
- **Proves**: AC 6 — Form edits following an overage warning invalidate previous client acknowledgment and require fresh acknowledgment on subsequent promotion attempts.
- **Inputs/fixture**: Over-budget draft; user cancels modal, mutates budget input or traveler count, and attempts promotion again.
- **Doubles or boundary isolation**: Fetch mock capturing second promotion request.
- **Edge cases**: Second request is sent with `budgetOverageAcknowledged: false`, ensuring server rejects with 400 and modal re-intercepts.

### 7. `verifies keyboard navigation, focus trapping, Escape dismissal, and aria-live status announcements`
- **Type**: Component/Accessibility test
- **Location**: `frontend/src/DraftPromotion.test.tsx`
- **Proves**: AC 7 — Keyboard navigation, focus trapping, Escape dismissal, and `aria-live` status announcements are verified.
- **Inputs/fixture**: Render `BudgetOverageModal` open.
- **Doubles or boundary isolation**: UserEvent keyboard simulation (`Tab`, `Shift+Tab`, `Escape`).
- **Edge cases**: Focus is trapped inside dialog; Escape closes dialog and restores focus to triggering button; `autosave-status` has `role="status"` and `aria-live="polite"`.

### 8. `handles version conflict (409 VERSION_CONFLICT) by preserving user edits and offering reload`
- **Type**: Integration test
- **Location**: `frontend/src/DraftPromotion.test.tsx`
- **Proves**: AC 4 / Concurrency resilience — Version conflict (409 `VERSION_CONFLICT`) preserves user input and presents "Reload from server" action.
- **Inputs/fixture**: Mock `POST .../plan` returning 409 `VERSION_CONFLICT`.
- **Doubles or boundary isolation**: Fetch mock.
- **Edge cases**: Form edits are not wiped; conflict alert renders with "Reload from server" button.

### 9. `maintains accessible layout and interactive controls under mobile viewport constraints`
- **Type**: Responsive layout / visual structure test
- **Location**: `frontend/src/DraftPromotion.test.tsx`
- **Proves**: AC 8 — Responsive tests confirm accessible layout and functionality on mobile viewports.
- **Inputs/fixture**: Window resize / viewport simulation (`window.innerWidth = 375`).
- **Doubles or boundary isolation**: DOM inspection of CSS classes and touch-target dimensions.
- **Edge cases**: Full-width buttons and wrapped banners.

## Safe Verification Commands
- Focused: `npm.cmd test -- src/DraftPromotion.test.tsx --run`
- Related suite: `npm.cmd test -- --run`
- Full safe suite: `mvn test` (backend) + `npm.cmd test -- --run` (frontend)

## Optional Developer Checks
- [ ] Open the application in a mobile browser or responsive developer tools (`<= 520px` width) and verify that the budget overage modal, readiness banner, and promotion buttons display cleanly with touch-friendly spacing.

## Exit Criteria
- [x] The planned red test fails for the intended reason before implementation, when applicable.
- [x] New and updated tests pass after implementation.
- [x] The broadest safe relevant repository test suite passes.
- [x] Acceptance criteria map to executable evidence.
- [x] Routine automated tests do not perform unintended live or destructive operations.
- [x] Material risks and edge cases identified above are covered.
- [x] Any optional check is reported as nonblocking and is not represented as already performed.
