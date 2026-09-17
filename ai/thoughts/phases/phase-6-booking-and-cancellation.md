# Phase 6 — Simulated Booking and Cancellation

## Outcome

A user can book one valid Planned itinerary with a deliberate confirmation, receive fictional references, and later cancel without fees while all finite inventory remains transactionally correct.

## Work packages

### 6.1 Booking review

- Present every selected component, traveler count, dates, warnings, component totals, and final booking grand total.
- State clearly that this is a simulated booking with fictional inventory and no payment.
- Collect explicit confirmation but no payment card or billing address.
- Prevent booking an Expired itinerary or a trip that already has an active booking.

### 6.2 Atomic inventory reservation

- Lock/revalidate every selected finite inventory row in a consistent order.
- Confirm ownership, snapshot state, price, eligibility, and availability inside the transaction.
- Reserve flight seats, accommodation units/nights, and a rental unit for its complete pickup/return interval together.
- On any conflict, reserve nothing and return a precise review-required response.
- Make repeated confirmation safe with an idempotency key.

### 6.3 Booking record and confirmations

- Create an immutable Booked itinerary/booking snapshot and fictional references per applicable component.
- On cancellation, retain that immutable snapshot as a Canceled Booking record. `Canceled` describes booking history, not a mutable itinerary state.
- Preserve the selected Planned alternative and other alternatives.
- Surface the booking under Upcoming and later Past without scheduled state mutation.
- Persist across application restart.

### 6.4 Fee-free cancellation

- Allow Cancel Booking and Cancel Trip only before the Trip becomes Expired in its departure timezone. Expired and Past Bookings remain immutable history and do not restore past inventory.
- Present distinct **Delete Draft**, **Delete Planned itinerary**, **Cancel Booking**, **Delete Trip**, and **Cancel Trip** actions so the affected scope is explicit.
- Delete Draft or Planned alternatives without inventory effects after appropriate confirmation.
- For Cancel Booking, require confirmation, restore all finite inventory atomically and exactly once, preserve details/references/cancellation time, and keep the Trip active.
- After Cancel Booking, offer Use a saved alternative, Create a new Draft, or Done for now. A selected saved alternative is duplicated into a Draft and must pass current validation before it can become Planned and Booked.
- Permit Delete Trip only when the Trip has never had a Booking; confirm the count of Draft and Planned alternatives that will be permanently removed.
- When booking history exists, use Cancel Trip and retain the Trip and history. Make its alternatives read-only and offer Duplicate into a new Trip.
- If Cancel Trip has an active Booking, release inventory and close the Trip in one atomic operation. If release fails, leave both Booking and Trip active.
- If only historical canceled Bookings exist, close the Trip without another inventory adjustment.
- Ensure concurrent cancel/rebook requests cannot create or lose inventory.

## Exit criteria

- Successful booking decrements every applicable inventory type exactly once.
- Failed or racing booking attempts never partially reserve inventory.
- Cancellation restores every reservation exactly once and retains history.
- Canceling only a Booking leaves its Trip usable; canceling a Trip with history makes its alternatives read-only.
- Cross-user booking/cancel attempts reveal no protected data.
- End-to-end tests cover booking with each valid component combination.

## Annotations

- **[UNDECIDED]** Exact format of fictional booking and component confirmation references.
- **[FUTURE]** Payment, cancellation penalties, refunds, modifications/exchanges, supplier cancellations, recovery, waitlists, and overbooking.
