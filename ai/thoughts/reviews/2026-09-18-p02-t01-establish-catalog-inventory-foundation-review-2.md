# P02-T01 — Establish the DeTour Catalog and Inventory Foundation Code Review — Cycle 2

## Scope and Repository State

Reviewed the ticket-scoped unstaged and untracked implementation against `main` at `8d0dbc6`, including migrations `V3`–`V6`, the two H2 triggers, catalog integration tests, Flyway/identity regression tests, dependency scope, ticket, research, and both plans. No staged changes were present. Existing Phase 1 migrations remain unmodified; no catalog HTTP, UI, Trip, booking, payment, or external-supplier behavior was added.

## Findings

No actionable findings.

## Findings Resolved in This Context

### [P1] Preserve accommodation metadata required by the settled fixture contract
- Location: `src/main/resources/db/migration/V5__add_accommodation_search_metadata.sql:1`
- Scenario: The original accommodation property model had no location, rating, or city-center-distance fields, so P02-T03 could not seed the required property display/location/rating/distance data or support its later deterministic stay ordering.
- Impact: The next required Phase 2 fixture ticket would have needed to alter this supposedly settled persistence contract and could not satisfy its acceptance criteria with the original schema.
- Evidence: `ai/thoughts/tickets/2026-09-18-p02-t03-complete-stay-rental-fixtures.md` requires deterministic location, guest-rating, and city-center-distance data; the original `accommodation_property` table contained only supplier, destination, category, and name.
- Fix: Added forward `V5` fields with bounds checks and updated valid test fixtures plus negative constraint assertions.

### [P2] Reject structurally invalid flight segment rows
- Location: `src/main/java/app/detour/catalog/persistence/FlightSegmentIntegrityTrigger.java:24`
- Scenario: A direct schedule or instance could accept a second segment, and a schedule segment could be changed to no longer begin at its scheduled origin, despite the parent declaring a direct/one-stop shape.
- Impact: Fixture generation could persist route-incoherent itinerary data that later search, timing, and booking code would treat as a valid catalog option.
- Evidence: `flight_schedule_segment` and `flight_instance_segment` originally allowed ordinal `2` independently of their parents' `segment_count`, with no route or layover cross-row validation.
- Fix: Added forward `V6` H2 triggers that constrain segment count, route endpoints, connection continuity, and one-stop layovers; added JDBC rejection coverage.

## Acceptance-Criteria and Plan Conformance

| Criterion/decision | Code evidence | Test evidence | Result: implemented/partial/missing/safe deviation |
| --- | --- | --- | --- |
| Clean forward DeTour lineage and no Wayfarer compatibility | Additive `V3`–`V6`; `V1`/`V2` unchanged | `DetourApplicationTest` applies versions `1`–`6` and checks empty catalog/legacy absence | implemented |
| Forward migration preserves Phase 1 users | No change to `detour_user`; additive migrations only | `PhaseOneCatalogForwardMigrationIntegrationTest` migrates a file H2 database from V2 through V6 and preserves user data | implemented |
| Database rejects invalid catalog values and references | Checks, foreign/composite keys, rental overlap trigger, flight integrity trigger | `CatalogSchemaIntegrationTest` and `RentalUnitOccupancyConstraintIntegrationTest` | implemented |
| Flight, stay, and rental finite-inventory models are representable | Schedule/instance segments, nightly inventory, physical units and occupancy records | Catalog schema integration tests exercise direct/one-stop, room, whole-property, and rental controls | implemented |
| Stable shared identifiers without user ownership | Unique catalog keys and no `detour_user` references | Metadata and duplicate-key assertions in `CatalogSchemaIntegrationTest` | implemented |
| USD cents and zoned time round-trip | `BIGINT` cents, airport IANA IDs, `TIMESTAMP WITH TIME ZONE` | `CatalogTemporalMoneyRoundTripIntegrationTest` | implemented |
| Preserve Phase 1 runtime and packaged startup | Identity runtime left unchanged | Full Maven suite, package, and packaged identity smoke | implemented |
| Deferred workflows remain absent | DDL and triggers only; no catalog application surface | Source-scope review plus existing identity integration tests | implemented |

## Active Project Guardrails

- None recorded in `ai/thoughts/design-lens.md`.

## Open Questions and Assumptions

- The V5 metadata columns are `NOT NULL` because this ticket's V3/V4 contract intentionally creates no catalog rows; the required V2-forward migration test proves the supported pre-catalog upgrade path.

## Verification Results

- PASS — `.\\mvnw.cmd -DskipFrontend=true "-Dtest=CatalogSchemaIntegrationTest,RentalUnitOccupancyConstraintIntegrationTest,CatalogTemporalMoneyRoundTripIntegrationTest,PhaseOneCatalogForwardMigrationIntegrationTest,DetourApplicationTest" test` — 7 tests passed.
- PASS — `.\\mvnw.cmd -DskipFrontend=true test` — 14 tests passed.
- PASS — `.\\mvnw.cmd -DskipFrontend=true package` — packaged `target/detour-0.1.0-SNAPSHOT.jar` successfully.
- PASS — `powershell -ExecutionPolicy Bypass -File .\\scripts\\verify-packaged-identity.ps1` — isolated packaged identity shell verification passed.

## Residual Risks and Optional Developer Checks

- H2 2.4.240 is newer than Flyway's verified H2 version (2.3.232), as reported by Flyway during every local verification run. This is an environment compatibility warning, not a ticket-specific test failure.

## Disposition

- `fixes-applied`
