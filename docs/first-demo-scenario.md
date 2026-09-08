# First demo: a weekend with a fixed arrival deadline

Status: fixture specification, verified by a standalone calculator and application
tests. The first running slice is described in the [README](../README.md).

## Request and presentation

> Plan a Boston-to-New York weekend for two, October 16–18, 2026. We need to
> be checked in by 5 pm Friday before an evening show. We want to stay in
> New York until at least 4 pm Sunday and be back in Boston by 11 pm.
> Keep transport, our room, and transfers under $1,200. Prefer a quiet room,
> then less transfer time; use price to break otherwise similar choices.

The initial UI supplies this as confirmed structured fields with an editable
example. Free-text interpretation follows in a later slice. The show is context
for the hotel deadline; we do not book it or assert feasibility of an unmodeled
hotel-to-theater journey.

All businesses, services, schedules, prices, buffers, and transfers below are
fictional demo data. They are not travel advice or live supplier information.

## Inventory and price units

The machine-readable source is [weekend-inventory.json](../design/weekend-inventory.json).
Use these identifiers in future database seeds. The example picker populates a
request; it must not pass a scenario key through the skill tree.

| Service | Direction | Departure → arrival | Fare per traveler | Seats |
| --- | --- | --- | ---: | ---: |
| RAIL-OUT | Friday Boston → NYC | 10:00 → 14:00 | $95 | 2 |
| AIR-OUT | Friday Boston → NYC | 13:00 → 14:10 | $65 | 6 |
| AIR-LATE | Friday Boston → NYC | 15:00 → 16:10 | $45 | 6 |
| RAIL-RETURN | Sunday NYC → Boston | 18:00 → 22:00 | $85 | 2 |
| AIR-RETURN | Sunday NYC → Boston | 19:00 → 20:10 | $65 | 6 |

Flight and rail legs are independent one-way products. Mixed-mode round trips
are allowed and included in the calculation. All air services use the same
fictional terminal in each city; all rail services use the same station.

| Hotel | Room per night | Rooms on each seeded night | Room characteristics |
| --- | ---: | ---: | --- |
| Central House | $220 | 3 | Compact double; no quiet-room guarantee |
| Garden Court | $290 | 1 | Courtyard king; guaranteed quiet-room allocation |
| Riverside Lodge | $180 | 4 | Spacious double; no quiet-room guarantee |

Each room accommodates two adults. Stay dates are check-in October 16,
check-out October 18: charge and reserve the nights of October 16 and 17.
Check-in begins at 15:00; checkout is 11:00 Sunday, with free luggage storage
and lounge access until the return transfer. Sunday departure means leaving
the property after retrieving luggage, not occupying the room past checkout.

All prices include fictional taxes and mandatory fees. Air fares include the
same cabin-baggage allowance; no checked bags are modeled. Store money as
integer cents. Do not multiply a room or whole-party transfer price by the
number of travelers. No meals, show tickets, or incidental local travel are
included; label this scope next to the total.

## Transfers and time boundaries

Use America/New_York as the business time zone. These fixture dates use UTC−04:00.
The calculator is deliberately limited to these dates and fixed offsets; future
Java validation must use zone-aware times rather than generalize that offset.

Trip timing begins and ends at a labeled Boston downtown meeting point, which
coincides with the rail station. Do not label it the traveler's home address.
Airport transfer from this meeting point costs $40 for the party and takes 30
minutes in each direction. Rail has no additional Boston transfer.

| NYC terminal ↔ hotel | Rail transfer / party | Air transfer / party |
| --- | --- | --- |
| Central House | 20 min / $20 each way | 75 min / $90 each way |
| Garden Court | 15 min / $20 each way | 65 min / $90 each way |
| Riverside Lodge | 60 min / $70 each way | 90 min / $110 each way |

Transfers are fixed-duration, on-demand simulated services, available throughout
the fixture travel periods. Each quoted vehicle fits the two-person party.
They have no finite inventory in the first slice; persist transfer line items
with the booking but reserve finite stock only for seats and hotel nights.
Do not claim a real taxi or shuttle reservation was made.

- Rail requires arrival at the station 20 minutes before departure and allows
  15 minutes after service arrival before onward travel.
- Air requires arrival at the terminal 90 minutes before departure and allows
  30 minutes after service arrival before onward travel.
- Hotel-ready time is the later of hotel arrival and 15:00 check-in. It must
  be at or before 17:00 Friday. No extra check-in processing time is modeled.
- Latest hotel departure is return departure minus NYC transfer and departure
  buffer. It must be at or after 16:00 Sunday.
- Boston completion is return arrival plus arrival buffer and Boston transfer.
  It must be at or before 23:00 Sunday.
- Boundary equality is allowed. An exact $1,200 total is within budget.

## Expected comparison

| Candidate | Transport for two | Two hotel nights | All transfers | Total | Friday hotel-ready |
| --- | ---: | ---: | ---: | ---: | --- |
| Rail both ways + Garden Court | $360 | $580 | $40 | **$980** | 15:00 |
| Rail both ways + Central House | $360 | $440 | $40 | **$840** | 15:00 |
| Rail both ways + Riverside Lodge | $360 | $360 | $140 | $860 | 15:15 |
| Early air both ways + Central House | $260 | $440 | $260 | $960 | 15:55 |
| Late air outbound + air return + Central House | $220 | $440 | $260 | $920 | **17:55 — infeasible** |

The intended recommendation is the $980 Garden Court trip: the guaranteed quiet
room and shorter transfers match the stated preference. The useful alternative
is the $840 Central House trip: save $140 while meeting every hard constraint.
The model explains that judgment using inventory facts. Java does not invent a
hidden comfort score or force a preselected recommendation by scenario name.

This is an expected behavior for the live demonstration, not a claim that the
model has already produced it. A different feasible selection is a product
quality issue to inspect against the request; an invalid selection is a hard
validation failure. Ordinary correctness tests must not depend on exact prose.

The cheaper Riverside room saves $80 on lodging versus Central but adds $100
in transfers. Early flights save $100 in fares versus rail but add $220 in
transfers with Central. The late flight lands at 16:10, yet the guest is not
hotel-ready until 17:55. Each explanation requires information from more than
one specialist.

For the recommended rail/Garden trip, leave the Boston meeting point at 09:40
Friday, arrive at the hotel at 14:30, and check in at 15:00. Leave the hotel at
17:25 Sunday and finish at the Boston meeting point at 22:15.

See [all 18 calculated combinations](calculated-combinations.md). Regenerate
them and check the anchor totals with `python scripts/verify-design.py`.
The calculator is development tooling; the eventual app must not require Python.

## Coordination requirements exposed by this scenario

1. Transport discovery must retain both viable travel modes when both are
   allowed. A rail-only preference should avoid unnecessary flight searches.
2. Hotel discovery must retain at least the quiet-room candidate and the
   lower-cost alternative. A cheapest-only shortlist loses the user's intent.
3. Shortlists must not reject a hotel or service merely because its own price
   is higher. The downstream combination can be better overall.
4. Local logistics takes candidate identifiers and evaluates transfers and
   complete-trip timing. It cannot independently choose the globally best
   hotel without knowing the transport combination.
5. The coordinator chooses among validated whole-trip candidates. It receives
   itemized facts and reason codes, not just free-text specialist summaries.
6. The application revalidates the returned identifiers and prices before
   storing a bookable proposal, and again inside the booking transaction.

This five-service, three-hotel universe has 18 combinations and fits comfortably
inside a small bounded design. Do not introduce aggressive shortlist pruning
in the first slice. When catalogs grow, define coverage and truncation semantics
before claiming that no feasible trip exists.

## Demonstration and acceptance branches

| Action | Expected deterministic outcome | Model behavior to inspect |
| --- | --- | --- |
| Plan default request | 12 feasible catalog combinations; minimum $840 | Recommend quiet Garden at $980 and compare Central at $840 |
| Change budget to $900 | Garden is unaffordable; Central at $840 remains feasible | Explain the quiet-room tradeoff without silently exceeding budget |
| Change budget to $800 | No feasible combination in this catalog | Explain the $40 minimum shortfall; do not relax the budget automatically |
| Allow rail only | Three feasible combinations, $840/$860/$980 | Use rail search; no flight search needed |
| Change hotel deadline to 14:00 | No room is ready before 15:00 check-in | Identify check-in as the blocker; do not invent early check-in |
| Accept rail/Garden | Reserve two seats on each rail leg and one room on each night atomically | No model involvement in confirming the transaction |
| Repeat acceptance with same idempotency key | Return original booking; no further stock change | Not applicable |
| Book a competing proposal after stock is consumed | Reject stale inventory without a partial booking | A subsequent plan must use current inventory |
| Restart | Preserve requirements, proposals, booking and depleted inventory | A new execution uses a new session, not replayed model text |

The calculator currently checks the default enumeration and hand-calculated
anchors. Application integration tests must later exercise transaction races,
price changes, validation failures, and restart persistence. A failed model or
tool call is an assessment failure, not evidence that the trip is infeasible.

Disruption recovery remains a later slice. The same alternative air inventory
can support it, but cancellation, refunds, preservation of existing reservations,
and atomic replacement need a separate specification before implementation.

## Next implementation-design step

Sketch the request/comparison/booked-trip workspace around the $980 versus $840
comparison. Then define Java DTOs and YAML specialist contracts with this
inventory as their worked example. The framework's matching
`agent-skills/loomspan-docs/references/skill-authoring/mental-model.md` and
`checklists/evaluate-a-skill-design.md` guide the planning boundaries; this
scenario does not introduce any new framework API requirement.
