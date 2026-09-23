---
date: 2026-09-23
repository: loomspan-travel-demo
branch: main
commit: 8211b9cf0b7e0e2d8441a919b75a57ad545ade23
ticket: ai/thoughts/tickets/2026-09-23-p07-t05-document-and-verify-release-package.md
tags: [detour, release, verification]
---

# DeTour Release Package Research

## Research Question
What currently builds, runs, persists, and verifies the DeTour release scope, and where does repository guidance or executable behavior remain obsolete?

## Summary
The checkout was clean at the start of research. Maven packages a React build into a Spring Boot JAR. H2 Flyway migrations V1–V18 create and seed the March 2027 catalog; user and trip tables start empty. The README covers a minimal build and reset but omits workflows, architecture, and broad verification. A security permit rule retains `/demo-disclosure/**`, while the only current disclosure is the React About tab.

## Repository State
Main at `8211b9cf0b7e0e2d8441a919b75a57ad545ade23`; no staged, unstaged, or untracked files before work. Local Java 25, Node 24.19.0, npm 11.17.0 are available. Generated `target/` and `frontend/dist/` exist and require fresh packaging to establish current contents.

## Current Behavior and Data Flow
Registration through `IdentityController` creates a user and servlet session; `GET /api/profile` includes trip summaries. `TripController` and `TripService` own trips, drafts, selection, and promotion. `BookingService` resolves trips by owner, checks idempotency keys, and delegates booking and cancellation to `BookingTransactionExecutor`; the repository stores booking snapshots and inventory. Clock injection supports a fixed instant; status and cutoff behavior use PDX time. `application.yml` defaults to loopback port 8082 and a persistent file database. `scripts/reset-detour.ps1` previews one default file and only removes it with `-ConfirmReset`; startup has no file-delete logic.

## Key Components
- `pom.xml:108` — copies built React assets into JAR resources.
- `src/main/resources/application.yml:2` — loopback port, H2 file URL, secure-cookie configuration.
- `src/main/resources/db/migration/V1__detour_platform.sql:1` — new DeTour migration lineage; V10 and V11 seed the catalog.
- `src/main/java/app/detour/identity/IdentityController.java:40` — register, login, logout, profile.
- `src/main/java/app/detour/trip/TripController.java:27` — owned trip API.
- `src/main/java/app/detour/booking/BookingService.java:53` — owner and idempotency handling.
- `src/main/java/app/detour/common/ClockConfiguration.java:15` — fixed-instant option.
- `src/main/java/app/detour/security/SecurityConfiguration.java:46` — stale public `/demo-disclosure/**` matcher.
- `frontend/src/components/AboutDemoTab.tsx:18` — current sole general demo disclosure.
- `scripts/verify-packaged-identity.ps1:1` — isolated JAR identity shell smoke check.

## Affected Areas
| Area | Current behavior and evidence |
| --- | --- |
| Documentation | `README.md:1` says Phase 7 release verification remains in progress and only documents basic build/reset. |
| Scope | `AboutDemoTab.tsx:25` names PDX, San Francisco, Munich, Mexico City and March 2027. |
| Database | `DetourApplicationTest.java:35` asserts V1–V18 and seeded catalog counts, with empty user/trip tables. |
| Persistence | `ApplicationRestartIntegrationTest.java:24`, `TripApplicationRestartIntegrationTest.java:24`, and `BookingApplicationRestartIntegrationTest.java:26` exercise file-backed restart. |
| Safety | `BookingConcurrencyIntegrationTest.java:59` covers contention and idempotency races; `BookingCancellationIntegrationTest.java:496` covers owner isolation. |

## Existing Tests and Fixtures
`frontend/package.json` defines `npm test` and `npm run build`; frontend tests cover auth, navigation, responsive markup, focus, disclosure, comparison, and booking. Maven tests include HTTP integration, fixture, schema, restart, booking concurrency, and cancellation suites. `TestClockConfiguration` supplies controllable instants; `scripts/show-catalog-fixture-summary.ps1` runs a fixture report without the web app. Existing packaged smoke only checks the identity shell, not an end-to-end plan or booking.

## Dependencies and Operational Constraints
Java 21+, Node/npm, and the Maven wrapper are needed for a clean package. No provider API key is configured. The packaged smoke script uses an isolated temporary H2 file and loopback port. Routine tests use memory or temporary file databases.

## Historical Context
Phase 7 specifies one About tab disclosure, release verification, and no deployment. Historical planning artifacts can retain former-system terms. The migration test deliberately names legacy table names as negative assertions.

## Follow-up Finding
The fuller controller scan found `/revisions`, singular `/stay` and `/rental`, and `/car(s)` aliases in `TripController.java:58-226`. Backend tests still used some aliases; the React client used only the plural and `/duplicate` routes. This is ticket-scope obsolete executable behavior.

## Open Questions
Whether the existing test and packaged smoke coverage fully demonstrate every ticket acceptance criterion requires execution and coverage inspection. Any missing proof belongs in the implementation/testing plan.
