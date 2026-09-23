---
date: 2026-09-23
repository: loomspan-travel-demo
branch: main
commit: 340f1edcf2bdff6f79f37876cdae544a72980283
ticket: ai/thoughts/tickets/2026-09-22-p06-t04-fee-free-cancellation-and-post-cancellation-experience.md
tags: [cancellation, booking, frontend, modals, accessibility, trip-workspace, profile, vitest]
---

# Fee-Free Cancellation and Post-Cancellation Triage Experience Research

## Research Question

How does the current DeTour codebase implement trip deletion, booking management, itinerary lifecycle state, and modal dialogs, and what existing components, endpoints, contracts, and test fixtures are in place to support the P06-T04 fee-free cancellation and post-cancellation triage experience?

## Summary

The backend foundation for transactional cancellation was completed in P06-T03:
- Endpoints `POST /api/trips/{tripId}/bookings/{bookingId}/cancel` and `POST /api/trips/{tripId}/cancel` are operational, verified with concurrency and atomic inventory restoration tests (`BookingCancellationIntegrationTest.java`).
- `detour_booking` preserves immutable cancellation history with `status = 'CANCELED'` and `canceled_at`, leaving all confirmation references intact.
- `detour_trip` supports `status = 'CANCELED'`, which rejects all draft/alternative additions, mutations, deletions, and promotions with `HTTP 409 TRIP_CANCELED`, while allowing `POST /api/trips/{tripId}/duplicate`.
- `TripRepository.hasBookingHistory(long tripId)` authoritatively gates trip deletion, rejecting `DELETE /api/trips/{tripId}` with `HTTP 409 CANNOT_DELETE_BOOKED_TRIP`.
- In `tripsApi.ts`, client functions `cancelBooking`, `cancelTrip`, `getBookingHistory`, and `getActiveBooking` are already implemented.

On the frontend, the UI currently lacks the user-facing cancellation and post-cancellation workflows:
- Active bookings in `TripWorkspace.tsx` display a "View Booking Details" button but offer no "Cancel Booking" action.
- When `trip.hasBookingHistory` is true, `TripWorkspace.tsx` and `TripListSection.tsx` render a disabled button labeled `"Has booking history"` instead of offering `"Cancel Trip"`.
- Dedicated confirmation modals `CancelBookingModal.tsx` and `CancelTripModal.tsx`, as well as `PostCancellationTriageModal.tsx`, do not yet exist.
- When a trip is canceled, `TripWorkspace.tsx` does not render a Canceled Trip banner or header badge, does not suppress mutation controls on drafts/alternatives, and lacks a collapsible Booking History section.
- `ProfileScreen.tsx` and `TripListSection.tsx` do not display a `CANCELED` badge on canceled trips and do not suppress cancel actions for Expired or Past trips.

## Repository State

- **Date:** 2026-09-23
- **Branch:** `main`
- **Commit:** `340f1edcf2bdff6f79f37876cdae544a72980283`
- **Working Tree:** Clean (`git status` reports nothing to commit).
- **Backend Build & Tests:** Passing (`mvnw.cmd test -Dtest=BookingCancellationIntegrationTest` runs 15/15 passed).
- **Frontend Build & Tests:** Passing (8 test files, 88 tests passing via Vitest).

## Current Behavior and Data Flow

### 1. Booking Cancellation Endpoint and Lifecycle (Backend)
- **Path:** `POST /api/trips/{tripId}/bookings/{bookingId}/cancel` handled by `TripController.java:279-291`, `BookingService.java:95-104`, and `BookingTransactionExecutor.java:206-253`.
- **Validation:**
  - Trip ownership verified via authenticated user ID (`ownedTrip`).
  - Rejects with `HTTP 400 TRIP_EXPIRED` if current time is on or after departure date midnight in `America/Los_Angeles` (`ClockConfiguration.PDX_ZONE`).
  - Rejects with `HTTP 409 TRIP_CANCELED` if the trip itself is already canceled.
  - Rejects with `HTTP 409 VERSION_CONFLICT` if `request.expectedVersion` does not match `detour_trip.version`.
  - Rejects with `HTTP 409 BOOKING_NOT_ACTIVE` if booking status is not `ACTIVE`.
- **Atomic Execution:**
  - Locks and increments flight seats (`available_seats + traveler_count`) for outbound and return legs.
  - Locks and increments accommodation inventory (`available_inventory + unit_count`) for all reserved nights.
  - Releases rental car occupancy (`occupancy_status = 'RELEASED'`).
  - Updates `detour_booking`: `status = 'CANCELED'`, `canceled_at = CURRENT_TIMESTAMP`. Preserves confirmation codes and totals.
  - Advances `detour_trip.version`.
  - Leaves `detour_trip.status = 'ACTIVE'`.
- **Response:** `HTTP 200 OK` returning `TripResponse` with updated version and `booking.status = 'CANCELED'`.

### 2. Trip Cancellation Endpoint and Lifecycle (Backend)
- **Path:** `POST /api/trips/{tripId}/cancel` handled by `TripController.java:293-303`, `BookingService.java:106-109`, and `BookingTransactionExecutor.java:255-301`.
- **Validation:**
  - Trip ownership verified.
  - Rejects with `HTTP 400 TRIP_EXPIRED` if trip departure date has arrived/passed.
  - Rejects with `HTTP 409 TRIP_ALREADY_CANCELED` if `trip.status` is already `'CANCELED'`.
  - Rejects with `HTTP 409 VERSION_CONFLICT` if `expectedVersion` does not match.
  - Rejects with `HTTP 400 NO_BOOKING_HISTORY` if trip has never had any bookings (delete trip must be used instead).
- **Atomic Execution:**
  - If an active booking exists: atomically releases all flight, stay, and rental inventory, sets booking status to `'CANCELED'`, and sets `detour_trip.status = 'CANCELED'`.
  - If only historical canceled bookings exist: sets `detour_trip.status = 'CANCELED'` without adjusting inventory.
  - Advances `detour_trip.version`.
- **Response:** `HTTP 200 OK` returning `TripResponse` with `status = 'CANCELED'` and updated version.
- **Subsequent Operation Restrictions on Canceled Trips:**
  - `replaceSharedDetails`, `createDraft`, `duplicateDraft`, `deleteDraft`, `promoteDraft`, `duplicateAlternative`, `deleteAlternative`, `selectDraftAirfare`, `removeDraftAirfare`, `selectDraftStay`, `removeDraftStay`, `selectDraftRental`, `removeDraftRental`, and `createBooking` all reject with `HTTP 409 TRIP_CANCELED`.
  - `duplicateTrip` (`POST /api/trips/{tripId}/duplicate`) is permitted and succeeds.

### 3. Active Booking Presentation in `TripWorkspace.tsx`
- In `TripWorkspace.tsx:1100-1133`, the active booking is displayed in `<section className="active-booking-banner card">`.
- Header shows badges: `BOOKED`, `Active Reservation`.
- Displays `Booking Reference`, `Booked` date, and `Grand Total`.
- Action button: `<button className="primary-button view-details-action-btn">View Booking Details</button>` which sets `workspaceView('booking-confirmation')`.
- There is **no** "Cancel Booking" button on this card.

### 4. Trip Deletion vs. Booking History Gating
- In `TripWorkspace.tsx:1037-1046`:
  ```tsx
  <button
    type="button"
    className="text-button delete-button"
    onClick={promptDeleteTrip}
    disabled={hasBookingHistory}
    title={hasBookingHistory ? 'Trips with booking history cannot be deleted' : undefined}
    aria-label={`Delete trip ${trip.label}`}
  >
    {hasBookingHistory ? 'Has booking history' : 'Delete trip'}
  </button>
  ```
- In `TripListSection.tsx:90-99`:
  Identical disabled button behavior when `trip.hasBookingHistory` is true.
- When `hasBookingHistory` is false, clicking triggers `ConfirmDeleteModal.tsx`, which prompts with alternative counts and calls `tripsApi.deleteTrip`.
- When `hasBookingHistory` is true, neither component currently offers a "Cancel Trip" action.

### 5. Existing Modal Patterns
Existing modals (`ConfirmDeleteModal.tsx`, `ConfirmRemoveModal.tsx`, `BudgetOverageModal.tsx`, `TripRevisionModal.tsx`, `TripCreateModal.tsx`):
- Wrapped in `<div className="modal-backdrop" onClick={...}>`.
- Container: `<div className="modal card" role="dialog" aria-modal="true" aria-labelledby="...">`.
- Focus management:
  - `previousActiveElement` ref saves `document.activeElement` on open, restores focus on close.
  - Initial focus moves to the primary cancel button or first input.
  - Tab / Shift+Tab keyboard focus trap loops within focusable elements.
  - Escape key dismisses the dialog.
- Destructive buttons currently use `.primary.delete-confirm-button` or `.delete-button` styling. Ticket specifies `.danger-button`.

## Key Components

- `frontend/src/api/tripsApi.ts:723-728` — provides `cancelBooking(tripId, bookingId, { expectedVersion })`, `cancelTrip(tripId, { expectedVersion })`, `getBookingHistory(tripId)`, and `getActiveBooking(tripId)`.
- `frontend/src/api/tripsApi.ts:393-412` — defines `TripResponse`, which includes `status?: string`, `version: number`, `booking?: BookingResponse | null`, and alternatives.
- `frontend/src/api/tripsApi.ts:11-28` — defines `TripProfileSummary`, which includes `hasBookingHistory: boolean`, `status?: string`, `bookedCount: number`, and `temporalStatus: 'UPCOMING' | 'PAST'`.
- `frontend/src/components/TripWorkspace.tsx:68-1558` — primary trip workspace managing active draft, progressive builder, shared details form, alternative cards, active booking banner, and comparison/review views.
- `frontend/src/components/TripWorkspace.tsx:1100-1133` — active booking banner where "Cancel Booking" action must be integrated.
- `frontend/src/components/TripWorkspace.tsx:1037-1046` — header navigation actions where "Cancel Trip" must replace "Delete Trip" when `hasBookingHistory` is true.
- `frontend/src/components/AlternativeCard.tsx:1-196` — renders draft and planned alternatives; controls must become read-only when trip is canceled.
- `frontend/src/components/ConfirmDeleteModal.tsx:1-163` — existing delete confirmation modal for draft, planned, and trip deletion.
- `frontend/src/components/TripRevisionModal.tsx:1-277` — existing trip duplication modal called from "Revise Trip"; can be reused or targeted for "Duplicate into a new Trip".
- `frontend/src/components/TripListSection.tsx:33-111` — renders `TripCard`s in profile lists; requires `CANCELED` badge, "Cancel Trip" action replacement, and suppression of cancel actions for past/expired trips.
- `frontend/src/components/ProfileScreen.tsx:23-294` — top-level profile container managing trip list, trip selection into workspace, and delete modal.
- `frontend/src/style.css` — styling rules for shell, cards, modals, badges, slots, and responsive layouts.

## Affected Areas

| Area | Current behavior and evidence |
| --- | --- |
| **Active Booking card (`TripWorkspace.tsx`)** | Only renders "View Booking Details" (`lines 1114-1120`). Does not render "Cancel Booking" action. |
| **Cancel Booking modal (`CancelBookingModal.tsx`)** | Does not exist. Needs accessible dialog explaining fee-free cancellation, inventory release, booking history retention, and active trip preservation; calls `tripsApi.cancelBooking`. |
| **Post-cancellation triage modal (`PostCancellationTriageModal.tsx`)** | Does not exist. Needs accessible dialog triggered immediately upon booking cancellation offering: 1) "Use a saved alternative" (duplicates planned into draft via `tripsApi.duplicateAlternative`), 2) "Create a new Draft" (`tripsApi.createDraft`), and 3) "Done for now" (dismisses modal). |
| **Trip deletion gating (`TripWorkspace.tsx`, `TripListSection.tsx`)** | When `hasBookingHistory` is true, displays disabled "Has booking history" button (`TripWorkspace.tsx:1041`, `TripListSection.tsx:94`). Must replace with "Cancel Trip" action. |
| **Cancel Trip modal (`CancelTripModal.tsx`)** | Does not exist. Needs accessible dialog explaining active reservation release without fees, permanent retention of booking history, read-only alternative locking, and duplication capability; calls `tripsApi.cancelTrip`. |
| **Canceled Trip presentation (`TripWorkspace.tsx`)** | No distinct header badge or banner for `trip.status === 'CANCELED'`. Builder slots, draft creation, and alternative editing are not disabled/hidden. No primary "Duplicate into a new Trip" button. No collapsible Booking History section. |
| **Alternative card read-only state (`AlternativeCard.tsx`)** | Does not check if the parent trip is canceled. Actions (promote, duplicate, delete, select for booking review) remain enabled unless trip is expired. |
| **Profile and Trip lists (`TripListSection.tsx`, `ProfileScreen.tsx`)** | Does not display `CANCELED` badge on canceled trips. Trips with booking history show disabled delete button without cancel action. Expired/past trips do not explicitly suppress cancel actions. |
| **Styles (`style.css`)** | Missing `.danger-button`, `.badge-canceled`, `.canceled-trip-banner`, `.booking-history-section`, and triage modal layout styling. |
| **Client API Tests (`api/tripsApi.test.ts`)** | Tests exist for `createBooking`, `getActiveBooking`, and `getBookingHistory`, but not yet for `cancelBooking` or `cancelTrip`. |

## Existing Tests and Fixtures

### Backend Tests
- `src/test/java/app/detour/booking/BookingCancellationIntegrationTest.java`:
  - `cancelingActiveBookingRestoresInventoryAndAdvancesTripVersion` (lines 59-112)
  - `rebookingPossibleAfterBookingCancellation` (lines 114-142)
  - `cancelingBookingOnExpiredTripRejectedWithHttp400` (lines 144-156)
  - `cancelingAlreadyCanceledBookingRejectedWithHttp409` (lines 158-178)
  - `cancelingTripWithActiveBookingAtomicallyRestoresInventoryAndSetsTripCanceled` (lines 180-207)
  - `cancelingTripWithOnlyCanceledBookingsSetsTripCanceledWithoutInventoryAdjustment` (lines 209-230)
  - `cancelingTripWithoutBookingHistoryRejectedWithHttp400` (lines 232-250)
  - `cancelingExpiredTripRejectedWithHttp400` (lines 252-265)
  - `cancelingAlreadyCanceledTripRejectedWithHttp409` (lines 267-283)
  - `allMutationsOnCanceledTripRejectedWithHttp409` (lines 285-384)
  - `duplicateTripAllowedOnCanceledTrip` (lines 386-413)
  - `deletingActivelyBookedPlannedAlternativeRejectedWithHttp409` (lines 415-431)
  - `deletingUnbookedPlannedAlternativeSucceedsAndPreservesHistoricalCanceledBookings` (lines 433-471)
  - `deletingTripWithBookingHistoryPermanentlyRejectedWithHttp409` (lines 473-493)
  - `multiUserIsolationPreventsCancelingOtherUsersBookingsOrTrips` (lines 495-523)

### Frontend Tests
- `frontend/src/api/tripsApi.test.ts`: 13 tests covering identity, trip creation, revision, draft mutation, component selection/removal, readiness, booking creation, active booking, and booking history.
- `frontend/src/components/BookingConfirmationView.test.tsx`: 8 tests verifying confirmation display, reference codes, clipboard copy, tally breakdown, and simulated booking disclosure.
- `frontend/src/ItineraryComparisonAndBookingReview.test.tsx`: 12 tests verifying comparison matrix, mobile switcher, selection limits, and booking review transition.
- `frontend/src/DraftPromotion.test.tsx`: 9 tests verifying promotion button, readiness banner, issue jumping, overage modal, focus trap, Escape dismissal, and status announcements.
- `frontend/src/ProgressiveTripBuilder.test.tsx`: 14 tests verifying entry modes, slot search/select/remove, confirmation modals, concurrency conflicts, and defensive resets.
- `frontend/src/App.test.tsx`: 28 tests verifying registration, login/logout, password change, trip creation, autosave debouncing, trip revision modal, and validation.

## Dependencies and Operational Constraints

1. **Transactional and Inventory Invariants:**
   - Cancel Booking and Cancel Trip are atomic server operations. The frontend must pass `expectedVersion` matching the current trip version to guard against stale updates.
   - Restored inventory is immediately available on the catalog; re-booking or re-planning requires revalidating against fresh inventory.
2. **Read-only Canceled Trips:**
   - Once a trip is canceled (`status = 'CANCELED'`), the server rejects all draft/alternative mutations. The frontend must disable/hide all editing, promotion, and booking controls to prevent user errors.
   - The only permitted mutation on a canceled trip is `duplicateTrip` (`POST /api/trips/{tripId}/duplicate`).
3. **Expiration Timezone Rule:**
   - A trip expires at the start of its departure date in departure timezone `America/Los_Angeles` (`ClockConfiguration.PDX_ZONE`).
   - Expired and Past bookings/trips cannot be canceled (rejected with HTTP 400). Frontend must hide or disable cancel actions on expired and past trips.
4. **Permanent Trip Deletion Rule:**
   - Delete Trip is only permitted when `hasBookingHistory` is false.
   - If `hasBookingHistory` is true, Delete Trip is forbidden (server returns HTTP 409). Cancel Trip must be presented instead.
5. **Accessibility Standards:**
   - Accessible modal dialogs: `role="dialog"`, `aria-modal="true"`, `aria-labelledby`, focus trapping, Escape dismissal, and focus restoration to trigger elements.
   - Live announcements for async outcomes via `aria-live="polite"`.
   - Distinct destructive button styling (`danger-button`).

## Historical Context

- In Phase 6 planning (`ai/thoughts/phases/phase-6-booking-and-cancellation.md` and `CONTINUATION.md`), work packages 6.3 and 6.4 specified fee-free cancellation, atomic inventory restoration, immutable booking history, distinct deletion terminology (five distinct actions), post-cancellation triage, and duplicate-trip workflows.
- P06-T01 delivered the inventory reservation engine and booking creation transaction.
- P06-T02 delivered booking review, confirmation view, and active booking card display.
- P06-T03 delivered the backend cancellation engine (`cancelBooking`, `cancelTrip`), Flyway migration `V18__add_trip_status_and_cancellation_constraints.sql`, and concurrency verification.
- P06-T04 is the frontend delivery ticket for fee-free cancellation, confirmation dialogs, post-cancellation triage, canceled trip presentation, booking history display, and profile integration.

## Open Questions

These questions are architectural/design considerations for the planning stage (`2_create_plan.md`):

1. **Cancel Trip Trigger in `TripListSection.tsx` vs. `TripWorkspace.tsx`:**
   Should "Cancel Trip" in `TripListSection.tsx` open `CancelTripModal` directly from the profile screen, or should it navigate into the workspace where the user cancels, or both?
   *(Note: `TripListSection` currently has `onDeleteTrip(trip: TripProfileSummary)`; adding `onCancelTrip(trip: TripProfileSummary)` in `ProfileScreen` alongside `deleteTarget` fits existing modal patterns cleanly).*

2. **Source Planned Selection when Duplicating a Canceled Trip:**
   `TripRevisionModal` requires at least one planned alternative to duplicate (`sourcePlannedItineraryIds`). If a canceled trip has planned alternatives, `TripRevisionModal` can be reused. What should happen if a canceled trip has no planned alternatives (e.g. only drafts existed before booking)? The continuation guide notes: *"if it has no selected/available Planned snapshot, duplicate only its shared details into one component-empty Draft and explain that no alternative was copied."* Planning should decide how `TripRevisionModal` handles this case or whether a dedicated mode is needed.

3. **Booking History Fetching Strategy in `TripWorkspace.tsx`:**
   `tripsApi.getBookingHistory(trip.id)` fetches all historical bookings (`List<BookingResponse>`). Should `TripWorkspace` load this on mount when `hasBookingHistory` is true, or lazily when the user expands the Booking History collapsible section?

---

## Step Report: 1_research_codebase.md
STATUS: complete
ARTIFACTS:
  - ai/thoughts/research/2026-09-22-p06-t04-fee-free-cancellation-and-post-cancellation-experience.md
SUMMARY: Researched the complete codebase for P06-T04 fee-free cancellation and post-cancellation triage. Verified that P06-T03 backend endpoints, database constraints, and API client methods are fully operational. Identified all frontend components, modal patterns, accessibility requirements, and missing cancellation dialogs needed to deliver the ticket.
DECISIONS:
  - none
DEVELOPER QUESTION: none
EVIDENCE: none
RECOMMENDATION: none
NEXT: Proceed to Steps 2 and 3 (`2_create_plan.md` and `3_testing_plan.md`).
