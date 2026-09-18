---
date: 2026-09-18
repository: loomspan-travel-demo
branch: main
commit: 8d0dbc6a80b6fb531a3302339f35855b326504d0
ticket: ai/thoughts/tickets/2026-09-18-p02-t01-establish-catalog-inventory-foundation.md
tags: [detour, flyway, h2, catalog, inventory, phase-2]
---

# DeTour Catalog and Inventory Foundation Research

## Research Question

What Phase 1 persistence, runtime, tests, and documented domain boundaries exist for adding the Phase 2 application-owned catalog and finite-inventory foundation without changing identity behavior or introducing later workflows?

## Summary

The checked-out application has a fresh two-migration DeTour Flyway lineage: an intentionally empty `V1` and `V2` containing only `detour_user`. No catalog, trip, itinerary, booking, reservation, pricing, or inventory production code or migration currently exists. The runtime is Spring Boot JDBC with Flyway and H2; existing migration tests use a new in-memory database and assert exactly versions `1` and `2`, while restart integration uses a temporary file-backed H2 database to prove identity data survives a process restart.

The product records identify catalog and inventory as shared application data, not user-owned data. They also establish the downstream requirements that stable catalog identifiers, integer-cent monetary inputs, zoned timing, finite capacity, nightly inventory, and rental intervals later support deterministic selection, snapshots, and transactional reservation/restoration.

## Repository State

- Observed 2026-09-18T10:24:38-07:00 on repository `loomspan-travel-demo`, branch `main`, commit `8d0dbc6a80b6fb531a3302339f35855b326504d0`.
- `git status --short --branch` reported `## main...origin/main` with no staged, unstaged, or untracked files. There is no pre-existing ticket diff to preserve.
- Recent history created Phase 1’s platform and identity migrations in commits `0aff2b4` and `796c523`; the current `8d0dbc6` commit only created Phase 2 tickets. The migration-file history shows no rewrite after those additions.

## Current Behavior and Data Flow

1. `DetourApplication` starts the Spring application; the Spring Boot Flyway starter and configured datasource then discover the default `classpath:db/migration` location.
2. `application.yml` selects a file-backed H2 URL by default, with `DETOUR_DATABASE_URL` override support. The normal local database is `data/detour`; restart tests replace it with an isolated temporary file database.
3. The applied lineage currently has only `V1__detour_platform.sql` (a comment-only baseline) and `V2__create_detour_user_identity.sql`, which creates `detour_user` with an identity primary key, unique canonical email, and `TIMESTAMP WITH TIME ZONE` creation time.
4. The JDBC identity repository reads and writes only `detour_user`. Its `OffsetDateTime` mapping demonstrates that the current JDBC/H2 stack maps a `TIMESTAMP WITH TIME ZONE` value to an explicit-offset Java type for identity timestamps.
5. Registration and profile behavior call that repository through `IdentityService`; no production package, route, repository, or migration exposes catalog records or inventory mutation. The frontend README explicitly describes the current foundation as containing no catalog, trip, or booking behavior.

## Key Components

- `pom.xml:18-60` — provides Spring MVC, JDBC, Flyway, runtime H2, and tests; there is no JPA/ORM or catalog-specific library.
- `src/main/resources/application.yml:12-25` — configures the `detour` Spring application and the overridable H2 datasource; no Flyway location override or catalog-specific configuration is present.
- `src/main/resources/db/migration/V1__detour_platform.sql:1` — completed forward-lineage baseline with no schema objects.
- `src/main/resources/db/migration/V2__create_detour_user_identity.sql:1-7` — completed identity schema that must remain in place for a Phase 1 database to retain accounts.
- `src/main/java/app/detour/identity/JdbcDetourUserRepository.java:20-60` — sole current JDBC persistence adapter; reads/writes only `detour_user` and maps `created_at` as `OffsetDateTime`.
- `src/test/java/app/detour/DetourApplicationTest.java:34-57` — clean in-memory Flyway assertion currently expects only migration versions `1` and `2`, checks the identity table is empty, and asserts specified legacy tables are absent.
- `src/test/java/app/detour/identity/ApplicationRestartIntegrationTest.java:23-60` — boots twice against one temporary file-backed H2 URL, proving a registered account persists while the old session is rejected.
- `scripts/reset-detour.ps1:1-23` — explicit, confirmation-gated removal of only `data/detour.mv.db`; application startup itself has no reset behavior.
- `scripts/verify-packaged-identity.ps1:1-43` — packaged-JAR smoke check starts on a temporary loopback port and isolated temporary H2 database, then removes its own process and temporary files.
- `ai/thoughts/phases/README.md:36-38` — records the fresh DeTour V1 lineage and clean-break rule: existing Wayfarer history/data is disposable, while there is no Wayfarer compatibility path.
- `ai/thoughts/architecture/2026-09-17-p00-t02-detour-replacement-architecture.md:59-77` — defines catalogs and inventory as shared application-owned data rather than relationally owned by a User or Trip.

## Affected Areas

| Area | Current behavior and evidence |
| --- | --- |
| Flyway lineage | Only `V1` and `V2` exist in `src/main/resources/db/migration`. `DetourApplicationTest` currently asserts the exact two-version list (`src/test/java/app/detour/DetourApplicationTest.java:35-40`), so the clean-lineage assertion is coupled to the present migration count. |
| Phase 1 identity persistence | `detour_user` is independent of future catalog data and is accessed by `JdbcDetourUserRepository` (`src/main/java/app/detour/identity/JdbcDetourUserRepository.java:20-57`). Restart coverage validates the identity database survives an application restart (`ApplicationRestartIntegrationTest.java:23-53`). |
| Catalog ownership | The architecture says shared catalogs/inventory are not user-owned and must not be reached by treating them as Trip/User ownership (`ai/thoughts/architecture/2026-09-17-p00-t02-detour-replacement-architecture.md:65-77`). No current relational catalog ownership path exists. |
| Required downstream data semantics | The Phase 2 record names destinations, airports, suppliers, schedules/instances, accommodations/units/nights, rental locations/classes/units, time-bounded rental reservations, and inventory (`ai/thoughts/phases/phase-2-catalog-and-fixtures.md:9-16`). Flight, stay, and rental fixture tickets rely on stable identifiers and their respective dated/interval capacities (`ai/thoughts/tickets/2026-09-18-p02-t02-seed-complete-airfare-catalog.md:9-18`; `ai/thoughts/tickets/2026-09-18-p02-t03-complete-stay-rental-fixtures.md:9-18`). |
| Time and money | Current persisted time uses `TIMESTAMP WITH TIME ZONE` and `OffsetDateTime` only for user creation (`V2__create_detour_user_identity.sql:5`; `JdbcDetourUserRepository.java:40-50`). No money representation exists. The roadmap settles USD integer cents and time-zone-aware schedule presentation (`ai/thoughts/phases/README.md:92-102`, `156-164`). |
| HTTP/frontend scope | The only current endpoint surface is identity/profile; the README states there is no catalog, trip, or booking behavior (`README.md:1-16`). No catalog API/UI, trip persistence, booking/cancellation, or inventory service is present. |
| Operations | Normal runs use a local file H2 database; tests use isolated memory/file URLs. The packaged-identity check is local loopback only and does not contact suppliers or model services (`README.md:14-24`; `scripts/verify-packaged-identity.ps1:19-42`). |

## Existing Tests and Fixtures

- `DetourApplicationTest` is the focused clean-database/Flyway test. It checks the applied-version list, table presence, empty identity data, and absence of listed legacy tables; it contains no catalog constraints, pricing, time-zone round-trip, capacity, or inventory checks.
- `ApplicationRestartIntegrationTest` verifies file-backed H2 migration/identity persistence and invalidates an old in-memory servlet session after restart. It does not exercise catalog data.
- `IdentityApiIntegrationTest` covers registration, login, password changes, CSRF, and protected profile behavior (`src/test/java/app/detour/identity/IdentityApiIntegrationTest.java:39-145`); it is unrelated to Phase 2 persistence except that it runs against a clean in-memory Flyway database.
- No test resources, catalog fixtures, SQL fixtures, or schema-level negative-case tests exist in the current checkout.
- Executed `./mvnw.cmd -DskipFrontend=true test` on 2026-09-18. It compiled 21 main and 3 test sources and passed 8 tests with no failures, errors, or skips. The run used H2 `2.4.240`; Flyway logged that this H2 version is newer than the Flyway-verified `2.3.232` version. The first sandboxed attempt could not create `C:\.m2\repository`; the successful rerun used the configured local Maven cache with approval.

## Dependencies and Operational Constraints

- Java source compatibility is configured as Java 21 (`pom.xml:14-17`); the executed Maven baseline used Java 25. Maven 3.9.11, Spring Boot 4.1.0, and H2 2.4.240 were observed during the test run.
- Maven’s `generate-resources` executions run `npm ci` and the frontend build by default (`pom.xml:74-103`); the executed backend check set `-DskipFrontend=true`.
- The application’s default database is persistent local state and the reset script is explicitly confirmation gated. The ticket’s forward-only DeTour requirement is consistent with the checked-in `V1`/`V2` history; there is no current Wayfarer migration or fallback configuration.
- Future component data is constrained by the recorded March 2027/PDX/SFO/MUC/MEX product boundary, USD cents, and fictional suppliers with real airport geography (`ai/thoughts/phases/README.md:79-102`). The record defers search, Trip/itinerary, booking/cancellation, live suppliers, dynamic pricing, multi-currency, and dates outside the supported window.

## Historical Context

- `0aff2b4` introduced the empty DeTour `V1` baseline after the clean-break platform reset; `796c523` added `V2` identity. Git history shows the active migrations were added, not subsequently edited.
- The architecture and Phase 1 documents superseded the former Wayfarer persistence-preservation approach: Wayfarer databases and Flyway history are disposable development state, while the DeTour migration chain is a fresh lineage (`ai/thoughts/phases/README.md:13-15`; `ai/thoughts/phases/phase-1-platform-reset-and-identity.md:19-25`).
- Phase 2 separates schema design (this ticket) from fixture population. The airfare ticket owns deterministic recurring/dated flight generation; the stay/rental ticket owns the remaining fixture values, cross-catalog integrity report, and summary output.

## Open Questions

- The checked-out code has no catalog schema or database constraint patterns beyond primary key, unique, and `NOT NULL`; planning must establish the concrete H2-compatible relational representation for the documented capacity, pricing, temporal-range, and rental-overlap invariants.
- The current clean-Flyway test asserts exactly versions `1` and `2`; the existing suite does not yet state how a forward catalog migration should be asserted alongside preservation of pre-existing Phase 1 users.
- The product documentation specifies requirements for later atomic reservation/restoration and immutable snapshots, but no current repository code supplies a catalog reference or reservation-record interface to validate against.
