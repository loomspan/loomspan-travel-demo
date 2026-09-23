---
date: 2026-09-22
repository: loomspan-travel-demo
branch: main
commit: af529b5c19b64a555f3f708f59ef81c5989c7fc5
ticket: ai/thoughts/tickets/2026-09-22-p06-t02-booking-confirmation-and-active-itinerary-experience.md
tags: [booking, confirmation, active-itinerary, trip-workspace, profile, phase-6]
---

# Booking Confirmation and Active Itinerary Experience Research

## Research Question

How does the system currently handle booking submission, confirmation display, active booking state across the Trip Workspace, and booking projections on the Profile and Trip List, and what are the exact component structures, API contracts, tests, and operational constraints governing the delivery of ticket P06-T02?

## Summary

Backend booking persistence, deterministic inventory reservation, and retrieval endpoints were established in P06-T01 (`POST /api/trips/{tripId}/bookings`, `GET /api/trips/{tripId}/bookings/active`, and `GET /api/trips/{tripId}/bookings`), returning complete `BookingResponse` payloads containing primary fictional booking references (`DT-XXXXXX`), component-level locator codes (`FL-XXXXXX`, `HT-XXXXXX`, `RC-XXXXXX`), itemized selections, and authoritative tallies.

In the frontend, `BookingReviewView.tsx` currently stages a disabled "Confirm Booking" button and placeholder note. The API client `frontend/src/api/tripsApi.ts` does not yet define booking types or client methods. There is no `BookingConfirmationView.tsx` component. `TripWorkspace.tsx` does not load or track active bookings, allowing travelers to select any Planned alternative for booking review even if an active booking exists. Finally, `TripProfileSummary` on the backend and `TripListSection.tsx` on the frontend track `bookedCount` and `hasBookingHistory`, but do not expose or render the primary booking reference on trip summary cards.

## Repository State

- **Date:** 2026-09-22
- **Repository:** `loomspan-travel-demo`
- **Branch:** `main`
- **Commit:** `af529b5c19b64a555f3f708f59ef81c5989c7fc5`
- **Working Tree:** Clean (verified via `git status`, no uncommitted modifications)
- **Baseline Test Suite Status:**
  - Frontend Vitest: 7 test files, 72 tests passing (`npm test`).
  - Backend Spring Boot / JUnit: 14 integration tests passing (`mvnw.cmd test -Dtest=BookingApiIntegrationTest`).

## Current Behavior and Data Flow

### 1. Booking Creation Endpoint and Payload Contract

The backend booking endpoint was delivered in P06-T01:
- **HTTP Route:** `POST /api/trips/{tripId}/bookings` (`TripController.java:251-263`).
- **Request Body:** Parsed by `TripRequests.booking(request, headerKey)` (`TripRequests.java:136-146`):
  - `plannedItineraryId`: UUID of the Planned alternative to book.
  - `expectedVersion`: non-negative long matching current `detour_trip.version`.
  - `idempotencyKey`: non-blank client string passed either in the JSON body or via the `Idempotency-Key` HTTP header.
- **Execution & Transactions:**
  - Checked for existing booking with the same `idempotencyKey` in `BookingService.java:61-64`.
  - Executed inside `@Transactional` in `BookingTransactionExecutor.java:49-193`:
    1. Trip expiration check (`ClockConfiguration.PDX_ZONE` midnight on `startDate`).
    2. Version check against `trip.version()`.
    3. Active booking check (`bookingRepository.hasActiveBooking(trip.id())`).
    4. Deterministic inventory row locking (`flight_instance`, `accommodation_nightly_inventory`, `rental_unit`).
    5. Decrementing flight seats and stay inventory; inserting `rental_unit_occupancy`.
    6. Generating references via `BookingReferenceGenerator.java`:
       - `DT-` + 6 alphanumeric chars for master booking reference.
       - `FL-` + 6 alphanumeric chars for airfare reference.
       - `HT-` + 6 alphanumeric chars for stay reference.
       - `RC-` + 6 alphanumeric chars for rental reference.
    7. Persisting immutable snapshot rows into `detour_booking`, `detour_booking_airfare_snapshot`, `detour_booking_stay_snapshot`, `detour_booking_stay_night_snapshot`, `detour_booking_rental_snapshot`.
    8. Incrementing `detour_trip.version`.
- **Response Codes and Body:**
  - `201 Created` for a newly executed reservation.
  - `200 OK` for an idempotent replay with identical `idempotencyKey`.
  - Response body matches `BookingResponse.java:8-24`:
    - `id`: UUID (public identifier).
    - `tripId`: UUID.
    - `plannedItineraryId`: UUID.
    - `bookingReference`: String (`DT-XXXXXX`).
    - `status`: `"ACTIVE"`.
    - `grandTotalCents`: long.
    - `idempotencyKey`: String.
    - `bookedAt`: OffsetDateTime.
    - `canceledAt`: OffsetDateTime (null for active).
    - `airfareReference`: String (`FL-XXXXXX` or null).
    - `stayReference`: String (`HT-XXXXXX` or null).
    - `rentalReference`: String (`RC-XXXXXX` or null).
    - `selections`: `DraftSelectionResponse` (airfare, stay, rental snapshots).
    - `tally`: `ItineraryTallyResponse` (canonical subtotals, grand total, budget position).
- **Failure Responses (HTTP 409 and 400):**
  - `INVENTORY_CONFLICT` (HTTP 409): Thrown when flight seats, nightly stay units, or rental interval are exhausted. Returns JSON error envelope:
    ```json
    {
      "code": "INVENTORY_CONFLICT",
      "message": "One or more selected components are unavailable.",
      "fields": {
        "airfare": "Selected flight does not have enough available seats for party size.",
        "stay": "Selected accommodation is sold out for one or more requested nights.",
        "rental": "Selected rental vehicle is no longer available for the requested interval."
      }
    }
    ```
  - `ALREADY_BOOKED` (HTTP 409): Thrown when trip already has an active booking:
    ```json
    { "code": "ALREADY_BOOKED", "message": "This trip already has an active booking.", "fields": {} }
    ```
  - `VERSION_CONFLICT` (HTTP 409): Thrown when `expectedVersion` mismatches `trip.version`:
    ```json
    { "code": "VERSION_CONFLICT", "message": "The trip was modified by another operation. Please refresh and try again.", "fields": {} }
    ```
  - `TRIP_EXPIRED` (HTTP 400): Thrown if departure date has passed:
    ```json
    { "code": "TRIP_EXPIRED", "message": "Cannot book an expired trip.", "fields": {} }
    ```
  - `NO_RESERVABLE_COMPONENTS` (HTTP 400): Thrown if planned itinerary has no selections.

### 2. Active Booking Inspection Endpoints

- `GET /api/trips/{tripId}/bookings/active` (`TripController.java:265-270`):
  - Returns `200 OK` with `BookingResponse` if an active booking exists for the trip.
  - Throws `ApiException(404, "RESOURCE_NOT_FOUND")` if no active booking exists.
- `GET /api/trips/{tripId}/bookings` (`TripController.java:272-277`):
  - Returns `200 OK` with `List<BookingResponse>` representing booking history ordered by `created_at DESC` (empty list if no bookings exist).

### 3. Frontend API Layer (`frontend/src/api/tripsApi.ts`)

- `tripsApi.ts` currently defines types for `TripResponse`, `AlternativeResponse`, `DraftSelectionResponse`, and `ItineraryTallyResponse`.
- It currently has **no** definitions for `BookingResponse` or `CreateBookingRequest`.
- It exposes **no** methods for:
  - `createBooking(tripId, payload)`
  - `getActiveBooking(tripId)`
  - `getBookingHistory(tripId)`
- Network and API errors are handled by `IdentityApiError` in `frontend/src/api/identityApi.ts:11-21`, parsing HTTP status, `code`, `fields`, and `apiMessage`.

### 4. Booking Review View (`frontend/src/components/BookingReviewView.tsx`)

- Renders trip parameters overview (`trip.destinationName`, dates, travelers and ages).
- Renders component snapshots for Airfare, Stay, and Rental Car with fallback "No ... selected" blocks.
- Renders authoritative tally rows and budget position badge (`Within Budget` or `Over Budget`).
- Renders mandatory simulated booking disclosure callout (`role="note"`, lines 257–266).
- Renders a disabled "Confirm Booking" button in lines 269–281:
  ```tsx
  <div className="booking-confirm-section">
    <button
      type="button"
      className="primary-button confirm-booking-btn"
      disabled={true}
      aria-disabled="true"
    >
      Confirm Booking
    </button>
    <p className="confirm-booking-note hint">
      Ready for Phase 6 simulated booking implementation.
    </p>
  </div>
  ```
- Props currently accepted:
  ```typescript
  export type BookingReviewViewProps = {
    trip: TripResponse;
    alternative: AlternativeResponse;
    returnTarget: 'workspace' | 'compare';
    onBack: () => void;
  };
  ```
- Currently has no pending state, no error alert region for HTTP 409 responses, and no callback for booking success.

### 5. Trip Workspace (`frontend/src/components/TripWorkspace.tsx`)

- Manages view switching via `workspaceView`: `'workspace' | 'compare' | 'booking-review'`.
- Does not currently maintain `activeBooking` state.
- When rendering Planned alternatives via `AlternativeCard.tsx`:
  - Passes `onSelectForBookingReview` unconditionally for all Planned alternatives (`TripWorkspace.tsx:1357-1372`).
  - Does not know if an alternative is `BOOKED`.
  - Does not disable "Select for Booking Review" on other alternatives when an active booking exists.
- Does not render an **Active Booking** summary banner at the top of the workspace.
- In `ItineraryComparisonView.tsx`:
  - `onSelectForBookingReview` is enabled for all displayed Planned alternatives (`ItineraryComparisonView.tsx:157-164, 314-321`), with no check for an existing active booking.

### 6. Profile and Trip List Projections

- `frontend/src/components/ProfileScreen.tsx` receives `upcoming` and `past` arrays of `TripProfileSummary`.
- `frontend/src/components/TripListSection.tsx:33-103` renders `TripCard`:
  - Displays `badge-upcoming` or `badge-past`.
  - Displays count pills: `trip.draftCount`, `trip.plannedCount`, `trip.expiredAlternativeCount`, and `{trip.bookedCount > 0 && <span className="count-pill badge-booked">{trip.bookedCount} Booked</span>}` (`TripListSection.tsx:64-66`).
  - Does **not** display a `BOOKED` badge on the trip header.
  - Does **not** display the primary booking reference (`DT-XXXXXX`).
- On the backend, `TripProfileSummary.java:7-22` currently consists of:
  ```java
  public record TripProfileSummary(
          UUID id,
          String destinationKey,
          String destinationName,
          LocalDate startDate,
          LocalDate endDate,
          String label,
          long version,
          String temporalStatus,
          int draftCount,
          int plannedCount,
          int expiredAlternativeCount,
          int bookedCount,
          boolean hasBookingHistory,
          List<AlternativeProfileSummary> alternatives) {
  }
  ```
  It does not include a `primaryBookingReference` field.

## Key Components

- `src/main/java/app/detour/trip/TripController.java:251-277` — REST controller handling `POST /api/trips/{tripId}/bookings`, `GET /api/trips/{tripId}/bookings/active`, and `GET /api/trips/{tripId}/bookings`.
- `src/main/java/app/detour/booking/BookingResponse.java:8-24` — Authoritative record returned by all booking endpoints, containing booking references, tally, selections, and status.
- `src/main/java/app/detour/booking/BookingTransactionExecutor.java:48-193` — Core reservation transaction verifying expiration, version concurrency, and single-active-booking constraint; executes deterministic inventory locks and decrements.
- `src/main/java/app/detour/booking/BookingReferenceGenerator.java:12-26` — Fictional code generator producing `DT-`, `FL-`, `HT-`, and `RC-` formatted strings.
- `src/main/java/app/detour/trip/TripProfileSummary.java:7-22` and `AlternativeProfileSummary.java:5-6` — DTOs for the profile summary projection.
- `src/main/java/app/detour/trip/TripService.java:112-169` — Generates upcoming and past trip summaries; computes `bookedCount` and `hasBookingHistory`.
- `src/main/java/app/detour/trip/JdbcTripRepository.java:255-265` — Queries `detour_booking` for `hasBookingHistory` and `activeBookingCount`.
- `frontend/src/api/tripsApi.ts:1-669` — TypeScript API client with types and HTTP request wrappers; currently missing booking endpoints.
- `frontend/src/api/identityApi.ts:11-57` — HTTP request infrastructure and `IdentityApiError` error envelope parser.
- `frontend/src/components/BookingReviewView.tsx:268-281` — Booking review component with staged disabled confirm button.
- `frontend/src/components/TripWorkspace.tsx:60-189, 937-974` — Main trip workspace managing view modes (`workspace`, `compare`, `booking-review`), alternative cards, and promotion.
- `frontend/src/components/AlternativeCard.tsx:40-74, 155-166` — Card rendering draft and planned alternatives; holds the "Select for Booking Review" action button.
- `frontend/src/components/ItineraryComparisonView.tsx:157-165, 314-322` — Side-by-side comparison view containing "Select for Booking Review" actions for compared alternatives.
- `frontend/src/components/ProfileScreen.tsx:84-108, 165-196` — Top-level profile screen managing trip list and workspace transitions.
- `frontend/src/components/TripListSection.tsx:33-103` — Profile trip list rendering `TripCard` under Upcoming and Past sections.
- `frontend/src/style.css:34-44, 244-270` — CSS styles containing `.badge-booked` (line 40) and booking review layouts.

## Affected Areas

| Area | Current behavior and evidence |
| --- | --- |
| `tripsApi.ts` Client & DTOs | Currently lacks `BookingResponse`, `CreateBookingRequest` types and `createBooking`, `getActiveBooking`, `getBookingHistory` client methods (`frontend/src/api/tripsApi.ts:602-668`). |
| `BookingReviewView.tsx` Action & Pending State | "Confirm Booking" is permanently disabled (`disabled={true}`, line 273). No client UUID generation, no submit call, no "Reserving inventory..." loading announcement, and no 409 conflict error summary. |
| `BookingConfirmationView.tsx` | Component does not exist. Must be created to render master `DT-` reference, component locator codes (`FL-`, `HT-`, `RC-`), traveler summary, itemized costs, fictional disclosure, `aria-live` announcement, copy actions, and return navigations. |
| `TripWorkspace.tsx` Active Booking Banner & State | Does not query active booking on mount/update. Does not display the Active Booking summary banner (reference, date, total, "View Booking Details"). Does not register `'booking-confirmation'` view mode. |
| `AlternativeCard.tsx` Single-Booking Constraint | Does not distinguish booked Planned alternatives from unbooked ones. Does not display `BOOKED` badge. Does not disable "Select for Booking Review" when an active booking exists (`AlternativeCard.tsx:155-166`). |
| `ItineraryComparisonView.tsx` Booking Constraint | "Select for Booking Review" button is active on all alternatives without checking whether the trip already has an active booking (`ItineraryComparisonView.tsx:160, 317`). |
| `ProfileScreen.tsx` & `TripListSection.tsx` | `TripCard` does not render `BOOKED` badge on trip header. Does not display the primary booking reference (`TripListSection.tsx:45-68`). |
| Backend `TripProfileSummary` & Repository | `TripProfileSummary.java:7-22` lacks `primaryBookingReference`. `TripRepository.java` has `activeBookingCount` but does not query the active booking reference for the summary. |
| Stylesheet (`frontend/src/style.css`) | Contains `.badge-booked` (line 40). Needs styling for the Active Booking workspace banner, confirmation screen layouts, code display with copy buttons, and conflict alert banners. |

## Existing Tests and Fixtures

- `frontend/src/ItineraryComparisonAndBookingReview.test.tsx`:
  - Lines 454–513: Tests transition from comparison matrix to booking review screen; explicitly checks that "Confirm Booking" is disabled and contains the staging note.
  - Lines 515–543: Tests transition from standalone planned alternative card to booking review screen and back.
  - Lines 545–574: Tests WCAG AA contrast classes and ARIA table semantics.
- `frontend/src/api/tripsApi.test.ts`:
  - Lines 22–370: Mocks `fetch` to verify payload structure, headers (`X-XSRF-TOKEN`), HTTP methods, and query strings for all API client methods.
- `frontend/src/DraftPromotion.test.tsx`:
  - Lines 238–300: Tests keyboard accessibility, focus trapping, Escape dismissal, and `aria-live` announcements.
- `src/test/java/app/detour/booking/BookingApiIntegrationTest.java`:
  - Lines 58–98: `booksValidPlannedItineraryAndDecrementsAllComponents` verifies `201 Created`, references pattern matching (`^DT-[A-Z0-9]{6}$`, `^FL-[A-Z0-9]{6}$`, `^HT-[A-Z0-9]{6}$`), and seat/room inventory decrements.
  - Lines 133–170: `idempotentReplayReturnsExistingBookingWithoutDoubleDecrement` verifies `200 OK` on repeated idempotency key.
  - Lines 227–263: `rejectsSecondActiveBookingOnTrip` verifies HTTP 409 `ALREADY_BOOKED`.
  - Lines 305–459: Verifies HTTP 409 `INVENTORY_CONFLICT` and full transactional rollback when airfare, stay, or rental inventory is exhausted.
  - Lines 496–548: `retrievesActiveBookingAndHistoryAndUpdatesProfile` verifies `GET /api/trips/{tripId}/bookings/active` returns 200 and updates `bookedCount: 1` on profile.

## Dependencies and Operational Constraints

1. **Database-Enforced Single Active Booking:**
   `detour_booking` enforces `CONSTRAINT uq_detour_booking_active_trip UNIQUE (active_trip_id)` where `active_trip_id` is a generated column (`CASE WHEN status = 'ACTIVE' THEN trip_id ELSE NULL END`). The database strictly rejects a second active booking with `DuplicateKeyException` (mapped to 409 `ALREADY_BOOKED`).
2. **Client-Side Idempotency Key:**
   The client must generate a unique UUID per booking submission attempt. Repeating the submission with the same idempotency key safely returns `200 OK` with the existing booking without double-decrementing inventory.
3. **Optimistic Concurrency Control:**
   Booking requires passing `expectedVersion` equal to `detour_trip.version`. Successful booking increments the version by 1. A stale version returns HTTP 409 `VERSION_CONFLICT`.
4. **Purely Fictional Simulated Inventory:**
   No credit cards, live payment gateways, external airline GDSs, or live supplier APIs are contacted. Clear disclosure copy is mandatory on both review and confirmation screens.
5. **Screen Reader and Accessibility Standards:**
   - Confirmation must announce the booking reference via `aria-live="polite"`.
   - Error summaries on conflict must use `role="alert"` and focus management.
   - Confirmation codes require accessible labels and copy indicators.
   - Contrast must meet WCAG AA standards.
6. **No Scheduled Background Jobs:**
   Upcoming vs Past grouping is derived dynamically from trip dates compared to the current clock date in `America/Los_Angeles`.

## Historical Context

- **Phase 0 & 1:** Established core application shell, CSRF protection, and session authentication.
- **Phase 3:** Introduced `detour_trip`, draft alternatives, and immutable planned snapshots (`detour_planned_*_snapshot`).
- **Phase 5 (P05-T04):** Delivered multi-alternative comparison and `BookingReviewView.tsx`. The "Confirm Booking" button was intentionally staged as disabled to prevent premature submissions prior to the reservation engine.
- **Phase 6 (P06-T01):** Delivered the atomic inventory reservation engine, transaction executor, database schema (`V17__create_booking_schema.sql`), and API endpoints (`/api/trips/{tripId}/bookings*`).

## Open Questions

1. **Profile Summary Booking Reference Delivery:**
   To show the primary booking reference on the trip card in `TripListSection.tsx`, should `TripProfileSummary` on the backend be extended to include `primaryBookingReference` (populated from `detour_booking`), or should the frontend fetch active bookings individually?
   *(For planning to resolve: adding `primaryBookingReference` to `TripProfileSummary` avoids N+1 client network calls).*
2. **AlternativeCard Presentation for the Booked Alternative:**
   When an active booking exists, should the booked Planned alternative replace "Select for Booking Review" with a "View Booking Details" action, or simply disable the review action?
   *(For planning to evaluate based on usability).*
3. **Itinerary Comparison Entry Point during Active Booking:**
   When an active booking exists, travelers can still compare Planned alternatives, but can they trigger booking review from comparison?
   *(The ticket states only one active booking is permitted and "Select for Booking Review" must be disabled with explanatory copy).*

---

## Step Report: 1_research_codebase
STATUS: complete
ARTIFACTS:
  - ai/thoughts/research/2026-09-22-p06-t02-booking-confirmation-and-active-itinerary-experience.md
SUMMARY: Researched the frontend and backend codebase relevant to booking confirmation, active itinerary presentation, and profile projections. Mapped out existing endpoints, data models, UI components, tests, and accessibility requirements. Documented key components, affected areas, and open questions for planning.
DECISIONS:
  - none
DEVELOPER QUESTION: none
EVIDENCE: none
RECOMMENDATION: none
NEXT: Proceed to Step 2 (create plan) and Step 3 (testing plan) using execution profile full.
