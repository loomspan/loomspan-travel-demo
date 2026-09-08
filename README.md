# Wayfarer — Loomspan travel demo

A complete local travel workspace demonstrating nested skill coordination,
whole-trip tradeoffs, deterministic validation, and persistent simulated bookings.

Plan a Boston–New York weekend for two. Compare a **$980 quiet-room trip** with
an **$840 budget alternative**, inspect the specialist execution, and explicitly
book against finite local inventory. All services, prices and reservations are
fictional; model planning uses the configured provider.

## Build and run

Requires Java 21+, Node.js 22.13+ and model access. The app consumes
`ai.loomspan:loomspan-spring-boot-starter:1.0.0-beta.1`. The local framework build
used during implementation was commit `23dc751a9749cbb72279ac5ecdc24d35ce6b9069`.
Install that dependency from the sibling framework checkout if needed:

```powershell
cd C:\opendev\code\loomspan-framework
.\mvnw.cmd -pl loomspan-spring-boot-starter -am install -DskipTests
cd C:\opendev\code\loomspan-travel-demo
$env:OPENAI_API_KEY = 'your-key'
$env:WAYFARER_MODEL = 'gpt-4.1'
.\mvnw.cmd package
.\scripts\run.ps1
```

Open [Wayfarer at localhost:8082](http://localhost:8082). The Maven build compiles
React and includes it in the Spring Boot JAR. Running the packaged app needs
Java and model access, with no frontend server or Docker. On macOS/Linux, use
`./mvnw package` and `java -jar target/wayfarer-0.1.0-SNAPSHOT.jar`.

The default database is `data/wayfarer.mv.db`; ordinary restarts never reset it.
`.env.example` lists configuration variables but is not automatically loaded.
`WAYFARER_MODEL_BASE_URL` selects an OpenAI-compatible endpoint and
`WAYFARER_PORT` changes the HTTP port.

For frontend development run `npm ci` and `npm run dev` in `frontend`, alongside
`./mvnw spring-boot:run -DskipFrontend=true`. Vite proxies `/api` to port 8082.

## Presenter walkthrough

1. Keep the default dates and two-person request. Confirm and compare with
   Loomspan. No inventory is reserved during planning.
2. Review Garden Court with rail both ways at $980 and the Central House
   alternative at $840. Both meet the Friday hotel deadline. Exact wording can
   vary; amounts and times are checked against the database.
3. Expand **Why these trips?** and **How the trip was assessed**. The latter
   contains the real session ID and observed skill start/finish events.
4. Before booking, edit the budget to $900 and reassess: the quiet-room upgrade
   no longer fits. At $800 there is no feasible trip; the minimum timing-valid
   trip is $840. Try rail-only to demonstrate selective search.
5. Restore $1,200, assess, review a trip, and confirm its simulated booking.
   Two seats on each service and one room on each night are reserved atomically.
6. Refresh or restart the app. The booking remains, and later requests see
   depleted inventory. Fresh fixture totals assume no earlier competing bookings.

Meals, show tickets and incidental local travel are excluded from quoted totals.
Timing begins/ends at the Boston downtown meeting point, not a home address.
Transfers are included quote line items, not finite taxi reservations.

## Skill coordination

```text
planTrip                         YAML planner
├─ planTransport                 YAML planner
│  ├─ searchRailServices         Java
│  └─ searchFlightServices       Java
├─ assessStay                    YAML direct specialist
│  └─ searchHotels               Java
└─ assessTripLogistics           YAML direct specialist
   └─ evaluateTripOptions        Java
```

Transport and stay investigations can overlap. Java searches persist immutable
catalog results for the assessment; logistics requires those results, evaluates
all combinations, and returns a compact preference-based decision. The root
synthesizes the proposal. Java revalidates selections and owns all reservation
writes. There is no canned recommendation fallback.

The [implementation notes](docs/implementation-notes.md) explain the persisted
handoff and the framework's planner-result size limit discovered during live
testing. Java contracts live in `Contracts.java`; YAML manifests are generated
by `scripts/generate-skill-manifests.py` and checked in.

## Verification

```powershell
.\mvnw.cmd test -DskipFrontend=true
npm run build --prefix frontend
$env:WAYFARER_LIVE_TEST = 'true'
.\mvnw.cmd '-Dtest=LivePlanningTest' -DskipFrontend=true test
Remove-Item Env:WAYFARER_LIVE_TEST
```

Ordinary tests use isolated in-memory H2 databases and mock only the supported
SkillTemplate facade. They cover catalog calculations, coverage, invalid model
results, HTTP flows, stale proposals, price changes, idempotency, booking races,
and interrupted assessments. Live tests use your configured provider and verify
actual nested execution, specialist overlap, preference choices, selective
rail-only search, infeasibility, and booking. Live traces and provider requests
may contain the fictional trip data. The separate Python design calculator
(`python scripts/verify-design.py`) requires Python 3.10+ and is not used by the app.

## Reset and optional Console

Stop Wayfarer, then run `./scripts/reset-demo.ps1 -ConfirmReset` to remove the
default local database and all its saved trips. Custom database locations are
left alone. Flyway seeds fresh inventory at the next startup. On macOS/Linux,
remove only `data/wayfarer.mv.db` while stopped.

Console is optional. Set `WAYFARER_OBSERVABILITY_ENABLED=true` and a separate
`WAYFARER_OBSERVABILITY_API_KEY` of at least 32 printable non-whitespace characters.
For a presentation, `WAYFARER_TRACE_PERSISTENCE=ALWAYS` retains successful traces
as well as failures. The ordinary UI needs no Console connection.

## Scope

One shared local workspace, one corridor and seeded weekend, two adults, one
room, one currency. This is not production authentication or a supplier booking
service; the server binds to loopback. Budgets, deadlines, allowed modes and
preference order are editable. Unsupported routes, dates, and party sizes are
rejected explicitly.

Free-text intake, loyalty benefits, disruption recovery, payments, supplier
connections, and booking cancellation remain later slices.

Design references: [brief](docs/design-brief.md),
[scenario and inventory](docs/first-demo-scenario.md),
[calculated combinations](docs/calculated-combinations.md),
[original workspace/contracts proposal](docs/workspace-and-contracts.md), and
[implemented refinements](docs/implementation-notes.md).

See [recorded verification](docs/verification.md) for test coverage and the
packaged-browser/restart checks completed for this slice.
