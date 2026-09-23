# P06-T02 — Deliver Booking Review Confirmation and Active Itinerary Experience

## Outcome

Travelers can explicitly trigger simulated booking from the Booking Review screen, generate an idempotency key, view an in-flight reservation state, receive a dedicated Booking Confirmation view displaying fictional reference numbers and component details, and observe the active `BOOKED` status reflected across the Trip Workspace and Profile without allowing a second active booking.

## Requirements

- **Interactive booking submission from `BookingReviewView.tsx`:**
  - Activate the currently disabled "Confirm Booking" button in `BookingReviewView.tsx`.
  - When clicked:
    - Generate a client-side UUID as the `idempotencyKey`.
    - Submit `POST /api/trips/{tripId}/bookings` with `plannedItineraryId`, `expectedVersion`, and `idempotencyKey`.
    - Display an accessible in-flight pending state (disabled button, "Reserving inventory..." loading announcement).
    - If the server returns a 409 Conflict (e.g. inventory sold out or already booked), display an accessible error summary detailing the unavailable components with a button to return to the workspace or alternative selection.
- **Dedicated Booking Confirmation view (`BookingConfirmationView.tsx`):**
  - Upon successful booking response, render the Booking Confirmation screen displaying:
    - Prominent success banner with the primary fictional booking reference (e.g. `Booking Reference: DT-K8M2P4`).
    - Component confirmation reference codes:
      - Airfare confirmation code (e.g. `Airline Record Locator: W3X8PL`).
      - Stay confirmation code (e.g. `Accommodation Confirmation: HT-4M9Q2P`).
      - Rental car confirmation code (e.g. `Rental Confirmation: RC-7T1N5V`).
      - Omit reference rows for unselected optional components.
    - Summary of travelers (count and ages), destination, and travel dates.
    - Itemized breakdown of booked component costs and authoritative grand total.
    - Clear simulated booking reminder: *"This was a simulated reservation using fictional inventory. No credit card was charged and no live supplier booking was made."*
    - Actions: "View in Trip Workspace" (navigates back to the Trip Workspace) and "View All Trips" (navigates to Profile).
- **Active booking presentation in `TripWorkspace.tsx`:**
  - When a trip has an active booking:
    - Display an **Active Booking** summary banner at the top of the workspace showing the booking reference, booked date, grand total, and a "View Booking Details" action.
    - Mark the source Planned alternative with a distinct, accessible `BOOKED` status badge.
    - Enforce the single-active-booking constraint: disable "Select for Booking Review" on all other Planned alternatives with an explanatory message: *"This trip already has an active booking. Only one active booking is permitted per trip."*
    - Allow travelers to continue creating new Drafts or duplicating alternatives, but prevent entering booking review or confirming a second booking.
- **Profile and Trip List presentation (`ProfileScreen.tsx`, `TripListSection.tsx`):**
  - Display the `BOOKED` badge on trip cards under Upcoming or Past trips.
  - Show the primary booking reference on the trip summary card.
  - Correctly derive Upcoming versus Past trip grouping based on trip dates without background scheduled jobs or mutable lifecycle state.
- **Accessibility and responsive design:**
  - The confirmation screen announces the successful booking reference to assistive technologies via an `aria-live="polite"` region.
  - All confirmation codes have associated copy-to-clipboard or accessible screen-reader labels.
  - Fully responsive across desktop (multi-column summary) and mobile viewports (stacked cards).

## Acceptance criteria

- [x] Clicking "Confirm Booking" on `BookingReviewView` sends `POST /api/trips/{tripId}/bookings` with an idempotency key and displays a pending state.
- [x] On success, the application renders the Booking Confirmation screen showing the master `DT-` booking reference, individual component confirmation codes, itemized pricing, and the simulated booking disclosure.
- [x] On inventory conflict (HTTP 409), the view displays an accessible error summary explaining why the reservation failed and keeps the traveler's draft/planned selections intact.
- [x] In the Trip Workspace, an active booking displays a prominent Active Booking card and badges the corresponding itinerary as `BOOKED`.
- [x] When an active booking exists, "Select for Booking Review" is disabled on all other Planned alternatives with clear explanatory copy.
- [x] On the Profile screen, booked trips display the `BOOKED` status badge and primary booking reference under Upcoming or Past sections.
- [x] All confirmation and status views meet WCAG AA contrast standards, keyboard navigation requirements, and screen-reader announcements verified in Vitest component tests.

## Context

- **Phase/work packages:** Phase 6 — Simulated Booking and Cancellation; work package 6.1 (Booking review), work package 6.3 (Booking record and confirmations).
- **Authoritative sources:** [`../phases/README.md`](../phases/README.md), [`../phases/CONTINUATION.md`](../phases/CONTINUATION.md), and [`../phases/phase-6-booking-and-cancellation.md`](../phases/phase-6-booking-and-cancellation.md).
- **Hard dependencies:** P06-T01 must be complete.
- **Downstream dependencies:** P06-T03 and P06-T04 attach fee-free cancellation controls to the active booking experience delivered here.
- **Scope exclusions:** Real payment gateways, printable PDF tickets, calendar (.ics) exports, and Version 2 Events.

## Execution profile

- **Recommended:** Full 5-Step Pipeline
- **Confidence:** high
- **Rationale:** Connects user-facing booking submission, in-flight state handling, dedicated confirmation views, workspace active-booking constraints, and profile badge projections across multiple interactive views.
- **Reassessment triggers:** none.
