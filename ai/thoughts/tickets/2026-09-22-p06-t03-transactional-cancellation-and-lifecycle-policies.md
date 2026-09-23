# P06-T03 — Deliver Transactional Cancellation Engine and Lifecycle Policies

## Outcome

The backend system provides atomic, fee-free cancellation of active bookings with exact inventory restoration (flight seats, room nights, rental vehicle occupancy), distinguishes temporary booking cancellation from permanent trip cancellation, enforces immutable history for past/expired travel, and prevents permanent deletion of any trip with booking history.

## Requirements

- **Atomic inventory restoration on Cancel Booking:**
  - Execute booking cancellation inside a single database transaction:
    1. Lock and retrieve the active `detour_booking` row for the trip.
    2. Enforce trip ownership and verify `status = 'ACTIVE'`.
    3. Check expiration: booking cannot be canceled if the trip is Expired (`isExpired(startDate)` in `America/Los_Angeles`). Reject with HTTP 400.
    4. Restore reserved inventory:
       - **Flights:** increment `available_seats = available_seats + traveler_count` for outbound and return `flight_instance` rows.
       - **Stay:** increment `available_inventory = available_inventory + unit_count` for every reserved night in `accommodation_nightly_inventory`.
       - **Rental car:** update the corresponding `rental_unit_occupancy` row from `occupancy_status = 'ACTIVE'` to `occupancy_status = 'RELEASED'`.
    5. Update `detour_booking`: set `status = 'CANCELED'` and `canceled_at = CURRENT_TIMESTAMP`. Retain all confirmation references and grand total intact as immutable historical snapshot.
    6. Advance `detour_trip.version`.
    7. Retain the Trip in `ACTIVE` status and keep all Planned and Draft alternatives available for future planning or re-booking.
- **Atomic Trip Cancellation (`Cancel Trip`):**
  - Allowed when the trip has booking history (one or more active or canceled bookings in `detour_booking`).
  - Allowed only before the trip becomes Expired (`!isExpired(startDate)`).
  - If the trip has an `ACTIVE` booking:
    - Atomically release all finite inventory (as defined in Cancel Booking above) AND set `detour_trip.status = 'CANCELED'` in the same transaction.
    - If inventory restoration fails, the entire transaction rolls back; both the booking and trip remain `ACTIVE`.
  - If the trip has only historical `CANCELED` bookings:
    - Set `detour_trip.status = 'CANCELED'` with no inventory adjustments.
  - Once a trip is `CANCELED`:
    - All existing Draft and Planned alternatives become strictly read-only.
    - Any attempt to add/edit/delete draft selections, promote drafts, or mutate trip details on a canceled trip is rejected with HTTP 409 Conflict (`TRIP_CANCELED`).
    - The trip retains its full history and can be duplicated into a new Trip via `duplicateTrip`.
- **Enforce Trip Deletion Policy (`Delete Trip`):**
  - Implement authoritative check for `TripRepository.hasBookingHistory(long tripId)`:
    - Query `SELECT EXISTS(SELECT 1 FROM detour_booking WHERE trip_id = ?)`:
    - If `true`: permanently block `DELETE /api/trips/{tripId}` with HTTP 409 Conflict (`CANNOT_DELETE_BOOKED_TRIP`), instructing the traveler to use Cancel Trip.
    - If `false`: permit permanent cascading deletion after verifying alternative count confirmation as established in Phase 3.
- **Alternative Deletion Safeguards:**
  - Prevent deleting a Planned alternative while it is actively associated with an `ACTIVE` booking. Return HTTP 409 Conflict (`CANNOT_DELETE_ACTIVE_BOOKED_ALTERNATIVE`).
  - Deleting an unbooked Planned alternative or Draft alternative has zero inventory effect.
- **Expiration and Past Travel Safeguards:**
  - Cancel Booking and Cancel Trip are strictly forbidden once a Trip is Expired (`isExpired(startDate)`).
  - Expired and Past bookings are immutable records; they never restore past inventory and cannot be modified.
- **API endpoints:**
  - `POST /api/trips/{tripId}/bookings/{bookingId}/cancel`:
    - Payload: `{ "expectedVersion": number }`
    - Response: HTTP 200 with updated `TripResponse` showing booking marked `CANCELED`, Trip remaining active, and inventory restored.
  - `POST /api/trips/{tripId}/cancel`:
    - Payload: `{ "expectedVersion": number }`
    - Response: HTTP 200 with updated `TripResponse` showing trip status `CANCELED`.

## Acceptance criteria

- [ ] Canceling an active booking restores flight seats, nightly room inventory, and updates rental car occupancy to `RELEASED` in a single atomic transaction.
- [ ] Following Cancel Booking, the booking record is retained with `status = 'CANCELED'` and `canceled_at` timestamp, and the trip remains active.
- [ ] Attempting to cancel a booking on an Expired trip (evaluated at start of departure date in `America/Los_Angeles`) is rejected with HTTP 400.
- [ ] `hasBookingHistory` returns true whenever any active or canceled booking exists for the trip, permanently preventing `DELETE /api/trips/{tripId}` with HTTP 409.
- [ ] Canceling a trip with an active booking atomically releases all inventory and sets the trip status to `CANCELED`.
- [ ] On a `CANCELED` trip, all mutations (creating/editing/deleting drafts, promotions) are rejected with HTTP 409.
- [ ] Attempting to delete a Planned alternative that is actively booked is rejected with HTTP 409.
- [ ] Concurrent cancellation and rebooking requests are concurrency-safe and cannot create phantom inventory or leak capacity.
- [ ] Multi-user isolation tests verify users cannot cancel bookings or trips belonging to other accounts.

## Context

- **Phase/work packages:** Phase 6 — Simulated Booking and Cancellation; work package 6.3 (Booking record and confirmations), work package 6.4 (Fee-free cancellation).
- **Authoritative sources:** [`../phases/README.md`](../phases/README.md), [`../phases/CONTINUATION.md`](../phases/CONTINUATION.md), and [`../phases/phase-6-booking-and-cancellation.md`](../phases/phase-6-booking-and-cancellation.md).
- **Hard dependencies:** P06-T01 must be complete.
- **Downstream dependencies:** P06-T04 delivers user-facing cancellation dialogs and post-cancellation triage.
- **Scope exclusions:** Cancellation fees, supplier penalties, flight rescheduling/exchanges, and Version 2 Events.

## Execution profile

- **Recommended:** Full 5-Step Pipeline
- **Confidence:** high
- **Rationale:** Implements atomic inventory restoration across three distinct catalog resources, concurrency locking, and strict lifecycle transitions between active, canceled, and immutable historical states.
- **Reassessment triggers:** none.
