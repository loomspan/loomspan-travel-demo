# P04-T02 — Deliver Stay Search and Draft Selection

## Outcome

Authenticated users can search accommodations for their Trip destination and dates filtered by accommodation type preference, receive automatic room-count calculations and transparent nightly pricing, see results deterministically ranked by available-budget fit and guest rating, and persist or remove a stay selection on an autosaved Draft alternative.

## Requirements

- Scope all stay search and selection mutations to the authenticated user and their owned Trip and Draft. Enforce cross-user isolation.
- Filter search by a mandatory accommodation type preference: `HOTEL`, `BED_AND_BREAKFAST`, or `VACATION_RENTAL`.
- Automatic room-count calculation:
  - For `HOTEL` and `BED_AND_BREAKFAST`, compute required bookable units: `requiredRooms = ceil(travelerCount / unitGuestCapacity)`.
  - For `VACATION_RENTAL`, treat as a whole property: `requiredRooms = 1`. Filter out vacation rentals whose guest capacity is less than `travelerCount`.
- Nightly inventory and capacity check:
  - Filter out any accommodation unit where `available_inventory < requiredRooms` for any night in the trip date range `[startDate, endDate)`.
- Complete stay pricing:
  - Calculate complete-stay price in USD integer cents: sum of `(base_price_cents + tax_cents + fee_cents)` across all nights in the stay, multiplied by `requiredRooms`.
  - Provide a transparent nightly and per-room breakdown.
- Available trip budget calculation:
  - Calculate available trip budget as `trip.budgetCents - selectedAirfareTotal - selectedCarTotal`.
  - When replacing an existing stay selection, do NOT subtract the existing stay from its own search budget.
  - If the Trip has no budget set (`budgetCents == null`), available budget is undefined.
- Deterministic search ranking:
  - Default ranking:
    - If a budget is defined, rank options that fit within the available trip budget (`totalPrice <= availableBudget`) before options that exceed it (Tier 1 then Tier 2).
    - Within each tier, order by highest guest rating (descending); then lowest complete-stay price (ascending); then nearest city center (ascending `distance_to_city_center_meters`).
    - If no overall budget has been supplied yet, omit budget-fit tiering and begin directly with highest guest rating, lowest complete price, then nearest city center.
  - Support user-selected sort overrides:
    - `LOWEST_PRICE`: ascending complete-stay total price;
    - `HIGHEST_RATING`: descending guest rating;
    - `NEAREST_CITY_CENTER`: ascending distance to city center in meters.
  - Use the immutable property catalog key as the final tie-breaker for every sort.
- Mutate Draft stay selection:
  - Save exactly one stay selection per Draft into `detour_trip_draft_stay_selection` (`draft_id`, `accommodation_unit_id`, `unit_count`).
  - Support replacing an existing stay selection with a new selection.
  - Support removing the stay selection from the Draft.
  - Enforce optimistic concurrency on Trip (`expectedVersion`) and Draft (`expectedDraftVersion`), advancing versions on mutation.
  - Return the updated `TripResponse` with full selection data.
- Do not introduce airfare search, rental car search, planning promotion, comparison UI, booking, or Version 2 Events in this ticket.

## Acceptance criteria

- [x] An authenticated user can search stays for their Trip destination and dates by supplying a required accommodation type (`HOTEL`, `BED_AND_BREAKFAST`, or `VACATION_RENTAL`).
- [x] Required room count is automatically calculated based on party size and unit capacity for hotels and B&Bs, and is set to 1 for vacation rentals.
- [x] Vacation rentals with guest capacity less than the traveler count are excluded.
- [x] Stays with insufficient inventory on any night during the trip are excluded from selectable results.
- [x] Complete-stay pricing sums all nightly base, tax, and fee amounts multiplied by the required room count in USD integer cents.
- [x] Available trip budget correctly subtracts already-selected airfare and car totals, but does not subtract an existing stay when replacing it.
- [x] Default ranking places within-budget stays before over-budget stays when a budget is present, ordered by rating, price, and distance with property catalog key tie-breaker.
- [x] When no trip budget is set, budget-fit tiering is omitted and results order directly by rating, price, and distance.
- [x] Sort overrides for lowest price, highest rating, and nearest city center order results deterministically.
- [x] Saving a stay selection persists unit ID and unit count in `detour_trip_draft_stay_selection`, advances draft version, and returns updated `TripResponse`.
- [x] Replacing a stay selection updates the existing selection without duplicate rows.
- [x] Removing a stay selection deletes the draft stay record cleanly and advances the draft version.
- [x] Mismatched `expectedVersion` or `expectedDraftVersion` returns 409 `VERSION_CONFLICT`.
- [x] Unauthorized access to another user's Trip returns 404 with no data disclosure.
- [x] Backend integration tests verify room calculation, inventory filtering, available budget math, ranking determinism, and selection persistence.

## Context

- **Phase/work package:** Phase 4 — Component Selection; work package 4.3 and stay selection in 4.1.
- **Authoritative sources:** [`../phases/README.md`](../phases/README.md), [`../phases/CONTINUATION.md`](../phases/CONTINUATION.md), and [`../phases/phase-4-component-selection.md`](../phases/phase-4-component-selection.md).
- **Required architecture:** [`../architecture/2026-09-17-p00-t02-detour-replacement-architecture.md`](../architecture/2026-09-17-p00-t02-detour-replacement-architecture.md).
- **Hard dependency:** Phase 2 stay fixtures and search metadata (V4, V5, V11) and Phase 3 Trip/Draft foundation (V12, V14).
- **Downstream dependencies:** P04-T04 consumes this API for stay search and selection in the progressive trip-builder.
- **Scope exclusions:** Airfare search, rental car search, whole-itinerary canonical pricing, comparison, booking/cancellation, and Version 2 Events.
- This ticket is sized for GPT-5.6 Terra around one cohesive domain capability: stay search, room calculation, available-budget determination, budget-fit ranking, draft selection persistence, and concurrency/authorization verification.

## Execution profile

- **Recommended:** Full 5-Step Pipeline
- **Confidence:** high
- **Rationale:** Introduces public API search and mutation contracts, dynamic party room calculations, multi-night inventory filtering, and budget-fit ranking tiers with optimistic locking.
- **Reassessment triggers:** If available budget computation encounters edge cases with partially invalid existing selections, keep on the full route to establish authoritative fallback rules.
