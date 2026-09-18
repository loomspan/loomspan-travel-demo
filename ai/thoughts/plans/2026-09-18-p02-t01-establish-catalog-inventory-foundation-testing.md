# DeTour Catalog and Inventory Foundation Testing Plan

## Change Summary

The change adds empty forward Flyway schema migrations (`V3` through `V9`) plus H2 constraint triggers. It expands the DeTour persistence contract from Phase 1 identity only to shared, application-owned reference/catalog data; dated flight capacity and direct/one-stop segment integrity; accommodation nightly availability with rating/location/distance metadata; and physical rental-unit temporal occupancy. It must leave identity HTTP/runtime behavior intact and must not seed the March 2027 catalog or add a workflow/API/UI.

## Impacted Areas and Risks

| Category | Risk | Planned evidence |
| --- | --- | --- |
| Flyway lineage | A clean database omits a table/constraint, or an installed V2 database cannot advance without losing `detour_user`. | Clean Spring/Flyway test plus a two-stage V2-to-V9 file-H2 integration test. |
| Shared ownership | A catalog table gains user ownership or has no stable retained identifier. | JDBC metadata assertions for root catalog tables, unique catalog keys, and no foreign key to `DETOUR_USER`. |
| Flight inventory | Invalid cents, capacity, segment timing, references, route/layover, or direct/one-stop shape reaches later fixture logic. | Minimal valid graph then parameterized invalid raw-JDBC inserts that must throw SQL constraint exceptions. |
| Stay inventory | A nightly record can become negative, over-capacity, orphaned, or incompatible with hotel/B&B versus whole-rental semantics, or lack valid later-sorting metadata. | Composite-key, check, and category negative cases with one valid room and whole-property control. |
| Rental availability | Overlap is accepted, adjacent intervals falsely conflict, or an update bypasses the constraint trigger. | Insert and update tests for the strict half-open overlap predicate on same/different physical units. |
| Time and money | Cents are converted to floating point, price components overflow their all-inclusive total, or offset/local time is lost around different airport zones and cross-date travel. | `BIGINT` round-trip, overflow-rejection, and `OffsetDateTime`/`ZoneId` round-trip cases for PDX, SFO, MUC, and MEX. |
| Phase 1 regression | New migration/trigger prevents current identity context, restart, or packaged shell startup. | Existing HTTP/restart tests, full backend suite, package, and isolated loopback smoke. |

## Existing Coverage and Environment Constraints

`DetourApplicationTest` is the current clean in-memory Flyway assertion and uses an isolated `jdbc:h2:mem:` URL. `ApplicationRestartIntegrationTest` already boots twice against an isolated temporary file H2 database and proves user persistence/session invalidation. `IdentityApiIntegrationTest` covers the current identity HTTP surface through `@SpringBootTest` and MockMvc. There are no catalog fixtures or persistence tests yet.

The repository test convention is JUnit 5 with Spring Boot/JDBC and the H2 database supplied by Maven; no external service, credentials, model, or network should be involved. Maven runs frontend tasks by default, so all backend-focused commands use the established `-DskipFrontend=true` property. The packaged smoke script requires a preceding package and creates/removes an isolated temporary H2 database and loopback child process.

## Failing Test First

- Name: `CatalogSchemaIntegrationTest.rejectsInvalidCatalogValuesAndReferences`
- Type: H2/Flyway JDBC integration test
- Location: `src/test/java/app/detour/catalog/CatalogSchemaIntegrationTest.java`
- Arrange/Act/Assert: Start with the existing clean Flyway test database and a minimal valid reference/supplier/flight graph; attempt an insert or update with negative cents, zero capacity, an invalid category, a missing foreign key, an invalid timestamp/date range, and nightly availability that mismatches or exceeds its unit capacity; assert each fails with an SQL constraint exception while the valid control rows remain queryable.
- Expected pre-fix failure: The current lineage has none of the catalog tables, so the first valid setup fails with table-not-found. Once tables exist without the planned checks, the corresponding invalid mutation succeeds instead of producing a constraint exception.

## Tests to Add or Update

### 1. `startsWithFreshDetourCatalogLineageAndNoFixtureData`

- Type: Spring Boot/Flyway integration regression
- Location: `src/test/java/app/detour/DetourApplicationTest.java`
- Proves: A clean database applies exactly versions `1` through `9`; catalog root tables are available but empty; no user seed or Wayfarer compatibility table appears.
- Inputs/fixture: The test's existing randomized H2 memory URL; no catalog insert fixture.
- Doubles or boundary isolation: Spring Boot uses the real Flyway migrations and H2 datasource only.
- Edge cases: Preserve the existing zero-user assertion and listed legacy-table absence rather than treating new catalog roots as legacy replacements.

### 2. `rejectsInvalidCatalogValuesAndReferences`

- Type: H2/Flyway JDBC integration
- Location: `src/test/java/app/detour/catalog/CatalogSchemaIntegrationTest.java`
- Proves: Every DDL-owned numeric, category, relationship, and temporal constraint is effective: nonnegative money/inventory, overflow-safe all-inclusive price components, positive bookable/seat capacity, `available <= capacity`, valid dates/instants, valid component categories, valid rating/distance metadata, unique keys, and non-orphan references.
- Inputs/fixture: `CatalogSchemaFixtures` inserts a minimum valid destination/airport/supplier/schedule, accommodation, and rental graph. Each negative case mutates exactly one attribute or parent relationship from that baseline.
- Doubles or boundary isolation: Raw `JdbcTemplate`/`DataSource` calls against a unique in-memory H2 database; no production catalog repository, seed migration, service, HTTP call, or live supplier.
- Edge cases: Test both flight `available_seats > seat_capacity` and accommodation nightly `available_inventory > inventory_capacity`; test price components whose sum exceeds `BIGINT` for flights, stays, and rentals; test a capacity mismatch rejected by the composite foreign key; test hotel/B&B room and vacation-rental whole-unit valid controls surrounding invalid pairings.

### 3. `retainsSharedCatalogKeysWithoutUserOwnership`

- Type: H2 metadata/JDBC integration
- Location: `src/test/java/app/detour/catalog/CatalogSchemaIntegrationTest.java`
- Proves: Catalog definitions and inventory carry stable unique catalog keys and shared reference relationships, while no new catalog/inventory foreign key points to `DETOUR_USER`.
- Inputs/fixture: Metadata from the fully migrated schema and duplicate-key insert attempts using minimal valid rows.
- Doubles or boundary isolation: Isolated H2 database with JDBC metadata APIs.
- Edge cases: Check the records later selected/snapshotted (`flight_instance`, accommodation property/unit, rental unit/class) rather than only root reference tables.

### 4. `rejectsOverlappingActiveOccupancyButAllowsHalfOpenBoundary`

- Type: H2 trigger integration
- Location: `src/test/java/app/detour/catalog/RentalUnitOccupancyConstraintIntegrationTest.java`
- Proves: The `RentalUnitOccupancyOverlapTrigger` rejects same-unit active intervals when `existing.pickup_at < candidate.return_at` and `candidate.pickup_at < existing.return_at`, permits a second interval that starts exactly at the first return instant, permits the same times on a different unit, and applies on an update as well as an insert.
- Inputs/fixture: One valid rental location/class and two physical units; fixed offset pickup/return values chosen at a DST-safe instant.
- Doubles or boundary isolation: Real Flyway-created trigger and H2 transaction; no booking/cancellation service. The test changes the occupancy marker to `RELEASED` only to prove released records do not reserve a unit, not to exercise a workflow.
- Edge cases: Equal endpoints (allowed), one-second overlap (rejected), complete containment (rejected), exact duplicate interval (rejected), and update from non-overlapping to overlapping (rejected).

### 5. `roundTripsUsdCentsAndAirportZonedFlightInstants`

- Type: H2/JDBC temporal and monetary integration
- Location: `src/test/java/app/detour/catalog/CatalogTemporalMoneyRoundTripIntegrationTest.java`
- Proves: Integer-cent price components come back unchanged without decimal/float conversion; flight-segment and itinerary instants come back as equal `OffsetDateTime` values; retained airport IANA zone IDs derive the intended local values.
- Inputs/fixture: Explicit PDX (`America/Los_Angeles`), SFO, MUC (`Europe/Berlin`), and MEX (`America/Mexico_City`) airport rows; a direct and a cross-date/international segment using fixed `OffsetDateTime` values and nonzero base/tax/fee cents.
- Doubles or boundary isolation: JDBC/H2 only; `ZoneId.of` validates the stored zone IDs in test code.
- Edge cases: Positive offset (MUC), negative offsets (PDX/SFO/MEX), a local-date transition, and a value near the `BIGINT` range suitable for application money limits without overflowing the SQL column.

### 6. `migratesVersionTwoIdentityDatabaseForwardWithoutDataLoss`

- Type: File-backed H2/Flyway migration integration
- Location: `src/test/java/app/detour/catalog/PhaseOneCatalogForwardMigrationIntegrationTest.java`
- Proves: Applying the existing migration location with Flyway `target("2")`, inserting a fully formed `detour_user`, then rerunning the complete migration path leaves the account intact and records versions through `9`.
- Inputs/fixture: A JUnit `@TempDir` H2 file URL and one direct SQL user insert matching `V2`.
- Doubles or boundary isolation: Explicit Flyway instances configured to the repository migration location and temporary local file only.
- Edge cases: Verify identity password hash and creation timestamp, not merely row count; assert no migration file is edited and no user/catalog ownership link is created.

### 7. `persistsAccountButRejectsPreRestartSession` and identity API suite

- Type: Existing Spring Boot HTTP/restart regression
- Location: `src/test/java/app/detour/identity/ApplicationRestartIntegrationTest.java` and `src/test/java/app/detour/identity/IdentityApiIntegrationTest.java`
- Proves: New migrations run at normal application boot without changing registration, login, CSRF, protected profile behavior, persisted account data, or in-memory session restart behavior.
- Inputs/fixture: Existing random in-memory/file H2 URLs and HTTP/MockMvc data.
- Doubles or boundary isolation: Existing loopback HTTP and MockMvc test setup; catalog remains unseeded.
- Edge cases: The migrated app must retain an identity-only external surface: no catalog endpoint/UI behavior is introduced as an accidental side effect.

## Safe Verification Commands

- Focused: `./mvnw.cmd -DskipFrontend=true -Dtest=CatalogSchemaIntegrationTest,RentalUnitOccupancyConstraintIntegrationTest,CatalogTemporalMoneyRoundTripIntegrationTest,PhaseOneCatalogForwardMigrationIntegrationTest test`
- Related suite: `./mvnw.cmd -DskipFrontend=true -Dtest=DetourApplicationTest,ApplicationRestartIntegrationTest,IdentityApiIntegrationTest test`
- Full safe suite: `./mvnw.cmd -DskipFrontend=true test`
- Packaging/startup gate: `./mvnw.cmd -DskipFrontend=true package` followed by `./scripts/verify-packaged-identity.ps1`

## Optional Developer Checks

- None. The planned tests use only isolated in-memory or temporary file H2 databases; the packaged smoke uses an isolated loopback port and temporary H2 location.

## Exit Criteria

- [ ] The planned red test fails for the intended missing-schema or missing-constraint reason before implementation.
- [x] Clean Flyway verification proves versions `1`–`9`, normalized empty catalog structures, zero seeded users/catalog fixtures, and no Wayfarer compatibility tables.
- [x] A version-`2` DeTour database migrates forward while retaining a registered identity record.
- [x] JDBC tests demonstrate all required DDL checks, foreign/composite-key constraints, stable-key uniqueness, shared catalog ownership, and category/range rejection.
- [x] Rental tests demonstrate the half-open interval rule, including back-to-back acceptance and same-unit overlap rejection on inserts and updates.
- [x] Money and temporal tests preserve `BIGINT` cents, explicit instants/offsets, airport zone IDs, and local presentation across all supported airport zones.
- [x] Existing identity HTTP/restart tests and the full safe backend suite pass.
- [x] The package and isolated packaged-identity smoke pass without external supplier/model access.
- [x] Acceptance criteria map to executable evidence; no routine test writes to the default local database or introduces a live/destructive side effect.
- [x] Any optional check is reported as nonblocking and is not represented as already performed.
