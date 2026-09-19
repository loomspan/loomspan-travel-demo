---
date: 2026-09-19
repository: loomspan-travel-demo
branch: main
commit: c01de889066fa04ab248a8010c918b2c1c426135
ticket: ai/thoughts/tickets/2026-09-19-p03-t01-establish-trip-draft-foundation.md
tags: [phase-3, trips, drafts, identity, flyway, authorization]
---

# P03-T01 Owned Trips and Initial Drafts Research

## Research Question

What existing DeTour identity, catalog, persistence, API, test, and frontend conventions must the first persistent, owner-scoped Trip and component-empty Draft use, and what Trip behavior already exists?

## Summary

The checkout contains the completed Phase 1 identity boundary and Phase 2 shared catalog fixtures, but no Trip, traveler, itinerary, Draft, or Trip API implementation. Authentication places a `DetourUserPrincipal` with an internal `userId` on the Spring Security principal; identity endpoints use that value in service calls, and the Phase 1 ticket requires all future user-owned resources to use the relational `User -> Trip -> downstream data` path and return a non-disclosing not-found result for foreign identifiers.

Persistence is H2/Flyway with forward-only numbered migrations through `V11`. The seeded catalog has exactly the three supported destination records—`destination-sfo`, `destination-muc`, and `destination-mex`—and PDX is a shared airport rather than a user-owned record. Existing APIs use JSON request records, `ApiException`, and `ApiExceptionHandler`; existing integration tests establish authenticated MockMvc clients from a CSRF-bearing public-shell request and use temporary H2 restart tests for persistence/session behavior.

The ticket intentionally establishes the shared aggregate creation/retrieval boundary only. Versioned Draft autosave and extra alternatives are reserved for P03-T02, and adult/budget/component readiness for Planned promotion is reserved for P03-T03.

## Repository State

- Observed 2026-09-19T14:48:47-07:00.
- Repository root: `C:/code/loomspan-travel-demo`; branch: `main`; commit: `c01de889066fa04ab248a8010c918b2c1c426135` (`write tickets for Phase 3`).
- `git status --short` produced no entries. No pre-existing working-tree changes were present at research time.
- No prior files existed under `ai/thoughts/research`, `ai/thoughts/plans`, or `ai/thoughts/testing` for this ticket.

## Current Behavior and Data Flow

1. A public `GET /` materializes the CSRF cookie. Registration and login call the identity controller, which saves a session-backed Spring Security context containing `DetourUserPrincipal(userId, email)`; protected controller methods obtain that principal with `@AuthenticationPrincipal` (`src/main/java/app/detour/identity/IdentityController.java:33-81`).
2. `IdentityService` validates inputs, uses `JdbcDetourUserRepository` for `detour_user`, and maps application failures to `ApiException`; its registration and password-change writes are transactional (`src/main/java/app/detour/identity/IdentityService.java:19-74`). The user repository exposes `findById`, which is the current owner lookup pattern (`src/main/java/app/detour/identity/DetourUserRepository.java:5-12`, `src/main/java/app/detour/identity/JdbcDetourUserRepository.java:41-48`).
3. Spring Security permits only the shell/static routes and registration/login; other requests require authentication and state-changing requests require the CSRF cookie/header pair (`src/main/java/app/detour/security/SecurityConfiguration.java:34-52`). Authentication failures are JSON `401 UNAUTHENTICATED`; malformed JSON is JSON `400 MALFORMED_REQUEST`; absent resources become JSON `404 RESOURCE_NOT_FOUND` (`src/main/java/app/detour/security/JsonAuthenticationEntryPoint.java:25-26`, `src/main/java/app/detour/api/ApiExceptionHandler.java:17-34`).
4. Flyway currently applies `V1` through `V11`. `V2` creates only the persistent identity table (`src/main/resources/db/migration/V2__create_detour_user_identity.sql:1-7`). `V3` creates `catalog_destination` with a unique immutable-style catalog key but no Trip relationship (`src/main/resources/db/migration/V3__create_shared_catalog_and_flight_schema.sql:1-12`); `V10` seeds San Francisco, Munich, and Mexico City plus PDX and destination airports (`src/main/resources/db/migration/V10__seed_march_2027_airfare_catalog.sql:4-17`).
5. The current React profile is an identity-only empty state and its API module implements only identity calls (`frontend/src/components/ProfileScreen.tsx:21-25`, `frontend/src/api/identityApi.ts:42-50`). There is no Trip UI, and the ticket explicitly excludes it.

## Key Components

- `src/main/java/app/detour/identity/DetourUserPrincipal.java:7-20` — authenticated principal carrying the persistent internal user key intended for future owner-scoped data access.
- `src/main/java/app/detour/identity/IdentityController.java:22-90` — current HTTP/controller convention, including principal checking and session authentication.
- `src/main/java/app/detour/identity/IdentityService.java:19-82` — transactional service and stable field-level validation-error convention.
- `src/main/java/app/detour/identity/JdbcDetourUserRepository.java:17-61` — JDBC `JdbcTemplate` persistence style and generated-key retrieval pattern.
- `src/main/java/app/detour/api/ApiException.java:5-33` and `src/main/java/app/detour/api/ApiExceptionHandler.java:13-39` — JSON error contract available to the Trip boundary.
- `src/main/java/app/detour/security/SecurityConfiguration.java:34-52` — same-origin session and CSRF boundary that applies automatically to new protected Trip routes.
- `src/main/resources/db/migration/V2__create_detour_user_identity.sql:1-7` — owner table and `BIGINT` identity key.
- `src/main/resources/db/migration/V3__create_shared_catalog_and_flight_schema.sql:1-31` — shared catalog destination/airport keys and relational constraints.
- `src/main/resources/db/migration/V9__prevent_all_inclusive_price_overflow.sql:1-11` — the established `BIGINT` integer-cent storage and overflow-conscious constraint convention.
- `src/main/resources/db/migration/V10__seed_march_2027_airfare_catalog.sql:4-17` — supported destination keys, display names, PDX, and destination time-zone data.
- `src/test/java/app/detour/identity/IdentityApiIntegrationTest.java:28-154` — MockMvc authenticated-client/CSRF helpers and stable HTTP-error assertions.
- `src/test/java/app/detour/identity/ApplicationRestartIntegrationTest.java:23-91` — two-process-context HTTP restart test using a temporary file-backed H2 database.
- `src/test/java/app/detour/catalog/PhaseOneCatalogForwardMigrationIntegrationTest.java:19-55` — forward-migration test that preserves an existing `detour_user` record before applying the complete lineage.

## Affected Areas

| Area | Current behavior and evidence |
| --- | --- |
| Owner identity | `detour_user` is the only current user-owned table. The Phase 1 identity ticket expressly sets the future owner relation to `User -> Trip -> downstream data` and requires non-disclosing not-found responses for another user's identifiers (`ai/thoughts/tickets/2026-09-17-p01-t02-establish-secure-user-identity.md:17-18`). |
| Authorization | The security chain authenticates all non-public routes; it cannot itself scope a resource lookup. Existing controller methods pass `principal.userId()` into services, so per-resource ownership checking is not yet implemented (`src/main/java/app/detour/identity/IdentityController.java:64-70`, `src/main/java/app/detour/security/SecurityConfiguration.java:46-48`). |
| Shared destinations | `catalog_destination` is application-owned reference data with generated numeric IDs and unique `catalog_key`; the three seeded values are `destination-sfo`, `destination-muc`, and `destination-mex` (`src/main/resources/db/migration/V3__create_shared_catalog_and_flight_schema.sql:1-12`, `src/main/resources/db/migration/V10__seed_march_2027_airfare_catalog.sql:4-7`). No Java catalog repository or HTTP lookup API exists. |
| Trip/Draft domain | No source, migration, frontend code, or tests mention a concrete `Trip`, `Draft`, `Itinerary`, traveler, or Trip endpoint. The next ticket, P03-T02, assumes P03-T01 supplies a settled owner-scoped Trip aggregate and version contract (`ai/thoughts/tickets/2026-09-19-p03-t02-deliver-versioned-draft-alternatives.md:31-34`). |
| Transactions and partial writes | Identity registration marks the service entry point `@Transactional` (`src/main/java/app/detour/identity/IdentityService.java:19-30`). There is no existing multi-row aggregate-creation repository/service to reuse. |
| Monetary values | Catalog price columns are `BIGINT` and migration V9 prevents addition overflow for price components (`src/main/resources/db/migration/V3__create_shared_catalog_and_flight_schema.sql:91-104`, `src/main/resources/db/migration/V9__prevent_all_inclusive_price_overflow.sql:1-11`). No application-wide Trip budget maximum exists. |
| HTTP/client boundary | Existing response records expose only explicit public fields; error envelopes are `{code,message,fields}`. The frontend request helper sends same-origin credentials and `X-XSRF-TOKEN` on unsafe requests (`src/main/java/app/detour/api/ApiError.java:5-7`, `frontend/src/api/identityApi.ts:16-40`). |
| UI scope | The profile only displays email, an empty-state message, and password management; no Trip interaction exists (`frontend/src/components/ProfileScreen.tsx:21-25`). The current ticket excludes the Trip/profile frontend, leaving visible Trip flow work to P03-T06. |

## Existing Tests and Fixtures

- `IdentityApiIntegrationTest` covers registration, session isolation, password changes, CSRF, malformed JSON, unauthenticated errors, and avoids exposing user IDs or password material in the profile response (`src/test/java/app/detour/identity/IdentityApiIntegrationTest.java:28-154`). It has reusable client helpers that start from `GET /` for CSRF and retain individual `MockHttpSession` values (`src/test/java/app/detour/identity/IdentityApiIntegrationTest.java:156-184`).
- `ApplicationRestartIntegrationTest` proves a file-backed H2 user survives a close/restart, while its old session is rejected and a new login succeeds (`src/test/java/app/detour/identity/ApplicationRestartIntegrationTest.java:23-57`). It is the closest restart/persistence test pattern for the aggregate.
- Catalog integration tests use dynamically named H2 memory databases and schema/fixture assertions. `PhaseOneCatalogForwardMigrationIntegrationTest` first targets Flyway V2, inserts a user, then applies the full migration chain and verifies the user remains (`src/test/java/app/detour/catalog/PhaseOneCatalogForwardMigrationIntegrationTest.java:19-55`).
- There are no existing Trip validation, two-user Trip isolation, atomic Trip/Draft creation, failed-create rollback, stable Trip/Draft identifier, or Trip restart tests. There are no Trip API fixtures.

## Dependencies and Operational Constraints

- Platform: Java 21, Spring Boot 4.1.0, Spring Security, `JdbcTemplate`, H2, Flyway, React/TypeScript/Vite, with Maven building the frontend into one JAR (`pom.xml:1-96`).
- The default datasource is a file-backed H2 database at `./data/detour`; startup does not delete it, and loopback session cookies are secure by default (`src/main/resources/application.yml:1-22`, `README.md:7-26`).
- Flyway history is already forward through V11. The Phase 2 ticket requires forward-only migrations and forbids rewriting applied DeTour migrations (`ai/thoughts/tickets/2026-09-18-p02-t01-establish-catalog-inventory-foundation.md:9-18`).
- Roadmap and architecture require deterministic server-side validation, USD integer cents, a fixed PDX origin, three March-2027 destinations, and backend-enforced relational ownership (`ai/thoughts/phases/README.md:70-93`, `ai/thoughts/architecture/2026-09-17-p00-t02-detour-replacement-architecture.md:57-72`).
- No external supplier/model service is configured or required. Existing integration tests use local H2 and loopback HTTP only.

## Historical Context

- Commit `796c523` introduced secure identity and tenant-isolation behavior; commit `39e08a2` introduced the catalog/inventory foundation; `32ee0a9` and `ca310ad` seeded and verified the March 2027 catalog; current commit `c01de88` wrote the Phase 3 tickets. The immediately preceding cleanup commit removed prior ticket pipeline artifacts, explaining their absence from the checkout.
- The architecture record specifies that the relational owner boundary is enforceable `User -> Trip -> Itinerary/Booking and downstream data`, but deliberately leaves table shapes and endpoints to later implementation (`ai/thoughts/architecture/2026-09-17-p00-t02-detour-replacement-architecture.md:57-72`).
- Phase 3 defines a Draft as mutable and allowed to be incomplete after required shared details are supplied; it separately makes exact ages, at least one adult, and budget prerequisites for Planned promotion (`ai/thoughts/phases/phase-3-trips-itineraries-and-profile.md:7-26`).

## Open Questions

- The ticket requires a documented realistic age bound and safe maximum integer-cent budget, but no repository artifact establishes either value. The existing `BIGINT` convention establishes storage capacity only; it does not select the product-safe bound.
- The ticket requires stable Trip/Draft identifiers and create/detail responses but does not prescribe URL or JSON field shapes. Existing identity and API error conventions are available, while the concrete resource contract remains for planning.
- The catalog currently identifies destinations by seeded `catalog_key` and generated numeric key. The ticket calls for an immutable destination reference, but does not choose which representation is stored/exposed; planning must reconcile that choice with the current shared catalog contract.
