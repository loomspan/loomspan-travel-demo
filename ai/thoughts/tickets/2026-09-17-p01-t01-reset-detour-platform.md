# P01-T01 — Reset the Application to a Clean DeTour Platform

## Outcome

Replace the executable Wayfarer application with a buildable DeTour Spring Boot and React foundation. The resulting application uses the settled DeTour identity and fresh development database lineage, starts without Loomspan or any model service, and contains no compatibility path to the superseded product.

## Requirements

- Apply the development-stage clean break: remove superseded Wayfarer backend and frontend behavior, routes, schemas, migrations, fixtures, configuration, tests, scripts, generated artifacts, and product-facing or technical documentation in the same change as their replacement. Do not retain adapters, placeholder interfaces, aliases, fallbacks, dual paths, deprecated wrappers, or data-import behavior.
- Remove Loomspan dependencies, version properties, imports, annotations, `SkillTemplate` use, YAML skills, manifest generation, model configuration and credentials, execution/trace views, model tests, and conversational-change behavior. Do not introduce another AI framework, model endpoint, prompt layer, natural-language interpreter, or API key.
- Use the architecture record's conventions consistently: Java package and Maven group `app.detour`; Maven artifact, Spring application, configuration prefix, and packaged-output stem `detour`; environment prefix `DETOUR_`; and frontend package/application identifier `detour-frontend`. No executable or product-facing layer may retain `wayfarer` as a target identifier.
- Retain Java 21, Spring Boot, React, H2, Flyway, Maven, and the single packaged-application JAR shape unless implementation research demonstrates a concrete incompatibility and obtains developer review before changing the platform.
- Replace the old migration chain with a fresh DeTour `V1` lineage in `classpath:db/migration`. Select a DeTour H2 path under the settled naming convention with no legacy datasource fallback, import, upgrade, or dual-schema behavior.
- Document an explicit, confirmation-gated development reset for the exact disposable local database targets selected by this ticket. The application must never delete database files during startup, and the reset must not broaden to unrelated files.
- Preserve historical planning records as provenance; their mentions of Wayfarer or Loomspan are not executable/product-facing remnants and must not be rewritten merely to make repository searches empty.
- Leave a minimal, coherent DeTour application shell for the identity tickets to extend. Do not implement accounts, catalog inventory, Trips, itineraries, planning, booking, cancellation, or Version 2 Events in this ticket.

## Acceptance criteria

- [x] Backend tests, the frontend production build, and the Maven packaged-application build succeed using the retained platform, producing `target/detour-0.1.0-SNAPSHOT.jar` when the existing project version remains unchanged.
- [x] The packaged DeTour application starts without a model endpoint, model credential, Loomspan configuration, or external AI service, and its frontend shell loads from the packaged JAR.
- [x] Scoped searches find no Loomspan/model integration or executable/product-facing Wayfarer identifier, route, storage key, schema, migration, fixture, compatibility alias, fallback, or transitional path; retained historical planning references are identified as the intentional exception.
- [x] A clean database applies only the fresh DeTour Flyway lineage beginning at `V1` and does not create or depend on the former Boston–New York scenario or Flyway history.
- [x] The documented reset previews exact DeTour database targets and requires confirmation before removal; startup does not remove files, and the reset cannot remove an unrelated database file placed beside the selected targets.
- [x] Maven coordinates, Java packages, Spring name, application configuration, environment variables, frontend metadata/storage keys, and packaged output consistently use the settled DeTour conventions without legacy fallbacks.
- [x] Obsolete Wayfarer/Loomspan tests and scripts are removed with the paths they covered, while the Phase 0 test-disposition record remains available to later tickets that recreate retained invariants against DeTour contracts.
- [x] The resulting application contains no account, catalog, Trip, itinerary, booking, cancellation, exchange, disruption, recovery, or Event behavior beyond the minimal platform shell.

## Context

- **Phase/work packages:** Phase 1 — Platform Reset and Identity; work packages 1.1 and 1.2.
- **Authoritative sources:** [`../phases/README.md`](../phases/README.md), [`../phases/CONTINUATION.md`](../phases/CONTINUATION.md), and [`../phases/phase-1-platform-reset-and-identity.md`](../phases/phase-1-platform-reset-and-identity.md).
- **Required architecture:** [`../architecture/2026-09-17-p00-t02-detour-replacement-architecture.md`](../architecture/2026-09-17-p00-t02-detour-replacement-architecture.md).
- **Hard dependency:** Phase 0 is complete. Its P00-T03 preservation policy is explicitly superseded by the roadmap's clean-break policy and must not govern this implementation.
- **Downstream dependencies:** P01-T02 depends on this platform, database lineage, and naming reset; P01-T03 depends on P01-T02.
- **Scope exclusions:** user/account behavior; catalog fixtures; Trip and itinerary persistence; component selection; booking/cancellation; visual-brand decisions; final public/demo copy; Version 2 Events.
- This is intentionally one cross-cutting reset ticket for GPT-5.6 Terra so deletion, replacement naming, database reset, and build repair land as one buildable state rather than a sequence of broken intermediate applications.

## Execution profile

- **Recommended:** Full 5-Step Pipeline
- **Confidence:** high
- **Rationale:** The change is repository-wide, removes supported application paths, changes persisted and packaged contracts, and requires material discovery to distinguish executable/product-facing remnants from historical records.
- **Reassessment triggers:** A concrete retained-platform incompatibility or evidence that a user-owned worktree change overlaps a path this clean break must replace requires developer review before proceeding.
