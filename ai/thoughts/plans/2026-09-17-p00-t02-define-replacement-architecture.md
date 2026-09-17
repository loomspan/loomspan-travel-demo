# P00-T02 Define DeTour Replacement Architecture Implementation Plan

## Overview

- Ticket: `ai/thoughts/tickets/2026-09-17-p00-t02-define-replacement-architecture.md`
- Research: `ai/thoughts/research/2026-09-17-p00-t02-define-replacement-architecture.md`
- Outcome: Add one durable, documentation-only DeTour architecture decision record that selects names, establishes capability disposition and domain ownership, and becomes the Phase 1 source of truth.

## Current State

Wayfarer is a Java 21 Spring Boot application in `demo.wayfarer`, with Maven coordinates `demo.loomspan:wayfarer`, a built React package named `wayfarer-frontend`, and a `target/wayfarer-0.1.0-SNAPSHOT.jar` run path (`pom.xml`, `frontend/package.json`, `scripts/run.ps1`). Its `application.yml` names both the application and H2 database `wayfarer` and includes Loomspan model, skill, trace, and observability configuration.

`ApiController`, `AssessmentService`, `IntakeService`, `TravelSkills`, the YAML manifests, and the React workspace implement the old unauthenticated, model-coordinated Boston--New York flow. `TripStore` already demonstrates reusable transactional inventory properties, but its global trip/booking schema and exchange/disruption flows are not compatible with DeTour.

The P00-T01 baseline found no concrete incompatibility with Java 21, Spring Boot, React, H2, Flyway, or Maven's single packaged-application shape. Its Maven checks did not start because Java was unavailable on `PATH`; `npm.cmd run build --prefix frontend` passed. Its 41 tests are disposed as 6 reusable behavior properties, 19 scenario-specific replacements, and 16 Loomspan/model-coupled removals.

## Desired End State

The repository will contain an architecture decision record, rather than an implementation change, that:

- establishes `app.detour`, `detour`, and the selected artifact/application/configuration/frontend/JAR conventions for subsequent tickets;
- retains the verified platform baseline and explicitly records that no incompatibility was found;
- gives every required Wayfarer capability a self-contained keep, replace, or remove decision without using old skills, UI copy, or demo documents as DeTour requirements;
- makes deterministic Java application logic the sole owner of planning, natural-language interpretation, ranking, explanation, and validation, with no model integration; and
- defines User, Trip, Itinerary, Booking, catalog, and inventory ownership/lifecycle boundaries without specifying tables, endpoints, or UI design.

## Scope

### In scope

- Add the durable decision record at `ai/thoughts/architecture/2026-09-17-p00-t02-detour-replacement-architecture.md`.
- Select and document technical names: Java package and Maven group ID `app.detour`; Maven artifact ID, Spring application name, configuration prefix, frontend package/application identifier, and packaged-output stem `detour`; environment-variable prefix `DETOUR_`; and expected JAR name `target/detour-0.1.0-SNAPSHOT.jar` while the existing version remains unchanged.
- Record the retained Java 21, Spring Boot, React, H2, Flyway, and Maven-built single-JAR shape, plus P00-T01's environmental Maven limitation as non-incompatibility evidence.
- Add a complete capability disposition matrix, a domain-boundary/lifecycle section, and the P00-T01 test-disposition migration rule.
- Verify the record's content and that the implementation diff contains only architecture documentation.

### Out of scope

- Renaming packages, Maven/frontend identifiers, application configuration, database paths, scripts, or packaged output.
- Removing Loomspan dependencies, code, manifests, credentials, observability, tests, or UI.
- Designing schemas, migrations, APIs, authentication mechanics, catalog fixtures, workflows, frontend visuals, or Version 2 Events.
- Deleting or migrating Wayfarer database files.

## Active Project Guardrails

None recorded in `ai/thoughts/design-lens.md`.

## Impact and Risk Analysis

- This record is a cross-phase contract: Phase 1 and P00-T03 will consume its selected names, so inconsistent technical identifiers or an ambiguous persistence handoff would create avoidable drift. The record must choose the general `DETOUR_` configuration convention but leave P00-T03 to select the exact new database filename and cleanup policy.
- Reusing current Wayfarer code as a behavioral authority would accidentally retain its fixed scenario, model-driven decisions, unauthenticated ownership, or supplier-disruption semantics. The roadmap and continuation guide, not current executable behavior, remain the product authority.
- The lifecycle boundary is sensitive: cancellation must remain immutable Booking history and must not become a generic itinerary status. The record must distinguish Draft and Planned itineraries, an active Booking, and a Canceled Booking snapshot.
- Documentation-only scope is intentional. No baseline build/test success is implied by this ticket, and the known Java-on-`PATH` limitation must not be misclassified as a platform incompatibility.

## Implementation Approach

Create a single first-class architecture decision record under `ai/thoughts/architecture/`; no architecture-record directory currently exists, so this makes the downstream contract easy to locate without mixing it into the product roadmap or code documentation. Use the roadmap and continuation guide as the DeTour authority, with P00-T01 as the test and compatibility evidence.

The record will make the following selected, evidence-supported conventions explicit: `app.detour` for Java package and Maven group ID; `detour` for Maven artifact ID, Spring application name, frontend package/application identifier, configuration prefix, and packaged JAR stem; `DETOUR_` for environment configuration variables; and `target/detour-0.1.0-SNAPSHOT.jar` as the expected output while the current version remains unchanged. The alternative of retaining a `wayfarer` identifier in one layer is rejected because the ticket and roadmap require DeTour, while inventing a different product namespace would violate the same settled convention.

The matrix will be self-contained and name a decision for reusable transaction behavior; planning/validation; orchestration and natural-language changes; booking, exchange, disruption, cancellation, and recovery; authentication; persistence; frontend; observability; scripts; documentation; and tests. It will separate general properties to retain from old contracts/fixtures to replace, and clearly mark Loomspan/model behavior and deferred exchange/disruption/recovery behavior for removal rather than a substitute AI integration.

## Phase 1: Write the architecture decision record

### Changes

- [x] `ai/thoughts/architecture/2026-09-17-p00-t02-detour-replacement-architecture.md` — add the record with the headings `Decision`, `Authoritative Inputs`, `Naming Conventions`, `Retained Platform Baseline`, `Capability Disposition Matrix`, `Domain Ownership and Lifecycle`, `Test-Transition Rule`, `Deferred Detail`, and `References`; explicitly note that P00-T01 found no concrete incompatibility.
- [x] `ai/thoughts/architecture/2026-09-17-p00-t02-detour-replacement-architecture.md` — add a keep/replace/remove matrix covering every ticket-required capability category, including the disposition and the target DeTour rule; do not refer readers to current YAML skills, demo documents, or UI copy to determine DeTour behavior.
- [x] `ai/thoughts/architecture/2026-09-17-p00-t02-detour-replacement-architecture.md` — define the relational ownership path `User -> Trip -> Itinerary/Booking and downstream data`, Trip-owned shared travel intent and alternatives, Itinerary-selected components/totals, Booking's one-active-per-Trip immutable inventory-reserving snapshot, and application-owned shared catalog/inventory.
- [x] `ai/thoughts/architecture/2026-09-17-p00-t02-detour-replacement-architecture.md` — state the lifecycle distinctions: Draft is mutable/incomplete, Planned is a stable itinerary snapshot, Booking is distinct from the itinerary state, and cancellation produces immutable Canceled Booking history rather than a generic itinerary state.
- [x] `ai/thoughts/architecture/2026-09-17-p00-t02-detour-replacement-architecture.md` — state that deterministic Java services own planning, natural-language interpretation, ranking, explanation, and validation; prohibit Loomspan and any substitute AI framework, model endpoint, prompt layer, or API key.
- [x] `ai/thoughts/architecture/2026-09-17-p00-t02-detour-replacement-architecture.md` — carry P00-T01 forward: recreate the six reusable inventory/HTTP properties using DeTour contracts and fixtures in their owning later tickets; replace the 19 scenario-coupled tests; remove the 16 model-coupled tests with the obsolete model paths.

### Automated verification

- [x] `rg -n "TODO|TBD|<[^>]+>" ai/thoughts/architecture/2026-09-17-p00-t02-detour-replacement-architecture.md` — produces no unresolved placeholders.
- [x] `rg -n "(derive|determine|interpret).*(Wayfarer|YAML|demo document|current UI)|(Wayfarer|YAML|demo document|current UI).*(derive|determine|interpret)" ai/thoughts/architecture/2026-09-17-p00-t02-detour-replacement-architecture.md` — produces no instruction that delegates DeTour requirements to obsolete sources; historical references that describe removal are reviewed separately.
- [x] `git diff --check` — reports no whitespace errors.

### Optional developer checks

- [ ] Read the naming and ownership sections as a downstream Phase 1/P00-T03 author and confirm they answer package, configuration-prefix, active-booking, and persistence-handoff questions without requiring the old application as an authority.

### Success criteria

The record alone names every convention and matrix/domain decision required by the ticket, clearly defers detailed design, and contains no unresolved behavior that would block P00-T03 or Phase 1.

## Phase 2: Validate documentation-only scope and traceability

### Changes

- [x] `ai/thoughts/architecture/2026-09-17-p00-t02-detour-replacement-architecture.md` — cross-check each matrix category against `ai/thoughts/phases/README.md`, `ai/thoughts/phases/CONTINUATION.md`, `ai/thoughts/phases/phase-0-baseline-and-boundaries.md`, and the P00-T01 baseline disposition; correct the record rather than adding new product behavior.
- [x] `ai/thoughts/architecture/2026-09-17-p00-t02-detour-replacement-architecture.md` — include references that let later tickets trace platform, domain, lifecycle, and test decisions to their governing sources.

### Automated verification

- [x] `git diff --name-only; git status --short` — shows the architecture record as the only ticket implementation artifact (including when it is untracked), preserves pre-existing workflow artifacts, and excludes production, test, configuration, generated, and database paths.
- [x] `git diff --check` — confirms the final documentation diff is whitespace-clean.

### Optional developer checks

- [ ] None.

### Success criteria

The final diff is limited to the architecture record and each acceptance criterion maps to a directly inspectable section or matrix row.

## Test Strategy

This is a documentation-contract change, so no production or JUnit test is added in this ticket. Step 3 will specify deterministic text-contract checks for required naming, matrix coverage, ownership/lifecycle rules, AI prohibition, P00-T01 disposition, absence of delegating placeholders, and documentation-only diff scope. It will also carry forward the baseline environment constraint: Maven tests are not a completion gate for a docs-only change and previously could not start without Java on `PATH`.

## Acceptance-Criteria Traceability

| Acceptance criterion | Planned code evidence | Planned test evidence |
| --- | --- | --- |
| Consistent DeTour names | Naming-conventions table in `ai/thoughts/architecture/2026-09-17-p00-t02-detour-replacement-architecture.md` | Deterministic required-token check for package, artifact, app, config prefix, frontend, and JAR name |
| Complete capability decisions | Capability matrix in the architecture record | Required-category and disposition check; scan confirms no delegated authority |
| Ownership and lifecycle are unambiguous | Domain-boundary and lifecycle sections in the architecture record | Required-concept and Draft/Planned/active/Canceled Booking wording check |
| Deterministic application logic and no AI | Deterministic-services decision and explicit prohibited-integrations list | Required deterministic/prohibited-token check |
| Platform retained unless incompatibility | Platform-baseline decision with P00-T01 evidence | Required-platform and no-incompatibility wording check |
| Documentation-only diff | New architecture record only | `git diff --name-only`, `git status --short`, and `git diff --check` |

## Risks and Rollback/Recovery

The primary risk is an inaccurate durable decision that misleads all downstream work. Mitigate it by grounding every matrix and lifecycle statement in the roadmap/continuation guide and by preserving P00-T01's test categories verbatim at the behavioral level. If review finds an error, amend only the architecture record before any Phase 1 implementation begins; no runtime state, schema, dependency, or Wayfarer database is changed or requires rollback.

## References

- `ai/thoughts/tickets/2026-09-17-p00-t02-define-replacement-architecture.md`
- `ai/thoughts/research/2026-09-17-p00-t02-define-replacement-architecture.md`
- `ai/thoughts/baselines/2026-09-17-p00-t01-capture-replacement-baseline.md`
- `ai/thoughts/phases/README.md`
- `ai/thoughts/phases/CONTINUATION.md`
- `ai/thoughts/phases/phase-0-baseline-and-boundaries.md`
- `pom.xml`, `frontend/package.json`, `src/main/resources/application.yml`, `scripts/run.ps1`
