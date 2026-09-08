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
