# P02-T02 — Seed Complete Deterministic Airfare Fixtures Testing Plan

## Change Summary

The change adds a Flyway V10-generated March 2027 airfare catalog: 3 destinations, 9 airports, 2 fictional airlines, 24 schedules, 720 dated flight instances, and 1,080 actual segments. It must be deterministic, supply four choices for each usable PDX direction/date/destination combination, preserve real airport/time-zone/connection facts, and remain an airfare-data-only change.

## Impacted Areas and Risks

| Category | Risk | Planned evidence |
| --- | --- | --- |
| Flyway data generation | A missing date, duplicate schedule/date, or accidental all-March range produces silent search gaps or extra unusable choices. | Cross-product assertions for outbound March 1–30 and inbound March 2–31, exact counts, and independent rebuild snapshots. |
| Zoned schedule data | Hard-coded offsets or wrong local-date conversion fails around US/German DST transitions or creates a post-March arrival. | `ZoneId` reconstruction, local-time/offset checks on boundary dates, actual duration/chronology checks, and final-arrival mutation test. |
| Connection policy | A structurally connected route uses the wrong airport or has an impractical layover. | Required destination-to-connection mapping plus 45-minute layover assertions and deliberate route/timing corruption. |
| Pricing and seats | A zero/negative total, capacity that cannot support the product's party size, or accidental age differentiation breaks later tally/booking work. | Integer-cent and 1–8 party arithmetic, mixed-age equality, capacity/availability assertions, and invalid price/capacity cases. |
| Stable catalog contract | Nondeterministic values or changed keys destabilize later ordering, snapshots, and references. | Ordered two-database snapshot comparison, key uniqueness/immutability assertions, and duplicate-key constraint case. |
| Existing regression suite | Tests expecting empty catalog tables or fixed generated IDs fail or hide migration regressions. | Updated startup/forward-migration/schema/temporal tests and the full backend suite. |
| Operational startup | The packaged JAR applies V10 on an isolated database and still serves the identity shell without external dependencies. | Maven package plus existing loopback `verify-packaged-identity.ps1` check. |

## Existing Coverage and Environment Constraints

The repository uses JUnit Jupiter/Spring Boot integration tests with H2 and Flyway. `CatalogSchemaIntegrationTest` already covers schema constraints but creates private fixtures; `CatalogTemporalMoneyRoundTripIntegrationTest` verifies `OffsetDateTime` persistence; `PhaseOneCatalogForwardMigrationIntegrationTest` tests a V2-to-current forward migration; and `DetourApplicationTest` asserts startup/current migration state. None proves generated airfare coverage, policy connections, determinism, or the March boundary.

The safe backend command is Maven with `-DskipFrontend=true`; Java 25 and the required Maven/H2 artifacts are present in this checkout. Full packaging runs the existing frontend build and may require Node/npm. Packaged verification only creates/removes its own temporary loopback H2 database and child process; it does not call suppliers or model services.

## Failing Test First

- Name: `AirfareFixtureIntegrationTest.cleanMigrationProducesCompleteDeterministicAirfareCatalog`
- Type: Flyway/H2 integration test
- Location: `src/test/java/app/detour/catalog/AirfareFixtureIntegrationTest.java`
- Arrange/Act/Assert: Create a UUID-named in-memory H2 datasource, apply the complete classpath Flyway lineage, run `AirfareFixtureIntegrityAssertions.assertValid(...)`, then assert 24 schedules, 720 instances, 1,080 segments, expected first/last service dates, and representative stable keys/values.
- Expected pre-fix failure: Before V10 exists, the fresh V1–V9 catalog has zero reference/flight rows, so the required airport/count/coverage assertion fails. This is the smallest acceptance-criteria test demonstrating the missing capability.

## Tests to Add or Update

### 1. `cleanMigrationProducesCompleteDeterministicAirfareCatalog`

- Type: Flyway/H2 integration test
- Location: `src/test/java/app/detour/catalog/AirfareFixtureIntegrationTest.java`
- Proves: The clean migration creates the exact catalog counts, every required destination/direction/usable service date gets 2 direct plus 2 one-stop options, no unused March 1 inbound or March 31 outbound service date is seeded, and representative public display fields/cents/capacities are present.
- Inputs/fixture: Two fresh UUID-named `jdbc:h2:mem:` databases migrated through classpath `db/migration`; no application fixture helper and no network.
- Doubles or boundary isolation: Direct Flyway/H2 boundary, isolated memory databases.
- Edge cases: Outbound March 1/March 30, inbound March 2/March 31, all three destinations, both directions, direct and one-stop counts.

### 2. `fixtureRowsHaveValidZonesRoutesDurationsAndMarchBoundary`

- Type: Flyway/H2 integration test using a package-private assertion helper
- Location: `src/test/java/app/detour/catalog/AirfareFixtureIntegrationTest.java` and `src/test/java/app/detour/catalog/AirfareFixtureIntegrityAssertions.java`
- Proves: Every airport zone is accepted by `ZoneId.of`; timestamps reconstruct each leg’s local schedule time/service-date offset; segment endpoints are contiguous; destination connections are exactly SEA/SLC, SEA/ORD, and LAX/DFW; each one-stop layover is at least 45 minutes; final arrival minus first departure is the authoritative elapsed duration; and final arrivals do not exceed March 31.
- Inputs/fixture: Entire V10 migration result, including dates during the March 14 US and March 28 Berlin DST transitions.
- Doubles or boundary isolation: None—read persisted fixture rows through JDBC.
- Edge cases: MUC overnight outbound rows, cross-zone connection rows, US-only and Germany-after-DST dates, March 30 outbound and March 31 inbound final arrivals.

### 3. `fixtureUsesOnePerTravelerFareAndMeaningfulComparisonVariation`

- Type: Flyway/H2 integration test
- Location: `src/test/java/app/detour/catalog/AirfareFixtureIntegrationTest.java`
- Proves: Each choice has a positive all-inclusive per-traveler amount and initial capacity/availability at least eight; multiplying that one amount by each party size 1–8 yields the whole-party total for representative mixed infant/child/teen/adult age lists with no age adjustment; and the fixture exposes independent differences in all-inclusive price, derived duration, departure instant, and stop count.
- Inputs/fixture: All V10 instances and fixed representative age arrays held in test source only.
- Doubles or boundary isolation: No age model is invented; the test verifies the sole persisted fare row and arithmetic expected by later phases.
- Edge cases: Lowest price is connecting, shortest/fewest-stop choice is direct, earliest departure differs from both, maximum supported party size.

### 4. `independentCleanBuildsProduceIdenticalOrderedAirfareSnapshots`

- Type: Flyway/H2 integration test
- Location: `src/test/java/app/detour/catalog/AirfareFixtureIntegrationTest.java`
- Proves: Independent clean migrations yield the same ordered airport/supplier/schedule/instance/segment catalog-key snapshots, display values, cents, capacities, dates, and offset-aware actual timestamps.
- Inputs/fixture: Two independently named in-memory H2 databases.
- Doubles or boundary isolation: Snapshot SQL explicitly orders by immutable semantic keys and segment ordinal, never generated identity IDs.
- Edge cases: Representative direct/one-stop and international/overnight rows plus the full row count.

### 5. `fixtureIntegrityRejectsRepresentativeCorruptions`

- Type: Flyway/H2 integration test, parameterized or isolated test methods
- Location: `src/test/java/app/detour/catalog/AirfareFixtureIntegrationTest.java`
- Proves: Automated checking fails after deliberately corrupting one clean test database per case: deleting a required instance (coverage); replacing a connection endpoint after disabling the test database’s structural trigger (connection policy); shrinking a layover below 45 minutes while retaining chronological order (timing); zeroing the three fare inputs (positive total); making availability inadequate for an eight-person party or attempting zero capacity (capacity/helper or H2 constraint); duplicating a key (unique constraint); and moving a final arrival to April 1 (boundary helper).
- Inputs/fixture: Fresh migration per mutation.
- Doubles or boundary isolation: Mutations occur only in temporary in-memory test databases. When a corruption is prohibited by an existing H2 constraint, `assertThrows` is the evidence that the automated integrity boundary rejects it; when structurally permissible but fixture-invalid, the assertion helper must reject it.
- Edge cases: Different validation owner (schema constraint, trigger, or complete-fixture verifier) for each invariant.

### 6. `startsWithFreshDetourCatalogLineageAndSeededAirfareData`

- Type: Spring Boot/Flyway startup integration test update
- Location: `src/test/java/app/detour/DetourApplicationTest.java`
- Proves: Application startup applies V1–V10, has no seeded users, contains the airfare/reference data owned by this ticket, leaves P02-T03 tables unseeded, and retains legacy-table absence.
- Inputs/fixture: Test class’s existing isolated H2 URL.
- Doubles or boundary isolation: Spring application context plus real Flyway migration.
- Edge cases: Migration version update and catalog-owned versus still-unseeded table distinction.

### 7. `migratesVersionTwoIdentityDatabaseForwardWithoutDataLoss`

- Type: File-backed Flyway integration test update
- Location: `src/test/java/app/detour/catalog/PhaseOneCatalogForwardMigrationIntegrationTest.java`
- Proves: An existing V2 identity row remains intact after V10 and the seeded flight catalog is available.
- Inputs/fixture: Existing `@TempDir` file H2 database.
- Doubles or boundary isolation: Real Flyway forward migration, no external service.
- Edge cases: User persistence plus final migration version 10.

### 8. Existing catalog schema and temporal tests

- Type: Spring Boot/H2 integration test updates
- Location: `src/test/java/app/detour/catalog/CatalogSchemaFixtures.java`, `CatalogSchemaIntegrationTest.java`, and `CatalogTemporalMoneyRoundTripIntegrationTest.java`
- Proves: Existing constraints, immutable keys, and zone/money round trips still hold when product airfare fixtures exist.
- Inputs/fixture: Stable V10 semantic keys plus idempotent isolated `test-*` non-airfare rows needed by existing accommodation/rental schema assertions.
- Doubles or boundary isolation: Existing class-local random H2 databases; use catalog-key lookups rather than numeric identities.
- Edge cases: Invalid test inserts use unseeded test dates and cannot collide with the generated schedule/date pairs.

## Safe Verification Commands

- Focused: `.\mvnw.cmd -DskipFrontend=true -Dtest=AirfareFixtureIntegrationTest test`
- Related suite: `.\mvnw.cmd -DskipFrontend=true -Dtest=AirfareFixtureIntegrationTest,DetourApplicationTest,PhaseOneCatalogForwardMigrationIntegrationTest,CatalogSchemaIntegrationTest,CatalogTemporalMoneyRoundTripIntegrationTest test`
- Full safe suite: `.\mvnw.cmd test -DskipFrontend=true`
- Package: `.\mvnw.cmd package`
- Packaged startup: `.\scripts\verify-packaged-identity.ps1` (run only after the package command)

## Optional Developer Checks

- None.

## Exit Criteria

- [ ] The planned red test fails for the intended missing-fixture reason before implementation.
- [ ] New and updated tests pass after implementation.
- [ ] The broadest safe relevant repository test suite passes.
- [ ] Acceptance criteria map to executable evidence.
- [ ] Routine automated tests use isolated H2 databases and do not perform live or destructive operations.
- [ ] Coverage, timing, required connections, pricing, capacity, identifiers, determinism, party arithmetic, and final-arrival edge cases are covered.
- [ ] Packaged JAR startup succeeds against an isolated temporary database with no external supplier or model service.
- [ ] Any optional check is reported as nonblocking and is not represented as already performed.
