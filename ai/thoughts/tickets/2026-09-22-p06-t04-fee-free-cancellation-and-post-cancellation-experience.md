# P06-T04 — Deliver Fee-Free Cancellation and Post-Cancellation Triage Experience

## Outcome

Travelers can initiate distinct, fee-free cancellation actions with clear confirmation dialogs, experience immediate post-cancellation triage (use a saved alternative, create a new draft, or finish), inspect immutable booking history on active and canceled trips, and duplicate canceled trips into fresh travel plans without ambiguous deletion terminology.

## Requirements

- **Distinct cancellation actions and confirmation modals:**
  - Clearly distinguish the five deletion and cancellation actions across the UI so travelers never face ambiguous "Cancel" prompts:
    1. **Delete Draft:** removes one unfinished draft alternative; no inventory effect.
    2. **Delete Planned itinerary:** removes one unbooked planned snapshot; no inventory effect.
    3. **Cancel Booking:** releases inventory, retains booking history, keeps the Trip active.
    4. **Delete Trip:** permanently deletes a trip that has never had any bookings.
    5. **Cancel Trip:** closes a trip with booking history, releases any active booking, makes alternatives read-only.
- **Cancel Booking flow (`CancelBookingModal.tsx`):**
  - Triggered from the "Cancel Booking" action on the Active Booking card in `TripWorkspace.tsx`.
  - Confirmation dialog explains:
    - Fictional reservation will be canceled without any cancellation fees.
    - All reserved seats, rooms, and rental vehicle will be released back to catalog inventory.
    - The booking reference and full cancellation record will be preserved in Trip history.
    - The Trip remains open and active for planning.
  - Submits `POST /api/trips/{tripId}/bookings/{bookingId}/cancel`.
  - On success, transitions immediately to the **Post-Cancellation Triage Dialog**.
- **Post-Cancellation Triage Dialog (`PostCancellationTriageModal.tsx`):**
  - Displays a success notice that the reservation was canceled fee-free.
  - Offers three explicit, mutually exclusive paths:
    1. **"Use a saved alternative":** displays a list of remaining Planned alternatives for this trip. Selecting one calls `duplicateAlternative` into a new Draft so it can be revalidated against current prices and availability before promotion and re-booking.
    2. **"Create a new Draft":** calls `createDraft` to start a fresh component-empty draft in this trip.
    3. **"Done for now":** dismisses the modal and remains in the Trip Workspace.
- **Trip Deletion versus Trip Cancellation gating:**
  - In `TripWorkspace.tsx` and `TripListSection.tsx`:
    - When `trip.hasBookingHistory` is `false`: show the "Delete Trip" action (triggering the existing `ConfirmDeleteModal` with draft/planned counts).
    - When `trip.hasBookingHistory` is `true`: replace "Delete Trip" with **"Cancel Trip"** (or disable Delete Trip with an explanatory notice: *"Trips with booking history cannot be deleted. You can cancel this trip instead."*).
- **Cancel Trip flow (`CancelTripModal.tsx`):**
  - Confirmation dialog explains:
    - Any active reservation will be released immediately without fees.
    - All booking history and confirmation records will be permanently retained.
    - All Draft and Planned alternatives in this trip will become read-only.
    - The trip can be duplicated into a new trip at any time.
  - Submits `POST /api/trips/{tripId}/cancel`.
  - On success, updates the workspace to the Canceled Trip state.
- **Canceled Trip presentation in `TripWorkspace.tsx`:**
  - Display a prominent **Canceled Trip** header badge and banner.
  - Mark all Draft and Planned alternatives as read-only: hide or disable "Edit", "Add component", "Promote to Planned", and "Select for Booking Review" controls.
  - Provide a primary **"Duplicate into a new Trip"** action that opens `TripRevisionModal` to duplicate shared details and select planned alternatives into a new trip.
  - Render a collapsible **Booking History** section displaying all historical bookings (reference, dates, component breakdown, and cancellation timestamp).
- **Profile and Trip List integration (`ProfileScreen.tsx`, `TripListSection.tsx`):**
  - Display a `CANCELED` badge on canceled trips.
  - Present canceled trips in a distinct "Canceled Trips" group or clearly badged within the profile lists.
  - For Expired and Past trips, hide or disable "Cancel Booking" and "Cancel Trip" actions, reflecting their immutable historical status.
- **Accessibility and interaction design:**
  - All cancellation and triage modals implement standard accessible modal mechanics (focus trap, Escape key dismissal, focus restoration).
  - Destructive confirmation buttons use distinct warning/danger styling (`class="danger-button"`).
  - Status updates are announced to assistive technologies via `aria-live="polite"`.

## Acceptance criteria

- [ ] "Cancel Booking" opens a confirmation modal detailing fee-free cancellation, inventory release, and retention of booking history.
- [ ] Confirming Cancel Booking releases inventory and opens the triage dialog offering "Use a saved alternative", "Create a new Draft", and "Done for now".
- [ ] Selecting "Use a saved alternative" duplicates the selected Planned alternative into a fresh Draft for revalidation.
- [ ] Trips with booking history present "Cancel Trip" rather than "Delete Trip"; "Delete Trip" is only offered when no booking has ever existed.
- [ ] Confirming Cancel Trip closes the trip, releases any active booking, and transitions the Trip Workspace to a read-only state.
- [ ] On a Canceled Trip, all alternatives are read-only, booking history is viewable, and a "Duplicate into a new Trip" action is provided.
- [ ] Expired and Past trips suppress cancel actions and display immutable status.
- [ ] Comprehensive Vitest test suite covers modal interactions, focus management, triage selections, and read-only canceled trip states.

## Context

- **Phase/work packages:** Phase 6 — Simulated Booking and Cancellation; work package 6.3 (Booking record and confirmations), work package 6.4 (Fee-free cancellation).
- **Authoritative sources:** [`../phases/README.md`](../phases/README.md), [`../phases/CONTINUATION.md`](../phases/CONTINUATION.md), and [`../phases/phase-6-booking-and-cancellation.md`](../phases/phase-6-booking-and-cancellation.md).
- **Hard dependencies:** P06-T01, P06-T02, and P06-T03 must be complete.
- **Downstream dependencies:** Phase 7 (Product Experience and Release) will polish end-to-end styling, responsive behavior, accessibility, and documentation.
- **Scope exclusions:** Rebooking onto different dates without cancellation, airline schedule changes, supplier disruptions, and Version 2 Events.

## Execution profile

- **Recommended:** Full 5-Step Pipeline
- **Confidence:** high
- **Rationale:** Delivers complex multi-branching modal dialogs, post-cancellation triage workflows, read-only state transitions, and integration across the Trip Workspace and Profile screens.
- **Reassessment triggers:** none.
