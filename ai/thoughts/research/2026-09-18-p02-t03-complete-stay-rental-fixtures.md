---
date: 2026-09-18
repository: loomspan-travel-demo
branch: main
commit: ea5f1a989de23027505a3eeb4355d0773cd4ea4b
ticket: ai/thoughts/tickets/2026-09-18-p02-t03-complete-stay-rental-fixtures.md
tags: [phase-2, catalog, flyway, accommodations, rental-cars, fixtures]
---

# P02-T03 Complete Stay and Rental Fixtures Research

## Research Question

What catalog schema, deterministic airfare fixtures, verification patterns, and repository execution surfaces already exist for completing the March 2027 accommodation and rental fixtures, complete-catalog integrity checks, and a human-readable fixture summary?

## Summary

The checked-out lineage has the normalized accommodation and rental persistence model from P02-T01 and the complete airfare seed from P02-T02, but contains no production accommodation/rental fixture migration and no fixture-summary command. Stable catalog keys are unique and immutable across the relevant catalog tables; money, capacity, nightly availability, metadata, rental class, and half-open active occupancy constraints are already persisted. Airfare-specific clean-migration, corruption, determinism, time-zone, connection, and March-boundary assertions provide the existing verification pattern but currently cover only the airfare domain.

## Repository State

- Captured 2026-09-18T21:26:42-07:00.
- Repository `loomspan-travel-demo`, branch `main`, commit `ea5f1a989de23027505a3eeb4355d0773cd4ea4b` (`clean up from P02-T02`).
- The working tree was clean before this research artifact was created. The P02-T02 commit is the latest substantive catalog change and adds `V10__seed_march_2027_airfare_catalog.sql` plus airfare fixture tests.

## Current Behavior and Data Flow

1. Flyway runs ordered SQL migrations from `src/main/resources/db/migration`. `V3` creates shared destinations, airports, suppliers, and flight tables; `V4` adds accommodation and rental inventory tables; `V5`, `V8`, and `V9` add accommodation metadata, catalog-key immutability, and all-inclusive price-overflow checks.
2. `V10__seed_march_2027_airfare_catalog.sql` is currently the only complete Phase 2 fixture seed. It creates the three destinations, nine airports (including PDX and required connection airports), and two airline suppliers, then derives dated flight instances using `SYSTEM_RANGE`.
3. There are currently no inserted lodging suppliers, accommodation properties/units/nightly rows, rental locations/classes/units, or rental occupancy rows in production migrations. Test-only helpers insert a minimal SFO hotel room and standard rental units when schema tests need them.
4. Accommodation availability is represented per `(accommodation_unit_id, night_date)` row. Rentals use individual physical-unit occupancy intervals; an active interval excludes another interval only when `existing.pickup_at < new.return_at` and `new.pickup_at < existing.return_at`, so a return/pickup at the same instant is non-overlapping.
5. The existing summary/report surface is absent: executable Java production code contains only the Spring application, identity, web/security, and persistence trigger classes; `scripts/` contains reset, run, and packaged-identity verification scripts only. Repository search found no fixture-summary or catalog-report command.

## Key Components

- `src/main/resources/db/migration/V3__create_shared_catalog_and_flight_schema.sql:1` — shared destination, airport, and supplier keys, including `CAR_RENTAL` and `LODGING` supplier categories; airport zones are stored as nonempty strings at `:22-30`.
- `src/main/resources/db/migration/V4__create_catalog_inventory_schema.sql:1` — accommodation property category is constrained to `HOTEL`, `BED_AND_BREAKFAST`, or `VACATION_RENTAL` and must use a lodging supplier.
- `src/main/resources/db/migration/V4__create_catalog_inventory_schema.sql:17` — units carry stable keys, guest capacity, inventory capacity, and type pairing: hotel/B&B units are `ROOM`; vacation rental units are `WHOLE_PROPERTY` (`:29-35`).
- `src/main/resources/db/migration/V4__create_catalog_inventory_schema.sql:38` — each nightly row retains availability and USD-cent base/tax/fee components and constrains available inventory to the unit's capacity (`:46-51`).
- `src/main/resources/db/migration/V4__create_catalog_inventory_schema.sql:54` — a rental location references both a destination and a destination-associated airport; the airport is unique to one rental location.
- `src/main/resources/db/migration/V4__create_catalog_inventory_schema.sql:66` — rental classes are supplier/location scoped and constrained to economy, standard, or SUV, with per-day USD-cent components (`:74-84`).
- `src/main/resources/db/migration/V4__create_catalog_inventory_schema.sql:87` — physical rental units have immutable-style catalog-key fields and a unique class-local unit identifier.
- `src/main/resources/db/migration/V4__create_catalog_inventory_schema.sql:97` — rental occupancy stores zoned pickup/return instants with `pickup_at < return_at`; `:111-113` registers the Java overlap trigger.
- `src/main/resources/db/migration/V5__add_accommodation_search_metadata.sql:1` — properties additionally persist latitude, longitude, location description, 0–5 rating, and nonnegative city-center distance.
- `src/main/resources/db/migration/V8__enforce_catalog_key_immutability_and_schedule_times.sql:25` — immutable-key triggers cover accommodation properties/units and rental locations/classes/units; shared catalog and flight triggers are in the same migration.
- `src/main/resources/db/migration/V9__prevent_all_inclusive_price_overflow.sql:5` — nightly and daily component sums cannot overflow a signed 64-bit total.
- `src/main/resources/db/migration/V10__seed_march_2027_airfare_catalog.sql:4` — existing fixture seed owns shared destinations/airports; the destination airports have zones at `:11-13`, and airline suppliers are inserted at `:20-22`.
- `src/main/resources/db/migration/V10__seed_march_2027_airfare_catalog.sql:77` — airfare values/instances are derived compactly; `SYSTEM_RANGE` supplies the March service dates at `:91-96`.
- `src/main/java/app/detour/catalog/persistence/RentalUnitOccupancyOverlapTrigger.java:23` — only `ACTIVE` rows participate in exclusion; it locks the physical unit before checking overlap at `:26-45`.
- `src/test/java/app/detour/catalog/AirfareFixtureIntegrityAssertions.java:34` — reusable style for clean-database fixture assertions: count, coverage, zones, row validity, connections, and variation checks.
- `src/test/java/app/detour/catalog/AirfareFixtureIntegrationTest.java:17` — clean Flyway migration, independent snapshot determinism, and representative-corruption rejection pattern.
- `src/test/java/app/detour/catalog/RentalUnitOccupancyConstraintIntegrationTest.java:35` — proves back-to-back active intervals are allowed and intersecting active intervals/updates are rejected.
- `src/test/java/app/detour/catalog/CatalogSchemaFixtures.java:13` — test-only seed path is deliberately minimal and is not production fixture generation.
- `src/test/java/app/detour/catalog/PhaseOneCatalogForwardMigrationIntegrationTest.java:19` — migrates an identity-only V2 file database through the current V10 lineage and expects 720 flight instances.

## Affected Areas

| Area | Current behavior and evidence |
| --- | --- |
| Flyway fixtures | `V10` seeds destinations, airport geography/zones, airline suppliers, schedules, and 720 flight instances; no later migration exists for lodging or cars. The forward-migration test currently expects Flyway version 10. |
| Accommodation catalog | The schema supports destination-owned property metadata, rooms/whole-property units, capacity, and per-night pricing/availability, but production tables remain empty after the current migration chain. |
| Rental catalog | The schema supports one airport location per destination airport, class-level daily inputs, unique physical units, and zoned occupancy intervals, but production rental tables remain empty after the current migration chain. |
| Availability semantics | Nightly accommodation availability is explicit. Rental availability is derived from the absence of intersecting active occupancy rows; the schema contains no rental availability-by-date table or persisted March-window boundary. |
| Integrity assertions | `AirfareFixtureIntegrityAssertions` hard-codes P02-T02 count/coverage/connection/time-zone expectations and is invoked by airfare integration tests. There is no cross-catalog assertion utility. |
| Deterministic snapshots | The airfare test creates two clean in-memory databases and compares an ordered SQL snapshot, but its snapshot query includes only destination, airport, supplier, and flight tables. |
| Summary command/documentation | No source, script, Maven execution, or README section currently generates a catalog summary. README documents package/run and packaged identity verification only. |

## Existing Tests and Fixtures

- `AirfareFixtureIntegrationTest` uses Flyway against fresh in-memory H2 databases, asserts validity, validates stable snapshot equality, and introduces deleted coverage, zero total price, depleted capacity, wrong connection, infeasible layover, final-arrival, and duplicate-key corruptions (`:17-95`).
- `AirfareFixtureIntegrityAssertions` expects 3 destinations, 9 airports, 2 airlines, 24 schedules, 720 instances, and 1,080 segments, validates all listed Java `ZoneId`s, tests every required March service date, and checks capacity for parties up to 8 (`:34-108`, `:144-162`).
- `CatalogSchemaIntegrationTest` exercises generic relational/check/overflow/category constraints, shared-data ownership, immutable identifiers, and the unit-type distinction. It relies on `CatalogSchemaFixtures.ensureMinimumCatalog` for missing accommodation/rental data, so it is not an end-to-end Phase 2 fixture test.
- `RentalUnitOccupancyConstraintIntegrationTest` uses the schema-test fixture helper and asserts half-open boundaries, released-row noninterference, overlaps, and update overlap rejection (`:31-48`).
- `CatalogTemporalMoneyRoundTripIntegrationTest` currently round-trips airfare cents and PDX/MUC/SFO/MEX zones only (`:28-47`).
- `PhaseOneCatalogForwardMigrationIntegrationTest` verifies V2 identity data survives migration through the current lineage, but its asserted migration version and flight count are tied to V10/P02-T02 (`:19-44`).

## Dependencies and Operational Constraints

- Java 21, Spring Boot, H2, Flyway, Maven, and a React frontend build are project dependencies (`pom.xml:1-100`). Maven's normal lifecycle invokes `npm ci` and `npm run build`; the `skipFrontend` Maven property defaults to `false` (`pom.xml:15-18`, `:65-101`).
- All fixture state is application-owned shared catalog data. The schema test explicitly checks that the catalog/inventory tables do not import `DETOUR_USER` foreign keys (`CatalogSchemaIntegrationTest.java:94-116`).
- No external supplier, model endpoint, or runtime catalog generator exists in the current feature path. The ticket's excluded Trip, selection, reservation/mutation, booking, payment, and UI layers do not yet have catalog implementations to modify.
- H2's migration expression support is already used for deterministic fixture date derivation (`V10:91-96`); zoned instants are constructed from persisted airport zone identifiers (`V10:98-106`).

## Historical Context

- P02-T01 established the current persisted contract: application-owned shared catalog data, integer cents, zone/offset instants, nightly accommodation availability, and half-open rental occupancy. Its implementation commit is `39e08a2`.
- P02-T02 added the current airfare-only catalog in commit `32ee0a9`; its ticket names P02-T03 as the downstream owner of accommodation/rental fixtures and the cross-catalog summary.
- The Phase 2 roadmap specifies two choices per accommodation type/destination, all three rental classes at each destination airport, deterministic March fixtures, integrity checks, and a lightweight reviewer-facing summary (`ai/thoughts/phases/phase-2-catalog-and-fixtures.md:19-45`).

## Open Questions

- The schema has no persisted rental availability calendar or supported-window constraint: vacant physical units are available for any valid interval unless later occupancy rows exist. Planning needs to define how the ticket's March-window availability assertion will observe fixture availability without adding an out-of-scope booking/inventory mutation service.
- `rental_vehicle_class` has no explicit vehicle guest/passenger capacity column. The ticket requires varied class supply, price, units, and interval availability, but does not require class capacity; planning should keep rental verification within the persisted attributes unless a schema change is separately justified.
- `accommodation_property` only has a property-level name and a generic supplier reference; properties/units already have enough display fields for the stated fictional supplier/display data, but the chosen key and display naming matrix is intentionally absent.
