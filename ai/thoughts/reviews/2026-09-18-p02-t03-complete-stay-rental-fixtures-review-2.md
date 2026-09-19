# P02-T03 Complete Stay and Rental Fixtures Code Review — Cycle 2

## Scope and Repository State

Reviewed the ticket, research, implementation plan, testing plan, active design lens, committed baseline (`ea5f1a9`), and all staged, unstaged, and untracked ticket-scoped work. The change adds V11 deterministic stay/rental fixtures, complete-catalog assertions and integration tests, a standalone fixture summary and wrapper, plus fresh-start/forward-migration regressions. No active project-specific guardrails are recorded.

The review traced the Flyway fixture rows through their existing inventory, money, immutable-key, and half-open rental-occupancy constraints; inspected summary database ownership and command invocation; and considered persistence, lifecycle, determinism, security/privacy, operations, and excluded API/booking scope. No sensitive values or external integrations are introduced.

## Findings

No actionable findings.

## Findings Resolved in This Context

### [P3] Close the isolated summary datasource after rendering
- Location: `src/main/java/app/detour/catalog/CatalogFixtureSummary.java:35`
- Scenario: each call to `render()` generated a uniquely named `DB_CLOSE_DELAY=-1` H2 database and retained its `SingleConnectionDataSource`; a long-lived process invoking the reusable summary repeatedly accumulated open connections and in-memory databases.
- Impact: the lightweight, repeatable summary command had avoidable resource growth outside its one-shot CLI use.
- Evidence: the pre-fix method created the datasource without a `finally`/`destroy` path, while its UUID-based H2 URL prevented later callers from reusing or closing the same database.
- Fix: remove `DB_CLOSE_DELAY=-1` and destroy the datasource in `finally` after writing output. The documented command and deterministic summary test pass after the change.

## Acceptance-Criteria and Plan Conformance

| Criterion/decision | Code evidence | Test evidence | Result |
| --- | --- | --- | --- |
| Two properties of each stay type/destination with March-night coverage | V11 inserts 18 properties/units and generates 30 nights per unit | complete fixture facade and clean Flyway tests | implemented |
| Parties 1–8 have meaningful stay tradeoffs | fixed capacity, cents, rating, and distance matrix | party-fit and variation assertions | implemented |
| Airport rental supply covers every class | V11 inserts 3 locations, 9 classes, 21 physical units | rental coverage/availability assertions | implemented |
| Rental cycle billing and half-open availability | integer-cent daily inputs; existing overlap trigger remains | 25-hour, partial-cycle, adjoining, and overlap cases | implemented |
| Whole Phase 2 integrity rejects representative violations | facade composes airfare checks with stay/rental checks | deletion/invalid-row corruption tests and deterministic snapshot | implemented |
| One deterministic, concise documented summary command | summary main, PowerShell wrapper, README | output determinism test and wrapper output | implemented |
| Clean rebuild/forward migration/startup remain valid | appended V11 and updated expectations | clean H2 tests, full suite, package, packaged identity check | implemented |
| Flyway-only fictional fixtures; excluded product scope unchanged | fixture SQL and isolated H2 summary have no API, UI, booking, supplier, or payment additions | changed-path review and local integration tests | implemented |

## Active Project Guardrails

- None recorded in `ai/thoughts/design-lens.md`.

## Open Questions and Assumptions

- None.

## Verification Results

- PASS — `& .\mvnw.cmd '-DskipFrontend=true' '-Dtest=AirfareFixtureIntegrationTest,StayAndRentalFixtureIntegrationTest,CatalogTemporalMoneyRoundTripIntegrationTest,RentalUnitOccupancyConstraintIntegrationTest,CatalogSchemaIntegrationTest,PhaseOneCatalogForwardMigrationIntegrationTest,DetourApplicationTest,CatalogFixtureSummaryIntegrationTest' test` — focused catalog, migration, summary, and startup regressions passed.
- PASS — `powershell -ExecutionPolicy Bypass -File .\scripts\show-catalog-fixture-summary.ps1` — deterministic concise catalog report emitted from an isolated H2 database.
- PASS — `& .\mvnw.cmd '-DskipFrontend=true' test` — full Maven test suite passed.
- PASS — `& .\mvnw.cmd '-DskipFrontend=true' package` — packaged application built successfully.
- PASS — `powershell -ExecutionPolicy Bypass -File .\scripts\verify-packaged-identity.ps1` — packaged identity shell verification passed.
- PASS — `git diff --check` — no whitespace errors.

## Residual Risks and Optional Developer Checks

- The host PowerShell execution policy blocks direct `.ps1` invocation; the wrapper was verified with the standard process-scoped bypass. Developers whose policy similarly blocks scripts should use their approved execution-policy mechanism.
- Flyway reports that the installed H2 2.4.240 is newer than Flyway's verified H2 version; all local clean-migration checks passed.

## Disposition

- `fixes-applied`
