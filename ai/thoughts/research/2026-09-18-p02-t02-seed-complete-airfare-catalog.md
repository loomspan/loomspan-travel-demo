---
date: 2026-09-18
repository: loomspan-travel-demo
branch: main
commit: 2a1f68841bbd3705ad1653933f2b3bc28f7d8c91
ticket: ai/thoughts/tickets/2026-09-18-p02-t02-seed-complete-airfare-catalog.md
tags: [phase-2, catalog, airfare, flyway, fixtures, h2]
---

# P02-T02 — Seed Complete Deterministic Airfare Fixtures Research

## Research Question

What persisted airfare model, Flyway/test conventions, existing constraints, and repository boundaries must the deterministic March 2027 PDX round-trip fixture work use?

## Summary

The checked-out P02-T01 schema contains empty shared catalog, reusable flight-schedule, dated flight-instance, and dated-segment tables. It can represent direct and one-stop journeys, individual integer-cent price inputs, finite seat capacity, immutable stable catalog keys, and offset-aware instants; it has no existing airfare fixtures, search API/UI, booking behavior, or summary generator.

The data contract needs both reusable schedule rows and date-specific rows: `flight_schedule` fixes route/display/stops, schedule segments capture local schedule fields, `flight_instance` binds a schedule to a `service_date` and its price/capacity, and instance segments retain actual zoned timestamps. Existing H2 triggers preserve direct/one-stop segment shape and chronological non-negative layovers but do not enforce required airport-pair sets, full date coverage, valid IANA zone IDs, the March 31 boundary, or exact choice counts.

The only pre-existing modification is the ticket execution note added after triage; it is ticket-scoped. The Full 5-Step Pipeline remains appropriate because the fixture introduces a persisted temporal dataset and verification contract; no profile reassessment is required.

## Repository State

- Observed 2026-09-18T15:58:15-07:00 on branch `main`, commit `2a1f68841bbd3705ad1653933f2b3bc28f7d8c91` (`clean up from P02-T01`).
- The worktree has one modification: `ai/thoughts/tickets/2026-09-18-p02-t02-seed-complete-airfare-catalog.md`. Its diff adds the developer's Full-pipeline approval and says that subsequent changes are attributed to this ticket.
- The P02-T01 implementation commit is `39e08a2f283bfa97dec186448757a35be6adde7d`; the next cleanup commit intentionally removed its temporary pipeline artifacts. No P02-T02 research/plan/testing artifact existed before this step.

## Current Behavior and Data Flow

1. Spring Boot loads the default Flyway location into an H2 datasource at startup (`src/main/resources/application.yml:13-17`; `pom.xml:35-43`). The committed migration lineage currently ends at V9.
2. `catalog_airport` retains one unique IATA code, optional destination association, coordinates, and an arbitrary nonempty zone string (`src/main/resources/db/migration/V3__create_shared_catalog_and_flight_schema.sql:14-31`). `catalog_supplier` categorizes suppliers, including `AIRLINE` (`V3...sql:33-41`). Neither table has data after a clean build.
3. A flight schedule belongs to an airline and declares one route, stop count 0/1, and one/two segment count. A schedule's flight number is unique per airline (`V3...sql:43-63`). Its segments store ordered airport legs, local departure/arrival times, day offset, and positive scheduled duration (`V3...sql:65-83`).
4. A dated flight instance has a unique stable `catalog_key` and a unique `(flight_schedule_id, service_date)` pair, along with base fare, taxes, fee, total/available seat capacity, and a one/two segment-count foreign key to its schedule (`V3...sql:85-105`). Its actual segments use `TIMESTAMP WITH TIME ZONE` and require strictly increasing timestamps (`V3...sql:107-117`).
5. `FlightSegmentIntegrityTrigger` checks that schedule segment endpoints match their parent route and each other, instance ordinal does not exceed schedule shape, and an adjacent instance leg has a non-negative layover. It also prevents deletion once a schedule or instance is complete (`src/main/java/app/detour/catalog/persistence/FlightSegmentIntegrityTrigger.java:25-42`, `45-98`, `134-200`).
6. V8 adds immutable-key triggers for the catalog and flight rows (`src/main/resources/db/migration/V8__enforce_catalog_key_immutability_and_schedule_times.sql:5-19`), and V9 prevents overflow when summing the three airfare price inputs (`V9__prevent_all_inclusive_price_overflow.sql:1-3`).

## Key Components

- `src/main/resources/db/migration/V3__create_shared_catalog_and_flight_schema.sql:14` — airport catalog, airline supplier, flight schedule/segment, flight instance/segment model.
- `src/main/resources/db/migration/V6__enforce_flight_segment_integrity.sql:1` and `V7__preserve_completed_flight_segment_shape.sql:1` — register the H2 flight-shape triggers, including deletion protection.
- `src/main/resources/db/migration/V8__enforce_catalog_key_immutability_and_schedule_times.sql:1` — validates non-overnight local time ordering and makes fixture catalog keys immutable after insertion.
- `src/main/resources/db/migration/V9__prevent_all_inclusive_price_overflow.sql:1` — preserves safe addition of per-traveler base fare, tax, and fee cents.
- `src/main/java/app/detour/catalog/persistence/FlightSegmentIntegrityTrigger.java:45` — enforcement of schedule endpoints/connection continuity; `:134` — enforcement of instance chronology.
- `src/test/java/app/detour/DetourApplicationTest.java:30` — current clean-lineage assertion expects V1–V9 and expects every catalog table to be empty (`:50-59`).
- `src/test/java/app/detour/catalog/CatalogSchemaIntegrationTest.java:43` — existing schema-level rejection/immutability tests; its test helper creates its own minimum rows rather than using Flyway fixtures.
- `src/test/java/app/detour/catalog/CatalogTemporalMoneyRoundTripIntegrationTest.java:27` — verifies `OffsetDateTime` persistence and `ZoneId` reconstruction from the airport zone string.
- `src/test/java/app/detour/catalog/PhaseOneCatalogForwardMigrationIntegrationTest.java:19` — applies V1–V2 to a file H2 database, then the full lineage while preserving an identity row; it presently asserts V9 at `:33`.
- `scripts/verify-packaged-identity.ps1:19` — package-level startup check uses a temporary isolated H2 database and performs only identity-shell HTTP checks.

## Affected Areas

| Area | Current behavior and evidence |
| --- | --- |
| Flyway migration lineage | V1–V9 have no catalog rows after clean migration. A new versioned migration can add data without altering earlier applied migrations; V3 has the airfare tables and V8 makes inserted keys immutable. |
| Airport/reference data | No PDX, SFO, MUC, MEX, SEA, SLC, ORD, LAX, or DFW record currently exists. Airport schema retains IATA, coordinates, zone ID, and optional destination relationship; it does not validate the zone name beyond nonemptiness (`V3...sql:14-31`). |
| Flight definitions | A schedule permits at most one/ two segments according to its stop count, the supplier/flight number pair is unique, and the instance table permits one dated row per schedule. These constraints permit, but do not themselves require, two direct and two one-stop choices per destination/direction/date (`V3...sql:43-105`). |
| Timing/local display | Static local times and day offsets live on schedule segments; authoritative per-date instants with offsets live on instance segments. Itinerary elapsed time is derivable as final `arrival_at` minus first `departure_at`; no dedicated aggregate itinerary-duration column is currently persisted (`V3...sql:65-83`, `107-117`). |
| Time/connection integrity | The trigger requires connected route segments and prevents a later leg from departing before the first arrives, but it accepts a zero-minute layover and has no required-connection airport policy or date-boundary rule (`FlightSegmentIntegrityTrigger.java:69-98`, `155-183`). |
| Pricing and capacity | Base fare, tax, fee, seat capacity, and available seats are per flight instance and constrained to nonnegative/positive ranges; totals cannot overflow (`V3...sql:85-105`; `V9...sql:1-3`). There is no age, fare-class, party, or inventory-mutation model in the schema. |
| Application layers | No Java catalog repository/service/controller and no React catalog result surface exists. The ticket's exclusions match the present implementation. |
| Startup/package verification | Maven packages Spring Boot plus the React shell (`pom.xml:60-126`); the packaged verification recreates a temporary H2 database at runtime and will apply any added Flyway fixture migration (`scripts/verify-packaged-identity.ps1:19-36`). |

## Existing Tests and Fixtures

- `CatalogSchemaFixtures` is a test-only hand-insert helper that supplies one SFO direct flight plus lodging/rental rows for schema tests (`src/test/java/app/detour/catalog/CatalogSchemaFixtures.java:11-54`). It should not be read as product fixture coverage.
- `CatalogSchemaIntegrationTest` exercises database rejection of invalid money, capacity, route/segment shape, catalog-key mutations, and shared-catalog ownership (`CatalogSchemaIntegrationTest.java:43-115`). It demonstrates an accepted one-stop shape and deletion protection (`:120-154`). It does not exercise Flyway-created airport data, airfare coverage/counts, required connections, final-arrival cutoff, determinism, or ordering variation.
- `CatalogTemporalMoneyRoundTripIntegrationTest` proves offset-aware round trips and application-side `ZoneId.of` lookup for its inserted zones (`CatalogTemporalMoneyRoundTripIntegrationTest.java:27-62`). It does not validate every fixture airport or fixture itinerary duration.
- `PhaseOneCatalogForwardMigrationIntegrationTest` is the nearest clean/forward Flyway test; it preserves a V2 identity row and checks the final migration version (`PhaseOneCatalogForwardMigrationIntegrationTest.java:19-45`).
- The baseline application test will fail after a fixture migration until its explicit “no fixture data” and expected-version assertions are replaced or narrowed (`DetourApplicationTest.java:30-59`).
- No airfare-specific integrity test, deterministic rebuild comparison, fixture summary command, search/selection behavior, or age-based airfare behavior test exists. This ticket owns the airfare integrity test surface; the phase's consolidated summary/cross-catalog reporting is assigned downstream to P02-T03 by its ticket.

## Dependencies and Operational Constraints

- The authoritative roadmap fixes the origin/destinations, March 1–31 2027 window, 1–14-night trip length, party size 1–8, same fare/one seat per traveler, direct/one-stop inventory, connection pairs, immutable final sort tie-breaker, USD cents, and final-arrival cutoff (`ai/thoughts/phases/README.md`, “Supported travel” and “Components and ranking”).
- The Phase 2 document requires compact Flyway SQL from recurring definitions/sequences rather than thousands of hand-authored rows and reserves the final whole-catalog summary for Phase 2.5 (`ai/thoughts/phases/phase-2-catalog-and-fixtures.md:13-35`).
- The ticket explicitly excludes any search, selection, trip, booking, cancellation, dynamic pricing, multi-currency, live supplier, or extra-date behavior. There is no user-owned relationship in catalog tables (`CatalogSchemaIntegrationTest.java:102-115`).
- H2 is the production/test database and Flyway migration engine already on the application classpath (`pom.xml:35-43`). Package verification starts an actual JAR on loopback against a temporary local H2 database; it has no external supplier/model dependency (`scripts/verify-packaged-identity.ps1:19-42`).
- Java 21 and Node/npm are required for the full package according to `README.md:7-10`; this research step did not run builds or live application startup.

## Historical Context

- P02-T01 was committed immediately before this ticket and establishes the flight data model. Its ticket says exact fixture names/schedules/prices/capacities are implementation-design choices and its completed acceptance criteria state that the schema represents recurring/dated direct and one-stop flights with zoned local timing, per-seat pricing, and capacity (`ai/thoughts/tickets/2026-09-18-p02-t01-establish-catalog-inventory-foundation.md:9-31`).
- The roadmap's final clarification states that fixture coverage is for direction/date combinations usable by a valid 1–14-night trip, not every calendar date in both directions (`ai/thoughts/phases/CONTINUATION.md`, “Settled clarifications from final roadmap review”).
- P02-T03 depends on both P02-T01 and P02-T02 and will add stay/rental fixtures plus the final cross-catalog integrity report and summary (`ai/thoughts/tickets/2026-09-18-p02-t03-complete-stay-rental-fixtures.md:17-29`, `36-43`).

## Open Questions

- The existing schema exposes duration through its first/last dated segment instants but has no stored aggregate itinerary-duration value. Planning needs to make the intended verification/calculation explicit so the ticket’s “total elapsed itinerary duration” requirement has one authoritative interpretation.
- The ticket fixes coverage semantics but leaves schedules open. Planning needs to settle the exact service-date sets after applying both the 1–14-night pairing rule and the chosen final-arrival instants; the model itself does not encode Trip dates or the March 31 cutoff.
- Required connection airports, valid IANA zones, exact 2-direct/2-one-stop counts, boundary compliance, and ranking variation are fixture-level conditions rather than database constraints. Their test scope and deliberate-mutation mechanism remain to be designed.
