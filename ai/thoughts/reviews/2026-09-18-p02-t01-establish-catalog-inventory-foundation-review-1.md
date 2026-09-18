# P02-T01 — Establish the DeTour Catalog and Inventory Foundation Code Review — Cycle 1

## Scope and Repository State

Reviewed the ticket-scoped working-tree change against `main` at `8d0dbc6`: additive `V3`/`V4` Flyway migrations, the H2 rental-occupancy trigger, catalog schema/migration/temporal tests, the clean-lineage assertion, and H2 compile availability. The review included staged, unstaged, and untracked work; there was no staged work and no unrelated implementation change to preserve. `V1` and `V2` remain unchanged.

The selected `full` profile remains appropriate: the change establishes persisted contracts, a concurrency-sensitive exclusion invariant, and a forward migration. No profile mismatch was found.

## Findings

No actionable findings.

## Findings Resolved in This Context

### [P2] Cover every persisted monetary constraint in the schema regression
- Location: `src/test/java/app/detour/catalog/CatalogSchemaIntegrationTest.java:45`
- Scenario: A later migration could remove a tax or fee nonnegative check from flight, accommodation, or rental pricing without failing the pre-review suite, which exercised only a negative flight base fare.
- Impact: Required schema-level monetary validation could regress undetected, allowing a negative all-inclusive price component into later pricing and booking work.
- Evidence: The DDL contains distinct checks for all nine stored base/tax/fee columns, but the original negative-path test covered only `flight_instance.base_fare_cents`.
- Fix: Added negative assertions for all flight, nightly-accommodation, and rental price components, plus missing negative inventory, zero accommodation-capacity, and rental-category checks. The focused and full suites pass.

## Acceptance-Criteria and Plan Conformance

| Criterion/decision | Code evidence | Test evidence | Result |
| --- | --- | --- | --- |
| Clean DeTour lineage creates normalized empty catalog/inventory without Wayfarer compatibility | Additive `V3`/`V4`; no legacy migration/data | `DetourApplicationTest` and full suite | implemented |
| A Phase 1 database migrates forward and retains users | `V1`/`V2` unchanged; later migrations are additive | `PhaseOneCatalogForwardMigrationIntegrationTest` | implemented |
| Invalid money, inventory, capacity, category, range, and reference data is rejected | DDL checks, FKs/composite FK, and occupancy trigger | Expanded `CatalogSchemaIntegrationTest`; occupancy integration test | implemented |
| Flight, stay, and rental finite-inventory models are representable | Separate schedules/instances, properties/units/nights, locations/classes/units/occupancy tables | Schema fixture and direct/one-stop, whole-property, occupancy tests | implemented |
| Shared stable identifiers can support later snapshots/revalidation | Unique `catalog_key` contracts and no user FKs | JDBC metadata/key assertions | implemented |
| Cents and time zones round trip without ambiguity | `BIGINT`, `TIMESTAMP WITH TIME ZONE`, airport IANA zones | `CatalogTemporalMoneyRoundTripIntegrationTest` | implemented |
| Identity behavior and packaged startup remain intact | Identity code remains unchanged; Flyway discovers V3/V4 | Full test suite, package, and isolated packaged smoke | implemented |
| Deferred workflows remain absent | Only migrations and a DB constraint trigger were added; no catalog API/service/UI or Trip/booking records | Source-scope inspection and identity suite | implemented |

## Active Project Guardrails

- None recorded in `ai/thoughts/design-lens.md`.

## Open Questions and Assumptions

- None.

## Verification Results

- PASS — `./mvnw.cmd -DskipFrontend=true '-Dtest=CatalogSchemaIntegrationTest,RentalUnitOccupancyConstraintIntegrationTest,CatalogTemporalMoneyRoundTripIntegrationTest,PhaseOneCatalogForwardMigrationIntegrationTest' test` — 6 tests passed.
- PASS — `./mvnw.cmd -DskipFrontend=true test` — 14 tests passed.
- PASS — `./mvnw.cmd -DskipFrontend=true package` — tests passed and executable JAR packaged.
- PASS — `powershell -ExecutionPolicy Bypass -File .\\scripts\\verify-packaged-identity.ps1` — isolated loopback packaged identity-shell smoke passed.
- PASS — `git diff --check` — no whitespace errors.

## Residual Risks and Optional Developer Checks

- The H2/Flyway test logs warn that H2 2.4.240 is newer than Flyway's verified H2 2.3.232. Current clean, forward-migration, and packaged tests pass; retain this as an environment-compatibility observation when upgrading dependencies.
- No optional developer checks.

## Disposition

- `fixes-applied`
