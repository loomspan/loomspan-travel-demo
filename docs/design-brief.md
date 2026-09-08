# Wayfarer — travel demo design brief

Status: original design brief. The first running slice is implemented; see
the [README](../README.md) and [implementation notes](implementation-notes.md).
Wayfarer is a working name. The proposed product direction is a leisure travel
concierge with simulated inventory and persistent bookings.

The first scenario now has a [concrete inventory and walkthrough](first-demo-scenario.md),
[machine-readable fixtures](../design/weekend-inventory.json), and
[verified combination totals](calculated-combinations.md). The target comparison
is a $980 quiet-room trip versus an $840 budget alternative. Both have now been
produced by live application tests.

## Purpose

Show how Loomspan coordinates specialists to produce a coherent trip from
competing preferences and dependent decisions. A successful demonstration must
make the coordination visible in the resulting choices, not only in a trace.

Use the venue demo's standalone application pattern and retain the old travel
sample's selective searches and specialist boundaries. Build against the current
Loomspan framework revision, presently 1.0.0-beta.1; record the exact tested
commit during implementation.

## Proposed presenter story

1. Start with a Boston-to-New York weekend for two travelers, October 16–18,
   2026. They need to reach their hotel by 17:00 Friday before an evening show.
   Their hard transport, lodging, and transfer budget is $1,200; comfort is a
   preference. Show tickets and meals are outside this explicitly labeled total.
2. Review and confirm the structured requirements. An example button supplies
   the same request for repeatable presentations.
3. Plan the trip. Transport and lodging specialists investigate alternatives;
   local logistics evaluates travel between terminals and hotels and arrival
   feasibility. The coordinator recommends a complete combination and, when
   available, one materially different feasible alternative.
4. Compare door-to-door timing, itemized cost, and the reasons for the choices.
   Seed inventory so the cheapest transport item does not necessarily produce
   the cheapest or most suitable complete trip. Exact fixtures and expected
   totals must be calculated before implementation is accepted.
5. Explicitly book an itinerary. Reopen the application to show that the trip,
   proposal, and simulated reservations persist and availability has changed.
6. In a later slice, simulate cancellation of the outbound service. Propose a
   repair with a cost and schedule comparison, preserving unaffected bookings
   where feasible. Apply the replacement only after explicit acceptance.

## Skill responsibilities

| Capability | Proposed responsibility |
| --- | --- |
| Request interpretation | Extract requirements and identify missing information for user confirmation |
| Trip coordinator | Plan specialist work, reconcile shortlists, and recommend complete combinations |
| Transport specialist | Choose relevant flight/train searches and return suitable outbound/return alternatives |
| Stay specialist | Compare hotels and investigate loyalty benefits when applicable |
| Local logistics specialist | Evaluate transfers and timing for candidate transport/hotel combinations |
| Java capabilities | Query authoritative inventory, calculate prices, and validate feasibility |

Use explicit planning where decomposition and selective child use justify it.
Do not make every specialist a planner merely to increase tree depth. Final
planning/direct-execution choices and contracts belong in the next design step.

Independent discovery may overlap. Transfer and whole-trip checks depend on
candidate results. Pass typed business inputs and results across boundaries,
including inventory identifiers; do not reconstruct bookable facts from prose.
Keep candidate sets bounded so combination checking remains understandable.

Models select and explain. Application services validate identifiers, dates,
party size, timing, availability, and all monetary totals. A model's declaration
that a trip is feasible is not sufficient to expose it as bookable.

## Application boundary

- Spring Boot backend, React/TypeScript frontend, file-backed H2, and Flyway.
- One application JAR serves the built UI; model access is the only required
  external service. Loomspan Console is optional developer tooling.
- One corridor, one currency, round trips, a small seeded calendar, a few
  hotels, and a bounded transfer catalog. All supplier inventory is simulated.
- Persist confirmed requests, requirement versions, proposal snapshots,
  inventory, bookings, and execution session references.
- Book through an explicit application action that revalidates price and
  availability and atomically reserves local inventory. Handle repeated clicks
  and competing bookings without duplicate or partial reservations.
- Present trip planning, proposal comparison, and booking in one workspace.
  Keep framework diagnostics in an optional developer view or Console.
- Exclude real supplier integrations, payments, messaging, multi-city travel,
  and general inventory administration from the initial scope.

## Delivery sequence

### 1. Define a testable demonstration

Specify seed inventory and calculate expected complete-trip combinations.
Include a tempting option that fails arrival timing, a viable budget option,
and a comfort tradeoff. Sketch the request, comparison, and booked-trip states.
Define typed specialist contracts and application validation ownership.

### 2. Build the first complete slice

Implement a confirmed structured request → Loomspan planning → validated
comparison → explicit simulated booking → persistence across restart.
Start with structured entry and an example request; free-text interpretation
can follow once the planning and booking path works.

Acceptance checks:

- Searches use database inventory and actual request fields, not scenario keys
  passed through every skill.
- Displayed timing and prices come from validated inventory and calculations.
- Actual nested specialist execution is inspectable through a session reference.
- An impossible request yields an actionable explanation without a bookable
  fabricated itinerary; an alternative is optional when only one trip is feasible.
- Stale proposals and insufficient availability cannot create a booking.
- Booking persists after restart and affects a subsequent request.
- Ordinary tests need no model access; opt-in live checks establish that the
  intended skill coordination works with the documented model configuration.

### 3. Add interpretation and preference variations

Support free-text intake with confirmation, budget/comfort changes, proposal
comparison across requirement versions, and a selective loyalty branch.

### 4. Add disruption recovery

Persist an explicit simulated service cancellation and invoke repair planning
against the existing booking. Specify cancellation/refund and replacement
rules before implementing writes. Keep the original commitment and history
visible until an accepted replacement is applied according to those rules.

## Next design decisions

1. Finalize the seeded schedules, hotel nights, transfer rules, prices, and
   expected recommendation/alternative for the presenter story.
2. Settle the workspace layout using a lightweight sketch.
3. Define bounded specialist inputs/outputs, planning boundaries, and the
   authoritative whole-trip validation contract.

These decisions should precede substantial application scaffolding. The first
slice should be runnable and reviewable before adding disruption recovery.
