# Code Review: Progressive Trip Builder Experience (Cycle 1)

## Metadata Header
- **Ticket ID:** `2026-09-21-p04-t04-deliver-progressive-trip-builder-experience`
- **Review Cycle:** 1
- **Reviewer:** Antigravity (Step 5 Pipeline Autonomous Reviewer)
- **Date:** 2026-09-22
- **Review Status:** Complete
- **Review Result:** `fixes-applied`

---

## Executive Summary
This independent code review evaluated the end-to-end implementation of ticket `2026-09-21-p04-t04` ("Deliver progressive trip builder experience"). The feature introduces multi-entry trip initialization (via Airfare, Stay, or Plan Trip), progressive component slots (Airfare, Stay, Rental Car) within active draft alternatives, interactive search sections with sort/filter controls, a persistent real-time itinerary cost tally against the trip budget, accessible safe removal modals, and optimistic concurrency recovery.

The review uncovered six actionable defects: two timezone calculation defects in flight and rental search formatting, defensive slot handling when selections are cleared remotely, modal focus restoration on unmount, missing error display in the removal modal, and formula duplication. All findings were resolved and verified with added automated unit tests.

The full backend suite (101 tests) and frontend suite (53 tests across 5 files) pass cleanly, and the production build compiles with zero errors.

---

## Verification Run Details

### Automated Verification Commands
1. **Backend Test Suite:**
   - **Command:** `.\mvnw.cmd test`
   - **Result:** BUILD SUCCESS (101 tests passed, 0 failures, 0 errors, 0 skipped, execution time 25.37s).
2. **Frontend Test Suite:**
   - **Command:** `npm.cmd test` in `frontend/`
   - **Result:** SUCCESS (5 test files, 53 tests passed, 0 failures, execution time 14.61s).
3. **Frontend Production Build & Typecheck:**
   - **Command:** `npm.cmd run build` in `frontend/` (`tsc -b && vite build`)
   - **Result:** SUCCESS (39 modules transformed, dist output generated with 0 TypeScript diagnostics).

---

## Scope Checklist
- [x] Three entry points on Profile Screen (`Plan Trip`, `Airfare`, `Stay`) opening `TripCreateModal` with appropriate initial intent.
- [x] `TripCreateModal` captures destination, dates, budget, travelers, and stay type preference when entering via Stay.
- [x] `TripWorkspace` presents `ItinerarySummaryTally` dynamically updating component costs, grand total, and budget difference.
- [x] `AirfareSlot` and `AirfareSearchSection` handle flight search, direct-only filtering, sorting, card presentation, selection, and replacement.
- [x] `StaySlot` and `StaySearchSection` handle accommodation search, type filtering, sorting, room count calculation, card presentation, selection, and replacement.
- [x] `RentalSlot` and `RentalSearchSection` handle rental vehicle search, category filtering, transmission filtering, 25+ driver age verification, card presentation, selection, and replacement.
- [x] `ConfirmRemoveModal` enforces two-step confirmation explicitly naming the component and price to be discarded.
- [x] Concurrency conflicts (409 VERSION_CONFLICT) preserve user search context and provide accessible reload recovery.
- [x] Component mutations update active draft alternative version while preserving unselected slots.

---

## Review Findings & Fixes

### Finding 1 [P2] - Flight times formatted in client browser timezone rather than airport local timezone
- **File:** `frontend/src/components/AirfareSearchSection.tsx`
- **Description:** `formatTime` previously called `new Date(isoString).toLocaleTimeString([], { hour: 'numeric', minute: '2-digit' })` without passing a `timeZone` option. If a user browsed from a timezone different from the airport (e.g. UTC, EST, or CET), flight departure and arrival times were formatted in the user's browser clock rather than the local airport time, causing schedule confusion.
- **Fix Applied:** Updated `formatTime` to accept an optional `timeZone?: string` parameter and passed `opt.outbound.departureTimeZone`, `opt.outbound.arrivalTimeZone`, `opt.returnFlight.departureTimeZone`, and `opt.returnFlight.arrivalTimeZone` directly to the `Intl.DateTimeFormat` options.

### Finding 2 [P2] - Rental pickup/return timestamp conversion bypassed destination airport timezone
- **File:** `frontend/src/components/RentalSearchSection.tsx`
- **Description:** `ensureIsoTimestamp` previously converted datetime-local strings (`YYYY-MM-DDTHH:mm`) by appending `:00Z` (UTC). Because the destination airport may be PST (-08:00) or CET (+01:00), appending UTC shifted early-morning SFO pickups or late-night MUC returns across day boundaries, violating backend date interval validation against the trip start/end dates.
- **Fix Applied:** Replaced `ensureIsoTimestamp` with `formatLocalToDestinationIso`, which maps destination keys (`destination-sfo`, `destination-muc`, `destination-mex`) to IANA timezone identifiers (`America/Los_Angeles`, `Europe/Berlin`, `America/Mexico_City`), computes the destination timezone's UTC offset, and formats the ISO timestamp with the destination offset. Added unit test in `ProgressiveTripBuilder.test.tsx`.

### Finding 3 [P2] - Component slots locked in phantom empty "Selected" state when selection became null
- **Files:** `frontend/src/components/AirfareSlot.tsx`, `StaySlot.tsx`, `RentalSlot.tsx`, `TripWorkspace.tsx`
- **Description:** If a trip revision or remote reload cleared a component selection while the slot mode was `'selected'`, the slot headers rendered green "Selected" badges while the slot bodies were empty/blank because `selectedAirfare` / `selectedStay` / `selectedRental` was `null`.
- **Fix Applied:** Added defensive rendering in `AirfareSlot`, `StaySlot`, and `RentalSlot` (`showSelected = mode === 'selected' && Boolean(selection)` and `showEmpty = mode === 'empty' || (mode === 'selected' && !selection)`). In `TripWorkspace.tsx`, added `useEffect` synchronization hooks that reset slot mode from `'selected'` to `'empty'` (or `'hidden'` for rental car) when active draft selections are cleared.

### Finding 4 [P3] - `ConfirmRemoveModal` focus restoration failed on unmount
- **File:** `frontend/src/components/ConfirmRemoveModal.tsx`
- **Description:** The focus restoration `useEffect` in `ConfirmRemoveModal` restored focus to `triggerElementRef.current` inside the effect body when `isOpen` became false. However, `TripWorkspace` conditionally renders the modal (`{removeTarget && <ConfirmRemoveModal ... />}`). When removed, `removeTarget` becomes `null`, unmounting the modal immediately without ever running the `isOpen === false` branch.
- **Fix Applied:** Moved focus restoration to the `useEffect` cleanup return callback (`return () => { triggerElementRef.current?.focus(); }`), ensuring focus is restored to the initiating "Remove" trigger button regardless of whether the modal is hidden or unmounted.

### Finding 5 [P3] - `ConfirmRemoveModal` lacked error display on mutation failure
- **Files:** `frontend/src/components/ConfirmRemoveModal.tsx`, `frontend/src/components/TripWorkspace.tsx`
- **Description:** If an API removal call failed with a non-conflict error (e.g., network error or server 500), `handleConfirmRemove` set `autosaveStatus('error')` but the modal remained open without displaying the error message to the user.
- **Fix Applied:** Added `errorMessage?: string` prop to `ConfirmRemoveModal`, displayed an inline `role="alert"` error message above modal action buttons, wired `removeError` state in `TripWorkspace.tsx`, and ensured conflicts close the modal so the top-level reload banner is accessible.

### Finding 6 [P3] - Duplicated pricing formula in `AirfareSlot.tsx`
- **Files:** `frontend/src/components/AirfareSlot.tsx`, `frontend/src/components/ItinerarySummaryTally.tsx`
- **Description:** `AirfareSlot.tsx` duplicated the airfare total price calculation formula from `ItinerarySummaryTally.tsx`.
- **Fix Applied:** Exported `computeAirfareTotalCents` from `ItinerarySummaryTally.tsx` and reused it in `AirfareSlot.tsx`, eliminating duplicate math logic.

---

## Acceptance Criteria Traceability

| Acceptance Criteria | State | Verification Method |
|---|---|---|
| 1. Profile screen entry points: Airfare, Stay, and Plan Trip open modal with appropriate preselected intent | Verified | Automated tests in `ProgressiveTripBuilder.test.tsx` verify all 3 paths launch correctly with preselected modes. |
| 2. Trip workspace presents progressive builder with Airfare & Stay ready, Rental Car initially hidden | Verified | Automated tests verify initial layout and "Add a car" button reveals rental slot. |
| 3. Airfare search supports filters, sorting, seat availability, card details, selection, and draft replacement | Verified | Automated tests verify flight search, direct filter, pricing, selection, and draft mutation. |
| 4. Stay search supports accommodation type filtering, sorting, rating/distance, transparent nightly breakdown, selection, and replacement | Verified | Automated tests verify room count math, rating, distance, per-night totals, and draft update. |
| 5. Rental car search validates dates, requires 25+ driver age checkbox + explanation if under 25, selection, and replacement | Verified | Automated tests verify pickup/return date validation, driver age rule with explanation, and selection. |
| 6. Persistent itinerary tally maintains real-time itemized component breakdown, grand total, and budget comparison | Verified | Automated tests verify accurate summation across components, within-budget badge, and overage badge. |
| 7. Safe component removal requires two-step confirmation naming component and price to be discarded | Verified | Automated tests verify confirmation modal renders component name and formatted price before removal. |
| 8. Concurrency conflict (409 VERSION_CONFLICT) displays alert with reload action while preserving user search inputs | Verified | Automated tests verify reload action recovers latest draft version without resetting active search options. |
| 9. Accessible dialog semantics, focus trapping, Escape key dismissal, and focus restoration | Verified | Automated tests verify dialog role, aria-modal, focus trapping, Escape dismissal, and trigger focus return. |

---

## Quality & Non-Functional Assessment

### Security
- All requests leverage existing authenticated API sessions without hardcoded credentials or unvalidated HTML injection (`dangerouslySetInnerHTML` is not used).
- Inputs are validated on the client and validated on the backend.

### Concurrency
- All component mutations pass `expectedVersion` and `expectedDraftVersion`.
- On 409 `VERSION_CONFLICT`, user search inputs and candidate selections are preserved while offering a clear "Reload from server" action.

### Performance
- Autosaves of traveler ages and budget are debounced (600ms) with equality checks to avoid redundant network requests.
- Component searches execute on demand and do not re-fetch unless filters/sorts change.

### Accessibility (a11y)
- Modals implement `role="dialog"`, `aria-modal="true"`, `aria-labelledby`, focus trapping, Escape key dismissal, and trigger focus restoration.
- Tally uses `aria-labelledby="tally-heading"` and semantic heading hierarchy.
- Autosave status uses `aria-live="polite"`, conflict alert uses `role="alert"`.

### Architecture & Maintainability
- Component responsibilities are cleanly separated: `AirfareSlot`, `AirfareSearchSection`, `StaySlot`, `StaySearchSection`, `RentalSlot`, `RentalSearchSection`, `ItinerarySummaryTally`, and `ConfirmRemoveModal`.
- Shared price calculations are centralized in `ItinerarySummaryTally.tsx` and reused across slots.

---

## Review Verdict
`REVIEW_RESULT: fixes-applied`
All identified defects have been fixed in the codebase and verified with comprehensive automated tests.
