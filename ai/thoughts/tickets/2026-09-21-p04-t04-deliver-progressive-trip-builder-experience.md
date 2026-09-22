# P04-T04 — Deliver Progressive Trip-Builder and Component Selection Experience

## Outcome

Authenticated users can initiate trips through Plan Trip, Airfare, or Stay entry points, progressively configure only the components they choose, monitor a real-time persistent itinerary summary and budget tally without exposing unopened component forms, search and select flights, stays, and rental cars, and safely remove selections through explicit confirmation dialogs.

## Requirements

- Expose the three authenticated entry points: **Plan Trip**, **Airfare**, and **Stay** on the authenticated workspace:
  - All three entry points collect destination, start/end dates (March 1–31, 2027, 1–14 nights), and traveler count (1–8) before creating the Trip and initial Draft.
  - **Plan Trip:** Creates Trip and Draft, then presents the progressive builder with Airfare and Stay slots ready for configuration. The Rental Car slot remains hidden until the user explicitly requests it.
  - **Airfare:** Creates Trip and Draft, then opens directly into the Airfare search and selection flow. Stay and Rental Car slots remain unopened until explicitly requested.
  - **Stay:** Collects the mandatory accommodation type preference (`HOTEL`, `BED_AND_BREAKFAST`, or `VACATION_RENTAL`) during the initial flow, creates Trip and Draft, then opens directly into Stay search. Airfare and Rental Car slots remain unopened until explicitly requested.
- Maintain a progressive builder workspace:
  - Keep optional components hidden: the Rental Car section is never shown upfront and appears only after clicking an explicit "Add a car" action.
  - Component slots (Airfare, Stay, Rental Car) display their current status:
    - Empty slot: displays an explicit "Add [component]" action.
    - Selected slot: displays full item details (carrier/times/stops for flights; property name/unit/rooms for stay; vehicle class/times for car), complete taxes-and-fees-inclusive price, a "Change" button, and a "Remove" button.
  - Maintain a persistent itinerary summary and budget tally:
    - Display individual component prices, authoritative grand total of selected items, the overall trip budget (if set), and remaining budget or overage indicator.
    - An absent budget suppresses budget-fit ranking and remaining/overage presentation without blocking progress.
- Implement component search and selection interfaces:
  - **Airfare Search:**
    - Filter by "Direct flights only" checkbox.
    - Sort override dropdown: Lowest price, Shortest duration, Earliest departure, Fewest stops.
    - List of round-trip combinations displaying carrier, flight numbers, stops, layover times/airports, departure/arrival in local time with timezone info, total duration, and complete-party price.
    - Action to select a combination, saving it to the Draft and updating the persistent tally.
  - **Stay Search:**
    - Accommodation type preference selector (`HOTEL`, `BED_AND_BREAKFAST`, `VACATION_RENTAL`).
    - Sort override dropdown: Lowest price, Highest rating, Nearest city center.
    - Displays automatic room count, guest rating, distance to city center, complete-stay total price, and transparent nightly/room breakdown.
    - Action to select a stay, saving it to the Draft and updating the persistent tally.
  - **Rental Car Search:**
    - Inputs for local pickup and return date/times within the Trip interval at the destination airport.
    - Driver age rule: If no traveler is 25 or older, disable selection and display an explanation: *"Rental cars require at least one traveler aged 25 or older. Please add traveler ages in Trip Details to select a car."*
    - Display available Economy, Standard, and SUV options with consecutive 24-hour cycle pricing and complete taxes/fees totals.
    - Action to select a car, saving it to the Draft and updating the persistent tally.
- Implement safe component removal:
  - Removing an already saved component requires an explicit confirmation modal naming the component and price being discarded.
  - Empty slots or unselected components discard without confirmation.
- State management, accessibility, and responsiveness:
  - Provide clear `Saving...`, `Saved`, and error status announcements for all draft mutations.
  - Handle version conflicts (409 `VERSION_CONFLICT`) gracefully by preserving user input and offering an actionable reload button.
  - Ensure full keyboard navigation, visible focus indicators, `aria-modal` dialogs with focus trapping and Escape key dismissal, and accessible labels on all form controls.
  - Desktop and mobile layouts preserve identical functionality without horizontal page scrolling.
- Do not introduce Phase 5 multi-alternative comparison columns, Phase 6 booking review/cancellation buttons, or Version 2 Events in this ticket.

## Acceptance criteria

- [x] Authenticated users can start trip creation from "Plan Trip", "Airfare", or "Stay" entry actions.
- [x] "Stay" flow collects accommodation type preference upfront; "Airfare" flow launches directly into flight search; "Plan Trip" opens builder with flight and stay slots visible.
- [x] Optional car component remains hidden until the user explicitly clicks "Add a car".
- [x] Persistent summary displays accurate component totals, total itinerary cost, and remaining budget or overage when a budget is defined.
- [x] Airfare search interface allows filtering by direct flights, sorting by price/duration/departure/stops, and selecting a combination updates the draft.
- [x] Stay search interface displays calculated room count, complete stay price, rating, and city center distance, allows sorting, and selecting a stay updates the draft.
- [x] Rental car search interface validates pickup/return dates, shows the 25+ age requirement explanation when ineligible, and selecting a car updates the draft.
- [x] Removing an active selection opens a confirmation modal naming the discarded item; confirming removes it and updates the persistent tally.
- [x] Concurrency conflicts explain that newer server data exists and provide a reload button without losing context.
- [x] All interactive dialogs trap focus, close on Escape, return focus to the trigger, and provide screen-reader announcements.
- [x] Frontend tests (Vitest) cover the 3 entry flows, progressive disclosure, search/select/remove interactions, confirmation dialogs, and budget tally updates.

## Context

- **Phase/work package:** Phase 4 — Component Selection; user-facing integration of work packages 4.1–4.4.
- **Authoritative sources:** [`../phases/README.md`](../phases/README.md), [`../phases/CONTINUATION.md`](../phases/CONTINUATION.md), and [`../phases/phase-4-component-selection.md`](../phases/phase-4-component-selection.md).
- **Required architecture:** [`../architecture/2026-09-17-p00-t02-detour-replacement-architecture.md`](../architecture/2026-09-17-p00-t02-detour-replacement-architecture.md).
- **Hard dependencies:** P04-T01 (Airfare API), P04-T02 (Stay API), and P04-T03 (Rental Car API) must be complete.
- **Downstream dependencies:** Phase 5 consumes this progressive builder and component selections for comparison and Planned promotion.
- **Scope exclusions:** Multi-alternative comparison columns, booking confirmation/checkout, cancellation workflows, custom Trip nicknames, and Version 2 Events.
- This ticket is sized for GPT-5.6 Terra as one end-to-end frontend integration after the backend search and selection APIs stabilize, avoiding repeated UI rewrites.

## Execution profile

- **Recommended:** Full 5-Step Pipeline
- **Confidence:** high
- **Rationale:** Integrates three entry flows, progressive disclosure, multiple modal dialogs, real-time pricing and budget tallies, and accessibility across desktop and mobile layouts.
- **Reassessment triggers:** If backend search and mutation contracts from P04-T01 through P04-T03 differ from the anticipated shape, align API client types on the full route before writing UI components.
