# P05-T03 Code Review — Cycle 1

## Scope and Repository State

- **Date:** 2026-09-22
- **Ticket:** `ai/thoughts/tickets/2026-09-22-p05-t03-deliver-draft-promotion-and-readiness-experience.md`
- **Execution Profile:** `full` (Full 5-Step Pipeline)
- **Base Commit:** `7aed48e95ef9cfb2597cfa35263c9208b93f86e3`
- **Reviewed production files:**
  - `frontend/src/api/identityApi.ts`
  - `frontend/src/components/BudgetOverageModal.tsx`
  - `frontend/src/components/DraftReadinessBanner.tsx`
  - `frontend/src/components/AlternativeCard.tsx`
  - `frontend/src/components/AirfareSlot.tsx`
  - `frontend/src/components/StaySlot.tsx`
  - `frontend/src/components/RentalSlot.tsx`
  - `frontend/src/components/TripWorkspace.tsx`
  - `frontend/src/style.css`
- **Reviewed test files:**
  - `frontend/src/DraftPromotion.test.tsx`

## Findings

No actionable findings remain open. All identified issues were resolved and verified within this cycle.

## Findings Resolved in This Context

### [P2] Exclude disabled elements and enforce DOM tree order in `BudgetOverageModal` focus trapping
- **Location:** `frontend/src/components/BudgetOverageModal.tsx:55-70`
- **Scenario:** When `BudgetOverageModal` opens, the "Confirm and Save as Planned" button is disabled by default until the acknowledgment checkbox is checked. The previous focus trap queried `modalRef.current.querySelectorAll(...)` without excluding `:not(:disabled)` and without guaranteeing tree order across compound selectors. In browser sequential focus navigation, disabled elements are skipped. When the user pressed Tab on the Cancel button, the handler did not intercept because `document.activeElement` (`cancel-button`) did not match the disabled button (`last`), allowing keyboard focus to escape the modal dialog into the background window. Furthermore, Shift+Tab from the checkbox attempted to focus the disabled confirm button (a no-op in browsers).
- **Impact:** Complete failure of dialog focus trapping for keyboard and screen-reader users during the initial unacknowledged state.
- **Evidence:** Reproducible in Vitest where `user.tab()` from `cancelButton` escaped to `document.body`.
- **Fix:** Used `Array.from(modalRef.current.querySelectorAll('*')).filter(el => el.matches('button:not(:disabled), [href], input:not(:disabled), select:not(:disabled), textarea:not(:disabled), [tabindex]:not([tabindex="-1"]):not(:disabled)'))`. Verified that Tab wraps between enabled controls (`cancel-button` and checkbox) when the confirm button is disabled, and includes the confirm button when acknowledged.

### [P2] Disable "Promote to Planned" on AlternativeCard and guard `handlePromoteDraft` against duplicate in-flight requests
- **Location:** `frontend/src/components/AlternativeCard.tsx:107-115`, `frontend/src/components/TripWorkspace.tsx:740-830`
- **Scenario:** The Progressive Builder's promote button properly disabled during promotion (`disabled={isExpired || promotionPending}`), but `AlternativeCard`'s "Promote to Planned" button only checked `disabled={tripExpired}` and omitted `promotionPending`. In addition, `handlePromoteDraft` lacked an in-flight guard (`isPromotingRef.current || promotionPending`). Rapid double-clicking or clicking the alternative card promote action while a promotion was in flight fired duplicate POST requests to `/api/trips/{tripId}/drafts/{draftId}/plan`.
- **Impact:** The first promotion request succeeded and bumped the trip/draft version; the second parallel request failed with HTTP 409 `VERSION_CONFLICT`, erroneously presenting the user with an alarming conflict alert ("The Trip has changed on the server. Reload before saving.").
- **Evidence:** Code trace of `AlternativeCard.tsx` and `handlePromoteDraft`.
- **Fix:** Added `promotionPending?: boolean` to `AlternativeCardProps`, bound it to the button (`disabled={tripExpired || promotionPending}` with loading text), passed `promotionPending` from `TripWorkspace`, and added synchronous `isPromotingRef.current` guard in `handlePromoteDraft`.

### [P3] Use `targetDraft` instead of `activeDraft` in `BUDGET_OVERAGE_UNACKNOWLEDGED` fallback tally
- **Location:** `frontend/src/components/TripWorkspace.tsx:780-788`
- **Scenario:** If `err.fields.grandTotalCents` was absent in an overage error and promotion was initiated from an Alternative Card for a draft other than `trip.drafts[0]`, the fallback tally computation used `activeDraft?.tally?.grandTotalCents` instead of the specific draft being promoted.
- **Impact:** Potential display of mismatched grand total and overage amount in the modal fallback path.
- **Evidence:** Inspection of `TripWorkspace.tsx:785`.
- **Fix:** Derived `const targetDraft = trip.drafts?.find((d) => d.id === draftId) ?? activeDraft;` and used `targetDraft?.tally?.grandTotalCents`.

## Acceptance-Criteria and Plan Conformance

| Criterion/decision | Code evidence | Test evidence | Result: implemented/partial/missing/safe deviation |
| --- | --- | --- | --- |
| AC 1: "Promote to Planned" action is accessible in builder and Draft alternative cards, disabled when expired | `TripWorkspace.tsx:984-993`, `AlternativeCard.tsx:106-116` | `DraftPromotion.test.tsx`: test 1 | implemented |
| AC 2: Incomplete Drafts display actionable blocking issues, and clicking an issue moves focus directly to missing field or slot | `DraftReadinessBanner.tsx:43-80`, `TripWorkspace.tsx:839-890` (`handleJumpToIssue`), `AirfareSlot.tsx:46`, `StaySlot.tsx:49`, `RentalSlot.tsx:45` (`tabIndex={-1}`) | `DraftPromotion.test.tsx`: test 2 | implemented |
| AC 3: Over-budget Draft promotion displays warning modal showing budget, grand total, and overage, requiring explicit checkbox acknowledgment | `BudgetOverageModal.tsx:79-152`, `TripWorkspace.tsx:780-800, 1320-1336` | `DraftPromotion.test.tsx`: test 3 | implemented |
| AC 4: Successfully promoted Drafts appear immediately as Planned snapshots with read-only badges and duplicate/delete actions | `TripWorkspace.tsx:756-768`, `AlternativeCard.tsx:118-139` | `DraftPromotion.test.tsx`: test 4 | implemented |
| AC 5: In-place modifications to Planned snapshots are prevented, and duplicating a Planned snapshot produces a new mutable Draft | `TripWorkspace.tsx:1094-1185` (`disabled={hasPlanned}`), `AlternativeCard.tsx:120`, `TripWorkspace.tsx:449-463` (`handleDuplicatePlanned`) | `DraftPromotion.test.tsx`: test 5 | implemented |
| AC 6: Form edits following overage warning invalidate previous client acknowledgment and require fresh acknowledgment on subsequent promotion | `TripWorkspace.tsx:1091, 1113, 1130, 1155, 1180, 729` (resets `setBudgetOverageAcknowledged(false)`) | `DraftPromotion.test.tsx`: test 6 | implemented |
| AC 7: Keyboard navigation, focus trapping, Escape key dismissal, and aria-live status announcements are verified in Vitest tests | `BudgetOverageModal.tsx:49-74`, `StatusRegion.tsx`, `TripWorkspace.tsx:940-955` (`role="status" aria-live="polite"`) | `DraftPromotion.test.tsx`: test 7 | implemented |
| AC 8: Responsive tests confirm accessible layout and functionality on both desktop and mobile viewports | `style.css:160-214` (`@media (max-width: 520px)`), `BudgetOverageModal.tsx`, `DraftReadinessBanner.tsx` | `DraftPromotion.test.tsx`: test 9 | implemented |

## Active Project Guardrails

- `None recorded` in `ai/thoughts/design-lens.md`.

## Open Questions and Assumptions

- None.

## Verification Results

- PASS — `npm.cmd test -- src/DraftPromotion.test.tsx --run` — 9/9 focused tests passed.
- PASS — `npm.cmd test -- --run` — 6/6 test files, 64/64 frontend tests passed.
- PASS — `.\mvnw.cmd test` — 140/140 backend integration tests passed.

## Residual Risks and Optional Developer Checks

- Optional developer check: Visually inspect modal backdrop presentation and focus cycling in a physical mobile browser or responsive devtools (`<= 520px` width) to verify tactile ergonomics.

## Disposition

- `fixes-applied`
