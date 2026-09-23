# Deliver Booking Review Confirmation and Active Itinerary Experience Testing Plan

## Change Summary

Ticket P06-T02 delivers interactive simulated booking submission, pending loading announcements, conflict handling, dedicated confirmation screens with fictional reference codes, workspace active booking banners with single-booking constraints, and profile `BOOKED` badge/reference projections.

This testing plan specifies the automated test suite across the frontend Vitest layer and backend Spring Boot layer to verify that all acceptance criteria and edge cases are proven deterministically with zero regressions.

## Impacted Areas and Risks

| Category | Risk | Planned evidence |
| --- | --- | --- |
| Booking Submission Flow | Duplicate submissions on double-clicks or network delays; missing client idempotency key. | Vitest test verifying "Confirm Booking" click immediately disables button, displays "Reserving inventory…", and sends a valid client UUID `idempotencyKey` via `tripsApi.createBooking`. |
| Inventory Conflict Handling (HTTP 409) | Traveler selections or inputs cleared on conflict; unannounced failure state for screen readers. | Vitest test mocking HTTP 409 `INVENTORY_CONFLICT` with field-level details (`airfare`, `stay`, `rental`), asserting an accessible `role="alert"` container, itemized conflict messages, intact selections, and return button. |
| Confirmation Screen Representation | Fictional codes omitted or incorrectly mapped; simulated disclosure missing; broken copy actions. | Vitest test verifying master `DT-` reference, component codes (`FL-`, `HT-`, `RC-`), omission of optional unselected component rows, simulated booking disclosure, and clipboard copy interactions with feedback. |
| Workspace Active Booking Presentation | Active booking not shown; travelers able to initiate a second booking on the same trip. | Vitest test asserting the Active Booking banner with details button, `BOOKED` badge on the booked Planned alternative, and disabled "Select for Booking Review" with single-booking explanation on other alternatives. |
| Comparison View Constraints | Comparison matrix allows initiating booking review when trip already has an active booking. | Vitest test asserting comparison view disables "Select for Booking Review" when `hasActiveBooking` is true, displaying explanatory guidance. |
| Profile & Trip Card Projections | Booked trips on profile do not display `BOOKED` badge or master booking reference string. | Spring Boot JUnit test verifying `primaryBookingReference` in `TripProfileSummary` and Vitest test in `App.test.tsx` verifying card header badge and reference text. |
| Accessibility & Screen Readers | Missing `aria-live` announcements during reservation pending state or confirmation load; low contrast badges. | Vitest tests asserting `aria-live="polite"` regions for pending reservation and confirmation reference, and semantic accessible buttons. |

## Existing Coverage and Environment Constraints

- **Frontend Test Environment:** Vitest 4.1.11 with jsdom 27.4.0 and `@testing-library/react` 16.3.0.
  - Windows PowerShell requires invoking test scripts with `npm.cmd test` rather than `npm test`.
  - JSDOM does not natively implement `navigator.clipboard.writeText`; tests interacting with copy buttons must mock `navigator.clipboard.writeText = vi.fn().mockResolvedValue(undefined)`.
- **Existing Frontend Suites:**
  - `frontend/src/ItineraryComparisonAndBookingReview.test.tsx` (8 tests): contains baseline comparison matrix and staged disabled booking review tests. Test at lines 455–513 currently expects "Confirm Booking" to be disabled with staging note.
  - `frontend/src/api/tripsApi.test.ts` (10 tests): tests client request encoding, headers, and methods.
  - `frontend/src/App.test.tsx` (27 tests): end-to-end user workflows, profile views, and trip operations.
- **Backend Test Environment:** Spring Boot 3 with JUnit 5 and MockMvc against an embedded H2 database (`.\mvnw.cmd test -Dtest=BookingApiIntegrationTest`).

## Failing Test First

### 1. Frontend Red Test: Interactive Booking Submission and Pending State
- **Name:** `submitsBookingWithIdempotencyKeyAndTransitionsToConfirmation`
- **Type:** Frontend Component Integration Test (Vitest / React Testing Library)
- **Location:** `frontend/src/ItineraryComparisonAndBookingReview.test.tsx`
- **Arrange/Act/Assert:**
  - *Arrange:* Render `TripWorkspace` with a planned alternative and navigate to `BookingReviewView`.
  - *Act:* Locate the "Confirm Booking" button and click it.
  - *Assert:* Expect button to be enabled (not disabled), expect pending announcement "Reserving inventory…", expect `tripsApi.createBooking` called with UUID idempotency key, and expect transition to `BookingConfirmationView` displaying `Booking Reference: DT-`.
  - *Expected pre-fix failure:* Button is hardcoded `disabled={true}`, has text "Ready for Phase 6 simulated booking implementation.", and `BookingConfirmationView` does not exist.

### 2. Backend Red Test: Profile Projection with Primary Booking Reference
- **Name:** `retrievesActiveBookingAndHistoryAndUpdatesProfile`
- **Type:** Backend Integration Test (Spring Boot MockMvc)
- **Location:** `src/test/java/app/detour/booking/BookingApiIntegrationTest.java:538-542`
- **Arrange/Act/Assert:**
  - *Arrange:* Create trip, plan itinerary, submit booking.
  - *Act:* Perform `GET /api/trips`.
  - *Assert:* `andExpect(jsonPath("$.upcoming[0].primaryBookingReference").value(matchesPattern("^DT-[A-Z0-9]{6}$")))`.
  - *Expected pre-fix failure:* JSON response field `primaryBookingReference` is missing because `TripProfileSummary` does not include the field.

---

## Tests to Add or Update

### 1. `frontend/src/api/tripsApi.test.ts` — Booking Client Endpoint Tests
- **Type:** Unit / API Client Test
- **Location:** `frontend/src/api/tripsApi.test.ts`
- **Proves:**
  - `createBooking` sends `POST /api/trips/{tripId}/bookings` with `Content-Type: application/json`, `X-XSRF-TOKEN`, `Idempotency-Key` header, and JSON body containing `plannedItineraryId`, `expectedVersion`, and `idempotencyKey`.
  - `getActiveBooking` sends `GET /api/trips/{tripId}/bookings/active`.
  - `getBookingHistory` sends `GET /api/trips/{tripId}/bookings`.
- **Inputs/fixture:** Standard mock UUIDs, trip ID `'trip-1'`, planned itinerary ID `'planned-1'`.
- **Doubles or boundary isolation:** Global `fetch` mocked via `vi.fn()`.
- **Edge cases:** Verification that `idempotencyKey` is passed both in JSON body and in `Idempotency-Key` HTTP header.

### 2. `frontend/src/components/BookingConfirmationView.test.tsx` — Confirmation Screen Component Tests
- **Type:** Component Test
- **Location:** `frontend/src/components/BookingConfirmationView.test.tsx`
- **Proves:**
  - Renders master booking reference (e.g., `DT-K8M2P4`) with `aria-live="polite"` announcement.
  - Renders component confirmation codes:
    - Airline Record Locator: `FL-W3X8PL`
    - Accommodation Confirmation: `HT-4M9Q2P`
    - Rental Confirmation: `RC-7T1N5V`
  - Omission of component rows when optional components are not selected (e.g. airfare-only booking).
  - Renders traveler details (count and ages), destination, and dates.
  - Renders itemized pricing breakdown and grand total.
  - Renders mandatory simulated booking disclosure: *"This was a simulated reservation using fictional inventory. No credit card was charged and no live supplier booking was made."*
  - Copies confirmation code to clipboard upon clicking "Copy" button and displays accessible visual confirmation.
  - Triggers `onViewInWorkspace` and `onViewAllTrips` navigation callbacks when action buttons are clicked.
- **Inputs/fixture:** Mock `TripResponse` and mock `BookingResponse` with multi-component selections.
- **Doubles or boundary isolation:** Mock `navigator.clipboard.writeText`.
- **Edge cases:** Unselected stay and rental components; single traveler vs multi-traveler formatting.

### 3. `frontend/src/ItineraryComparisonAndBookingReview.test.tsx` — Submission, Conflict & Workspace Constraints
- **Type:** Component & Integration Test
- **Location:** `frontend/src/ItineraryComparisonAndBookingReview.test.tsx`
- **Proves:**
  - **Update existing test (line 455):** Verify "Confirm Booking" is enabled by default and staging note is removed.
  - **New test `handlesInteractiveBookingSubmissionWithPendingStateAndConfirmation`:** Clicking "Confirm Booking" sets pending state (`disabled`, "Reserving inventory…"), calls `tripsApi.createBooking`, and renders confirmation view on success.
  - **New test `handles409InventoryConflictWithAccessibleAlertAndPreservedSelections`:**
    - Mock `tripsApi.createBooking` rejecting with `IdentityApiError('api', 409, 'INVENTORY_CONFLICT', {airfare: 'Selected flight sold out', stay: 'Accommodation unavailable'}, 'One or more components are unavailable')`.
    - Assert `role="alert"` container is rendered with specific component messages.
    - Assert traveler's itinerary selections remain displayed and intact.
    - Assert action button "Return to Trip Workspace" returns to workspace safely.
  - **New test `rendersActiveBookingBannerAndEnforcesSingleBookingConstraintInWorkspace`:**
    - Render `TripWorkspace` where `activeBooking` exists (or returns from `getActiveBooking`).
    - Assert Active Booking summary card is visible with master booking reference, booked date, grand total, and "View Booking Details" button.
    - Assert booked Planned alternative displays `<span className="badge badge-booked">BOOKED</span>`.
    - Assert other Planned alternatives display disabled "Select for Booking Review" button with hint: *"This trip already has an active booking. Only one active booking is permitted per trip."*
    - Assert creating drafts and duplicating alternatives remains enabled.
  - **New test `disablesSelectForBookingReviewInComparisonViewWhenActiveBookingExists`:**
    - In `ItineraryComparisonView`, when `hasActiveBooking` is true, assert "Select for Booking Review" is disabled across all comparison columns with explanatory note.
- **Inputs/fixture:** Mock trip with 2 planned alternatives and mock booking response.
- **Doubles or boundary isolation:** Mock `tripsApi.createBooking`, `tripsApi.getActiveBooking`, `tripsApi.getTrip`.
- **Edge cases:** Concurrent booking error (`ALREADY_BOOKED`), version conflict error (`VERSION_CONFLICT`).

### 4. `frontend/src/App.test.tsx` — Profile Screen Booked Projections
- **Type:** Integration Test
- **Location:** `frontend/src/App.test.tsx`
- **Proves:**
  - Renders Profile screen with a booked trip in Upcoming trips list.
  - Asserts `<span className="badge badge-booked">BOOKED</span>` is displayed on the trip card header.
  - Asserts `Booking Reference: DT-XXXXXX` is displayed on the trip summary card.
- **Inputs/fixture:** Mock profile with `TripProfileSummary` containing `bookedCount: 1` and `primaryBookingReference: 'DT-TEST01'`.
- **Doubles or boundary isolation:** Mock `identityApi.getProfile`.

### 5. `src/test/java/app/detour/booking/BookingApiIntegrationTest.java` — Backend Profile Projection
- **Type:** Spring Boot MockMvc Integration Test
- **Location:** `src/test/java/app/detour/booking/BookingApiIntegrationTest.java`
- **Proves:**
  - `GET /api/trips` returns `primaryBookingReference` matching `^DT-[A-Z0-9]{6}$` for booked trip, and `null` for unbooked trip.
- **Inputs/fixture:** Real H2 database transactions with registered user and booked itinerary.
- **Doubles or boundary isolation:** None (full integration test with database).

---

## Safe Verification Commands

- Focused frontend test:
  ```powershell
  npm.cmd test -- src/ItineraryComparisonAndBookingReview.test.tsx
  ```
- Related new component & API tests:
  ```powershell
  npm.cmd test -- src/components/BookingConfirmationView.test.tsx src/api/tripsApi.test.ts
  ```
- Focused backend test:
  ```powershell
  .\mvnw.cmd test -Dtest=BookingApiIntegrationTest
  ```
- Full safe frontend suite:
  ```powershell
  npm.cmd test
  ```
- Full safe backend suite:
  ```powershell
  .\mvnw.cmd test
  ```

## Optional Developer Checks

- None.

## Exit Criteria

- [x] The planned red test fails for the intended reason before implementation, when applicable.
- [x] New and updated tests pass after implementation.
- [x] The broadest safe relevant repository test suite passes.
- [x] Acceptance criteria map to executable evidence.
- [x] Routine automated tests do not perform unintended live or destructive operations.
- [x] Material risks and edge cases identified above are covered.
- [x] Any optional check is reported as nonblocking and is not represented as already performed.
