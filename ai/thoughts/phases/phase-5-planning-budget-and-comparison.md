# Phase 5 — Planning, Budget, and Comparison

## Outcome

Users understand total cost while they build, preserve valid alternatives as Planned snapshots, compare those alternatives, and deliberately choose one for simulated booking.

## Work packages

### 5.1 Implement canonical price calculation

- Calculate airfare, stay, rental, and grand totals on the server.
- Accept a zero overall budget, reject negative values, and enforce a documented maximum safely representable by the application's integer-cent money type.
- Show remaining overall budget or overage without blocking progress.
- Calculate the available budget for a component search as the overall budget minus authoritative totals for already selected components other than the component being searched or replaced.
- When no overall budget has been supplied yet, omit budget-fit ranking and remaining/overage presentation until the user supplies one.
- Recalculate from authoritative catalog data before Planned promotion and booking.

### 5.2 Validate readiness

- Require destination, dates, traveler ages, budget, and at least one selected reservable component.
- Reject stale, unavailable, sold-out, or structurally invalid selections.
- Require acknowledgment of a budget-overage warning before promotion to Planned.
- Invalidate an acknowledgment whenever an affected selection, traveler, date, price, or budget setting changes.
- Surface every blocking issue together with an action that takes the user to the relevant component.

### 5.3 Save stable Planned snapshots

- Copy resolved component and price details into an immutable, auditable snapshot.
- Preserve enough supplier, schedule, property, and rental description that future catalog edits cannot rewrite history.
- Retain links to inventory identifiers for later booking revalidation.
- Provide Duplicate to Draft rather than editing the snapshot.

### 5.4 Compare Planned itineraries

- Let users choose multiple Planned alternatives from the same trip.
- Limit one comparison to three Planned alternatives.
- Compare total, budget position, flight stops/duration/times, stay/type/location, and car.
- Visually distinguish missing optional components from zero-cost components.
- Let users select one comparison candidate for booking review.
- Use columns on desktop and a stacked comparison with a persistent itinerary selector on mobile.

## Exit criteria

- The tally remains consistent across builder, profile, comparison, and booking review.
- Available-budget and over-budget calculations are covered for adding and replacing each component type.
- Planned snapshots do not change when Drafts, preferences, or catalog display data change.
- Readiness errors and warnings are actionable and accessible.

## Annotations

- **[FUTURE]** Saved comparison sets, sharing, printable proposals, and collaborative selection.
