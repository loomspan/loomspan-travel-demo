# Complete Stay and Rental Fixtures Testing Plan

## Change Summary

V11 will extend the existing clean Flyway catalog with deterministic March 2027 accommodations, nightly inventory, and airport rental fleets. Tests must prove the whole catalog—not only the new rows—retains valid airfare coverage, uses existing accommodation and half-open rental semantics correctly, is deterministic across clean builds, and has a concise repeatable summary command.

## Impacted Areas and Risks

| Category | Risk | Planned evidence |
| --- | --- | --- |
| Flyway lineage | Missing/incorrect V11 data or stale V10 expectations leave a clean database incomplete | clean migration, V11/version/count assertions, forward migration from identity V2 |
| Accommodation inventory | A type/destination/night is missing; capacity is not usable for 1--8; price/metadata tradeoffs collapse | complete fixture facade with 18-property/540-night, party-fit, and variation checks |
| Rental inventory | A destination/class/unit is absent, billing is confused with calendar days, or interval semantics regress | class/fleet checks, 25-hour/cross-midnight price arithmetic, physical-unit overlap test |
| Cross-catalog integrity | Airfare checks are accidentally weakened while adding new domain checks | facade composes retained airfare assertions and corruption tests cover old/new domains |
| Determinism | SQL generation/order or summary presentation differs between clean migrations | expanded ordered snapshot plus repeated summary-writer comparison |
| Summary command | command accesses a developer DB, starts a server, or produces an unreadable/nonrepeatable dump | in-memory generator design review, output-shape test, wrapper execution |
| Existing regressions | test fixtures collide with production V11 data; identity/startup expectations remain V10 | test-helper idempotence, focused Spring/Flyway tests, full safe suite, packaged shell check |

## Existing Coverage and Environment Constraints

`AirfareFixtureIntegrationTest` already migrates a fresh random H2 database, validates airfare rows, compares an ordered snapshot, and introduces representative corruptions. `AirfareFixtureIntegrityAssertions` has the settled flight zones, direction/date coverage, connection feasibility, capacity, price, and March-31 arrival checks. `RentalUnitOccupancyConstraintIntegrationTest` already exercises half-open active occupancy but currently receives test helper units.

Maven's normal lifecycle performs `npm ci` and the React build, so catalog-only verification should use `-DskipFrontend=true`. H2 and Flyway are local project dependencies; no credentials, network, supplier, or application process is required. The wrapper command must compile/run only its isolated in-memory generator. The broad packaged check starts a temporary local process and is the only planned optional-environment-style test.

## Failing Test First

- Name: `cleanMigrationProducesCompleteStayAndRentalCatalog`
- Type: Flyway integration test
- Location: `src/test/java/app/detour/catalog/StayAndRentalFixtureIntegrationTest.java`
- Arrange/Act/Assert: migrate a fresh random H2 database through the current migrations; call `CatalogFixtureIntegrityAssertions.assertValid`; assert the SFO/MUC/MEX stay and rental counts/known stable keys.
- Expected pre-fix failure: before V11 and the cross-catalog assertions, the database has zero accommodation properties, nightly rows, rental locations/classes/units, so the required counts and coverage fail.

## Tests to Add or Update

### 1. `cleanMigrationProducesCompleteStayAndRentalCatalog`

- Type: Flyway integration
- Location: `src/test/java/app/detour/catalog/StayAndRentalFixtureIntegrationTest.java`
- Proves: exactly two properties per category/destination, one valid unit per property, all March 1--30 nights, valid stable keys/metadata/prices/inventory, and all airport/class/fleet coverage.
- Inputs/fixture: a fresh random in-memory H2 URL and Flyway classpath migrations only.
- Doubles or boundary isolation: none; database is disposable and isolated.
- Edge cases: each property's unit kind matches its category; nightly availability stays within capacity; every supplier category/link and airport/destination link is valid; all active fixture occupancy count is zero.

### 2. `assertsPartyFitAndMeaningfulStayTradeoffsForOneThroughEight`

- Type: fixture assertion integration
- Location: `src/test/java/app/detour/catalog/CatalogFixtureIntegrityAssertions.java` called by `StayAndRentalFixtureIntegrationTest.java`
- Proves: for every party size 1--8, a hotel/B&B room option has `available_inventory >= ceil(party / guest_capacity)` or a whole property has `guest_capacity >= party`; complete-stay totals, ratings, and distances are not all tied and the fixture matrix creates distinct ordering outcomes.
- Inputs/fixture: V11 clean catalog and one representative 1-, 7-, and 14-night stay total calculation using 30-night coverage.
- Doubles or boundary isolation: none.
- Edge cases: party eight requires four two-person rooms, two four-person rooms, or a capacity-eight whole property; a one-person party can use a larger room/property but does not erase price/rating/distance variation.

### 3. `rentalFixturesSupportCycleBillingAndHalfOpenAvailability`

- Type: Flyway/JDBC integration
- Location: `src/test/java/app/detour/catalog/StayAndRentalFixtureIntegrationTest.java`
- Proves: each destination/class has a usable physical unit over a complete local March interval; a 25-hour SFO interval bills `2 * dailyTotal`; a short interval crossing midnight bills `1 * dailyTotal`; a same-instant return/pickup succeeds and an intersecting active interval is rejected.
- Inputs/fixture: V11 fixture units, airport zone IDs from `catalog_airport`, local `OffsetDateTime` values away from DST ambiguity (for example SFO 2027-03-10 10:00 to 2027-03-11 11:00), and existing overlap trigger.
- Doubles or boundary isolation: no booking service is introduced; direct test inserts are isolated to the test's H2 database and represent future reservation rows only.
- Edge cases: active versus released occupancy, different physical units, exact boundary equality, overlap on insert, and integer cent totals with nonnegative components.

### 4. `completeFixtureIntegrityRejectsRepresentativeCorruptions`

- Type: negative Flyway/JDBC integration
- Location: `src/test/java/app/detour/catalog/AirfareFixtureIntegrationTest.java`
- Proves: the new complete facade fails when a March nightly row is deleted, a stay is made unavailable/invalid for an intended party, a rental class/unit is removed, or a representative stable catalog key is duplicated/changed where constraints or assertions apply; existing deleted airfare, zero-price, capacity, connection, layover, and March-31 corruptions remain failing cases.
- Inputs/fixture: independently migrated H2 databases—one corruption per database to keep diagnostics deterministic.
- Doubles or boundary isolation: database triggers may be deliberately dropped only where the established airfare test already needs to create an otherwise-blocked corruption.
- Edge cases: assertion-level omissions not caught by schema constraints and database-level uniqueness/immutability checks.

### 5. `independentCleanBuildsProduceIdenticalOrderedCatalogSnapshots`

- Type: determinism integration
- Location: `src/test/java/app/detour/catalog/AirfareFixtureIntegrationTest.java`
- Proves: two clean migrations yield identical sorted snapshots covering existing airfare tables and all V11 suppliers, properties, units, nightly rows, rental locations/classes, and physical units.
- Inputs/fixture: two independently named in-memory H2 databases.
- Doubles or boundary isolation: none.
- Edge cases: generated nightly dates, integer-cent fields, keys, class-local physical identifiers, supplier categories, and metadata order.

### 6. `roundTripsStayRentalCentsAndLocalIntervalValues`

- Type: Spring/JDBC integration
- Location: `src/test/java/app/detour/catalog/CatalogTemporalMoneyRoundTripIntegrationTest.java`
- Proves: representative nightly and daily base/tax/fee cents round-trip exactly, and SFO/MUC/MEX airport zones produce expected local pickup/return dates/times.
- Inputs/fixture: V11 stable keys and `ZoneId.of` values persisted in the catalog.
- Doubles or boundary isolation: local in-memory Spring datasource.
- Edge cases: non-US zone (`Europe/Berlin` and `America/Mexico_City`), all-inclusive addition without floating point, and local-date interpretation.

### 7. `summaryOutputIsDeterministicAndConcise`

- Type: generator integration
- Location: `src/test/java/app/detour/catalog/CatalogFixtureSummaryIntegrationTest.java`
- Proves: two runs of `CatalogFixtureSummary` produce identical text containing total-count headings and representative airfare, stay, and rental options for SFO/MUC/MEX, including timing/price/capacity/rating/distance/inventory details, without listing every row.
- Inputs/fixture: generator-created isolated random H2 migrations and captured `StringBuilder`/writer output.
- Doubles or boundary isolation: the generator itself owns a random in-memory H2 database; no environment variables or file database are read.
- Edge cases: sorted output, no random URL/timestamp, all destination/type/class sections present, and concise line-count/absence-of-raw-nightly-dump assertion.

### 8. Fresh-start and forward-migration regression updates

- Type: Spring Boot and Flyway integration
- Location: `src/test/java/app/detour/DetourApplicationTest.java`, `src/test/java/app/detour/catalog/PhaseOneCatalogForwardMigrationIntegrationTest.java`, and `src/test/java/app/detour/catalog/CatalogSchemaIntegrationTest.java`
- Proves: a normal application context applies V11 with real clean counts, an identity-only V2 database migrates to V11 without losing identity data, and `CatalogSchemaFixtures` remains idempotent for negative schema tests after V11 real rows exist.
- Inputs/fixture: isolated Spring H2 URLs, a temporary file H2 forward-migration database, and test-only `test-*` keys.
- Doubles or boundary isolation: no external services; the file database is created in JUnit's temporary directory.
- Edge cases: expected version update, no seeded users, no legacy tables, helper fixture does not assume `rental_unit` is empty.

## Safe Verification Commands

- Focused: `.\mvnw.cmd -DskipFrontend=true -Dtest=StayAndRentalFixtureIntegrationTest,CatalogFixtureSummaryIntegrationTest test`
- Related suite: `.\mvnw.cmd -DskipFrontend=true -Dtest=AirfareFixtureIntegrationTest,CatalogTemporalMoneyRoundTripIntegrationTest,RentalUnitOccupancyConstraintIntegrationTest,CatalogSchemaIntegrationTest,PhaseOneCatalogForwardMigrationIntegrationTest,DetourApplicationTest test`
- Full safe suite: `.\mvnw.cmd -DskipFrontend=true test`
- Packaged regression: `.\mvnw.cmd -DskipFrontend=true package` followed by `.\scripts\verify-packaged-identity.ps1`
- Documented summary: `.\scripts\show-catalog-fixture-summary.ps1`

## Optional Developer Checks

- Run `.\scripts\show-catalog-fixture-summary.ps1` and read the compact output as a reviewer would. It is nonblocking: the test asserts its structure and determinism.

## Exit Criteria

- [x] The planned clean-migration test fails before V11 for the intended zero-fixture reason, when run against the pre-change lineage.
- [x] New and updated fixture, determinism, temporal, schema, startup, and summary tests pass after implementation.
- [x] The broadest safe relevant repository suite (`.\mvnw.cmd -DskipFrontend=true test`) passes.
- [x] Acceptance criteria map to executable data, assertion, and command evidence.
- [x] Routine tests use only isolated H2 databases and do not call live suppliers, mutate a developer database, or perform booking operations.
- [x] Full March-night coverage, party fit, rental classes/fleets, daily-cycle rounding, interval overlap, price/metadata variation, zones, airfare connections, and the March-31 flight boundary are covered.
- [x] The summary command produces a deterministic concise report and the optional visual scan is reported as nonblocking.
