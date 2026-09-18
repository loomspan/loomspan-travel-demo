---
date: 2026-09-17
repository: loomspan-travel-demo
branch: main
commit: 09786c454cca74f957f4664469726bc8c2cf847e
ticket: ai/thoughts/tickets/2026-09-17-p01-t02-establish-secure-user-identity.md
tags: [detour, identity, authentication, sessions, csrf, flyway, tenant-isolation]
---

# Establish Secure DeTour Accounts and Tenant Isolation Research

## Research Question

What identity, persistence, HTTP, session, CSRF, authorization, static-asset, and test behavior exists in the clean DeTour checkout that P01-T02 must extend?

## Summary

The P01-T01 checkout is a deliberately minimal Spring Boot MVC, JDBC, H2, Flyway, and React foundation. It has no identity schema, Java HTTP handler, security configuration, password encoder, session configuration, exception mapping, or application/catalog endpoint. The sole `V1` migration is an empty baseline comment; current database persistence is the Flyway schema-history table plus any future schema introduced through that lineage.

The packaged application shape already serves a built React shell from Spring Boot static resources. The runtime datasource is a file-backed H2 database under `data/`, while the sole test overrides it with a unique in-memory H2 URL. There are no existing identity fixtures, mock users, default credentials, user-owned resources, or two-user isolation coverage.

## Repository State

- Recorded 2026-09-17 in the America/Los_Angeles workspace timezone.
- Repository: `loomspan-travel-demo`; branch: `main`; commit: `09786c454cca74f957f4664469726bc8c2cf847e` (`clean up from P01-T01`).
- `git status --short --branch` reported `## main...origin/main` with no staged, unstaged, or untracked changes.
- `data/` contains no local database file at this checkout. Existing `target/` build and Surefire output are ignored build artifacts, not ticket work.

## Current Behavior and Data Flow

1. `app.detour.DetourApplication` is the sole Java source and only starts Spring Boot; there are no controllers, services, repositories, entities, filters, or exception handlers (`src/main/java/app/detour/DetourApplication.java:6-10`). A repository-wide executable-source search found no request mapping, security, CSRF, cookie, browser-storage, controller, model, or credential code.
2. Spring config binds the server to `127.0.0.1:8082` by default and configures the file-backed H2 datasource `jdbc:h2:file:./data/detour;DB_CLOSE_ON_EXIT=FALSE;LOCK_TIMEOUT=10000` (`src/main/resources/application.yml:1-10`). This establishes the local persisted-data location but contains no TLS/proxy, Spring Security, session, or cookie settings.
3. Flyway runs from its conventional classpath location because the Flyway starter is present, but the fresh `V1` script only contains an explanatory comment (`pom.xml:31-35`, `src/main/resources/db/migration/V1__detour_platform.sql:1`). Therefore no DeTour application tables or credentials currently exist.
4. Maven runs `npm ci` and the Vite production build during `generate-resources`, then copies `frontend/dist` into `target/classes/static` (`pom.xml:60-108`). The React entry point renders a static foundation message only and makes no network requests (`frontend/src/main.tsx:5-14`). There is no separate frontend server in the packaged flow.
5. `scripts/run.ps1` launches the packaged JAR, and `scripts/reset-detour.ps1` independently targets only `data/detour.mv.db` after an explicit confirmation (`scripts/run.ps1:1-7`, `scripts/reset-detour.ps1:1-23`). Startup itself has no database-deletion behavior.

## Key Components

- `pom.xml:18-45` — Includes Web MVC, JDBC, H2 console, Flyway, H2 runtime, and Spring Boot test; it does not declare a Spring Security or password-encoding dependency.
- `src/main/resources/application.yml:1-10` — Loopback server and file H2 datasource configuration; no identity, HTTPS, cookie, security, session, or CSRF settings.
- `src/main/resources/db/migration/V1__detour_platform.sql:1` — Existing fresh Flyway lineage is intentionally schema-empty.
- `src/main/java/app/detour/DetourApplication.java:6-10` — Application bootstrap only.
- `src/test/java/app/detour/DetourApplicationTest.java:17-50` — Current context/Flyway test pattern. It replaces the production datasource with a unique in-memory H2 database and checks that only migration `1` applied and removed Wayfarer tables are absent.
- `frontend/src/main.tsx:5-14` — Current packaged static application shell, with no identity client behavior or browser-managed state.
- `scripts/run.ps1:1-7` and `README.md:7-14` — Packaged-JAR launch and documented single-application deployment shape.
- `scripts/reset-detour.ps1:1-23` and `README.md:23-32` — Explicit, constrained development database reset boundary.

## Affected Areas

| Area | Current behavior and evidence |
| --- | --- |
| Persistence | The standard Flyway location has only the fresh `V1` baseline comment. The production datasource is file-backed H2, so application rows created by a future migration can survive a process restart; no application data tables exist today (`application.yml:7-10`, `V1__detour_platform.sql:1`). |
| Authentication and passwords | No Spring Security dependency or any security/credential source is present (`pom.xml:18-45`; executable-source search). There is no account, credential hash, email normalization, registration, login, logout, current-profile, or password-change behavior. |
| Sessions and CSRF | No session policy, cookie attributes, CSRF mechanism, reverse-proxy/TLS setting, or security filter chain is configured (`application.yml:1-10`; executable-source search). The current default loopback HTTP deployment has no stated production HTTPS configuration. |
| HTTP contract and errors | No application controller or controller advice exists, so no identity endpoint shape, JSON validation response, unauthenticated/forbidden response, malformed-input response, or non-disclosing missing-resource response is implemented. |
| Authorization and ownership | There are no user-owned tables or identifiers yet. The required architecture records the future relational path as `User -> Trip -> Itinerary/Booking and downstream data`, with every user-data query and mutation scoped through the authenticated User (`ai/thoughts/architecture/2026-09-17-p00-t02-detour-replacement-architecture.md:62-70`). |
| Static/public surface | Maven packages the Vite build into Spring Boot static resources (`pom.xml:60-108`). The present React shell is static and contains no disclosure surface or API calls (`frontend/src/main.tsx:5-14`); no security rule currently differentiates static assets from application endpoints. |
| Scope boundary | The roadmap requires login for browsing/planning and ordinary self-service accounts without roles or administrators; new users begin empty (`ai/thoughts/phases/README.md`, “Users and profiles”; `ai/thoughts/phases/phase-1-platform-reset-and-identity.md:25-44`). The ticket expressly excludes Trip, itinerary, booking, catalog, and Event persistence/behavior. |

## Existing Tests and Fixtures

- `DetourApplicationTest.startsWithOnlyFreshDetourMigration` is the only test. It uses `@SpringBootTest`, dynamically supplies a unique `jdbc:h2:mem:` database URL, and verifies that Flyway reports only version `1` and that listed Wayfarer tables do not exist (`src/test/java/app/detour/DetourApplicationTest.java:17-50`).
- No HTTP integration client, authenticated test session, CSRF test helper, password test fixture, user fixture, static-resource test, restart test, or two-user isolation test exists.
- Existing Surefire output records the sole baseline test as passing (one test, zero failures) from the previous build. It is historical build output rather than a check run in this research step; this research step did not start the application, contact an external service, or execute tests.
- Java, Maven, and npm are locally available (`java` reports JDK 25; Maven 3.9.11; npm 11.17.0). The project declares Java 21 as its target (`pom.xml:14-16`).

## Dependencies and Operational Constraints

- The retained platform is Java 21, Spring Boot, React, H2, Flyway, Maven, and a single packaged application JAR; the architecture record says a materially different platform requires concrete incompatibility evidence and developer review (`ai/thoughts/architecture/2026-09-17-p00-t02-detour-replacement-architecture.md:35-39`).
- P01-T01 intentionally removed legacy Wayfarer controllers, schema, migrations, tests, model behavior, and frontend flows in commit `0aff2b4`. Its replacement baseline has no compatibility endpoints or legacy database fallback.
- The default application is loopback-only and has no external provider, frontend development server, model service, or API key requirement (`README.md:7-20`). The ticket’s same-packaged-app requirement matches this current deployment shape; no cross-origin configuration exists.
- The development reset command can remove only the default database file after explicit confirmation. A schema change does not authorize automatic deletion of the local database (`scripts/reset-detour.ps1:12-23`, `README.md:23-32`).
- The H2 console starter is on the classpath (`pom.xml:27-30`), but no `spring.h2.console.enabled` setting exists. The prior Surefire condition report recorded the console auto-configuration as disabled because that property was absent.

## Historical Context

- The P01-T01 reset commit (`0aff2b4`) replaced the Wayfarer application with the current DeTour baseline and removed legacy `/api` controller routes, domain code, legacy Flyway migrations, Loomspan/model artifacts, and associated tests. Those removed files are historical evidence only and do not provide a DeTour contract.
- The architecture decision explicitly leaves detailed schemas, APIs, authentication mechanics, and frontend visual design to owning tickets while fixing the ownership rule and clean-break boundary (`ai/thoughts/architecture/2026-09-17-p00-t02-detour-replacement-architecture.md:62-70, 94-95`).
- P01-T03 is downstream and states that it will consume, rather than redesign, the P01-T02 identity/session/CSRF contracts (`ai/thoughts/tickets/2026-09-17-p01-t03-deliver-authenticated-identity-experience.md:9-13, 36-41`).

## Open Questions

- The ticket leaves endpoint shapes and implementation details to planning. No current source names a URL namespace, request/response format, CSRF bootstrap/delivery mechanism, or error-envelope format for P01-T03 to consume.
- Current configuration documents only loopback HTTP. There is no repository evidence of a production HTTPS termination/proxy setting or a test/deployment profile for secure-cookie behavior; planning must distinguish confirmed current configuration from the ticket’s production-cookie requirement.
- There are no user-owned resources or exposed user identifiers in this phase beyond the planned identity surface. The exact identity identifiers that require the ticket’s two-user isolation coverage are therefore not established by the checkout.
