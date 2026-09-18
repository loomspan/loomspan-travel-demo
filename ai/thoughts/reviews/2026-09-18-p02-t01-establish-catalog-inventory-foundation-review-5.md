# P02-T01 — Establish the DeTour Catalog and Inventory Foundation Code Review — Cycle 5

## Scope and Repository State

Reviewed the ticket, research, implementation plan, testing plan, active design lens, all catalog migrations (`V3`–`V8`), H2 trigger implementations, catalog tests, connected identity tests/configuration, and the complete staged, unstaged, and untracked working tree. The comparison base is `origin/main` / `8d0dbc6`; all production schema, trigger, test, and ticket/planning artifacts listed by the working tree are ticket-scoped. There were no active project-specific guardrails.

The initial review found two ticket-scoped integrity gaps. This context applied the smallest forward-only fix as `V8`, added regression coverage, and completed a post-fix review. No actionable findings remain.

## Findings

No actionable findings.

## Findings Resolved in This Context

### [P2] Reject invalid same-day recurring schedule ranges
- Location: `src/main/resources/db/migration/V3__create_shared_catalog_and_flight_schema.sql:86`
- Scenario: A schedule segment with `arrival_day_offset = 0`, departure at 12:00, and arrival at 11:00 satisfied the original independent column checks.
- Impact: A supposedly recurring direct/one-stop schedule could encode an impossible local time range and feed malformed fixture generation or later presentation.
- Evidence: The original DDL constrained only the arrival-day value and positive duration; it had no relationship between the two local times.
- Fix: `V8` adds `ck_flight_schedule_segment_local_times`; `CatalogSchemaIntegrationTest` proves the invalid update is rejected.

### [P2] Preserve retained catalog identifiers against updates
- Location: `src/main/resources/db/migration/V3__create_shared_catalog_and_flight_schema.sql:2`
- Scenario: Any unique `catalog_key` could be updated to a new unused value after a later snapshot or deterministic fixture reference had retained it.
- Impact: The required stable, immutable identifier contract was only partially enforced and could break snapshot/revalidation references.
- Evidence: Unique constraints reject duplicates but allow non-conflicting updates; no previous trigger or check prohibited them.
- Fix: `ImmutableCatalogKeyTrigger`, registered by forward-only `V8` on all retained catalog entities, rejects key changes. JDBC assertions cover destination, flight instance, accommodation unit, and rental unit representatives.

## Acceptance-Criteria and Plan Conformance

| Criterion/decision | Code evidence | Test evidence | Result |
| --- | --- | --- | --- |
| Clean forward lineage with no Wayfarer compatibility | Additive `V3`–`V8`; no edits to `V1`/`V2` | `DetourApplicationTest` | implemented |
| V2 database migrates forward and preserves users | Additive migrations leave `detour_user` independent | `PhaseOneCatalogForwardMigrationIntegrationTest` | implemented |
| Schema rejects invalid values, ranges, categories, references, and capacity overflow | Checks, foreign/composite keys, occupancy and flight triggers, V8 local-time check | `CatalogSchemaIntegrationTest`, `RentalUnitOccupancyConstraintIntegrationTest` | implemented |
| Flight, stay, and rental finite-inventory models | Separate schedule/instance/segment, property/unit/night, and location/class/unit/occupancy tables | Catalog schema and occupancy tests | implemented |
| Stable shared identifiers | Unique catalog keys plus V8 immutable-key trigger; no catalog foreign key to `detour_user` | Metadata and update-rejection assertions | implemented |
| Integer-cent money and zoned timing | `BIGINT` cents, `TIMESTAMP WITH TIME ZONE`, airport IANA zone IDs | `CatalogTemporalMoneyRoundTripIntegrationTest` | implemented |
| Phase 1 runtime and packaged startup remain intact | Identity code/routes unchanged | Full backend suite, package, loopback smoke | implemented |
| Deferred workflows remain absent | DDL/constraint-trigger-only catalog change; no catalog endpoint, service, UI, booking, or payment code | Source-scope review and identity suite | implemented |

## Active Project Guardrails

- None recorded.

## Open Questions and Assumptions

- None.

## Verification Results

- PASS — `.\mvnw.cmd -DskipFrontend=true "-Dtest=DetourApplicationTest,CatalogSchemaIntegrationTest,RentalUnitOccupancyConstraintIntegrationTest,CatalogTemporalMoneyRoundTripIntegrationTest,PhaseOneCatalogForwardMigrationIntegrationTest" test` — 7 tests passed against isolated H2 databases.
- PASS — `.\mvnw.cmd -DskipFrontend=true test` — 14 tests passed.
- PASS — `.\mvnw.cmd -DskipFrontend=true package` — package and its complete test suite passed.
- PASS — `powershell -ExecutionPolicy Bypass -File .\scripts\verify-packaged-identity.ps1` — isolated loopback packaged identity smoke passed.
- PASS — `git diff --check` — no whitespace errors in tracked changes.

## Residual Risks and Optional Developer Checks

- Flyway reports that H2 `2.4.240` is newer than its verified `2.3.232`; all required isolated H2 verification passed, so this is a dependency-compatibility observation rather than an open ticket finding.
- Optional developer checks: none.

## Disposition

- `fixes-applied`
