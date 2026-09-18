# DeTour Catalog and Inventory Foundation Implementation Plan

## Overview

- Ticket: `ai/thoughts/tickets/2026-09-18-p02-t01-establish-catalog-inventory-foundation.md`
- Research: `ai/thoughts/research/2026-09-18-p02-t01-establish-catalog-inventory-foundation.md`
- Outcome: Add forward-only, H2/Flyway-managed shared catalog and finite-inventory schema contracts for Phase 2 without adding catalog fixtures or any user workflow.

## Current State

The active DeTour lineage is `V1__detour_platform.sql` and `V2__create_detour_user_identity.sql`; `V2` owns the independent `detour_user` table. Spring Boot discovers migrations from the default classpath location and uses JDBC/H2, with no ORM, catalog adapter, catalog endpoint, or catalog test fixture currently present. `DetourApplicationTest` currently asserts exactly the two existing migration versions, so it must become a full-lineage schema test without weakening the legacy-table or empty-user checks. `JdbcDetourUserRepository` is the only JDBC repository and must remain untouched because catalog data is application-owned rather than user-owned.

## Desired End State

Forward migrations create the normalized, empty catalog foundation below. It can subsequently be seeded with the fixed March 2027 data and can retain stable catalog keys for later snapshots and authoritative revalidation, but this ticket neither inserts the Phase 2 fixtures nor exposes or mutates them through the application.

- The schema represents real-airport/destination reference data and fictional suppliers; no catalog table has a `detour_user` or future Trip ownership relationship.
- Recurring flight schedules and their one-or-two-segment definitions are distinct from dated, explicitly offset flight instances and their segment times. An instance carries the per-seat USD-cent amounts and remaining/total seat values, with `0 <= available_seats <= seat_capacity`.
- Accommodation properties and bookable units are distinct from nightly availability. Unit capacity is positive; every night carries the matching immutable inventory capacity through a composite foreign key and may expose only `0..capacity` availability.
- Rental locations, vehicle classes, and physical rental units are distinct from time-bounded unit-occupancy records. A database trigger rejects overlapping active intervals for the same unit while allowing `return_at == next_pickup_at`.
- All catalog monetary fields are nonnegative `BIGINT` USD cents whose base, tax, and fee components have an overflow-safe all-inclusive sum. Airport IANA zone IDs are retained beside `TIMESTAMP WITH TIME ZONE` instants so later code can derive unambiguous local presentation.
  - A clean database applies versions `1` through `9`; an existing version-`2` DeTour database migrates forward and retains its registered user. No Wayfarer object, compatibility schema, or user-facing behavior is added.

## Scope

### In scope

- Forward Flyway migrations, the H2 trigger class needed to enforce rental interval exclusion, and tests for schema, migration compatibility, money, and time semantics.
- Normalized reference, flight, accommodation, rental, inventory, and occupancy structures with database-enforced relational/value constraints.
- Updating existing clean-database migration expectations while retaining the Phase 1 identity regression coverage.

### Out of scope

- Phase 2 fixture rows, flight generation, fixture summaries, repository APIs, search/selection UI or HTTP endpoints.
- Trip/itinerary/snapshot tables, booking/cancellation services, inventory mutation workflows, payment, supplier integration, dynamic pricing, multi-currency, or dates outside March 2027.
- Any edit to `V1` or `V2`, Wayfarer fallback, or migration of disposable Wayfarer state.

## Active Project Guardrails

None recorded in `ai/thoughts/design-lens.md`.

## Impact and Risk Analysis

- **Flyway compatibility:** Existing installations have versions `1` and `2` recorded. Only additive `V3` through `V9` migrations preserve the identity table and prevent Flyway checksum/history breakage.
- **Shared-data boundary:** Catalog/inventory is intentionally not user-owned. Foreign keys from the new tables to `detour_user` would violate the architecture and would complicate later authorization boundaries.
- **Capacity integrity:** SQL `CHECK` constraints work for values held in one row, but nightly availability must also be tied to the unit's capacity. A composite foreign key containing the immutable capacity avoids an unenforceable cross-table comparison.
- **Rental exclusion:** H2 does not provide a portable declarative temporal exclusion constraint. An H2 `Trigger` that locks the physical-unit row before checking the half-open predicate is the smallest database-level enforcement available on the retained platform; later reservation transactions can reuse the same serialization point.
- **Temporal fidelity:** `TIMESTAMP WITH TIME ZONE` preserves an instant/explicit offset, not an airport presentation zone name. Store each airport's IANA zone ID separately and round-trip instants as `OffsetDateTime`; tests must cover values across the supported zones and a cross-date arrival.
- **No seed leakage:** Test-only JDBC inserts may create minimal valid catalog rows to exercise constraints, but migrations must create only schema/constraint objects in this work package.

## Implementation Approach

Use two forward migrations so shared reference/flight definitions and finite-inventory domains remain readable without combining fixture values with DDL:

1. `V3` creates the empty shared catalog reference layer and flight schedule/instance model. All externally retained identifiers are stable, unique `catalog_key` values in addition to surrogate primary keys; later snapshots can retain the catalog key while joins remain relational.
2. `V4` creates accommodation, rental, nightly/seat inventory structures and installs an H2 constraint trigger for rental occupancy. `available_*` values remain in the same row as their capacity where possible; accommodation nights reference `(unit_id, inventory_capacity)` so their declared capacity must equal the catalog unit's capacity.
3. `V5` adds the accommodation location, rating, and city-center-distance fields that the settled fixture and later stay-sorting contract require, without rewriting the prior schema migration.
4. `V6` adds H2 triggers that reject route-mismatched or excessive schedule/instance segment rows and infeasible one-stop layovers.
5. `V7` extends those segment triggers to reject destructive changes to a completed direct/one-stop shape without rewriting an already-applied migration.
6. `V8` rejects same-day recurring segments whose local arrival is not after departure and prevents retained catalog keys from changing after creation.
7. `V9` rejects price-component combinations whose all-inclusive USD-cent total would exceed `BIGINT`.

Retain the existing JDBC/Flyway pattern rather than adding JPA or premature catalog repositories. Put the minimal H2 `Trigger` implementation under a catalog-persistence package and make H2 available at compile time, because Flyway references that Java trigger class at runtime. Do not use a generic availability table: the three component families have different aggregation and temporal semantics, and separate normalized tables keep Phase 2 fixtures compact and Phase 6 reservation behavior explicit.

## Phase 1: Define Shared Catalog and Flight Contracts

### Changes

- [x] `pom.xml` — change the retained H2 dependency from runtime-only to compile availability so `org.h2.api.Trigger` can be implemented; retain the same H2 runtime artifact and do not introduce an ORM or external dependency.
- [x] `src/main/resources/db/migration/V3__create_shared_catalog_and_flight_schema.sql` — add empty application-owned `catalog_destination`, `catalog_airport`, and `catalog_supplier` tables. Give each a surrogate key plus a unique immutable `catalog_key`; retain IATA code, geography, and IANA `time_zone_id` on airports, destination association where applicable, and a checked supplier category (`AIRLINE`, `LODGING`, or `CAR_RENTAL`).
- [x] `src/main/resources/db/migration/V3__create_shared_catalog_and_flight_schema.sql` — add `flight_schedule`, `flight_schedule_segment`, `flight_instance`, and `flight_instance_segment`. Model recurring local schedule details separately from dated segment/itinerary instants; enforce segment ordinal/count and allowed stop count (`0` or `1`), airport/supplier foreign keys, nonnegative base/tax/fee cents, `departure_at < arrival_at`, positive duration/capacity, and `0 <= available_seats <= seat_capacity`. Keep the dated instance's stable catalog key unique for future snapshot/sort use.
- [x] `src/test/java/app/detour/DetourApplicationTest.java` — update `startsWithFreshDetourIdentityMigrationAndNoSeededPrivilege` to expect the complete versions `1`–`9`, assert the catalog root tables exist but contain no fixture data, and preserve the identity-empty and legacy-absence assertions.
- [x] `src/test/java/app/detour/catalog/CatalogSchemaFixtures.java` — add test-only JDBC helpers that insert the smallest valid reference/supplier/schedule graph needed by later schema tests; these helpers are deliberately not a production seed migration.

### Automated verification

- [x] `./mvnw.cmd -DskipFrontend=true -Dtest=DetourApplicationTest,CatalogSchemaIntegrationTest test` — a clean H2 database applies the complete lineage and exposes the constrained empty catalog schema.

### Optional developer checks

- [x] None.

**Success criteria:** A brand-new in-memory database reaches Flyway version `9`, retains an empty `detour_user` table, contains no legacy Wayfarer table, and accepts a valid direct/one-stop flight graph while rejecting the tested invalid values and references.

## Phase 2: Add Stay, Rental, and Temporal Inventory Enforcement

### Changes

- [x] `src/main/resources/db/migration/V4__create_catalog_inventory_schema.sql` — add `accommodation_property`, `accommodation_unit`, and `accommodation_nightly_inventory`. Check lodging categories (`HOTEL`, `BED_AND_BREAKFAST`, `VACATION_RENTAL`) and the permitted room-versus-whole-property unit pairing; enforce positive bookable guest and inventory capacity, nonnegative nightly base/tax/fee cents, valid nightly dates, and `0 <= available_inventory <= inventory_capacity`. Use a composite foreign key from the night row to `(accommodation_unit_id, inventory_capacity)` so a night cannot claim a different or larger capacity than its unit.
- [x] `src/main/resources/db/migration/V4__create_catalog_inventory_schema.sql` — add `rental_location`, `rental_vehicle_class`, `rental_unit`, and `rental_unit_occupancy`. Bind locations to destination airports, restrict vehicle class category to `ECONOMY`, `STANDARD`, or `SUV`, keep daily price inputs as nonnegative USD cents, require physical-unit ownership by a class, require `pickup_at < return_at`, and retain a checked `ACTIVE`/`RELEASED` occupancy marker for a later reservation transaction to release exactly once. Add the lookup index used by overlap checks.
- [x] `src/main/java/app/detour/catalog/persistence/RentalUnitOccupancyOverlapTrigger.java` — implement H2's `Trigger` contract for insert/update of `rental_unit_occupancy`: lock the referenced physical unit, reject an `ACTIVE` interval when an existing active interval satisfies `existing.pickup_at < candidate.return_at AND candidate.pickup_at < existing.return_at`, and permit a boundary-touching return/pickup pair. Keep it a persistence invariant only; do not add a booking service, endpoint, or lifecycle API.
- [x] `src/main/resources/db/migration/V4__create_catalog_inventory_schema.sql` — register `RentalUnitOccupancyOverlapTrigger` as the table's before-insert/before-update constraint trigger after creating the rental tables.
- [x] `src/test/java/app/detour/catalog/CatalogSchemaIntegrationTest.java` — use only isolated H2/JDBC fixture rows to prove check, unique, and foreign-key rejection for negative cents/inventory, zero capacity, invalid categories, mismatched or above-capacity nightly inventory, invalid flight/rental times, and orphaned references; include valid contrasting inserts.
- [x] `src/test/java/app/detour/catalog/RentalUnitOccupancyConstraintIntegrationTest.java` — prove the trigger rejects intersecting intervals for one physical unit and accepts back-to-back half-open intervals and intervals for different units.
- [x] `src/test/java/app/detour/catalog/CatalogTemporalMoneyRoundTripIntegrationTest.java` — persist representative integer-cent values and explicitly offset timestamps for PDX, SFO, MUC, and MEX schedules; retrieve them as `OffsetDateTime`, verify the same cents/instant/offset, and derive local values using the retained IANA airport zone IDs, including a cross-date arrival.

### Automated verification

- [x] `./mvnw.cmd -DskipFrontend=true -Dtest=CatalogSchemaIntegrationTest,RentalUnitOccupancyConstraintIntegrationTest,CatalogTemporalMoneyRoundTripIntegrationTest test` — all schema and temporal/monetary invariants are exercised against isolated H2 databases.

### Optional developer checks

- [x] None.

**Success criteria:** Invalid catalog data fails at the database boundary; valid physical rental units permit adjacent intervals but never intersecting occupancy; and price/timestamp round trips do not introduce floating-point or timezone ambiguity.

## Phase 3: Prove Forward Migration and Preserve Phase 1 Runtime Behavior

### Changes

- [x] `src/test/java/app/detour/catalog/PhaseOneCatalogForwardMigrationIntegrationTest.java` — create an isolated file-backed H2 database, migrate it only through Flyway target version `2`, insert a valid `detour_user`, then run the complete lineage and assert the user and password data remain while the version history advances to `9` and catalog tables are present.
- [x] `src/test/java/app/detour/identity/ApplicationRestartIntegrationTest.java` — retain the existing two-boot identity behavior unchanged; only adjust it if its Flyway version assumptions require no source-level change.
- [x] `scripts/verify-packaged-identity.ps1` — do not alter the identity-only smoke contract; use it after packaging to show the catalog migrations did not prevent the existing loopback startup path.

### Automated verification

- [x] `./mvnw.cmd -DskipFrontend=true test` — full backend suite, including the forward-migration and existing identity regressions, passes on isolated H2 databases.
- [x] `./mvnw.cmd -DskipFrontend=true package` — build the executable JAR without requiring frontend installation/build.
- [x] `./scripts/verify-packaged-identity.ps1` — packaged application starts against an isolated temporary H2 database and still serves the Phase 1 identity shell.

### Optional developer checks

- [x] None.

**Success criteria:** A real Phase-1-shaped database advances without rewriting prior migration files or losing a user, and the packaged application starts without external suppliers, model services, or new catalog UI/API behavior.

## Test Strategy

Use integration-level JDBC tests, not mocked repositories, because the acceptance criteria are H2/Flyway DDL behavior. Construct minimal valid catalog graphs in test helpers, then make one invalid change per assertion so a failure identifies the actual constraint. Cover clean migration, version-2 forward migration, catalog ownership absence, all numeric/category/foreign-key/time checks, night capacity matching, rental half-open overlap, and money/time-zone round trips. Retain the existing HTTP/restart identity tests as regression evidence and run the packaged loopback smoke only after a successful package.

## Acceptance-Criteria Traceability

| Acceptance criterion | Planned code evidence | Planned test evidence |
| --- | --- | --- |
| Clean DeTour lineage creates normalized catalog/inventory with no Wayfarer compatibility | `V3__create_shared_catalog_and_flight_schema.sql`, `V4__create_catalog_inventory_schema.sql` | `DetourApplicationTest` checks versions/tables/empty data and legacy absence |
| Phase 1 migrates forward and retains users | Additive `V3`/`V4`; `V1`/`V2` untouched | `PhaseOneCatalogForwardMigrationIntegrationTest` targets 2 then migrates fully |
| Invalid prices, capacity, ranges, references, categories, and above-capacity inventory reject | DDL checks, foreign/composite keys, unique keys, trigger | `CatalogSchemaIntegrationTest` negative JDBC cases |
| All three component families and finite semantics are representable | Flight schedule/instance/segment, accommodation/unit/night, rental/location/class/unit/occupancy tables | Valid direct/one-stop, room/property, and rental fixture graphs plus occupancy tests |
| Stable identifiers and shared ownership support later snapshots/revalidation | Unique, immutable `catalog_key` on retained entities; no user foreign key | Schema metadata/unique-key/update-rejection assertions in `CatalogSchemaIntegrationTest` |
| Cents and zoned times round-trip | `BIGINT` money columns, airport IANA zones, `TIMESTAMP WITH TIME ZONE` columns | `CatalogTemporalMoneyRoundTripIntegrationTest` |
| Backend/Flyway/startup and Phase 1 identity remain intact | Existing application configuration and identity paths unchanged | Full Maven test, package, and `verify-packaged-identity.ps1` |
| Deferred workflows are not introduced | DDL plus one trigger only; no controller/service/repository/UI changes | Source-scope review and existing identity HTTP suite |

## Risks and Rollback/Recovery

These migrations are forward-only. Before applying them to a local persistent DeTour database, take the normal local-file backup or use the existing explicit reset procedure only if the developer chooses to discard local development state; application startup must not reset it. If an unreleased migration needs correction, add a later forward migration rather than modifying `V1`–`V4`. The H2-specific trigger is intentionally isolated behind one class and one migration registration so a later database-platform change can replace that mechanism without changing catalog ownership or the half-open interval contract.

## References

- `ai/thoughts/tickets/2026-09-18-p02-t01-establish-catalog-inventory-foundation.md`
- `ai/thoughts/research/2026-09-18-p02-t01-establish-catalog-inventory-foundation.md`
- `ai/thoughts/phases/README.md`
- `ai/thoughts/phases/phase-2-catalog-and-fixtures.md`
- `ai/thoughts/architecture/2026-09-17-p00-t02-detour-replacement-architecture.md`
- `src/main/resources/db/migration/V1__detour_platform.sql`
- `src/main/resources/db/migration/V2__create_detour_user_identity.sql`
- `src/test/java/app/detour/DetourApplicationTest.java`
