# Wayfarer verification — September 7, 2026

Environment: Java 21.0.2, Node 24.18.0, Spring Boot 4.1.0,
Loomspan 1.0.0-beta.1 installed from framework commit
`23dc751a9749cbb72279ac5ecdc24d35ce6b9069`, OpenAI-compatible connection using
`gpt-4.1` for all four YAML skills.

## Automated checks

- Full Maven package: frontend installation, TypeScript compilation, Vite
  production assets, 24 ordinary Java tests, and executable Spring Boot JAR.
  Two opt-in live tests are skipped in ordinary builds.
- `TripApplicationTest`: all 18 fixture combinations, party/room/transfer units,
  mixed modes, budget and timing boundaries, missing hotel nights, immutable
  snapshots, invalid selections, required catalog/evaluation receipts, late
  writes, idempotency, changed prices, old/foreign proposals, competing bookings,
  recovery of interrupted assessments, owned-inventory credit, preserved hotels,
  atomic exchanges, rollback on sold-out or repriced inventory, stale booking
  baselines, history, replay, and concurrent exchange/new-booking acceptance.
- `HttpFlowTest`: request → asynchronous assessment → booking → reload through
  actual HTTP endpoints, followed by revision → reassessment → exchange →
  replay → history reload; provider failure remains FAILED rather than infeasible;
  invalid JSON and missing IDs receive useful error responses.
- Two opt-in `LivePlanningTest` cases passed with the actual model: the $980
  quiet-room recommendation and $840 alternative, observed transport/stay
  interval overlap, transactional acceptance, and a rail-only $800 request
  yielding the $40 shortfall without executing flight search.

Initial-slice default live session: `6220be4d-fa97-4de8-b07c-6d2f2e257c6b`.
Transport ran from 01:10:29.630 to 01:10:34.112 UTC on September 8; stay ran
from 01:10:29.631 to 01:10:32.702, confirming observed overlap. The final live
run also passed the qualitative-prose validator, keeping model-generated
numeric claims out of the user-facing quote explanation.

Test fakes are limited to the supported SkillTemplate boundary in ordinary
tests. The running app has no fake-planner mode or deterministic recommendation
fallback. Live diagnostics are written under ignored `target/`; test databases
are isolated in-memory H2 instances.

## Packaged application and browser

Started the JAR on port 8083 against a separate file-backed test database under
`target/browser-demo`. Used the actual React form to save the default request,
run real Loomspan planning, compare the $980/$840 options, review the itinerary,
and confirm the $980 Garden Court booking.

Stopped and restarted the Java process against the same database. Reloaded the
browser and independently checked the API: booking
`67ae5c66-7681-4981-976f-54e53e3a989d` remained with total 98,000 cents.
After stopping the test process, a direct H2 query verified zero remaining
seats on RAIL-OUT and RAIL-RETURN, and zero Garden Court rooms on both October
16 and 17. This verifies the stock changes persisted, not only the UI record.

Inspected the real comparison at 1024px and the booked-trip layout at 360px.
The narrow page had equal client and scroll widths (345px after scrollbar),
with no horizontal overflow. Booking confirmation moved keyboard focus to the
explicit acceptance button. The separate test process was stopped afterward;
the user-facing default instance uses fresh `data/wayfarer` inventory.

## Limits of this verification

Model wording varies, and success with this tree/configuration does not establish
compatibility with other models. The prompts keep final decisions compact to
fit the current framework's planner-result context limit; see implementation
notes for the source anchors. Java remains the booking correctness boundary.

Real supplier integrations, production authentication, free-text intake,
loyalty, and disruption recovery are not part of this slice or these checks.


## Change-flow live verification

The final full package build passed all 26 tests: 24 ordinary tests and both
opt-in live tests enabled. Live verification now includes initial
planning and booking followed by revised planning and exchange through the same
real skill tree. Exchange session: `d50e65f2-50d6-4198-a00c-a9a2012b0cf6`.
The accepted replacement kept Garden Court, used rail outbound and flight return,
cost $1,050, and returned to the Boston meeting point at 21:10. The $980 original
booking stayed current throughout planning. Ordinary tests also verify that an
exchange and another trip's booking cannot both consume the last flight seats.

The final live test also asserts that this replacement has no alternative: no
other feasible trip improves a saved preference. The earlier all-flight option
is correctly excluded. A subsequent frontend-only package rebuild incorporated
single-option layout and accepted-history labels without backend changes.


The packaged browser flow was verified against separate file database
`target/exchange-browser`: booked the original Garden Court rail trip, edited
its return deadline using the preset, ran live reassessment, reviewed the single
$1,050 option, and explicitly accepted the exchange. The history showed the $70
difference and both itineraries. After stopping and restarting the server, the
browser and HTTP API returned the same booking
`cb090e81-3f8d-4f86-a08f-43ee7bfd04ae`, total, and history entry. The comparison
and accepted-history layouts were visually inspected at the normal viewport.
The test process was stopped afterward. The user-facing instance on port 8082
applied Flyway V4 to the existing default database without resetting saved trips.


## Disruption recovery verification

The V5 package passed all 32 tests: 29 ordinary tests and three opt-in live
cases. New coverage includes idempotent service cancellation, all affected
bookings, canceled-service exclusion, stale catalog fencing, retained outbound
and hotel allocations, recovery rollback, explicit mode/budget suggestions,
no-inventory infeasibility, and cancellation racing an exchange. HTTP coverage
includes cancellation and replay against the exact current booking.

The live recovery case starts with a fixture rail-only booking and cancels its
return train. Real nested planning reports infeasibility, Java suggests allowing
flights, the test explicitly saves that request revision, and real nested
planning produces the $1,050 replacement. Acceptance clears attention without
restoring seats on the canceled train. Final recovery session:
`da2a1e02-62e3-402e-8c75-544d733d8a1f`.


The packaged browser check used the existing isolated `target/exchange-browser`
database, migrated from V4. Its $1,050 booking had a return flight and a 21:30
Boston deadline. The UI cancellation control marked it Needs attention. Live
recovery reported the deadline blocker and suggested 22:15 Eastern. Reviewing
that suggestion populated a draft; explicitly confirming it ran a new real
assessment and produced the $980 rail replacement. Explicit recovery acceptance
cleared attention, preserved the outbound and hotel, and added a second history
entry with a $70 simulated refund. The affected-state UI was visually inspected.

After stopping the test server, a separate H2 connection to the file database
confirmed the AIR-RETURN cancellation persisted, its available seats remained
zero, both rail service allocations remained consumed, and both Garden Court
nights remained reserved. Recovery booking reference:
`b88b8568-f9e2-4b61-ada3-9e54347177d2`.

Restarting the packaged server and reloading the browser/API returned the same recovered booking, two history entries, and retained catalog version. The test process was stopped; the default app remains on port 8082 with the user's existing data migrated to V5.

## Conversational change verification

The V6 package build passed 40 tests with live tests enabled, including the
existing initial planning, exchange, and recovery cases. A subsequent HTTP
test addition passed all four HTTP tests. The current suite therefore has 37
ordinary tests and four opt-in live cases, verified across these runs.
Frontend TypeScript checking and the Vite production build passed during packaging.

Intake tests cover clarification history, exact proposed differences, no writes
to requirements or inventory before confirmation, saved-budget arithmetic,
invalid patches, stale request/catalog/booking rejection, trip scoping,
idempotent confirmation, and concurrent confirmation producing one revision.
HTTP coverage verifies an empty latest-draft response, interpretation, explicit
confirmation, and replay.

The final live intake test starts with a $980 Garden Court booking and a $1,200
saved budget. Five real model calls verify clarification for an unspecified
earlier return, an exact 21:30 deadline, a $100 budget increase to $1,300,
rejection of a mixed unsupported destination change, and clarification for a
conditional future disruption policy. This targeted test passed after the full
package run. Intake session: `5d4d870e-92bb-4bd8-8031-c734b20a81e5`.

The packaged browser check used a separate `target/intake-browser` database.
After booking the $980 Garden Court rail trip, asking to get home earlier
produced a clarification. Stopping and restarting the server restored that
question and conversation. Answering 21:30 Eastern produced a visually inspected
review table containing only the return deadline change from 23:00 to 21:30.
The API still showed request revision 1 and the original booking before
confirmation. Intake session: `4b9c0f0d-fa3f-4f0a-ad3c-ccac56155dc8`.

Explicit confirmation saved revision 2 and invoked the existing nested planning
flow. The resulting $1,050 proposal retained Garden Court and the outbound rail
service, returning by flight with a 21:10 Boston arrival. The API confirmed that
the original $980 booking `2d7c991a-76b2-4458-b581-308dbbc56709` remained current
and the persisted draft recorded confirmed revision 2. Replacement acceptance
remained a separate review action. The isolated test instance was stopped after
verification. Port 8082 runs the packaged V6 app with existing user data preserved.

## Guided demo verification

The frontend-only guide slice passed TypeScript checking, the Vite production
build, and Maven packaging with tests skipped. No backend or skill behavior
changed, so the model and reservation suites were not rerun for this slice.

The packaged browser on port 8083 used the existing isolated intake database.
All three scenario selectors were exercised. The change guide detected an
existing replacement proposal and navigated to comparison; its selection
survived refresh. Recovery guidance opened the booked itinerary without
canceling it. Starting a new weekend displayed initial planning guidance without
persisting a trip. Desktop screenshots were inspected for the scenario cards,
walkthrough, and coordination panel.

The panel displayed actual completion events for all three specialists and
the coordinator, with two of six combinations feasible, matching the saved
assessment. The API afterward still showed revision 2, the same $980 booking,
catalog version zero, no disruption, and two assessments. Scenario navigation
caused no domain writes. The temporary server was stopped; the rebuilt app runs
on port 8082 with the default database preserved.
