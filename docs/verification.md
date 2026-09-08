# First-slice verification — September 7, 2026

Environment: Java 21.0.2, Node 24.18.0, Spring Boot 4.1.0,
Loomspan 1.0.0-beta.1 installed from framework commit
`23dc751a9749cbb72279ac5ecdc24d35ce6b9069`, OpenAI-compatible connection using
`gpt-4.1` for all four YAML skills.

## Automated checks

- Full Maven package: frontend installation, TypeScript compilation, Vite
  production assets, 17 ordinary Java tests, and executable Spring Boot JAR.
  Two opt-in live tests are skipped in ordinary builds.
- `TripApplicationTest`: all 18 fixture combinations, party/room/transfer units,
  mixed modes, budget and timing boundaries, missing hotel nights, immutable
  snapshots, invalid selections, required catalog/evaluation receipts, late
  writes, idempotency, changed prices, old/foreign proposals, competing bookings,
and recovery of interrupted assessments.
- `HttpFlowTest`: request → asynchronous assessment → booking → reload through
  actual HTTP endpoints; provider failure remains FAILED rather than infeasible;
  invalid JSON and missing IDs receive useful error responses.
- Two opt-in `LivePlanningTest` cases passed with the actual model: the $980
  quiet-room recommendation and $840 alternative, observed transport/stay
  interval overlap, transactional acceptance, and a rail-only $800 request
  yielding the $40 shortfall without executing flight search.

Final default live session: `6220be4d-fa97-4de8-b07c-6d2f2e257c6b`.
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
