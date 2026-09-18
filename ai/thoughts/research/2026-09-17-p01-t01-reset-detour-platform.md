---
date: 2026-09-17
repository: loomspan-travel-demo
branch: main
commit: 908caa6a51ae224bbe68163d090c0c6b3ae0f27d
ticket: ai/thoughts/tickets/2026-09-17-p01-t01-reset-detour-platform.md
tags: [detour, platform-reset, clean-break, loomspan-removal, flyway, frontend]
---

# P01-T01 Reset the Application to a Clean DeTour Platform Research

## Research Question

What executable, configuration, persistence, frontend, test, script, generated-asset, and product-documentation surfaces currently implement Wayfarer/Loomspan, and what retained platform boundaries constrain the requested clean DeTour foundation?

## Summary

The current checkout is a complete Wayfarer application, not a partial DeTour shell. Its `demo.wayfarer` backend persists a fixed Boston--New York scenario, invokes Loomspan YAML skills through `SkillTemplate`, and exposes trip, assessment, booking, exchange, cancellation, and conversational-intake APIs; the React workspace consumes those APIs and presents the associated workflows.

The retained build shape is Maven + Java 21 + Spring Boot + H2/Flyway + React/Vite, with Maven building `frontend` during `generate-resources` and copying `frontend/dist` into the executable JAR. Current Maven coordinates, configuration, package locations, H2 path, reset script, frontend package/title/storage keys, test packages, migrations, docs, and `.env.example` all use legacy identifiers or behavior.

The authoritative roadmap and architecture record require a development-stage clean break: the existing migration chain and all Wayfarer/model paths are disposable; historical records under `ai/thoughts/` are intentional provenance and are not executable/product-facing remnants. The ticket deliberately excludes all account, catalog, Trip, itinerary, booking, cancellation, and Events behavior, so no current domain behavior is retained in this platform-reset shell.

## Repository State

- Captured 2026-09-17T20:46:02-07:00.
- Repository root: `C:/code/loomspan-travel-demo`; branch: `main`; commit: `908caa6a51ae224bbe68163d090c0c6b3ae0f27d` (`phase1 tickets created`).
- `git status --short` produced no output. There are no staged, unstaged, or untracked developer changes to distinguish from ticket work.
- `data/`, `target/`, `frontend/node_modules/`, and `frontend/dist/` are ignored (`.gitignore:1-8`). The tracked reset documentation identifies the current ignored legacy targets as `data/wayfarer.mv.db` and `data/wayfarer.trace.db`; no source inspection or research action opened, changed, or deleted a database.

## Current Behavior and Data Flow

1. Spring Boot launches `demo.wayfarer.WayfarerApplication` (`src/main/java/demo/wayfarer/WayfarerApplication.java:1-8`) using `application.yml`. The application binds to localhost, defaults to port 8082, opens `jdbc:h2:file:./data/wayfarer`, enables the H2 console, and configures a Loomspan OpenAI-compatible connection, model, YAML-skill location, quotas, observability, and trace persistence (`src/main/resources/application.yml:1-46`).
2. The React entry point fetches `/api/example` and `/api/trips`, saves the selected trip in `localStorage` as `wayfarer.trip`, and drives the fixed request/assessment/booking/exchange/cancellation workspace (`frontend/src/main.tsx:1-124`). Its API routes map to a single controller that exposes current Wayfarer trip, assessment, booking, exchange, return-cancellation, and intake endpoints (`src/main/java/demo/wayfarer/ApiController.java:10-27`).
3. `AssessmentService` creates persistent assessment state, queues work, then calls `SkillTemplate.invoke("planTrip", ...)`; it records Loomspan session/event data and tells users to inspect model configuration or Console diagnostics on failure (`src/main/java/demo/wayfarer/AssessmentService.java:17-48`). `TravelSkills` supplies Java methods annotated with `@SkillMethod`/`@SkillParam` for the YAML planner and writes model-coordination receipts (`src/main/java/demo/wayfarer/TravelSkills.java:9-48`).
4. Conversational changes form a second model path: `IntakeService` persists `intake_draft` rows, calls `SkillTemplate.invoke("interpretTripChange", ...)`, validates returned patches, and confirms them into a new trip revision (`src/main/java/demo/wayfarer/IntakeService.java:13-92`, `src/main/java/demo/wayfarer/IntakeService.java:111-208`). The frontend presents that state, session ID, and Loomspan-branded controls (`frontend/src/Conversation.tsx:9-30`). `Coordination` renders recorded skill-execution evidence and console references (`frontend/src/Coordination.tsx:3-16`).
5. The six Flyway migrations create the Wayfarer catalog/trip/assessment/booking schema, seed the October 2026 Boston--New York inventory, then add model receipts, exchange history, disruptions, and conversation drafts (`src/main/resources/db/migration/V1__business_schema.sql:1-10`, `V2__seed_inventory.sql:1-16`, `V3__catalog_receipts.sql:1-6`, `V4__booking_exchanges.sql:1-8`, `V5__service_disruptions.sql:1-8`, and `V6__conversation_drafts.sql:1-7`). This chain necessarily depends on the existing Flyway history when opening the default legacy database.
6. `scripts/reset-demo.ps1` is confirmation-gated but only deletes the two literal Wayfarer H2 targets after resolving them under `data/` (`scripts/reset-demo.ps1:1-11`). It does not preview targets before deletion. The old README instead presents that operation as reset of a Wayfarer demo (`README.md:14-37`, `README.md:190-197`).

## Key Components

- `pom.xml:11-35` — declares `demo.loomspan:wayfarer`, Loomspan/Spring AI version management, and the Loomspan starter; `pom.xml:64-117` retains the Maven frontend build and copy-to-static-JAR pipeline.
- `src/main/resources/application.yml:1-46` — current server, `WAYFARER_` datasource/port configuration, Spring name, and all model/trace configuration.
- `src/main/java/demo/wayfarer/` — the entire backend package is legacy: application entry point, controller, domain contracts/calculator/store, model assessment service, skill adapter, and conversational-intake service.
- `src/main/resources/skills/*.yml` — five generated Loomspan skill manifests; `planTrip.yml` coordinates nested travel skills and `interpretTripChange.yml` contains the natural-language patch contract.
- `scripts/generate-skill-manifests.py` — generates all five manifests and reports that fact at its end; it is tied to the removed Loomspan manifest format.
- `src/main/resources/db/migration/V1__business_schema.sql` through `V6__conversation_drafts.sql` — legacy V1--V6 Flyway lineage and fixed inventory/assessment/exchange/disruption/intake persistence.
- `frontend/package.json:1` and `frontend/package-lock.json:2-8` — frontend is named `wayfarer-frontend`; React, TypeScript, Vite, and the production build command are retained-platform dependencies.
- `frontend/index.html:2`, `frontend/src/main.tsx:1-124`, `frontend/src/types.ts:1-9`, `frontend/src/Conversation.tsx:1-31`, `frontend/src/Coordination.tsx:1-16`, and `frontend/src/DemoGuide.tsx:1-28` — current Wayfarer identity, `wayfarer.*` storage keys, fixed-scenario UI, model conversation, traces, exchange/recovery, and demo walkthroughs.
- `frontend/vite.config.ts:1-3` — retained Vite/React configuration with the current backend proxy at port 8082.
- `.env.example:1-9`, `README.md:1-218`, and `docs/*.md` — product-facing/technical material currently documents model credentials, Loomspan behavior, and the Boston--New York scenario.

## Affected Areas

| Area | Current behavior and evidence |
| --- | --- |
| Build identity and packaging | Maven currently produces the `wayfarer` artifact under group `demo.loomspan`; Java 21, Boot 4.1.0, H2/Flyway, and React build/copy stages are all present in `pom.xml:5-117`. |
| Backend behavior | Every Java class sits in `demo.wayfarer`. `ApiController` exposes legacy routes (`ApiController.java:15-27`); `TripStore`, `TripCalculator`, `Contracts`, and `IntakeContracts` implement the fixed planning/booking model used by those routes. |
| Model integration | The Maven starter (`pom.xml:33-35`), config (`application.yml:18-46`), `SkillTemplate` services (`AssessmentService.java:3,19-47`; `IntakeService.java:3,18-92`), Java annotations (`TravelSkills.java:3-4,13-31`), YAML manifests, generated-manifest script, traces, and model-dependent frontend components form one connected integration. |
| Persistence | The current default file database is `data/wayfarer`, and Flyway discovers the standard classpath migrations. V1 defines both fixed inventory and Trip/assessment/booking schema; V2 seeds the Boston--New York data; V3--V6 add model/exchange/disruption/intake tables. |
| Reset safety | The current script has an opt-in switch and literal target names, but removes immediately after confirmation (`scripts/reset-demo.ps1:1-11`). Existing records state that Phase 1 must select exact DeTour targets and document a preview plus confirmation; the application configuration itself has no startup deletion command. |
| Frontend | The single React app is a complete product experience rather than a minimal shell. It uses a Wayfarer title/package and local-storage identifiers, makes only legacy `/api` calls, and includes planning, booking, exchanges, disruptions, recovery, conversation, and trace UI (`frontend/src/main.tsx:13-124`). |
| Documentation and environment examples | `.env.example` documents `OPENAI_API_KEY`, `WAYFARER_MODEL`, and model/observability settings. README and all current `docs/` files describe the superseded product and its operational/model instructions. |
| Historical provenance | `ai/thoughts/phases/README.md`, `CONTINUATION.md`, the architecture record, baseline, Phase 0 tickets, and phase/ticket records intentionally mention Wayfarer and Loomspan to state the replacement decision. The ticket expressly preserves these records. |

## Existing Tests and Fixtures

- Five JUnit classes under `src/test/java/demo/wayfarer/` contain 41 `@Test` methods. The Phase 0 baseline classifies six transaction/HTTP properties as later reimplementation targets, 19 fixed-scenario tests as replacements, and 16 Loomspan/model tests as removals (`ai/thoughts/baselines/2026-09-17-p00-t01-capture-replacement-baseline.md`, “Automated-test disposition inventory”). None is a DeTour contract or a shell test.
- `TripApplicationTest`, `HttpFlowTest`, and `IntakeServiceTest` use the legacy package, schema, routes, and model mocks; `LivePlanningTest` and `LiveIntakeTest` are gated by `WAYFARER_LIVE_TEST` and exercise provider-backed Loomspan paths. For example, `HttpFlowTest` declares a mocked `SkillTemplate` and legacy in-memory datasource at `src/test/java/demo/wayfarer/HttpFlowTest.java:1-42`.
- There are no tracked frontend test/spec files and `frontend/package.json:1` provides only `dev` and `build`. Baseline evidence records `npm.cmd run build --prefix frontend` as passing at capture time; Maven test/package could not start because Java was unavailable on `PATH` in that environment, not because the retained platform was incompatible.
- Migration fixtures are embedded in `V2__seed_inventory.sql`; product documentation also contains scenario facts and calculated combinations. These are legacy fixtures/documentation, not DeTour inputs.

## Dependencies and Operational Constraints

- The architecture record fixes the future identifiers: Java/Maven group `app.detour`, artifact/JAR stem/application/config prefix `detour`, environment prefix `DETOUR_`, and frontend identifier `detour-frontend` (`ai/thoughts/architecture/2026-09-17-p00-t02-detour-replacement-architecture.md`, “Naming Conventions”). With version unchanged, the target JAR name is `target/detour-0.1.0-SNAPSHOT.jar`.
- The retained platform is Java 21, Spring Boot, React, H2, Flyway, Maven, and one packaged JAR. The architecture record says no concrete incompatibility was found; earlier Maven failures were environmental Java-path failures, while the frontend production build passed.
- The reset must be development-only and confirmation-gated. Current policy evidence names only legacy targets; it contains no existing DeTour path or file. No startup operation may delete local files, and a reset must not reach unrelated neighbors.
- Maven invokes `npm` rather than `npm.cmd` in `pom.xml:79-104`; this is relevant to Windows verification because the baseline used `npm.cmd` successfully after PowerShell policy blocked `npm.ps1`.
- No live model, network, provider, or database reset was exercised during this research.

## Historical Context

- Git history shows the checked-out Wayfarer feature sequence: initial setup, booking change, disruption recovery, conversational changes, guided scenarios, then the Phase 0 baseline/architecture and a roadmap update allowing destructive changes. The current commit is `phase1 tickets created`.
- The authoritative roadmap’s development-stage clean-break policy supersedes the conservative Phase 0 P00-T03 policy. P00-T03 is retained as provenance and explicitly says it must not be used as a Phase 1 requirement (`ai/thoughts/tickets/2026-09-17-p00-t03-establish-persistence-migration-policy.md:1-4`).
- Phase 1 work packages 1.1 and 1.2 match this ticket: remove Loomspan/model behavior and reset branding/configuration/migrations before the separately ticketed identity work (`ai/thoughts/phases/phase-1-platform-reset-and-identity.md`, “1.1” and “1.2”).

## Open Questions

- No DeTour H2 file path or exact disposable reset target exists in the current checkout. The architecture and ticket delegate selection of the DeTour path and confirmation-gated reset targets to this implementation ticket; the only source-backed file targets today are legacy Wayfarer targets, which the new reset must not retain.
- No current backend or frontend test establishes the expected observable contents of the minimal DeTour shell. The existing test disposition supports deleting legacy tests; the implementation/testing stages need to establish executable shell, clean-lineage, reset-safety, packaging, and absence checks from the ticket’s acceptance criteria.
