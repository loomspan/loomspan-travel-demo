# DeTour Version 2 — Event Discovery and Booking

## Status

This document records Event functionality deliberately deferred from the initial DeTour release. Version 1 includes airfare, accommodations, and rental cars only. Event schema, fixtures, APIs, screens, pricing, warnings, and inventory must not be introduced by Version 1 implementation tickets.

Last updated: 2026-09-17.

## Product intent

Version 2 adds flexible Event discovery and booking without making Events depend on a complete flight-and-stay itinerary. A user may add Events to a Trip or come to DeTour specifically to find and book Events. Event attendance may cover only part of the Trip's travelers or may include additional friends who are not Trip travelers.

Events should remain flexible. Timing, age suitability, and distance concerns inform the user through clear warnings rather than unnecessarily prohibiting a selection.

## Settled boundaries carried forward

- Event categories include museum, landmark/historical site, theater, concert, market, and sports.
- The catalog includes both free sites and dated ticketed performances.
- Free sites have no reservable inventory and are informational selections, not bookable components.
- An Event-only plan may be booked when it contains at least one ticketed Event with reservable capacity. A free-site-only collection may be saved for planning but cannot produce a Booking by itself.
- Event dates and times must occur within the user's Event search dates and within the supported March 1–31, 2027 fixture window.
- Event timing is independent of selected flights. An Event that conflicts with another Event or with travel timing produces a warning, not a prohibition.
- Conflict warnings must identify the affected selections and remain visible through booking review. They do not need to drive automatic rescheduling or itinerary optimization.
- Users choose an attendee count for each ticketed Event independently of the Trip traveler count. This supports attendance by only part of the party or by additional friends.
- Ticketed Events reserve the selected attendee capacity only when booked.
- Event results are deterministic and rank by popularity after active filters, with an immutable Event/performance identifier as the final tie-breaker.

## Discovery and selection

- Add an authenticated **Event Calendar** home-page entry point.
- Support standalone destination/date discovery centered on the destination city as well as Event selection within an existing Trip.
- Provide visible and individually removable date, category, age-suitability, price, and radius filters.
- Radius choices are 1, 5, 10, 25, and 50 miles; the default is 10 miles.
- Standalone searches calculate distance from the destination city center.
- Trip Event searches calculate distance from the selected stay when one exists and otherwise from the destination city center.
- Users may select an Event despite an age-suitability warning after explicit acknowledgment.
- Users may retain schedule-conflicting Events after a visible warning and explicit acknowledgment.
- Replacing or removing a stay never silently removes selected Events. Recalculate distance from the replacement stay or city center and mark affected selections **Review required**.
- Users may retain an out-of-radius Event after acknowledgment.
- Event warning acknowledgments are invalidated when an affected Event, attendee count, traveler age, date, time, stay/location, price, or filter-dependent selection changes.

## Planning and budget behavior

- Events have an optional Event Allowance.
- Unused Event Allowance never reduces the overall available Trip budget and is never treated as an actual cost.
- Costs of already booked Events are included in the Trip's actual budget tally and booking history.
- Before booking, selected Event costs are shown as a projected Event total, separate from actual booked cost.
- The booking grand total for an Event booking includes every paid Event and its selected attendee count.
- Budget and allowance overages warn but do not prohibit planning or booking.

## Booking and cancellation

- Revalidate Event ownership, performance time, attendee count, price, suitability acknowledgments, and capacity on the server.
- Reserve every selected ticketed Event atomically and idempotently; reserve no capacity for free sites.
- Event-only booking is supported and does not require airfare, accommodation, or rental-car selection.
- Preserve an immutable Event booking snapshot with fictional confirmation references and the attendee count for each Event.
- Cancellation restores ticketed capacity exactly once and retains the canceled Event booking history.
- A canceled Event booking is an immutable historical record, consistent with the Version 1 distinction between itinerary alternatives and Booking history.

## Catalog and fixture direction

- Create approximately 15–20 listings per destination across the supported categories.
- Seed dated performances, always-available/free sites, venue locations, complete attendee pricing, capacity, popularity, and age-suitability metadata.
- Ensure useful results exist across supported March dates and radius choices.
- Store coordinates or deterministic precomputed distances sufficient for fixture-based radius filtering; no live mapping service is required.
- Add integrity checks for date/category coverage, pricing, capacity, timezone validity, radius examples, and deterministic ordering.

## Interaction with duplication

- When creating a new Trip from selected Planned snapshots, copy Event selections into the new Draft only when their destination and occurrence dates remain compatible.
- Remove incompatible Event selections and include them in the same post-duplication summary used for incompatible Version 1 components.
- Never silently rewrite the original Planned snapshot.

## Version 2 decisions still required

- Maximum attendee count per Event and whether additional attendee ages must be captured for suitability warnings.
- Whether users may book an Event immediately from search results or must first save an Event-only Planned snapshot.
- Whether Events can be added to an already active airfare/stay/car Booking or must use a separate Event Booking. Adding them to an active Booking would otherwise overlap Version 1's deferred booking-modification scope.
- Whether cancellation acts on an entire Event Booking, individual Events, or both.
- Exact behavior and copy for projected selected-Event cost versus already booked Event cost in the Trip budget presentation.
- Exact fictional Event names, schedules, prices, capacities, popularity scores, coordinates/distances, and suitability metadata.

## Explicit exclusions unless separately approved

- Automatic schedule optimization or conflict resolution.
- Recommendations based on learned preferences.
- Live venue or ticketing-provider integration.
- Maps, live routing, and live travel-time calculations.
- Resale, waitlists, dynamic pricing, seat maps, and ticket transfer.
