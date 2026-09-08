# Workspace and skill contracts — first slice

Status: original implementation proposal. See [implementation notes](implementation-notes.md)
for the running slice and compact persisted handoffs that replace the proposed
DTOs below. The separate interactive sketch uses fixture data locally and has
no API, database, model execution, or persistent booking integration.
Read alongside [the first scenario](first-demo-scenario.md).

## Workspace

One trip workspace contains a request, its current comparison, and the eventual
booking. A quiet sidebar identifies the trip. No dashboard is needed for the
first slice. Begin at request entry in the real app; the design sketch initially
opens the comparison so the central product decision is visible immediately.

### Request

Show the route, travel dates, two travelers, one room, budget, permitted travel
modes, hotel-ready deadline, earliest Sunday hotel departure, latest Boston
return, and preference ordering. Use labeled structured fields and a Load example
action. The first release supports the seeded corridor/dates and two adults in
one room; reject unsupported combinations explicitly. Do not silently fall back
to fixture dates or invent availability. Some fixed fields are read-only in the
sketch; final form validation must explain the actual supported boundaries.

Confirm and compare saves an immutable requirement revision and starts an
assessment. Separate save errors from planning failures. While running, show
an honest indeterminate state and elapsed time; stage labels may appear only
when observed. Never animate fabricated specialists or percentages. The request
remains readable. Editing creates a new revision; a result for an older revision
cannot become current or bookable.

### Comparison

Show a recommended trip and at most one distinct alternative. Each contains:

- Hotel and room characteristics, transport modes, and total for the party.
- Transport, room-night, and transfer subtotals with unambiguous units.
- Friday hotel-ready time and transfer duration; expanded itinerary includes
  the full outbound and return schedule.
- A concise reason connected to the user's priorities, including concessions.
- Review trip, leading to explicit booking confirmation.

Keep total scope next to the comparison: two adults, one room, two nights,
transport and transfers; excludes meals, show tickets, and incidental travel.
Show Eastern time and the Boston meeting-point boundary. A cheapest transport
fare is never presented as the total trip cost.

Why these trips? expands fact-backed comparisons and exclusions. How the trip
was assessed is separate, optional developer detail: actual session reference,
observed specialist execution, and validation summary. Do not use model-reported
`toolsUsed` as execution truth. Console remains optional, and its diagnostic
REST types must not become the business UI API.

If one feasible option exists, render one card without an empty alternative.
If none exists, show the deterministic blocker and an Edit request action.
For an $800 budget, a $840 trip may be explained as $40 over budget but must not
receive a booking action. A tool timeout is Assessment failed with Retry, never
No trips available. A stale-price or sold-out booking response preserves the
old quote for reference and offers a new assessment without silently charging
more or substituting an itinerary.

### Review and booked trip

Before confirmation, repeat selected hotel/room, dates, party, outbound/return
services, total, and simulated-inventory label. A single explicit confirmation
performs the transaction. Disable duplicate submission while pending, but rely
on server idempotency for correctness.

After success show the booking reference and a chronological itinerary, with
the checked-in room interval distinguished from luggage storage after checkout.
Show reserved seats and hotel nights; transfers are priced line items without
finite reservations. A booked trip is read-only in this slice. Do not expose
cancellation or repair controls before those operations exist.

The interactive sketch intentionally does not persist state. Its comparisons
illustrate the two presenter choices, not a substitute catalog search engine.

## Skill tree and planning decisions

```text
Application → planTrip                       YAML planner
               ├─ planTransport              YAML planner
               │    ├─ searchRailServices    Java
               │    └─ searchFlightServices  Java
               ├─ assessStay                 YAML direct specialist
               │    └─ searchHotels          Java
               └─ assessTripLogistics        YAML direct specialist
                    └─ evaluateTripOptions   Java

Application → validate/store proposal → explicit transactional booking
```

Names are proposed exact registered identities. There are no booking tools in
the model's allowed surface. Java annotations own Java skills; no YAML wrappers.

`planTrip` merits planning because it coordinates independent discovery and a
dependent evaluation, then selects whole-trip choices. `planTransport` merits
planning because it chooses relevant search branches, including selective rail
or air use, before synthesizing travel options. `assessStay` is a direct
specialist: one catalog mission plus interpretation of room/preference tradeoffs
does not need another explicit task plan. `assessTripLogistics` is direct: call
the deterministic evaluator, identify material timing/cost interactions, and
return checked options and explainable blockers.

Transport and stay discovery may form an explicitly grouped independent unit.
Logistics depends on both. Inside transport, flight and rail searches can be
grouped when both are relevant; a rail-only request must not call air search.
All planning leaves are reads against an immutable catalog snapshot, so these
groups have no competing inventory writes. Group metadata is not proof of
observed overlap; demonstrate overlap from actual execution intervals.

Proposed initial `max_steps`: 6 for the root and 4 for transport, including the
final synthesis step at each level. These bound the intended three-task root
and two-search transport paths with limited room for justified extra work.
Set `planning_mode: true` and `concurrency: true` only on these planners.
Actual model, timeout, session quota, and live latency tuning remains part of
implementation verification; no model compatibility has been established yet.

## Common types and ownership

These are business DTO definitions, not claims about new framework APIs.
Use closed records/objects; validate every cross-field business rule in Java.

| Type | Required fields |
| --- | --- |
| `RequestRef` | `requestId: string`, `revision: integer`, `catalogSnapshotId: string` |
| `ConfirmedTripRequest` | `ref: RequestRef`, `originId: string`, `destinationId: string`, `outboundDate: string`, `returnDate: string`, `partySize: integer`, `roomCount: integer`, `currency: string`, `budgetCents: integer`, `hotelReadyBy: string`, `leaveHotelNoEarlierThan: string`, `returnToOriginBy: string`, `allowedModes: array<string>`, `priorities: array<string>` |
| `ServiceOption` | `serviceId: string`, `mode: string`, `direction: string`, `departsAt: string`, `arrivesAt: string`, `terminalFromId: string`, `terminalToId: string`, `farePerPersonCents: integer`, `availableSeats: integer` |
| `HotelOption` | `hotelId: string`, `roomTypeId: string`, `nightlyRoomCents: integer`, `roomCapacity: integer`, `minimumRoomsAvailable: integer`, `checkInAt: string`, `quietRoomGuaranteed: boolean`, `roomDescription: string` |
| `TripSelection` | `outboundServiceId: string`, `returnServiceId: string`, `hotelId: string`, `roomTypeId: string` |
| `CheckedTrip` | `selection: TripSelection`, `transportCents: integer`, `lodgingCents: integer`, `transferCents: integer`, `totalCents: integer`, `startAt: string`, `hotelArrivalAt: string`, `hotelReadyAt: string`, `leaveHotelAt: string`, `returnToOriginAt: string`, `totalTransferMinutes: integer`, `quietRoomGuaranteed: boolean`, `violations: array<Violation>` |
| `Violation` | `code: string`, `subjectId: string`, `message: string` |
| `CatalogCoverage` | `complete: boolean`, `matchedCount: integer`, `returnedCount: integer` |

All timestamp strings are offset-bearing ISO-8601 and interpreted against the
configured business zone. `format` in a Loomspan output schema is guidance;
Java enforces actual date/time parsing, ordering, and units. Money is integer
USD cents throughout. Allowed mode strings are `RAIL` and `FLIGHT`; priority
strings are `QUIET_ROOM`, `SHORT_TRANSFERS`, and `LOWEST_TOTAL`, in requested
order. Reject unknown priorities instead of silently treating them as lowest
price. Duplicate priorities and duplicate IDs are invalid.

The application owns the request revision, assessment ID, catalog snapshot,
timestamps, current caller identity, and execution session linkage. Create and
persist the catalog snapshot before invocation, in a short transaction; it
contains the rules, prices, dated stock, and request-relevant inventory for one
assessment. Do not hold a database transaction open during model calls.

Pass the request and its reference explicitly at each relevant boundary. Java
leaves load authoritative request values using `RequestRef`, not copied model
prices or deadlines. The reference is a lookup key, not authorization. Apply
normal caller/resource access checks and finally reject any result not matching
the application-started request revision and snapshot. Do not accept trusted
identity from tool arguments or introduce ambient framework state.

## Capability contracts

### Entry: `planTrip`

Input: `{ request: ConfirmedTripRequest }`.

Output: `{ ref: RequestRef, status: string, recommended: TripChoice | null,
alternative: TripChoice | null, explanation: string, blockers: array<Violation> }`.

`TripChoice` is `{ selection: TripSelection, rationale: string }`. The root does
not echo or calculate the authoritative quote. The application hydrates the
displayed trip from validated selections and the original snapshot.

Status is `OPTIONS` or `NO_FEASIBLE_TRIP`. All root fields are required; the two
choice fields are explicitly nullable. Java enforces status-dependent rules:
OPTIONS requires a recommended feasible selection, an optional distinct feasible
alternative, and no global blockers. NO_FEASIBLE_TRIP requires null choices and
at least one verified blocker after complete evaluation. Unsupported output or
an incomplete search is assessment failure, not an additional model status.

### `planTransport`

Input: `{ request: ConfirmedTripRequest }`.

Output: `{ ref: RequestRef, outbound: array<ServiceOption>,
returnOptions: array<ServiceOption>, coverage: CatalogCoverage,
considerations: array<string> }`.

Java searches take `{ ref: RequestRef }` and return the same direction-separated
service arrays and coverage for their own mode. Each resolves route, dates,
party, and allowed modes from the saved request. Mode is fixed by the skill's
identity. A forbidden-mode call is rejected. Catalog results may include a late
arrival so logistics can explain the rejection. Exclude cancelled services or
insufficient seat stock deterministically, with catalog counts scoped to these
eligibility rules. Do not prune higher fares purely on price.

The specialist retains all eligible returned service IDs in the first slice,
merges coverage across required mode searches, and explains fare/duration
tradeoffs. The application checks coverage independently against the snapshot;
it does not trust model assertions of completeness.

### `assessStay`

Input: `{ request: ConfirmedTripRequest }`.

Output: `{ ref: RequestRef, hotels: array<HotelOption>,
coverage: CatalogCoverage, considerations: array<string> }`.

`searchHotels` takes `{ ref: RequestRef }`, returns hotels plus coverage, and
checks destination, both room nights, occupancy, and available stock. Preserve
all eligible hotels here. Do not apply the entire trip budget independently to
each specialist or omit the quiet-room choice because its nightly rate is
higher. Loyalty lookup is deliberately absent until the later loyalty slice.

### `assessTripLogistics`

Input: `{ request: ConfirmedTripRequest, outboundServiceIds: array<string>,
returnServiceIds: array<string>, roomOptions: array<RoomRef> }`, where `RoomRef`
contains `hotelId` and `roomTypeId`.

Output: `{ ref: RequestRef, trips: array<CheckedTrip>,
coverage: CatalogCoverage, observations: array<string>,
catalogBlockers: array<Violation> }`.

`evaluateTripOptions` takes `{ ref: RequestRef, outboundServiceIds: array<string>,
returnServiceIds: array<string>, roomOptions: array<RoomRef> }`. It resolves every
ID from the snapshot and calculates the Cartesian product with buffers,
check-in time, date/occupancy rules, and transfer prices. It returns all checked
trips, including infeasible candidates with stable violations. Preserve those
Java facts; the specialist adds concise observations about interactions.

Before calculation, Java verifies that candidate IDs exactly cover the eligible
snapshot inventory for the request's allowed modes and hotels. Missing IDs,
extra IDs, wrong direction, foreign snapshot references, and duplicates reject
the evaluation call. They must not produce a complete no-feasible result.
Empty inventory can legitimately yield an empty product, with a verified
`NO_OUTBOUND`, `NO_RETURN`, or `NO_ROOM` diagnostic returned separately as a
required `catalogBlockers: array<Violation>` on both evaluator and logistics
results. The main success fixture has an empty catalogBlockers array.

Cap the initial evaluator at 4 outbound services, 4 return services, 4 room
options (64 combinations). Overflow produces `CATALOG_LIMIT_EXCEEDED` and
assessment failure with explicit incomplete coverage; never truncate and claim
global infeasibility. Current inventory is 3 × 2 × 3 = 18 combinations.

Violation codes include `HOTEL_DEADLINE`, `EARLY_RETURN_DEPARTURE`,
`LATE_ORIGIN_RETURN`, and `OVER_BUDGET`. A budget-shortfall explanation uses the
least costly trip satisfying all non-budget hard constraints, not the cheapest
invalid late arrival. At $800 that valid lower bound is $840.

## Evidence and validation boundaries

Proposed evidence annotations, attached only to immediate root output fields
of each YAML skill:

| Skill / output field | Direct-child evidence |
| --- | --- |
| planTrip / recommended, alternative, status, explanation, blockers | `planTransport and assessStay and assessTripLogistics` |
| planTransport / outbound, returnOptions, coverage, considerations | `searchRailServices or searchFlightServices` |
| assessStay / hotels, coverage, considerations | `searchHotels` |
| assessTripLogistics / trips, coverage, observations, catalogBlockers | `evaluateTripOptions` |

The OR expression preserves selective rail-only/flight-only paths. It does not
prove that both allowed modes were searched when required; enforce coverage
through the catalog and application checks above. Evidence proves successful
direct-child execution, not exact ID lineage, factual truth, order, or amounts.
Root contracts name specialists, never their private Java children.

Use Loomspan-supported closed output objects, string enums, required lists,
explicit nullable choices, and arrays with object items. Do not use `oneOf`,
conditional schemas, or schema descriptions as business enforcement. Cardinality,
unique selections, monotonic times, ID lineage, money, and status relationships
remain Java checks. Final implementation must test the actual reflected DTO and
YAML schema shapes against the selected framework version.

## Assessment and booking application boundary

Proposed REST responsibilities:

| Operation | Behavior |
| --- | --- |
| `POST /api/trips` | Validate and save confirmed requirements; return trip and revision |
| `POST /api/trips/{id}/assessments` | Require current revision, snapshot catalog, queue model assessment; return 202 with assessment ID |
| `GET /api/assessments/{id}` | Return app-owned status QUEUED/RUNNING/SUCCEEDED/FAILED; completed result includes validated proposal or infeasibility |
| `POST /api/trips/{id}/bookings` | Accept proposal ID, choice ID, and idempotency key; revalidate and reserve atomically |
| `GET /api/trips/{id}` | Restore requirements, proposals, current assessment state, and booking after refresh |

Use a bounded application executor for assessments and poll the business status
endpoint initially. The app owns asynchronous job persistence; Loomspan does
not become a durable job queue. On process startup, mark interrupted QUEUED or
RUNNING assessments failed with a retryable interruption reason. Do not replay
them automatically or claim their model work resumes after a restart.

After model success, recalculate from the original snapshot, validate the root
choice(s), and store the proposal and immutable price/timing line items. A
requirement revision race leaves a historical result that cannot be accepted.
At booking, compare current stock, prices, and all timing/transfer rules to the
accepted quote under transaction control; reject material changes and request
reassessment. Lock finite inventory in a stable order, recheck current trip
state, and update both service rows and both hotel-night rows atomically.

Enforce one booking per trip in the database. Scope idempotency to the trip and
caller; the same key and payload returns the original result, while a reused
key with a different payload conflicts. A retry after successful booking must
return that booking before applying stale-stock rejection. Do not hold model
execution inside this transaction. Persist session IDs for diagnostics without
coupling business retention to Console trace retention.

## Implementation checks

- Ordinary Java tests exercise fixture totals, mixed-mode trips, both hotel
  nights, exact deadline/budget boundaries, invalid/omitted IDs, coverage gaps,
  and all nullable/status relationships.
- Integration tests exercise competing bookings, quote changes, duplicate
  acceptance, revision races, and recovery of persisted trip/job state.
- Live tests establish the root/transport planning boundaries, selective
  rail-only search, actual allowed overlap, and coherent quiet-versus-budget
  choices. Exact model wording and arbitrary plan IDs are not acceptance criteria.
- UI checks cover one/two/no choices, a failed assessment, pending confirmation,
  a stale quote, and the read-only booked state at desktop and narrow widths.

Framework references consulted in the matching source checkout:
`agent-skills/loomspan-docs/references/skill-authoring/{mental-model,
input-contracts,output-contracts,evidence-contracts,planning-concurrency}.md`.
The Java API integration and final YAML declarations should be reviewed against
the matching java-api knowledge set when implementation begins.
