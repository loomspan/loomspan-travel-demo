# Phase 3 — Trips, Itineraries, and Profile

## Outcome

Authenticated users can create upcoming trips, maintain multiple autosaved Draft alternatives, preserve stable Planned snapshots, and navigate future and historical work from a clear profile.

## Work packages

### 3.1 Implement the trip aggregate

- Store owner, destination, start/end dates, travelers/ages, and budget.
- Require destination, supported start/end dates, and a traveler count of 1–8 before creating a Trip and its first component-empty Draft.
- Require each traveler's age, at least one adult, and a budget before promotion to Planned.
- Derive the display label from destination and dates.
- Use optimistic concurrency/versioning so concurrent tabs cannot silently overwrite shared trip details.

### 3.2 Implement itinerary alternatives

- Allow multiple Draft and Planned itineraries per trip.
- Let the user either start a component-empty Draft or explicitly duplicate any existing Draft, Planned, Booked itinerary, or Canceled Booking snapshot into a new Draft.
- Autosave Draft component selections and expose saving/saved/error status.
- Promote a valid Draft to an immutable Planned snapshot.
- Duplicate Draft, Planned, Booked itinerary, or Canceled Booking snapshot content into a new Draft as allowed.
- Enforce at most one active Booked itinerary per trip.
- Preserve unselected Planned alternatives after booking.
- Permit new Draft and Planned alternatives after booking while blocking a second booking until the active booking is canceled.

### 3.3 Protect shared-detail changes

- Permit changes to destination, dates, and travelers while no Planned itinerary exists.
- When traveler count or ages change in a Draft, reprice and revalidate every selection against capacity and eligibility. Retain selections that remain valid; remove invalid selections; and immediately summarize every price, room-count, eligibility, or selection change.
- When destination or date changes make a Draft component incompatible, remove that component and immediately tell the user what was removed and why.
- When Planned alternatives exist, create a new Trip for revised shared details and let the user select which Planned snapshots to use as sources. Convert each selected source into a Draft in the new Trip; do not copy Draft, Booked, or Canceled alternatives as part of this Trip-level operation.
- Remove component selections incompatible with the revised destination, dates, or travelers and show a clear post-duplication summary naming everything removed and why.
- Apply the same selective Planned-snapshot duplication and incompatibility rules when duplicating a canceled Trip.
- Never silently rewrite or invalidate Planned snapshots.
- Recalculate budget presentation without mutating snapshot selections when the budget changes.

### 3.4 Build profile organization

- Show upcoming trips ordered by start date, with nested counts/statuses for Draft, Planned, and Booked alternatives.
- Show past trips based on end date rather than a scheduled status mutation.
- Label unbooked Draft/Planned alternatives Expired at the start of the departure date in the departure location's timezone and prevent booking. For the fixed PDX origin, use `America/Los_Angeles`.
- Preserve past Booked and Canceled Booking history. A Canceled Booking is an immutable historical booking snapshot, not a mutable itinerary state; its source Planned snapshot remains a separate alternative unless explicitly deleted when deletion is otherwise allowed.
- Allow deletion of Drafts and explicit deletion of Planned snapshots; protect booking history.
- Permit permanent Trip deletion only when no Booking has ever existed, with confirmation listing every Draft and Planned alternative that will be removed.
- When booking history exists, retain a canceled Trip under Canceled Trips and make its Draft and Planned alternatives read-only.
- Offer selective duplication of its Planned snapshots into Drafts in a new Trip rather than reopening the canceled aggregate. If no Planned snapshot exists or none is selected, allow duplication of the shared Trip details into one component-empty Draft and explain that no Planned alternative was copied.

## Exit criteria

- New users see an empty, useful profile state.
- A user can create a trip and multiple Draft alternatives and refresh without losing progress.
- Planned snapshots remain stable.
- Shared-detail edits create a new trip when required.
- Upcoming, Past, and Expired views behave correctly under a controllable clock.

## Annotations

- **[RESOLVED]** The current profile UI establishes card hierarchy and status/count presentation; Phase 7 may refine its visual consistency and accessibility.
- **[FUTURE]** Custom trip names, notes, sharing, collaboration, voting, and merging independently created trips.
