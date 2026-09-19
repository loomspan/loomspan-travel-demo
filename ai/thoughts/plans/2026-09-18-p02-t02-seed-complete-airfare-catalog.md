# P02-T02 — Seed Complete Deterministic Airfare Fixtures Implementation Plan

## Overview

- Ticket: `ai/thoughts/tickets/2026-09-18-p02-t02-seed-complete-airfare-catalog.md`
- Research: `ai/thoughts/research/2026-09-18-p02-t02-seed-complete-airfare-catalog.md`
- Outcome: A clean H2/Flyway build contains a compact, reproducible March 2027 PDX airfare catalog with four usable choices per destination, direction, and date, plus automated integrity proof.

## Current State

`V3__create_shared_catalog_and_flight_schema.sql` already represents immutable catalog keys, airline schedules with one or two segments, dated priced/capacitated instances, local schedule times, and `TIMESTAMP WITH TIME ZONE` actuals. `FlightSegmentIntegrityTrigger` prevents structural route discontinuities and backwards dated legs, while V8 and V9 protect catalog-key mutation and price-sum overflow. It deliberately does not encode fixture coverage, airport-policy pairs, valid IANA zones, a positive connection buffer, or the March 31 final-arrival limit.

The clean migration lineage ends at V9. `DetourApplicationTest` currently expects empty catalog tables, and the existing catalog test helper assumes it owns the catalog, so those tests must be updated for a now-seeded airfare catalog. There is no catalog API, service, UI, Trip, selection, or inventory-mutation path to extend.

## Desired End State

- Seed the three destinations; PDX, destination, and required connection airports; fictional airlines; 24 reusable schedule definitions; 720 dated instances; and 1,080 dated segments in a V10 migration.
- For each destination there are two direct and two one-stop choices for outbound dates March 1–30 and inbound dates March 2–31. Those are exactly the service dates that can be paired into a March 1–31 Trip lasting 1–14 nights; the first/latest valid outbound/inbound dates therefore remain covered while neither side adds an unusable date.
- Every actual timestamp is derived from its service date, local schedule time, and IANA zone using H2 zone conversion, so March DST transitions receive the correct offset. Final arrivals are no later than 2027-03-31 23:59:59 local at their final airport.
- Prices are integer cents per traveler, capacities are initially available and at least eight seats, and no age-specific fare data or pricing behavior is introduced. Complete-party totals are the one per-traveler all-inclusive amount multiplied by party size.
- Acceptance-criteria mapping is recorded in the traceability table below; no search, selection, booking, inventory mutation, additional dates, supplier integration, currency, frontend, or Trip behavior changes.

## Scope

### In scope

- A compact H2/Flyway V10 airfare/reference-data migration.
- Fixture-specific integration assertions, deterministic rebuild comparison, and deliberately-corrupted test-scope checks.
- Updating existing migration/startup/catalog tests so they test their current concern against seeded airfare rather than an empty catalog or private overlapping fixtures.
- Packaged-JAR startup verification against its isolated temporary database.

### Out of scope

- Accommodation/rental fixtures or the phase-wide summary generator owned by P02-T03.
- Schema changes, API/controller/service/repository additions, React changes, Trip/itinerary persistence, selection, booking/cancellation, or inventory decrement.
- Live suppliers, dynamic pricing, multiple currencies, origin/destination/date expansion, and any age-priced fare rule.

## Active Project Guardrails

None recorded in `ai/thoughts/design-lens.md`.

## Impact and Risk Analysis

The migration is persisted application data and runs on every clean database, so its keys, count, values, and ordering inputs must be reproducible. Flyway migrations are immutable after release; V10 must add data rather than alter V1–V9.

The material correctness risk is temporal. US and German DST changes occur on different March dates, so fixed `-08`/`+01` literals would produce wrong local schedule/elapsed values. The migration will derive each local timestamp from the service date and an IANA zone (`TIMESTAMP ... AT TIME ZONE zone`), then tests will reconstruct local values with `ZoneId.of` and verify actual elapsed time and layovers. A 45-minute minimum layover is the fixture definition of feasible: existing structural triggers still protect non-negative order, and the fixture verifier adds the stronger catalog promise.

The schema stores one per-instance fare, capacity, and availability and contains no traveler-age field. The smallest correct implementation is to leave that model unchanged, seed availability of at least eight, and test the same stored all-inclusive fare for representative age mixes and party sizes 1–8; adding a speculative age/fare or booking model would violate scope.

Existing catalog tests currently create rows with production-like keys and fixed IDs. They must use the Flyway fixture for common flight reference data and isolated `test-*` lodging/rental rows or key lookups for schema-only checks, so future P02-T03 fixture additions cannot make the test order or identity values brittle.

## Implementation Approach

Add one SQL migration, `V10__seed_march_2027_airfare_catalog.sql`. Use `VALUES`/CTE schedule definitions plus H2 `SYSTEM_RANGE` date sets—not hand-authored instance rows and not application startup generation. Seed stable semantic keys in every base definition and build an instance key as `airfare-<out|in>-<sfo|muc|mex>-<d1|d2|c1|c2>-YYYYMMDD`; the existing immutable-key trigger then prevents post-seed renaming.

The migration will seed exactly these airport policies and zones: PDX `America/Los_Angeles`; SFO `America/Los_Angeles`; MUC `Europe/Berlin`; MEX `America/Mexico_City`; SEA `America/Los_Angeles`; SLC `America/Denver`; ORD `America/Chicago`; LAX `America/Los_Angeles`; DFW `America/Chicago`. Destination airports alone reference their `catalog_destination`; PDX and connection airports remain shared airports with no destination row.

Use fictional suppliers `Cascade Skies` (`supplier-cascade-skies`) and `Meridian Air` (`supplier-meridian-air`). The 24 schedule definitions are keyed by direction, destination, and option (`airfare-out-sfo-d1`, for example); their flight numbers, local timings, fixed per-traveler price components, and capacities are the following implementation data contract. `c1` and `c2` are the required connection alternatives in the stated order.

| Destination / option | Outbound display schedule | Inbound display schedule | Base / tax / fee cents / seats |
| --- | --- | --- | --- |
| SFO d1 | Cascade CS101, PDX 08:15 → SFO 10:10 | Cascade CS102, SFO 08:05 → PDX 10:00 | 16,500 / 1,410 / 390 / 48 |
| SFO d2 | Meridian MA118, PDX 13:40 → SFO 15:45 | Meridian MA119, SFO 16:20 → PDX 18:20 | 15,400 / 1,290 / 360 / 56 |
| SFO c1 (SEA) | Cascade CS121, PDX 06:35 → SEA 07:35; SEA 08:35 → SFO 10:50 | Cascade CS122, SFO 06:15 → SEA 08:30; SEA 09:30 → PDX 10:30 | 11,900 / 1,150 / 330 / 40 |
| SFO c2 (SLC) | Meridian MA128, PDX 09:00 → SLC 11:05; SLC 12:00 → SFO 12:55 | Meridian MA129, SFO 07:20 → SLC 10:10; SLC 11:10 → PDX 11:15 | 13,200 / 1,210 / 350 / 44 |
| MUC d1 | Cascade CS401, PDX 13:10 → MUC 09:00 (+1) | Cascade CS402, MUC 09:15 → PDX 11:30 | 58,800 / 4,670 / 610 / 36 |
| MUC d2 | Meridian MA418, PDX 17:15 → MUC 13:05 (+1) | Meridian MA419, MUC 12:20 → PDX 14:30 | 55,900 / 4,450 / 590 / 40 |
| MUC c1 (SEA) | Cascade CS431, PDX 07:10 → SEA 08:15; SEA 09:20 → MUC 07:00 (+1) | Cascade CS432, MUC 08:20 → SEA 10:15; SEA 11:20 → PDX 12:25 | 50,100 / 4,020 / 550 / 32 |
| MUC c2 (ORD) | Meridian MA438, PDX 05:50 → ORD 11:30; ORD 12:40 → MUC 05:40 (+1) | Meridian MA439, MUC 07:10 → ORD 10:20; ORD 11:30 → PDX 13:45 | 52,600 / 4,210 / 570 / 36 |
| MEX d1 | Cascade CS501, PDX 08:25 → MEX 14:20 | Cascade CS502, MEX 07:55 → PDX 10:05 | 29,400 / 2,360 / 490 / 52 |
| MEX d2 | Meridian MA518, PDX 14:50 → MEX 20:55 | Meridian MA519, MEX 14:10 → PDX 16:20 | 27,200 / 2,180 / 460 / 60 |
| MEX c1 (LAX) | Cascade CS531, PDX 06:30 → LAX 08:45; LAX 10:00 → MEX 15:35 | Cascade CS532, MEX 06:40 → LAX 08:40; LAX 09:45 → PDX 12:00 | 22,300 / 1,780 / 410 / 48 |
| MEX c2 (DFW) | Meridian MA538, PDX 07:15 → DFW 13:10; DFW 14:15 → MEX 15:55 | Meridian MA539, MEX 08:30 → DFW 12:05; DFW 13:15 → PDX 15:30 | 24,700 / 1,960 / 430 / 54 |

For every date/schedule cross-product, create a direct/one-stop `flight_instance` and one/two instance segments. Store local schedule arrival-day offsets (MUC outbound final legs are +1; all listed connection first legs and inbound final legs are same-day). Generate actual segments from their local date/time and airport zone with H2 `DATEADD` plus `AT TIME ZONE`; do not calculate timestamps from a single fixed offset. The elapsed itinerary is deliberately derived as the final `arrival_at` minus the first `departure_at`, the authoritative form already stored by the schema; no redundant duration column is warranted.

The alternatives are intentionally non-collapsing: direct choices have fewer stops, connection choices include the lowest all-inclusive prices, timings vary from early connecting departures to later directs, and actual elapsed duration varies by route/connection (including DST-aware variations). Thus Phase 4 receives meaningful price, duration, earliest-departure, and stop-count inputs while retaining immutable instance keys as later tie-breakers.

## Phase 1: Seed the immutable March airfare catalog

### Changes

- [ ] `src/main/resources/db/migration/V10__seed_march_2027_airfare_catalog.sql` — insert the three destination rows, nine real-code/geographically correct airport rows, two fictional AIRLINE supplier rows, 24 reusable schedule/segment definitions, then cross-join the direction-specific March date sets to insert exactly 720 dated instances and 1,080 actual segments. Use the schedule matrix above, stable semantic keys, all-initial availability equal to capacity, and H2 local-date/IANA-zone timestamp construction.
- [ ] `src/main/resources/db/migration/V10__seed_march_2027_airfare_catalog.sql` — constrain generation in SQL to outbound March 1–30 and inbound March 2–31, and retain only schedule definitions whose derived final actual arrival is on or before March 31. Keep all values as deterministic literals/expressions; do not touch earlier migrations or add a runtime generator.

### Automated verification

- [ ] `AirfareFixtureIntegrityIntegrationTest` focused run — a clean Flyway database has the planned reference rows, 24 schedules, 720 instances, and 1,080 actual segments with the planned key/value matrix.

### Optional developer checks

- [ ] None.

**Success criteria:** migrating an empty H2 database succeeds using V1–V10, produces no date outside the specified service-date sets, and produces the exact catalog counts above with stable fixture keys.

## Phase 2: Add fixture integrity and determinism coverage

### Changes

- [ ] `src/test/java/app/detour/catalog/AirfareFixtureIntegrityAssertions.java` — add a package-private JDBC assertion helper that validates the entire seeded airfare contract: required airports and `ZoneId` values; direct/one-stop count for every destination/direction/usable date; required connection pairs; contiguous route endpoints; actual timestamp order; 45+ minute one-stop layovers; derived itinerary durations; service-date/local-time reconstruction; final-arrival cutoff; immutable/unique semantic keys; positive all-inclusive price and capacity/availability sufficient for parties through eight; and meaningful comparison variation.
- [ ] `src/test/java/app/detour/catalog/AirfareFixtureIntegrationTest.java` — migrate fresh UUID-named in-memory H2 databases with Flyway, run the assertion helper against the clean fixture, calculate same-per-traveler all-inclusive totals for representative mixed-age parties of sizes 1–8, compare ordered full fixture snapshots from two independent rebuilds, and prove representative coverage, connection, timing/layover, price, capacity, duplicate-key, and final-arrival violations are rejected either by the helper or the existing database integrity constraints.

### Automated verification

- [ ] `./mvnw.cmd -DskipFrontend=true -Dtest=AirfareFixtureIntegrityIntegrationTest test` — clean migration, full integrity invariant set, deliberate failures, and independent deterministic rebuild pass.

### Optional developer checks

- [ ] None.

**Success criteria:** the valid V10 fixture passes all catalog assertions; each named deliberate mutation causes the applicable assertion or H2 constraint to fail; two independent clean migrations produce byte-for-byte-equivalent ordered fixture snapshots.

## Phase 3: Preserve existing migration, schema, temporal, and startup checks

### Changes

- [ ] `src/test/java/app/detour/DetourApplicationTest.java` — update `startsWithFreshDetourCatalogLineageAndNoFixtureData` to assert V1–V10, zero seeded users, the airfare/reference-data counts owned here, and still-empty accommodation/rental inventory tables; retain absence checks for legacy tables.
- [ ] `src/test/java/app/detour/catalog/PhaseOneCatalogForwardMigrationIntegrationTest.java` — expect V10 after migrating a V2 identity database forward, retain the user-row preservation assertion, and add a lightweight assertion that the seeded airfare catalog is available after the forward migration.
- [ ] `src/test/java/app/detour/catalog/CatalogSchemaFixtures.java` and `src/test/java/app/detour/catalog/CatalogSchemaIntegrationTest.java` — stop using an empty-catalog gate and fixed identity values; use key lookups and idempotent isolated `test-*` lodging/rental rows for schema-only cases, with fresh non-covered test dates for invalid inserts. Preserve all existing constraint/immutability intent without colliding with product fixture rows.
- [ ] `src/test/java/app/detour/catalog/CatalogTemporalMoneyRoundTripIntegrationTest.java` — use a stable migrated airfare row and the seeded PDX/SFO/MUC/MEX zone records for money and offset round-trip coverage instead of reinserting overlapping airports/destinations.

### Automated verification

- [ ] `./mvnw.cmd -DskipFrontend=true -Dtest=AirfareFixtureIntegrityIntegrationTest,DetourApplicationTest,PhaseOneCatalogForwardMigrationIntegrationTest,CatalogSchemaIntegrationTest,CatalogTemporalMoneyRoundTripIntegrationTest test` — affected migration/schema/startup tests pass together.
- [ ] `./mvnw.cmd test -DskipFrontend=true` — the complete safe backend suite passes.
- [ ] `./mvnw.cmd package` then `./scripts/verify-packaged-identity.ps1` — the package starts on its isolated loopback H2 database with no provider/model dependency and retains identity-shell behavior.

### Optional developer checks

- [ ] None.

**Success criteria:** no existing test assumes an empty airfare catalog or fixture-assigned numeric ID; identity preservation, the full backend suite, and packaged startup all pass after V10 is applied.

## Test Strategy

Use integration tests at the Flyway/H2 boundary because the observable deliverable is persisted catalog data. A reusable test-only JDBC verifier provides complete coverage over the generated set and can be run against both valid clean databases and isolated deliberately damaged databases. Preserve lower-level schema tests for constraints, but do not mistake them for fixture completeness tests. The separate testing plan specifies the red test, mutation cases, snapshots, and safe commands.

## Acceptance-Criteria Traceability

| Acceptance criterion | Planned code evidence | Planned test evidence |
| --- | --- | --- |
| Exactly 2 direct + 2 one-stop per usable combination | V10 schedule CTE and two direction date sets | Complete cross-product/count assertions |
| Required connections and feasible zoned timing | V10 airport/schedule/actual-segment seed data | Route, zone, chronology, 45-minute layover, and duration assertions |
| March 31 arrival boundary and valid 1–14 night coverage | V10 outbound/inbound range construction and final-arrival predicate | First/last-date coverage plus final-arrival assertion and corrupt-arrival test |
| Stable display, price, and capacity inputs | V10 suppliers, flight numbers, semantic keys, cents, and capacities | Key/display/price/capacity/immutability assertions |
| Same fare and seat per traveler; useful variation | V10 single fare row per instance and 32+ capacities | Party-size/age-mix arithmetic and distinct price/duration/departure/stop assertions |
| Rebuild determinism | Deterministic literal/CTE/date generation in V10 | Ordered snapshots from two fresh Flyway databases |
| Deliberate invariant failures | Existing H2 constraints plus test-only verifier | Representative corrupt coverage/timing/connection/price/capacity/key/arrival cases |
| Existing behavior and package startup | Updated affected regression tests; no app-layer change | Backend suite, package, and isolated loopback script |
| No out-of-scope behavior | V10 plus test-only source only | Changed-path/diff review and absence of API/UI/domain additions |

## Risks and Rollback/Recovery

The main rollout risk is a faulty SQL expression or DST assumption causing V10 to fail or produce wrong actual timestamps. The integration test migrates clean H2 databases before packaging and exercises representative boundary dates, so failure is caught before a developer uses the application database. During this pre-release clean-break stage, recovery is to revert the ticket's migration/test changes and recreate disposable local DeTour databases using the existing explicit reset process; do not edit an already-applied migration in place or introduce data compatibility paths.

## References

- `ai/thoughts/tickets/2026-09-18-p02-t02-seed-complete-airfare-catalog.md`
- `ai/thoughts/research/2026-09-18-p02-t02-seed-complete-airfare-catalog.md`
- `ai/thoughts/phases/README.md`
- `ai/thoughts/phases/CONTINUATION.md`
- `ai/thoughts/phases/phase-2-catalog-and-fixtures.md`
- `src/main/resources/db/migration/V3__create_shared_catalog_and_flight_schema.sql`
- `src/main/java/app/detour/catalog/persistence/FlightSegmentIntegrityTrigger.java`
