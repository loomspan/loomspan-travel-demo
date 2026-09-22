# P05-T04 — Deliver Multi-Alternative Planned Itinerary Comparison and Booking Selection Experience

## Outcome

Users can select two or three Planned alternatives for a Trip, compare them side-by-side on desktop or stacked on mobile across price, budget position, flights, stays, and cars, visually distinguish missing optional components from zero-cost items, and select a preferred Planned alternative to enter simulated booking review.

## Requirements

- Multi-alternative Planned selection:
  - Provide a comparison trigger in the Alternatives section of the Trip Workspace when at least two Planned alternatives exist for the trip.
  - Allow users to select 2 or 3 Planned alternatives using checkboxes or a comparison selection tray.
  - Enforce the 2-to-3 alternative constraint: disable comparison when fewer than 2 are selected; prevent selecting more than 3 alternatives with an accessible explanatory notification.
- Responsive side-by-side and stacked comparison layouts:
  - **Desktop layout:** render selected Planned alternatives in responsive side-by-side comparison columns.
  - **Mobile layout:** render stacked comparison cards with a persistent itinerary switcher/tab bar allowing users to quickly toggle between compared alternatives without losing scroll context.
- Comprehensive attribute comparison matrix:
  - **Financial summary:** authoritative grand total, budget position (remaining budget or overage highlighted with distinct color badges).
  - **Airfare:** outbound and return flight numbers, carrier name, stops count, layover airports/times, departure/arrival local times with timezone labels, total duration, and complete-party fare breakdown.
  - **Stay:** property name, accommodation category (`HOTEL`, `BED_AND_BREAKFAST`, `VACATION_RENTAL`), unit name, room count, distance to city center, complete stay total, and nightly price breakdown.
  - **Rental car:** vehicle class, pickup and return airport local date/times, consecutive 24-hour cycle count, total price including taxes/fees.
- Visual distinction for missing components:
  - Clearly distinguish optional components that were not selected (e.g. "No rental car selected" with muted styling and em-dash) from zero-cost or included items, preventing user confusion.
- Candidate selection for Booking Review:
  - Each compared Planned alternative (and standalone Planned alternative cards) includes a primary "Select for Booking Review" action.
  - Selecting an alternative transitions to the Booking Review view/screen displaying:
    - Selected trip destination, dates, and traveler count.
    - All included components with full descriptive snapshot details.
    - Itemized component totals and final booking grand total.
    - Prominent disclosure stating: *"This is a simulated booking with fictional inventory. No real payment, billing address, or external reservation is required."*
    - Non-interactive / disabled action for "Confirm Booking" clearly labeled as ready for Phase 6 simulated booking implementation.
  - Provide an easy navigation path to return from Booking Review back to comparison or the Trip Workspace.
- Accessibility and responsiveness:
  - The comparison view uses semantic table headers or accessible ARIA roles (`grid`, `columnheader`, `rowheader`).
  - The mobile itinerary switcher is fully keyboard operable and announces active alternative changes to screen readers.
  - Color contrast meets WCAG AA standards for within-budget and over-budget badges.

## Acceptance criteria

- [ ] Users can select 2 or 3 Planned alternatives for a Trip and launch the comparison view; selecting fewer than 2 or more than 3 is cleanly constrained.
- [ ] Desktop viewport renders side-by-side comparison columns; mobile viewport renders a stacked layout with a persistent alternative switcher.
- [ ] Comparison displays grand total, budget position (remaining/overage), flight stops/duration/times, stay type/location/rooms, and car class/times.
- [ ] Missing optional components are visually and semantically distinguished from zero-cost selections.
- [ ] Clicking "Select for Booking Review" on any compared alternative opens the booking review summary with all component details, totals, and the fictional booking disclosure.
- [ ] Keyboard navigation, screen-reader announcements, and responsive viewport behavior are thoroughly verified in Vitest tests.

## Context

- **Phase/work packages:** Phase 5 — Planning, Budget, and Comparison; work package 5.4 (Compare Planned itineraries).
- **Authoritative sources:** [`../phases/README.md`](../phases/README.md), [`../phases/CONTINUATION.md`](../phases/CONTINUATION.md), and [`../phases/phase-5-planning-budget-and-comparison.md`](../phases/phase-5-planning-budget-and-comparison.md).
- **Hard dependencies:** P05-T01, P05-T02, and P05-T03 must be complete.
- **Downstream dependencies:** Phase 6 (Simulated Booking and Cancellation) attaches transactional inventory reservation and confirmation records to the Booking Review screen established here.
- **Scope exclusions:** Saved comparison sets, sharing/exporting comparisons, Phase 6 transactional booking and cancellation execution, and Version 2 Events.

## Execution profile

- **Recommended:** Full 5-Step Pipeline
- **Confidence:** high
- **Rationale:** Integrates multi-alternative data binding, responsive desktop/mobile matrix layouts, accessible focus/tab management, and the transitional Booking Review boundary leading into Phase 6.
- **Reassessment triggers:** If comparison requires server-side saved comparison sets, halt and escalate; the roadmap explicitly marks saved comparison sets as [FUTURE] and restricts comparison to in-memory client selection of 2–3 Planned alternatives.
