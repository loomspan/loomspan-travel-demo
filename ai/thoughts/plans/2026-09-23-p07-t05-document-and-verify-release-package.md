# DeTour Release Package Implementation Plan

## Overview
- Ticket: `ai/thoughts/tickets/2026-09-23-p07-t05-document-and-verify-release-package.md`
- Research: `ai/thoughts/research/2026-09-23-p07-t05-document-and-verify-release-package.md`
- Outcome: current documentation and repeatable evidence for a release-ready JAR.

## Current State
`README.md` is a short early-phase guide. `pom.xml` bundles the React app; `application.yml` uses persistent H2 and no provider keys. Migration, API, inventory, restart, and UI tests exist. `SecurityConfiguration.java` retains a public `/demo-disclosure/**` matcher with no route implementation. `TripController.java` also retains route aliases used by older backend tests.

## Desired End State
The README gives accurate local setup, use, reset, architecture, fixtures, and test commands. A fresh clean package passes backend/frontend tests and isolated packaged HTTP/restart checks. The only supported scope is the fictional PDX to SFO/MUC/MEX March 2027 travel planner; obsolete executable routes and product copy are absent.

## Scope
### In scope
- README and release evidence, a packaged smoke test with an isolated database, narrow gaps in regression evidence, removal of the obsolete security matcher.
### Out of scope
- Providers, payment, exchanges, recovery workflows, events, deployment, native apps, and database migration from earlier disposable states.

## Active Project Guardrails
None recorded in `ai/thoughts/design-lens.md`.

## Impact and Risk Analysis
The public security matcher is a stale authorization rule; remove it without broadening public access. The package must use a clean build so old static assets are not bundled. Packaged tests must use an isolated temporary database and terminate their own child process. Booking, cancellation, inventory, and persisted contracts should remain unchanged; existing targeted tests must prove them.

## Implementation Approach
Keep behavior changes minimal. Remove the unused public route allowance. Extend the existing packaged verification workflow to exercise a clean database, registration, a representative Trip/Plan, restart, new login, and persisted state. Rely on focused integration suites for booking and cancellation concurrency, clock boundaries, and snapshots; add missing regression coverage only where inspection shows a concrete gap. Document exact commands and results in a release evidence artifact.

## Phase 1: Remove obsolete behavior and update guidance
### Changes
- [x] `src/main/java/app/detour/security/SecurityConfiguration.java` — delete the unused `/demo-disclosure/**` public matcher.
- [x] `src/main/java/app/detour/trip/TripController.java` and affected integration tests — remove old route aliases and use canonical plural/duplicate paths.
- [x] `README.md` — replace incomplete guidance with supported scope, setup, architecture, configuration, workflow, fixtures, reset/clean break, package and verification instructions.
### Automated verification
- [x] Scoped source and generated-asset `rg` scans — no obsolete executable/product references; negative route assertions remain in the release verifier by design.
### Optional developer checks
- [ ] Keyboard and narrow-screen visual walkthrough of the packaged UI, if browser automation cannot cover the rendered interaction.

## Phase 2: Strengthen packaged and persistence evidence
### Changes
- [x] `scripts/verify-packaged-release.ps1` and `.mjs` — isolated packaged startup, HTTP registration-to-plan, booking/cancellation, restart, new login, persisted state, cleanup; retired the identity-only script.
- [x] `src/test/java/app/detour/booking/BookingApplicationRestartIntegrationTest.java` — existing active-booking snapshot test retained; packaged verifier covers canceled history after restart, so no duplicate test edit needed.
- [x] `ai/thoughts/release/2026-09-23-p07-t05-verification.md` — record commands, results, scope search, and residual checks.
### Automated verification
- [x] `.\mvnw.cmd clean verify` — backend, integration, frontend build, JAR.
- [x] `npm test` from `frontend/` — frontend interaction/accessibility assertions.
- [x] `.\scripts\verify-packaged-release.ps1` — fresh packaged HTTP and restart proof.
- [x] `.\scripts\show-catalog-fixture-summary.ps1` — inspect supported fixtures.
### Optional developer checks
- [ ] Manual keyboard and responsive viewport review if no automated browser driver is present.

## Test Strategy
Use existing JUnit integration tests for server validation, ownership, transactionality, concurrency, clock boundaries, migration and restart. Use Vitest for accessible UI behavior. Use a temporary file database and loopback port for packaged HTTP checks. No routine test uses live services.

## Acceptance-Criteria Traceability
| Acceptance criterion | Planned code evidence | Planned test evidence |
| --- | --- | --- |
| Accurate README | `README.md`, scripts/config | Execute documented commands |
| Clean DB, registration-to-plan, no keys | Flyway lineage and packaged smoke | `DetourApplicationTest`, packaged script |
| Restart/new login and persistence | file H2, sessions, snapshots | restart integration tests and packaged script |
| Backend/frontend/concurrency/accessibility/responsive/package | existing suites plus release evidence | clean Maven verify, npm test, smoke, UI checks |
| No obsolete implementation/copy | security matcher removal and scoped scan | `rg` and built JAR inspection |
| Release-ready package | `target/detour-0.1.0-SNAPSHOT.jar` | clean build and packaged smoke |

## Risks and Rollback/Recovery
The release script must leave the default `data/detour` untouched. If verification fails, fix the scoped defect and rebuild from clean state; do not reset a user's database. Documentation/security matcher changes are reversible through Git.

## References
Ticket, research document, `README.md`, `pom.xml`, `application.yml`, `scripts/`, and Phase 7 plan.
