# P02-T02 Code Review — Cycle 3

## Scope and Repository State

Reviewed the ticket-scoped unstaged and untracked changes against `main` at `2a1f688`, including V10, the new airfare fixture verifier/integration test, affected startup/forward-migration/schema/temporal tests, and the ticket/research/plan/testing-plan artifacts. No staged changes were present. The only changed production artifact is the Flyway V10 fixture migration; no API, UI, Trip, inventory-mutation, booking, supplier, currency, or expanded-date behavior is introduced.

The selected `full` profile remains appropriate: this is a persisted, deterministic, cross-time-zone fixture contract. No profile mismatch or developer decision is required.

## Findings

No actionable findings.

## Findings Resolved in This Context

### [P2] Exercise persisted invariant corruption rather than only helper primitives

- Location: `src/test/java/app/detour/catalog/AirfareFixtureIntegrationTest.java:51`
- Scenario: Price, capacity, layover, and final-arrival cases previously called public assertion helpers with synthetic arguments, so they would not prove the complete JDBC fixture verifier rejects a damaged persisted catalog.
- Impact: A regression that stopped wiring those checks into `assertValid` could evade the representative-corruption acceptance criterion.
- Fix: Corrupt separate clean in-memory migrations, run the full verifier, and retain coverage/connection/key cases. The test now also holds each Flyway/H2 migration open on one suppressed-close connection so H2 constraints remain valid during update mutations.

### [P2] Snapshot all deterministic fixture contracts

- Location: `src/test/java/app/detour/catalog/AirfareFixtureIntegrationTest.java:98`
- Scenario: The ordered rebuild snapshot omitted destinations, airports/time zones, suppliers, schedule display data, and schedule segment local-time fields.
- Impact: Determinism evidence would not detect a drift in those persisted inputs even though downstream display and identity behavior depend on them.
- Fix: Snapshot all seeded reference, supplier, schedule, schedule-segment, instance, and instance-segment semantic values in stable order.

### [P2] Avoid masking schema constraints with seeded schedule/date uniqueness

- Location: `src/test/java/app/detour/catalog/CatalogSchemaIntegrationTest.java:46`
- Scenario: Invalid-price and zero-capacity insertions used V10's schedule ID and March service dates, which already have instances; the unique schedule/date constraint could fail before the intended price/capacity constraint.
- Impact: The regression test would no longer demonstrate its named database constraints after seeding.
- Fix: Use a stable schedule-key lookup and unseeded April dates.

## Acceptance-Criteria and Plan Conformance

| Criterion/decision | Code evidence | Test evidence | Result |
| --- | --- | --- | --- |
| Four choices for each usable destination/direction/date | V10 date ranges and 24 schedule definitions | full cross-product checks in `AirfareFixtureIntegrityAssertions` | implemented |
| Required connection, zoned timing, layover, duration | V10 airport/segment matrix and zone-derived timestamps | full verifier plus persisted connection/timing mutations | implemented |
| March 31 cutoff and valid trip-date sets | V10 outbound Mar 1–30 and inbound Mar 2–31 generation | coverage and final-arrival mutation checks | implemented |
| Stable display, identifier, price, capacity | semantic V10 keys, fictional supplier/schedule fields, price/capacity rows | keys, positive pricing/capacity, complete snapshot, duplicate-key rejection | implemented |
| Same per-traveler fare and meaningful variation | one priced instance per flight, no age fare model | mixed-age party arithmetic and variation assertions | implemented |
| Rebuild determinism | literal/CTE-based migration | two independent full semantic snapshots | implemented |
| Deliberate invariant failures | schema constraints, triggers, and fixture verifier | persisted coverage/connection/timing/price/capacity/final-arrival corruptions and duplicate-key constraint | implemented |
| Existing behavior and packaged startup | migration-only production change | related suite, full backend suite, package, and loopback packaged verification | implemented |
| Out-of-scope behavior excluded | changed-path inspection | no production API/UI/domain changes in diff | implemented |

## Active Project Guardrails

- None recorded in `ai/thoughts/design-lens.md`.

## Open Questions and Assumptions

- None.

## Verification Results

- PASS — `git diff --check` — no whitespace errors.
- PASS — `.\mvnw.cmd -DskipFrontend=true '-Dtest=AirfareFixtureIntegrationTest,CatalogSchemaIntegrationTest,CatalogTemporalMoneyRoundTripIntegrationTest,DetourApplicationTest,PhaseOneCatalogForwardMigrationIntegrationTest' test` — 10 tests passed.
- PASS — `.\mvnw.cmd test -DskipFrontend=true` — 18 tests passed.
- PASS — `.\mvnw.cmd package` — frontend build, backend tests, and Spring Boot package passed.
- PASS — `powershell -ExecutionPolicy Bypass -File .\scripts\verify-packaged-identity.ps1` — isolated loopback packaged identity-shell verification passed.

## Residual Risks and Optional Developer Checks

- H2 2.4.240 is newer than Flyway's verified H2 version and emits a warning during tests; the complete migration/test/package checks pass with this installed version.

## Disposition

- `fixes-applied`
