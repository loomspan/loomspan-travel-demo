# P05-T02 — Establish Authoritative Readiness Validation, Overage Acknowledgment, and Auditable Planned Snapshots

## Outcome

The backend validates complete planning readiness against real-time catalog availability, requires explicit acknowledgment of budget-overage warnings before Planned promotion, invalidates acknowledgments on subsequent changes, and creates auditable, immutable Planned snapshot records that preserve complete descriptive and pricing facts without relying on live catalog joins.

## Requirements

- Validate comprehensive promotion readiness:
  - Destination, start/end dates, and traveler count must conform to supported system constraints.
  - Exact traveler ages must be present for all travelers in `trip.travelerAges`, and at least one traveler must be an adult (age 18 or older).
  - Budget must be defined (`trip.budgetCents != null` and `>= 0`).
  - At least one reservable component (airfare, stay, or rental car) must be selected.
  - Return all blocking readiness issues together in a structured map (e.g. `{"travelerAges": "...", "components": "..."}`) rather than halting at the first failure.
- Revalidate catalog availability and staleness before promotion:
  - **Airfare:** verify both outbound and return flight instances exist, match trip dates/destination, and have `available_seats >= trip.travelerCount`.
  - **Stay:** verify accommodation unit matches destination, guest capacity satisfies party size (with required room count), and every night across the trip interval has `available_inventory >= unitCount`.
  - **Rental car:** verify rental unit exists, matches destination airport, driver age requirement (at least one traveler 25+) is satisfied, pickup/return dates are within trip interval with pickup < return, and unit is available over the complete requested interval.
  - If any component is sold out, stale, or unavailable, reject promotion with 400 `PLANNING_NOT_READY` and identify the offending component and specific reason.
- Budget-overage warning and acknowledgment:
  - Calculate canonical grand total against `trip.budgetCents`.
  - If `grandTotal > trip.budgetCents`, promotion requires `budgetOverageAcknowledged: true` in the promotion request body (`TripRequests.Promotion`).
  - If `grandTotal > trip.budgetCents` and `budgetOverageAcknowledged` is false or null, reject promotion with 400 `BUDGET_OVERAGE_UNACKNOWLEDGED` detailing the overage amount and grand total.
  - Invalidate any prior acknowledgment if an affected selection, traveler, date, price, or budget changes before promotion is finalized.
- Expose Draft readiness inspection endpoint:
  - Implement `GET /api/trips/{tripId}/drafts/{draftId}/readiness` returning:
    - `ready`: boolean indicating if the draft can be promoted immediately.
    - `blockingIssues`: map of field/component keys to descriptive messages.
    - `isOverBudget`: boolean indicating whether the current grand total exceeds the trip budget.
    - `budgetOverageCents`: overage amount in integer cents when over budget (or `null`/`0`).
    - `requiresOverageAcknowledgment`: boolean indicating if overage acknowledgment is required.
- Persist auditable, immutable Planned snapshots:
  - Add Flyway migration (`V16`) to enhance planned snapshot tables with complete descriptive attributes so snapshots never reconstruct display data from live catalog joins:
    - **Airfare snapshot:** carrier name, flight numbers, stops count, layover airports/durations, departure and arrival local timestamps with timezones, total duration minutes, and per-traveler fare breakdown.
    - **Stay snapshot:** accommodation property category (`HOTEL`, `BED_AND_BREAKFAST`, `VACATION_RENTAL`), property name, address/location, distance to city center, unit name, guest capacity, required room count, and nightly rate breakdown.
    - **Rental snapshot:** vehicle class name, location name, pickup and return timestamps, unit identifier, and daily rate breakdown.
  - Ensure Planned snapshots are never reconstructed from live catalog joins so future catalog price, schedule, or naming edits cannot alter historical Planned records.
  - Retain foreign key references to inventory identifiers (`outbound_flight_instance_id`, `return_flight_instance_id`, `accommodation_unit_id`, `rental_unit_id`) for Phase 6 booking revalidation.
- Maintain immutability and lifecycle rules:
  - Reject in-place update attempts on Planned snapshots with 409 `IMMUTABLE_ALTERNATIVE`.
  - Duplicate Planned snapshot into a new mutable Draft (`duplicateAlternative`) copying snapshot content while assigning fresh Draft version.
  - Delete Planned snapshot with required confirmation flag (`AlternativeDelete.confirmed = true`).
  - Disallow promotion of an Expired trip or draft with 400 `ALTERNATIVE_EXPIRED`.

## Acceptance criteria

- [x] Draft promotion fails with 400 `PLANNING_NOT_READY` when any required field (destination, dates, ages, adult, budget, at least one component) is missing or incomplete, returning all issues together.
- [x] Draft promotion fails with 400 `PLANNING_NOT_READY` when any selected flight seats, stay nightly inventory, or rental unit are sold out or unavailable.
- [x] Over-budget draft promotion is rejected with 400 `BUDGET_OVERAGE_UNACKNOWLEDGED` unless `budgetOverageAcknowledged: true` is explicitly supplied in the promotion request.
- [x] The readiness inspection endpoint (`GET /api/trips/{tripId}/drafts/{draftId}/readiness`) returns accurate blocking issues, overage status, and promotion eligibility.
- [x] Complete descriptive and schedule facts (carrier, stops, duration, property category, distance, car class) are persisted into Planned snapshot records in Flyway V16 tables.
- [x] Modifying catalog display data or deleting a source Draft does not alter previously saved Planned snapshots.
- [x] Concurrency and transactional tests verify that racing promotion requests succeed once and do not produce partial or corrupted snapshots.
- [x] Multi-user isolation prevents unauthorized inspection, promotion, or deletion across different accounts.

## Context

- **Phase/work packages:** Phase 5 — Planning, Budget, and Comparison; work package 5.2 (Validate readiness) and work package 5.3 (Save stable Planned snapshots).
- **Authoritative sources:** [`../phases/README.md`](../phases/README.md), [`../phases/CONTINUATION.md`](../phases/CONTINUATION.md), and [`../phases/phase-5-planning-budget-and-comparison.md`](../phases/phase-5-planning-budget-and-comparison.md).
- **Hard dependencies:** P05-T01 (Canonical Pricing and Server Tally Engine) must be complete.
- **Downstream dependencies:** P05-T03 (Frontend Draft Promotion and Readiness UI) consumes the readiness endpoint and overage acknowledgment contract; P05-T04 consumes the rich Planned snapshot records for comparison.
- **Scope exclusions:** Frontend comparison view, frontend promotion modal/drawer, Phase 6 booking transaction, and Version 2 Events.

## Execution profile

- **Recommended:** Full 5-Step Pipeline
- **Confidence:** high
- **Rationale:** Introduces schema migration V16, a new readiness API endpoint, concurrency-safe snapshot creation, inventory availability checks, and lifecycle constraints on Planned promotion.
- **Reassessment triggers:** If catalog availability checks reveal race conditions between promotion and subsequent booking, preserve the read-only promotion check and keep transactional inventory locks isolated to Phase 6 booking.
