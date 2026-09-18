# P02-T01 — Establish the DeTour Catalog and Inventory Foundation Code Review — Cycle 6

## Scope and Repository State

Reviewed the ticket-scoped changes relative to `8d0dbc6` on `main`, including tracked, unstaged, and untracked migrations, H2 triggers, catalog integration tests, build metadata, ticket/planning artifacts, and the existing identity regression surface. `V1` and `V2` remain unchanged; no catalog API, UI, Trip, booking, payment, or supplier integration was introduced. Previous review documents were intentionally not read.

The Full 5-Step Pipeline remains appropriate: this is a persisted, forward-only Flyway contract with H2 trigger behavior and later inventory/concurrency implications.

## Findings

No actionable findings remain.

## Findings Resolved in This Context

### [P2] Prevent all-inclusive price overflow
- Location: `src/main/resources/db/migration/V9__prevent_all_inclusive_price_overflow.sql:1`
- Scenario: Before this review, a flight, nightly stay, or rental class could store nonnegative `BIGINT` base, tax, and fee components whose sum exceeded `BIGINT` (for example, base `9223372036854775807` plus one cent tax).
- Impact: A catalog row accepted by the schema could not produce a correct deterministic all-inclusive USD-cent total; a later total calculation would overflow or fail.
- Evidence: The prior constraints validated each component independently only. The added negative cases in `CatalogSchemaIntegrationTest` exercise this boundary for all three priced component families.
- Fix: Added additive `V9` checks requiring each component sum to fit in `BIGINT`, updated clean/forward-lineage expectations to version `9`, and updated plan references.

## Acceptance-Criteria and Plan Conformance

| Criterion/decision | Code evidence | Test evidence | Result: implemented/partial/missing/safe deviation |
| --- | --- | --- | --- |
| Clean DeTour lineage creates normalized empty catalog/inventory without Wayfarer compatibility | Additive `V3`–`V9`; `V1`/`V2` unchanged | `DetourApplicationTest`; full suite | implemented |
| Phase 1 migrates forward without user loss | Additive migrations only | `PhaseOneCatalogForwardMigrationIntegrationTest` targets V2 then reaches V9 | implemented |
| Reject invalid money, inventory, capacity, temporal, category, relationship, and overflow values | DDL checks, composite FKs, H2 triggers, `V9` total checks | `CatalogSchemaIntegrationTest`; occupancy test | implemented |
| Represent direct/one-stop flights, lodging/unit/night inventory, and unit interval occupancy | Flight, accommodation, rental tables plus integrity triggers | Catalog schema, temporal, and occupancy integration tests | implemented |
| Preserve shared stable identifiers without user ownership | Unique catalog keys and immutable-key triggers; no `detour_user` FK | Catalog metadata and key-update tests | implemented |
| Preserve cents and zoned time semantics | `BIGINT`, `TIMESTAMP WITH TIME ZONE`, airport IANA zone IDs | `CatalogTemporalMoneyRoundTripIntegrationTest` | implemented |
| Preserve Phase 1 runtime behavior and packaged startup | Identity code and HTTP surface unchanged | Full backend suite, package, and loopback packaged smoke | implemented |
| Keep deferred workflows out of scope | DDL/test-only fixtures and persistence triggers only | Source-scope review; existing identity API suite | implemented |

## Active Project Guardrails

- None recorded in `ai/thoughts/design-lens.md`.

## Open Questions and Assumptions

- None.

## Verification Results

- PASS — `& .\mvnw.cmd '-DskipFrontend=true' '-Dtest=CatalogSchemaIntegrationTest,RentalUnitOccupancyConstraintIntegrationTest,CatalogTemporalMoneyRoundTripIntegrationTest,PhaseOneCatalogForwardMigrationIntegrationTest,DetourApplicationTest' test` — 7 tests passed after the V9 fix.
- PASS — `& .\mvnw.cmd '-DskipFrontend=true' test` — 14 backend tests passed.
- PASS — `& .\mvnw.cmd '-DskipFrontend=true' package` — executable JAR packaged successfully; 14 tests passed during package.
- PASS — `powershell -ExecutionPolicy Bypass -File .\scripts\verify-packaged-identity.ps1` — isolated loopback packaged identity shell verification passed.

## Residual Risks and Optional Developer Checks

- H2 `2.4.240` is newer than Flyway's logged verified H2 version `2.3.232`; all migrations and tests passed on the configured version. The schema remains intentionally H2-specific for its trigger contracts.
- No optional developer checks.

## Disposition

- `fixes-applied`
