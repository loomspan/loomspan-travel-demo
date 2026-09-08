# First running slice: implementation decisions

The application uses Spring Boot 4.1, React 19, TypeScript, Vite, Flyway and
file-backed H2. It consumes Loomspan 1.0.0-beta.1 through its supported API.
The original design documents describe the intent; this document records
implementation refinements and is authoritative where their proposed DTOs differ.

## Persistence and transactions

Spring JDBC replaces the initially suggested JPA layer. The small schema benefits
from explicit row locks and SQL transactions. Requests have immutable revisions;
each assessment stores an immutable snapshot of request, inventory, and rules.
No transaction remains open during a model call.

Each Java catalog search records an immutable receipt under its assessment ID
and kind (RAIL, FLIGHT, HOTELS). These are application business records, not
framework mission state or an internal Loomspan API. Independent searches write
separate receipt keys; a short assessment-row lock makes repeated writes
idempotent and prevents writes after terminal status. These are not reservations.

The logistics evaluator requires receipts for every requested travel mode and
hotels, verifies coverage against the snapshot, computes all combinations, and
records EVALUATION. Missing searches fail instead of becoming infeasibility.
Before storing a proposal, Java requires that evaluation receipt and recomputes
its contents from the original snapshot. This also protects the application
when a model mistakes an ordinary Java tool error for a valid business result.

Booking locks the trip and inventory in a consistent order, recalculates the
chosen option against current inventory, rejects a changed quote, and reserves
both transport legs and both hotel nights atomically. Unique booking-per-trip
and idempotency checks protect repeat acceptance. Transfers are persisted quote
line items without finite reservation stock.

## Compact skill handoffs

The implemented graph retains the planned root, nested transport planner,
direct stay specialist, direct logistics specialist, and four Java capabilities.

- `planTrip` accepts a `PlanningInput` record containing `assessmentId` and the
  confirmed `TripRequest`. Use SkillTemplate's Object overload to normalize
  nested records; a raw map containing Java records is not JSON-compatible input.
- `planTransport` and `assessStay` receive the same explicit assessment ID and
  request. They return compact IDs and considerations after the Java searches.
- `assessTripLogistics` receives the assessment ID. It consumes the stored
  discovery results through `evaluateTripOptions`, so the coordinator need not
  recopy all service IDs. Early live runs demonstrated that recopying semantic
  identifiers could turn AIR-OUT into AIR-EARLY and RETURN into RET.
- The evaluator returns authoritative whole-trip facts and the saved request.
  Logistics considers the full set and returns a compact recommendation and
  alternative, with exact candidate IDs and short rationale. The root synthesizes
  that result. The application supplies displayed price/timing facts itself.

Source inspection identified a relevant framework limit: `StepPromptBuilder`
caps LAST TOOL RESULT at 1,000 characters in both assigned-step and final-response
prompts, and `StepLoopMissionExecutionEngine` records 100-character tool-result
previews in the execution summary. `StepPromptBuilderTest` protects truncation.
Returning all 18 options from logistics therefore hid later feasible entries
from the root. This is an existing implementation limit with an authoring-guide
coverage gap, not evidence that the model had read every option and rejected it.
No framework source was changed. The full catalog remains available to the
direct specialist, whose compact decision puts status and candidate IDs first.

`assessmentId` identifies one immutable request revision/catalog snapshot; this
replaces a repeated three-field RequestRef DTO. Candidate IDs are exact joined
service/hotel identifiers. Each seeded hotel has one room type, so a separate
roomTypeId adds no choice in this slice. Mode values use fixture-aligned
lowercase `rail`/`flight`; priorities remain uppercase strings.

The model is responsible for preference judgment and explanation. There is no
deterministic recommendation fallback in the running application. Ordinary
tests mock SkillTemplate at its supported public boundary; opt-in live tests
invoke the real model and verify the intended coordination.

Model prose is restricted to qualitative tradeoffs. The application renders
exact prices, savings, times, counts and numeric blocker explanations. A live
run produced correct candidate selections but an incorrect savings amount in
its prose; final validation now rejects digits, currency symbols and internal
priority tokens in model explanations. This lexical rule does not prove every
qualitative claim; Java's inventory facts remain the authoritative quote.

## Runtime and access scope

This is a shared local demonstration workspace, not a multi-user travel service.
It binds to loopback, has no login or tenant boundaries, and uses no real supplier
or payment service. Do not expose it as a public booking platform.

Two application workers and eight queued submissions bound concurrent model
assessments. The successful facade observer supplies session IDs and completed
skill start/finish events; it does not provide live stage updates. The UI shows
indeterminate progress while running and observed events after completion.
Only timestamps, event type, frame ID, and skill route are retained in the UI
projection. Full prompts and tool payloads are not exposed through business APIs.

On restart, queued/running assessments become failed and can be retried. Saved
requirements, successful proposals, bookings, and inventory remain. Loomspan is
not used as a durable job queue. A timeout or model error is assessment failure;
only a complete deterministic catalog evaluation can establish infeasibility.

## Deliberate limits

The initial app supports the seeded Boston–New York October 16–18, 2026 weekend,
two adults, and one room. Budgets, deadlines, priorities, and permitted modes are
editable. Unsupported routes/dates/party sizes are rejected explicitly.
The evaluator caps each catalog dimension at four, rejecting overflow instead
of truncating. Free-text intake, loyalty, disruption recovery, payments, and
supplier integrations remain later slices.


## Change and rebook

`PUT /api/trips/{id}` now permits revisions of a booked request. Its booking
remains current until explicit `POST /api/trips/{id}/exchanges` acceptance,
using the same command shape as booking. Initial booking and exchange are
separate endpoints; neither can silently perform the other operation.

Each replacement assessment persists its `base_booking_id`. Under the trip
and catalog locks, its immutable snapshot credits only that booking's two seats
on each leg and its room on both nights. It restricts hotel candidates to the
booked hotel and requires both night rows to exist. This is availability for
replacement planning, not released public inventory. The existing skill tree
runs again, with fresh receipts, on that snapshot and the revised request.

Acceptance locks the trip and catalog, verifies the current revision and exact
base booking, provisionally releases the old allocation, recomputes and compares
the complete quote, reserves the replacement, replaces the booking row, and
records before/after booking JSON. These writes commit as one transaction;
any failure rolls all of them back. The catalog guard serializes exchanges with
new bookings, including last-seat competition. Persisted exchange keys replay
that acceptance's original response; reuse for another choice is rejected.
The immutable before/after records remain visible in the trip's change history.

The entire replacement is repriced against the current catalog, including
retained items. There are no supplier fare locks, penalties, real payments,
partial exchanges, date/party changes, or hotel changes in this slice.

A live exchange test initially selected an all-flight option even though it
lost on the saved transfer preference. The evaluator now sorts complete
candidates with feasible trips first and then the saved preferences in order.
The logistics prompt uses the first feasible candidate; final Java validation
rejects a recommendation worse than the best preference score. Tied scores are
allowed. This is a computed ordering of catalog facts, not a fallback for
failed model execution. The model still coordinates searches and synthesizes
its evidence-backed decision and alternative.


Alternatives must improve at least one saved preference over the recommendation;
otherwise the model must return null and Java rejects the unsupported alternative.
This matters for hotel-preserving changes: a more expensive trip with the same
quiet room and longer transfers is not a meaningful alternative under the saved
preferences. A browser run exposed such an option with a misleading price phrase;
this case is now covered by the prompt and validation tests.

The evaluation tool also returns a compact `selections` header: the computed
recommended ID and eligible alternative IDs. The logistics specialist uses that
header with the complete quote evidence. This makes an empty alternative set
explicit instead of asking the model to infer it from a long catalog.


## Return-service disruption recovery

Flyway V5 adds persistent service cancellations and a catalog version counter.
`POST /api/trips/{id}/return-cancellation` requires the exact current booking ID
and its return service ID. It locks the trip and catalog, records the supplier
cancellation once, zeros that service's inventory, and increments the catalog
version. Repeated requests against the same current booking are idempotent;
a request aimed at a superseded booking is rejected. No model can cancel a
service. The UI exposes this action under explicit simulated demo controls.

Attention is derived from the current booking's return service and the global
cancellation record, so all bookings on the service are affected. Booked-trip
views poll for these changes. Canceled services are absent from every new
snapshot, including owned-inventory credits. A recovery snapshot restricts
outbound candidates to the booked service and hotels to the booked hotel.
The existing transport, stay, and logistics tree executes with fresh receipts;
the evaluation result includes the canceled recovery service ID as context.

Every assessment records the catalog version and its recovery service ID.
Cancellation invalidates earlier proposals globally; acceptance checks the
version under the catalog lock. A successful assessment of an old immutable
snapshot can remain historical evidence, but it cannot be accepted after a
cancellation. Interrupted sessions use the existing retry behavior.

Recovery uses the same atomic exchange transaction. It verifies retained
outbound/hotel IDs, preserves their allocation, and reserves a new return.
Releasing a canceled return never restores its seats. Failure retains the
affected booking, attention state, outbound seats, and hotel nights; it does
not claim that the canceled return is usable. Success records normal immutable
before/after history and clears attention through the new booking's service.

For a completed infeasible recovery, Java evaluates the same snapshot with both
modes and broad same-weekend budget/timing bounds. It derives up to three sets
of necessary changes from otherwise feasible candidates: allowed modes, total
budget, hotel-ready deadline, hotel departure limit, and Boston return deadline.
These are draft request suggestions, never bookable quotes or automatic consent.
The traveler reviews them and starts a new real skill assessment. Missing or
canceled inventory cannot be repaired by relaxing preferences, so no suggestion
is offered when the broader finite catalog is also infeasible.

This slice simulates pre-travel supplier cancellation of a return service only.
It does not model outbound disruptions, in-progress journeys, fee/refund policies,
service reinstatement, or live supplier notifications. Entire replacement totals
retain the demo's current-catalog repricing policy; no real payment occurs.
