# P04-T03 — Deliver Rental Car Search and Draft Selection

## Outcome

Authenticated users can search airport rental car inventory within their Trip dates, enforce the 25+ driver eligibility rule with an actionable explanation, calculate duration and complete pricing in consecutive 24-hour billing cycles, rank options deterministically across economy, standard, and SUV classes, and persist or remove a rental car selection on an autosaved Draft alternative.

## Requirements

- Scope all car search and selection mutations to the authenticated user and their owned Trip and Draft. Enforce cross-user isolation.
- Driver eligibility rule (25+):
  - Rental car selection requires at least one traveler on the Trip to be 25 or older.
  - If traveler ages have not yet been supplied or no traveler is aged 25+, search must report that car selection is disabled and provide an explicit actionable explanation: *"Rental cars require at least one traveler aged 25 or older. Please add traveler ages in Trip Details to select a car."*
  - Selection mutation must strictly reject saving a rental car if no traveler is 25 or older.
- Interval and location validation:
  - Pickup and return occur at the destination airport rental location.
  - Pickup and return date/times must fall within the Trip's start and end date interval in the destination airport's local timezone (`[startDate 00:00, endDate 23:59]`).
  - Require return strictly after pickup (`pickupAt < returnAt`).
- Inventory availability check:
  - Search physical units across Economy, Standard, and SUV classes at the destination airport.
  - A unit is available only if it has no overlapping occupancy in `rental_unit_occupancy` during the half-open interval `[pickupAt, returnAt)`. Units with overlapping reservations are excluded.
- Duration and price calculation:
  - Calculate duration in consecutive 24-hour billing cycles from pickup to return.
  - Round any partial final cycle up to a full 24-hour cycle (e.g. 25 hours = 2 billing cycles).
  - Price equals `billingCycles * (daily_base_price_cents + daily_tax_cents + daily_fee_cents)`.
  - Provide complete taxes-and-fees-inclusive totals in USD integer cents.
- Deterministic search ranking:
  - Default order by vehicle class: Economy, then Standard, then SUV.
  - Within each vehicle class, order by lowest complete total price, then immutable vehicle unit catalog key tie-breaker.
- Mutate Draft rental selection:
  - Save at most one rental-car selection per Draft into `detour_trip_draft_rental_selection` (`draft_id`, `rental_unit_id`, `pickup_at`, `return_at`).
  - Support replacing an existing rental selection with a newly chosen unit and times.
  - Support removing the rental selection from the Draft.
  - Enforce optimistic concurrency on Trip (`expectedVersion`) and Draft (`expectedDraftVersion`), advancing versions on mutation.
  - Return the updated `TripResponse` with full selection data.
- Do not introduce airfare search, accommodation search, planning promotion, comparison UI, booking, or Version 2 Events in this ticket.

## Acceptance criteria

- [x] An authenticated user can search rental cars for their Trip by specifying pickup and return date/times within the trip interval at the destination airport.
- [x] Requests with return before or equal to pickup, or times outside the trip date window, are rejected with clear validation messages.
- [x] When no traveler is 25 or older, search indicates selection is disabled and provides the required 25+ explanation; selecting a car in this state is rejected.
- [x] Rental units with overlapping reservations in `rental_unit_occupancy` over `[pickupAt, returnAt)` are excluded from selectable results.
- [x] Pricing charges consecutive 24-hour cycles and rounds any partial final cycle up to a full cycle (e.g., 25 hours charged as 2 full days) in USD integer cents.
- [x] Results default-order by Economy, Standard, SUV; then lowest total price; then vehicle unit catalog key.
- [x] Saving a rental selection persists unit ID, pickup time, and return time in `detour_trip_draft_rental_selection`, advances draft version, and returns updated `TripResponse`.
- [x] Replacing a rental selection updates the draft without duplicate rows.
- [x] Removing a rental selection deletes the draft rental record and advances the draft version.
- [x] Concurrency conflicts return 409 `VERSION_CONFLICT`; unauthorized access returns 404 with no data disclosure.
- [x] Backend integration tests verify age gating, 24-hour billing cycle math, half-open interval occupancy checking, ranking determinism, and selection persistence.

## Context

- **Phase/work package:** Phase 4 — Component Selection; work package 4.4 and car selection in 4.1.
- **Authoritative sources:** [`../phases/README.md`](../phases/README.md), [`../phases/CONTINUATION.md`](../phases/CONTINUATION.md), and [`../phases/phase-4-component-selection.md`](../phases/phase-4-component-selection.md).
- **Required architecture:** [`../architecture/2026-09-17-p00-t02-detour-replacement-architecture.md`](../architecture/2026-09-17-p00-t02-detour-replacement-architecture.md).
- **Hard dependency:** Phase 2 rental catalog fixtures (V4, V11) and Phase 3 Trip/Draft foundation (V12, V14).
- **Downstream dependencies:** P04-T04 consumes this API for rental car search and selection in the progressive trip-builder.
- **Scope exclusions:** Airfare search, accommodation search, whole-itinerary canonical pricing, comparison, booking/cancellation, and Version 2 Events.
- This ticket is sized for GPT-5.6 Terra around one cohesive domain capability: rental car search, 25+ driver validation, 24-hour billing cycle calculation, half-open interval occupancy checks, draft selection persistence, and concurrency/authorization verification.

## Execution profile

- **Recommended:** Full 5-Step Pipeline
- **Confidence:** high
- **Rationale:** Introduces public API search and mutation contracts, zoned datetime interval validation, half-open occupancy checking, 24-hour cycle billing math, and draft selection persistence with optimistic locking.
- **Reassessment triggers:** If timezone offsets for the destination airports introduce ambiguities in interval bounding across calendar day boundaries, keep on the full route to establish explicit zoned evaluation rules.
