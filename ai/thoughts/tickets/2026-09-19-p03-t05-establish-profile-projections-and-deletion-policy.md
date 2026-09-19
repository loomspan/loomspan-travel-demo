# P03-T05 — Establish Date-Derived Profile Views and Safe Deletion Policy

## Outcome

The backend provides a deterministic profile view of upcoming and past Trips with alternative counts and booking eligibility derived from time, while destructive actions clearly distinguish deleting an alternative from deleting a Trip and leave a safe contract for Phase 6 booking history.

## Requirements

- Provide an owner-scoped profile projection ordered by Trip start date. Include the derived Trip label, dates, destination, and nested counts/status information for the alternative types currently present.
- Derive Upcoming and Past from dates at read time; do not persist or schedule a status mutation. A Trip is Past after its end date according to the product's date semantics.
- Derive Expired for unbooked Draft and Planned alternatives at the start of the departure date in `America/Los_Angeles`, the fixed PDX departure timezone. Expired alternatives remain visible but cannot be promoted or booked.
- Inject or otherwise control the application clock in deterministic tests, including the instant immediately before and at the expiration boundary and around relevant daylight-saving/offset behavior.
- Support Delete Draft and explicit, confirmation-gated Delete Planned itinerary as distinct operations. Protect immutable booking history from these operations and do not treat Canceled Booking as an itinerary state.
- Permit permanent deletion of a Phase 3 Trip, for which no Booking can yet exist, only after confirmation data identifies the Trip and lists the exact Draft and Planned counts that will be removed; a stale confirmation must fail if those counts or the Trip version changed. Phase 6 must add the durable “no Booking has ever existed” guard before it introduces Booking records.
- Delete an eligible Trip and all of its Draft/Planned alternatives atomically without changing catalog inventory. Do not add placeholder Booking history; Phase 6 owns rejection when history exists and the retained Cancel Trip/Canceled Trips behavior.
- Define the profile projection and deletion guard so Phase 6 can add one active Booked count, immutable Canceled Booking history, Canceled Trips, and the one-active-booking restriction without rewriting the meaning of Draft, Planned, Expired, Upcoming, or Past. Do not create placeholder bookings or simulated history.

## Acceptance criteria

- [ ] The profile endpoint returns only the signed-in user's Trips, orders upcoming Trips by start date with a deterministic tie-breaker, and reports accurate Draft and Planned counts/statuses.
- [ ] A controllable clock proves the precise transition to Expired at midnight on the departure date in `America/Los_Angeles`, and expired unbooked alternatives cannot be promoted or booked.
- [ ] Upcoming versus Past changes through date-derived reads without a scheduled database mutation, and records remain correct across restart.
- [ ] Delete Draft, Delete Planned itinerary, and Delete Trip have distinct guarded behavior; stale confirmations and cross-user requests fail without partial deletion or protected-data disclosure.
- [ ] An eligible never-booked Trip is deleted atomically only after confirmation that lists every Draft and Planned alternative count that will disappear.
- [ ] No Phase 3 deletion operation models, accepts, or removes Booked/Canceled Booking history, and the downstream requirement for Phase 6 to block permanent deletion after any Booking is explicit in the deletion contract.
- [ ] Profile queries avoid leaking another user's counts or identifiers and remain deterministic when multiple Trips share a date.
- [ ] Phase 6 booking creation, inventory cancellation, confirmation references, and Canceled Trips UI are not introduced.

## Context

- **Phase/work package:** Phase 3 — Trips, Itineraries, and Profile; backend and temporal portions of work package 3.4.
- **Authoritative sources:** [`../phases/README.md`](../phases/README.md), [`../phases/CONTINUATION.md`](../phases/CONTINUATION.md), [`../phases/phase-3-trips-itineraries-and-profile.md`](../phases/phase-3-trips-itineraries-and-profile.md), and the booking-history extension rules in [`../phases/phase-6-booking-and-cancellation.md`](../phases/phase-6-booking-and-cancellation.md).
- **Hard dependencies:** P03-T03 must be complete. P03-T04 should be complete before the profile becomes the primary entry point to revised Trips.
- **Downstream dependencies:** P03-T06 consumes this projection and deletion contract. Phase 6 extends it with active Booked and Canceled Booking history and implements Cancel Booking/Cancel Trip inventory behavior.
- **Scope exclusions:** final profile card presentation, booking or cancellation transactions, inventory mutation, deleting booking history, scheduled status jobs, custom timezones/origins, and Version 2 Events.
- This ticket is sized for GPT-5.6 Terra around temporal classification, protected deletion, and owner-scoped projection logic; the substantial responsive profile interaction work remains separate.

## Execution profile

- **Recommended:** Full 5-Step Pipeline
- **Confidence:** high
- **Rationale:** The ticket defines time-sensitive lifecycle behavior, destructive persisted operations, authorization boundaries, and forward-compatible booking-history protection.
- **Reassessment triggers:** If the current schema has no safe way to prove that a Booking ever existed once Phase 6 records are present, retain the full route and settle the durable deletion guard rather than relying on current active state.
