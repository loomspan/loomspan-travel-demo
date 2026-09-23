# Deliver Booking Review Confirmation and Active Itinerary Experience Implementation Plan

## Overview
- Ticket: `ai/thoughts/tickets/2026-09-22-p06-t02-booking-confirmation-and-active-itinerary-experience.md`
- Research: `ai/thoughts/research/2026-09-22-p06-t02-booking-confirmation-and-active-itinerary-experience.md`
- Outcome: Deliver interactive booking submission from `BookingReviewView.tsx` with client idempotency key and in-flight pending state, create a dedicated `BookingConfirmationView.tsx` with fictional references and accessibility announcements, render active booking banners and single-booking constraints in `TripWorkspace.tsx`, and project `BOOKED` badges and primary booking references to `ProfileScreen.tsx` and `TripListSection.tsx`.

## Current State

In P06-T01, backend booking infrastructure was established:
- Endpoints `POST /api/trips/{tripId}/bookings`, `GET /api/trips/{tripId}/bookings/active`, and `GET /api/trips/{tripId}/bookings` (`src/main/java/app/detour/trip/TripController.java:251-277`) return authoritative `BookingResponse` payloads containing primary reference `bookingReference` (`DT-XXXXXX`), component codes `airfareReference` (`FL-XXXXXX`), `stayReference` (`HT-XXXXXX`), `rentalReference` (`RC-XXXXXX`), selections, and tallies.
- Single active booking constraint is strictly enforced by database unique index `uq_detour_booking_active_trip` on `active_trip_id` in `detour_booking` (`V17__create_booking_schema.sql:27`).

However, the frontend and profile projections remain unintegrated:
- `frontend/src/api/tripsApi.ts` lacks `BookingResponse`, `CreateBookingRequest` types, and `createBooking`, `getActiveBooking`, and `getBookingHistory` client methods.
- `frontend/src/components/BookingReviewView.tsx:269-281` contains a hardcoded disabled "Confirm Booking" button and placeholder note "Ready for Phase 6 simulated booking implementation." It lacks UUID idempotency generation, pending loading announcements, and 409 conflict error rendering.
- `BookingConfirmationView.tsx` does not exist.
- `frontend/src/components/TripWorkspace.tsx:157` only supports `'workspace' | 'compare' | 'booking-review'` view modes, lacks `activeBooking` state, does not display an Active Booking summary banner, and does not restrict "Select for Booking Review" on other Planned alternatives when an active booking exists.
- `frontend/src/components/AlternativeCard.tsx:155-164` renders "Select for Booking Review" unconditionally on all Planned alternatives without `BOOKED` status badges or disabled constraints.
- `frontend/src/components/ItineraryComparisonView.tsx:157-164, 314-321` renders "Select for Booking Review" unconditionally.
- `src/main/java/app/detour/trip/TripProfileSummary.java:7-22` and `frontend/src/api/tripsApi.ts:11-26` lack `primaryBookingReference`. `TripListSection.tsx:45-68` renders count pill `{trip.bookedCount > 0 && <span className="count-pill badge-booked">{trip.bookedCount} Booked</span>}`, but does not display a `BOOKED` status badge in the trip card header nor the primary booking reference string.

## Desired End State

1. **Booking Submission Flow (`BookingReviewView.tsx`):**
   - The "Confirm Booking" button is active and interactive.
   - Clicking generates a client UUID `idempotencyKey` and submits `POST /api/trips/{tripId}/bookings` with `plannedItineraryId`, `expectedVersion`, and `idempotencyKey`.
   - Accessible in-flight state is displayed: button disabled with loading indicator and `aria-live="polite"` region announcing "Reserving inventory...".
   - If HTTP 409 Conflict occurs (such as `INVENTORY_CONFLICT` or `ALREADY_BOOKED`), an accessible error summary (`role="alert"`) displays the unavailable components or reason, preserves user selections intact, and offers a button to return to the workspace.
   - On success, transitions to the Booking Confirmation view.
2. **Dedicated Confirmation Screen (`BookingConfirmationView.tsx`):**
   - Renders a prominent success banner with the primary reference (e.g. `Booking Reference: DT-K8M2P4`).
   - Renders component locator codes with copy-to-clipboard buttons and accessible labels:
     - Airfare: `Airline Record Locator: FL-XXXXXX`
     - Stay: `Accommodation Confirmation: HT-XXXXXX`
     - Rental: `Rental Confirmation: RC-XXXXXX`
     - Omission of reference rows for unselected optional components.
   - Displays traveler summary (count, ages), destination, and dates.
   - Displays itemized cost breakdown and grand total.
   - Displays simulated booking disclosure: *"This was a simulated reservation using fictional inventory. No credit card was charged and no live supplier booking was made."*
   - Announces the booking reference via `aria-live="polite"`.
   - Provides navigation actions: "View in Trip Workspace" and "View All Trips".
3. **Workspace Active Booking & Single-Booking Constraint (`TripWorkspace.tsx`, `AlternativeCard.tsx`, `ItineraryComparisonView.tsx`):**
   - `TripWorkspace` retrieves and maintains `activeBooking`.
   - When active booking exists, renders a prominent **Active Booking** summary card at the top of the workspace with booking reference, booked date, grand total, and a "View Booking Details" button.
   - The booked Planned alternative displays a distinct, accessible `BOOKED` badge.
   - All other Planned alternatives in both `AlternativeCard` and `ItineraryComparisonView` have "Select for Booking Review" disabled with explanatory copy: *"This trip already has an active booking. Only one active booking is permitted per trip."*
   - Travelers can continue creating/duplicating drafts, but cannot enter review or confirm a second booking.
4. **Profile & Trip List Projections (`ProfileScreen.tsx`, `TripListSection.tsx`, `TripProfileSummary`):**
   - `TripProfileSummary` on backend and frontend carries `primaryBookingReference`.
   - Booked trips display a `BOOKED` status badge in the header and the primary booking reference on the card.
   - Upcoming vs Past grouping remains purely date-derived without mutable scheduled jobs.

## Scope

### In scope
- Backend `TripProfileSummary`, `TripRepository`, and `TripService` extension to expose `primaryBookingReference`.
- Frontend API client types and methods in `frontend/src/api/tripsApi.ts`.
- `BookingReviewView.tsx` interactive submission, pending loading state, and 409 conflict error rendering.
- `BookingConfirmationView.tsx` component creation with reference codes, copy actions, disclosure, and accessibility.
- `TripWorkspace.tsx` active booking banner, state loading, and view transitions.
- `AlternativeCard.tsx` and `ItineraryComparisonView.tsx` `BOOKED` badge and single-active-booking constraint enforcement.
- `TripListSection.tsx` `BOOKED` badge and primary reference display.
- Styling in `frontend/src/style.css` for active booking banners, confirmation views, and badges adhering to WCAG AA contrast.
- Automated tests in Vitest and Spring Boot JUnit covering all acceptance criteria.

### Out of scope
- Real payment gateway or billing address integration (simulated inventory only).
- Printable PDF generation, email dispatches, or calendar (.ics) exports.
- Cancellation flows (handled in downstream tickets P06-T03 and P06-T04).
- Version 2 Events.

## Active Project Guardrails

None recorded in `ai/thoughts/design-lens.md`.

## Impact and Risk Analysis

1. **Client-Side Concurrency & Idempotency:**
   - *Risk:* Double-clicking or network retries causing duplicate bookings or version conflicts.
   - *Mitigation:* Generate a client UUID `idempotencyKey` once per submission attempt; disable the confirm button immediately upon click; if the server replays with `200 OK` (idempotent duplicate), treat it as a successful booking.
2. **Version Staleness on Booking:**
   - *Risk:* The trip version increments upon booking. Subsequent workspace edits might fail if `trip.version` is not refreshed.
   - *Mitigation:* Upon successful booking, `TripWorkspace` updates `activeBooking` and refreshes trip state via `tripsApi.getTrip(trip.id)` (or propagates the incremented version), ensuring optimistic concurrency remains aligned.
3. **Accessibility & Screen Reader Compliance:**
   - *Risk:* Screen readers missing dynamic confirmation references or inventory conflict errors.
   - *Mitigation:* Add `aria-live="polite"` regions announcing the booking reference on confirmation and in-flight reservation message on submission; wrap error summaries in `role="alert"` with descriptive copy.
4. **Performance & Avoidance of N+1 Queries on Profile:**
   - *Risk:* Querying active bookings individually for each trip on the profile screen would cause N+1 HTTP requests.
   - *Mitigation:* Extend `TripProfileSummary` on the backend so the profile query returns `primaryBookingReference` in the initial payload.

## Implementation Approach

1. **Backend Layer:**
   - Extend `TripProfileSummary` record to include `String primaryBookingReference`.
   - In `TripRepository` and `JdbcTripRepository`, implement `Optional<String> findPrimaryBookingReference(long tripId)` querying `detour_booking` ordered by `CASE WHEN status = 'ACTIVE' THEN 0 ELSE 1 END, created_at DESC LIMIT 1`.
   - In `TripService.tripsProfile`, populate `primaryBookingReference`.
   - Update `BookingApiIntegrationTest` to verify `primaryBookingReference` appears on profile after booking.
2. **API Layer (`frontend/src/api/tripsApi.ts`):**
   - Add `BookingResponse` and `CreateBookingRequest` interfaces.
   - Add `primaryBookingReference?: string | null;` to `TripProfileSummary`.
   - Add `createBooking`, `getActiveBooking`, and `getBookingHistory` client methods.
   - Add unit tests in `tripsApi.test.ts`.
3. **Submission & View Layer (`BookingReviewView.tsx`):**
   - Add local state for `isSubmitting`, `submitError` (with code, message, fields), and `submitAnnouncement`.
   - Implement `handleConfirmBooking` generating UUID `idempotencyKey` and calling `tripsApi.createBooking`.
   - On error: parse `IdentityApiError` and display error alert box with unavailable components list (`fields.airfare`, `fields.stay`, `fields.rental`).
   - On success: call prop `onBookingSuccess: (booking: BookingResponse) => void`.
4. **Confirmation Component (`BookingConfirmationView.tsx`):**
   - Create `frontend/src/components/BookingConfirmationView.tsx`.
   - Present primary booking reference (`DT-XXXXXX`), component codes (`FL-XXXXXX`, `HT-XXXXXX`, `RC-XXXXXX` conditionally), copy buttons with clipboard write, traveler info, itemized pricing, disclosure note, and navigation buttons.
5. **Workspace Orchestration (`TripWorkspace.tsx`):**
   - Add `workspaceView: 'workspace' | 'compare' | 'booking-review' | 'booking-confirmation'`.
   - Fetch `tripsApi.getActiveBooking(trip.id)` on mount and upon booking confirmation (catching 404 cleanly as null).
   - If `activeBooking` exists:
     - Render `ActiveBookingBanner` at the top of workspace with "View Booking Details" action.
     - Pass `isBooked={alt.id === activeBooking.plannedItineraryId}` and `hasActiveBooking={Boolean(activeBooking)}` to `AlternativeCard`.
     - In `AlternativeCard`: render `BOOKED` badge on booked alternative; disable "Select for Booking Review" on all other planned alternatives with explanatory message.
     - Pass `hasActiveBooking={Boolean(activeBooking)}` to `ItineraryComparisonView` and disable review selection.
6. **Profile Projections (`TripListSection.tsx`):**
   - In `TripCard`: render `<span className="badge badge-booked">BOOKED</span>` when `trip.bookedCount > 0`.
   - Render `Booking Reference: {trip.primaryBookingReference}` on the card when present.
7. **Styles (`frontend/src/style.css`):**
   - Add styling for the Active Booking banner, confirmation screen, code copy buttons, and conflict alert boxes meeting WCAG AA contrast.

---

## Phase 1: Backend DTO, Query & API Extension for Primary Booking Reference

### Changes
- [x] `src/main/java/app/detour/trip/TripProfileSummary.java` — Add `String primaryBookingReference` to record fields.
- [x] `src/main/java/app/detour/trip/TripRepository.java` — Add `Optional<String> findPrimaryBookingReference(long tripId);`.
- [x] `src/main/java/app/detour/trip/JdbcTripRepository.java` — Implement `findPrimaryBookingReference(long tripId)` using `SELECT booking_reference FROM detour_booking WHERE trip_id = ? ORDER BY CASE WHEN status = 'ACTIVE' THEN 0 ELSE 1 END, created_at DESC LIMIT 1`.
- [x] `src/main/java/app/detour/trip/TripService.java` — In `tripsProfile`, pass `trips.findPrimaryBookingReference(trip.id()).orElse(null)` into `TripProfileSummary`.
- [x] `src/test/java/app/detour/booking/BookingApiIntegrationTest.java` — In `retrievesActiveBookingAndHistoryAndUpdatesProfile`, assert `upcoming[0].primaryBookingReference` matches pattern `^DT-[A-Z0-9]{6}$`.

### Automated verification
- [x] `.\mvnw.cmd test -Dtest=BookingApiIntegrationTest` — Expected proof: all integration tests pass, including new profile summary assertion.
- [x] `.\mvnw.cmd test -Dtest=TripApiIntegrationTest` — Expected proof: all trip profile tests pass with updated `TripProfileSummary` signature.

### Optional developer checks
- [ ] None.

---

## Phase 2: Frontend API Client & Types Extension (`tripsApi.ts`)

### Changes
- [x] `frontend/src/api/tripsApi.ts` —
  - Add `primaryBookingReference?: string | null;` to `TripProfileSummary`.
  - Export `type CreateBookingRequest = { plannedItineraryId: string; expectedVersion: number; idempotencyKey?: string; }`.
  - Export `type BookingResponse = { id: string; tripId: string; plannedItineraryId: string; bookingReference: string; status: 'ACTIVE' | 'CANCELED' | string; grandTotalCents: number; idempotencyKey: string; bookedAt: string; canceledAt?: string | null; airfareReference?: string | null; stayReference?: string | null; rentalReference?: string | null; selections: DraftSelectionResponse; tally: ItineraryTallyResponse; }`.
  - Add client methods to `tripsApi`:
    - `createBooking: (tripId: string, payload: CreateBookingRequest) => Promise<BookingResponse>`
    - `getActiveBooking: (tripId: string) => Promise<BookingResponse>`
    - `getBookingHistory: (tripId: string) => Promise<BookingResponse[]>`
- [x] `frontend/src/api/tripsApi.test.ts` —
  - Add unit tests for `createBooking` (verifying `POST /api/trips/{tripId}/bookings`, body structure, `Idempotency-Key` header).
  - Add unit tests for `getActiveBooking` (verifying `GET /api/trips/{tripId}/bookings/active`).
  - Add unit tests for `getBookingHistory` (verifying `GET /api/trips/{tripId}/bookings`).

### Automated verification
- [x] `npm.cmd test -- src/api/tripsApi.test.ts` — Expected proof: all tripsApi tests pass.

### Optional developer checks
- [ ] None.

---

## Phase 3: Interactive Booking Submission & Conflict Handling (`BookingReviewView.tsx`)

### Changes
- [x] `frontend/src/components/BookingReviewView.tsx` —
  - Update props: add `onBookingSuccess: (booking: BookingResponse) => void`.
  - Add local states: `isSubmitting: boolean`, `submitAnnouncement: string`, `errorMessage: string | null`, `errorFields: Record<string, string> | null`.
  - Activate the "Confirm Booking" button: remove `disabled={true}`, attach `onClick={handleConfirmBooking}`, set `disabled={isSubmitting}`, and render "Reserving inventory…" when `isSubmitting`.
  - Add `aria-live="polite"` region for `submitAnnouncement`.
  - Implement `handleConfirmBooking`:
    - Generate client-side UUID `idempotencyKey` via `crypto.randomUUID()` with fallback.
    - Set `isSubmitting(true)` and `submitAnnouncement('Reserving inventory...')`.
    - Call `tripsApi.createBooking(trip.id, { plannedItineraryId: alternative.id, expectedVersion: trip.version, idempotencyKey })`.
    - On success: invoke `onBookingSuccess(response)`.
    - On error: catch `IdentityApiError`, clear loading state, populate `errorMessage` and `errorFields`.
  - Render accessible error summary (`role="alert"`) when error occurs:
    - Display error heading (e.g., "Reservation could not be completed").
    - If `errorFields` exist (e.g. `airfare`, `stay`, `rental`), render itemized list of component conflict descriptions.
    - Provide action buttons: "Return to Trip Workspace" (`onBack()`).
    - Keep user's selections and view intact.

### Automated verification
- [x] `npm.cmd test -- src/ItineraryComparisonAndBookingReview.test.tsx` — Expected proof: existing booking review tests updated and passing.

### Optional developer checks
- [ ] None.

---

## Phase 4: Dedicated Booking Confirmation Screen (`BookingConfirmationView.tsx`)

### Changes
- [x] `frontend/src/components/BookingConfirmationView.tsx` — [NEW] Create component accepting:
  ```typescript
  export type BookingConfirmationViewProps = {
    trip: TripResponse;
    booking: BookingResponse;
    onViewInWorkspace: () => void;
    onViewAllTrips: () => void;
  };
  ```
  - `aria-live="polite"` status region announcing: `"Booking confirmed! Reference: ${booking.bookingReference}"`.
  - Prominent success header: `"Booking Confirmed!"` and `Booking Reference: DT-XXXXXX`.
  - Fictional component confirmation codes section:
    - If `booking.airfareReference`: `Airline Record Locator: {booking.airfareReference}`.
    - If `booking.stayReference`: `Accommodation Confirmation: {booking.stayReference}`.
    - If `booking.rentalReference`: `Rental Confirmation: {booking.rentalReference}`.
    - Omit row if optional component was not selected.
    - Each code includes a "Copy" button that writes to `navigator.clipboard.writeText` with accessible feedback (`aria-label={`Copy confirmation code ${code}`}`).
  - Trip parameters summary: destination name, traveler count & ages, travel dates.
  - Itemized financial summary breakdown: Airfare subtotal, Stay subtotal, Rental subtotal, Grand Total.
  - Mandatory fictional inventory disclosure callout (`role="note"`):
    *"This was a simulated reservation using fictional inventory. No credit card was charged and no live supplier booking was made."*
  - Actions: "View in Trip Workspace" button (`onViewInWorkspace`) and "View All Trips" button (`onViewAllTrips`).
- [x] `frontend/src/components/BookingConfirmationView.test.tsx` — [NEW] Vitest component tests:
  - Verifies master `DT-` reference and `aria-live` announcement.
  - Verifies component codes rendered with copy buttons, and omission of unselected component codes.
  - Verifies simulated booking disclosure text.
  - Verifies clipboard copy action and feedback.
  - Verifies navigation callbacks ("View in Trip Workspace", "View All Trips").
  - Verifies WCAG AA semantic elements and keyboard accessibility.

### Automated verification
- [x] `npm.cmd test -- src/components/BookingConfirmationView.test.tsx` — Expected proof: all tests pass.

### Optional developer checks
- [ ] None.

---

## Phase 5: Workspace Active Booking Banner & Single-Booking Constraint

### Changes
- [x] `frontend/src/components/TripWorkspace.tsx` —
  - Add `'booking-confirmation'` to `workspaceView` type (`'workspace' | 'compare' | 'booking-review' | 'booking-confirmation'`).
  - Add `activeBooking: BookingResponse | null` state (and accept optional `initialActiveBooking?: BookingResponse | null` in props).
  - Add `useEffect` to fetch `tripsApi.getActiveBooking(trip.id)` on mount/update (catching 404 as `null`).
  - When `activeBooking` exists:
    - Render prominent **Active Booking** summary banner at the top of the workspace:
      - Title: "Active Booking"
      - Reference: `bookingReference`
      - Booked date: formatted `bookedAt`
      - Grand total: formatted `grandTotalCents`
      - Action: "View Booking Details" button (switches `workspaceView` to `'booking-confirmation'`).
  - In `handleBookingSuccess`:
    - Set `activeBooking` to newly created booking.
    - Set `workspaceView('booking-confirmation')`.
    - Trigger trip refresh via `tripsApi.getTrip(trip.id)` / `onTripUpdated` so version concurrency stays aligned.
  - In `workspaceView === 'booking-confirmation'`:
    - Render `<BookingConfirmationView>` with actions to return to `'workspace'` or call `onBack()`.
  - Pass `isBooked={activeBooking?.plannedItineraryId === alt.id}` and `hasActiveBooking={Boolean(activeBooking)}` to `AlternativeCard`.
  - Pass `hasActiveBooking={Boolean(activeBooking)}` to `ItineraryComparisonView`.
- [x] `frontend/src/components/AlternativeCard.tsx` —
  - Add props: `isBooked?: boolean`, `hasActiveBooking?: boolean`, `onViewBookingDetails?: () => void`.
  - When `isBooked`:
    - Display `<span className="badge badge-booked">BOOKED</span>` in the card header.
    - Replace or supplement review button with "View Booking Details" or booked indicator.
  - When `hasActiveBooking && !isBooked`:
    - Disable "Select for Booking Review" (`disabled={true}`, `aria-disabled="true"`).
    - Display explanatory copy: `<p className="hint booking-disabled-hint">This trip already has an active booking. Only one active booking is permitted per trip.</p>`.
- [x] `frontend/src/components/ItineraryComparisonView.tsx` —
  - Add prop `hasActiveBooking?: boolean`.
  - When `hasActiveBooking`:
    - Disable "Select for Booking Review" on all comparison columns.
    - Display explanatory note: `"This trip already has an active booking. Only one active booking is permitted per trip."`.

### Automated verification
- [x] `npm.cmd test -- src/ItineraryComparisonAndBookingReview.test.tsx` — Expected proof: workspace active booking banner, `BOOKED` badge, single-booking constraint, and confirmation transition tests pass.

### Optional developer checks
- [ ] None.

---

## Phase 6: Profile & Trip List `BOOKED` Badge and Booking Reference Projections

### Changes
- [x] `frontend/src/components/TripListSection.tsx` —
  - In `TripCard`:
    - If `trip.bookedCount > 0`: display `<span className="badge badge-booked">BOOKED</span>` in the card header next to Upcoming/Past badge.
    - If `trip.primaryBookingReference`: display `<p className="trip-card-booking-ref">Booking Reference: <strong>{trip.primaryBookingReference}</strong></p>`.
- [x] `frontend/src/style.css` —
  - Add styles for:
    - `.active-booking-banner`: prominent card styling with teal/purple accent, booking reference highlight, details button.
    - `.booking-confirmation-view`: full confirmation layout, status banner, confirmation code items with copy button.
    - `.copy-code-btn`: accessible copy button with hover/focus states.
    - `.booking-conflict-alert`: accessible 409 error alert box with high contrast red border and readable text.
    - `.booking-disabled-hint`: accessible muted guidance text.
  - Verify WCAG AA contrast standards across all new classes.
- [x] `frontend/src/App.test.tsx` & `frontend/src/ProgressiveTripBuilder.test.tsx` —
  - Add tests in `App.test.tsx` verifying booked trips on the Profile screen display the `BOOKED` badge and `primaryBookingReference`.

### Automated verification
- [x] `npm.cmd test` — Expected proof: all frontend tests pass across the entire suite.
- [x] `.\mvnw.cmd test` — Expected proof: all backend tests pass across the entire suite.

### Optional developer checks
- [ ] None.

---

## Test Strategy

1. **Unit & Component Level (Vitest & React Testing Library):**
   - API client: `tripsApi.test.ts` tests `createBooking`, `getActiveBooking`, and `getBookingHistory` mock endpoints and headers.
   - Confirmation component: `BookingConfirmationView.test.tsx` verifies all rendered codes, copy interactions, disclosure, and `aria-live` region.
   - Review & Submission: `ItineraryComparisonAndBookingReview.test.tsx` tests the interactive confirm button, idempotency key generation, pending announcement, 409 conflict error rendering, and transition to confirmation.
   - Workspace constraint: Tests active booking banner rendering, `BOOKED` badge on booked alternative, and disabled "Select for Booking Review" with explanatory copy on unbooked alternatives.
   - Profile projections: `App.test.tsx` tests that booked trips in Upcoming and Past sections display `BOOKED` badge and primary reference.
2. **Backend Integration Level (Spring Boot & JUnit):**
   - `BookingApiIntegrationTest.java` asserts that `GET /api/trips` returns `primaryBookingReference` matching `^DT-[A-Z0-9]{6}$` for booked trips.
3. **Accessibility & WCAG AA:**
   - ARIA roles: `role="alert"` on conflict summaries, `role="note"` on fictional disclosures, `aria-live="polite"` on reservation and confirmation announcements.
   - Focus management: proper focusability of copy buttons and error actions.

---

## Acceptance-Criteria Traceability

| Acceptance criterion | Planned code evidence | Planned test evidence |
| --- | --- | --- |
| Clicking "Confirm Booking" on `BookingReviewView` sends `POST /api/trips/{tripId}/bookings` with an idempotency key and displays a pending state. | `BookingReviewView.tsx` `handleConfirmBooking` invoking `tripsApi.createBooking` with UUID `idempotencyKey` and `isSubmitting` state. | `ItineraryComparisonAndBookingReview.test.tsx` asserting button click sends POST with idempotency key and renders "Reserving inventory…". |
| On success, the application renders the Booking Confirmation screen showing the master `DT-` booking reference, individual component confirmation codes, itemized pricing, and the simulated booking disclosure. | `BookingConfirmationView.tsx` and `TripWorkspace.tsx` `'booking-confirmation'` view mode. | `BookingConfirmationView.test.tsx` and `ItineraryComparisonAndBookingReview.test.tsx` asserting master DT- code, component codes, totals, and disclosure. |
| On inventory conflict (HTTP 409), the view displays an accessible error summary explaining why the reservation failed and keeps the traveler's draft/planned selections intact. | `BookingReviewView.tsx` `role="alert"` error summary with `errorFields` itemization and return action. | `ItineraryComparisonAndBookingReview.test.tsx` mock 409 conflict test asserting error summary display and persistent selections. |
| In the Trip Workspace, an active booking displays a prominent Active Booking card and badges the corresponding itinerary as `BOOKED`. | `TripWorkspace.tsx` Active Booking banner and `AlternativeCard.tsx` `.badge-booked` badge. | `ItineraryComparisonAndBookingReview.test.tsx` asserting Active Booking banner and `BOOKED` badge on planned alternative. |
| When an active booking exists, "Select for Booking Review" is disabled on all other Planned alternatives with clear explanatory copy. | `AlternativeCard.tsx` and `ItineraryComparisonView.tsx` disabled review buttons with single-active-booking hint. | `ItineraryComparisonAndBookingReview.test.tsx` asserting disabled buttons and explanatory hint on second planned alternative. |
| On the Profile screen, booked trips display the `BOOKED` status badge and primary booking reference under Upcoming or Past sections. | `TripProfileSummary.java`, `TripService.java`, `TripListSection.tsx` header `BOOKED` badge and reference paragraph. | `App.test.tsx` and `BookingApiIntegrationTest.java` verifying `BOOKED` badge and `primaryBookingReference` presence. |
| All confirmation and status views meet WCAG AA contrast standards, keyboard navigation requirements, and screen-reader announcements verified in Vitest component tests. | `aria-live="polite"` regions, `role="alert"` containers, semantic buttons, and accessible CSS colors in `style.css`. | `BookingConfirmationView.test.tsx` and `ItineraryComparisonAndBookingReview.test.tsx` asserting ARIA attributes and keyboard events. |

---

## Risks and Rollback/Recovery

- **Risk:** Idempotency key collision or stale client version.
  - *Recovery:* Client UUID generation using standard RFC 4122 v4; on HTTP 409 `VERSION_CONFLICT`, user is presented with a clear explanation and prompt to refresh the trip.
- **Risk:** Breaking existing tests that expected "Confirm Booking" to be disabled with staging note.
  - *Recovery:* Update `ItineraryComparisonAndBookingReview.test.tsx` to test the active/interactive button behavior while adding focused tests for submission, loading, and error states.
- **Rollback:** All changes are purely additive to the API client, DTOs, and UI components; rolling back git commit restores the staged disabled button and previous workspace view modes.

---

## References

- Ticket: `ai/thoughts/tickets/2026-09-22-p06-t02-booking-confirmation-and-active-itinerary-experience.md`
- Research: `ai/thoughts/research/2026-09-22-p06-t02-booking-confirmation-and-active-itinerary-experience.md`
- Backend Controller: `src/main/java/app/detour/trip/TripController.java:251-277`
- Booking Service & Transaction: `src/main/java/app/detour/booking/BookingService.java`, `BookingTransactionExecutor.java`
- Frontend Review View: `frontend/src/components/BookingReviewView.tsx`
- Frontend Workspace: `frontend/src/components/TripWorkspace.tsx`
- Profile List Section: `frontend/src/components/TripListSection.tsx`
