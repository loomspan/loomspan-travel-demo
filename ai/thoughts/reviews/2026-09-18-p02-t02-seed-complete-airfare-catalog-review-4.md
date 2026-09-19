# P02-T02 Code Review — Cycle 4

## Scope and Repository State

Reviewed the complete ticket-scoped working-tree change against `main` at `2a1f688`: the V10 Flyway migration, airfare integrity test/helper, affected catalog/startup/forward-migration tests, and the ticket/research/plan/testing-plan artifacts. The checkout contains no unrelated changed paths; all modified and untracked paths are attributable to P02-T02 under the ticket execution note. No active project-specific guardrails are recorded in `ai/thoughts/design-lens.md`.

The migration uses deterministic literals plus `SYSTEM_RANGE`, retains the V1–V9 lineage, and makes no API, UI, Trip, booking, inventory-mutation, provider, or currency change. Persistence, temporal conversion, data integrity, test isolation, operational startup, and security/privacy exposure were reviewed. The change is correctly on the selected `full` profile because it introduces a persisted temporal fixture contract.

## Findings

No actionable findings.

## Findings Resolved in This Context

- [P2] Strengthened the fixture verification so the deliberately shortened layover is evaluated by connection-policy validation before local-schedule reconstruction, and the April arrival is evaluated by the final-arrival boundary before its expected schedule mismatch. This ensures each mutation demonstrates the invariant named by the test.
- [P2] Extended party-price verification from one arbitrary fare row to every generated fare and added concrete assertions that the least-expensive options connect, shortest options are direct, and earliest departures differ from those orderings.
- [P3] Replaced fixture test rows with `test-*` semantic keys and removed remaining numeric-ID dependencies from schema assertions, so the tests exercise the seeded catalog by stable keys and remain isolated from future product fixtures.

## Acceptance-Criteria and Plan Conformance

| Criterion/decision | Code evidence | Test evidence | Result |
| --- | --- | --- | --- |
| Four choices for every usable direction/date/destination | V10 schedule matrix and two 30-day date sets generate 720 instances. | `assertCoverage` checks two direct and two one-stop options for all 360 required combinations. | implemented |
| Required airports, connections, timing, and layovers | V10 seeds the nine planned IATA/zone rows and the SEA/SLC, SEA/ORD, LAX/DFW routing matrix; zoned segment timestamps derive from local values. | `assertAirportZones`, row reconstruction, connection checks, and 45-minute layover mutation. | implemented |
| March boundary and valid trip-date coverage | V10 limits outbound services to March 1–30 and inbound services to March 2–31; segment insert retains final local arrivals through March 31. | Full cross-product and final-arrival checks, including an April mutation. | implemented |
| Stable keys, display data, pricing, and capacity | V10 has stable semantic keys, fictional supplier/flight data, cents, and capacity values; existing immutability/overflow constraints remain in the lineage. | Snapshot comparison, duplicate-key constraint case, per-row price/capacity checks, and existing schema immutability checks. | implemented |
| Same fare/seat per traveler and useful variation | One fare/capacity row exists for each instance; no age-specific model was added. | Every fare is multiplied across mixed-age parties of sizes 1–8; variation checks prove distinct non-collapsing price, duration, departure, and stop inputs. | implemented |
| Deterministic rebuild | V10 uses only literals, joins, and deterministic H2 date ranges. | Ordered semantic snapshots from independent clean H2 migrations match. | implemented |
| Representative integrity failures | Generic constraints/triggers and test-only verifier jointly own the invariants. | Coverage, connection, layover, price, capacity, duplicate key, and final-arrival corruptions each reject. | implemented |
| Existing behavior and packaged startup | No app-layer behavior changed; affected startup and forward-lineage tests target V10. | Related suite, full backend suite, package, and isolated loopback packaged-startup check pass. | implemented |
| Scope exclusions | Only migration, test, and pipeline-artifact paths changed. | Diff review found no API/UI/domain/booking/provider/date-range addition. | implemented |

## Active Project Guardrails

- None recorded.

## Open Questions and Assumptions

- None.

## Verification Results

- PASS — `.\mvnw.cmd -DskipFrontend=true -Dtest=AirfareFixtureIntegrationTest test` — clean migration and fixture-specific assertions passed before the review fixes.
- PASS — `.\mvnw.cmd -DskipFrontend=true "-Dtest=AirfareFixtureIntegrationTest,DetourApplicationTest,PhaseOneCatalogForwardMigrationIntegrationTest,CatalogSchemaIntegrationTest,CatalogTemporalMoneyRoundTripIntegrationTest" test` — 10 affected tests passed after the fixes.
- PASS — `.\mvnw.cmd test -DskipFrontend=true` — full safe backend suite passed: 18 tests.
- PASS — `.\mvnw.cmd package` — frontend build, tests, and Spring Boot package completed.
- PASS — `powershell -ExecutionPolicy Bypass -File .\scripts\verify-packaged-identity.ps1` — packaged identity shell started on isolated loopback H2 and passed verification.

## Residual Risks and Optional Developer Checks

- Flyway logs that the installed H2 2.4.240 version is newer than Flyway's verified H2 version. All clean-migration and packaged-startup checks passed with that installed version; no ticket-scoped defect was observed.

## Disposition

- `fixes-applied`
