# Complete Stay and Rental Fixtures Implementation Plan

## Overview

- Ticket: `ai/thoughts/tickets/2026-09-18-p02-t03-complete-stay-rental-fixtures.md`
- Research: `ai/thoughts/research/2026-09-18-p02-t03-complete-stay-rental-fixtures.md`
- Outcome: A clean Flyway database contains a deterministic, fictional March 2027 accommodation and airport-rental catalog alongside the existing airfare catalog, with cross-catalog integrity assertions and a documented, deterministic summary command.

## Current State

`V10__seed_march_2027_airfare_catalog.sql` owns the three destinations, destination airports/zones, airlines, and 720 March flight instances. `V4__create_catalog_inventory_schema.sql` already owns the normalized accommodation nightly-inventory and rental-unit/half-open-occupancy contracts; `V5` supplies property metadata and `V9` protects all-inclusive cent totals. No production migration inserts lodging suppliers/properties/units/nights or rental suppliers/locations/classes/units.

The existing assertion pattern is `AirfareFixtureIntegrityAssertions` plus clean-H2 Flyway tests in `AirfareFixtureIntegrationTest`. Its deterministic snapshot covers only airfare tables. `CatalogSchemaFixtures.ensureMinimumCatalog` also assumes the production catalog has no stay/rental rows, so it must become explicitly test-only and idempotent once V11 supplies real rows. The project has no catalog command surface; Maven supports `-DskipFrontend=true`, and existing PowerShell scripts are the documented local-command convention.

## Desired End State

A clean migration through V11 has 18 fictional accommodation properties (two of HOTEL, BED_AND_BREAKFAST, and VACATION_RENTAL in each of SFO, MUC, and MEX), one stable unit per property, and 30 nightly rows per unit for 1--30 March. Each unit has an all-inclusive positive nightly price from nonnegative integer-cent components and enough capacity/inventory to make party sizes 1--8 demonstrably usable with distinct room/property-fit, price, rating, and distance tradeoffs.

It also has one fictional rental supplier/location at each destination airport, all ECONOMY/STANDARD/SUV classes, and 21 stable physical units. No active occupancy rows are seeded: availability is intentionally represented by the absence of an intersecting active interval under the already-settled half-open constraint, not by a new rental calendar or booking service. Tests will demonstrate full March interval availability, 24-hour-cycle billing math (including a rounded partial final cycle), and back-to-back versus intersecting intervals.

The complete Phase 2 catalog has one reusable assertion surface, a stable full snapshot, and a short report command that creates an isolated in-memory migrated database, prints fixed-order counts and representative flight/stay/rental details, and has no effect on the developer database or application runtime.

## Scope

### In scope

- A V11 Flyway fixture migration for fictional lodging and airport-rental inventory only.
- Complete-catalog integrity and determinism tests, including the existing airfare contract.
- A small standalone Java fixture-summary generator, its PowerShell wrapper, and README usage.
- Updating existing fresh-start, forward-migration, temporal round-trip, and schema-test assumptions for V11 data.

### Out of scope

- Catalog search/selection APIs or UI; Trip/itinerary persistence; booking, cancellation, payment, or inventory mutation services.
- A rental availability-calendar table, dynamic pricing, real suppliers, multiple currencies, extra destinations, or dates outside March 2027.
- Changing the settled persisted schema or replacing the existing rental-overlap trigger.

## Active Project Guardrails

None recorded in `ai/thoughts/design-lens.md`.

## Impact and Risk Analysis

- **Migration/data contract:** V11 is an irreversible appended migration. Use stable, semantically named catalog keys and only existing tables/constraints; do not modify V10 shared keys or retrofit a new availability model.
- **Supported-night boundary:** Valid outbound flights start March 1 and valid returns end March 31, so stay nights are March 1--30 inclusive. Generating nightly rows from `SYSTEM_RANGE(1, 30)` makes the boundary compact and independently assertable.
- **Rental availability:** There is deliberately no per-day rental inventory table. Empty fixture occupancy means a physical unit is available for every valid March interval; test this using real local-zone `OffsetDateTime` intervals and the established overlap trigger, rather than claiming calendar rows exist.
- **Capacity interpretation:** A HOTEL/B&B unit's `guest_capacity` is per room and `available_inventory` is room count; test required rooms as `ceil(party / guest_capacity)`. A VACATION_RENTAL `WHOLE_PROPERTY` uses one property/unit and must itself hold the party. This is the existing unit-kind schema distinction, not a new public selection API.
- **Regression reach:** Fresh-start tests currently expect V10 and empty inventory tables; test-only helper data must not distort the clean V11 fixture tests. Keep fixture tests on independently migrated H2 databases and retain identity and packaged-start coverage.
- **Summary safety:** The command must migrate an isolated, randomly named in-memory H2 database, not use `DETOUR_DATABASE_URL`, start Spring, or mutate a file-backed developer database.

## Implementation Approach

Append `V11__seed_march_2027_stay_and_rental_catalog.sql` after the airfare migration. Insert a small set of three lodging suppliers and three rental suppliers, then use `catalog_key` subqueries to link all fixture rows. Insert 18 properties/units and derive their 540 nightly rows from `SYSTEM_RANGE(1, 30)`. Insert a rental location at SFO/MUC/MEX, three class rows per location, and a deliberately varied 7-unit fleet per destination; leave `rental_unit_occupancy` empty.

Keep validation at the test boundary, where the current repository already verifies complete fixture data. Add `CatalogFixtureIntegrityAssertions` as the complete-catalog facade: it will retain/compose the airfare checks and add coverage, values, capacity-fit, rental location/class/unit, interval, and stable-key checks. This is preferable to encoding policy in more database constraints or adding application services, because the ticket requires fixture completeness verification and explicitly excludes those production behaviors.

Implement the summary as `CatalogFixtureSummary` with a plain `main` and a testable writer/query method. It will migrate an isolated H2 database with the classpath migrations and emit sorted, compact sections. A PowerShell wrapper invokes it after `compile`; README documents the single command. This avoids a server endpoint, live database access, or an environment-specific tool while producing reviewable output.

### Fixture design matrix

The V11 migration will use the following fixed accommodation rows. `nightly total` is base + tax + fee in USD cents; every row has 30 nightly inventory records with the same deterministic breakdown. Use fees of 500 cents for HOTEL, 300 for BED_AND_BREAKFAST, and 800 for VACATION_RENTAL; for a total `T` and category fee `F`, set `base = floor((T - F) / 1.08)` and `tax = T - F - base`. This produces the exact listed total with a deterministic approximately-8% tax and no floating point. Inventory is available room/property count.

| Destination | Property key / display name | Type, unit | Location description | Guests / inventory | Nightly total | Rating / distance m |
| --- | --- | --- | --- | --- | ---: | --- |
| SFO | `stay-sfo-hotel-harbor` / Harbor Civic Hotel | HOTEL, Harbor King Room | Civic Center | 2 / 6 | 19,000 | 4.3 / 550 |
| SFO | `stay-sfo-hotel-summit` / Summit Family Suites | HOTEL, Bay Family Suite | Embarcadero | 4 / 3 | 32,500 | 4.7 / 1,400 |
| SFO | `stay-sfo-bnb-mission` / Mission Garden House | BED_AND_BREAKFAST, Garden Room | Mission Dolores | 2 / 4 | 15,500 | 4.5 / 2,100 |
| SFO | `stay-sfo-bnb-pacific` / Pacific View Inn | BED_AND_BREAKFAST, View Loft | North Beach | 4 / 2 | 24,500 | 4.1 / 750 |
| SFO | `stay-sfo-rental-sunset` / Sunset Courtyard Cottage | VACATION_RENTAL, Entire Cottage | Outer Sunset | 4 / 1 | 30,500 | 4.6 / 3,900 |
| SFO | `stay-sfo-rental-presidio` / Presidio Grand Home | VACATION_RENTAL, Entire Home | Presidio | 8 / 1 | 52,000 | 4.8 / 2,200 |
| MUC | `stay-muc-hotel-isar` / Isar Market Hotel | HOTEL, Market Double | Altstadt | 2 / 6 | 16,500 | 4.4 / 800 |
| MUC | `stay-muc-hotel-alpine` / Alpine Family Hotel | HOTEL, Family Studio | Schwabing | 4 / 3 | 27,500 | 4.8 / 2,700 |
| MUC | `stay-muc-bnb-glocken` / Glocken Garden B&B | BED_AND_BREAKFAST, Garden Room | Glockenbach | 2 / 4 | 13,500 | 4.6 / 1,900 |
| MUC | `stay-muc-bnb-englischer` / Englischer Loft Inn | BED_AND_BREAKFAST, Loft Room | Englischer Garten | 4 / 2 | 22,000 | 4.2 / 650 |
| MUC | `stay-muc-rental-lehel` / Lehel Courtyard Flat | VACATION_RENTAL, Entire Flat | Lehel | 4 / 1 | 26,500 | 4.7 / 1,250 |
| MUC | `stay-muc-rental-bavaria` / Bavaria Terrace House | VACATION_RENTAL, Entire House | Nymphenburg | 8 / 1 | 46,000 | 4.5 / 4,100 |
| MEX | `stay-mex-hotel-centro` / Centro Alameda Hotel | HOTEL, Alameda Double | Alameda Central | 2 / 6 | 11,000 | 4.1 / 500 |
| MEX | `stay-mex-hotel-paseo` / Paseo Family Suites | HOTEL, Paseo Suite | Paseo de la Reforma | 4 / 3 | 19,500 | 4.6 / 1,800 |
| MEX | `stay-mex-bnb-coyoacan` / Coyoacán Courtyard B&B | BED_AND_BREAKFAST, Courtyard Room | Coyoacán | 2 / 4 | 9,500 | 4.8 / 6,400 |
| MEX | `stay-mex-bnb-roma` / Roma Artisan Inn | BED_AND_BREAKFAST, Artisan Loft | Roma Norte | 4 / 2 | 17,000 | 4.3 / 2,100 |
| MEX | `stay-mex-rental-condesa` / Condesa Patio Casa | VACATION_RENTAL, Entire Casa | Condesa | 4 / 1 | 21,000 | 4.7 / 3,300 |
| MEX | `stay-mex-rental-pedregal` / Pedregal Family Villa | VACATION_RENTAL, Entire Villa | Pedregal | 8 / 1 | 35,000 | 4.4 / 9,200 |

Use fictional local descriptions and plausible coordinates near the stated destination; do not imply the properties or suppliers exist. The three lodging supplier keys/names will be `supplier-stays-pacific` / Pacific Lantern Stays, `supplier-stays-bavaria` / Bavaria Hearth Lodging, and `supplier-stays-valle` / Valle Vista Stays. The rental suppliers will be `supplier-rental-sfo` / Harborline Mobility, `supplier-rental-muc` / Alpine Roadworks, and `supplier-rental-mex` / Círculo Drive.

Rental classes use stable keys `rental-{destination}-{economy|standard|suv}`, with total daily cents and physical-unit counts: SFO = ECONOMY 4,800/3, STANDARD 7,100/2, SUV 10,300/2; MUC = 4,500/2, 6,500/3, 9,600/2; MEX = 3,700/2, 5,500/2, 8,200/3. Use category fees of 180, 260, and 380 cents respectively, then the same `base = floor((T - F) / 1.08)`, `tax = T - F - base` calculation. Name physical units predictably (`rental-unit-{destination}-{class}-01`, etc.). This creates varied class price and fleet-count data without asserting an unsupported passenger-capacity model.

## Phase 1: Seed settled stay and rental inventory

### Changes

- [x] `src/main/resources/db/migration/V11__seed_march_2027_stay_and_rental_catalog.sql` — insert the six fixed stay choices per SFO/MUC/MEX, their stable room/whole-property units, and all March 1--30 nightly rows generated with `SYSTEM_RANGE`; persist the fixture matrix's names, metadata, capacity, inventory, nonnegative cent components, and fictional supplier associations.
- [x] `src/main/resources/db/migration/V11__seed_march_2027_stay_and_rental_catalog.sql` — insert the three fictional rental suppliers, SFO/MUC/MEX airport locations, all three class rows per location, and the 21 stable physical-unit keys; leave occupancy empty so availability remains derived from interval non-overlap.
- [x] `src/test/java/app/detour/catalog/CatalogSchemaFixtures.java` — make test-only minimum/schema fixture insertion independently idempotent by testing for its own `test-*` keys rather than interpreting real V11 data as a replacement; preserve its role for schema-negative tests without contaminating production-fixture assertions.
- [x] `src/test/java/app/detour/DetourApplicationTest.java` — expect migration 11 and real clean-catalog counts, including nightly inventory and all accommodation/rental tables, while preserving the no-seeded-user and no-legacy-table assertions.
- [x] `src/test/java/app/detour/catalog/PhaseOneCatalogForwardMigrationIntegrationTest.java` — advance the expected current version to 11 and assert representative post-V11 catalog counts while proving V2 identity data is unchanged.

### Automated verification

- [x] `.\mvnw.cmd -DskipFrontend=true -Dtest=DetourApplicationTest,PhaseOneCatalogForwardMigrationIntegrationTest,CatalogSchemaIntegrationTest test` — a fresh and a forward-migrated database apply V11 and schema tests remain isolated.

### Optional developer checks

- [ ] None.

## Phase 2: Verify complete catalog semantics and determinism

### Changes

- [x] `src/test/java/app/detour/catalog/CatalogFixtureIntegrityAssertions.java` — create the complete Phase 2 assertion facade, retaining the exact airfare coverage/connection/zone/March-31 checks and adding destination/type counts, stable-key uniqueness, 30-night stay coverage, valid metadata/pricing/inventory, 1--8 party room/property fit and non-identical price/rating/distance ordering, airport/class/unit coverage, empty active fixture occupancy, rental local-zone/interval availability, and daily-cycle arithmetic.
- [x] `src/test/java/app/detour/catalog/AirfareFixtureIntegrityAssertions.java` — expose or retain only the airfare-specific reusable checks needed by the facade; do not weaken existing direct/connection coverage or corruption diagnostics.
- [x] `src/test/java/app/detour/catalog/StayAndRentalFixtureIntegrationTest.java` — migrate isolated H2 databases, assert the V11 stay/rental fixtures through the complete facade, calculate a 25-hour local rental as two daily totals and a cross-midnight short interval as one, and exercise a real fixture unit to prove adjoining active intervals pass while intersecting intervals fail.
- [x] `src/test/java/app/detour/catalog/AirfareFixtureIntegrationTest.java` — use complete-catalog assertions, extend its ordered snapshot with supplier categories, stay property/unit/night rows, rental location/class/unit rows, and assert two clean migrations match exactly; retain representative corruption checks and add missing-night, invalid stay/rental fixture, and missing class/unit examples that fail the facade.
- [x] `src/test/java/app/detour/catalog/CatalogTemporalMoneyRoundTripIntegrationTest.java` — round-trip representative V11 nightly and rental daily cents plus SFO/MUC/MEX pickup/return zoned instants in addition to existing airfare values.

### Automated verification

- [x] `.\mvnw.cmd -DskipFrontend=true -Dtest=AirfareFixtureIntegrationTest,StayAndRentalFixtureIntegrationTest,CatalogTemporalMoneyRoundTripIntegrationTest,RentalUnitOccupancyConstraintIntegrationTest test` — integrity, determinism, cents, zones, and overlap rules hold on clean migrated databases.

### Optional developer checks

- [ ] None.

## Phase 3: Provide the reviewer-facing summary command

### Changes

- [x] `src/main/java/app/detour/catalog/CatalogFixtureSummary.java` — add a plain Java entry point and writer that migrates a random in-memory H2 database and prints stable-order Phase 2 counts plus one representative direct/connection flight, each destination's stay type/property (total, capacity, rating, distance), and each rental class (daily total, unit count, local-zone interval example). Keep all queries read-only after migration and output free of random database identifiers/timestamps.
- [x] `scripts/show-catalog-fixture-summary.ps1` — add a fail-fast wrapper that runs `compile` and the summary main through Maven with `-DskipFrontend=true`; it must not start the web application or target `data/detour`.
- [x] `README.md` — document the summary command, that it uses an isolated in-memory clean Flyway catalog, the meaning of the concise sections, and the existing package/identity verification instructions without overstating an API/UI feature.
- [x] `src/test/java/app/detour/catalog/CatalogFixtureSummaryIntegrationTest.java` — capture the writer output from two independently migrated sources and assert equality, required headings/counts/destination/component representatives, and the absence of raw full-row dumps.

### Automated verification

- [x] `.\scripts\show-catalog-fixture-summary.ps1` — emits one deterministic concise report from an isolated migrated catalog.
- [x] `.\mvnw.cmd -DskipFrontend=true -Dtest=CatalogFixtureSummaryIntegrationTest test` — report structure and deterministic output are executable assertions.

### Optional developer checks

- [x] Run the summary once and visually confirm that the compact representative data makes missing destination/type/class coverage obvious; this is nonblocking because output shape/content is also asserted.

## Test Strategy

Step 3 will use isolated clean-H2 Flyway integration tests as the primary boundary. It will first add a missing-V11-fixture test that fails before the migration, then cover fixture-row completeness/validity, party-fit calculations, rental billing and interval semantics, deterministic full snapshots, and representative assertion corruption. Existing schema, identity, and packaged startup tests remain regression gates. The summary is tested as a deterministic writer and run through its documented command; neither test path uses a live supplier, file database, or booking mutation.

## Acceptance-Criteria Traceability

| Acceptance criterion | Planned code evidence | Planned test evidence |
| --- | --- | --- |
| Two properties of each stay type per destination with March nights | `V11` accommodation inserts and `SYSTEM_RANGE(1, 30)` nightly generation | `CatalogFixtureIntegrityAssertions` and `StayAndRentalFixtureIntegrationTest` count/type/date checks |
| Parties 1--8 have varied usable stay options | V11 capacity/inventory/price/rating/distance matrix | party room/property-fit and variation assertions |
| Every destination airport has economy, standard, SUV units | V11 rental suppliers/locations/classes/21 units | location/category/unit count and stable-key checks |
| Rental pricing uses rounded 24-hour cycles and half-open intervals | V11 daily cent inputs; existing `RentalUnitOccupancyOverlapTrigger` | 25-hour/cross-midnight billing calculation plus fixture-unit back-to-back/overlap test |
| Complete Phase 2 integrity checks detect violations | complete assertion facade and updated airfare test | clean pass plus representative missing/invalid/corrupt fixture failures |
| One documented deterministic summary command | `CatalogFixtureSummary`, PowerShell wrapper, README | summary integration output comparison and wrapper run |
| Clean rebuild remains deterministic | V11 deterministic SQL and isolated summary migration | extended ordered snapshot and summary-output equality |
| Fictional Flyway-only offline catalog | V11, summary's in-memory Flyway setup, no service/API additions | migration and static summary tests |
| Existing identity, clean migration, and packaged startup remain sound | updated expected migration/count tests; no security/runtime changes | focused catalog tests then full safe Maven suite and packaged verification |
| Excluded product behavior stays absent | no controller/service/schema change beyond V11 fixtures/summary | review of changed paths and existing startup/API regressions |

## Risks and Rollback/Recovery

V11 must be reviewed before release because Flyway migrations are append-only; a bad fixture migration is corrected with a later migration rather than edited after it has been applied. The selected data deliberately uses empty rental occupancy, so no cleanup/recovery transaction is required and availability is always query-derived. If summary queries drift, its output test and full snapshot identify the difference before handoff. No external or production data is contacted, so rollback is deployment migration management rather than supplier reconciliation.

## References

- `ai/thoughts/tickets/2026-09-18-p02-t03-complete-stay-rental-fixtures.md`
- `ai/thoughts/research/2026-09-18-p02-t03-complete-stay-rental-fixtures.md`
- `ai/thoughts/phases/phase-2-catalog-and-fixtures.md`
- `src/main/resources/db/migration/V3__create_shared_catalog_and_flight_schema.sql`
- `src/main/resources/db/migration/V4__create_catalog_inventory_schema.sql`
- `src/main/resources/db/migration/V5__add_accommodation_search_metadata.sql`
- `src/main/resources/db/migration/V9__prevent_all_inclusive_price_overflow.sql`
- `src/main/resources/db/migration/V10__seed_march_2027_airfare_catalog.sql`
- `src/test/java/app/detour/catalog/AirfareFixtureIntegrityAssertions.java`
