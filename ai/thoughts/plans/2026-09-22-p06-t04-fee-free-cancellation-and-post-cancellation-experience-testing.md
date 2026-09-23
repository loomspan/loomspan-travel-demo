# Fee-Free Cancellation and Post-Cancellation Triage Experience Testing Plan

## Change Summary

Deliver focused regression and verification tests for P06-T04 fee-free cancellation, post-cancellation triage workflows, trip deletion vs. cancellation gating, read-only canceled trip state transitions, collapsible booking history presentation, and profile list integration.

## Impacted Areas and Risks

| Category | Risk | Planned evidence |
| --- | --- | --- |
| **API Client Serialization** | `cancelBooking` and `cancelTrip` might fail to send CSRF tokens or serialize `expectedVersion` incorrectly in request payloads. | Unit tests in `frontend/src/api/tripsApi.test.ts` asserting exact HTTP method, path, headers, and request body. |
| **Active Booking Cancellation Flow** | "Cancel Booking" action on Active Booking banner might not open confirmation modal, or confirmation might fail to clear active booking and launch triage. | Integration tests in `FeeFreeCancellationAndTriage.test.tsx` verifying modal opening, fee-free disclosure, API dispatch, and immediate triage modal launch. |
| **Post-Cancellation Triage Execution** | Triage options ("Use a saved alternative", "Create a new Draft", "Done for now") might not trigger the correct API mutations (`duplicateAlternative`, `createDraft`) or might fail to update the workspace. | Integration tests in `FeeFreeCancellationAndTriage.test.tsx` covering all three mutually exclusive triage paths and their draft updates. |
| **Deletion vs. Cancellation Gating** | Trips with booking history might erroneously render "Delete trip" (triggering backend `409 CANNOT_DELETE_BOOKED_TRIP`), or unbooked trips might render "Cancel trip". | Integration tests asserting "Delete trip" is present only when `hasBookingHistory` is false, and "Cancel trip" is present when `hasBookingHistory` is true. |
| **Canceled Trip Read-Only Enforcement** | A canceled trip (`trip.status === 'CANCELED'`) might leave builder slots, component search/select, draft mutations, or promotions enabled, causing 409 errors. | Integration tests verifying builder controls, alternative card actions, and details form inputs are disabled or hidden in read-only state. |
| **Trip Duplication from Canceled State** | "Duplicate into a new Trip" on canceled trips might fail to open `TripRevisionModal` or fail to copy planned alternatives. | Integration test verifying primary "Duplicate into a new Trip" button opens revision modal and creates a fresh active trip. |
| **Booking History Display** | Historical bookings might not render cancellation timestamps, component reference codes, or total costs in the collapsible section. | Integration test verifying `BookingHistorySection` fetches and renders all active and canceled bookings with breakdown. |
| **Temporal Status / Expiration Boundaries** | Past or expired trips might allow cancellation attempts, resulting in backend `400 TRIP_EXPIRED`. | Integration tests verifying "Cancel Booking" and "Cancel Trip" are disabled or hidden on past and expired trips. |
| **Modal Accessibility & Focus Management** | Chained modal dialogs (Cancel Booking -> Post-Cancellation Triage) might trap keyboard focus or fail to restore focus on dismiss. | Integration tests verifying focus trap (Tab/Shift+Tab), Escape key dismissal, initial focus, and focus restoration. |

## Existing Coverage and Environment Constraints

- **Backend Integration Tests:** `BookingCancellationIntegrationTest.java` (15/15 tests passing) covers atomic inventory release, concurrent cancellation protection, and immutable booking record retention.
- **Frontend Framework:** React 19, Vitest 4.1, `@testing-library/react`, and `@testing-library/user-event`.
- **Environment:** Node.js on Windows. Commands are run via `cmd /c npm test` or `cmd /c npx vitest run`.
- **Test Isolation:** All frontend network requests are mocked via `fetchMock` or API client spies; tests require no running backend server.

## Failing Test First

### 1. API Client Client Methods
- **Name:** `cancelBooking and cancelTrip API client methods send expectedVersion and CSRF headers`
- **Type:** Unit test (Vitest)
- **Location:** `frontend/src/api/tripsApi.test.ts`
- **Arrange/Act/Assert:**
  - Mock `fetchMock.mockResolvedValueOnce(json(200, { id: 'trip-1', version: 2, status: 'ACTIVE' }))`.
  - Call `await tripsApi.cancelBooking('trip-1', 'booking-1', { expectedVersion: 1 })`.
  - Assert `fetchMock` was called with `/api/trips/trip-1/bookings/booking-1/cancel`, method `'POST'`, headers including `'X-XSRF-TOKEN': 'secret-token'`, and body `JSON.stringify({ expectedVersion: 1 })`.
  - Call `await tripsApi.cancelTrip('trip-1', { expectedVersion: 2 })`.
  - Assert `fetchMock` was called with `/api/trips/trip-1/cancel`, method `'POST'`, and body `JSON.stringify({ expectedVersion: 2 })`.
- **Expected pre-fix failure:** Pre-fix, `tripsApi.test.ts` lacks test coverage for `cancelBooking` and `cancelTrip`.

### 2. Component First Acceptance Red Test
- **Name:** `Cancel Booking button opens CancelBookingModal with fee-free explanation and inventory notice`
- **Type:** Component Integration test (Vitest + RTL)
- **Location:** `frontend/src/FeeFreeCancellationAndTriage.test.tsx`
- **Arrange/Act/Assert:**
  - Render `TripWorkspace` with an active trip and active booking.
  - Query for `screen.getByRole('button', { name: /cancel booking/i })`.
- **Expected pre-fix failure:** `Unable to find an accessible element with the role "button" and name /cancel booking/i` (because `TripWorkspace.tsx` only renders "View Booking Details").

## Tests to Add or Update

### 1. `cancelBooking and cancelTrip in tripsApi.test.ts`
- **Type:** Unit test
- **Location:** `frontend/src/api/tripsApi.test.ts`
- **Proves:** API client correctly formats endpoint URLs, passes HTTP POST, includes CSRF token header, and serializes `expectedVersion`.
- **Inputs/fixture:** Trip ID `'trip-1'`, booking ID `'booking-1'`, payload `{ expectedVersion: 1 }`.
- **Doubles or boundary isolation:** Global `fetchMock` stub.
- **Edge cases:** Verifies both booking cancellation and full trip cancellation endpoints.

### 2. `Cancel Booking button opens CancelBookingModal with fee-free explanation`
- **Type:** Component integration test
- **Location:** `frontend/src/FeeFreeCancellationAndTriage.test.tsx`
- **Proves:** Clicking "Cancel Booking" on the active booking card opens `CancelBookingModal` explaining fee-free cancellation, inventory restoration to catalog, retention of booking history, and active trip preservation.
- **Inputs/fixture:** Mock trip with active booking (`bookingReference: 'DT-K8M2P4'`).
- **Doubles or boundary isolation:** `tripsApi` methods stubbed.
- **Edge cases:** Modal contains accessible `role="dialog"`, `aria-modal="true"`, and danger button styling.

### 3. `Confirming Cancel Booking releases inventory and launches PostCancellationTriageModal`
- **Type:** Workflow integration test
- **Location:** `frontend/src/FeeFreeCancellationAndTriage.test.tsx`
- **Proves:** Clicking confirm in `CancelBookingModal` calls `tripsApi.cancelBooking`, clears active booking from workspace, closes the confirmation dialog, and immediately launches `PostCancellationTriageModal`.
- **Inputs/fixture:** Mock trip (v2) and active booking.
- **Doubles or boundary isolation:** `tripsApi.cancelBooking` resolved with updated `TripResponse` (v3).
- **Edge cases:** Focus shifts smoothly to triage modal without keyboard focus loss.

### 4. `Triage option 1: Use a saved alternative duplicates planned alternative into a fresh draft`
- **Type:** Workflow integration test
- **Location:** `frontend/src/FeeFreeCancellationAndTriage.test.tsx`
- **Proves:** Selecting "Use a saved alternative" from triage dialog calls `tripsApi.duplicateAlternative` with the chosen Planned ID, adds a fresh Draft alternative to the workspace, and dismisses triage modal.
- **Inputs/fixture:** Trip with 1 remaining Planned alternative.
- **Doubles or boundary isolation:** `tripsApi.duplicateAlternative` resolved with updated trip containing new draft.
- **Edge cases:** When trip has no planned alternatives, option indicates no saved alternatives are available.

### 5. `Triage option 2: Create a new Draft creates an empty draft alternative`
- **Type:** Workflow integration test
- **Location:** `frontend/src/FeeFreeCancellationAndTriage.test.tsx`
- **Proves:** Selecting "Create a new Draft" calls `tripsApi.createDraft`, adds a fresh component-empty draft to the workspace, and dismisses triage modal.
- **Inputs/fixture:** Trip in post-cancellation state.
- **Doubles or boundary isolation:** `tripsApi.createDraft` resolved with updated trip.
- **Edge cases:** Handles version increment smoothly.

### 6. `Triage option 3: Done for now dismisses triage modal`
- **Type:** Component integration test
- **Location:** `frontend/src/FeeFreeCancellationAndTriage.test.tsx`
- **Proves:** Clicking "Done for now" closes the triage modal without initiating mutations, leaving traveler in the active Trip Workspace.
- **Inputs/fixture:** Post-cancellation triage dialog open.
- **Doubles or boundary isolation:** None needed.
- **Edge cases:** Workspace remains open and interactive for other actions.

### 7. `Trip deletion vs trip cancellation gating based on hasBookingHistory`
- **Type:** Component integration test
- **Location:** `frontend/src/FeeFreeCancellationAndTriage.test.tsx`
- **Proves:** Trips with `hasBookingHistory: false` show "Delete trip" (triggering `ConfirmDeleteModal`); trips with `hasBookingHistory: true` show "Cancel trip" (triggering `CancelTripModal`).
- **Inputs/fixture:** Unbooked trip vs. trip with booking history.
- **Doubles or boundary isolation:** Component rendered with props.
- **Edge cases:** Verifies gating in both `TripWorkspace.tsx` and `TripListSection.tsx`.

### 8. `Confirming Cancel Trip updates workspace to Canceled read-only state`
- **Type:** Workflow integration test
- **Location:** `frontend/src/FeeFreeCancellationAndTriage.test.tsx`
- **Proves:** Clicking "Cancel trip" opens `CancelTripModal`; confirming calls `tripsApi.cancelTrip` and transitions workspace to Canceled state with `CANCELED` header badge and descriptive banner.
- **Inputs/fixture:** Active trip with booking history.
- **Doubles or boundary isolation:** `tripsApi.cancelTrip` resolved with `{ status: 'CANCELED', version: 3 }`.
- **Edge cases:** Active booking is cleared or marked canceled.

### 9. `Canceled trip disables builder slots, hides draft mutations, and provides Duplicate Trip action`
- **Type:** Component integration test
- **Location:** `frontend/src/FeeFreeCancellationAndTriage.test.tsx`
- **Proves:** When `trip.status === 'CANCELED'`, Progressive Builder slots are read-only, draft mutation controls in `AlternativeCard` are hidden or disabled, details form inputs are disabled, and a primary "Duplicate into a new Trip" button is rendered.
- **Inputs/fixture:** Canceled trip (`status: 'CANCELED'`).
- **Doubles or boundary isolation:** `tripsApi.duplicateTrip` mocked.
- **Edge cases:** Clicking "Duplicate into a new Trip" opens `TripRevisionModal` configured for trip duplication.

### 10. `Collapsible Booking History section renders historical bookings and components`
- **Type:** Component integration test
- **Location:** `frontend/src/FeeFreeCancellationAndTriage.test.tsx`
- **Proves:** `BookingHistorySection` renders booking reference codes, timestamps, grand total prices, and flight/stay/rental breakdowns for all past and active bookings.
- **Inputs/fixture:** Trip with 1 active and 1 canceled booking in history.
- **Doubles or boundary isolation:** `tripsApi.getBookingHistory` resolved with fixture array.
- **Edge cases:** Collapsible section toggles open and closed accessible via keyboard.

### 11. `Expired and Past trips suppress cancel actions`
- **Type:** Component integration test
- **Location:** `frontend/src/FeeFreeCancellationAndTriage.test.tsx`
- **Proves:** Trips with `temporalStatus === 'PAST'` or `isExpired: true` hide or disable "Cancel Booking" and "Cancel Trip" buttons with explanatory messaging.
- **Inputs/fixture:** Past trip (`temporalStatus: 'PAST'`) and expired trip (`isExpired: true`).
- **Doubles or boundary isolation:** None needed.
- **Edge cases:** Verifies both Active Booking banner and header nav actions.

### 12. `Modal accessibility: focus trap, Escape dismissal, and live announcements`
- **Type:** Accessibility test
- **Location:** `frontend/src/FeeFreeCancellationAndTriage.test.tsx`
- **Proves:** All three modals (`CancelBookingModal`, `PostCancellationTriageModal`, `CancelTripModal`) trap keyboard focus, dismiss on `Escape`, restore focus on close, and emit `aria-live="polite"` status announcements.
- **Inputs/fixture:** Modals opened in test harness.
- **Doubles or boundary isolation:** DOM events simulated via `@testing-library/user-event`.
- **Edge cases:** Shift+Tab on first element wraps to last element.

### 13. `Profile screen renders CANCELED badge and handles trip cancellation directly`
- **Type:** Component integration test
- **Location:** `frontend/src/FeeFreeCancellationAndTriage.test.tsx`
- **Proves:** `ProfileScreen` and `TripListSection` render a `CANCELED` badge on canceled trips, and allow canceling booked trips directly from the profile trip list via `CancelTripModal`.
- **Inputs/fixture:** Profile summary with upcoming booked trip.
- **Doubles or boundary isolation:** `tripsApi.cancelTrip` and `onRefreshProfile` mocked.
- **Edge cases:** Successful cancellation refreshes profile list.

## Safe Verification Commands

- Focused API Client: `cmd /c npx vitest run src/api/tripsApi.test.ts`
- Related Cancellation & Triage Suite: `cmd /c npx vitest run src/FeeFreeCancellationAndTriage.test.tsx`
- Full Safe Suite: `cmd /c npm test`

## Optional Developer Checks

- Nonblocking manual verification in browser dev mode:
  - Run `cmd /c npm run dev`
  - Navigate to a trip with an active booking in the browser.
  - Verify that the Active Booking banner displays "Cancel Booking" alongside "View Booking Details".
  - Click "Cancel Booking" and verify dialog content, danger button styling, and subsequent transition to `PostCancellationTriageModal`.
  - Select "Use a saved alternative" and verify that a new draft alternative appears in the workspace.
  - Cancel the trip and verify the `CANCELED` header badge, warning banner, read-only slot controls, and "Duplicate into a new Trip" button.

## Exit Criteria

- [x] The planned red test fails for the intended reason before implementation, when applicable.
- [x] New and updated tests pass after implementation.
- [x] The broadest safe relevant repository test suite passes (`cmd /c npm test`).
- [x] Acceptance criteria map to executable evidence.
- [x] Routine automated tests do not perform unintended live or destructive operations.
- [x] Material risks and edge cases identified above are covered.
- [x] Any optional check is reported as nonblocking and is not represented as already performed.
