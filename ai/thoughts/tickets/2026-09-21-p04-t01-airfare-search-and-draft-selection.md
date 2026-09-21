# P04-T01 — Deliver Airfare Search and Draft Selection

## Outcome

Authenticated users can search round-trip flight combinations between PDX and their Trip destination for their scheduled dates, filter for direct flights, receive deterministically ranked results priced for their complete party, and persist or remove an airfare selection on an autosaved Draft alternative.

## Requirements

- Scope all flight search and selection mutations to the authenticated user and their owned Trip and Draft. Never allow one user to search, read, or mutate another user's Draft.
- Search outbound and return flights for the fixed PDX origin and the Trip's destination and dates:
  - Generate valid round-trip combinations from dated outbound and return `flight_instance` records.
  - Exclude combinations where either flight has insufficient available seats for the Trip's traveler count.
  - Exclude any flight instance whose final arrival occurs after March 31, 2027.
- Provide a `directOnly` filter: when true, include only direct flights in both directions; when false, include both direct and one-stop flights.
- Calculate complete-party pricing in USD integer cents:
  - Every traveler reserves a seat and pays the exact same airfare regardless of age.
  - Total complete-party fare equals `travelerCount * (baseFare + tax + fee)` for outbound plus return.
  - Display clear base fare, taxes, and fees breakdown alongside the party total.
- Implement deterministic search ranking:
  - Default ranking: direct flights first (both legs direct); then lowest complete-party price; then shortest scheduled duration (sum of outbound and return durations).
  - Support user-selected sort overrides:
    - `LOWEST_PRICE`: ascending complete-party price;
    - `SHORTEST_DURATION`: ascending total duration;
    - `EARLIEST_DEPARTURE`: outbound flight local departure time;
    - `FEWEST_STOPS`: ascending total stop count across both directions.
  - Use the immutable flight-combination identifier (e.g. `${outboundCatalogKey}__${returnCatalogKey}`) as the final tie-breaker for every sort.
- Expose flight details: carrier, flight numbers, stop count, layover airport and duration for connecting flights, departure and arrival in destination/origin local time with timezone info, total duration, available seats, and complete-party price.
- Mutate Draft airfare selection:
  - Save exactly one round-trip airfare selection per Draft into `detour_trip_draft_airfare_selection` (`draft_id`, `outbound_flight_instance_id`, `return_flight_instance_id`).
  - Support replacing an existing airfare selection with a newly chosen combination.
  - Support removing the airfare selection from the Draft.
  - Enforce optimistic concurrency on Trip (`expectedVersion`) and Draft (`expectedDraftVersion`), incrementing the Draft version and advancing the Trip version on mutation.
  - Return the updated `TripResponse` containing the full `DraftSelectionResponse` and updated versions.
- Do not introduce accommodation search, rental car search, planning promotion, comparison UI, booking, or Version 2 Events in this ticket.

## Acceptance criteria

- [ ] An authenticated user can search round-trip flights for their Trip destination and dates and receives available round-trip combinations between PDX and the destination.
- [ ] Combinations with insufficient seat capacity for the traveler count are omitted from results.
- [ ] The `directOnly` filter restricts results to direct flights; omitting it returns direct and one-stop flights.
- [ ] Default ranking places direct flights first, then orders by complete-party price, then duration, ending with the deterministic flight combination key tie-breaker.
- [ ] Sort overrides for lowest price, shortest duration, earliest departure, and fewest stops sort deterministically with the combination key tie-breaker.
- [ ] Pricing calculates the exact complete-party total (`travelerCount * perSeatTotal`) in USD integer cents with transparent base/tax/fee breakdown.
- [ ] Saving an airfare selection persists outbound and return flight instance IDs in `detour_trip_draft_airfare_selection`, advances draft version, and returns updated `TripResponse`.
- [ ] Replacing an airfare selection replaces the existing selection cleanly without orphaned or duplicate rows.
- [ ] Removing an airfare selection deletes the draft airfare record and advances the draft version.
- [ ] Mutations with mismatched `expectedVersion` or `expectedDraftVersion` return 409 `VERSION_CONFLICT` without modifying persisted data.
- [ ] Another user cannot search or mutate airfare on a Trip they do not own and receives 404 with no data disclosure.
- [ ] Backend integration tests verify search, filtering, deterministic sorting, concurrency conflict handling, and cross-user isolation.

## Context

- **Phase/work package:** Phase 4 — Component Selection; work package 4.2 and airfare selection in 4.1.
- **Authoritative sources:** [`../phases/README.md`](../phases/README.md), [`../phases/CONTINUATION.md`](../phases/CONTINUATION.md), and [`../phases/phase-4-component-selection.md`](../phases/phase-4-component-selection.md).
- **Required architecture:** [`../architecture/2026-09-17-p00-t02-detour-replacement-architecture.md`](../architecture/2026-09-17-p00-t02-detour-replacement-architecture.md).
- **Hard dependency:** Phase 2 flight catalog fixtures (V3, V10) and Phase 3 Trip/Draft foundation (V12, V14).
- **Downstream dependencies:** P04-T04 consumes this API for flight search and selection in the progressive trip-builder.
- **Scope exclusions:** Accommodation search, rental car search, whole-itinerary canonical pricing, comparison, booking/cancellation, and Version 2 Events.
- This ticket is sized for GPT-5.6 Terra around one cohesive domain capability: airfare search, party pricing, deterministic sorting, draft selection persistence, and concurrency/authorization verification.

## Execution profile

- **Recommended:** Full 5-Step Pipeline
- **Confidence:** high
- **Rationale:** Introduces public API search and mutation contracts, round-trip pairing logic, deterministic ranking algorithms, and draft selection persistence with optimistic locking.
- **Reassessment triggers:** If round-trip combination generation cannot be performed deterministically within acceptable response times using the Phase 2 schema, keep on the full route to refine query indexing rather than relaxing determinism.
