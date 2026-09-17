# Phase 4 — Component Selection

## Outcome

Users enter through Plan Trip, Airfare, or Stay and add only the components they explicitly choose. Searches are deterministic, filterable, sortable, eligibility-aware, and priced for the complete party.

## Work packages

### 4.1 Create progressive trip-builder shell

- Present the three authenticated entry points.
- Collect destination, start/end dates, and traveler count before creating a Trip and Draft, then collect only the additional fields needed by the selected entry point.
- Maintain a persistent itinerary summary and budget tally without exposing unopened component forms.
- Add explicit actions to add or remove airfare, stay, and car.
- Removing a component requires confirmation when it discards a saved selection.

### 4.2 Airfare search and selection

- Search outbound and return combinations for PDX and the trip destination/dates.
- Filter by sufficient seats and Direct flights only.
- Default-rank direct first, then complete-party price, then duration.
- Support lowest-price, shortest-duration, earliest-departure, and fewest-stops sorts.
- Use the immutable flight-combination identifier as the final tie-breaker for every sort.
- Show stop count, connection/layover, departure and arrival in local time, duration, and complete-party price.
- Save exactly one round-trip airfare selection per itinerary.

### 4.3 Stay search and selection

- Require one accommodation type preference for Plan Trip and Stay entry flows.
- Calculate required hotel/B&B rooms automatically; treat a vacation rental as a whole property.
- Filter out insufficient nightly availability or capacity.
- Display complete-stay price including taxes/fees and a transparent nightly/room breakdown.
- Default-rank stays by fit within the currently available trip budget, highest guest rating, lowest complete-stay price, then nearest city center.
- If the user has not supplied an overall budget yet, omit budget fit from the stay ranking and begin with highest guest rating.
- Support lowest-total-price, highest-guest-rating, and nearest-city-center sort overrides.
- Calculate available trip budget as the overall budget minus authoritative totals for already selected airfare and car; when replacing a stay, do not subtract the existing stay from its own search budget.
- Use the immutable property identifier as the final tie-breaker for every sort.
- Save exactly one stay selection per itinerary.

### 4.4 Rental-car selection

- Expose Add a car only when the user requests it.
- Disable selection with an explanation unless at least one traveler is 25+.
- Collect local pickup and return date/times at the destination airport, require both to fall within the Trip's start/end-date interval, require return after pickup, and search inventory for that interval.
- Calculate duration and price in consecutive 24-hour billing cycles from pickup to return, rounding any partial final cycle up to a full cycle.
- Display economy, standard, and SUV options with complete taxes-and-fees-inclusive totals. Default-order by economy, standard, then SUV; within each class use lowest complete total and then immutable catalog identifier.
- Save at most one rental-car selection per itinerary.

## Exit criteria

- Every entry point creates or contributes to an autosaved Draft.
- Optional components remain hidden until explicitly requested.
- Search results never expose unavailable capacity as selectable.
- Sorting and filtering are deterministic and covered by tests.
- Every displayed and persisted selection uses server-calculated complete pricing.

## Annotations

- **[FUTURE]** Event discovery and booking are defined in the [Version 2 Events roadmap](../future/version-2-events.md).
- **[FUTURE]** Maps, live travel times, seat maps, room selection, and vehicle extras.
