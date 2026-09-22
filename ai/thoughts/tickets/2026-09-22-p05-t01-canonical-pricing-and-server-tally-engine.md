# P05-T01 — Implement Canonical Pricing and Server Tally Engine

## Outcome

The server deterministically calculates canonical airfare, stay, rental car, and grand totals, remaining budget, overage, and available component search budgets across all Draft and Planned alternatives, exposing authoritative price summaries in API responses so all client views share a consistent, tamper-proof price calculation.

## Requirements

- Calculate individual component prices and grand totals entirely on the server using integer cents (USD):
  - **Airfare total:** `(outbound per-traveler fare + return per-traveler fare) * trip.travelerCount`, where each direction's fare is `base_fare_cents + tax_cents + fee_cents`.
  - **Stay total:** sum of nightly rates (`base_price_cents + tax_cents + fee_cents`) for all nights across the trip interval multiplied by `unitCount` (required room count).
  - **Rental car total:** consecutive 24-hour billing cycles between local pickup and return date/times (rounding any partial final cycle up to a full cycle) multiplied by the vehicle's daily rate (`daily_base_price_cents + daily_tax_cents + daily_fee_cents`).
  - **Grand total:** sum of selected airfare total, stay total, and rental car total.
- Maintain authoritative tally and budget position in alternative and draft responses:
  - If `trip.budgetCents` is set:
    - If `grandTotal <= budgetCents`, calculate `remainingBudgetCents = budgetCents - grandTotal`, `budgetOverageCents = 0`, and `isOverBudget = false`.
    - If `grandTotal > budgetCents`, calculate `remainingBudgetCents = 0`, `budgetOverageCents = grandTotal - budgetCents`, and `isOverBudget = true`.
  - If `trip.budgetCents` is null:
    - Return `remainingBudgetCents = null`, `budgetOverageCents = null`, and `isOverBudget = false`.
- Enforce budget validation boundaries:
  - An overall budget of `0` cents is valid.
  - Negative budget values are rejected with 400 `VALIDATION_FAILED`.
  - Enforce the maximum representable budget of $1,000,000.00 (`100_000_000` cents). Values exceeding this bound are rejected with 400 `VALIDATION_FAILED`.
- Calculate available search budget canonically across component searches:
  - Available search budget is `trip.budgetCents` minus authoritative totals for already selected components other than the component being searched or replaced.
  - When replacing an existing selection (e.g. searching stays when a stay is already selected in the draft), do not subtract the existing stay's cost from the search's available budget.
  - When no overall budget has been supplied yet, available search budget is `null`, suppressing budget-fit ranking and remaining/overage presentation.
- Expose structured pricing summaries on all alternative and trip responses:
  - Include `airfareTotalCents`, `stayTotalCents`, `rentalTotalCents`, `grandTotalCents`, `remainingBudgetCents`, `budgetOverageCents`, and `isOverBudget` on `DraftResponse`, `PlannedResponse`, `AlternativeResponse`, and `TripResponse` (or via an embedded `ItineraryTallyResponse`).
- Recalculate component prices from authoritative catalog data on retrieval and mutation to guarantee price freshness against live catalog fixtures.
- Exclude booking reservation mutations, UI components, and Phase 6 checkout from this ticket.

## Acceptance criteria

- [ ] Airfare, stay, rental car, and grand totals are calculated on the server and returned in integer cents for all Draft, Planned, and Alternative responses.
- [ ] Trips with a defined budget return accurate `remainingBudgetCents`, `budgetOverageCents`, and `isOverBudget` indicators matching the server-calculated totals.
- [ ] Trips without a defined budget return `null` for remaining budget and overage, and omit budget-fit ranking inputs in component searches.
- [ ] Overall budgets of `0` cents are accepted, while negative values and values exceeding `100_000_000` cents are rejected with 400 `VALIDATION_FAILED`.
- [ ] Available budget in stay and rental car searches accurately deducts other selected components while excluding the component being searched or replaced.
- [ ] Unit and HTTP integration tests prove consistent tally calculations across all combinations of components (airfare only, stay only, rental only, airfare+stay, stay+car, airfare+car, and all three).
- [ ] Multi-user isolation tests verify that pricing calculations and tally responses are strictly scoped to the authenticated trip owner.

## Context

- **Phase/work packages:** Phase 5 — Planning, Budget, and Comparison; work package 5.1 (Implement canonical price calculation).
- **Authoritative sources:** [`../phases/README.md`](../phases/README.md), [`../phases/CONTINUATION.md`](../phases/CONTINUATION.md), and [`../phases/phase-5-planning-budget-and-comparison.md`](../phases/phase-5-planning-budget-and-comparison.md).
- **Hard dependencies:** Phase 4 must be complete (P04-T01 through P04-T04).
- **Downstream dependencies:** P05-T02 consumes canonical pricing for readiness revalidation and overage acknowledgment; P05-T03 and P05-T04 display server-calculated tallies in builder, comparison, and booking review.
- **Scope exclusions:** UI components, promotion readiness revalidation, budget overage modal dialog, snapshot schema extensions, simulated booking transactions, and Version 2 Events.

## Execution profile

- **Recommended:** Full 5-Step Pipeline
- **Confidence:** high
- **Rationale:** Establishes cross-cutting server-authoritative financial calculation contracts and response structures across Draft, Planned, and Alternative representations that downstream promotion, comparison, and booking depend on.
- **Reassessment triggers:** If pricing rules require modifying existing catalog fixture schemas or flight pricing models beyond the settled integer-cent contract, escalate before changing persistence contracts.
