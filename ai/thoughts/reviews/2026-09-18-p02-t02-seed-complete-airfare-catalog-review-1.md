# P02-T02 — Seed Complete Deterministic Airfare Fixtures Code Review — Cycle 1

## Scope and Repository State

Reviewed all staged, unstaged, and untracked ticket-scoped work against `main` at `2a1f688`. Scope includes the V10 Flyway fixture migration, new airfare integrity tests, updated migration/catalog regressions, and the ticket/research/planning artifacts. No unrelated dirty work was present; the ticket's execution note attributes the worktree to P02-T02.

The migration seeds three destinations, nine airports, two fictional airlines, 24 schedules, 720 instances, and 1,080 segments. The implementation stays in migration and test sources: no API, UI, Trip, booking, inventory-mutation, supplier, or currency behavior was added.

## Findings

No remaining actionable findings.

## Findings Resolved in This Context

### [P2] Strengthen the airfare integrity proof

- Location: `src/test/java/app/detour/catalog/AirfareFixtureIntegrationTest.java:23`, `src/test/java/app/detour/catalog/AirfareFixtureIntegrityAssertions.java:112`
- Scenario: The original mixed-age assertion compared an expression with itself, the verifier did not demonstrate duration variation, and representative connection, layover, positive-price, minimum-capacity, and final-arrival failures were not all exercised.
- Impact: A regression in several ticket acceptance invariants could have passed the new test suite.
- Fix: Added per-option connection policy, derived-duration variation, invalid-value validator cases, an actual connection corruption, and mixed-age complete-party arithmetic using one persisted fare.

### [P3] Preserve whole-property schema coverage

- Location: `src/test/java/app/detour/catalog/CatalogSchemaIntegrationTest.java:119`
- Scenario: Updating the schema test to use seeded airfare had removed its valid whole-property and bed-and-breakfast unit inserts.
- Impact: A pre-existing accepted catalog shape would no longer be regression-tested.
- Fix: Restored those assertions using isolated `test-*` catalog keys and fixture lookups.

## Acceptance-Criteria and Plan Conformance

| Criterion/decision | Code evidence | Test evidence | Result |
| --- | --- | --- | --- |
| Two direct and two one-stop choices for each usable combination | `V10__seed_march_2027_airfare_catalog.sql:77-96` | Full date/direction/destination coverage in `AirfareFixtureIntegrityAssertions` | implemented |
| Required connections, feasible zoned legs/layovers, correct stops/duration | V10 schedule/segment matrix | Per-option connection, local-zone, chronology, 45-minute layover, and derived-duration checks | implemented |
| March boundary and valid 1–14-night date coverage | V10 outbound Mar 1–30 and inbound Mar 2–31 generation | Coverage and final-arrival checks, including invalid boundary value | implemented |
| Stable IDs, fictional display data, positive price/capacity | V10 semantic keys, suppliers, fares, capacities | Key uniqueness/immutability, positive-price and capacity validation | implemented |
| Same fare/seat per traveler and meaningful variation | One fare/capacity row per instance in V10 | Mixed-age party arithmetic; price, duration, departure, and stop-count variation checks | implemented |
| Deterministic rebuilds | Literal definitions and deterministic H2 date ranges | Independent ordered snapshot comparison | implemented |
| Representative invariant failures | Schema constraints and fixture verifier | Coverage, connection, timing, price, capacity, identifier, and boundary failures | implemented |
| Existing identity, backend, clean-Flyway, and packaged startup behavior | Updated existing integration tests; no application-layer change | Related suite, full suite, package, and loopback startup check | implemented |
| No excluded behavior added | Changed-path review | No API/UI/domain additions in diff | implemented |
| Plan's test class name differs (`AirfareFixtureIntegrationTest` rather than `AirfareFixtureIntegrityIntegrationTest`) | Test is present and invoked by standard Maven selection | Focused suite passes | safe deviation |

## Active Project Guardrails

- None recorded in `ai/thoughts/design-lens.md`.

## Open Questions and Assumptions

- None.

## Verification Results

- PASS — `.\mvnw.cmd -DskipFrontend=true -Dtest=AirfareFixtureIntegrationTest test` — fixture migration, integrity, corruption, party, and determinism checks pass.
- PASS — `.\mvnw.cmd -DskipFrontend=true '-Dtest=AirfareFixtureIntegrationTest,DetourApplicationTest,PhaseOneCatalogForwardMigrationIntegrationTest,CatalogSchemaIntegrationTest,CatalogTemporalMoneyRoundTripIntegrationTest' test` — 10 related tests pass.
- PASS — `.\mvnw.cmd test -DskipFrontend=true` — 18 backend tests pass after the final review fixes.
- PASS — `.\mvnw.cmd package` — frontend build, 18 tests, and Spring Boot JAR packaging pass.
- PASS — `powershell -ExecutionPolicy Bypass -File .\scripts\verify-packaged-identity.ps1` — isolated loopback packaged-startup verification passes.
- PASS — `git diff --check` — no whitespace errors.

## Residual Risks and Optional Developer Checks

- The fixture verifier supplies direct invalid values to its package-private price, capacity, layover, and boundary validators. During review, direct updates to completed H2 fixture rows produced an H2 2.4 internal `database closed` constraint-evaluation error; this does not affect the immutable fixture deliverable or current startup paths. A future inventory-mutation ticket should add transactional update coverage using its application datasource.

## Disposition

- `fixes-applied`
