# Fee-Free Cancellation and Post-Cancellation Triage Experience Implementation Plan

## Overview
- Ticket: `ai/thoughts/tickets/2026-09-22-p06-t04-fee-free-cancellation-and-post-cancellation-experience.md`
- Research: `ai/thoughts/research/2026-09-22-p06-t04-fee-free-cancellation-and-post-cancellation-experience.md`
- Outcome: Deliver user-facing fee-free cancellation modals, post-cancellation triage dialogs ("Use a saved alternative", "Create a new Draft", "Done for now"), trip deletion vs. cancellation gating, read-only canceled trip workspace presentation with immutable booking history, profile list integration with `CANCELED` badges, and trip duplication from canceled states.

## Current State

In P06-T03, the backend cancellation engine and database constraints were fully completed:
- `POST /api/trips/{tripId}/bookings/{bookingId}/cancel` cancels active bookings and atomically restores flight seats, accommodation inventory, and rental car occupancy (`BookingTransactionExecutor.java:206-253`).
- `POST /api/trips/{tripId}/cancel` atomically cancels any active reservation, marks `detour_trip.status = 'CANCELED'`, and leaves all historical bookings preserved (`BookingTransactionExecutor.java:255-301`).
- All mutations on canceled trips (`createDraft`, `promoteDraft`, `selectDraftAirfare`, etc.) are rejected with `HTTP 409 TRIP_CANCELED`, while `POST /api/trips/{tripId}/duplicate` is explicitly permitted (`BookingCancellationIntegrationTest.java:285-413`).
- `TripRepository.hasBookingHistory(tripId)` prevents permanently deleting any trip with booking records (`HTTP 409 CANNOT_DELETE_BOOKED_TRIP`).
- `frontend/src/api/tripsApi.ts:723-728` defines `cancelBooking`, `cancelTrip`, `getBookingHistory`, and `getActiveBooking`.

However, the frontend UI currently lacks the user-facing cancellation and triage experience:
- In `TripWorkspace.tsx:1100-1133`, the Active Booking banner only renders "View Booking Details" with no option to cancel.
- In `TripWorkspace.tsx:1037-1046` and `TripListSection.tsx:90-99`, trips with booking history render a disabled button labeled `"Has booking history"` instead of offering `"Cancel Trip"`.
- Dedicated confirmation dialogs (`CancelBookingModal.tsx`, `CancelTripModal.tsx`, and `PostCancellationTriageModal.tsx`) do not exist.
- Canceled trips (`trip.status === 'CANCELED'`) do not render a prominent header badge or banner in `TripWorkspace.tsx`, do not lock/hide editing controls in `AlternativeCard.tsx` and builder slots, and lack a collapsible Booking History section.
- `ProfileScreen.tsx` and `TripListSection.tsx` do not display a `CANCELED` badge on canceled trips and do not suppress cancel actions for Expired or Past trips.

## Desired End State

1. **Clear distinction across five deletion and cancellation actions:**
   - Travelers are never presented with ambiguous "Cancel" buttons.
   - 1) "Delete draft" (draft alternative removal), 2) "Delete planned itinerary" (planned alternative removal), 3) "Cancel Booking" (active reservation cancellation), 4) "Delete trip" (unbooked trip permanent removal), and 5) "Cancel Trip" (booked trip closure).
2. **Cancel Booking Modal (`CancelBookingModal.tsx`):**
   - Opened from "Cancel Booking" on the Active Booking banner in `TripWorkspace.tsx`.
   - Explains that the reservation will be canceled without fees, inventory will be released, booking history will be retained, and the trip remains active.
   - Executes `tripsApi.cancelBooking(trip.id, booking.id, { expectedVersion: trip.version })`.
   - On success, updates trip state, clears active booking, and immediately launches `PostCancellationTriageModal.tsx`.
3. **Post-Cancellation Triage Modal (`PostCancellationTriageModal.tsx`):**
   - Displays a success confirmation that the booking was canceled fee-free.
   - Provides three mutually exclusive paths:
     1. *"Use a saved alternative"*: lists remaining Planned alternatives; selecting one duplicates it into a new Draft via `tripsApi.duplicateAlternative` for revalidation.
     2. *"Create a new Draft"*: calls `tripsApi.createDraft` to start a fresh component-empty draft.
     3. *"Done for now"*: dismisses the modal and remains in the Trip Workspace.
4. **Trip Deletion vs. Trip Cancellation Gating:**
   - When `hasBookingHistory` is `false`: UI displays "Delete trip" (triggering `ConfirmDeleteModal`).
   - When `hasBookingHistory` is `true`: UI replaces "Delete trip" with "Cancel Trip" (triggering `CancelTripModal`).
   - For Expired and Past trips: cancel actions are hidden or disabled with an explanatory tooltip/notice.
5. **Cancel Trip Modal (`CancelTripModal.tsx`):**
   - Opened from "Cancel Trip" in `TripWorkspace.tsx` and `TripListSection.tsx`.
   - Explains that active reservations will be released fee-free, booking records permanently retained, alternatives locked as read-only, and the trip can be duplicated at any time.
   - Executes `tripsApi.cancelTrip(trip.id, { expectedVersion: trip.version })`.
   - On success, updates the workspace to Canceled Trip state.
6. **Canceled Trip Presentation (`TripWorkspace.tsx`, `AlternativeCard.tsx`):**
   - Prominent `CANCELED` header badge and descriptive banner.
   - Builder slots, draft creation, and alternative editing/promotion controls are hidden or disabled in read-only mode.
   - Primary action: "Duplicate into a new Trip" opening `TripRevisionModal`.
   - Collapsible `BookingHistorySection` displaying all historical bookings (reference, timestamps, total price, component breakdown).
7. **Profile and Trip List Presentation (`TripListSection.tsx`, `ProfileScreen.tsx`):**
   - `CANCELED` badge rendered on canceled trips in trip cards.
   - Direct "Cancel trip" action available from `TripCard` opening `CancelTripModal`.
   - Past and expired trips suppress cancel actions.

## Scope

### In scope
- Client API tests for `cancelBooking` and `cancelTrip` in `frontend/src/api/tripsApi.test.ts`.
- Creation of `CancelBookingModal.tsx`, `PostCancellationTriageModal.tsx`, `CancelTripModal.tsx`, and `BookingHistorySection.tsx`.
- Integration of "Cancel Booking" action and modal in `TripWorkspace.tsx`.
- Immediate post-cancellation transition to `PostCancellationTriageModal.tsx` with three triage paths.
- Replacement of "Delete trip" with "Cancel trip" in `TripWorkspace.tsx` and `TripListSection.tsx` when `hasBookingHistory` is true.
- Implementation of Canceled Trip read-only view in `TripWorkspace.tsx` and `AlternativeCard.tsx`.
- Primary "Duplicate into a new Trip" action on canceled trips using `TripRevisionModal.tsx`.
- Collapsible Booking History presentation in `TripWorkspace.tsx`.
- Profile screen and trip list updates in `TripListSection.tsx` and `ProfileScreen.tsx`.
- Accessibility enhancements: focus trapping, Escape dismissal, focus restoration, `aria-live` announcements, and distinct `.danger-button` styling.
- Comprehensive Vitest test suite in `frontend/src/FeeFreeCancellationAndTriage.test.tsx`.

### Out of scope
- Backend modifications (all backend endpoints, constraints, and tests were delivered in P06-T03).
- Rebooking onto different dates without cancellation.
- Airline schedule changes or supplier disruption simulations.
- Version 2 Event bus integrations.

## Active Project Guardrails

None recorded in `ai/thoughts/design-lens.md`.

## Impact and Risk Analysis

| Risk | Impact | Planned Mitigation |
| --- | --- | --- |
| **Optimistic Concurrency Conflict** | Cancellation requests require `expectedVersion`. If the trip was updated concurrently, server returns `HTTP 409 VERSION_CONFLICT`. | Pass `trip.version` in payload. On conflict error, catch and display conflict message offering to reload latest trip state. |
| **Trip Expiration Boundary** | Trips departing today or earlier cannot be canceled (server returns `HTTP 400 TRIP_EXPIRED`). | Detect `isExpired` and `temporalStatus === 'PAST'` on the frontend and disable/hide "Cancel Booking" and "Cancel Trip" buttons with explanatory messages. |
| **Attempting Draft/Alternative Mutations on Canceled Trip** | Server strictly rejects all component selections, draft additions, and promotions on canceled trips with `HTTP 409 TRIP_CANCELED`. | When `trip.status === 'CANCELED'`, mark workspace and alternative cards as read-only: suppress all edit, add, promote, and booking buttons, allowing only "Duplicate into a new Trip". |
| **Accessibility and Focus Trapping** | Multiple modals chained sequentially (Cancel Booking -> Post-Cancellation Triage) could lose keyboard focus. | Ensure `CancelBookingModal` and `PostCancellationTriageModal` manage focus cleanly: when triage opens, focus is directed to the first triage option; on close, focus is restored to the primary workspace heading. |
| **Duplicating Canceled Trip with No Planned Alternatives** | `POST /api/trips/{id}/duplicate` requires at least one source planned itinerary ID. If a canceled trip had no planned alternatives, duplication cannot select any. | In `TripRevisionModal.tsx`, display a clear notice when no planned alternatives exist and disable submission, preventing runtime 400 errors. |

## Implementation Approach

### 1. Architectural Placement and State Ownership
- `TripWorkspace.tsx` remains the authoritative owner of the active trip state, active draft, and active booking within the workspace view.
- When an active booking is canceled via `tripsApi.cancelBooking`, the returned `TripResponse` updates `trip` state, `activeBooking` is reset to null, and `isTriageModalOpen` is immediately set to true.
- When a triage action is selected:
  - "Use a saved alternative" calls `tripsApi.duplicateAlternative(trip.id, plannedId, { expectedVersion: trip.version })`, updating `trip` with the new draft.
  - "Create a new Draft" calls `tripsApi.createDraft(trip.id, { expectedVersion: trip.version })`, updating `trip` with the empty draft.
  - "Done for now" closes the modal.
- When a trip is canceled via `tripsApi.cancelTrip`, the returned `TripResponse` has `status = 'CANCELED'`, transitioning `TripWorkspace` into its read-only mode and updating `onTripUpdated`.

### 2. Modal Accessibility Architecture
- Follow the established accessible dialog pattern used in `ConfirmDeleteModal.tsx` and `BudgetOverageModal.tsx`:
  - `role="dialog"`, `aria-modal="true"`, `aria-labelledby="<heading-id>"`.
  - Save `document.activeElement` on open; restore on close.
  - Keyboard trap: capture `Tab` and `Shift+Tab` to keep focus within dialog.
  - Listen for `Escape` to close (or dismiss).
  - Use `aria-live="polite"` region in the workspace to announce cancellation results.

### 3. Clear Terminology Separation
- Ensure the 5 actions use exact button text:
  1. `Delete draft` (Draft alternative)
  2. `Delete planned itinerary` (Planned snapshot)
  3. `Cancel Booking` (Active booking banner)
  4. `Delete trip` (Trip with no booking history)
  5. `Cancel Trip` (Trip with booking history)
- Destructive confirmation buttons use `.primary.danger-button` with red background (`#b91c1c`) to visually signal permanent or cancellation actions.

---

## Phase 1: API Client Tests & Core Cancellation Dialogs

### Changes
- [x] `frontend/src/api/tripsApi.test.ts` — add unit tests for `cancelBooking` and `cancelTrip`, verifying endpoint URLs, HTTP methods, `expectedVersion` serialization, and CSRF token transmission.
- [x] `frontend/src/components/CancelBookingModal.tsx` [NEW] — create accessible confirmation modal:
  - Explains: fee-free cancellation, inventory release back to catalog, permanent retention of booking reference and cancellation record, and that trip remains active for planning.
  - Submits `onConfirm` calling `cancelBooking`.
  - Accessible mechanics: focus trap, Escape dismissal, focus restoration, danger button styling.
- [x] `frontend/src/components/PostCancellationTriageModal.tsx` [NEW] — create accessible triage modal:
  - Header with success banner announcing fee-free cancellation.
  - Option 1: "Use a saved alternative" — lists `trip.planned` alternatives with component summaries; clicking calls `onUseAlternative(plannedId)`.
  - Option 2: "Create a new Draft" — button calling `onCreateDraft()`.
  - Option 3: "Done for now" — button calling `onClose()`.
  - Accessible mechanics: initial focus on first option, keyboard trap, Escape dismissal.
- [x] `frontend/src/components/CancelTripModal.tsx` [NEW] — create accessible trip cancellation modal:
  - Explains: active reservation released without fees, all booking records permanently retained, draft and planned alternatives locked as read-only, and ability to duplicate into a new trip at any time.
  - Submits `onConfirm` calling `cancelTrip`.
  - Accessible mechanics: focus trap, Escape dismissal, focus restoration, danger button styling.
- [x] `frontend/src/style.css` — add styling rules:
  - `.danger-button` (`background: #b91c1c; color: white;`)
  - `.badge-canceled` (`background: #f1f5f9; color: #475569; border: 1px solid #cbd5e1;`)
  - `.canceled-trip-banner` (prominent warning card layout)
  - `.triage-modal` and `.triage-option-card` styling
  - `.booking-history-section`, `.history-item`, and `.history-meta` styling

### Automated verification
- [x] `cmd /c npx vitest run src/api/tripsApi.test.ts` — verify all API client tests pass including new cancellation endpoints.

### Optional developer checks
- None.

---

## Phase 2: Active Booking Cancellation & Post-Cancellation Triage Integration

### Changes
- [x] `frontend/src/components/TripWorkspace.tsx`:
  - Add state variables: `isCancelBookingModalOpen`, `cancelBookingPending`, `cancelBookingError`, `isTriageModalOpen`, `triagePending`, `triageAction`, `triageError`.
  - In Active Booking banner (`lines 1100-1133`), add "Cancel Booking" action button:
    - Disabled or hidden if `isExpired || temporalStatus === 'PAST'`.
    - Clicking sets `isCancelBookingModalOpen(true)`.
  - Implement `handleConfirmCancelBooking`:
    - Calls `tripsApi.cancelBooking(trip.id, activeBooking.id, { expectedVersion: trip.version })`.
    - Updates `trip` state with the returned `TripResponse` (`applyTripState(updatedTrip)`).
    - Clears `activeBooking` (`setActiveBooking(null)`).
    - Closes `CancelBookingModal`.
    - Opens `PostCancellationTriageModal` (`setIsTriageModalOpen(true)`).
    - Announces cancellation via `autosaveMessage` or `aria-live` region.
  - Implement `handleTriageUseAlternative(plannedId: string)`:
    - Calls `tripsApi.duplicateAlternative(trip.id, plannedId, { expectedVersion: trip.version })`.
    - Updates `trip` state, closes triage modal, and announces status.
  - Implement `handleTriageCreateDraft()`:
    - Calls `tripsApi.createDraft(trip.id, { expectedVersion: trip.version })`.
    - Updates `trip` state, closes triage modal, and announces status.
  - Render `CancelBookingModal` and `PostCancellationTriageModal`.

### Automated verification
- [x] `cmd /c npx vitest run` — verify existing workspace and builder tests continue passing.

### Optional developer checks
- None.

---

## Phase 3: Trip Deletion vs. Trip Cancellation Gating & Canceled Trip State

### Changes
- [x] `frontend/src/components/TripWorkspace.tsx`:
  - In workspace nav bar (`lines 1036-1046`):
    - When `!hasBookingHistory`: render "Delete trip" button (existing `promptDeleteTrip`).
    - When `hasBookingHistory`:
      - If `trip.status === 'CANCELED'`: render disabled button or badge labeled `"Trip canceled"`.
      - If `isExpired || temporalStatus === 'PAST'`: render disabled button labeled `"Cancel trip"` with explanatory title.
      - Otherwise: render `"Cancel trip"` button (`className="text-button delete-button"`) opening `CancelTripModal`.
  - Implement `handleConfirmCancelTrip`:
    - Calls `tripsApi.cancelTrip(trip.id, { expectedVersion: trip.version })`.
    - Updates `trip` state (`applyTripState(updatedTrip)` where `status = 'CANCELED'`).
    - Sets `activeBooking(null)`.
    - Closes `CancelTripModal`.
    - Calls `onTripUpdated(updatedTrip)`.
  - When `trip.status === 'CANCELED'`:
    - Header: display `<span className="badge badge-canceled">Canceled</span>`.
    - Render prominent `CanceledTripBanner`:
      - Notice explaining reservations released, alternatives read-only, and historical records preserved.
      - Primary button: `"Duplicate into a new Trip"` triggering `setIsRevisionModalOpen(true)`.
    - Disable/hide all mutation controls:
      - Progressive Builder: disable "Save as Planned Itinerary", disable slot actions, hide "Add a car".
      - Shared Details form: disable all inputs and Save button.
      - Alternatives section: hide/disable "Create empty draft".
  - Render `CancelTripModal`.
- [x] `frontend/src/components/AlternativeCard.tsx`:
  - Add prop `tripCanceled?: boolean`.
  - When `tripCanceled` is true:
    - Hide or disable "Delete draft", "Duplicate to new draft", "Promote to Planned".
    - Hide or disable "Delete planned itinerary", "Duplicate to draft", "Select for Booking Review".
    - If `isBooked`, preserve "View Booking Details".
- [x] `frontend/src/components/TripRevisionModal.tsx`:
  - Support mode/title prop: when called from a canceled trip, show title `"Duplicate into a new Trip"` and button `"Duplicate trip"`.
  - If `trip.planned.length === 0`: render notice indicating no planned alternatives are available to copy, preventing invalid empty submissions.

### Automated verification
- [x] `cmd /c npx vitest run` — verify all modal and builder tests pass.

### Optional developer checks
- None.

---

## Phase 4: Collapsible Booking History Presentation

### Changes
- [x] `frontend/src/components/BookingHistorySection.tsx` [NEW]:
  - Fetches `tripsApi.getBookingHistory(tripId)` on mount and when `hasBookingHistory` changes.
  - Renders accessible collapsible section (`<details className="booking-history-accordion">` or disclosure button):
    - Heading: `Booking History ({bookings.length})`.
    - Lists all bookings (both active and canceled) with:
      - Booking Reference code (`bookingReference`).
      - Status badge (`ACTIVE` or `CANCELED`).
      - Booked date timestamp.
      - Canceled date timestamp (if status is `CANCELED`).
      - Grand total formatted price.
      - Component breakdown (Airfare carrier/flights, Stay property/unit, Rental car/location).
- [x] `frontend/src/components/TripWorkspace.tsx`:
  - Render `BookingHistorySection` whenever `hasBookingHistory` is true or `trip.status === 'CANCELED'`.
  - Trigger history reload when a booking or trip cancellation completes.

### Automated verification
- [x] `cmd /c npx vitest run` — verify workspace renders without regression.

### Optional developer checks
- None.

---

## Phase 5: Profile Screen and Trip List Integration

### Changes
- [x] `frontend/src/components/TripListSection.tsx`:
  - Add `onCancelTrip?: (trip: TripProfileSummary) => void` to props.
  - In `TripCard`:
    - Display `<span className="badge badge-canceled">Canceled</span>` if `trip.status === 'CANCELED'`.
    - In actions:
      - If `trip.status === 'CANCELED'`: render disabled button `"Trip canceled"`.
      - If `trip.hasBookingHistory && trip.status !== 'CANCELED'`:
        - If `isPast || trip.expiredAlternativeCount > 0`: render disabled `"Cancel trip"` button with explanatory title.
        - Otherwise: render `"Cancel trip"` button calling `onCancelTrip(trip)`.
      - If `!trip.hasBookingHistory`: render `"Delete trip"` calling `onDelete(trip)`.
- [x] `frontend/src/components/ProfileScreen.tsx`:
  - Add state `cancelTripTarget: TripProfileSummary | null`, `cancelTripPending: boolean`, `cancelTripError?: string`.
  - Pass `onCancelTrip={(trip) => setCancelTripTarget(trip)}` to `TripListSection`.
  - Render `CancelTripModal` in `ProfileScreen`:
    - On confirm: calls `tripsApi.cancelTrip(cancelTripTarget.id, { expectedVersion: cancelTripTarget.version })`.
    - On success: refreshes profile (`onRefreshProfile()`) and clears target.

### Automated verification
- [x] `cmd /c npx vitest run src/App.test.tsx` — verify profile screen tests pass.

### Optional developer checks
- None.

---

## Phase 6: Comprehensive Vitest Verification Suite

### Changes
- [x] `frontend/src/FeeFreeCancellationAndTriage.test.tsx` [NEW]:
  - Test 1: Cancel Booking flow — clicking "Cancel Booking" on Active Booking banner opens `CancelBookingModal` with fee-free explanation, inventory release notice, and booking history preservation.
  - Test 2: Confirming Cancel Booking sends `POST /api/trips/{id}/bookings/{bookingId}/cancel`, clears active booking, and opens `PostCancellationTriageModal`.
  - Test 3: Post-Cancellation Triage — selecting "Use a saved alternative" calls `duplicateAlternative` with selected Planned ID and adds a fresh Draft to the workspace.
  - Test 4: Post-Cancellation Triage — selecting "Create a new Draft" calls `createDraft` and provides an empty draft.
  - Test 5: Post-Cancellation Triage — selecting "Done for now" closes triage dialog and remains in workspace.
  - Test 6: Deletion vs. Cancellation Gating — trips without booking history present "Delete trip"; trips with booking history present "Cancel trip".
  - Test 7: Confirming Cancel Trip sends `POST /api/trips/{id}/cancel` and transitions Trip Workspace to Canceled state.
  - Test 8: Canceled Trip state — displays CANCELED badge, warning banner, locks/disables builder slots and draft mutations, and provides "Duplicate into a new Trip" button.
  - Test 9: Booking History section displays historical bookings with reference codes, timestamps, grand total, and component breakdown.
  - Test 10: Expired and Past trips suppress/disable "Cancel Booking" and "Cancel Trip" actions.
  - Test 11: Accessible modal mechanics — verifies focus trap, Escape dismissal, and focus restoration on all cancellation dialogs.
  - Test 12: Profile screen integration — displays `CANCELED` badge and allows canceling booked trips directly from profile.

### Automated verification
- [x] `cmd /c npx vitest run src/FeeFreeCancellationAndTriage.test.tsx` — all 12 tests pass.
- [x] `cmd /c npm test` — all test suites pass cleanly.

### Optional developer checks
- None.

---

## Test Strategy

- **API Client Unit Tests (`tripsApi.test.ts`):** Verify exact request method, path parameters, payload formatting (`expectedVersion`), and CSRF token transmission for `cancelBooking` and `cancelTrip`.
- **Component & Workflow Integration Tests (`FeeFreeCancellationAndTriage.test.tsx`):**
  - Render `TripWorkspace` with mock trips and bookings in various lifecycle states (Active booking, Canceled trip, Expired trip, Past trip).
  - Use `@testing-library/react` and `@testing-library/user-event` to simulate user interactions on cancellation buttons, modal confirmations, triage options, and accordion toggles.
  - Verify DOM updates, disabled attributes, accessibility roles (`role="dialog"`, `aria-modal="true"`, `aria-live`), and API invocation arguments.
- **Regression Suite:** Run the full Vitest suite (`cmd /c npm test`) to confirm zero regressions in existing builder, promotion, comparison, and authentication flows.

---

## Acceptance-Criteria Traceability

| Acceptance criterion | Planned code evidence | Planned test evidence |
| --- | --- | --- |
| "Cancel Booking" opens a confirmation modal detailing fee-free cancellation, inventory release, and retention of booking history. | `frontend/src/components/CancelBookingModal.tsx`, `frontend/src/components/TripWorkspace.tsx:handlePromptCancelBooking` | `FeeFreeCancellationAndTriage.test.tsx: test 1` |
| Confirming Cancel Booking releases inventory and opens the triage dialog offering "Use a saved alternative", "Create a new Draft", and "Done for now". | `frontend/src/components/TripWorkspace.tsx:handleConfirmCancelBooking`, `frontend/src/components/PostCancellationTriageModal.tsx` | `FeeFreeCancellationAndTriage.test.tsx: test 2` |
| Selecting "Use a saved alternative" duplicates the selected Planned alternative into a fresh Draft for revalidation. | `frontend/src/components/PostCancellationTriageModal.tsx`, `frontend/src/components/TripWorkspace.tsx:handleTriageUseAlternative` | `FeeFreeCancellationAndTriage.test.tsx: test 3` |
| Trips with booking history present "Cancel Trip" rather than "Delete Trip"; "Delete Trip" is only offered when no booking has ever existed. | `frontend/src/components/TripWorkspace.tsx:workspace-nav`, `frontend/src/components/TripListSection.tsx:TripCard` | `FeeFreeCancellationAndTriage.test.tsx: test 6` |
| Confirming Cancel Trip closes the trip, releases any active booking, and transitions the Trip Workspace to a read-only state. | `frontend/src/components/CancelTripModal.tsx`, `frontend/src/components/TripWorkspace.tsx:handleConfirmCancelTrip` | `FeeFreeCancellationAndTriage.test.tsx: test 7` |
| On a Canceled Trip, all alternatives are read-only, booking history is viewable, and a "Duplicate into a new Trip" action is provided. | `frontend/src/components/TripWorkspace.tsx:canceled-trip-banner`, `frontend/src/components/AlternativeCard.tsx:tripCanceled`, `frontend/src/components/BookingHistorySection.tsx` | `FeeFreeCancellationAndTriage.test.tsx: test 8, test 9` |
| Expired and Past trips suppress cancel actions and display immutable status. | `frontend/src/components/TripWorkspace.tsx`, `frontend/src/components/TripListSection.tsx` | `FeeFreeCancellationAndTriage.test.tsx: test 10` |
| Comprehensive Vitest test suite covers modal interactions, focus management, triage selections, and read-only canceled trip states. | `frontend/src/FeeFreeCancellationAndTriage.test.tsx` | `FeeFreeCancellationAndTriage.test.tsx: tests 1-12` |

---

## Risks and Rollback/Recovery

- **Risk:** Modal focus management errors or backdrop clicks causing dialog unmount without restoring focus.
  - **Mitigation:** Use `previousActiveElement` ref pattern with fallback to workspace headings, verified with keyboard trap tests.
- **Risk:** Stale version conflicts during cancellation.
  - **Mitigation:** Always supply `trip.version` in the cancel payload; if `VERSION_CONFLICT` occurs, present an alert to reload latest trip state.
- **Rollback:** Code changes are isolated to frontend presentation and client API calls; if needed, changes can be rolled back via git without any database migration reversals.

## References
- Ticket: `ai/thoughts/tickets/2026-09-22-p06-t04-fee-free-cancellation-and-post-cancellation-experience.md`
- Research: `ai/thoughts/research/2026-09-22-p06-t04-fee-free-cancellation-and-post-cancellation-experience.md`
- Backend Tests: `src/test/java/app/detour/booking/BookingCancellationIntegrationTest.java`
- Backend Service: `src/main/java/app/detour/booking/BookingService.java`, `BookingTransactionExecutor.java`
- API Client: `frontend/src/api/tripsApi.ts`
- Primary Components: `frontend/src/components/TripWorkspace.tsx`, `frontend/src/components/TripListSection.tsx`, `frontend/src/components/ProfileScreen.tsx`, `frontend/src/components/AlternativeCard.tsx`
